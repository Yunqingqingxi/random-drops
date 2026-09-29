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
 * 更多附魔（yunxigames enchants 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-enchants.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级后旧配置文件依然可用。每包配置相互独立。
 */
public final class EnchantsConfig extends YgConfig {
	public static final String FILE_NAME = "yg-enchants.json";

	// ================================================================ v1.12.0
	// 下面两组是 v1.12.0 的新功能：附魔突破（3 个新附魔）+ 全局事件系统（青蛙雨 / 天降陨石）。

	// --------------------------------------------- 附魔突破（总开关）

	/**
	 * <b>附魔突破总开关</b>（默认 true）。
	 *
	 * <p>三个新附魔：雷霆万钧（头盔）、臭脚（鞋）、碎裂（武器/工具）。关掉后
	 * 既不会在随机掉落里带出碎裂，穿戴/使用的特效也全部停用。
	 */
	public boolean enableEnchantmentBreakthrough = true;

	/** 雷霆万钧穿戴后，每隔多少游戏刻召唤一次雷击（默认 100 = 5 秒）。 */
	public int thunderIntervalTicks = 100;

	/** 雷雨天频率提升：每隔多少刻召唤一次（默认 20 = 1 秒）。 */
	public int thunderStormIntervalTicks = 20;

	/** 雷击覆盖半径（格，默认 32 = 2 个区块）。 */
	public double thunderRadius = 24.0D;

	/** 臭脚穿戴后，清理附近花草的半径（格，默认 8）。 */
	public double stinkyRadius = 8.0D;

	/** 臭脚给附近玩家施加的反胃时长（秒，默认 8）。 */
	public int stinkyNauseaSeconds = 8;

	/** 臭脚清理花草 + 刷新反胃的节奏（游戏刻，默认 20 = 1 秒）。 */
	public int stinkyTickInterval = 20;

	/**
	 * 臭脚的<b>隐藏 buff</b>：穿戴者附近更容易刷出亡灵生物。
	 *
	 * <p>每个节奏点按这个概率额外刷 1 只亡灵（默认 0.05 = 5%/秒），且附近亡灵数量超过
	 * {@link #stinkyUndeadCap} 时不再刷。亡灵本身<b>不吃反胃</b>（反而被吸引）。
	 */
	public double stinkyUndeadSpawnChance = 0.05D;

	/** 臭脚附近亡灵数量上限（默认 4），超过就不再刷。 */
	public int stinkyUndeadCap = 4;

	/**
	 * <b>碎裂</b>出现在随机掉落的武器/工具上的概率（默认 0.10 = 10%）。
	 *
	 * <p>只落在武器/工具类（剑、镐、斧、铲、锄、三叉戟、重锤、弓、弩、钓竿等）上，
	 * 普通方块/消耗品不带这个附魔。
	 */
	public double shatterApplyChance = 0.10D;

	/** 碎裂「每次使用（攻击 / 挖方块）」触发特效的概率（默认 0.15）。 */
	public double shatterProcChance = 0.15D;

	/**
	 * 触发特效后，负面（反噬）的比例（默认 0.25 = 触发里 1/4 是负面）。
	 *
	 * <p>正面 = 对目标秒杀 / 对方块秒破 + 顺手碎掉背包里一件物品；
	 * 负面 = 碎裂穿戴者<b>全身装备 + 手中武器/工具</b>。
	 */
	public double shatterNegativeChance = 0.25D;

	/** 正面特效里「顺手碎掉背包一件物品」的概率（默认 0.5，混沌风味）。 */
	public double shatterSelfShatterChance = 0.5D;

	// --------------------------------------------- 附魔突破二期（v1.13）

	/**
	 * <b>磁石</b>（v1.13 新附魔，任意护甲）：穿戴时定期把半径内掉落物吸向自己。
	 *
	 * <p>跳过还处于拾取延迟的掉落物（玩家自己 Q 丢的东西不会被立刻吸回来）。
	 * 随 {@link #enableEnchantmentBreakthrough} 总开关一起生效。
	 */
	public boolean enableMagnet = true;

	/** 磁石吸附半径（格，默认 8）。 */
	public double magnetRadius = 8.0D;

	/** 磁石吸附结算间隔（游戏刻，默认 5 —— 每秒 4 次）。 */
	public int magnetIntervalTicks = 5;

	/** 磁石每次结算给掉落物的朝向玩家速度系数（默认 0.35，太大会把物品甩过头）。 */
	public double magnetPullStrength = 0.35D;

