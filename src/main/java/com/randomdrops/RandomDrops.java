package com.randomdrops;

import com.randomdrops.command.RandomDropsCommand;
import com.randomdrops.EnchantmentEffects;
import com.randomdrops.GlobalEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomDrops implements ModInitializer {
	public static final String MOD_ID = "randomdrops";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RandomDropsConfig config = RandomDropsConfig.load();
		LOGGER.info("[random-drops] 配置载入完成：方块掉落={} 生物掉落={} 生物概率={} 权重(敌对/中立/友好)={}/{}/{}",
				config.enableBlockDrops, config.enableMobDrops, config.mobChance,
				config.hostileWeight, config.neutralWeight, config.passiveWeight);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				RandomDropsCommand.register(dispatcher));

		// 每个游戏刻重置「本 tick 最多生成多少只生物」的配额
		ServerTickEvents.START_SERVER_TICK.register(server -> DropRandomizer.resetTickBudget());

		// 爆出来的生物落地僵直的倒计时
		ServerTickEvents.END_SERVER_TICK.register(server -> MobStun.tick());

		// 关服时清掉保底计数，避免残留
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> DropRandomizer.clearPity());

		// 关服时也清掉精英表（下次开服就是新的一局）
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> EliteMobs.reset());

		// 关服时清掉击杀奖励的计数与效果池缓存
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> KillEffects.reset());

		// ---- v1.11 ----
		// 关服时清掉 TNT 冷却、徒手挖木头连击、断肢状态与掉落统计
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TntIgnition.reset();
			HarvestEvents.reset();
			LimbInjury.reset();
			DropTally.reset();
			DropMerger.reset();
			// v1.12：清掉附魔效果计时与全局事件 HUD
			EnchantmentEffects.reset();
			GlobalEvents.reset();
		});

		// 每秒一次：掉落统计播报 + 断肢到期 / 重生后重新贴合
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}

			DropTally.onTick(server);
			LimbInjury.tick(server);
		});

		// v1.12：雷霆万钧 / 臭脚 每刻结算（碎裂由攻击 / 破坏方块的钩子驱动）
		ServerTickEvents.END_SERVER_TICK.register(server -> EnchantmentEffects.tick(server));

		// 开服时清零「本局统计」——一局就是一局，不跨进程累计
		ServerLifecycleEvents.SERVER_STARTING.register(server -> SessionStats.reset());

		// 精英怪：出生时标记，死亡时掉宝藏
		EliteMobs.register();

		// 击杀奖励：玩家击杀生物时直接赋予随机药水效果
		KillEffects.register();

		// 末影龙被击杀 → 通关结算（标题 + 统计 + 宝藏雨）
		Finale.register();

		// v1.12：附魔突破（雷霆万钧 / 臭脚 / 碎裂）与全局事件系统
		EnchantmentEffects.register();
		GlobalEvents.register();

		SelfTest.register();
	}
}
