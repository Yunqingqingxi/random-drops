package com.yunxigames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-enchants 回归测试：钉死 Gson 缺项补回与「显式 false 不可被偷改」的历史坑。
 */
class EnchantsRegressionTest {

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
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertTrue(cfg.enableLeech, "缺项布尔必须补回代码默认 true");
		assertTrue(cfg.enableSwift, "缺项布尔必须补回代码默认 true");
	}

	@Test
	void explicitFalseInJsonMustNotBeOverwritten() throws Exception {
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableGreed\": false}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertFalse(cfg.enableGreed, "玩家明确写 false 必须保持 false");
	}

	@Test
	void missingNumericFieldFallsBackToCodeDefaultNotZero() throws Exception {
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertEquals(8.0D, cfg.magnetRadius,
				"数值缺项应补回代码默认 8.0，而不是 Gson 的 0（min/max 钳制会放过 0 吗？不会，但默认值不为 0 的字段必须靠补回）");
	}
}
