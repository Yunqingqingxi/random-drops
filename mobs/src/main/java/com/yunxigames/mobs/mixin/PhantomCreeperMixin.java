package com.yunxigames.mobs.mixin;

import com.yunxigames.MobsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幻翼 × 苦力怕混合生物（由 randomdrops-mobs 模块提供）。
 *
 * <p>设计原则：<b>保住幻翼原能力</b>（飞行、俯冲、原有的 ambient / hurt / death 行为），
 * 只<b>额外</b>叠加苦力怕的爆炸能力，并可选地把音效换成自定义音频。
 *
 * <p>爆炸：幻翼俯冲命中目标（{@code Mob#doHurtTarget}）时，在自身位置引爆一次
 * {@link Level.ExplosionInteraction#MOB} 爆炸。为避免它每次俯冲都把自己炸死
 * （那样就失去「幻翼原能力」了），引爆前临时设为无敌，炸完再还原 ——
 * 只有目标与周围方块 / 实体受损，幻翼自己存活、继续飞行。
 *
 * <p>音效：把幻翼的 ambient / hurt / death 三个音效换成本模块自定义音频
 * （{@code yg_mobs:phantom_creeper}，即 res/bz.mp3 转码出的 ogg）。
 * 由 {@code phantomSoundBz} 配置项控制开关；关闭则原版幻翼音效照常播放。
 */
@Mixin(Phantom.class)
public abstract class PhantomCreeperMixin {

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

	/** 俯冲命中即引爆 —— 苦力怕的爆炸能力。 */
	@Inject(method = "doHurtTarget", at = @At("HEAD"))
	private void yg$explodeOnHit(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!MobsConfig.get().phantomCreeperEnabled) {
			return;
		}

		float power = MobsConfig.get().phantomCreeperExplosionPower;
		if (power <= 0.0F) {
			return;
		}

		boolean fire = MobsConfig.get().phantomCreeperExplosionFire;

		// 临时无敌：避免混合生物每次俯冲都把自己炸死，保住「幻翼原能力」。
		Phantom self = (Phantom) (Object) this;
		boolean wasInvuln = self.isInvulnerable();
		self.setInvulnerable(true);
		try {
			level.explode(self, self.getX(), self.getY(), self.getZ(),
					power, fire, Level.ExplosionInteraction.MOB);
		} finally {
			self.setInvulnerable(wasInvuln);
		}
	}
}
