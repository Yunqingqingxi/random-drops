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
 * yg-bingo 回归测试：钉死 Gson 缺项补回与「显式 false 不可被偷改」的历史坑。
 */
class BingoRegressionTest {

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
		Files.writeString(configDir.resolve(BingoConfig.FILE_NAME),
				"{\"enableItemBingo\": true}");
		BingoConfig cfg = BingoConfig.load();
		assertTrue(cfg.enableKillBingo, "老配置缺 enableKillBingo 必须补回默认 true");
	}

	@Test
	void explicitFalseInJsonMustNotBeOverwritten() throws Exception {
		Files.writeString(configDir.resolve(BingoConfig.FILE_NAME),
				"{\"enableItemBingo\": false}");
		BingoConfig cfg = BingoConfig.load();
		assertFalse(cfg.enableItemBingo, "玩家明确写 false 必须保持 false（旧盲补写法会偷改成 true）");
	}

	@Test
	void missingBlacklistFallsBackToCodeDefault() throws Exception {
		Files.writeString(configDir.resolve(BingoConfig.FILE_NAME),
				"{\"enableItemBingo\": true}");
		BingoConfig cfg = BingoConfig.load();
		assertFalse(cfg.entityBlacklist.isEmpty(), "黑名单缺项应补回代码默认名单");
	}
}
