package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 随机换位（yunxigames swap 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-swap.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级后旧配置文件依然可用。
 *
 * <p><b>默认值原则「爽但不劝退」</b>：受伤换位很混沌，概率默认压在 15% ——
 * 一场战斗大概换一两次，够刺激又不至于打怪全靠信仰。
 */
public final class SwapConfig extends YgConfig {
	public static final String FILE_NAME = "yg-swap.json";

	// ---------- 受伤随机换位 ----------

	/** 总开关：受伤时按概率与附近随机一个活体互换位置。 */
	public boolean hurtSwapEnabled = true;

	/** 每次受伤触发换位的概率（0~1，默认 0.15）。 */
	public float hurtSwapChance = 0.15f;

	/** 换位对象的搜索半径（格，默认 48：一般战斗场景里「附近」的合理范围）。 */
	public double hurtSwapMaxRadius = 48.0;

	/** 换位对象是否包含其他玩家（PvP / 朋友互相坑，默认开）。 */
	public boolean hurtSwapIncludePlayers = true;

	/** 换位后清空双方摔落距离（换到悬崖边不会因为对方攒的摔落白送摔死 —— 爽但不劝退）。 */
	public boolean hurtSwapClearFallDistance = true;

	/** 换位发生时给双方玩家发提示 + 播末影人音效（反馈演出）。 */
	public boolean hurtSwapAnnounce = true;

	/**
	 * 生物黑名单：这些 id 不会被选为换位对象（Boss 换位等于传送到死）。
	 * 支持精确 id 与 {@code 命名空间:*} 通配 —— 第三方 mod 的 Boss 也可以在这里挡掉。
	 */
	public List<String> entityBlacklist = new ArrayList<>(List.of(
			"minecraft:ender_dragon", "minecraft:wither"));

	private transient YgConfig.IdFilter entityBlacklistFilter = YgConfig.IdFilter.EMPTY;

	public boolean isEntityBlacklisted(net.minecraft.resources.Identifier id) {
		return entityBlacklistFilter.matches(id);
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-swap.json");
	private static volatile SwapConfig instance;

	private SwapConfig() {
	}

	/** 自检专用：造一份全新默认配置（绕开单例，不落盘、不影响运行中的 instance）。 */
	static SwapConfig blankForTest() {
		return new SwapConfig();
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static SwapConfig get() {
		SwapConfig local = instance;
		if (local == null) {
			synchronized (SwapConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized SwapConfig load() {
		Path path = configPath(FILE_NAME);
		SwapConfig loaded = null;
		com.google.gson.JsonObject raw = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				// 先解析成 JsonObject 留底：merge 时用它区分「json 里没写这一项」和「明确写了 false」
				raw = GSON.fromJson(reader, com.google.gson.JsonObject.class);
				loaded = GSON.fromJson(raw, SwapConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-swap.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new SwapConfig();
		} else {
			mergeMissingTrueBooleans(loaded, raw);
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
			LOGGER.error("[yg-swap.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/**
	 * 补齐 json 里<b>确实缺失</b>的「默认值为 true」的布尔字段。
	 *
	 * <p><b>为什么必须补</b>：Gson 反序列化走 Unsafe 直接建对象、不执行字段初始化器 ——
	 * json 里没写的 {@code boolean} 会留在 JVM 默认值 {@code false} 而不是代码默认 {@code true}，
	 * 升级新增的开关在老配置上会静默关闭。
	 *
	 * <p><b>为什么先留底 {@link com.google.gson.JsonObject}</b>：只看反序列化结果无法区分
	 * 「json 缺项」和「玩家明确写了 false」—— 两者读进来都是 false。照搬其它包「默认 true 却读到
	 * false 就补回」的写法会把玩家明确关掉的开关又偷偷打开；这里用 {@code raw.has(字段名)}
	 * 判存在性，只补 json 里真的没写的项。新加「默认 true」的布尔开关时自动覆盖，不必单独处理。
	 */
	private static void mergeMissingTrueBooleans(SwapConfig loaded, com.google.gson.JsonObject raw) {
		if (raw == null) {
			return; // 没有留底（损坏文件走默认实例），无从判断缺项，保持原样
		}

		SwapConfig defaults = new SwapConfig();
		for (Field field : SwapConfig.class.getFields()) {
			if (field.getType() != boolean.class || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				if (field.getBoolean(defaults) && !field.getBoolean(loaded) && !raw.has(field.getName())) {
					field.setBoolean(loaded, true);
					LOGGER.info("[yg-swap.json] 老配置缺少新字段 {}，已补回默认值 true", field.getName());
				}
			} catch (ReflectiveOperationException e) {
				LOGGER.warn("[yg-swap.json] 补默认值时跳过字段 {}：{}", field.getName(), e.toString());
			}
		}
	}

	/** 修正越界 / 缺失的值，并解析黑名单过滤器（NaN 一并治：!(x>=lo && x<=hi) 对 NaN 恒真）。 */
	private void validate() {
		if (entityBlacklist == null) entityBlacklist = new ArrayList<>();
		entityBlacklistFilter = parseFilter(entityBlacklist, "entityBlacklist");

		if (!(hurtSwapChance > 0.0f && hurtSwapChance <= 1.0f)) hurtSwapChance = 0.15f;
		if (!(hurtSwapMaxRadius >= 8.0 && hurtSwapMaxRadius <= 256.0)) hurtSwapMaxRadius = 48.0;
	}
}