	/**
	 * <b>贪婪</b>（v1.13 新附魔，挖掘类工具）：挖方块时按概率<b>额外</b>随机掉一件物品。
	 *
	 * <p>额外掉落走 {@code DropRandomizer.makeStack}（和随机掉落同一套规则，附魔书/药水都带真实数据）。
	 */
	public boolean enableGreed = true;

	/** 贪婪每次挖掘额外掉一件的概率（默认 0.10 = 10%）。 */
	public double greedExtraChance = 0.10D;

	/**
	 * <b>诅咒系总开关</b>（v1.13）：负重诅咒 / 易碎诅咒的<b>效果</b>开关。
	 *
	 * <p>注意：即使关掉效果，这两枚附魔作为注册表成员仍可能出现在随机附魔书里
	 * （名字仍是红色诅咒字）——只是穿了没任何副作用。想让池子里根本不出诅咒，
	 * 见 README「已知限制」：需要改 {@code DropRandomizer.enchantedBook} 的过滤。
	 */
	public boolean enableCursedEnchantments = true;

	/** 负重诅咒：全身移速降低比例（默认 0.20 = -20%，ADD_MULTIPLIED_TOTAL）。 */
	public double curseBurdenSpeedPenalty = 0.20D;

	/** 易碎诅咒：受到攻击时，一件已装备的护甲按此概率直接碎裂消失（默认 0.10）。 */
	public double curseFrailtyBreakChance = 0.10D;

	// --------------------------------------------- 附魔突破三期（v1.14）

	/**
	 * <b>击杀随机升级附魔</b>（v1.14）：玩家击杀生物后按概率让一件身上装备的本模组附魔 +1 级。
	 *
	 * <p>只升不降（I→II→III），满级（III）的装备不再进入候选。只升级<b>已穿戴/手持</b>的，
	 * 背包与附魔书不参与。全部自定义附魔（含诅咒）都吃这个机制 —— 升级诅咒是真实的抉择。
	 */
	public boolean enableEnchantLevelUp = true;

	/** 每次玩家击杀生物触发升级掷骰的概率（v1.14.1 起默认 15%，覆盖全部附魔）。 */
	public double killEnchantLevelUpChance = 0.15D;

	/**
	 * <b>汲取</b>（v1.14 新附魔，锐利武器）：命中时按「每级 1.5 半心」回复生命。
	 *
	 * <p>挂在攻击命中（AFTER_DAMAGE）上，目标死了或没造成伤害就不回血。
	 */
	public boolean enableLeech = true;

	/** 汲取每级回复的生命（半心为单位，1.5 = 每级 0.75 颗心，默认 1.5）。 */
	public double leechHealPerLevel = 1.5D;

	/**
	 * <b>疾风</b>（v1.14 新附魔，靴子）：移速 +5%×等级（与负重诅咒同属性、可并存抵消）。
	 */
	public boolean enableSwift = true;

	/**
	 * <b>威压</b>（v1.14 新附魔，头盔）：定期震慑半径（6 格 × 等级）内的敌对生物，
	 * 施加缓慢（强度 = 等级）。
	 */
	public boolean enableDread = true;

	// ---------- v1.14.1 图书管理员交易重做 ----------

	/**
	 * <b>图书管理员交易随机刷新</b>（v1.14.1）：每次右键打开交易都重掷，
	 * 卖「顶级附魔书」（全部种类、等级=各自 max_level），代价是随机物品 ×最多 3 个。
	 */
	public boolean enableLibrarianRefresh = true;

	/** 图书管理员每笔交易的代价物品数量上限（默认 3）。 */
	public int librarianMaxCost = 3;

	// --------------------------------------------- 蓝银撑杆跳（v1.1.0）

	/**
	 * <b>蓝银撑杆跳</b>（v1.1.0 新附魔，<b>只能附在木棍上</b>）总开关。
	 *
	 * <p>手持带本附魔的木棍右键：杆立在地上、人撑起来向前飞出去。关掉后右键无响应，
	 * 内存里的杆也会立刻清空（它们本来就只是粒子，不留世界状态）。
	 */
	public boolean enablePoleVault = true;

	/** 立杆高度上限（格，默认 5）：也就是「杆立起来 5 格高」；头顶净空不足时按净空缩短。 */
	public double poleVaultLength = 5.0D;

	/**
	 * 每级附魔额外提供的抬升高度（格/级，默认 1.5）。
	 *
	 * <p>蓝银草杆是一根弹簧：蓄能来自助跑，回弹时把多余的功还给撑杆人。等级越高蓄得越多，
	 * 但再高也翻不过自己撑的那根杆（见 {@code poleVaultLength} 的硬上限）。
	 */
	public double poleVaultApexPerLevel = 1.5D;

