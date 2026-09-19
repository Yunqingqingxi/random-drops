package com.randomdrops;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LightningBolt;

import java.util.ArrayList;
import java.util.List;

/**
 * 「附魔突破」七个附魔的运行期效果。
 *
 * <ul>
 *   <li><b>雷霆万钧</b>：头盔附魔；穿戴者每隔数秒令周身两区块内的生物被雷击（雷雨天频率更高）。</li>
 *   <li><b>臭脚</b>：鞋附魔；穿戴时周边花草枯萎、附近玩家获得反胃、亡灵被吸引且更易生成。</li>
 *   <li><b>碎裂</b>：武器 / 工具附魔；攻击或挖掘时有概率触发 —— 正面可秒杀/秒破并偶发自损一件装备，
 *       负面则碎裂全身装备与所用武器。头盔带雷霆时碎裂秒杀落雷（<b>雷碎</b>组合）。</li>
 *   <li><b>磁石</b>（v1.13）：任意护甲；穿戴时定期把半径内掉落物吸向自己（跳过拾取延迟中的物品）。</li>
 *   <li><b>贪婪</b>（v1.13）：挖掘类工具；挖方块时按概率额外随机掉一件（走随机掉落池）。</li>
 *   <li><b>负重诅咒</b>（v1.13）：任意护甲；穿戴时移速降低（属性修饰符，脱下即恢复）。</li>
 *   <li><b>易碎诅咒</b>（v1.13）：任意护甲；受到伤害时按概率一件护甲直接碎裂消失。</li>
 * </ul>
 *
 * <p>雷霆 / 臭脚 / 磁石 / 负重由服务器 tick 驱动（{@link #tick}），
 * 碎裂 / 贪婪 / 易碎由攻击 / 破坏方块的 Fabric 事件驱动。
 */
public final class EnchantmentEffects {
	private EnchantmentEffects() {
	}

