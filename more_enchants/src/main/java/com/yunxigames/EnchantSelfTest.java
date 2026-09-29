package com.yunxigames;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 开服自检：把这一批功能逐条跑一遍，结论直接写进日志。
 *
 * <p>为什么要在<b>真服务器</b>上跑而不是写单元测试：这些功能全都要摸到
 * {@code ServerLevel}、实体生成、掉落路径和广播，纯 mock 测不出「真的能用」。
 * 所以自检挂在 {@code SERVER_STARTED} 上，拿真实的 {@code overworld} 当实验场。
 *
 * <p>触发方式：配置里把 {@code selfTestRolls} 设成大于 0 的数（比如 300），
 * 开服时就会跑一遍；跑完改回 0 即可关闭。也可以用 {@code /yg selftest} 随时手动跑。
 *
 * <p>自检期间 {@link SessionStats} 是暂停的 —— 几千次假掉落不该污染「本局战绩」。
 */

import static com.yunxigames.SelfTest.*;

public final class EnchantSelfTest {
	private EnchantSelfTest() {
	}

static void checkStinkyFeet(MinecraftServer server, ServerLevel level, EnchantsConfig config) {
		// 基准点取真实地表：写死 y=80 在空中时，亡灵找不到落脚点会生成失败，
		// 于是这项在不同世界里时好时坏（不是功能问题，是测试环境没搭对）。
		BlockPos base = level.getHeightmapPos(
				net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, new BlockPos(5, 64, 5));

		// 铺一小块石台：若落点是水面/沙滩，亡灵生成会找不到可站立的地面而失败，
		// 那是测试环境问题而非功能问题（顺带把区块强制加载出来）。
		for (int px = -2; px <= 2; px++) {
			for (int pz = -2; pz <= 2; pz++) {
				level.setBlock(base.offset(px, 0, pz), Blocks.STONE.defaultBlockState(), 2);
			}
		}

		level.setBlock(base.above(), Blocks.DANDELION.defaultBlockState(), 2);
		level.setBlock(base.above().east(), Blocks.POPPY.defaultBlockState(), 2);
		level.setBlock(base.above().east(2), Blocks.TALL_GRASS.defaultBlockState(), 2);
		level.setBlock(base, Blocks.GRASS_BLOCK.defaultBlockState(), 2);

		EnchantmentEffects.witherPlants(level, base, config.stinkyRadius);

		boolean flowerGone = level.getBlockState(base.above()).isAir()
				&& level.getBlockState(base.above().east()).isAir();
		boolean grassGone = level.getBlockState(base.above().east(2)).isAir();
		boolean dirt = level.getBlockState(base).getBlock() == Blocks.DIRT;

		// 临时拉满生成概率：自检只调用一次，按默认概率抽可能抽不到，会误判成功能失效
		double savedChance = config.stinkyUndeadSpawnChance;
		config.stinkyUndeadSpawnChance = 1.0D;

		int before;
		int after;
		try {
			before = countUndead(level, base, config.stinkyRadius + 4.0D);
			EnchantmentEffects.spawnUndeadNear(level, base);
			after = countUndead(level, base, config.stinkyRadius + 4.0D);
		} finally {
			config.stinkyUndeadSpawnChance = savedChance;
		}

		boolean undeadSpawned = after > before;

		// 清理
		level.setBlock(base.above(), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(base.above().east(), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(base.above().east(2), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(base, Blocks.AIR.defaultBlockState(), 2);

		check("⑲ 臭脚·花草枯萎+亡灵生成",
				flowerGone && grassGone && dirt && undeadSpawned,
				"花消失=" + flowerGone + " 草消失=" + grassGone + " 草方块→泥土=" + dirt
						+ "；亡灵生成 " + before + "→" + after);
	}

	
static void checkMagnet(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> magnet = ModEnchantments.byName(level, ModEnchantments.MAGNET);
		boolean registered = magnet != null;
		boolean detect = false;
		if (registered) {
			ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
			setEnchant(chest, magnet);
			detect = ModEnchantments.hasEnchantment(chest, magnet);
		}

		// 吸力路径：3 格外放一个掉落物，结算后应有朝向中心的速度分量
		Vec3 center = new Vec3(0.5, 90.0, 0.5);
		ItemEntity drop = new ItemEntity(level, 3.5, 90.0, 0.5, new ItemStack(Items.DIRT));
		boolean added = level.addFreshEntity(drop);
		EnchantmentEffects.magnetPullAt(level, null, center, config.magnetRadius, config.magnetPullStrength);
		Vec3 v = drop.getDeltaMovement();
		boolean pulled = added && v.lengthSqr() > 0.0001D
				&& (center.x - drop.getX()) * v.x > 0.0D;
		drop.discard();

		check("㉑ 磁石·注册+吸附",
				registered && detect && pulled,
				"注册=" + registered + " 护甲检出=" + detect + " 掉落物被吸=" + pulled);
	}

	
static void checkCurseBurden(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> burden = ModEnchantments.byName(level, ModEnchantments.CURSE_OF_BURDEN);
		boolean registered = burden != null;

		boolean applied = false;
		boolean removed = false;
		ArmorStand stand = spawnArmorStand(level, new BlockPos(0, 90, 0));
		if (stand != null) {
			try {
				EnchantmentEffects.applyBurden(stand, 1, config);
				AttributeInstance speed = stand.getAttribute(Attributes.MOVEMENT_SPEED);
				applied = speed != null && speed.hasModifier(EnchantmentEffects.BURDEN_ID);

				EnchantmentEffects.applyBurden(stand, 0, config);
				removed = speed == null || !speed.hasModifier(EnchantmentEffects.BURDEN_ID);
			} finally {
				stand.discard();
			}
		}

		check("㉓ 负重诅咒·注册+移速减益",
				registered && applied && removed,
				"注册=" + registered + " 穿上挂减益=" + applied + " 脱下摘除=" + removed);
	}

	
static void checkCurseFrailty(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> frailty = ModEnchantments.byName(level, ModEnchantments.CURSE_OF_FRAILTY);
		boolean registered = frailty != null;

		boolean broke = false;
		boolean kept = false;
		if (registered) {
			double savedChance = config.curseFrailtyBreakChance;
			config.curseFrailtyBreakChance = 1.0D;
			ArmorStand stand = spawnArmorStand(level, new BlockPos(0, 90, 0));
			try {
				ItemStack cursedHelm = new ItemStack(Items.IRON_HELMET);
				setEnchant(cursedHelm, frailty);
				stand.setItemSlot(EquipmentSlot.HEAD, cursedHelm);
				EnchantmentEffects.procFrailty(stand, level, config);
				broke = stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty();

				// 对照：不带诅咒的头盔不会被碎
				stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				EnchantmentEffects.procFrailty(stand, level, config);
				kept = !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
			} finally {
				config.curseFrailtyBreakChance = savedChance;
				if (stand != null) {
					stand.discard();
				}
			}
		}

		check("㉔ 易碎诅咒·注册+护甲碎裂",
				registered && broke && kept,
				"注册=" + registered + " 带诅咒护甲被碎=" + broke + " 无诅咒护甲保留=" + kept);
	}

	
static void checkNewEnchantments(ServerLevel level, EnchantsConfig config) {
		boolean leechOk = ModEnchantments.byName(level, ModEnchantments.LEECH) != null;
		boolean swiftOk = ModEnchantments.byName(level, ModEnchantments.SWIFT) != null;
		boolean dreadOk = ModEnchantments.byName(level, ModEnchantments.DREAD) != null;

		// 疾风：穿上挂加速 modifier、脱下摘除
		boolean swiftApplied = false;
		boolean swiftRemoved = false;
		ArmorStand stand = spawnArmorStand(level, new BlockPos(0, 90, 0));
		if (stand != null) {
			try {
				EnchantmentEffects.applySwift(stand, 2);
				AttributeInstance speed = stand.getAttribute(Attributes.MOVEMENT_SPEED);
				swiftApplied = speed != null && speed.hasModifier(EnchantmentEffects.SWIFT_ID);
				EnchantmentEffects.applySwift(stand, 0);
				swiftRemoved = speed == null || !speed.hasModifier(EnchantmentEffects.SWIFT_ID);
			} finally {
				stand.discard();
			}
		}

		// 威压：直接实例化一只尸壳放进世界。位置刻意用 ⑲ 已激活的 (5,80,5) 区段附近 ——
		// 无玩家时 getEntities 对从未访问过的区段会返回空（见 README 已知限制）；
		// 生产环境里威压结算发生在玩家身边，区块必然激活，不受此影响。
		boolean dreadEffect = false;
		boolean zombieOk = false;
		int dreadHits = -1;
		boolean dreadSlowness = false;
		// 与 ⑲ 同一处地表（该区段已被 ⑲ 强制加载）：无玩家时 getEntities 对
		// 从未加载的区段会返回空，写死 (5,80,5) 在新区块上必然数不到实体。
		BlockPos dreadBase = level.getHeightmapPos(
				net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, new BlockPos(5, 64, 5));
		ArmorStand dreadStand = spawnArmorStand(level, dreadBase);
		EntityType<?> huskType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:husk"));
		if (dreadStand != null && huskType != null) {
			@SuppressWarnings("unchecked")
			EntityType<? extends Mob> husk = (EntityType<? extends Mob>) huskType;
			Mob zombie = husk.create(level, EntitySpawnReason.EVENT);
			if (zombie != null) {
				try {
					zombie.setPos(dreadBase.getX() + 1.5, dreadBase.getY() + 1.0, dreadBase.getZ() + 1.5); // 威压半径（6×2=12 格）内
					zombieOk = level.addFreshEntity(zombie);
					dreadHits = zombieOk ? EnchantmentEffects.dreadTick(level, dreadStand, 2, config) : -1;
					dreadSlowness = zombie.hasEffect(MobEffects.SLOWNESS);
					dreadEffect = dreadHits > 0 && dreadSlowness;
				} finally {
					zombie.discard();
				}
			}
			dreadStand.discard();
		}

		check("㉖ 新附魔·汲取/疾风/威压",
				leechOk && swiftOk && dreadOk && swiftApplied && swiftRemoved && dreadEffect,
				"注册 汲取=" + leechOk + " 疾风=" + swiftOk + " 威压=" + dreadOk
						+ "；疾风挂/摘=" + swiftApplied + "/" + swiftRemoved
						+ "；尸壳入世界=" + zombieOk + "；命中数=" + dreadHits
						+ "；缓开放上=" + dreadSlowness);
	}

	
static void checkEnchantLevelUp(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> shatter = ModEnchantments.byName(level, ModEnchantments.SHATTER);
		boolean registered = shatter != null;

		boolean ladder = false;
		boolean capped = false;
		if (registered) {
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			setEnchant(sword, shatter); // I 级

			int afterFirst = EnchantmentLevelUps.levelUp(sword, shatter);
			int afterSecond = EnchantmentLevelUps.levelUp(sword, shatter);
			ladder = afterFirst == 2 && afterSecond == 3
					&& ModEnchantments.getLevel(sword, shatter) == 3;

			int still = EnchantmentLevelUps.levelUp(sword, shatter);
			capped = still == 3 && ModEnchantments.getLevel(sword, shatter) == 3;
		}

		check("㉗ 击杀升级·只升不降+满级封顶",
				registered && ladder && capped,
				"注册=" + registered + " I→II→III 递进=" + ladder + " 满级(III)封顶=" + capped);
	}

	
static void checkUniversalLevelUp(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> sharpness = vanillaEnchant(level, "minecraft:sharpness");
		Holder<Enchantment> binding = vanillaEnchant(level, "minecraft:binding_curse");
		boolean registered = sharpness != null && binding != null;

		boolean vanillaLadder = false;
		boolean curseExcluded = false;
		if (registered) {
			// 锋利（max 5）：III → IV 可以升
			ItemStack sword = new ItemStack(Items.IRON_SWORD);
			ItemEnchantments.Mutable ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			ench.set(sharpness, 3);
			EnchantmentHelper.setEnchantments(sword, ench.toImmutable());
			vanillaLadder = EnchantmentLevelUps.levelUp(sword, sharpness) == 4;

			// 绑定诅咒：不参与升级（等级不变）
			ItemStack helm = new ItemStack(Items.IRON_HELMET);
			ItemEnchantments.Mutable curse = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			curse.set(binding, 1);
			EnchantmentHelper.setEnchantments(helm, curse.toImmutable());
			curseExcluded = EnchantmentLevelUps.levelUp(helm, binding) == 1;
		}

		boolean chanceTuned = config.killEnchantLevelUpChance >= 0.14D
				&& config.killEnchantLevelUpChance <= 0.16D;

		check("㉞ 升级全附魔·含原版+排除诅咒+15%",
				registered && vanillaLadder && curseExcluded && chanceTuned,
				"原版锋利 III→IV=" + vanillaLadder + " 诅咒排除=" + curseExcluded
						+ " 概率=15%：" + chanceTuned);
	}

	
static void checkLibrarian(ServerLevel level, EnchantsConfig config) {
		net.minecraft.world.item.trading.MerchantOffers offers =
				LibrarianTrades.generateOffers(level, level.getRandom(), 3);

		boolean sizeOk = offers.size() == 3;
		boolean topLevels = true;
		boolean costOk = true;

		for (int i = 0; i < offers.size(); i++) {
			var offer = offers.get(i);
			ItemStack sell = offer.getResult();
			// 附魔书走 STORED_ENCHANTMENTS（铁砧实际读取的组件），兼容读 ENCHANTMENTS
			ItemEnchantments ench = sell.getOrDefault(DataComponents.STORED_ENCHANTMENTS,
					sell.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
			if (ench.isEmpty()) {
				topLevels = false;
				break;
			}
			for (Holder<Enchantment> h : ench.keySet()) {
				if (ench.getLevel(h) != h.value().getMaxLevel()) {
					topLevels = false; // 必须是顶级
				}
			}
			// 代价：随机物品，数量 1~3
			int costCount = offer.getCostA().getCount();
			if (costCount < 1 || costCount > 3) {
				costOk = false;
			}
		}

		check("㊱ 图书管理员·随机顶级附魔书交易",
				sizeOk && topLevels && costOk,
				"3 笔交易=" + sizeOk + " 全为顶级附魔书=" + topLevels
						+ " 代价物品数量 1~3=" + costOk);
	}

	/** 自检辅助：统计半径内的亡灵生物数量（臭脚自检用）。 */
	private static int countUndead(ServerLevel level, BlockPos center, double r) {
		AABB box = new AABB(center.getX() - r, center.getY() - r, center.getZ() - r,
				center.getX() + r, center.getY() + r, center.getZ() + r);
		return level.getEntities((Entity) null, box,
				e -> EnchantmentEffects.isUndead(e, level)).size();
	}
	// ------------------------------------------------------------ v1.12.0 附魔突破 + 全局事件

	/** ⑯：三个新附魔能从注册表解析出来，且 hasEnchantment / isWeaponOrTool 判断正确。 */
	public static void checkModEnchantments(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> thunder = ModEnchantments.byName(level, "thunderous");
		Holder<Enchantment> stinky = ModEnchantments.byName(level, "stinky_feet");
		Holder<Enchantment> shatter = ModEnchantments.byName(level, "shatter");
		boolean resolved = thunder != null && stinky != null && shatter != null;

		ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
		setEnchant(helmet, thunder);
		ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
		setEnchant(boots, stinky);
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		setEnchant(sword, shatter);

		boolean detect = ModEnchantments.hasEnchantment(helmet, thunder)
				&& ModEnchantments.hasEnchantment(boots, stinky)
				&& ModEnchantments.hasEnchantment(sword, shatter);
		boolean weaponTool = ModEnchantments.isWeaponOrTool(Items.DIAMOND_SWORD)
				&& ModEnchantments.isWeaponOrTool(Items.IRON_PICKAXE)
				&& !ModEnchantments.isWeaponOrTool(Items.DIAMOND_HELMET)
				&& !ModEnchantments.isWeaponOrTool(Items.APPLE);

		check("⑯ 三个新附魔注册+检测", resolved && detect && weaponTool,
				"雷霆=" + (thunder != null) + " 臭脚=" + (stinky != null) + " 碎裂=" + (shatter != null)
						+ "；检测命中=" + detect + "；武器/工具判定=" + weaponTool);
	}

	public static void setEnchant(ItemStack stack, Holder<Enchantment> holder) {
		if (holder == null) {
			return;
		}
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		m.set(holder, 1);
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
	}

		public static void checkThunderLightning(ServerLevel level, EnchantsConfig config) {
		EntityType<?> lbType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:lightning_bolt"));
		boolean typeOk = lbType instanceof EntityType;
		boolean added = false;
		if (typeOk) {
			@SuppressWarnings("unchecked")
			EntityType<? extends LightningBolt> lb = (EntityType<? extends LightningBolt>) lbType;
			LightningBolt bolt = new LightningBolt(lb, level);
			BlockPos spawn = new BlockPos(0, 90, 0);
			bolt.setPos(spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5);
			added = level.addFreshEntity(bolt);
		}

		check("⑱ 雷霆万钧·雷击生成", typeOk && added,
				"闪电实体类型存在=" + typeOk + "；addFreshEntity=" + added);
	}


	/** ㉒：贪婪已注册，且 randomLootOne 能稳定供给随机物品（连抽 20 次至少 15 次非空）。 */
	public static void checkGreed(ServerLevel level, EnchantsConfig config) {
		Holder<Enchantment> greed = ModEnchantments.byName(level, ModEnchantments.GREED);
		boolean registered = greed != null;

		BlockPos pos = new BlockPos(0, 90, 0);
		int nonEmpty = 0;
		for (int i = 0; i < 20; i++) {
			if (!LootSupply.randomItem(level, pos, level.getRandom()).isEmpty()) {
				nonEmpty++;
			}
		}

		check("㉒ 贪婪·注册+额外掉落",
				registered && nonEmpty >= 15,
				"注册=" + registered + "；连抽 20 次非空 " + nonEmpty + " 次（应 ≥15）");
	}

}
