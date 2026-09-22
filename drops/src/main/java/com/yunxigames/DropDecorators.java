package com.yunxigames;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 掉落物品装饰钩子（随机掉落包的扩展点）。
 *
 * <p>随机掉落的每个物品生成到最后一步，会依次经过已注册的装饰器 —— 「更多附魔」包
 * 就是在这里给武器 / 工具按概率附着碎裂附魔的。随机掉落包不依赖任何玩法包：
 * 没装更多附魔时列表为空，什么都不发生。
 */
public final class DropDecorators {
	/** 装饰器：对即将掉出的物品做最后加工（附魔 / 组件等）。 */
	public interface Decorator {
		void decorate(ItemStack stack, Item item, ServerLevel level, RandomSource random);
	}

	private static final List<Decorator> DECORATORS = new ArrayList<>();

	/** 由各玩法包在初始化时注册。 */
	public static void register(Decorator decorator) {
		DECORATORS.add(decorator);
	}

	/** 随机掉落生成物品的最后一步调用。 */
	public static void apply(ItemStack stack, Item item, ServerLevel level, RandomSource random) {
		for (Decorator decorator : DECORATORS) {
			decorator.decorate(stack, item, level, random);
		}
	}

	private DropDecorators() {
	}
}
