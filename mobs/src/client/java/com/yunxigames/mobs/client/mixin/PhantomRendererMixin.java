package com.yunxigames.mobs.client.mixin;

import com.yunxigames.mobs.client.PhantomCreeperModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.phantom.PhantomModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把幻翼的渲染模型换成「苦力怕幻翼」混合模型（yg-mobs 客户端）。
 *
 * <p>原版 {@link PhantomRenderer} 在构造时用 {@code PhantomModel} 初始化父类的
 * {@code model} 字段（{@code LivingEntityRenderer#model}，protected 非 final）。
 * 这里在构造末尾把它整个替换掉 —— 渲染器其余部分（贴图、缩放、旋转、眼睛层）
 * 都不动，所以幻翼的飞行姿态、体型变化等原有表现全部保留。
 *
 * <p>两个模型部件都从 {@code ModelLayerLocation} 烘焙：{@code PHANTOM} 提供翅膀 / 尾巴，
 * {@code CREEPER} 提供头 / 身体。
 */
@Mixin(PhantomRenderer.class)
public abstract class PhantomRendererMixin {

	@Shadow
	protected PhantomModel model;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void yg$useMixedModel(EntityRendererProvider.Context context, CallbackInfo ci) {
		ModelPart phantomRoot = context.bakeLayer(ModelLayers.PHANTOM);
		ModelPart creeperRoot = context.bakeLayer(ModelLayers.CREEPER);
		this.model = new PhantomCreeperModel(phantomRoot, creeperRoot);
	}
}