	/**
	 * 助跑动能折算成高度的效率（默认 1.0 = 不设损耗）。
	 *
	 * <p>MC 里重力大、跑速低，助跑本身只能贡献不到半格（疾跑约 0.49 格），所以高度主要靠杆；
	 * 助跑真正的意义在<b>水平动量</b>：跑多快就飞多远。
	 */
	public double poleVaultRunUpEfficiency = 1.0D;

	/**
	 * 起跳所需的最小水平速度（格/刻，默认 0.15）。
	 *
	 * <p>参考：走路约 0.216、疾跑约 0.28。低于这个值撑不起来 —— 站着不动没有动量，
	 * 物理上也跳不了撑杆跳。
	 */
	public double poleVaultMinRunUp = 0.15D;

	/** 起跳后保留的水平动量比例（默认 1.0 = 动量守恒）；调大像被杆甩出去，调小像原地拔高。 */
	public double poleVaultForwardRetain = 1.0D;

	/**
	 * 撑杆跳自己的落地是否免摔落伤害（默认 true）。
	 *
	 * <p>拦的是「这一跳造成的下坠」：起跳后 {@code CUSHION_TICKS} 内、人在空中时把摔落距离按住
	 * （fallDistance 是摔伤的唯一输入）。关掉就是硬核物理，从悬崖边撑出去自求多福。
	 */
	public boolean poleVaultCushionedLanding = true;

	/** 撑杆跳冷却（游戏刻，默认 60 = 3 秒），走原版物品冷却 —— 零状态、客户端能看到冷却条。 */
	public int poleVaultCooldownTicks = 60;

	/**
	 * 放手瞬间传给杆的反向角冲量（弧度/刻，默认 0.003）。
	 *
	 * <p>决定杆倒得多快：杆立在「不稳定平衡」上（α ∝ sinθ，θ≈0 时几乎不动），
	 * 所以它天生先是纹丝不动、随后越倒越快。默认值下约 1.7 秒倒平，与人上升的时间尺度对得上。
	 */
	public double poleVaultToppleNudge = 0.003D;

	/** 杆的粒子特效开关（默认 true）：关掉后只剩动作与音效，适合低配或嫌晃眼。 */
	public boolean enablePoleVaultParticles = true;


	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-enchants.json");
	private static volatile EnchantsConfig instance;

