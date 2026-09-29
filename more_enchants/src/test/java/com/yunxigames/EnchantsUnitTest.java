package com.yunxigames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-enchants 单元测试：validate() 钳制的纯逻辑验证。
 * 特别覆盖 min/max 钳制链的 NaN 盲区（Math.min/max 遇 NaN 原样放行）。
 */
class EnchantsUnitTest {

	@Test
	void nanRadiusFallsBackToCodeDefault() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.thunderRadius = Double.NaN;
		cfg.validate();
		assertEquals(24.0D, cfg.thunderRadius, "NaN 必须先回落默认值再钳制");
	}

	@Test
	void nanStinkyAndMagnetRadiusFallBack() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.stinkyRadius = Double.NaN;
		cfg.magnetRadius = Double.NaN;
		cfg.validate();
		assertEquals(8.0D, cfg.stinkyRadius);
		assertEquals(8.0D, cfg.magnetRadius);
	}

	@Test
	void outOfRangeValuesAreClamped() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.magnetRadius = -5.0D;
		cfg.thunderIntervalTicks = 0;
		cfg.curseBurdenSpeedPenalty = 5.0D;
		cfg.validate();
		assertEquals(1.0D, cfg.magnetRadius, "低于下界钳到下界");
		assertEquals(100, cfg.thunderIntervalTicks);
		assertEquals(0.9D, cfg.curseBurdenSpeedPenalty, "高于上界钳到上界");
	}

	@Test
	void nanProbabilityStyleFieldsFallToDefault() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.curseBurdenSpeedPenalty = Double.NaN;
		cfg.shatterApplyChance = Double.NaN;
		cfg.validate();
		assertEquals(0.20D, cfg.curseBurdenSpeedPenalty, "!(x>=lo) 写法对 NaN 恒真，应落默认值");
		assertEquals(0.0D, cfg.shatterApplyChance);
	}

	@Test
	void poleVaultFieldsAreClampedAndNaNGuarded() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.poleVaultLength = Double.NaN;      // NaN 必须先回落默认值，再进 min/max 链
		cfg.poleVaultApexPerLevel = -3.0D;
		cfg.poleVaultRunUpEfficiency = 99.0D;
		cfg.poleVaultMinRunUp = 5.0D;
		cfg.poleVaultForwardRetain = -1.0D;
		cfg.poleVaultCooldownTicks = -20;
		cfg.poleVaultToppleNudge = Double.NaN;
		cfg.validate();

		assertEquals(5.0D, cfg.poleVaultLength, "NaN 杆长回落默认 5（杆长同时是起跳高度的硬上限）");
		assertEquals(0.0D, cfg.poleVaultApexPerLevel, "负的弹性能钳到 0");
		assertEquals(3.0D, cfg.poleVaultRunUpEfficiency, "效率上界 3");
		assertEquals(1.0D, cfg.poleVaultMinRunUp, "门槛上界 1.0 格/刻");
		assertEquals(0.0D, cfg.poleVaultForwardRetain, "负的动量保留钳到 0");
		assertEquals(0, cfg.poleVaultCooldownTicks, "负冷却钳到 0");
		assertEquals(0.003D, cfg.poleVaultToppleNudge, 1.0E-12D, "NaN 倒杆冲量回落默认");
	}

	@Test
	void NaNRunUpSpeedStillRefusesToLaunch() {
		// 玩家速度理论上不会 NaN，但配置钳制链的历史坑就是「NaN 会穿透 min/max」，
		// 这里把同一类防线钉在物理入口上：NaN 助跑速度既不能算出 NaN 高度，也不能放行
		PoleVaultPhysics.Launch nan = PoleVaultPhysics.solveLaunch(
				Double.NaN, 3, 5.0D, 1.5D, 1.0D, 0.15D, 1.0D);
		assertTrue(nan.refused(), "NaN 速度必须判为「撑不起来」而不是放行");
	}
}
