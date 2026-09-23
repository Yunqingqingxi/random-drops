package com.yunxigames.mobs.mixin;

import com.yunxigames.MobsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幻翼 × 苦力怕混合生物的<b>音效替换</b>（yg-mobs 包）。
 *
 * <p>把幻翼的 ambient / hurt / death 三个音效换成本包自定义音频
 * （{@code yg_mobs:phantom_creeper}，即 res/bz.mp3 转码出的 ogg）。
 * 这三个方法 Phantom 自己就声明了，所以本 mixin 可以安全地直接挂 {@code Phantom}
 * （与爆炸那个必须挂 {@code Mob} 的 mixin 分开 —— 见 {@link PhantomCreeperMixin}）。
 *
 * <p>由 {@code phantomSoundBz} 配置项控制开关；关闭则原版幻翼音效照常播放。
 */
@Mixin(Phantom.class)
public abstract class PhantomCreeperSoundMixin {

	private static final Identifier SOUND_ID = Identifier.parse("yg_mobs:phantom_creeper");
	private static SoundEvent customSound;

	/** 懒加载自定义音效：等 YunxiGamesMobs 在 onInitialize 里注册完再取；取不到就回退原版。 */
	private static SoundEvent getCustomSound() {
		if (customSound == null) {
			// 26.2 的 Registry.get 返回 Optional<Reference<T>>，要 .value() 拆出实体对象
			customSound = BuiltInRegistries.SOUND_EVENT.get(SOUND_ID)
					.map(h -> h.value()).orElse(null);
		}
		return customSound;
	}

	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void yg$bzAmbient(CallbackInfoReturnable<SoundEvent> cir) {
		if (MobsConfig.get().phantomSoundBz) {
			SoundEvent se = getCustomSound();
			if (se != null) {
				cir.setReturnValue(se);
			}
		}
	}

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void yg$bzHurt(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		if (MobsConfig.get().phantomSoundBz) {
			SoundEvent se = getCustomSound();
			if (se != null) {
				cir.setReturnValue(se);
			}
		}
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void yg$bzDeath(CallbackInfoReturnable<SoundEvent> cir) {
		if (MobsConfig.get().phantomSoundBz) {
			SoundEvent se = getCustomSound();
			if (se != null) {
				cir.setReturnValue(se);
			}
		}
	}
}
