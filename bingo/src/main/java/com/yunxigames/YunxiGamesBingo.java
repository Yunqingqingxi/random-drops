package com.yunxigames;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bingo 包入口（yg_bingo）：物品与击杀双板集卡，地图绘制 + 连线奖励。
 *
 * <p>本包自带 yg-core 基础库与自己的配置（{@code config/yg-bingo.json}），可独立安装。
 * 物品池借用随机掉落包的物品池接口时保持可选依赖 —— 没装随机掉落也能跑（用原版物品池）。
 */
public class YunxiGamesBingo implements ModInitializer {
	public static final String MOD_ID = "yg_bingo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BingoConfig.load();

		Bingos.register();

		// 关服清掉 Bingo 缓存（下次开服就是新的一局）
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> Bingos.reset());

		// 自检
		SelfTest.registerStep("㉛ Bingo·双板+地图+连线结构",
				ctx -> BingoSelfTest.checkBingo(ctx.server, ctx.level, BingoConfig.get()));
		SelfTest.register(() -> BingoConfig.get().selfTestRolls);

		LOGGER.info("[yg-bingo] Bingo 已加载");
	}
}
