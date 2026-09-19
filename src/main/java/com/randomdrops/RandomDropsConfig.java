package com.randomdrops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

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
 * random-drops 的配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/random-drops.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级模组后旧配置文件依然可用。
 */
public final class RandomDropsConfig {
	public static final String FILE_NAME = "random-drops.json";

	// ---------------------------------------------------------------- 总开关

	/** 是否接管方块掉落（挖泥土、炸石头、活塞推方块……）。 */
	public boolean enableBlockDrops = true;

	/** 是否接管生物死亡的掉落表。玩家死亡永远不受影响；经验值与装备掉落也不受影响。 */
	public boolean enableMobDrops = true;

	/**
	 * true：只有玩家亲手破坏方块才可能掉出生物，爆炸 / 活塞 / 其它模组造成的破坏只掉物品。
	 *
	 * <p>默认 false —— 也就是「所有掉落都遵循同一套随机规则」，不管方块是被谁破坏的。
	 * 如果服务器上有人喜欢用 TNT 大面积爆破，可以把这项改成 true 来避免一次爆炸刷出太多怪。
	 */
	public boolean spawnMobsOnlyFromPlayers = false;

	// ------------------------------------------------------------ 概率与权重

	/**
	 * <b>断链开关</b>（默认 true）：只有<b>玩家击杀</b>的生物，死亡时才可能爆出新生物。
	 *
	 * <p>这是防止指数爆炸的关键。关掉它就会形成自持循环：
	 * 生物死亡 → 爆出敌对生物 → 它去杀别的生物 → 又产生死亡 → 又爆生物……
	 * 按默认数值算，每只爆出来的僵尸一生杀 5 只生物、每只死亡期望爆出
	 * {@code 15% × 2 = 0.3} 只，后代数 {@code 5 × 0.3 = 1.5 > 1}，会指数增长。
	 *
	 * <p>打开后每只新生物都必须由一次玩家击杀换来，期望后代数 0.3 &lt; 1，链条自然收敛。
	 * 被爆炸 / 火焰 / 摔落 / 其它生物杀死的生物仍然照常掉随机物品，只是不再爆生物。
	 */
	public boolean mobSpawnRequiresPlayerKill = true;

	/**
	 * 兜底限速：{@link #mobSpawnRateWindowSeconds} 秒内，掉落最多生成多少只生物；0 = 不限。
	 *
	 * <p>它不是防指数爆炸的必需项（那由 {@link #mobSpawnRequiresPlayerKill} 保证），
	 * 只是给服务器资源兜底，防「几十个玩家同时挖矿」这类量大的正常情况。
	 */
	public int mobSpawnRateLimit = 40;

	/** 兜底限速的窗口长度（秒）。 */
	public int mobSpawnRateWindowSeconds = 60;

	/**
	 * 掉落物是「生物」的概率（0.0 - 1.0）。默认 0.08。
	 *
	 * <p>三大类的默认比例：<b>物品 67% / 生物 8% / 什么都不掉 25%</b>。
	 * 物品拿的是剩下的那部分（{@code 1 - mobChance - emptyChance}）。
	 *
	 * <p>早期版本这里是 0.15，实际玩下来「爆怪」太密了 —— 挖两下就窜出一只，
	 * 洞里根本清不过来，反而挤掉了「这一下会掉什么好东西」的期待感。
	 * 降到 8% 之后怪还是有，但更像「意外」而不是「常态」。
	 */
	public double mobChance = 0.08D;

	/**
	 * <b>什么都不掉</b>的概率（0.0 - 1.0）。默认 0.25。
	 *
	 * <p>它与 {@link #mobChance} 之和若超过 1，会按比例缩放并打日志提醒。
	 */
	public double emptyChance = 0.25D;

	/**
	 * <b>方块</b>掉落的保底：按物品分别计数，同一物品每 N 次掉落必定有 1 次掉「原物品」。
	 *
	 * <p>键是「这次原本会掉出来的物品」，所以中间穿插挖别的方块不会影响某个物品的进度 ——
	 * 连着挖 5 次泥土（中间夹着挖石头）时，第 5 次泥土必定掉泥土。0 表示关闭。
	 */
	public int pityThreshold = 5;

	/**
	 * <b>生物</b>掉落的保底：按生物类型分别计数，同一种生物每 N 次击杀必定有 1 次掉它的原版战利品。
	 *
	 * <p>例如连杀 3 只牛，第 3 只必定掉回牛肉和皮革；中间杀猪不影响牛的计数。0 表示关闭。
	 * 只有玩家造成的击杀才计数。
	 */
	public int mobPityThreshold = 3;

	/** 敌对生物权重（默认 50%）。 */
	public int hostileWeight = 50;

	/** 中立生物权重（默认 30%）。 */
	public int neutralWeight = 30;

	/** 友好（被动）生物权重（默认 20%）。 */
	public int passiveWeight = 20;

	/**
	 * <b>开局保护</b>窗口（分钟）。世界时间在这个窗口内时，改用下面那套更温和的参数。
	 *
	 * <p>一局制的容错率低：前 20 分钟要是因为随机掉不出木头、食物、床，这一局基本就废了。
	 * 因为玩法是「打到末影龙就结束」，**世界时间 ≈ 这一局的进度**，直接拿它当判据就行，
	 * 完全不需要持久化。0 = 关闭开局保护。
	 */
	public int earlyGameMinutes = 20;

	/** 开局窗口内「什么都不掉」的概率（默认 5%，平时 25%）—— 保证起步能拿到材料。 */
	public double earlyEmptyChance = 0.05D;

	/** 开局窗口内的敌对生物权重（默认 10，平时 50）—— 相对地更容易爆出牛羊这类食物来源。 */
	public int earlyHostileWeight = 10;

	/** 开局窗口内的方块保底次数（默认 3，平时 5）—— 更快拿到木头石头这类起步材料。 */
	public int earlyPityThreshold = 3;

	/**
	 * <b>按进度调概率</b>：开局保护结束后，越往这一局的后半段走，掉落越「刺激」。
	 *
	 * <p>一局制没有等级、没有装备分，唯一能反映进度的就是<b>世界时间</b>（约等于这一局打了多久）。
	 * 分三档：
	 * <ul>
	 *   <li>{@code EARLY}（0 ~ {@link #earlyGameMinutes}）：见上面的开局保护，温和；</li>
	 *   <li>{@code MID}（~ {@link #midGameMinutes}）：暴击概率 ×{@link #midJackpotMultiplier}；</li>
	 *   <li>{@code LATE}（之后）：暴击概率 ×{@link #lateJackpotMultiplier}。</li>
	 * </ul>
	 *
	 * <p>这样「刚开局」和「快通关」的手感明显不同，而后半段的暴击就是给长局的补偿。
	 */
	public boolean progressScaling = true;

	/** 中期的分界（分钟）。超过 {@link #earlyGameMinutes} 但没到这里的算中期。 */
	public int midGameMinutes = 60;

	/** 中期暴击概率倍率（默认 1.5）。 */
	public double midJackpotMultiplier = 1.5D;

	/** 后期暴击概率倍率（默认 3.0）—— 打到这个点，暴击该来得更频繁。 */
	public double lateJackpotMultiplier = 3.0D;

	/** 分档变化时在聊天栏提示一次（纯氛围，让玩家感知到「进入中/后期」）。 */
	public boolean announceProgressTier = true;

