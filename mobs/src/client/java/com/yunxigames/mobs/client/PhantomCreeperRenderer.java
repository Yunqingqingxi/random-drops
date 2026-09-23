package com.yunxigames.mobs.client;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PhantomRenderer;

/**
 * 「苦力怕幻翼」的渲染器（yg-mobs 客户端）：保留幻翼的飞行姿态与翅膀，把头身换成苦力怕。
 *
 * <p><b>为什么是「继承 + 注册」而不是 Mixin 改渲染器</b>：原版 {@link PhantomRenderer} 的模型字段
 * {@code model} 声明在父类 {@code LivingEntityRenderer} 里（protected，非 final）。Mixin 的
 * {@code @Shadow} 只在该<b>目标类自身</b>查找字段、不会沿继承链找，所以「挂 PhantomRenderer 去 shadow
 * 父类字段」会直接报 {@code @Shadow field model was not located in the target class}，把渲染线程打死
 * （表现就是游戏能启动、但全程黑屏）。
 *
 * <p>而子类访问继承来的 protected 字段本就是普通 Java —— 于是这里只做两件事：
 * 继承原版渲染器（贴图、缩放、俯冲旋转、眼睛层全部原样保留），然后在构造末尾把模型换成混合模型。
 * 之后由 {@link YunxiGamesMobsClient} 把 {@code minecraft:phantom} 的渲染器注册成本类，
 * 因此<b>所有自然生成的幻翼</b>都会是苦力怕头身 + 幻翼翅膀。
 */
public class PhantomCreeperRenderer extends PhantomRenderer {

	public PhantomCreeperRenderer(EntityRendererProvider.Context context) {
		super(context);

		// 父类构造里已经建好了一份原版幻翼模型，这里直接替换掉。
		// 两个模型部件都从模型层烘焙：PHANTOM 提供翅膀 / 尾巴，CREEPER 提供头 / 身体。
		this.model = new PhantomCreeperModel(
				context.bakeLayer(ModelLayers.PHANTOM),
				context.bakeLayer(ModelLayers.CREEPER));
	}
}
