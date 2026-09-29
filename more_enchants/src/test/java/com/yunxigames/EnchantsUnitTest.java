package com.yunxigames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
