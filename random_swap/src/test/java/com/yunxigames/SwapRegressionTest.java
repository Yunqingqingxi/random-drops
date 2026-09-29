package com.yunxigames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-swap 回归测试：钉死 Gson 缺项补回与「显式 false 不可被偷改」的历史坑。
 * 本包从诞生起就用 JsonObject 存在性检查，这里保证它不退化。
 */
class SwapRegressionTest {

	@TempDir
	Path configDir;

	@BeforeEach
	void injectConfigDir() {
		YgConfig.configDirOverride = configDir;
	}

	@AfterEach
	void resetConfigDir() {
		YgConfig.configDirOverride = null;
	}

	@Test
	void missingBooleanFieldsFallBackToCodeDefaultTrue() throws Exception {
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME),
				"{\"hurtSwapEnabled\": true}");
		SwapConfig cfg = SwapConfig.load();
		assertTrue(cfg.hurtSwapIncludePlayers, "缺项布尔必须补回代码默认 true");
		assertTrue(cfg.hurtSwapAnnounce, "缺项布尔必须补回代码默认 true");
	}

	@Test
	void explicitFalseInJsonMustNotBeOverwritten() throws Exception {
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME),
				"{\"hurtSwapEnabled\": true, \"hurtSwapIncludePlayers\": false}");
		SwapConfig cfg = SwapConfig.load();
		assertFalse(cfg.hurtSwapIncludePlayers, "玩家明确写 false 必须保持 false");
	}

	@Test
	void corruptedConfigFallsBackToDefaults() throws Exception {
		Files.writeString(configDir.resolve(SwapConfig.FILE_NAME), "{{{不是json");
		SwapConfig cfg = SwapConfig.load();
		assertTrue(cfg.hurtSwapEnabled, "损坏文件应回退到默认配置而不是崩溃");
		assertFalse(cfg.entityBlacklist.isEmpty(), "默认黑名单（龙/凋灵）应就位");
	}
}