	/** 注册碎裂 / 贪婪 / 易碎的事件钩子（雷霆 / 臭脚 / 磁石 / 负重的 tick 在 {@link #tick} 里驱动）。 */
	public static void register() {
		// 攻击：持碎裂武器打中目标时触发；被打者穿易碎诅咒时按概率碎一件护甲
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, damage, newHealth, blocked) -> {
			RandomDropsConfig config = RandomDropsConfig.get();
			if (!config.enableEnchantmentBreakthrough) {
				return;
			}

			// 易碎诅咒：受伤（任意来源）后结算，死了就不碎了
			if (entity instanceof LivingEntity victim && victim.isAlive()) {
				procFrailty(victim, (ServerLevel) victim.level(), config);
			}

			if (!(source.getEntity() instanceof LivingEntity attacker)) {
				return;
			}

			ServerLevel level = (ServerLevel) attacker.level();
			Holder<Enchantment> shatter = ModEnchantments.shatter(level);

			if (ModEnchantments.hasEnchantment(attacker.getMainHandItem(), shatter)) {
				procShatter(level, attacker, entity, true, level.getRandom());
			}
		});

		// 挖掘：持碎裂工具破坏方块时触发；持贪婪工具时按概率额外随机掉一件
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			RandomDropsConfig config = RandomDropsConfig.get();
			if (!config.enableEnchantmentBreakthrough) {
				return;
			}

			ServerLevel slevel = (ServerLevel) level;
			Holder<Enchantment> shatter = ModEnchantments.shatter(slevel);

			if (ModEnchantments.hasEnchantment(player.getMainHandItem(), shatter)) {
				procShatter(slevel, player, null, false, slevel.getRandom());
			}

			if (!(player instanceof ServerPlayer serverPlayer)) {
				return; // 客户端侧的伪造调用不处理
			}

			Holder<Enchantment> greed = ModEnchantments.greed(slevel);
			if (config.enableGreed && ModEnchantments.hasEnchantment(player.getMainHandItem(), greed)) {
				procGreed(slevel, serverPlayer, pos, slevel.getRandom());
			}
		});
	}

	/** 关服时无需清理持久状态（效果本身无状态），保留空方法以便统一接线。 */
	public static void reset() {
	}

	/** 每个游戏刻调用：为在线玩家结算雷霆万钧 / 臭脚 / 磁石 / 负重的效果。 */
	public static void tick(MinecraftServer server) {
		RandomDropsConfig config = RandomDropsConfig.get();
		if (!config.enableEnchantmentBreakthrough) {
			// 总开关关闭：把可能残留的负重 modifier 摘干净（瞬态不进存档，但在线玩家会带着）
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					applyBurden(player, false, config);
				}
			}
			return;
		}

		int tick = server.getTickCount();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();

			Holder<Enchantment> thunder = ModEnchantments.thunderous(level);
			if (ModEnchantments.hasEnchantment(player.getItemBySlot(EquipmentSlot.HEAD), thunder)) {
				int interval = level.isThundering()
						? Math.max(1, config.thunderStormIntervalTicks)
						: Math.max(1, config.thunderIntervalTicks);
				if (tick % interval == 0) {
					strikeAround(level, player, config);
				}
			}

			Holder<Enchantment> stinky = ModEnchantments.stinkyFeet(level);
			if (ModEnchantments.hasEnchantment(player.getItemBySlot(EquipmentSlot.FEET), stinky)) {
				if (config.stinkyTickInterval >= 1 && tick % config.stinkyTickInterval == 0) {
					stinkyFeetTick(level, player, config);
				}
			}

			// 磁石：任意护甲部位带磁石即生效
			if (config.enableMagnet
					&& anyArmorHas(player, ModEnchantments.magnet(level))
					&& tick % Math.max(1, config.magnetIntervalTicks) == 0) {
				magnetPull(level, player, config);
			}

			// 负重诅咒：每 20 刻幂等结算（穿上挂减速、脱下摘除），modifier 是瞬态的不进存档
			if (tick % 20 == 0) {
				boolean hasBurden = config.enableCursedEnchantments
						&& anyArmorHas(player, ModEnchantments.curseOfBurden(level));
				applyBurden(player, hasBurden, config);
			}
		}
	}

	// ------------------------------------------------------------ 雷霆万钧

	/** 在指定坐标落下一道真实雷击（会伤害该处实体）。 */
	static void strikeLightning(ServerLevel level, double x, double y, double z) {
		EntityType<?> lbType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:lightning_bolt"));
		if (!(lbType instanceof EntityType)) {
			return;
		}

		@SuppressWarnings("unchecked")
		EntityType<? extends LightningBolt> type = (EntityType<? extends LightningBolt>) lbType;
		LightningBolt bolt = new LightningBolt(type, level);
		bolt.setPos(x, y, z);
		bolt.setVisualOnly(false);
		level.addFreshEntity(bolt);
	}

	private static void strikeAround(ServerLevel level, ServerPlayer wearer, RandomDropsConfig config) {
		double r = config.thunderRadius;
		AABB box = new AABB(
				wearer.getX() - r, wearer.getY() - r, wearer.getZ() - r,
				wearer.getX() + r, wearer.getY() + r, wearer.getZ() + r);

		List<Entity> nearby = level.getEntities(wearer, box,
				e -> e instanceof LivingEntity && !(e instanceof Player));

		int cap = 4;
		int n = 0;
		for (Entity e : nearby) {
			if (n++ >= cap) {
				break;
			}
			strikeLightning(level, e.getX(), e.getY() + 0.5, e.getZ());
		}
	}

	// ------------------------------------------------------------ 臭脚

	private static boolean isPlant(BlockState st) {
		return st.is(BlockTags.FLOWERS, s -> true)
				|| st.is(BlockTags.SMALL_FLOWERS, s -> true)
				|| st.getBlock() == Blocks.TALL_GRASS
				|| st.getBlock() == Blocks.FERN;
	}

	private static void stinkyFeetTick(ServerLevel level, ServerPlayer wearer, RandomDropsConfig config) {
		double r = config.stinkyRadius;
		witherPlants(level, wearer.blockPosition(), r);

		AABB box = new AABB(
				wearer.getX() - r, wearer.getY() - r, wearer.getZ() - r,
				wearer.getX() + r, wearer.getY() + r, wearer.getZ() + r);

		// 附近玩家获得反胃（离开范围后不再续期，自然消失）
		for (Entity e : level.getEntities(wearer, box, e -> e instanceof ServerPlayer && e != wearer)) {
			((ServerPlayer) e).addEffect(new MobEffectInstance(MobEffects.NAUSEA,
					config.stinkyNauseaSeconds * 20, 0));
		}

		// 亡灵被吸引（对非中立亡灵无效 —— 它们本就不是玩家，不受反胃影响，只被吸引）
		for (Entity e : level.getEntities(wearer, box,
				e -> e instanceof Mob && isUndead(e, level))) {
			((Mob) e).setTarget(wearer);
		}

		// 隐藏 buff：穿戴者附近更容易生成亡灵
		int nearbyUndead = level.getEntities(wearer, box,
				e -> isUndead(e, level)).size();
		if (nearbyUndead < config.stinkyUndeadCap
				&& level.getRandom().nextDouble() < config.stinkyUndeadSpawnChance) {
			spawnUndeadNear(level, wearer.blockPosition());
		}
	}

	/** 让以 center 为中心、radius 为半径范围内的花草枯萎、草方块变泥土（自检可独立调用）。 */
	static void witherPlants(ServerLevel level, BlockPos center, double radius) {
		int ri = (int) Math.ceil(radius);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos p = center.offset(dx, dy, dz);
					BlockState st = level.getBlockState(p);
					if (st.getBlock() == Blocks.GRASS_BLOCK) {
						level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
					} else if (isPlant(st)) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/** 判断实体是否为「亡灵」标签成员（26.2 的 EntityType 无 is(TagKey) 便捷方法，走 registry 查 tag）。 */
	public static boolean isUndead(Entity e, Level level) {
		Registry<EntityType<?>> registry = level.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE);
		EntityType<?> type = e.getType();
		for (Holder<EntityType<?>> h : registry.getTagOrEmpty(EntityTypeTags.UNDEAD)) {
			if (h.value().equals(type)) {
				return true;
			}
		}
		return false;
	}

	public static void spawnUndeadNear(ServerLevel level, BlockPos pos) {
		String[] ids = {
				"minecraft:zombie", "minecraft:skeleton", "minecraft:husk",
				"minecraft:drowned", "minecraft:zombie_villager", "minecraft:zombie_piglin"
		};
		String id = ids[level.getRandom().nextInt(ids.length)];
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
		if (type == null) {
			return;
		}

		BlockPos p = pos.offset(
				(int) (level.getRandom().nextDouble() * 6.0D - 3.0D), 2,
				(int) (level.getRandom().nextDouble() * 6.0D - 3.0D));
		type.spawn(level, p, net.minecraft.world.entity.EntitySpawnReason.EVENT);
	}

	// ------------------------------------------------------------ 磁石

	/** 四件护甲任意一件带有指定附魔。 */
	private static boolean anyArmorHas(LivingEntity entity, Holder<Enchantment> holder) {
		if (holder == null) {
			return false;
		}

		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
		}) {
			if (ModEnchantments.hasEnchantment(entity.getItemBySlot(slot), holder)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 把半径内的掉落物吸向穿戴者。
	 *
	 * <p>两道豁免：① {@code hasPickUpDelay()} 的物品（玩家自己 Q 丢的东西默认有 40 刻
	 * 拾取延迟）不吸 —— 否则刚扔出去的垃圾会立刻飞回手上；② 已经贴脸（&lt;0.5 格）的
	 * 不推 —— 让它自然进入拾取距离，避免速度来回抖。
	 */
	private static void magnetPull(ServerLevel level, ServerPlayer player, RandomDropsConfig config) {
		magnetPullAt(level, player, new Vec3(player.getX(), player.getY() + 0.5D, player.getZ()),
				config.magnetRadius, config.magnetPullStrength);
	}

	/** 磁石核心：把以 center 为中心、radius 半径内的掉落物吸向 center（自检可独立调用）。 */
	static void magnetPullAt(ServerLevel level, Entity exclude, Vec3 center,
			double radius, double strength) {
		AABB box = new AABB(
				center.x - radius, center.y - radius, center.z - radius,
				center.x + radius, center.y + radius, center.z + radius);

		for (Entity e : level.getEntities(exclude, box, x -> x instanceof ItemEntity)) {
			ItemEntity item = (ItemEntity) e;
			if (item.hasPickUpDelay() || item.isRemoved()) {
				continue;
			}

			Vec3 toCenter = new Vec3(
					center.x - item.getX(),
					center.y - item.getY(),
					center.z - item.getZ());
			if (toCenter.length() < 0.5D) {
				continue;
			}

			Vec3 pull = toCenter.normalize().scale(strength);
			item.setDeltaMovement(pull.x, pull.y + 0.08D, pull.z);
		}
	}

	// ------------------------------------------------------------ 诅咒系

	/** 负重诅咒的属性修饰符 key（瞬态 modifier，不进存档）。 */
	static final Identifier BURDEN_ID =
			Identifier.fromNamespaceAndPath(RandomDrops.MOD_ID, "curse_of_burden");

	/** 穿着负重诅咒：移速乘 -penalty；脱下：摘掉 modifier。幂等，可每秒重复结算。 */
	static void applyBurden(LivingEntity entity, boolean hasBurden, RandomDropsConfig config) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}

		if (hasBurden) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(BURDEN_ID,
					-config.curseBurdenSpeedPenalty, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			speed.removeModifier(BURDEN_ID);
		}
	}

	/**
	 * 易碎诅咒：受到伤害后按概率让一件非空护甲直接碎裂消失。
	 *
	 * <p>刻意不放大伤害数值（那需要在伤害管线里插手，容易与其它模组打架），
	 * 而是「装备本身易碎」—— 挨打一次就有概率少一件，惩罚直观且可预测。
	 */
	static void procFrailty(LivingEntity victim, ServerLevel level, RandomDropsConfig config) {
		if (!config.enableCursedEnchantments || config.curseFrailtyBreakChance <= 0.0D) {
			return;
		}

		Holder<Enchantment> frailty = ModEnchantments.curseOfFrailty(level);
		if (!anyArmorHas(victim, frailty)) {
			return;
		}

		if (level.getRandom().nextDouble() >= config.curseFrailtyBreakChance) {
			return;
		}

		// 只从「真的穿着的」护甲里挑一件碎 —— 选中空槽就放弃等于白挨一下打
		List<EquipmentSlot> occupied = new ArrayList<>();
		for (EquipmentSlot s : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
		}) {
			if (!victim.getItemBySlot(s).isEmpty()) {
				occupied.add(s);
			}
		}
		if (occupied.isEmpty()) {
			return;
		}

		EquipmentSlot slot = occupied.get(level.getRandom().nextInt(occupied.size()));
		victim.setItemSlot(slot, ItemStack.EMPTY);
		notify(victim, "§c[易碎诅咒] 你的" + slotName(slot) + "应声碎裂！");
	}

	// ------------------------------------------------------------ 贪婪

	/**
	 * 贪婪触发：从随机掉落池额外抽一件掉在挖掘点。
	 *
	 * <p>走 {@link DropRandomizer#randomLootOne}（含维度 / 群系池与刷怪蛋、分层过滤），
	 * 所以贪婪给的东西和「挖一下随机掉」是同一套规则 —— 附魔书 / 药水都带真实数据。
	 */
	private static void procGreed(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
		RandomDropsConfig config = RandomDropsConfig.get();
		if (config.greedExtraChance <= 0.0D
				|| random.nextDouble() >= config.greedExtraChance) {
			return;
		}

		ItemStack extra = DropRandomizer.randomLootOne(level, pos, random);
		if (!extra.isEmpty()) {
			Block.popResource(level, pos, extra);
			notify(player, "§6[贪婪] " + extra.getHoverName().getString() + " ×" + extra.getCount()
					+ " 从方块里蹦了出来！");
		}
	}

	// ------------------------------------------------------------ 碎裂

	private static void procShatter(ServerLevel level, LivingEntity user, LivingEntity target,
			boolean isAttack, RandomSource random) {
		RandomDropsConfig config = RandomDropsConfig.get();
		if (random.nextDouble() >= config.shatterProcChance) {
			return;
		}

		if (random.nextDouble() < config.shatterNegativeChance) {
			// 负面：碎裂全身装备 + 所用武器 / 工具
			for (EquipmentSlot slot : new EquipmentSlot[] {
					EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
					EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
			}) {
				user.setItemSlot(slot, ItemStack.EMPTY);
			}
			notify(user, "§c[碎裂·厄运] 全身装备与武器尽数碎裂！");
			return;
		}

		// 正面：秒杀 / 秒破
		if (isAttack && target != null) {
			target.kill(level);
			notify(user, "§6[碎裂·秒杀] 目标被瞬间粉碎！");

			// 雷碎组合：头盔带雷霆万钧时，秒杀落雷 —— 落雷本身还能波及周围
			Holder<Enchantment> thunder = ModEnchantments.thunderous(level);
			if (ModEnchantments.hasEnchantment(user.getItemBySlot(EquipmentSlot.HEAD), thunder)) {
				strikeLightning(level, target.getX(), target.getY(), target.getZ());
				notify(user, "§e[雷碎] 雷霆应声而至！");
			}
		} else {
			notify(user, "§6[碎裂·秒破] 方块应声碎裂！");
		}

		// 正面也可能随机碎掉自己的一件装备（含武器本身）
		if (random.nextDouble() < config.shatterSelfShatterChance) {
			EquipmentSlot[] slots = {
					EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
					EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
			};
			EquipmentSlot slot = slots[random.nextInt(slots.length)];
			user.setItemSlot(slot, ItemStack.EMPTY);
			notify(user, "§e[碎裂] 你的" + slotName(slot) + "碎裂消失了！");
		}
	}

	private static String slotName(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "头盔";
			case CHEST -> "胸甲";
			case LEGS -> "护腿";
			case FEET -> "靴子";
			case MAINHAND -> "主手武器/工具";
			case OFFHAND -> "副手";
			default -> "装备";
		};
	}

	private static void notify(LivingEntity user, String text) {
		if (user instanceof ServerPlayer sp) {
			sp.sendSystemMessage(Component.literal(text));
		}
	}
}
