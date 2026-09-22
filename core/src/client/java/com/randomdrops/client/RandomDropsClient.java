package com.randomdrops.client;

import com.randomdrops.RandomDrops;
import net.fabricmc.api.ClientModInitializer;

public class RandomDropsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 掉落判定完全在服务端进行，客户端不需要做任何事。
		RandomDrops.LOGGER.info("[random-drops] 客户端已加载");
	}
}