	/**
	 * <b>暴击大爆</b>开关。
	 *
	 * <p>现在全物品等概率，意味着抽到钻石和抽到泥土一样平淡 —— 暴击就是补上「哇」的那一刻：
	 * 小概率触发时必定从 {@link #jackpotItems} 宝藏池里出，还配音效、粒子和全服广播。
	 */
	public boolean enableJackpot = true;

	/** 每次掉落触发暴击的概率（默认 0.01 = 1%）。 */
	public double jackpotChance = 0.01D;

	/** 暴击时从这些物品里等概率抽一个（写成不存在的 id 会被忽略并记日志）。 */
	public List<String> jackpotItems = new ArrayList<>(List.of(
			"minecraft:diamond",
			"minecraft:diamond_block",
			"minecraft:netherite_scrap",
			"minecraft:netherite_ingot",
			"minecraft:ancient_debris",
			"minecraft:elytra",
			"minecraft:enchanted_golden_apple",
			"minecraft:totem_of_undying",
			"minecraft:shulker_shell",
			"minecraft:nether_star",
			"minecraft:trident",
			"minecraft:heart_of_the_sea",
			"minecraft:enchanted_book",
			"minecraft:dragon_breath",
			"minecraft:mace",
			"minecraft:trial_key",
			"minecraft:ominous_trial_key"));

	/** 暴击时放音效。 */
	public boolean jackpotSound = true;

	/** 暴击时撒粒子。 */
	public boolean jackpotParticles = true;

	/** 暴击时全服广播（一局之内最直接的社交爽点）。 */
	public boolean jackpotBroadcast = true;

	/** 保底触发时给可见反馈（粒子 + 音效 + 动作栏文字）。 */
	public boolean showPityFeedback = true;

	/**
	 * 稀有掉落下全服广播（不限 jackpot，只要是稀有物品就播报）。
	 *
	 * <p>"稀有" 定义：暴击池物品算稀有，额外增加几个「高价值/高稀有」物品作为补充。
	 */
	public boolean rareDropBroadcast = false;

	// ------------------------------------------------------------ 通关结算

	/**
	 * 末影龙被击杀时来一场「通关结算」：全服标题 + 本局统计播报 + 原地宝藏雨。
	 *
	 * <p>这局的玩法就是「打到末影龙就收工」，所以结算只给仪式感，<b>不给任何长期奖励</b>：
	 * 没有存档、没有排行榜、没有经济，也就不需要持久化。
	 */
	public boolean enableFinale = true;

	/**
	 * 通关宝藏雨撒几件（0 表示不撒）。
	 *
	 * <p>默认 24 —— 打得痛快，收尾也得有点排面，但别多到把末地地面铺满。
	 * 内容全部来自 {@link #jackpotItems}，所以改宝藏池就等于改结算奖励。
	 */
	public int finaleTreasureCount = 24;

	/**
	 * 宝藏雨的散落半径（格）。
	 *
	 * <p>默认 8：以末影龙倒下的位置为中心撒开，太密集会互相叠在一起。
	 */
	public int finaleTreasureSpread = 8;

	/** 通关时给全服弹大标题。 */
	public boolean finaleTitle = true;

	/** 通关时把本局统计广播到聊天栏。 */
	public boolean finaleBroadcast = true;

	/** 通关时的音效与粒子。 */
	public boolean finaleEffects = true;

	// ------------------------------------------------------------ 维度 / 群系

	/**
	 * <b>维度影响池子</b>：在下界、末地挖/杀时，掉落更偏向当地的高价值物品。
	 *
	 * <p>一局制里跑图是要花时间的 —— 既然玩家愿意跑去下界挖远古残骸、去末地打潜影贝，
	 * 掉落就该「认这个地方」。判定用 {@code level.dimension()}，零成本。
	 */
	public boolean dimensionAffectsPools = true;

	/** 下界的暴击概率倍率（默认 2.0）。 */
	public double netherJackpotMultiplier = 2.0D;

	/** 末地的暴击概率倍率（默认 3.0）—— 终点站，收益最高。 */
	public double endJackpotMultiplier = 3.0D;

	/** 在下界/末地时，改从专属池抽的概率（默认 0.25）。 */
	public double dimensionBonusChance = 0.25D;

	/**
	 * 下界专属掉落池：命中「维度加成」时从这里抽一件。
	 *
	 * <p>写不存在的 id 会被忽略并记日志；池子为空则该维度退回普通池。
	 */
	public List<String> netherBonusItems = new ArrayList<>(List.of(
			"minecraft:blaze_rod",
			"minecraft:blaze_powder",
			"minecraft:magma_cream",
			"minecraft:glowstone_dust",
			"minecraft:quartz",
			"minecraft:nether_wart",
			"minecraft:ancient_debris",
			"minecraft:netherite_scrap",
			"minecraft:ghast_tear",
			"minecraft:wither_skeleton_skull"));

	/** 末地专属掉落池。 */
	public List<String> endBonusItems = new ArrayList<>(List.of(
			"minecraft:ender_pearl",
			"minecraft:ender_eye",
			"minecraft:chorus_fruit",
			"minecraft:popped_chorus_fruit",
			"minecraft:shulker_shell",
			"minecraft:dragon_breath",
			"minecraft:elytra",
			"minecraft:end_crystal"));

	/**
	 * <b>群系影响池子</b>：在海洋、沙漠、丛林这类「有特色产出」的群系里，
	 * 小概率改从 {@link #biomeBonusItems} 抽。
	 *
	 * <p>判定方式是把群系 id 的路径做子串匹配（{@code ocean} / {@code desert} ...），
	 * 不依赖任何硬编码的群系注册表键，跨版本更稳。
	 */
	public boolean biomeAffectsPools = true;

	/** 命中特色群系时，改走群系池的概率（默认 0.15）。 */
	public double biomeBonusChance = 0.15D;

	/** 特色群系才会触发的池子（全世界通用一份，按 id 匹配决定是否生效）。 */
	public List<String> biomeBonusItems = new ArrayList<>(List.of(
			"minecraft:heart_of_the_sea",
			"minecraft:prismarine_shard",
			"minecraft:prismarine_crystals",
			"minecraft:nautilus_shell",
			"minecraft:turtle_egg",
			"minecraft:sponge",
			"minecraft:cactus",
			"minecraft:dead_bush",
			"minecraft:cocoa_beans",
			"minecraft:bamboo",
			"minecraft:melon_slice",
			"minecraft:sweet_berries"));

	/**
	 * 哪些群系算「特色群系」—— 群系 id 路径里含这些词之一就生效。
	 *
	 * <p>子串匹配是刻意的：{@code warm_ocean}、{@code deep_frozen_ocean} 都能被 {@code ocean} 命中。
	 */
	public List<String> biomeKeywords = new ArrayList<>(List.of(
			"ocean",
			"desert",
			"jungle",
			"swamp",
			"mushroom",
			"badlands",
			"beach"));

	// ------------------------------------------------------------ 精英怪

	/**
	 * <b>精英怪</b>：掉落爆出来的生物有几率变成「精英」，当场掉好东西。
	 *
	 * <p>刻意<b>不做长期奖励</b>：精英不掉专属货币、不涨等级、不进存档 ——
	 * 它就是一个「看到就该打」的即时爽点：发光、血更厚、打死爆宝藏池。
	 */
	public boolean enableEliteMobs = true;

