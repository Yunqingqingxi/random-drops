package com.randomdrops;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 全局事件系统 + 持久 HUD。
 *
 * <p>每 {@code eventIntervalMinutes} 分钟尝试触发一次全局事件（允许「无事件」），
 * 有 {@code eventCritChance} 概率触发「暴击」——同时来两个事件。
 * 当前事件通过一条常驻的 {@link ServerBossEvent}（Boss 血条式 HUD）展示给所有在线玩家，
 * 没事件时显示「距离下次事件」倒计时。
 *
 * <p>事件：
 * <ul>
 *   <li><b>青蛙雨</b>：事件期间每隔若干刻，在每名玩家头顶附近刷出青蛙。</li>
 *   <li><b>天降陨石</b>：事件期间每隔若干刻，在每名玩家附近引爆一枚陨石（爆炸）。</li>
 * </ul>
 */
public final class GlobalEvents {
	private GlobalEvents() {
	}

	private static final int TICKS_PER_MINUTE = 20 * 60;

	private static ServerBossEvent hud;
	private static long nextEventTick = -1;
	private static final List<ScheduledEvent> active = new ArrayList<>();

	private enum EventType {
		FROG_RAIN, METEOR_SHOWER
	}

	private static final class ScheduledEvent {
		final EventType type;
		final long startTick;
		final long endTick;

		ScheduledEvent(EventType type, long startTick, long endTick) {
			this.type = type;
			this.startTick = startTick;
			this.endTick = endTick;
		}
	}

	/** 注册每刻 tick 驱动。 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GlobalEvents::tick);
	}

	/** 关服时清掉 HUD 与进行中的事件。 */
	public static void reset() {
		if (hud != null) {
			hud.removeAllPlayers();
		}
		active.clear();
		nextEventTick = -1;
	}

	private static void tick(MinecraftServer server) {
		RandomDropsConfig config = RandomDropsConfig.get();
		if (!config.enableEvents) {
			return;
		}

		long now = server.getTickCount();
		if (nextEventTick < 0) {
			nextEventTick = now + Math.max(1, config.eventIntervalMinutes) * TICKS_PER_MINUTE;
		}

		// 到期事件清除
		active.removeIf(e -> now >= e.endTick);

		// 到点触发
		if (now >= nextEventTick) {
			trigger(server, config, now);
			nextEventTick = now + Math.max(1, config.eventIntervalMinutes) * TICKS_PER_MINUTE;
		}

		// 运行进行中的事件
		for (ScheduledEvent e : active) {
			runEvent(server, e, now, config);
		}

		updateHud(server, config, now);
	}

	private static void trigger(MinecraftServer server, RandomDropsConfig config, long now) {
		if (server.getPlayerList().getPlayerCount() == 0) {
			return; // 没人在场不触发，避免空转
		}

		RandomSource random = server.overworld().getRandom();
		if (random.nextDouble() >= config.eventChance) {
			return; // 允许无事件
		}

		List<EventType> pool = new ArrayList<>();
		if (config.enableFrogRain) {
			pool.add(EventType.FROG_RAIN);
		}
		if (config.enableMeteorShower) {
			pool.add(EventType.METEOR_SHOWER);
		}
		if (pool.isEmpty()) {
			return;
		}

		boolean crit = random.nextDouble() < config.eventCritChance;
		if (crit && pool.size() >= 2) {
			active.add(new ScheduledEvent(EventType.FROG_RAIN, now, now + config.frogRainDurationSeconds * 20L));
			active.add(new ScheduledEvent(EventType.METEOR_SHOWER, now, now + config.meteorDurationSeconds * 20L));
		} else {
			EventType t = pool.get(random.nextInt(pool.size()));
			long dur = (t == EventType.FROG_RAIN
					? config.frogRainDurationSeconds
					: config.meteorDurationSeconds) * 20L;
			active.add(new ScheduledEvent(t, now, now + dur));
		}
	}

