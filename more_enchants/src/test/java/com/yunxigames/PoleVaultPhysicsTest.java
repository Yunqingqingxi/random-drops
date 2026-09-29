package com.yunxigames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蓝银撑杆跳的物理内核测试。
 *
 * <p>这一层刻意不碰任何 Minecraft 类型（{@link PoleVaultPhysics} 只有 double），
 * 所以能在纯 JUnit 里把物理钉死：峰值反解自洽、杆顶硬上限、水平动量守恒、助跑门槛、
 * 以及倒杆的能量守恒 —— 真服务器上只能看个热闹，精度得靠这里。
 */
class PoleVaultPhysicsTest {

	/** 与 {@link PoleVaultPhysics} 默认值一致的默认参数，省得每处都抄一遍。 */
	private static PoleVaultPhysics.Launch launch(double runUp, int level, double poleLength) {
		return PoleVaultPhysics.solveLaunch(runUp, level, poleLength, 1.5D, 1.0D, 0.15D, 1.0D);
	}

	@Test
	void apexSolverIsSelfConsistent() {
		for (double apex : new double[] { 0.5D, 1.0D, 2.0D, 3.0D, 4.0D }) {
			double vy = PoleVaultPhysics.vyForApex(apex);
			assertEquals(apex, PoleVaultPhysics.apexHeight(vy), 1.0E-6D,
					"反解出来的初速跑回积分器必须正好落在 " + apex + " 格");
		}
	}

	@Test
	void solverUsesMcIntegratorNotClosedForm() {
		// MC 的每刻阻力让实际需要的初速比闭式解 √(2gh) 高一成以上 ——
		// 用闭式解算「刚好顶到杆顶」的初速，人就会顶穿杆顶
		double closedForm = Math.sqrt(2.0D * PoleVaultPhysics.GRAVITY * 4.0D);
		assertTrue(PoleVaultPhysics.vyForApex(4.0D) > closedForm * 1.05D,
				"必须按 MC 的积分器反解，而不是连续解 √(2gh)");
	}

	@Test
	void apexNeverExceedsPoleTop() {
		// 满级 + 疾跑：预算 1.5×3 + 0.49 = 4.99 格，但杆只有 5 格，脚底最多到杆顶下一格
		PoleVaultPhysics.Launch full = launch(0.28D, 3, 5.0D);
		assertEquals(5.0D - PoleVaultPhysics.HEADROOM, full.apex(), 1.0E-9D,
				"再大的能量也翻不过自己撑的那根杆");
		assertTrue(PoleVaultPhysics.apexHeight(full.vy()) <= 4.0D + 1.0E-6D);

		// 没到上限时按预算走（I 级：1.5 + 0.49 = 1.99）
		PoleVaultPhysics.Launch low = launch(0.28D, 1, 5.0D);
		assertEquals(1.5D + PoleVaultPhysics.runUpHeight(0.28D, 1.0D), low.apex(), 1.0E-9D);
	}

	@Test
	void horizontalMomentumIsConserved() {
		PoleVaultPhysics.Launch fast = launch(0.28D, 2, 5.0D);
		assertEquals(0.28D, fast.horizontalSpeed(), 1.0E-12D, "水平动量照搬助跑（保留系数 1.0）");
		assertFalse(fast.refused());

		// 保留系数是可调的平衡旋钮：调大就像被杆甩出去
		PoleVaultPhysics.Launch thrown = PoleVaultPhysics.solveLaunch(
				0.28D, 2, 5.0D, 1.5D, 1.0D, 0.15D, 1.5D);
		assertEquals(0.42D, thrown.horizontalSpeed(), 1.0E-12D);
	}

	@Test
	void standingStillIsRefused() {
		assertTrue(launch(0.0D, 3, 5.0D).refused(), "站着不动撑不起来");
		assertTrue(launch(0.14D, 3, 5.0D).refused(), "差一点点也不够");
		assertFalse(launch(0.15D, 3, 5.0D).refused(), "刚好到门槛就该放行");
		assertTrue(launch(0.20D, 1, 2.0D).apex() <= 2.0D - PoleVaultPhysics.HEADROOM + 1.0E-9D,
				"矮杆的封顶一样要吃住");
	}

	@Test
	void toppleFollowsEnergyConservation() {
		double length = 5.0D;
		double omega = 0.003D;
		double tilt = 0.0D;
		int ticks = 0;
		while (tilt < Math.PI / 2.0D - 1.0E-9D && ticks < 600) {
			double[] next = PoleVaultPhysics.stepTopple(tilt, omega, length, Math.PI / 2.0D);
			tilt = next[0];
			omega = next[1];
			ticks++;
		}

		assertEquals(Math.PI / 2.0D, tilt, 1.0E-9D, "杆必须真的倒平");
		double analytic = PoleVaultPhysics.rodImpactOmega(length);
		assertEquals(analytic, omega, analytic * 0.2D,
				"倒平那一刻的角速度要对得上能量守恒解析解 √(3g/L)，数值积分跑偏就是积分器写错了");
		assertTrue(ticks > 10 && ticks < 200, "5 格杆的倒伏应该是一两秒的事，实际 " + ticks + " 刻");
	}

	@Test
	void toppleStartsSlowThenAccelerates() {
		// 倒立摆的特征：α ∝ sinθ，θ≈0 时几乎不动 —— 这正是「杆先直挺着、人升上去，再倒」的物理来源
		double length = 5.0D;
		double omega = 0.003D;
		double tilt = 0.0D;
		int total = 0;
		while (tilt < Math.PI / 2.0D - 1.0E-9D && total < 600) {
			double[] next = PoleVaultPhysics.stepTopple(tilt, omega, length, Math.PI / 2.0D);
			tilt = next[0];
			omega = next[1];
			total++;
		}

		double quarter = sampleTilt(length, total / 4);
		double threeQuarters = sampleTilt(length, total * 3 / 4);
		assertTrue(quarter < 0.15D, "前 1/4 时间杆几乎还是直的，实际 " + quarter + " 弧度");
		assertTrue(Math.PI / 2.0D - threeQuarters > quarter * 5.0D,
				"越倒越快：最后 1/4 转过的角度必须远大于最开始的");
	}

	@Test
	void shorterPoleFallsFaster() {
		// 同样的重力，短杆转得更快（ω = √(3g/L)）—— 顺手也验证了杆长真的进了物理
		assertTrue(PoleVaultPhysics.rodImpactOmega(3.0D) > PoleVaultPhysics.rodImpactOmega(5.0D));
	}

	/** 从静止倒到第 ticks 刻时的倾角（测试辅助）。 */
	private static double sampleTilt(double length, int ticks) {
		double omega = 0.003D;
		double tilt = 0.0D;
		for (int i = 0; i < ticks; i++) {
			double[] next = PoleVaultPhysics.stepTopple(tilt, omega, length, Math.PI / 2.0D);
			tilt = next[0];
			omega = next[1];
		}
		return tilt;
	}
}