	/** 爆出生物时，变成精英的概率（默认 0.08 = 8%）。 */
	public double eliteChance = 0.08D;

	/** 精英的生命上限倍率（默认 2.0）。 */
	public double eliteHealthMultiplier = 2.0D;

	/** 精英死亡时从宝藏池掉几件（默认 3）。0 = 只掉普通随机掉落。 */
	public int eliteTreasureCount = 3;

	/** 精英是否带发光效果（默认 true）—— 一眼就能认出来。 */
	public boolean eliteGlowing = true;

	/** 精英出生时是否全服播报（默认 true）。 */
	public boolean eliteBroadcast = true;

	/** 精英名字前缀，用于区分。 */
	public String eliteNamePrefix = "精英 ";

	// ------------------------------------------------------ 击杀生物 → 药水效果

	/**
	 * <b>击杀生物直接获得随机药水效果</b>（不产生药水物品，效果当场生效）。
	 *
	 * <p>刻意<b>只在击杀生物时给</b>：挖方块、爆炸、活塞都拿不到。
	 * 这样「想要 buff 就去打怪」成为一条独立的收益线，也让降低爆怪概率
	 * （{@link #mobChance} 0.15 → 0.08）不至于把打怪的动机一起削掉。
	 *
	 * <p><b>这是一个赌注</b>：触发之后先按 {@link #killEffectHarmfulChance} 掷一次，
	 * 有几率掉进 {@link #killEffectsHarmful} 负面池 —— 也就是说打怪拿到的可能是 debuff。
	 * 负面池的默认时长比有益池短一半（15 秒 vs 30 秒），所以长期仍是赚的，
	 * 但每一次都得掂量一下。
	 *
	 * <p>效果直接作用在<b>击杀者</b>身上；非玩家击杀（生物互杀、摔死、烧死）不触发。
	 */
	public boolean enableKillEffects = true;

	/** 每次由玩家击杀生物时，触发药水效果的概率（默认 0.15）。 */
	public double killEffectChance = 0.15D;

	/**
	 * <b>有益池</b>。每一项的格式二选一：
	 * <pre>
	 *   "minecraft:speed"           —— 用下面的全局时长 / 等级
	 *   "minecraft:speed;45;1"      —— 单独指定「秒数;等级」（等级 0 = I 级）
	 * </pre>
	 *
	 * <p>这里写了<b>有害</b>效果会被跳过并记日志（提示你挪到 {@link #killEffectsHarmful}）——
	 * 两个池子各自自洽，不会出现「有益池里混了个中毒」这种事。
	 */
	public List<String> killEffects = new ArrayList<>(List.of(
			"minecraft:speed",
			"minecraft:haste",
			"minecraft:strength",
			"minecraft:jump_boost",
			"minecraft:regeneration",
			"minecraft:resistance",
			"minecraft:fire_resistance",
			"minecraft:water_breathing",
			"minecraft:night_vision",
			"minecraft:health_boost",
			"minecraft:absorption",
			"minecraft:saturation",
			"minecraft:luck",
			"minecraft:slow_falling",
			"minecraft:conduit_power",
			"minecraft:dolphins_grace",
			"minecraft:hero_of_the_village",
			"minecraft:breath_of_the_nautilus"));

	/**
	 * <b>负面池</b>——赌输的那一半。
	 *
	 * <p>挑的都是「难受但不致命、而且会自己结束」的效果：缓慢、挖掘疲劳、虚弱、饥饿、
	 * 反胃、失明、霉运、黑暗。刻意<b>没放</b>中毒 / 凋零 / 飘浮：
	 * 那三个在残血或悬崖边是会直接要命的，而「击杀奖励」不该是个死亡陷阱。
	 * 真想要更刺激就自己往这个列表里加。
	 *
	 * <p>时长用 {@link #killEffectHarmfulDurationSeconds}（默认 15 秒，比有益池短一半），
	 * 所以整体期望仍然是赚的 —— 赌的是「这一把会不会难受一下」。
	 */
	public List<String> killEffectsHarmful = new ArrayList<>(List.of(
			"minecraft:slowness",
			"minecraft:mining_fatigue",
			"minecraft:weakness",
			"minecraft:hunger",
			"minecraft:nausea",
			"minecraft:blindness;8;0",
			"minecraft:unluck",
			"minecraft:darkness;8;0"));

	/** 触发时掉进负面池的概率（默认 0.25 = 四分之一）。设 0 即恢复成「纯奖励」。 */
	public double killEffectHarmfulChance = 0.25D;

	/** 全局效果时长（秒，默认 30）。单项写了 {@code id;秒数;等级} 时以单项为准。 */
	public int killEffectDurationSeconds = 30;

	/** 全局效果等级（0 = I 级，1 = II 级；默认 0）。 */
	public int killEffectAmplifier = 0;

	/** 负面效果的全局时长（秒，默认 15）—— 刻意比有益池短，让整体期望为正。 */
	public int killEffectHarmfulDurationSeconds = 15;

	/** 负面效果的全局等级（默认 0 = I 级）。 */
	public int killEffectHarmfulAmplifier = 0;

	/** 只在击杀<b>敌对</b>生物时给（默认 false）。分组沿用 {@code hostileWeight} 那套规则。 */
	public boolean killEffectHostileOnly = false;

	/** 触发时在动作栏提示拿到了什么效果。 */
	public boolean killEffectMessage = true;

	/** 药水效果最长多少秒（安全阀，防止配置写个离谱的数字把玩家永久 buff 住）。 */
	public int killEffectMaxSeconds = 600;

	// ---------------------------------------------------------------- 物品

	/**
	 * 掉出物品时的数量范围（含两端，各自会被限制在 1 - 64）。
	 *
	 * <p>默认 1~64 —— 也就是每次都在这之间随机；实际数量还会被物品自身的堆叠上限压住
	 * （比如剑只能 1 把、鸡蛋最多 16 个）。
	 */
	public int itemCountMin = 1;
	public int itemCountMax = 8;

	/**
	 * 生物死亡掉落要不要允许「什么都不掉」。
	 *
	 * <p>默认 false —— 生存前期杀牛却什么都没掉太劝退；关掉之后那部分概率并给物品。
	 */
	public boolean allowNothingOnMobDrop = false;

	/**
	 * 由掉落爆出来的生物，先「僵直」多少个游戏刻（20 刻 = 1 秒）。
	 *
	 * <p>默认 40（2 秒）：不然你正在洞里挖矿，突然贴脸爆出 3 只僵尸，跑都没地方跑。
	 * 僵直期间生物不动也不攻击，但可以被攻击。设 0 关闭。
	 */
	public int spawnedMobStunTicks = 40;

	/**
	 * 一次掉落最多爆出几只生物（含两端）。
	 *
	 * <p>默认 1~3 —— 也就是一次最多 3 只。想固定成 1 只就把两个都设成 1。
	 */
	public int mobCountMin = 1;
	public int mobCountMax = 3;

	/**
	 * 每游戏刻最多生成多少只生物（0 表示不限制）。
	 *
	 * <p>这是给服务器兜底用的：一次 TNT 连锁爆炸可能瞬间破坏几百个方块，
	 * 若每个方块都独立掷骰子，一 tick 内刷出几百只怪足以卡死服务器。
	 * 超出上限的那些方块会退化成掉落物品。
	 */
	public int maxMobsPerTick = 30;

