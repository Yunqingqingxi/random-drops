package com.yunxigames;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * yunxigames 系列每包独立配置的公共基类。
 *
 * <p>每个玩法包（drops / enchants / events / bingo / mobs）都有自己的 {@code *Config}
 * 继承本类，读自己包的 {@code config/yg-<包名>.json}，相互完全独立 —— 装哪个包就只有哪份配置。
 *
 * <p>基类提供两样所有包通用的东西：
 * <ul>
 *   <li>{@link #debugLog}：调试日志总开关（写进每包各自的 json）；</li>
 *   <li>{@link #selfTestRolls}：开服自检掷骰次数，&gt;0 时开服自动跑一遍自检
 *       （跑完记得改回 0），也可以用各包命令手动触发。</li>
 * </ul>
 *
 * <p>另附 id 列表解析工具（{@link #parseIds} / {@link #parseFilter} 与 {@link IdFilter}），
 * 供子类的 validate() 把字符串列表规范化成可高效匹配的过滤器。
 */
public abstract class YgConfig {
	/** 调试日志总开关。 */
	public boolean debugLog = false;

	/** 开服自检每项掷骰次数；0 = 关闭自检。 */
	public int selfTestRolls = 0;

	/** 解析配置目录下的相对路径。 */
	protected static Path configPath(String fileName) {
		return FabricLoader.getInstance().getConfigDir().resolve(fileName);
	}

	protected static Set<Identifier> parseIds(List<String> raw, String field) {
		Set<Identifier> parsed = new LinkedHashSet<>();

		for (String entry : raw) {
			if (entry == null || entry.isBlank()) {
				continue;
			}

			String text = entry.trim().toLowerCase(Locale.ROOT);
			Identifier id = text.indexOf(':') < 0
					? Identifier.tryParse("minecraft:" + text)
					: Identifier.tryParse(text);

			if (id == null) {
				LoggerFactory.getLogger("yunxigames").warn("[yunxigames] {} 里的 \"{}\" 不是合法 id，已忽略", field, entry);
			} else {
				parsed.add(id);
			}
		}

		return Set.copyOf(parsed);
	}

	protected static IdFilter parseFilter(List<String> raw, String field) {
		Set<Identifier> exact = new LinkedHashSet<>();
		Set<String> namespaces = new LinkedHashSet<>();

		for (String entry : raw) {
			if (entry == null || entry.isBlank()) {
				continue;
			}

			String text = entry.trim().toLowerCase(Locale.ROOT);

			// worldedit:* —— 整个命名空间
			if (text.endsWith(":*")) {
				String namespace = text.substring(0, text.length() - 2);
				if (!namespace.isEmpty()) {
					namespaces.add(namespace);
				}
				continue;
			}

			Identifier id = text.indexOf(':') < 0
					? Identifier.tryParse("minecraft:" + text)
					: Identifier.tryParse(text);

			if (id == null) {
				LoggerFactory.getLogger("yunxigames").warn("[yunxigames] {} 里的 \"{}\" 不是合法 id，已忽略", field, entry);
			} else {
				exact.add(id);
			}
		}

		return new IdFilter(Set.copyOf(exact), Set.copyOf(namespaces));
	}

	/** 一份 id 过滤器：支持精确 id，以及整个命名空间（{@code 模组名:*}）。 */
	static final class IdFilter {
		static final IdFilter EMPTY = new IdFilter(Set.of(), Set.of());

		private final Set<Identifier> exact;
		private final Set<String> namespaces;

		private IdFilter(Set<Identifier> exact, Set<String> namespaces) {
			this.exact = exact;
			this.namespaces = namespaces;
		}

		boolean matches(Identifier id) {
			return id != null && (this.exact.contains(id) || this.namespaces.contains(id.getNamespace()));
		}
	}
}
