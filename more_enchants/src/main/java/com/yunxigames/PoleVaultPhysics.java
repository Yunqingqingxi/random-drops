package com.yunxigames;

/**
 * 「蓝银撑杆跳」的纯数学内核：起跳参数反解 + 立杆倒伏的刚体积分。
 *
 * <p><b>为什么这一层不引用任何 Minecraft 类型</b>（连 {@code Vec3} 都不用）：
 * 物理是玩法里唯一能被「钉死」的部分，做成纯 double 函数就能被普通 JUnit 直接测，
 * 不用起服务器、不用 mock 世界。世界状态（方块、粒子、玩家）由 {@link PoleVault} 喂进来。
 *
 * <p><b>为什么用 MC 的重力常数而不是现实世界的 9.8</b>：玩家的竖直运动每刻执行
 * {@code vy = (vy - 0.08) * 0.98}，所以「给多大初速能上多高」必须按这个积分器反解。
 * 连续解 {@code h = v²/(2g)} 在 MC 里只是量级估计 —— 每刻 2% 的阻力会吃掉约 7% 的高度，
 * 拿它当「峰值刚好顶到杆顶」的硬上限会顶穿（4 格高的闭式解 0.80，实际需要 0.90）。
 */
final class PoleVaultPhysics {
	private PoleVaultPhysics() {
	}

	/** MC 的重力加速度（格/刻²）—— 与玩家自由落体同一套常数。 */
	static final double GRAVITY = 0.08D;

	/** MC 每刻施加在竖直速度上的空气阻力。 */
	static final double DRAG = 0.98D;

	/**
	 * 握杆留白（格）：玩家高 1.8 格，脚底最高到杆顶下方 1 格时头顶正好与杆顶齐平。
	 *
	 * <p>这是「杆多高就只能跳多高」那条硬上限的来源 —— 它是人的尺寸，不是平衡旋钮，
	 * 所以写成常量而不是配置项。
	 */
	static final double HEADROOM = 1.0D;

	/** 二分反解的迭代次数（60 次足够把 double 逼到机器精度）。 */
	private static final int SOLVER_ITERATIONS = 60;

	/** 模拟用的最大刻数：防呆，正常抛物线的上升段不会超过 200 刻。 */
	private static final int MAX_TICKS = 400;

	/**
	 * 给定竖直初速，能升到多高（逐刻跑 MC 那套 {@code vy=(vy-0.08)*0.98; y+=vy}）。
	 *
	 * <p>返回的是「脚底上升的格数」，不含起跳点本身的高度。
	 */
	static double apexHeight(double vy) {
		double v = vy;
		double y = 0.0D;
		for (int i = 0; i < MAX_TICKS; i++) {
			v = (v - GRAVITY) * DRAG;
			if (v <= 0.0D) {
				break;
			}
			y += v;
		}
		return y;
	}