	private static void runEvent(MinecraftServer server, ScheduledEvent e, long now, RandomDropsConfig config) {
		if (e.type == EventType.FROG_RAIN) {
			if (now % Math.max(1, config.frogRainIntervalTicks) == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					ServerLevel lvl = p.level();
					for (int i = 0; i < config.frogRainPerPlayer; i++) {
						double dx = (lvl.getRandom().nextDouble() * 2.0D - 1.0D) * config.frogRainRadius;
						double dz = (lvl.getRandom().nextDouble() * 2.0D - 1.0D) * config.frogRainRadius;
						BlockPos pos = p.blockPosition().offset((int) Math.floor(dx), 6, (int) Math.floor(dz));
						spawnFrogAt(lvl, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
					}
				}
			}
		} else {
			if (now % Math.max(1, config.meteorIntervalTicks) == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					ServerLevel lvl = p.level();
					double dx = (lvl.getRandom().nextDouble() * 2.0D - 1.0D) * config.meteorRadius;
					double dz = (lvl.getRandom().nextDouble() * 2.0D - 1.0D) * config.meteorRadius;
					BlockPos pos = p.blockPosition().offset((int) Math.floor(dx), 0, (int) Math.floor(dz));
					spawnMeteorAt(lvl, pos.getX() + 0.5, p.getY(), pos.getZ() + 0.5, config);
				}
			}
		}
	}

	/** 在指定坐标刷出一只青蛙（供事件与自检共用）。 */
	static void spawnFrogAt(ServerLevel level, double x, double y, double z) {
		EntityType<?> frogType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:frog"));
		if (!(frogType instanceof EntityType)) {
			return;
		}

		@SuppressWarnings("unchecked")
		EntityType<? extends Animal> ft = (EntityType<? extends Animal>) frogType;
		Frog frog = new Frog(ft, level);
		frog.setPos(x, y, z);
		level.addFreshEntity(frog);
	}

	/** 在指定坐标引爆一枚陨石（供事件与自检共用）。 */
	static void spawnMeteorAt(ServerLevel level, double x, double y, double z, RandomDropsConfig config) {
		level.explode(null, x, y, z, config.meteorExplosionRadius,
				config.meteorFire, Level.ExplosionInteraction.MOB);
	}

	private static void updateHud(MinecraftServer server, RandomDropsConfig config, long now) {
		if (!config.eventHudEnabled) {
			if (hud != null) {
				hud.setVisible(false);
			}
			return;
		}

		ensureHud();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			hud.addPlayer(p);
		}

		long intervalTicks = Math.max(1, config.eventIntervalMinutes) * TICKS_PER_MINUTE;

		if (active.isEmpty()) {
			long remain = Math.max(0, nextEventTick - now);
			int mins = (int) (remain / TICKS_PER_MINUTE);
			hud.setName(Component.literal("§e暂无事件 §7| 下次事件 " + mins + " 分"));
			hud.setProgress(intervalTicks > 0 ? (float) remain / (float) intervalTicks : 0.0F);
		} else {
			ScheduledEvent first = active.get(0);
			long total = Math.max(1, first.endTick - first.startTick);
			long remain = Math.max(0, first.endTick - now);
			String name = active.size() > 1
					? "青蛙雨 + 天降陨石"
					: (first.type == EventType.FROG_RAIN ? "青蛙雨" : "天降陨石");
			hud.setName(Component.literal("§c当前事件：" + name + " §7| 剩余 " + (remain / 20L) + "s"));
			hud.setProgress((float) remain / (float) total);
		}
		hud.setVisible(true);
	}

	private static ServerBossEvent ensureHud() {
		if (hud == null) {
			hud = new ServerBossEvent(UUID.randomUUID(),
					Component.literal("暂无事件"), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
			hud.setVisible(true);
		}
		return hud;
	}

	// ------------------------------------------------------------ 自检辅助

	/** 自检用：确保 HUD 已创建并返回。 */
	static ServerBossEvent ensureHudForTest() {
		return ensureHud();
	}

	/** 自检用：直接跑一次 HUD 更新逻辑。 */
	static void updateHudForTest(MinecraftServer server, RandomDropsConfig config, long now) {
		updateHud(server, config, now);
	}

	/** 自检用：当前进行中的事件数。 */
	static int activeEventCount() {
		return active.size();
	}

	/** 自检用：绕过「需有玩家在场」的限制，强制加入一个事件。 */
	static void forceTriggerNoPlayerCheck(MinecraftServer server, RandomDropsConfig config) {
		List<EventType> pool = new ArrayList<>();
		if (config.enableFrogRain) {
			pool.add(EventType.FROG_RAIN);
		}
		if (config.enableMeteorShower) {
			pool.add(EventType.METEOR_SHOWER);
		}
		if (pool.isEmpty()) {
			return;
		}

		long now = server.getTickCount();
		EventType t = pool.get(0);
		long dur = (t == EventType.FROG_RAIN
				? config.frogRainDurationSeconds
				: config.meteorDurationSeconds) * 20L;
		active.add(new ScheduledEvent(t, now, now + dur));
	}
}