	/**
	 * 永不进入随机物品池的物品。
	 *
	 * <p>三种写法都支持：{@code minecraft:dirt}（精确 id）、{@code dirt}（等价于 minecraft:dirt）、
	 * {@code worldedit:*}（整个命名空间，用来一次性排除某个模组的所有物品）。
	 *
	 * <p>默认把<b>管理员 / 创造模式专属</b>的东西排除了：命令方块系列、屏障、光源方块、
	 * 结构方块、结构空位、拼图方块、调试棒、基岩、刷怪笼、知识之书、末地传送门框架、
	 * 测试方块，以及试炼刷怪笼 / 宝库 / 紫水晶母岩 / 强化深板岩 / 石化橡木台阶这类
	 * 正常生存拿不到的技术性方块。不想要的话删掉对应条目即可。
	 */
	public List<String> itemBlacklist = new ArrayList<>(List.of(
			"minecraft:air",

			// —— 管理员 / 创造模式专属 ——
			"minecraft:command_block",
			"minecraft:chain_command_block",
			"minecraft:repeating_command_block",
			"minecraft:command_block_minecart",
			"minecraft:barrier",
			"minecraft:light",
			"minecraft:structure_block",
			"minecraft:structure_void",
			"minecraft:jigsaw",
			"minecraft:debug_stick",
			"minecraft:knowledge_book",
			"minecraft:test_block",
			"minecraft:test_instance_block",

			// —— 生存无法正常获得的技术性方块 ——
			"minecraft:bedrock",
			"minecraft:spawner",
			"minecraft:end_portal_frame",
			"minecraft:trial_spawner",
			"minecraft:vault",
			"minecraft:budding_amethyst",
			"minecraft:reinforced_deepslate",
			"minecraft:petrified_oak_slab"));

	// ---------------------------------------------------------------- 生物

	/**
	 * 被归为「中立」的生物 id。不在此列表中的生物按原版 {@code MobCategory} 分类：
	 * {@code MONSTER} 记为敌对，其余记为友好。
	 */
	public List<String> neutralMobs = new ArrayList<>(List.of(
			"minecraft:enderman", "minecraft:spider", "minecraft:cave_spider",
			"minecraft:wolf", "minecraft:iron_golem", "minecraft:snow_golem",
			"minecraft:bee", "minecraft:llama", "minecraft:trader_llama",
			"minecraft:panda", "minecraft:polar_bear", "minecraft:goat",
			"minecraft:dolphin", "minecraft:ocelot", "minecraft:cat",
			"minecraft:pufferfish", "minecraft:zombified_piglin", "minecraft:piglin",
			"minecraft:hoglin", "minecraft:zoglin"));

	/** 永不生成的生物 id（默认排除会摧毁世界的 Boss）；同样支持 {@code 模组名:*} 写法。 */
	public List<String> entityBlacklist = new ArrayList<>(List.of(
			"minecraft:ender_dragon", "minecraft:wither", "minecraft:giant",
			"minecraft:illusioner", "minecraft:player"));

	/**
	 * 额外并入「友好」池的生物 id。
	 *
	 * <p>原版把村民、流浪商人这类不会自然生成的生物标成 {@code MISC} 分类，默认会被过滤掉；
	 * 想把它们加回来就写在这里（铁傀儡、雪傀儡默认已在 {@link #neutralMobs} 里）。
	 */
	public List<String> extraPassiveMobs = new ArrayList<>(List.of(
			"minecraft:villager", "minecraft:wandering_trader"));

	/** 在日志里打印每次掉落替换的结果，排查问题用。 */
	public boolean debugLog = false;

	/**
	 * 启动自检的轮数，0 表示关闭（默认）。
	 *
	 * <p>大于 0 时，服务器启动 5 秒后会在世界出生点把「挖泥土」实际跑若干轮，
	 * 并在日志里打印物品 / 生物的实际分布，用来确认注入是否生效、权重是否符合预期。
	 */
	public int selfTestRolls = 0;

	// ================================================================ v1.11
	// 下面五组是 v1.11.0 的新功能：禁用刷怪蛋、TNT 引燃、掉落合并 + 定时播报、
	// 触发事件 + 断肢受伤、分层掉落 + 终极物资。

	// ---------------------------------------------- ① 刷怪蛋（Spawner Egg）

	/**
	 * <b>禁用刷怪蛋掉落</b>（默认 true）。
	 *
	 * <p>刷怪蛋掉出来会直接崩坏玩法：拿到一颗僵尸蛋就能无限刷怪刷物资，
	 * 「这一下会掉什么」的悬念全没了。所以默认把这一类<b>整个剔除</b>：
	 * 随机物品池构建时就跳过它们，抽中时的兜底重抽也会跳过，
	 * 万一还是漏出来（比如被写进了宝藏池）就直接丢弃并广播警告。
	 *
	 * <p>判定分三层，从宽到严：
	 * <ul>
	 *   <li>物品类是 {@code SpawnEggItem}（原版与绝大多数模组都继承它）；</li>
	 *   <li>物品 id 以 {@code _spawn_egg} 结尾（覆盖自定义实现的模组）；</li>
	 *   <li>写在 {@link #extraSpawnerEggItems} 里的 id / 命名空间。</li>
	 * </ul>
	 */
	public boolean blockSpawnerEggDrops = true;

	/** 万一还是掉出刷怪蛋，是否全服广播警告（默认 true）。 */
	public boolean spawnerEggBroadcast = true;

	/** 额外的刷怪蛋 id 名单（支持 {@code 模组名:*} 写法），给不走 SpawnEggItem 的模组兜底。 */
	public List<String> extraSpawnerEggItems = new ArrayList<>();

	// ------------------------------------------------------- ② TNT 引燃甩射

	/**
	 * <b>挖方块有几率引爆 TNT</b>（默认 true）。
	 *
	 * <p>2% 的「惊喜」：挖到符合条件的方块时，以挖掘点为中心朝四周各甩出一枚点燃的 TNT。
	 * 引信留了 4 秒（{@link #tntIgniteFuseTicks}），够跑开几步。
	 */
	public boolean enableTntIgnition = true;

	/** 触发概率（默认 0.02 = 2%）。 */
	public double tntIgniteChance = 0.02D;

	/**
	 * 同一玩家的冷却（秒，默认 300）。
	 *
	 * <p>挖方块太密集 —— 不设冷却的话 2% 会变成「每分钟都在炸」。
	 * 冷却只针对<b>真的引爆</b>的那一刻，没触发不计时。0 = 不冷却。
	 */
	public int tntIgniteCooldownSeconds = 300;

	/** 引信长度（游戏刻，默认 80 = 4 秒）。 */
	public int tntIgniteFuseTicks = 80;

	/** 甩出半径（格，默认 2.0）—— TNT 落点离挖掘点多远。 */
	public double tntIgniteSpreadBlocks = 2.0D;

	/**
	 * 甩几个方向（默认 4 = 东/南/西/北）。
	 *
	 * <p>8 = 再加四个斜角，威力翻倍，慎改。小于 1 视为 1。
	 */
	public int tntIgniteDirections = 4;

	/**
	 * 哪些方块能引燃，两种写法：
	 * <pre>
	 *   "#minecraft:logs"   —— 方块标签（# 开头）
	 *   "minecraft:cobweb"  —— 精确方块 id
	 * </pre>
	 */
	public List<String> tntIgniteBlocks = new ArrayList<>(List.of(
			"#minecraft:logs",
			"#minecraft:base_stone_overworld",
			"minecraft:cobweb",
			"minecraft:hay_block"));

