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
 * 更多生物（幻翼 × 苦力怕混合）（yunxigames mobs 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-mobs.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级后旧配置文件依然可用。每包配置相互独立。
 */
public final class MobsConfig extends YgConfig {
	public static final String FILE_NAME = "yg-mobs.json";

	// ---------- 生物改动（mobs 模块）：幻翼 × 苦力怕混合 ----------
	/**
	 * <b>幻翼 × 苦力怕混合生物</b>（v1.15，由 yg-mobs 模块实现）。
	 *
	 * <p>true：幻翼在保留原版飞行 / 俯冲能力的同时，俯冲命中目标时会引发一次爆炸
	 * （苦力怕的爆炸能力）。false：幻翼完全保持原版行为，模块只负责替换音效。
	 */
	public boolean phantomCreeperEnabled = true;

	/**
	 * 混合生物俯冲命中目标时的爆炸威力（≈ TNT 当量）。默认 3.0，与苦力怕持平；
	 * 调高会更炸、调 0 则只剩音效替换（等效于关闭爆炸）。
	 */
	public float phantomCreeperExplosionPower = 3.0F;

	/** 爆炸是否引燃火焰（默认 false，避免天上掉火球烧山）。 */
	public boolean phantomCreeperExplosionFire = false;

	/**
	 * 是否把幻翼的<b>原版音效</b>替换为自定义音频（{@code assets/yg_mobs/sounds/phantom_creeper.ogg}）。
	 * false 则保留原版幻翼音效，模块不触碰声音。
	 */
	public boolean phantomSoundBz = true;


	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-mobs.json");
	private static volatile MobsConfig instance;

	private MobsConfig() {
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static MobsConfig get() {
		MobsConfig local = instance;
		if (local == null) {
			synchronized (MobsConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized MobsConfig load() {
		Path path = configPath(FILE_NAME);
		MobsConfig loaded = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, MobsConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-mobs.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new MobsConfig();
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
			LOGGER.error("[yg-mobs.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析各个 id 列表。 */
	private void validate() {
		// 爆炸威力用 !(x >= 0) 顺带挡掉 NaN；允许设为 0（等效关闭爆炸，只剩音效替换）。
		if (!(phantomCreeperExplosionPower >= 0.0F)) phantomCreeperExplosionPower = 3.0F;
		phantomCreeperExplosionPower = Math.min(16.0F, phantomCreeperExplosionPower);
	}
}
