package com.yunxigames.mobs.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.EntityTypes;

/**
 * yg-mobs 的客户端入口：把原版幻翼的渲染器换成「苦力怕幻翼」渲染器。
 *
 * <p>外观属于客户端资源与渲染，服务端不参与 —— 所以这里只注册渲染器：
 * 打架判定（俯冲爆炸、俯冲音效）仍在服务端的 mixin 里，两边各管各的、互不影响。
 * 玩家没装本模组时，服务端行为照常，只是看到的还是原版幻翼外观。
 */
public class YunxiGamesMobsClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		// 注册到 minecraft:phantom 这个原版实体类型上 —— 因为这个模组不新增生物，
		// 是「改写原版幻翼」，所以自然生成 / 刷怪 / 已有存档里的幻翼统统变样。
		EntityRendererRegistry.register(EntityTypes.PHANTOM, PhantomCreeperRenderer::new);
	}
}