	/** 引燃时是否在挖掘点撒一把碎块（默认 true）。 */
	public boolean tntIgniteDebris = true;

	/**
	 * 碎块清单，格式 {@code id;数量}，数量省略为 1。
	 *
	 * <p>默认黑石 2 / 沙砾 2 / 泥土 1 —— 爆炸总得留下点痕迹。
	 */
	public List<String> tntIgniteDebrisItems = new ArrayList<>(List.of(
			"minecraft:blackstone;2",
			"minecraft:gravel;2",
			"minecraft:dirt;1"));

	/** 引燃时全服广播（默认 true）—— 附近有人的时候这条消息很关键。 */
	public boolean tntIgniteBroadcast = true;

	// ------------------------------------------------- ③ 掉落合并 + 定时播报

	/**
	 * <b>掉落物自动合并堆叠</b>（默认 true）。
	 *
	 * <p>地上的同类物品在生成瞬间就并进附近已有的那一堆，直到堆满（64 或物品自身上限）。
	 * 一次 TNT 连锁炸掉几百个方块时，原本会掉出几百个掉落物实体，合并后只剩几个 ——
	 * 既少卡顿，捡起来也爽快。
	 *
	 * <p>只有真正被完全吸收的那一件才会取消生成，所以不存在「物品凭空消失」。
	 */
	public boolean dropMergeEnabled = true;

	/** 合并的搜索半径（格，默认 2.5）。0 表示只看脚下那一格。 */
	public double dropMergeRadius = 2.5D;

	/** 一次最多检查附近多少个掉落物（默认 24）—— 兜底防爆，别让大爆炸时扫全图。 */
	public int dropMergeMaxScan = 24;

	/**
	 * <b>定时播报本服掉落统计</b>（默认 true）。
	 *
	 * <p>每 {@link #dropSummaryIntervalSeconds} 秒广播一次「这段时间掉了些什么」，
	 * 让「别人挖到好东西」这件事被看见 —— 随机掉落的乐趣有一半是给别人看的。
	 */
	public boolean dropSummaryEnabled = true;

	/** 播报间隔（秒，默认 120 = 2 分钟），最小 10 秒。 */
	public int dropSummaryIntervalSeconds = 120;

	/** 每次播报最多列出几种物品（默认 5）。 */
	public int dropSummaryMaxLines = 5;

	// -------------------------------------------------- ④ 触发事件 + 断肢受伤

	/**
	 * <b>徒手挖木头事件</b>（默认 true）：连续不用斧头挖掉 {@link #bareHandLogThreshold} 个原木，
	 * 就地补一份木材。
	 *
	 * <p>原木徒手挖极慢，能连挖 50 个的人值得奖励；而一拿斧头就从头计数，
	 * 所以这条只在「还没工具」或「工具没了」的那段时间有意义。
	 */
	public boolean enableBareHandLogEvent = true;

	/** 连续挖多少个原木触发（默认 50）。 */
	public int bareHandLogThreshold = 50;

	/** 触发时补多少个原木（默认 8）。 */
	public int bareHandLogRewardCount = 8;

	/** 触发时全服广播（默认 true）。 */
	public boolean bareHandLogBroadcast = true;

	/**
	 * <b>高处跌落受伤</b>（默认 true）：跌落高度超过 {@link #fallInjuryHeight} 格且没有缓降时，
	 * 就地掉几个木块并摔断手脚。
	 *
	 * <p>50 格本来是必死的，能活下来（水、伤害吸收……）也该留下点代价。
	 * 断肢用属性修饰符实现：移速 / 挖掘速度按百分比扣，到期自动恢复。
	 */
	public boolean enableFallInjury = true;

	/** 触发断肢的跌落高度（格，默认 50）。 */
	public double fallInjuryHeight = 50.0D;

	/** 重伤的跌落高度（格，默认 75）—— 到这里就是双腿双手。 */
	public double fallInjurySevereHeight = 75.0D;

	/** 跌落时是否连手一起伤（默认 true）。false = 只伤腿。 */
	public boolean fallInjuryHurtsArms = true;

	/** 落地时掉落的物品，格式 {@code id;数量}（默认 4 个橡木原木）。 */
	public List<String> fallInjuryDropItems = new ArrayList<>(List.of("minecraft:oak_log;4"));

	/** 断一处（一条腿 / 一只手）的惩罚比例（默认 0.30 = -30%）。 */
	public double limbOnePenalty = 0.30D;

	/** 断两处（双腿 / 双手）的惩罚比例（默认 0.80 = -80%）。 */
	public double limbTwoPenalty = 0.80D;

	/** 轻度断肢持续多少秒（默认 60）。 */
	public int limbInjurySeconds = 60;

	/** 重度断肢持续多少秒（默认 120）。 */
	public int limbInjurySevereSeconds = 120;

	/** 断肢时全服广播（默认 true）。 */
	public boolean limbInjuryBroadcast = true;

	// ------------------------------------------------- ⑤ 分层掉落 + 终极物资

	/**
	 * <b>分层掉落</b>（默认 true）：某些物品只在对应维度产出。
	 *
	 * <p>和已有的「维度专属池」是两回事：专属池是「在下界更可能抽到烈焰棒」，
	 * 这里是「下界合金碎片在主世界<b>根本抽不到</b>」—— 想拿就得去下界。
	 */
	public boolean enableTieredDrops = true;

	/** 只在下界产出的物品（写在这里的物品会从主世界 / 末地的随机结果里被剔除）。 */
	public List<String> netherExclusiveItems = new ArrayList<>(List.of(
			"minecraft:ancient_debris",
			"minecraft:netherite_scrap"));

	/** 只在末地产出的物品。 */
	public List<String> endExclusiveItems = new ArrayList<>(List.of(
			"minecraft:end_crystal",
			"minecraft:dragon_breath"));

	/**
	 * 抽到「别的维度专属」的物品时，最多重抽几次（默认 8）。
	 *
	 * <p>专属物品在物品池里占比极小，几乎一次就中；只有极端配置（整个池子都是专属物品）
	 * 才会重抽到底 —— 那时这次什么都不掉，也不会把专属物品漏到别的维度。
	 */
	public int exclusiveRerollAttempts = 8;

	/**
	 * <b>终极物资</b>（默认 true）：在指定维度有 {@link #ultimateChance} 的概率直接产出
	 * 末地传送门框架 / 末影之眼。
	 *
	 * <p>这两样在正常生存里几乎拿不到（传送门框架默认就在物品黑名单里），
	 * 所以它是这一局真正的「终点奖励」：凑齐 12 个框架就能开一个传送门。
	 */
	public boolean enableUltimateDrops = true;

	/** 触发概率（默认 0.01 = 1%）。 */
	public double ultimateChance = 0.01D;

	/**
	 * 在哪个维度触发：{@code the_end} / {@code the_nether} / {@code overworld} / {@code any}。
	 *
	 * <p>默认 {@code the_end} —— 终点站才配发终点奖励。
	 */
	public String ultimateDimension = "the_end";

	/** 命中时给多少个末地传送门框架（默认 12，正好够开一个门）。 */
	public int ultimatePortalFrameCount = 12;

	/** 命中时给多少个末影之眼（默认 12）。 */
	public int ultimateEnderEyeCount = 12;

	/** 终极物资全服广播（默认 true）。 */
	public boolean ultimateBroadcast = true;

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

