package com.randomdrops;

import java.util.List;
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
 * 开服时就会跑一遍；跑完改回 0 即可关闭。也可以用 {@code /randomdrops selftest} 随时手动跑。
 *
 * <p>自检期间 {@link SessionStats} 是暂停的 —— 几千次假掉落不该污染「本局战绩」。
 */
public final class SelfTest {
	private SelfTest() {
	}

	/** 自动自检只跑一次（同进程重开服也只跑一次，避免刷屏）。 */
	private static final AtomicBoolean AUTO_RAN = new AtomicBoolean();

	private static int passed;
	private static int failed;

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (RandomDropsConfig.get().selfTestRolls <= 0) {
				return;
			}

			if (AUTO_RAN.compareAndSet(false, true)) {
				run(server);
			}
		});
	}

	/** 跑一遍全部自检，返回失败条数（0 = 全过）。 */
	public static int run(MinecraftServer server) {
		RandomDropsConfig config = RandomDropsConfig.get();
		long rolls = Math.max(1L, config.selfTestRolls);

		passed = 0;
		failed = 0;

		RandomDrops.LOGGER.info("[random-drops] ===== 自检开始（每项掷 {} 次）=====", rolls);

		ServerLevel level = server.overworld();
		BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(0, 64, 0));

		// 自检期间的假掉落不进「本局战绩」
		SessionStats.pause();
		DropTally.pause();

		// 把会刷屏的播报先关掉，逐项验证广播时再单独打开
		boolean savedJackpotBroadcast = config.jackpotBroadcast;
		boolean savedFinaleBroadcast = config.finaleBroadcast;
		boolean savedFinaleTitle = config.finaleTitle;
		boolean savedEliteBroadcast = config.eliteBroadcast;
		boolean savedUltimateBroadcast = config.ultimateBroadcast;

		config.jackpotBroadcast = false;
		config.finaleBroadcast = false;
		config.finaleTitle = false;
		config.eliteBroadcast = false;
		config.ultimateBroadcast = false;

		try {
			DropRandomizer.resetPools();

			checkItemCountRange(level, pos, config, rolls);
			checkMobDropNeverEmpty(level, pos, config, rolls);
			checkSpawnedMobStun(level, pos, config);
			checkJackpot(level, pos, config, rolls);
			checkPity(level, config);
			checkRareDropBroadcast(level, pos, config);
			checkFinale(level, pos, config);
			checkProgression(level, config);
			checkDimensionAndBiome(server, level, pos, config, rolls);
			checkEliteMobs(level, pos, config);
			checkKillEffects(level, pos, config);

			// ---- v1.11 ----
			checkSpawnerEggs(level, pos, config);
			checkTntIgnition(level, pos, config);
			checkDropMerging(level, pos, config);
			checkEventsAndLimbInjury(level, pos, config);
			checkTieredDrops(server, level, pos, config);

			// ---- v1.11.1：特殊物品必须带真实数据 ----
			checkSpecialItems(level, config);

			// ---- v1.12.0：附魔突破 + 全局事件 ----
			checkModEnchantments(level, config);
			checkShatterApply(level, config);
			checkThunderLightning(level, config);
			checkStinkyFeet(server, level, config);
			checkGlobalEvents(server, level, config);

			// ---- v1.13.0：附魔突破二期（磁石 / 贪婪 / 诅咒系） ----
			checkMagnet(level, config);
			checkGreed(level, config);
			checkCurseBurden(level, config);
			checkCurseFrailty(level, config);
		} catch (Throwable error) {
			failed++;
			RandomDrops.LOGGER.error("[random-drops] 自检过程中抛异常", error);
		} finally {
			config.jackpotBroadcast = savedJackpotBroadcast;
			config.finaleBroadcast = savedFinaleBroadcast;
			config.finaleTitle = savedFinaleTitle;
			config.eliteBroadcast = savedEliteBroadcast;
			config.ultimateBroadcast = savedUltimateBroadcast;

			// 让通关结算恢复到「还没发生过」，免得自检把正式的那次吃掉
			Finale.resetForTest();

			SessionStats.resume();
			DropTally.resume();
		}

		if (failed == 0) {
			RandomDrops.LOGGER.info("[random-drops] ===== 自检结束：{} 项全部通过 =====", passed);
		} else {
			RandomDrops.LOGGER.error("[random-drops] ===== 自检结束：通过 {} 项，失败 {} 项 =====", passed, failed);
		}

		return failed;
	}

	// ------------------------------------------------------------ ① 三个劝退点

	/** ①-a：掉出物品的数量必须落在 1~8（且不超过堆叠上限）。 */
	private static void checkItemCountRange(ServerLevel level, BlockPos pos, RandomDropsConfig config, long rolls) {
		int min = config.itemCountMin;
		int max = config.itemCountMax;
		int bound = Math.max(min, max);

		double savedMobChance = config.mobChance;
		config.mobChance = 0.0D;

		int seenLow = Integer.MAX_VALUE;
		int seenHigh = 0;
		int samples = 0;
		boolean stackCapRespected = true;

		try {
			for (long i = 0; i < rolls; i++) {
				for (ItemStack stack : DropRandomizer.rollBlockDrop(level, pos, null)) {
					int count = stack.getCount();
					seenLow = Math.min(seenLow, count);
					seenHigh = Math.max(seenHigh, count);
					samples++;

					if (count > stack.getMaxStackSize()) {
						stackCapRespected = false;
					}
				}
			}
		} finally {
			config.mobChance = savedMobChance;
		}

		boolean configOk = min >= 1 && max >= min;
		boolean observedOk = samples > 0 && seenLow >= 1 && seenHigh <= bound && stackCapRespected;

		check("①-a 物品数量范围", configOk && observedOk,
				"配置 " + min + "~" + max + "；实测 " + samples + " 件，落在 " + seenLow + "~" + seenHigh
						+ "；未超堆叠上限=" + stackCapRespected);
	}

	/** ①-b：生物掉落永远不该「什么都不掉」。 */
	private static void checkMobDropNeverEmpty(ServerLevel level, BlockPos pos, RandomDropsConfig config, long rolls) {
		boolean saved = config.allowNothingOnMobDrop;
		double savedMobChance = config.mobChance;

		config.allowNothingOnMobDrop = false;
		config.mobChance = 0.0D;

		long emptyBefore = DropRandomizer.nothingRolls();
		long itemsBefore = SessionStats.itemsGiven();

		try {
			for (long i = 0; i < rolls; i++) {
				DropRandomizer.applyMobDrop(level, pos, null);
			}
		} finally {
			config.allowNothingOnMobDrop = saved;
			config.mobChance = savedMobChance;
		}

		long emptyDelta = DropRandomizer.nothingRolls() - emptyBefore;

		check("①-b 生物掉宝不出空手", emptyDelta == 0,
				"配置 allowNothingOnMobDrop=false；掷 " + rolls + " 次，空手 " + emptyDelta + " 次（应为 0）");
	}

	/** ①-c：爆出来的生物要有 40 刻落地僵直，并且到点自动解锁。 */
	private static void checkSpawnedMobStun(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		int ticks = config.spawnedMobStunTicks;
		Mob mob = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);

		if (mob == null) {
			check("①-c 落地僵直", false, "无法在 " + pos.toShortString() + " 生成测试生物");
			return;
		}

		boolean stunned;
		boolean released;

		try {
			MobStun.stun(mob, ticks);
			stunned = mob.isNoAi();

			for (int i = 0; i < ticks; i++) {
				MobStun.tick();
			}

			released = !mob.isNoAi();
		} finally {
			mob.discard();
		}

		check("①-c 落地僵直", ticks == 40 && stunned && released,
				"配置 " + ticks + " 刻（应为 40）；爆出时 isNoAi=" + stunned + "、推进 " + ticks
						+ " 刻后解锁=" + released);
	}

	// ------------------------------------------------------------ ② 暴击大爆

	/** ②：暴击概率拉到 100% 时，掉出来的必须全是宝藏池里的东西。 */
	private static void checkJackpot(ServerLevel level, BlockPos pos, RandomDropsConfig config, long rolls) {
		double savedChance = config.jackpotChance;
		boolean savedEnabled = config.enableJackpot;
		double savedMobChance = config.mobChance;

		config.jackpotChance = 1.0D;
		config.enableJackpot = true;
		config.mobChance = 0.0D;

		long jackpotsBefore = DropRandomizer.jackpots();
		int fromPool = 0;
		int notFromPool = 0;
		int empty = 0;

		try {
			for (long i = 0; i < rolls; i++) {
				List<ItemStack> drops = DropRandomizer.rollBlockDrop(level, pos, null);

				if (drops.isEmpty()) {
					empty++;
					continue;
				}

				if (DropRandomizer.isJackpotItem(drops.get(0).getItem())) {
					fromPool++;
				} else {
					notFromPool++;
				}
			}
		} finally {
			config.jackpotChance = savedChance;
			config.enableJackpot = savedEnabled;
			config.mobChance = savedMobChance;
		}

		long jackpotDelta = DropRandomizer.jackpots() - jackpotsBefore;
		boolean poolNotEmpty = !DropRandomizer.itemPoolSnapshot().isEmpty();

		check("② 暴击大爆", poolNotEmpty && fromPool == rolls && notFromPool == 0 && jackpotDelta == rolls,
				"概率临时拉到 100%，掷 " + rolls + " 次：来自宝藏池 " + fromPool + " 次 / 非宝藏 " + notFromPool
						+ " 次 / 空 " + empty + " 次；暴击计数 +" + jackpotDelta + "（应全部来自宝藏池）");
	}

	// ------------------------------------------------------------ ③ 保底可见化

	/** ③：保底按阈值触发，且反馈路径（粒子/音效/动作栏）不会抛异常。 */
	private static void checkPity(ServerLevel level, RandomDropsConfig config) {
		int threshold = Math.max(1, config.pityThreshold);
		UUID probe = UUID.randomUUID();
		int triggerAt = -1;

		try {
			for (int i = 1; i <= threshold + 3; i++) {
				if (DropRandomizer.advancePity(probe, "item:minecraft:dirt", threshold)) {
					triggerAt = i;
					break;
				}
			}
		} finally {
			DropRandomizer.forgetPity(probe);
		}

		// 反馈路径：cause 为 null 时必须安全（爆炸等非玩家路径真的会传 null）
		boolean feedbackSafe = true;

		try {
			Feedback.pity(level, BlockPos.ZERO, new ItemStack(Items.DIRT), null);
		} catch (Throwable error) {
			feedbackSafe = false;
			RandomDrops.LOGGER.error("[random-drops] 保底反馈在 cause=null 时抛异常", error);
		}

		check("③ 保底可见化", triggerAt == threshold && feedbackSafe && config.showPityFeedback,
				"阈值 " + threshold + " → 第 " + triggerAt + " 次触发（应为 " + threshold + "）；"
						+ "cause=null 时反馈安全=" + feedbackSafe + "；可见反馈开关=" + config.showPityFeedback);
	}

	// ------------------------------------------------------------ ④ 稀有掉落广播

	/** ④：稀有物品判定正确，且广播路径真的能跑通。 */
	private static void checkRareDropBroadcast(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		boolean saved = config.rareDropBroadcast;
		Item elytra = BuiltInRegistries.ITEM.getValue(Identifier.tryParse("minecraft:elytra"));

		boolean rareDetected = elytra != null && elytra != Items.AIR && DropRandomizer.isRareItem(elytra);
		boolean commonRejected = !DropRandomizer.isRareItem(Items.DIRT);
		boolean broadcastSafe = true;

		try {
			// 打开开关，真的走一遍广播（无人在线，只会进日志）
			config.rareDropBroadcast = true;
			Feedback.rareDrop(level, pos, new ItemStack(Items.ELYTRA), null);

			// cause=null 的署名兜底也要安全
			Feedback.rareDrop(level, pos, new ItemStack(Items.DIAMOND), null);
		} catch (Throwable error) {
			broadcastSafe = false;
			RandomDrops.LOGGER.error("[random-drops] 稀有掉落广播抛异常", error);
		} finally {
			config.rareDropBroadcast = saved;
		}

		check("④ 稀有掉落广播", rareDetected && commonRejected && broadcastSafe,
				"鞘翅算稀有=" + rareDetected + "、泥土不算稀有=" + commonRejected + "、广播执行安全=" + broadcastSafe);
	}

	// ------------------------------------------------------------ ⑤ 通关结算

	/** ⑤：结算流程完整跑一遍 —— 宝藏雨数量对得上，统计能出报表。 */
	private static void checkFinale(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		Finale.resetForTest();

		int dropped;
		List<String> report;

		try {
			dropped = Finale.fireForTest(level, pos, null);
			report = SessionStats.report();
		} finally {
			Finale.resetForTest();
		}

		int wanted = config.finaleTreasureCount;
		boolean countOk = dropped == wanted;
		boolean reportOk = report != null && report.size() >= 5;
		boolean flagOk = config.enableFinale;

		check("⑤ 末影龙通关结算", countOk && reportOk && flagOk,
				"宝藏雨 " + dropped + " 件（期望 " + wanted + "）；统计报表 " + (report == null ? 0 : report.size())
						+ " 行；结算开关=" + flagOk);
	}

	// ------------------------------------------------------------ ⑥ 进度分档

	/** ⑥：分档逻辑可判定，且「没有世界」时必须退化成 1.0 倍（自检不能被放大）。 */
	private static void checkProgression(ServerLevel level, RandomDropsConfig config) {
		Progression.Tier live = Progression.tier(level, config);
		Progression.Tier headless = Progression.tier(null, config);
		double liveMultiplier = Progression.jackpotMultiplier(level, config);
		double headlessMultiplier = Progression.jackpotMultiplier(null, config);

		// 用纯函数把三段分界真的走一遍 —— 服务器刚开时世界时间永远是 0，
		// 只测「当前档位」的话 MID/LATE 两条路根本没被跑过。
		long early = Math.max(1, config.earlyGameMinutes);
		long mid = Math.max(early + 1, config.midGameMinutes);

		Progression.Tier atStart = Progression.tierForMinutes(0L, config);
		Progression.Tier atEarlyEdge = Progression.tierForMinutes(early - 1, config);
		Progression.Tier atMidStart = Progression.tierForMinutes(early, config);
		Progression.Tier atMidEdge = Progression.tierForMinutes(mid - 1, config);
		Progression.Tier atLate = Progression.tierForMinutes(mid, config);

		boolean boundariesOk = atStart == Progression.Tier.EARLY
				&& atEarlyEdge == Progression.Tier.EARLY
				&& atMidStart == Progression.Tier.MID
				&& atMidEdge == Progression.Tier.MID
				&& atLate == Progression.Tier.LATE;

		boolean configOk = config.midJackpotMultiplier >= 1.0D && config.lateJackpotMultiplier >= 1.0D;
		boolean headlessOk = headless == Progression.Tier.EARLY && headlessMultiplier == 1.0D;
		boolean liveOk = liveMultiplier >= 1.0D;

		check("⑥ 开局保护 / 按进度调概率", configOk && headlessOk && liveOk && boundariesOk,
				"开局保护 " + config.earlyGameMinutes + " 分钟、中期分界 " + config.midGameMinutes + " 分钟；"
						+ "分界 " + early + "/" + mid + " → "
						+ atStart + "/" + atEarlyEdge + "/" + atMidStart + "/" + atMidEdge + "/" + atLate
						+ "（应为 EARLY/EARLY/MID/MID/LATE）；"
						+ "当前档位 " + live + "（倍率 ×" + trim(liveMultiplier) + "）；无世界时 " + headless
						+ " ×" + trim(headlessMultiplier) + "（应为 EARLY ×1）");
	}

	// ------------------------------------------------------------ ⑦ 维度 / 群系

	/** ⑦：三个专属池都解析得出物品；主世界没有维度池；换维度能拿到对应池。 */
	private static void checkDimensionAndBiome(MinecraftServer server, ServerLevel level, BlockPos pos,
			RandomDropsConfig config, long rolls) {
		List<Item> netherPool = DropRandomizer.resolvePoolSnapshot(config.netherBonusItems);
		List<Item> endPool = DropRandomizer.resolvePoolSnapshot(config.endBonusItems);
		List<Item> biomePool = DropRandomizer.resolvePoolSnapshot(config.biomeBonusItems);

		boolean poolsOk = !netherPool.isEmpty() && !endPool.isEmpty() && !biomePool.isEmpty();

		boolean overworldHasNoDimensionPool = Progression.dimensionPool(level, config) == null;

		ServerLevel nether = server.getLevel(Level.NETHER);
		ServerLevel end = server.getLevel(Level.END);

		boolean netherOk = nether == null || Progression.dimensionPool(nether, config) == config.netherBonusItems;
		boolean endOk = end == null || Progression.dimensionPool(end, config) == config.endBonusItems;

		boolean netherMultiplierOk = nether == null
				|| Progression.dimensionMultiplier(nether, config) == config.netherJackpotMultiplier;
		boolean endMultiplierOk = end == null
				|| Progression.dimensionMultiplier(end, config) == config.endJackpotMultiplier;

		Identifier biomeId = Progression.biomeId(level, pos);
		boolean biomeReadable = biomeId != null;
		boolean headlessBiomeOk = !Progression.isSpecialBiome(null, pos, config)
				&& Progression.biomeId(null, pos) == null;

		boolean exclusiveHeadlessOk = Progression.rollExclusivePool(null, pos, level.getRandom(), config) == null;

		// 端到端：把维度池概率拉满，在下界掷一轮，掉出来的必须全是下界池里的东西
		boolean endToEndOk = true;
		String endToEndDetail = "（无下界维度，跳过）";

		if (nether != null && !netherPool.isEmpty()) {
			double savedChance = config.dimensionBonusChance;
			double savedMobChance = config.mobChance;
			double savedJackpot = config.jackpotChance;

			config.dimensionBonusChance = 1.0D;
			config.mobChance = 0.0D;
			config.jackpotChance = 0.0D;

			int fromNetherPool = 0;
			int outside = 0;

			try {
				BlockPos netherPos = nether.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(0, 64, 0));

				for (long i = 0; i < Math.min(rolls, 120L); i++) {
					for (ItemStack stack : DropRandomizer.rollBlockDrop(nether, netherPos, null)) {
						if (netherPool.contains(stack.getItem())) {
							fromNetherPool++;
						} else {
							outside++;
						}
					}
				}
			} finally {
				config.dimensionBonusChance = savedChance;
				config.mobChance = savedMobChance;
				config.jackpotChance = savedJackpot;
			}

			endToEndOk = fromNetherPool > 0 && outside == 0;
			endToEndDetail = "下界实掷 来自下界池 " + fromNetherPool + " 件 / 池外 " + outside + " 件";
		}

		check("⑦ 维度 / 群系影响池子", poolsOk && overworldHasNoDimensionPool && netherOk && endOk
						&& netherMultiplierOk && endMultiplierOk && biomeReadable && headlessBiomeOk
						&& exclusiveHeadlessOk && endToEndOk,
				"池子 下界=" + netherPool.size() + " / 末地=" + endPool.size() + " / 群系=" + biomePool.size()
						+ "；主世界无维度池=" + overworldHasNoDimensionPool
						+ "；下界×" + trim(config.netherJackpotMultiplier) + "=" + netherMultiplierOk
						+ "、末地×" + trim(config.endJackpotMultiplier) + "=" + endMultiplierOk
						+ "；当前位置群系=" + biomeId + "；" + endToEndDetail);
	}

	// ------------------------------------------------------------ ⑧ 精英怪

	/** ⑧：精英只从玩家来源生成；升级后带名字、发光、血更厚，并且能被登记/清理。 */
	private static void checkEliteMobs(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		// 非玩家来源（cause=null）不该产出精英 —— 否则刷怪塔会量产精英
		boolean rejectsNonPlayer = !EliteMobs.rollElite(null, level.getRandom(), config);

		Mob mob = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);

		if (mob == null) {
			check("⑧ 精英怪", false, "无法生成用于测试的僵尸");
			return;
		}

		boolean made;
		boolean marked;
		boolean glowing;
		boolean named;
		boolean healthier;
		boolean persistent;

		try {
			double baseHealth = mob.getMaxHealth();
			made = EliteMobs.makeElite(mob, level, null);
			marked = EliteMobs.isElite(mob);
			glowing = mob.hasGlowingTag() || mob.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING);
			named = mob.getCustomName() != null
					&& mob.getCustomName().getString().contains(config.eliteNamePrefix);
			healthier = mob.getMaxHealth() > baseHealth;
			persistent = mob.isPersistenceRequired();
		} finally {
			EliteMobs.forgetForTest(mob);
			mob.discard();
		}

		check("⑧ 精英怪", rejectsNonPlayer && made && marked && glowing && named && healthier && persistent
						&& config.eliteTreasureCount >= 0,
				"非玩家来源拒绝=" + rejectsNonPlayer + "；升级成功=" + made + "、已登记=" + marked
						+ "、发光=" + glowing + "、带名=" + named + "、血更厚=" + healthier
						+ "、不消失=" + persistent + "；死亡掉 " + config.eliteTreasureCount + " 件");
	}

	// ------------------------------------------------------------ ⑨ 击杀奖励（药水效果）

	/**
	 * ⑨：爆怪概率已下调；两个池子能解析且各自分类正确；只有玩家击杀才给；
	 * 负面概率能真的把结果推向负面池；真正发一次效果能落到活体身上。
	 */
	private static void checkKillEffects(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		// 爆怪概率下调：必须低于早期的 0.15，且物品/生物/空手三者比例自洽
		boolean mobChanceReduced = config.mobChance > 0.0D && config.mobChance < 0.15D;
		double ratio = config.mobChance + config.emptyChance + config.itemChance();
		boolean ratioSane = Math.abs(ratio - 1.0D) < 1.0E-6D;

		KillEffects.Pools pools = KillEffects.poolSnapshot(config);
		boolean poolsOk = !pools.beneficial().isEmpty() && !pools.harmful().isEmpty()
				&& pools.beneficial().size() == config.killEffects.size()
				&& pools.harmful().size() == config.killEffectsHarmful.size();
		boolean categoriesOk = pools.beneficial().stream().noneMatch(KillEffects.Entry::harmful)
				&& pools.harmful().stream().allMatch(KillEffects.Entry::harmful);

		// 负面池默认时长应当短于有益池 —— 这是「长期仍然是赚的」的前提
		boolean harmfulShorter = config.killEffectHarmfulDurationSeconds < config.killEffectDurationSeconds;

		// 只有玩家击杀才给 —— null 与「另一个生物」都必须被拒
		boolean rejectsNonPlayer = !KillEffects.isPlayerKill(null);

		// 池子自洽：有益池里写有害效果会被拒，反之亦然
		List<String> savedBeneficial = config.killEffects;
		List<String> savedHarmful = config.killEffectsHarmful;

		int misplacedInBeneficial;
		int misplacedInHarmful;
		int overrideDuration;
		int overrideAmplifier;
		boolean overrideHarmfulFlag;

		try {
			config.killEffects = List.of("minecraft:poison");
			config.killEffectsHarmful = List.of("minecraft:speed");
			KillEffects.Pools wrong = KillEffects.poolSnapshot(config);
			misplacedInBeneficial = wrong.beneficial().size();
			misplacedInHarmful = wrong.harmful().size();

			// 单项覆盖写法：id;秒数;等级（顺带验证 harmful 标记）
			config.killEffects = List.of();
			config.killEffectsHarmful = List.of("minecraft:slowness;20;2");
			List<KillEffects.Entry> overridden = KillEffects.poolSnapshot(config).harmful();

			overrideDuration = overridden.isEmpty() ? -1 : overridden.get(0).durationTicks();
			overrideAmplifier = overridden.isEmpty() ? -1 : overridden.get(0).amplifier();
			overrideHarmfulFlag = !overridden.isEmpty() && overridden.get(0).harmful();
		} finally {
			config.killEffects = savedBeneficial;
			config.killEffectsHarmful = savedHarmful;
		}

		boolean placementOk = misplacedInBeneficial == 0 && misplacedInHarmful == 0;
		boolean overrideOk = overrideDuration == 20 * 20 && overrideAmplifier == 2 && overrideHarmfulFlag;

		// 负面概率的两个极端必须真的选对池子
		double savedHarmfulChance = config.killEffectHarmfulChance;
		boolean forcedHarmful;
		boolean forcedBeneficial;

		try {
			config.killEffectHarmfulChance = 1.0D;
			forcedHarmful = KillEffects.pickHarmful(pools, config);

			config.killEffectHarmfulChance = 0.0D;
			forcedBeneficial = !KillEffects.pickHarmful(pools, config);
		} finally {
			config.killEffectHarmfulChance = savedHarmfulChance;
		}

		boolean chanceOk = forcedHarmful && forcedBeneficial;

		// 端到端：负面率拉满，真的发一次，必须是负面效果且确实落到活体身上
		Mob probe = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
		boolean applied = false;
		boolean harmfulApplied = false;
		String appliedDetail = "（无法生成测试生物）";

		if (probe != null) {
			double savedChance = config.killEffectHarmfulChance;
			config.killEffectHarmfulChance = 1.0D;

			try {
				KillEffects.Entry granted = KillEffects.grantForTest(probe, level);
				applied = granted != null && probe.hasEffect(granted.effect());
				harmfulApplied = granted != null && granted.harmful();
				appliedDetail = granted == null
						? "未给到效果"
						: (applied
								? "已生效 " + (granted.harmful() ? "【负面】" : "【有益】")
										+ KillEffects.displayName(granted)
								: "给了但没生效");
			} finally {
				config.killEffectHarmfulChance = savedChance;
				probe.discard();
			}
		}

		check("⑨ 击杀奖励（药水效果 · 含负面）", mobChanceReduced && ratioSane && poolsOk && categoriesOk
						&& harmfulShorter && rejectsNonPlayer && placementOk && overrideOk && chanceOk
						&& applied && harmfulApplied,
				"爆怪概率 " + trim(config.mobChance) + "（已从 0.15 下调=" + mobChanceReduced
						+ "）；物品/生物/空手=" + trim(config.itemChance()) + "/" + trim(config.mobChance)
						+ "/" + trim(config.emptyChance) + " 合计 " + trim(ratio)
						+ "；池子 有益" + pools.beneficial().size() + "/负面" + pools.harmful().size()
						+ "（分类正确=" + categoriesOk + "）；负面时长 " + config.killEffectHarmfulDurationSeconds
						+ "秒 < 有益 " + config.killEffectDurationSeconds + "秒=" + harmfulShorter
						+ "；非玩家击杀被拒=" + rejectsNonPlayer
						+ "；错池条目 有益池" + misplacedInBeneficial + "/负面池" + misplacedInHarmful + "（应 0/0）"
						+ "；负面率 0%→有益、100%→负面=" + chanceOk
						+ "；单项覆盖 20 秒/III 级/负面=" + overrideOk
						+ "；实发一次（负面率拉满）：" + appliedDetail);
	}

	// ------------------------------------------------------------ ⑩ 刷怪蛋禁用

	/**
	 * ⑩：刷怪蛋被判定为禁用、不在随机池里、全是蛋的池子抽不出东西；
	 * 关掉开关后又能正常放行（开关真的有用）。
	 */
	private static void checkSpawnerEggs(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		Item egg = SpawnEggItem.byId(EntityTypes.ZOMBIE).map(Holder::value).orElse(null);
		boolean eggFound = egg != null && egg != Items.AIR;

		boolean instanceBanned = eggFound && SpawnerEggGuard.isBanned(egg, config);
		boolean idBanned = SpawnerEggGuard.isBannedId(
				Identifier.fromNamespaceAndPath("minecraft", "zombie_spawn_egg"), config);
		boolean commonAllowed = !SpawnerEggGuard.isBanned(Items.DIRT, config);

		// 池子里一件刷怪蛋都不能有
		long eggsInPool = DropRandomizer.itemPoolSnapshot().stream()
				.filter(item -> SpawnerEggGuard.isBanned(item, config))
				.count();

		// 全是刷怪蛋的池子 → 抽不出东西（宁可什么都不掉，也不放行）
		boolean allEggsRejected = eggFound
				&& SpawnerEggGuard.pickAllowed(List.of(egg), level.getRandom(), config) == null;

		// 开关关掉后应当放行
		boolean offPasses;

		try {
			config.blockSpawnerEggDrops = false;
			offPasses = !SpawnerEggGuard.isBanned(egg, config);
		} finally {
			config.blockSpawnerEggDrops = true;
		}

		check("⑩ 刷怪蛋禁用", config.blockSpawnerEggDrops && eggFound && instanceBanned && idBanned
						&& commonAllowed && eggsInPool == 0 && allEggsRejected && offPasses,
				"找到僵尸刷怪蛋=" + eggFound + "；按类判定=" + instanceBanned + "、按 id 判定=" + idBanned
						+ "；泥土放行=" + commonAllowed + "；随机池里残留刷怪蛋 " + eggsInPool + " 件（应为 0）"
						+ "；全是蛋的池子被拒=" + allEggsRejected + "；关掉开关后放行=" + offPasses);
	}

	// ------------------------------------------------------------ ⑪ TNT 引燃

	/**
	 * ⑪：能甩出配置数量的 TNT（4 个方向）、冷却真的挡得住、方块清单匹配正确、碎块解析正确。
	 *
	 * <p>生成出来的 TNT 会<b>立刻销毁</b> —— 自检不该把出生点炸出一个坑。
	 */
	private static void checkTntIgnition(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		int wanted = Math.max(1, config.tntIgniteDirections);
		List<PrimedTnt> spawned = TntIgnition.spawnOnlyForTest(level, pos, config);
		int count = spawned.size();

		for (PrimedTnt tnt : spawned) {
			tnt.discard();
		}

		boolean fuseOk = spawned.isEmpty() || spawned.get(0).getFuse() == config.tntIgniteFuseTicks;

		// 冷却：刚引爆过 → 挡下；冷却设为 0 → 放行
		UUID probe = UUID.randomUUID();
		boolean cooldownBlocks;
		boolean cooldownOffPasses;

		try {
			TntIgnition.markCooldownForTest(probe);
			cooldownBlocks = !TntIgnition.cooldownAllows(probe, config);

			int savedCooldown = config.tntIgniteCooldownSeconds;
			config.tntIgniteCooldownSeconds = 0;
			cooldownOffPasses = TntIgnition.cooldownAllows(probe, config);
			config.tntIgniteCooldownSeconds = savedCooldown;
		} finally {
			TntIgnition.reset();
		}

		// 方块清单：原木 / 石头算引燃，泥土不算，蜘蛛网（精确 id）算引燃
		boolean logMatches = TntIgnition.matches(Blocks.OAK_LOG.defaultBlockState(), config.tntIgniteBlocks);
		boolean stoneMatches = TntIgnition.matches(Blocks.STONE.defaultBlockState(), config.tntIgniteBlocks);
		boolean cobwebMatches = TntIgnition.matches(Blocks.COBWEB.defaultBlockState(), config.tntIgniteBlocks);
		boolean dirtRejected = !TntIgnition.matches(Blocks.DIRT.defaultBlockState(), config.tntIgniteBlocks);

		// 碎块解析：id;数量
		ItemStack debris = TntIgnition.parseEntry("minecraft:gravel;2");
		boolean debrisOk = debris.getItem() == Items.GRAVEL && debris.getCount() == 2;
		boolean badIdOk = TntIgnition.parseEntry("minecraft:this_item_does_not_exist;2").isEmpty();

		check("⑪ TNT 引燃甩射", count == wanted && fuseOk && cooldownBlocks && cooldownOffPasses
						&& logMatches && stoneMatches && cobwebMatches && dirtRejected && debrisOk && badIdOk,
				"甩出 " + count + " 枚（配置 " + wanted + " 个方向，半径 " + trim(config.tntIgniteSpreadBlocks)
						+ " 格，引信 " + config.tntIgniteFuseTicks + " 刻=" + fuseOk + "）；"
						+ "冷却挡下=" + cooldownBlocks + "、冷却设 0 放行=" + cooldownOffPasses
						+ "；方块匹配 原木=" + logMatches + " 石头=" + stoneMatches + " 蜘蛛网=" + cobwebMatches
						+ " 泥土(应 false)=" + dirtRejected
						+ "；碎块 gravel;2 解析=" + debrisOk + "、假 id 忽略=" + badIdOk);
	}

	// ------------------------------------------------------------ ⑫ 掉落合并

	/**
	 * ⑫：同类掉落物会并成一堆；不同物品不并；关掉开关后完全不并。
	 *
	 * <p>测试点选在<b>高空</b>（出生点上方 150 格），避开前面那几项自检撒在地上的一大堆掉落物。
	 */
	private static void checkDropMerging(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		boolean saved = config.dropMergeEnabled;

		// 这一项要独占同刻窗口，先把前面几项自检留下的条目清干净
		DropMerger.reset();

		BlockPos isolated = new BlockPos(pos.getX(), Math.min(300, pos.getY() + 150), pos.getZ());
		double x = isolated.getX() + 0.5D;
		double y = isolated.getY() + 0.5D;
		double z = isolated.getZ() + 0.5D;

		ItemEntity base = null;
		int afterCount = -1;
		boolean merged = false;
		boolean incomingEmptied = false;
		boolean differentRejected = false;
		boolean offRespected = false;
		boolean baseAdded = false;
		boolean baseTracked = false;
		boolean windowCleared = false;
		int tracked = -1;

		try {
			config.dropMergeEnabled = true;

			// 走的是「入世界」那条路：混入会先试合并，并光就取消生成，否则登记进同刻窗口
			base = new ItemEntity(level, x, y, z, new ItemStack(Items.DIRT, 3));
			baseAdded = level.addFreshEntity(base);
			tracked = DropMerger.windowSize(level);
			baseTracked = tracked >= 1;

			ItemEntity incoming = new ItemEntity(level, x, y, z, new ItemStack(Items.DIRT, 5));
			merged = DropMerger.absorb(level, incoming);
			incomingEmptied = incoming.getItem().isEmpty();
			afterCount = base.getItem().getCount();

			// 不同物品不该被并掉
			ItemEntity stone = new ItemEntity(level, x, y, z, new ItemStack(Items.STONE, 2));
			differentRejected = !DropMerger.absorb(level, stone) && stone.getItem().getCount() == 2;

			// 关掉开关 → 完全不并
			config.dropMergeEnabled = false;
			ItemEntity whenOff = new ItemEntity(level, x, y, z, new ItemStack(Items.DIRT, 2));
			offRespected = !DropMerger.absorb(level, whenOff) && whenOff.getItem().getCount() == 2;

			DropMerger.reset();
			windowCleared = DropMerger.windowSize(level) == 0;
		} finally {
			config.dropMergeEnabled = saved;

			if (base != null) {
				base.discard();
			}

			DropMerger.reset();
		}

		check("⑫ 掉落物自动合并", merged && incomingEmptied && afterCount == 8
						&& differentRejected && offRespected && baseAdded && baseTracked && windowCleared,
				"3 个泥土 + 5 个泥土 → 一堆 " + afterCount + " 个（应 8，被完全吸收=" + merged
						+ "）；石头不并入=" + differentRejected + "；关掉开关后不并=" + offRespected
						+ "；半径 " + trim(config.dropMergeRadius) + " 格"
						+ "；[诊断] 入世界=" + baseAdded + "、同刻窗口登记 " + tracked + " 件、清空=" + windowCleared);
	}

	// ------------------------------------------------- ⑬ 触发事件 + 断肢受伤

	/** ⑬：徒手连挖触发、斧头判定、跌落分档、断肢真的把移速 / 挖掘速度压下来并能解除。 */
	private static void checkEventsAndLimbInjury(ServerLevel level, BlockPos pos, RandomDropsConfig config) {
		// 徒手挖木头：连击到阈值触发
		UUID probe = UUID.randomUUID();
		int threshold = Math.max(1, config.bareHandLogThreshold);
		int triggeredAt = -1;

		try {
			for (int i = 1; i <= threshold; i++) {
				if (HarvestEvents.advance(probe) >= threshold) {
					triggeredAt = i;
					break;
				}
			}
		} finally {
			HarvestEvents.clearStreak(probe);
		}

		// 斧头判定：斧头算「有工具」，空手与泥土不算
		boolean axeDetected = HarvestEvents.hasAxe(new ItemStack(Items.DIAMOND_AXE));
		boolean dirtIsNotAxe = !HarvestEvents.hasAxe(new ItemStack(Items.DIRT));
		boolean emptyHandIsNotAxe = !HarvestEvents.hasAxe(null);

		// 跌落分档（纯函数）
		boolean belowRejected = !FallInjury.triggers(config.fallInjuryHeight - 1, config);
		boolean atThreshold = FallInjury.triggers(config.fallInjuryHeight, config);
		FallInjury.Plan light = FallInjury.plan(config.fallInjuryHeight, config);
		FallInjury.Plan severe = FallInjury.plan(config.fallInjurySevereHeight, config);

		boolean planOk = light.legs() == 1 && light.seconds() == config.limbInjurySeconds
				&& severe.legs() == 2 && severe.seconds() == config.limbInjurySevereSeconds
				&& (!config.fallInjuryHurtsArms || (light.arms() == 1 && severe.arms() == 2));

		// 断肢真的压属性：拿一只僵尸当载体
		Mob carrier = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);

		boolean legApplied = false;
		boolean armApplied = false;
		boolean lightSlows = false;
		boolean severeSlowsMore = false;
		boolean cleared = false;
		boolean miningAttributePresent = false;
		boolean miningPenaltyOk = Math.abs(LimbInjury.miningMultiplier(1, config)
				- (1.0D - config.limbOnePenalty)) < 1.0E-9D
				&& Math.abs(LimbInjury.miningMultiplier(2, config)
						- (1.0D - config.limbTwoPenalty)) < 1.0E-9D;
		String detail = "（无法生成测试生物）";

		if (carrier != null) {
			double baseSpeed = carrier.getAttributeValue(Attributes.MOVEMENT_SPEED);

			// 注意：BLOCK_BREAK_SPEED 只有玩家身上有，拿僵尸当载体时「断手」那一路挂不上去 ——
			// 所以按「载体有没有这个属性」来断言，惩罚比例另用纯函数核对。
			miningAttributePresent = carrier.getAttribute(Attributes.BLOCK_BREAK_SPEED) != null;

			try {
				LimbInjury.injure(carrier, 1, 1, 60);
				legApplied = LimbInjury.hasLegModifier(carrier);
				armApplied = LimbInjury.hasArmModifier(carrier);

				double lightSpeed = carrier.getAttributeValue(Attributes.MOVEMENT_SPEED);
				lightSlows = lightSpeed < baseSpeed
						&& Math.abs(lightSpeed / baseSpeed - (1.0D - config.limbOnePenalty)) < 0.01D;

				LimbInjury.injure(carrier, 2, 2, 60);
				double severeSpeed = carrier.getAttributeValue(Attributes.MOVEMENT_SPEED);
				severeSlowsMore = severeSpeed < lightSpeed
						&& Math.abs(severeSpeed / baseSpeed - (1.0D - config.limbTwoPenalty)) < 0.01D;

				LimbInjury.clear(carrier);
				cleared = !LimbInjury.hasLegModifier(carrier) && !LimbInjury.hasArmModifier(carrier);

				detail = "移速 " + trim(baseSpeed) + " → 断一处 " + trim(lightSpeed)
						+ " → 断两处 " + trim(severeSpeed) + " → 解除后 "
						+ trim(carrier.getAttributeValue(Attributes.MOVEMENT_SPEED));
			} finally {
				LimbInjury.clear(carrier.getUUID());
				LimbInjury.clear(carrier);
				carrier.discard();
			}
		}

		check("⑬ 触发事件 + 断肢受伤", triggeredAt == threshold && axeDetected && dirtIsNotAxe
						&& emptyHandIsNotAxe && belowRejected && atThreshold && planOk
						&& legApplied && armApplied == miningAttributePresent && miningPenaltyOk
						&& lightSlows && severeSlowsMore && cleared,
				"徒手连挖阈值 " + threshold + " → 第 " + triggeredAt + " 次触发；斧头判定 斧=" + axeDetected
						+ " 泥土=" + dirtIsNotAxe + " 空手=" + emptyHandIsNotAxe
						+ "；跌落分档 " + trim(config.fallInjuryHeight) + " 格以下不触发=" + belowRejected
						+ "、达到即触发=" + atThreshold + "；"
						+ trim(config.fallInjuryHeight) + "格→断" + light.legs() + "腿" + light.arms() + "手/"
						+ light.seconds() + "秒，" + trim(config.fallInjurySevereHeight) + "格→断"
						+ severe.legs() + "腿" + severe.arms() + "手/" + severe.seconds() + "秒=" + planOk
						+ "；属性修饰符 腿=" + legApplied + " 手=" + armApplied
								+ "（载体有挖掘速度属性=" + miningAttributePresent + "）"
								+ "；挖掘倍率 -30%/-80% 计算正确=" + miningPenaltyOk
						+ "；-30%命中=" + lightSlows + " -80%命中=" + severeSlowsMore
						+ " 解除=" + cleared + "；" + detail);
	}

	// ------------------------------------------------- ⑭ 分层掉落 + 终极物资

	/** ⑭：维度专属物品在别的维度会被剔除、终极物资能产出且维度判定正确。 */
	private static void checkTieredDrops(MinecraftServer server, ServerLevel level, BlockPos pos,
			RandomDropsConfig config) {
		Item netherOnly = Items.ANCIENT_DEBRIS;
		Item endOnly = Items.END_CRYSTAL;

		boolean netherBlockedInOverworld = TieredDrops.isExclusiveElsewhere(netherOnly, level, config);
		boolean endBlockedInOverworld = TieredDrops.isExclusiveElsewhere(endOnly, level, config);
		boolean commonAllowed = !TieredDrops.isExclusiveElsewhere(Items.DIRT, level, config);

		ServerLevel nether = server.getLevel(Level.NETHER);
		boolean netherAllowedInNether = nether == null
				|| !TieredDrops.isExclusiveElsewhere(netherOnly, nether, config);

		// 关掉分层开关后一律放行
		boolean offPasses;

		try {
			config.enableTieredDrops = false;
			offPasses = !TieredDrops.isExclusiveElsewhere(netherOnly, level, config);
		} finally {
			config.enableTieredDrops = true;
		}

		// 终极物资：命中时会给框架或末影之眼，数量按配置来
		ItemStack grant = TieredDrops.pickUltimate(level.getRandom(), config);
		boolean grantOk = !grant.isEmpty()
				&& (grant.getItem() == Items.END_PORTAL_FRAME || grant.getItem() == Items.ENDER_EYE)
				&& grant.getCount() == (grant.getItem() == Items.END_PORTAL_FRAME
						? config.ultimatePortalFrameCount : config.ultimateEnderEyeCount);

		// 维度判定：默认是末地，主世界应当不匹配
		boolean dimensionGateOk = !"any".equalsIgnoreCase(config.ultimateDimension)
				&& !TieredDrops.dimensionMatches(level, config);

		// 端到端：末地存在时把概率拉满实掷一轮
		boolean endToEndOk = true;
		String endToEndDetail = "（无末地维度，跳过）";
		ServerLevel end = server.getLevel(Level.END);

		if (end != null) {
			double savedChance = config.ultimateChance;
			double savedMobChance = config.mobChance;
			double savedJackpot = config.jackpotChance;

			config.ultimateChance = 1.0D;
			config.mobChance = 0.0D;
			config.jackpotChance = 0.0D;

			int hits = 0;
			int misses = 0;

			try {
				BlockPos endPos = end.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(0, 64, 0));

				for (long i = 0; i < 40L; i++) {
					for (ItemStack stack : DropRandomizer.rollBlockDrop(end, endPos, null)) {
						if (stack.getItem() == Items.END_PORTAL_FRAME || stack.getItem() == Items.ENDER_EYE) {
							hits++;
						} else {
							misses++;
						}
					}
				}
			} finally {
				config.ultimateChance = savedChance;
				config.mobChance = savedMobChance;
				config.jackpotChance = savedJackpot;
			}

			endToEndOk = hits > 0 && misses == 0;
			endToEndDetail = "末地实掷 40 次：终极物资 " + hits + " 件 / 其它 " + misses + " 件";
		}

		check("⑭ 分层掉落 + 终极物资", netherBlockedInOverworld && endBlockedInOverworld && commonAllowed
						&& netherAllowedInNether && offPasses && grantOk && dimensionGateOk && endToEndOk,
				"主世界剔除 下界专属=" + netherBlockedInOverworld + " 末地专属=" + endBlockedInOverworld
						+ "；普通物品放行=" + commonAllowed
						+ "；下界内放行下界专属=" + netherAllowedInNether
						+ "；关掉分层开关后放行=" + offPasses
						+ "；终极物资 " + (grant.isEmpty() ? "空" : grant.getHoverName().getString()
								+ " ×" + grant.getCount()) + "（概率 " + trim(config.ultimateChance)
						+ "，维度 " + config.ultimateDimension + "，主世界不匹配=" + dimensionGateOk + "）；"
						+ endToEndDetail);
	}

	// ------------------------------------------- ⑮ 特殊物品必须带真实数据（修 bug：空壳附魔书）

	/**
	 * ⑮：附魔书 / 药水这类「本身是容器」的物品，掉出来必须带着真实数据才有用。
	 *
	 * <p>历史 bug：直接 {@code new ItemStack(Items.ENCHANTED_BOOK)} 造出的是<b>空书</b>，
	 * 没有任何附魔，拿在手里既用不了也没有效果。这里验证 {@link DropRandomizer#makeStack}
	 * 真的往里写了数据。
	 */
	private static void checkSpecialItems(ServerLevel level, RandomDropsConfig config) {
		var random = level.getRandom();

		// 附魔书：存了至少一条真实附魔
		ItemStack book = DropRandomizer.makeRealSpecial(Items.ENCHANTED_BOOK, 1, level, random);
		ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
		boolean bookHasEnchant = stored != null && !stored.isEmpty();
		int bookEnchantCount = stored == null ? 0 : stored.size();
		boolean bookLevelOk = bookHasEnchant
				&& stored.entrySet().stream().allMatch(e -> e.getIntValue() >= 1);

		// 药水：写了 PotionContents 且真有效果
		ItemStack potion = DropRandomizer.makeRealSpecial(Items.POTION, 1, level, random);
		net.minecraft.world.item.alchemy.PotionContents pc = potion.get(DataComponents.POTION_CONTENTS);
		boolean potionHasEffect = pc != null && pc.hasEffects();

		// 对照：普通物品不受影响（数量正确、无多余数据）
		ItemStack dirt = DropRandomizer.makeRealSpecial(Items.DIRT, 4, level, random);
		boolean dirtOk = dirt.getItem() == Items.DIRT && dirt.getCount() == 4
				&& dirt.get(DataComponents.STORED_ENCHANTMENTS) == null;

		// 多掷几次，确认附魔书不是「偶尔」才有数据（抽 30 次，每次都该带附魔）
		boolean bookAlwaysEnchanted = true;
		for (int i = 0; i < 30; i++) {
			ItemEnchantments again = DropRandomizer.makeRealSpecial(Items.ENCHANTED_BOOK, 1, level, random)
					.get(DataComponents.STORED_ENCHANTMENTS);
			if (again == null || again.isEmpty()) {
				bookAlwaysEnchanted = false;
				break;
			}
		}

		check("⑮ 特殊物品带真实数据（修空壳附魔书 bug）",
				bookHasEnchant && bookLevelOk && bookAlwaysEnchanted && potionHasEffect && dirtOk,
				"附魔书存了 " + bookEnchantCount + " 条附魔、等级≥1=" + bookLevelOk
						+ "、连掷 30 次都非空=" + bookAlwaysEnchanted
						+ "；药水带效果=" + potionHasEffect
						+ "；普通泥土数量正确且无附魔=" + dirtOk);
	}

	// ------------------------------------------------------------ v1.12.0 附魔突破 + 全局事件

	/** ⑯：三个新附魔能从注册表解析出来，且 hasEnchantment / isWeaponOrTool 判断正确。 */
	private static void checkModEnchantments(ServerLevel level, RandomDropsConfig config) {
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

	private static void setEnchant(ItemStack stack, Holder<Enchantment> holder) {
		if (holder == null) {
			return;
		}
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		m.set(holder, 1);
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
	}

	/** ⑰：碎裂按配置概率附着在随机掉落的武器/工具上（chance=1 全中，chance=0 全不中），非武器/工具不受影响。 */
	private static void checkShatterApply(ServerLevel level, RandomDropsConfig config) {
		double savedChance = config.shatterApplyChance;
		boolean savedEnabled = config.enableEnchantmentBreakthrough;
		config.enableEnchantmentBreakthrough = true;

		boolean allShatter;
		boolean noneShatter;
		boolean appleClean;
		try {
			config.shatterApplyChance = 1.0D;
			int total = 60;
			int withShatter = 0;
			for (int i = 0; i < total; i++) {
				ItemStack s = DropRandomizer.makeRealSpecial(Items.DIAMOND_SWORD, 1, level, level.getRandom());
				if (ModEnchantments.hasEnchantment(s, ModEnchantments.shatter(level))) {
					withShatter++;
				}
			}
			allShatter = withShatter == total;

			config.shatterApplyChance = 0.0D;
			int none = 0;
			for (int i = 0; i < 30; i++) {
				ItemStack s = DropRandomizer.makeRealSpecial(Items.IRON_PICKAXE, 1, level, level.getRandom());
				if (!ModEnchantments.hasEnchantment(s, ModEnchantments.shatter(level))) {
					none++;
				}
			}
			noneShatter = none == 30;

			config.shatterApplyChance = 1.0D;
			ItemStack apple = DropRandomizer.makeRealSpecial(Items.APPLE, 1, level, level.getRandom());
			appleClean = !ModEnchantments.hasEnchantment(apple, ModEnchantments.shatter(level));
		} finally {
			config.enableEnchantmentBreakthrough = savedEnabled;
			config.shatterApplyChance = savedChance;
		}

		check("⑰ 碎裂 10% 附着随机武器/工具",
				allShatter && noneShatter && appleClean,
				"chance=1 时 " + (allShatter ? "全部" : "未全部") + "带碎裂；chance=0 时 "
						+ (noneShatter ? "全不带" : "仍带") + "；苹果(非武器)" + (appleClean ? "干净" : "被污染"));
	}

	/** ⑱：雷霆万钧的雷击能在世界中生成真实闪电实体。 */
	private static void checkThunderLightning(ServerLevel level, RandomDropsConfig config) {
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

	/** ⑲：臭脚让周边花草枯萎、草方块变泥土，且隐藏 buff 能在附近生成亡灵。 */
	private static void checkStinkyFeet(MinecraftServer server, ServerLevel level, RandomDropsConfig config) {
		BlockPos base = new BlockPos(5, 80, 5);
		level.setBlock(base.offset(0, 1, 0), Blocks.DANDELION.defaultBlockState(), 2);
		level.setBlock(base.offset(1, 1, 0), Blocks.POPPY.defaultBlockState(), 2);
		level.setBlock(base.offset(2, 1, 0), Blocks.TALL_GRASS.defaultBlockState(), 2);
		level.setBlock(base.offset(0, 0, 0), Blocks.GRASS_BLOCK.defaultBlockState(), 2);

		EnchantmentEffects.witherPlants(level, base, config.stinkyRadius);

		boolean flowerGone = level.getBlockState(base.offset(0, 1, 0)).isAir()
				&& level.getBlockState(base.offset(1, 1, 0)).isAir();
		boolean grassGone = level.getBlockState(base.offset(2, 1, 0)).isAir();
		boolean dirt = level.getBlockState(base.offset(0, 0, 0)).getBlock() == Blocks.DIRT;

		int before = countUndead(level, base, config.stinkyRadius + 4.0D);
		EnchantmentEffects.spawnUndeadNear(level, base);
		int after = countUndead(level, base, config.stinkyRadius + 4.0D);
		boolean undeadSpawned = after > before;

		// 清理
		level.setBlock(base.offset(0, 1, 0), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(base.offset(1, 1, 0), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(base.offset(2, 1, 0), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(base.offset(0, 0, 0), Blocks.AIR.defaultBlockState(), 2);

		check("⑲ 臭脚·花草枯萎+亡灵生成",
				flowerGone && grassGone && dirt && undeadSpawned,
				"花消失=" + flowerGone + " 草消失=" + grassGone + " 草方块→泥土=" + dirt
						+ "；亡灵生成 " + before + "→" + after);
	}

	private static int countUndead(ServerLevel level, BlockPos center, double r) {
		AABB box = new AABB(center.getX() - r, center.getY() - r, center.getZ() - r,
				center.getX() + r, center.getY() + r, center.getZ() + r);
		return level.getEntities((Entity) null, box,
				e -> EnchantmentEffects.isUndead(e, level)).size();
	}

	/** ⑳：全局事件系统能触发、HUD 正常、青蛙雨与天降陨石可生成。 */
	private static void checkGlobalEvents(MinecraftServer server, ServerLevel level, RandomDropsConfig config) {
		boolean savedEnabled = config.enableEvents;
		boolean savedHud = config.eventHudEnabled;
		boolean savedFrog = config.enableFrogRain;
		boolean savedMeteor = config.enableMeteorShower;

		config.enableEvents = true;
		config.eventHudEnabled = true;
		config.enableFrogRain = true;
		config.enableMeteorShower = true;

		boolean hudOk = false;
		boolean triggered = false;
		boolean nameOk = false;
		boolean frogOk = false;
		boolean meteorOk = true;
		try {
			ServerBossEvent hud = GlobalEvents.ensureHudForTest();
			hudOk = hud != null;

			config.enableMeteorShower = false; // 只测青蛙雨，避免名字混淆
			GlobalEvents.forceTriggerNoPlayerCheck(server, config);
			triggered = GlobalEvents.activeEventCount() > 0;

			GlobalEvents.updateHudForTest(server, config, server.getTickCount());
			nameOk = hud.getName().getString().contains("青蛙雨");

			BlockPos origin = new BlockPos(0, 90, 0);
			int before = countFrogs(level, origin, 40.0D);
			GlobalEvents.spawnFrogAt(level, origin.getX() + 0.5, origin.getY() + 10.0, origin.getZ() + 0.5);
			int after = countFrogs(level, origin, 40.0D);
			frogOk = after > before;

			try {
				GlobalEvents.spawnMeteorAt(level, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, config);
			} catch (Throwable t) {
				meteorOk = false;
			}
		} finally {
			config.enableEvents = savedEnabled;
			config.eventHudEnabled = savedHud;
			config.enableFrogRain = savedFrog;
			config.enableMeteorShower = savedMeteor;
			GlobalEvents.reset();
		}

		check("⑳ 全局事件·青蛙雨/陨石+HUD",
				hudOk && triggered && nameOk && frogOk && meteorOk,
				"HUD创建=" + hudOk + " 触发=" + triggered + " HUD含「青蛙雨」=" + nameOk
						+ " 青蛙生成=" + frogOk + " 陨石不崩=" + meteorOk);
	}

	private static int countFrogs(ServerLevel level, BlockPos center, double r) {
		AABB box = new AABB(center.getX() - r, center.getY() - r, center.getZ() - r,
				center.getX() + r, center.getY() + r, center.getZ() + r);
		return level.getEntities((Entity) null, box, e -> e instanceof Frog).size();
	}

	/** ㉑：磁石已注册、护甲可检出，且吸力真的把掉落物推向中心。 */
	private static void checkMagnet(ServerLevel level, RandomDropsConfig config) {
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

	/** ㉒：贪婪已注册，且 randomLootOne 能稳定供给随机物品（连抽 20 次至少 15 次非空）。 */
	private static void checkGreed(ServerLevel level, RandomDropsConfig config) {
		Holder<Enchantment> greed = ModEnchantments.byName(level, ModEnchantments.GREED);
		boolean registered = greed != null;

		BlockPos pos = new BlockPos(0, 90, 0);
		int nonEmpty = 0;
		for (int i = 0; i < 20; i++) {
			if (!DropRandomizer.randomLootOne(level, pos, level.getRandom()).isEmpty()) {
				nonEmpty++;
			}
		}

		check("㉒ 贪婪·注册+额外掉落",
				registered && nonEmpty >= 15,
				"注册=" + registered + "；连抽 20 次非空 " + nonEmpty + " 次（应 ≥15）");
	}

	/** ㉓：负重诅咒已注册，穿上挂移速减益 modifier、脱下摘除。 */
	private static void checkCurseBurden(ServerLevel level, RandomDropsConfig config) {
		Holder<Enchantment> burden = ModEnchantments.byName(level, ModEnchantments.CURSE_OF_BURDEN);
		boolean registered = burden != null;

		boolean applied = false;
		boolean removed = false;
		ArmorStand stand = spawnArmorStand(level, new BlockPos(0, 90, 0));
		if (stand != null) {
			try {
				EnchantmentEffects.applyBurden(stand, true, config);
				AttributeInstance speed = stand.getAttribute(Attributes.MOVEMENT_SPEED);
				applied = speed != null && speed.hasModifier(EnchantmentEffects.BURDEN_ID);

				EnchantmentEffects.applyBurden(stand, false, config);
				removed = speed == null || !speed.hasModifier(EnchantmentEffects.BURDEN_ID);
			} finally {
				stand.discard();
			}
		}

		check("㉓ 负重诅咒·注册+移速减益",
				registered && applied && removed,
				"注册=" + registered + " 穿上挂减益=" + applied + " 脱下摘除=" + removed);
	}

	/** ㉔：易碎诅咒已注册，受击时带诅咒的护甲被碎、不带诅咒的护甲保留。 */
	private static void checkCurseFrailty(ServerLevel level, RandomDropsConfig config) {
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

	/** 自检用：在指定位置生成一个盔甲架（有护甲槽与属性表，测诅咒足够了）。 */
	private static ArmorStand spawnArmorStand(ServerLevel level, BlockPos pos) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:armor_stand"));
		if (!(type instanceof EntityType)) {
			return null;
		}

		@SuppressWarnings("unchecked")
		EntityType<? extends ArmorStand> as = (EntityType<? extends ArmorStand>) type;
		ArmorStand stand = new ArmorStand(as, level);
		stand.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		level.addFreshEntity(stand);
		return stand;
	}

	// ------------------------------------------------------------ 工具

	private static void check(String name, boolean ok, String detail) {
		if (ok) {
			passed++;
			RandomDrops.LOGGER.info("[random-drops] 自检 ✅ {} —— {}", name, detail);
		} else {
			failed++;
			RandomDrops.LOGGER.error("[random-drops] 自检 ❌ {} —— {}", name, detail);
		}
	}

	/** 把 double 写成好看的样子（去掉多余的 0）。 */
	private static String trim(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1.0E9D) {
			return String.valueOf((long) value);
		}

		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
