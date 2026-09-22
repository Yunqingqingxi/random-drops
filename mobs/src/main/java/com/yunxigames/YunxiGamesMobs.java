package com.yunxigames;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生物改变模块入口（randomdrops-mobs）。
 *
 * <p>负责把自定义音效 {@code yg_mobs:phantom_creeper}（res/bz.mp3 转码出的 ogg）
 * 注册进声音注册表；并把「幻翼 × 苦力怕混合生物」相关的自检步骤挂进统一自检流程。
 *
 * <p>实际的<b>混合行为</b>（保留飞行 / 俯冲 + 获得苦力怕爆炸 + 音效替换）由
 * {@link com.yunxigames.mobs.mixin.PhantomCreeperMixin} 在运行时织入 {@code Phantom} 类，
 * 本类只做注册与自检挂载，不持有任何游戏逻辑，从而保持模块可独立嵌入其它项目。
 */
public class YunxiGamesMobs implements ModInitializer {
	public static final String MOD_ID = "yg_mobs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 自定义音效 id：幻翼原版 ambient/hurt/death 会被替换为它（见 config.phantomSoundBz）。 */
	public static final Identifier PHANTOM_CREEPER_SOUND = Identifier.parse("yg_mobs:phantom_creeper");

	@Override
	public void onInitialize() {
		// 注册自定义音效：固定范围事件，玩家在 16 格内都能听到。
		SoundEvent event = SoundEvent.createFixedRangeEvent(PHANTOM_CREEPER_SOUND, 16.0F);
		Registry.register(BuiltInRegistries.SOUND_EVENT, PHANTOM_CREEPER_SOUND, event);

		// 把生物相关的自检步骤挂进统一自检流程
		SelfTest.register(() -> MobsConfig.get().selfTestRolls);
		SelfTest.registerStep("㊲ 生物改动·幻翼×苦力怕混合",
				ctx -> MobSelfTest.checkPhantomCreeper(ctx.server, ctx.level, MobsConfig.get()));
		SelfTest.registerStep("㊳ 生物改动·自定义音效注册",
				ctx -> MobSelfTest.checkSoundRegistered(ctx.server, ctx.level, MobsConfig.get()));

		LOGGER.info("[yg-mobs] 生物模块已加载（幻翼×苦力怕混合 + 自定义音效）");
	}
}