	// --------------------------------------------- 事件系统（总开关）

	/** <b>全局事件系统总开关</b>（默认 true）。 */
	public boolean enableEvents = true;

	/** 每隔多少分钟掷一次骰子决定是否触发事件（默认 10）。 */
	public int eventIntervalMinutes = 10;

	/**
	 * 这次掷骰子<b>触发事件</b>的概率（默认 0.6）。
	 *
	 * <p>剩下的 40% 就是「什么都没发生」——保留空白，让玩家有「虚惊一场」的喘息。
	 */
	public double eventChance = 0.6D;

	/** 触发后<b>暴击（双事件）</b>的概率（默认 0.15）。 */
	public double eventCritChance = 0.15D;

	/** 青蛙雨持续多少秒（默认 60）。 */
	public int frogRainDurationSeconds = 60;

	/** 天降陨石持续多少秒（默认 60）。 */
	public int meteorDurationSeconds = 60;

	/** 青蛙雨：每个在线玩家上方，每隔多少刻掉一批青蛙（默认 40 = 2 秒）。 */
	public int frogRainIntervalTicks = 40;

	/** 青蛙雨：每个玩家每次掉几只青蛙（默认 2）。 */
	public int frogRainPerPlayer = 2;

	/** 青蛙雨：以玩家为中心的水平半径（格，默认 8）。 */
	public double frogRainRadius = 8.0D;

	/** 天降陨石：每隔多少刻在随机玩家附近砸一个陨石（默认 30 = 1.5 秒）。 */
	public int meteorIntervalTicks = 50;

	/** 天降陨石：以玩家为中心的水平半径（格，默认 12）。 */
	public double meteorRadius = 8.0D;

	/** 天降陨石：陨石爆炸半径（格，默认 3）。 */
	public float meteorExplosionRadius = 2.0F;

	/** 天降陨石是否在落点点燃火焰（默认 true）。 */
	public boolean meteorFire = true;

	/** 是否启用「青蛙雨」事件（默认 true）。 */
	public boolean enableFrogRain = true;

	/** 是否启用「天降陨石」事件（默认 true）。 */
	public boolean enableMeteorShower = true;

	/** 持久 HUD 面板（Boss 血条样式的世界状态条）是否启用（默认 true）。 */
	public boolean eventHudEnabled = true;

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

	/** 每次玩家击杀生物触发升级掷骰的概率（默认 0.05 = 5%）。 */
	public double killEnchantLevelUpChance = 0.05D;

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

	// --------------------------------------------- v1.14 事件体验与悬赏

	/**
	 * <b>无掉落方块黑名单</b>：这些方块被移除时<b>不</b>触发随机掉落。
	 *
	 * <p>修「灭火也掉随机物品」bug：火被水扑灭走的也是「无破坏者移除」路径，
	 * 但火原版就没有任何掉落 —— 凭空掉随机物品是 bug 不是惊喜。
	 */
	public List<String> noDropBlocks = List.of("minecraft:fire", "minecraft:soul_fire");

	/** 天降陨石：陨石从玩家头顶多高处开始下坠（格，默认 28）。 */
	public double meteorSpawnHeight = 28.0D;

	/** 天降陨石：下坠加速度（越大砸得越快，默认 0.55 —— 给 2~3 秒的预警时间）。 */
	public double meteorFallSpeed = 0.55D;

	/** 天降陨石：爆炸后掉落矿物簇的概率与规模 —— 爆炸点残留 2~4 块矿物（默认 true）。 */
	public boolean meteorOreResidue = true;

	// ---------- v1.14 新事件（雷池 / 血月 / 福到） ----------

	/**
	 * <b>雷池</b>（v1.14 新事件）：局部雷暴 —— 随机暴露玩家头顶周围持续落雷。
	 * 躲进屋里 / 山洞（头顶不直通天空）就完全安全。
	 */
	public boolean enableThunderPool = true;

	/** 雷池持续秒数（默认 60）。 */
	public int thunderPoolDurationSeconds = 60;

	/** 雷池落雷间隔（刻，默认 40 = 2 秒一波；每波每玩家 1~2 道雷）。 */
	public int thunderPoolIntervalTicks = 40;

	/** 雷池散布半径（格，默认 10）。 */
	public double thunderPoolRadius = 10.0D;

	/**
	 * <b>血月</b>（v1.14 新事件）：持续在暴露玩家附近涌出敌对生物 —— 危险与掉落并存。
	 */
	public boolean enableBloodMoon = true;

	/** 血月持续秒数（默认 120）。 */
	public int bloodMoonDurationSeconds = 120;

	/** 血月刷怪间隔（刻，默认 300 = 15 秒一波；每波每玩家 2 只）。 */
	public int bloodMoonIntervalTicks = 300;

	/**
	 * <b>福到</b>（v1.14 新事件）：纯福利 —— 天上掉随机物品（礼盒式的掉落雨）。
	 */
	public boolean enableFortuneRain = true;

	/** 福到持续秒数（默认 60）。 */
	public int fortuneRainDurationSeconds = 60;

	/** 福到掉落间隔（刻，默认 100 = 5 秒一波）。 */
	public int fortuneRainIntervalTicks = 100;

	// ---------- 猎杀悬赏（支线任务） ----------

	/**
	 * <b>猎杀悬赏</b>（v1.14）：定期全服发布「猎杀 N 只某类生物」的支线任务，
	 * 玩家击杀计入进度，达成时击杀者获得宝藏奖励。用第二条 Boss 条 HUD 显示进度。
	 */
	public boolean enableBounties = true;

	/** 悬赏目标数量下限（默认 5）。 */
	public int bountyMinKills = 5;

	/** 悬赏目标数量上限（默认 12）。 */
	public int bountyMaxKills = 12;

	/** 悬赏达成时奖励的宝藏件数（默认 3）。 */
	public int bountyRewardCount = 3;

	/** 悬赏达成后的冷却下限（分钟，默认 5）。 */
	public int bountyCooldownMinMinutes = 5;

	/** 悬赏达成后的冷却上限（分钟，默认 12）。 */
	public int bountyCooldownMaxMinutes = 12;

	/** 悬赏 HUD 开关（与事件 HUD 相互独立，可同屏堆叠）。 */
	public boolean bountyHudEnabled = true;

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

	// -------------------------------------------------- 运行时派生（不写进 json）

	private transient Set<Identifier> neutralIds = Set.of();
	private transient Set<Identifier> extraPassiveIds = Set.of();
	private transient IdFilter entityBlacklistFilter = IdFilter.EMPTY;
	private transient IdFilter itemBlacklistFilter = IdFilter.EMPTY;
	private transient IdFilter spawnerEggFilter = IdFilter.EMPTY;
	private transient Set<Identifier> netherExclusiveIds = Set.of();
	private transient Set<Identifier> endExclusiveIds = Set.of();

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile RandomDropsConfig instance;