	EnchantsConfig() {  // 包内可见：单元测试与 YgConfig 缺项补回需要 new 默认实例
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static EnchantsConfig get() {
		EnchantsConfig local = instance;
		if (local == null) {
			synchronized (EnchantsConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized EnchantsConfig load() {
		Path path = configPath(FILE_NAME);
		EnchantsConfig loaded = null;
		com.google.gson.JsonObject raw = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				// 先解析成 JsonObject 留底：merge 用它区分「json 里没写这一项」和「明确写了值」
				raw = GSON.fromJson(reader, com.google.gson.JsonObject.class);
				loaded = GSON.fromJson(raw, EnchantsConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-enchants.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new EnchantsConfig();
		} else {
			mergeMissingFields(loaded, raw, new EnchantsConfig());
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
			LOGGER.error("[yg-enchants.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析各个 id 列表。 */
	void validate() {
		// min/max 钳制链对 NaN 会原样放行（Math.min/max 遇 NaN 返回 NaN），先回落默认值再钳
		thunderRadius = orDefaultIfNaN(thunderRadius, 24.0D);
		stinkyRadius = orDefaultIfNaN(stinkyRadius, 8.0D);
		magnetRadius = orDefaultIfNaN(magnetRadius, 8.0D);

		// ---- v1.12.0 附魔突破 ----
		if (!(thunderIntervalTicks >= 1)) thunderIntervalTicks = 100;
		thunderIntervalTicks = Math.min(20 * 600, thunderIntervalTicks);
		if (!(thunderStormIntervalTicks >= 1)) thunderStormIntervalTicks = 20;
		thunderStormIntervalTicks = Math.min(thunderIntervalTicks, thunderStormIntervalTicks);
		thunderRadius = Math.min(128.0D, Math.max(1.0D, thunderRadius));
		stinkyRadius = Math.min(32.0D, Math.max(1.0D, stinkyRadius));
		stinkyNauseaSeconds = Math.min(600, Math.max(1, stinkyNauseaSeconds));
		stinkyTickInterval = Math.min(20 * 60, Math.max(1, stinkyTickInterval));
		if (!(stinkyUndeadSpawnChance >= 0.0D)) stinkyUndeadSpawnChance = 0.0D;
		if (stinkyUndeadSpawnChance > 1.0D) stinkyUndeadSpawnChance = 1.0D;
		stinkyUndeadCap = Math.min(64, Math.max(0, stinkyUndeadCap));

		if (!(shatterApplyChance >= 0.0D)) shatterApplyChance = 0.0D;
		if (shatterApplyChance > 1.0D) shatterApplyChance = 1.0D;
		if (!(shatterProcChance >= 0.0D)) shatterProcChance = 0.0D;
		if (shatterProcChance > 1.0D) shatterProcChance = 1.0D;
		if (!(shatterNegativeChance >= 0.0D)) shatterNegativeChance = 0.0D;
		if (shatterNegativeChance > 1.0D) shatterNegativeChance = 1.0D;
		if (!(shatterSelfShatterChance >= 0.0D)) shatterSelfShatterChance = 0.0D;
		if (shatterSelfShatterChance > 1.0D) shatterSelfShatterChance = 1.0D;

		// ---- v1.13.0 ----
		magnetRadius = Math.min(32.0D, Math.max(1.0D, magnetRadius));
		magnetIntervalTicks = Math.min(20 * 60, Math.max(1, magnetIntervalTicks));
		if (!(magnetPullStrength >= 0.0D)) magnetPullStrength = 0.35D;
		magnetPullStrength = Math.min(2.0D, magnetPullStrength);
		if (!(greedExtraChance >= 0.0D)) greedExtraChance = 0.0D;
		if (greedExtraChance > 1.0D) greedExtraChance = 1.0D;
		if (!(curseBurdenSpeedPenalty >= 0.0D)) curseBurdenSpeedPenalty = 0.20D;
		curseBurdenSpeedPenalty = Math.min(0.9D, curseBurdenSpeedPenalty);
		if (!(curseFrailtyBreakChance >= 0.0D)) curseFrailtyBreakChance = 0.0D;
		if (curseFrailtyBreakChance > 1.0D) curseFrailtyBreakChance = 1.0D;

		// ---- v1.14.0 ----
		if (!(killEnchantLevelUpChance >= 0.0D)) killEnchantLevelUpChance = 0.0D;
		if (killEnchantLevelUpChance > 1.0D) killEnchantLevelUpChance = 1.0D;
		if (!(leechHealPerLevel >= 0.0D)) leechHealPerLevel = 1.5D;
		leechHealPerLevel = Math.min(10.0D, leechHealPerLevel);

		// 图书管理员交易
		if (librarianMaxCost < 1) librarianMaxCost = 3;
		librarianMaxCost = Math.min(16, librarianMaxCost);

		// ---- v1.1.0 蓝银撑杆跳 ----
		// 杆长决定「杆多高」，也是起跳高度的硬上限，所以下界不能低于 2（否则立杆就没意义了）
		poleVaultLength = orDefaultIfNaN(poleVaultLength, 5.0D);
		poleVaultLength = Math.min(16.0D, Math.max(2.0D, poleVaultLength));
		poleVaultApexPerLevel = orDefaultIfNaN(poleVaultApexPerLevel, 1.5D);
		poleVaultApexPerLevel = Math.min(8.0D, Math.max(0.0D, poleVaultApexPerLevel));
		poleVaultRunUpEfficiency = orDefaultIfNaN(poleVaultRunUpEfficiency, 1.0D);
		poleVaultRunUpEfficiency = Math.min(3.0D, Math.max(0.0D, poleVaultRunUpEfficiency));
		poleVaultMinRunUp = orDefaultIfNaN(poleVaultMinRunUp, 0.15D);
		poleVaultMinRunUp = Math.min(1.0D, Math.max(0.0D, poleVaultMinRunUp));
		poleVaultForwardRetain = orDefaultIfNaN(poleVaultForwardRetain, 1.0D);
		poleVaultForwardRetain = Math.min(3.0D, Math.max(0.0D, poleVaultForwardRetain));
		poleVaultCooldownTicks = Math.min(20 * 600, Math.max(0, poleVaultCooldownTicks));
		poleVaultToppleNudge = orDefaultIfNaN(poleVaultToppleNudge, 0.003D);
		poleVaultToppleNudge = Math.min(0.05D, Math.max(0.0D, poleVaultToppleNudge));
	}
}
