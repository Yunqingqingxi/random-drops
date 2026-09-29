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

	@Test
	void missingPoleVaultFieldsFallBackToCodeDefaults() throws Exception {
		// 老配置文件里没有蓝银撑杆跳那几项：布尔不能被 Gson 读成 false（玩法静默消失），
		// 数值不能读成 0（杆长 0 会让整条钳制链钳到下界 2）
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertTrue(cfg.enablePoleVault, "缺项的撑杆跳总开关必须补回 true");
		assertTrue(cfg.poleVaultCushionedLanding, "缺项的落地缓冲必须补回 true");
		assertTrue(cfg.enablePoleVaultParticles, "缺项的粒子开关必须补回 true");
		assertEquals(5.0D, cfg.poleVaultLength, "缺项杆长补回 5 格，而不是 0");
		assertEquals(0.003D, cfg.poleVaultToppleNudge, 1.0E-12D, "缺项倒杆冲量补回代码默认");
	}
}