	/** 反解：要跳到 {@code apex} 格高，需要多大的竖直初速（apexHeight 对 vy 单调递增）。 */
	static double vyForApex(double apex) {
		if (!(apex > 0.0D)) {
			return 0.0D;
		}

		double lo = 0.0D;
		double hi = 1.0D;
		while (apexHeight(hi) < apex && hi < 64.0D) {
			hi *= 2.0D;
		}

		for (int i = 0; i < SOLVER_ITERATIONS; i++) {
			double mid = (lo + hi) / 2.0D;
			if (apexHeight(mid) < apex) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2.0D;
	}

	/** 助跑动能按 {@code η·v²/(2g)} 折算成的高度（格）。 */
	static double runUpHeight(double speed, double efficiency) {
		if (!(speed > 0.0D) || !(efficiency > 0.0D)) {
			return 0.0D;
		}
		return efficiency * speed * speed / (2.0D * GRAVITY);
	}

	/**
	 * 一次撑杆跳的起跳解算结果。
	 *
	 * @param vy              竖直初速（格/刻）
	 * @param horizontalSpeed 水平初速（格/刻）
	 * @param apex            预期峰值高度（格，已按杆顶封顶）
	 * @param refused         助跑不足，这一跳不成立
	 */
	record Launch(double vy, double horizontalSpeed, double apex, boolean refused) {
	}

	/**
	 * 起跳解算：把「蓝银草杆的弹性能 + 助跑动能」换成竖直初速，水平动量照搬助跑。
	 *
	 * <p>高度预算 {@code h = min(每级弹性能×等级 + η·v²/(2g), 杆长−留白)} ——
	 * 后半段就是「杆多高只能跳多高」：再大的能量也翻不过自己撑的那根杆。
	 *
	 * @param runUpSpeed      起跳瞬间的水平速度（格/刻）
	 * @param level           附魔等级（1~3）
	 * @param poleLength      杆长（格）
	 * @param apexPerLevel    每级杆子弹性能给的高度（格/级）
	 * @param runUpEfficiency 助跑动能转高度的效率
	 * @param minRunUp        起跳所需的最小水平速度
	 * @param forwardRetain   水平动量保留比例（1.0 = 动量守恒）
	 */
	static Launch solveLaunch(double runUpSpeed, int level, double poleLength,
			double apexPerLevel, double runUpEfficiency, double minRunUp, double forwardRetain) {
		// NaN / 负速度一律当 0：配置钳制链的历史坑就是 NaN 会穿透 min/max，这里把同一道防线
		// 钉在物理入口，绝不让 NaN 变成「一个很大的高度」
		double speed = runUpSpeed > 0.0D ? runUpSpeed : 0.0D;
		double retain = forwardRetain > 0.0D ? forwardRetain : 0.0D;
		double horizontal = speed * retain;

		// 站着不动是撑不起来杆的：没有水平动量，人只会原地蹬腿
		if (!(speed >= minRunUp)) {
			return new Launch(0.0D, horizontal, 0.0D, true);
		}

		double cap = Math.max(0.0D, poleLength - HEADROOM);
		double budget = apexPerLevel * Math.max(0, level) + runUpHeight(speed, runUpEfficiency);
		double apex = Math.min(Math.max(0.0D, budget), cap);
		return new Launch(vyForApex(apex), horizontal, apex, false);
	}

	/**
	 * 倒伏角加速度（弧度/刻²）：匀质细杆绕底端倒下，{@code α = 3g/(2L)·sinθ}。
	 *
	 * <p>θ 是与竖直方向的夹角。θ≈0 时 α≈0 —— 杆立在「不稳定平衡」上，所以它天生
	 * 先是纹丝不动、随后越倒越快，这段节奏和人上升的时间尺度天然对得上，不用手写缓动。
	 */
	static double toppleAlpha(double tilt, double length) {
		return 3.0D * GRAVITY / (2.0D * Math.max(0.5D, length)) * Math.sin(tilt);
	}

	/**
	 * 半隐式欧拉推进一刻（对倒立摆这种能量增长的刚体，显式欧拉会越算越飞）。
	 *
	 * @return {@code {新的 tilt, 新的 omega}}
	 */
	static double[] stepTopple(double tilt, double omega, double length, double maxTilt) {
		double nextOmega = omega + toppleAlpha(tilt, length);
		double nextTilt = tilt + nextOmega;
		if (nextTilt > maxTilt) {
			nextTilt = maxTilt;
		}
		return new double[] { nextTilt, nextOmega };
	}

	/**
	 * 杆倒平那一刻的角速度（能量守恒解析解 {@code ω = √(3g/L)}）。
	 *
	 * <p>自检拿它核对数值积分 —— 数值解跑偏了就说明积分器写错了。
	 */
	static double rodImpactOmega(double length) {
		return Math.sqrt(3.0D * GRAVITY / Math.max(0.5D, length));
	}
}
