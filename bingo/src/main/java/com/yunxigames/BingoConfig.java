package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bingo（物品 / 击杀集卡）（yunxigames bingo 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-bingo.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级后旧配置文件依然可用。每包配置相互独立。
 */
public final class BingoConfig extends YgConfig {
	public static final String FILE_NAME = "yg-bingo.json";

	// ---------- Bingo（物品 / 击杀集卡） ----------

	/**
	 * <b>物品 Bingo</b>（v1.14）：随机 25 件主池物品的 5×5 板，玩家把它们捡进背包盖章；
	 * 横 / 竖 / 斜连线发奖，全清大奖后换新一局。进度绘制在<b>锁定的地图</b>上，手持查看。
	 */
	public boolean enableItemBingo = true;

	/**
	 * <b>击杀 Bingo</b>（v1.14）：随机 25 种生物的 5×5 板，击杀盖章；规则同物品板。
	 */
	public boolean enableKillBingo = true;

	/** 每点亮一条 Bingo 线奖励的宝藏件数（默认 2）。 */
	public int bingoLineRewardCount = 2;

	/** 25 格全清（Bingo）大奖的宝藏件数（默认 8）。 */
	public int bingoClearRewardCount = 8;


	/**
	 * 生物黑名单：这些 id 不进 Bingo 的击杀目标（Boss / 特殊生物不该被集卡）。
	 * 支持精确 id 与 {@code 命名空间:*} 通配。
	 */
	public List<String> entityBlacklist = new ArrayList<>(List.of(
			"minecraft:ender_dragon", "minecraft:wither", "minecraft:giant",
			"minecraft:illusioner", "minecraft:player"));

	private transient IdFilter entityBlacklistFilter = IdFilter.EMPTY;

	public boolean isEntityBlacklisted(Identifier id) {
		return entityBlacklistFilter.matches(id);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-bingo.json");
	private static volatile BingoConfig instance;

	BingoConfig() {  // 包内可见：单元测试与 YgConfig 缺项补回需要 new 默认实例
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static BingoConfig get() {
		BingoConfig local = instance;
		if (local == null) {
			synchronized (BingoConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized BingoConfig load() {
		Path path = configPath(FILE_NAME);
		BingoConfig loaded = null;
		com.google.gson.JsonObject raw = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				// 先解析成 JsonObject 留底：merge 用它区分「json 里没写这一项」和「明确写了值」
				raw = GSON.fromJson(reader, com.google.gson.JsonObject.class);
				loaded = GSON.fromJson(raw, BingoConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-bingo.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new BingoConfig();
		} else {
			mergeMissingFields(loaded, raw, new BingoConfig());
		}

		loaded.validate();
		instance = loaded;
		loaded.save();
		return loaded;
	}

	/** 把当前配置写回磁盘。 */
	public synchronized void save() {
		Path path = configPath(FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("[yg-bingo.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析各个 id 列表。 */
	void validate() {
		if (entityBlacklist == null) entityBlacklist = new ArrayList<>();
		entityBlacklistFilter = parseFilter(entityBlacklist, "entityBlacklist");

		// Bingo 奖励规模
		if (bingoLineRewardCount < 0) bingoLineRewardCount = 2;
		bingoLineRewardCount = Math.min(16, bingoLineRewardCount);
		if (bingoClearRewardCount < 0) bingoClearRewardCount = 8;
		bingoClearRewardCount = Math.min(48, bingoClearRewardCount);
	}
}
