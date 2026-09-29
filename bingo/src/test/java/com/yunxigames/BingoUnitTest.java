package com.yunxigames;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-bingo 单元测试：validate() 钳制与黑名单过滤的纯逻辑验证。
 */
class BingoUnitTest {

	@Test
	void rewardCountsAreClampedBothWays() {
		BingoConfig cfg = new BingoConfig();
		cfg.bingoLineRewardCount = -5;
		cfg.bingoClearRewardCount = -1;
		cfg.validate();
		assertEquals(2, cfg.bingoLineRewardCount, "负连线奖励回落默认 2");
		assertEquals(8, cfg.bingoClearRewardCount, "负清板奖励回落默认 8");
	}

	@Test
	void oversizedRewardCountsAreClamped() {
		BingoConfig cfg = new BingoConfig();
		cfg.bingoLineRewardCount = 99;
		cfg.bingoClearRewardCount = 999;
		cfg.validate();
		assertEquals(16, cfg.bingoLineRewardCount);
		assertEquals(48, cfg.bingoClearRewardCount);
	}

	@Test
	void nullEntityBlacklistIsRepaired() {
		BingoConfig cfg = new BingoConfig();
		cfg.entityBlacklist = null;
		cfg.validate();
		assertNotNull(cfg.entityBlacklist);
	}

	@Test
	void entityBlacklistSupportsNamespaceWildcard() {
		BingoConfig cfg = new BingoConfig();
		cfg.entityBlacklist = new ArrayList<>(List.of("somemod:*", "minecraft:ender_dragon"));
		cfg.validate();

		assertTrue(cfg.isEntityBlacklisted(Identifier.parse("somemod:anything")),
				"命名空间通配应拦下整个 somemod（第三方 mod 兼容约定）");
		assertFalse(cfg.isEntityBlacklisted(Identifier.parse("othermod:anything")));
		assertTrue(cfg.isEntityBlacklisted(Identifier.parse("minecraft:ender_dragon")));
	}
}