	public RandomDropsConfig() {
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static RandomDropsConfig get() {
		RandomDropsConfig local = instance;
		if (local == null) {
			synchronized (RandomDropsConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized RandomDropsConfig load() {
		Path path = configPath();
		RandomDropsConfig loaded = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, RandomDropsConfig.class);
			} catch (IOException | JsonParseException e) {
				RandomDrops.LOGGER.warn("[random-drops] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new RandomDropsConfig();
		}

		loaded.validate();
		instance = loaded;
		loaded.save();
		return loaded;
	}

	public static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	/** 把当前配置写回磁盘。 */
	public synchronized void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			RandomDrops.LOGGER.error("[random-drops] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析各个 id 列表。 */
	private void validate() {
		if (itemBlacklist == null) itemBlacklist = new ArrayList<>();
		if (neutralMobs == null) neutralMobs = new ArrayList<>();
		if (extraPassiveMobs == null) extraPassiveMobs = new ArrayList<>();
		if (entityBlacklist == null) entityBlacklist = new ArrayList<>();

		// 用 !(x >= 0) 的写法顺便挡掉 NaN
		if (!(mobChance >= 0.0D)) mobChance = 0.0D;
		if (mobChance > 1.0D) mobChance = 1.0D;
		if (!(emptyChance >= 0.0D)) emptyChance = 0.0D;
		if (emptyChance > 1.0D) emptyChance = 1.0D;

		double chances = mobChance + emptyChance;
		if (chances > 1.0D) {
			RandomDrops.LOGGER.warn("[random-drops] mobChance({}) + emptyChance({}) 超过 1，按比例缩放到合计 1",
					mobChance, emptyChance);
			mobChance /= chances;
			emptyChance /= chances;
		}

		hostileWeight = Math.max(0, hostileWeight);
		neutralWeight = Math.max(0, neutralWeight);
		passiveWeight = Math.max(0, passiveWeight);
		if ((long) hostileWeight + neutralWeight + passiveWeight <= 0L) {
			hostileWeight = 50;
			neutralWeight = 30;
			passiveWeight = 20;
		}

		itemCountMin = Math.min(64, Math.max(1, itemCountMin));		itemCountMax = Math.min(64, Math.max(1, itemCountMax));
		if (itemCountMin > itemCountMax) {
			int swap = itemCountMin;
			itemCountMin = itemCountMax;
			itemCountMax = swap;
		}

		mobSpawnRateLimit = Math.min(100_000, Math.max(0, mobSpawnRateLimit));
		mobSpawnRateWindowSeconds = Math.min(3600, Math.max(1, mobSpawnRateWindowSeconds));
		spawnedMobStunTicks = Math.min(20 * 60, Math.max(0, spawnedMobStunTicks));

		earlyGameMinutes = Math.min(24 * 60, Math.max(0, earlyGameMinutes));
		if (!(earlyEmptyChance >= 0.0D)) earlyEmptyChance = 0.0D;
		if (earlyEmptyChance > 1.0D) earlyEmptyChance = 1.0D;
		earlyHostileWeight = Math.max(0, earlyHostileWeight);
		earlyPityThreshold = Math.min(1000, Math.max(0, earlyPityThreshold));

		if (jackpotItems == null) jackpotItems = new ArrayList<>();
		if (!(jackpotChance >= 0.0D)) jackpotChance = 0.0D;
		if (jackpotChance > 1.0D) jackpotChance = 1.0D;

		finaleTreasureCount = Math.min(512, Math.max(0, finaleTreasureCount));
		finaleTreasureSpread = Math.min(64, Math.max(0, finaleTreasureSpread));

		mobCountMin = Math.min(64, Math.max(1, mobCountMin));
		mobCountMax = Math.min(64, Math.max(1, mobCountMax));
		if (mobCountMin > mobCountMax) {
			int swap = mobCountMin;
			mobCountMin = mobCountMax;
			mobCountMax = swap;
		}
		maxMobsPerTick = Math.min(100_000, Math.max(0, maxMobsPerTick));
		pityThreshold = Math.min(1000, Math.max(0, pityThreshold));
		mobPityThreshold = Math.min(1000, Math.max(0, mobPityThreshold));

		// 击杀奖励（药水效果）
		if (killEffects == null) killEffects = new ArrayList<>();
		if (killEffectsHarmful == null) killEffectsHarmful = new ArrayList<>();
		if (!(killEffectChance >= 0.0D)) killEffectChance = 0.0D;
		if (killEffectChance > 1.0D) killEffectChance = 1.0D;
		if (!(killEffectHarmfulChance >= 0.0D)) killEffectHarmfulChance = 0.0D;
		if (killEffectHarmfulChance > 1.0D) killEffectHarmfulChance = 1.0D;
		killEffectMaxSeconds = Math.min(24 * 60 * 60, Math.max(1, killEffectMaxSeconds));
		killEffectDurationSeconds = Math.min(killEffectMaxSeconds, Math.max(1, killEffectDurationSeconds));
		killEffectHarmfulDurationSeconds = Math.min(killEffectMaxSeconds, Math.max(1, killEffectHarmfulDurationSeconds));
		killEffectAmplifier = Math.min(255, Math.max(0, killEffectAmplifier));
		killEffectHarmfulAmplifier = Math.min(255, Math.max(0, killEffectHarmfulAmplifier));
		if (eliteNamePrefix == null) eliteNamePrefix = "精英 ";

		neutralIds = parseIds(neutralMobs, "neutralMobs");
		extraPassiveIds = parseIds(extraPassiveMobs, "extraPassiveMobs");
		entityBlacklistFilter = parseFilter(entityBlacklist, "entityBlacklist");
		itemBlacklistFilter = parseFilter(itemBlacklist, "itemBlacklist");

		// ---- v1.11 ----
		spawnerEggFilter = parseFilter(extraSpawnerEggItems, "extraSpawnerEggItems");
		netherExclusiveIds = parseIds(netherExclusiveItems, "netherExclusiveItems");
		endExclusiveIds = parseIds(endExclusiveItems, "endExclusiveItems");

		if (tntIgniteBlocks == null) tntIgniteBlocks = new ArrayList<>();
		if (tntIgniteDebrisItems == null) tntIgniteDebrisItems = new ArrayList<>();
		if (fallInjuryDropItems == null) fallInjuryDropItems = new ArrayList<>();

		if (!(tntIgniteChance >= 0.0D)) tntIgniteChance = 0.0D;
		if (tntIgniteChance > 1.0D) tntIgniteChance = 1.0D;
		tntIgniteCooldownSeconds = Math.min(24 * 3600, Math.max(0, tntIgniteCooldownSeconds));
		tntIgniteFuseTicks = Math.min(20 * 600, Math.max(1, tntIgniteFuseTicks));
		tntIgniteSpreadBlocks = Math.min(16.0D, Math.max(0.0D, tntIgniteSpreadBlocks));
		tntIgniteDirections = Math.min(8, Math.max(1, tntIgniteDirections));

		dropMergeRadius = Math.min(8.0D, Math.max(0.0D, dropMergeRadius));
		dropMergeMaxScan = Math.min(256, Math.max(1, dropMergeMaxScan));
		dropSummaryIntervalSeconds = Math.min(24 * 3600, Math.max(10, dropSummaryIntervalSeconds));
		dropSummaryMaxLines = Math.min(20, Math.max(1, dropSummaryMaxLines));

		bareHandLogThreshold = Math.min(100_000, Math.max(1, bareHandLogThreshold));
		bareHandLogRewardCount = Math.min(64, Math.max(1, bareHandLogRewardCount));

		fallInjuryHeight = Math.min(1000.0D, Math.max(1.0D, fallInjuryHeight));
		fallInjurySevereHeight = Math.min(2000.0D, Math.max(fallInjuryHeight, fallInjurySevereHeight));
		limbOnePenalty = Math.min(0.95D, Math.max(0.0D, limbOnePenalty));
		limbTwoPenalty = Math.min(0.95D, Math.max(0.0D, limbTwoPenalty));
		limbInjurySeconds = Math.min(24 * 3600, Math.max(1, limbInjurySeconds));
		limbInjurySevereSeconds = Math.min(24 * 3600, Math.max(1, limbInjurySevereSeconds));

		exclusiveRerollAttempts = Math.min(64, Math.max(1, exclusiveRerollAttempts));
		if (!(ultimateChance >= 0.0D)) ultimateChance = 0.0D;
		if (ultimateChance > 1.0D) ultimateChance = 1.0D;
		if (ultimateDimension == null || ultimateDimension.isBlank()) ultimateDimension = "the_end";
		ultimatePortalFrameCount = Math.min(64, Math.max(0, ultimatePortalFrameCount));
		ultimateEnderEyeCount = Math.min(64, Math.max(0, ultimateEnderEyeCount));

		// ---- v1.12.0 ----
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

		if (eventIntervalMinutes < 1) eventIntervalMinutes = 10;
		if (!(eventChance >= 0.0D)) eventChance = 0.0D;
		if (eventChance > 1.0D) eventChance = 1.0D;
		if (!(eventCritChance >= 0.0D)) eventCritChance = 0.0D;
		if (eventCritChance > 1.0D) eventCritChance = 1.0D;
		frogRainDurationSeconds = Math.min(60 * 60, Math.max(1, frogRainDurationSeconds));
		meteorDurationSeconds = Math.min(60 * 60, Math.max(1, meteorDurationSeconds));
		frogRainIntervalTicks = Math.min(20 * 600, Math.max(1, frogRainIntervalTicks));
		frogRainPerPlayer = Math.min(16, Math.max(0, frogRainPerPlayer));
		frogRainRadius = Math.min(32.0D, Math.max(0.0D, frogRainRadius));
		meteorIntervalTicks = Math.min(20 * 600, Math.max(1, meteorIntervalTicks));
		meteorRadius = Math.min(64.0D, Math.max(0.0D, meteorRadius));
		meteorExplosionRadius = (float) Math.min(16.0D, Math.max(0.0D, meteorExplosionRadius));

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

		// 体验收敛（v1.14 反馈：范围/伤害太大，体验太差）：只压新装机的默认值，
		// 已有配置文件里写死的值保持不动（磁盘值优先），README 有迁移说明。
		meteorRadius = Math.min(64.0D, Math.max(0.0D, meteorRadius));
		meteorExplosionRadius = (float) Math.min(16.0D, Math.max(0.0D, meteorExplosionRadius));
		if (!(meteorSpawnHeight >= 8.0D)) meteorSpawnHeight = 28.0D;
		meteorSpawnHeight = Math.min(120.0D, meteorSpawnHeight);
		if (!(meteorFallSpeed >= 0.1D)) meteorFallSpeed = 0.55D;
		meteorFallSpeed = Math.min(3.0D, meteorFallSpeed);
		if (bountyMinKills < 1) bountyMinKills = 5;
		bountyMinKills = Math.min(100, bountyMinKills);
		if (bountyMaxKills < bountyMinKills) bountyMaxKills = Math.max(bountyMinKills, 12);
		bountyMaxKills = Math.min(200, bountyMaxKills);
		if (bountyRewardCount < 0) bountyRewardCount = 3;
		bountyRewardCount = Math.min(24, bountyRewardCount);
		if (bountyCooldownMinMinutes < 0) bountyCooldownMinMinutes = 5;
		if (bountyCooldownMaxMinutes < bountyCooldownMinMinutes) {
			bountyCooldownMaxMinutes = bountyCooldownMinMinutes;
		}

		// Bingo 奖励规模
		if (bingoLineRewardCount < 0) bingoLineRewardCount = 2;
		bingoLineRewardCount = Math.min(16, bingoLineRewardCount);
		if (bingoClearRewardCount < 0) bingoClearRewardCount = 8;
		bingoClearRewardCount = Math.min(48, bingoClearRewardCount);
	}

	private static Set<Identifier> parseIds(List<String> raw, String field) {
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
				RandomDrops.LOGGER.warn("[random-drops] {} 里的 \"{}\" 不是合法 id，已忽略", field, entry);
			} else {
				parsed.add(id);
			}
		}

		return Set.copyOf(parsed);
	}

	public boolean isNeutral(Identifier id) {
		return neutralIds.contains(id);
	}

	/** 物品分支实际占的比例 —— 扣掉生物与「什么都不掉」之后剩下的都给它。 */
	public double itemChance() {
		return Math.max(0.0D, 1.0D - mobChance - emptyChance);
	}

	// ------------------------------------------------ 开局保护的「生效值」

	/** 当前该用哪个「什么都不掉」概率。 */
	public double effectiveEmptyChance(boolean early) {
		return early ? earlyEmptyChance : emptyChance;
	}

	/** 当前该用哪个敌对权重。 */
	public int effectiveHostileWeight(boolean early) {
		return early ? earlyHostileWeight : hostileWeight;
	}

	/** 当前该用哪个方块保底次数。 */
	public int effectivePityThreshold(boolean early) {
		return early ? earlyPityThreshold : pityThreshold;
	}

	public boolean isExtraPassive(Identifier id) {
		return extraPassiveIds.contains(id);
	}

	public boolean isEntityBlacklisted(Identifier id) {
		return entityBlacklistFilter.matches(id);
	}

	public boolean isItemBlacklisted(Identifier id) {
		return itemBlacklistFilter.matches(id);
	}

	// ------------------------------------------------------- v1.11 的判定

	/**
	 * 这个物品 id 算不算刷怪蛋（按 id 判定的那两层）。
	 *
	 * <p>「类是不是 SpawnEggItem」由 {@code SpawnerEggGuard} 那边判，因为要拿物品实例。
	 */
	public boolean isSpawnerEggId(Identifier id) {
		if (!blockSpawnerEggDrops || id == null) {
			return false;
		}

		return id.getPath().endsWith("_spawn_egg") || spawnerEggFilter.matches(id);
	}

	/** 是否「只在下界产出」的物品。 */
	public boolean isNetherExclusive(Identifier id) {
		return enableTieredDrops && id != null && netherExclusiveIds.contains(id);
	}

	/** 是否「只在末地产出」的物品。 */
	public boolean isEndExclusive(Identifier id) {
		return enableTieredDrops && id != null && endExclusiveIds.contains(id);
	}

	/** 一份 id 过滤器：支持精确 id，以及整个命名空间（{@code 模组名:*}）。 */
	private static final class IdFilter {
		private static final IdFilter EMPTY = new IdFilter(Set.of(), Set.of());

		private final Set<Identifier> exact;
		private final Set<String> namespaces;

		private IdFilter(Set<Identifier> exact, Set<String> namespaces) {
			this.exact = exact;
			this.namespaces = namespaces;
		}

		private boolean matches(Identifier id) {
			return id != null && (this.exact.contains(id) || this.namespaces.contains(id.getNamespace()));
		}
	}

	private static IdFilter parseFilter(List<String> raw, String field) {
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
				RandomDrops.LOGGER.warn("[random-drops] {} 里的 \"{}\" 不是合法 id，已忽略", field, entry);
			} else {
				exact.add(id);
			}
		}

		return new IdFilter(Set.copyOf(exact), Set.copyOf(namespaces));
	}
}
