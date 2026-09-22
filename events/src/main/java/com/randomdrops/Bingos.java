package com.randomdrops;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bingo 板（物品 / 击杀）：全服共同进度的 5×5 集卡玩法。
 *
 * <ul>
 *   <li><b>物品板</b>：随机 25 件主池物品，玩家把任意一件捡进背包即盖章；</li>
 *   <li><b>击杀板</b>：随机 25 种生物，任意玩家击杀即盖章；</li>
 *   <li><b>连线</b>：横 / 竖 / 斜共 12 条线，任意一条盖满即发奖（全服播报 +
 *       触发者脚下宝藏）；同一局内每条线只奖一次；25 格全清 = 大奖 + 换新一局；</li>
 *   <li><b>地图</b>：每块板一张<b>锁定的手绘地图</b> —— 5×5 色块，盖章时对应格变绿，
 *       玩家把它拿在手上就能实时看到进度（vanilla 地图同步机制）。</li>
 * </ul>
 *
 * <p>两张板相互独立（可单独开关）；玩家上线自动补发地图。
 */
public final class Bingos {
	private Bingos() {
	}

	private static final RandomSource RANDOM = RandomSource.create();
	private static final int BOARD = 5;

	/** 12 条线：5 行 + 5 列 + 2 条对角（每条 5 个格子下标）。 */
	private static final int[][] LINES = buildLines();

	private static int[][] buildLines() {
		int[][] lines = new int[12][BOARD];
		for (int i = 0; i < BOARD; i++) {
			for (int j = 0; j < BOARD; j++) {
				lines[i][j] = i * BOARD + j;                    // 行
				lines[BOARD + i][j] = j * BOARD + i;            // 列
			}
		}
		for (int j = 0; j < BOARD; j++) {
			lines[2 * BOARD][j] = j * BOARD + j;                // 主对角
			lines[2 * BOARD + 1][j] = j * BOARD + (BOARD - 1 - j); // 副对角
		}
		return lines;
	}

	// ---- 物品板 ----
	private static boolean itemBoardActive;
	private static String[] itemTargets = new String[BOARD * BOARD];
	private static boolean[] itemDone = new boolean[BOARD * BOARD];
	private static final Set<Integer> itemRewardedLines = new HashSet<>();
	private static ItemStack itemMapStack;

	// ---- 击杀板 ----
	private static boolean killBoardActive;
	private static EntityType<?>[] killTargets = new EntityType[BOARD * BOARD];
	private static boolean[] killDone = new boolean[BOARD * BOARD];
	private static final Set<Integer> killRewardedLines = new HashSet<>();
	private static ItemStack killMapStack;

	/** 已发过地图的玩家（上线补发用）。 */
	private static final Set<UUID> handedOut = new HashSet<>();

	/** 注册 tick 与击杀钩子。 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Bingos::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Bingos::onDeath);
	}

	/** 关服清理。 */
	public static void reset() {
		itemBoardActive = false;
		killBoardActive = false;
		itemRewardedLines.clear();
		killRewardedLines.clear();
		handedOut.clear();
		itemMapStack = null;
		killMapStack = null;
	}

	private static void tick(MinecraftServer server) {
		RandomDropsConfig config = RandomDropsConfig.get();
		if (!config.enableItemBingo && !config.enableKillBingo) {
			return;
		}
		if (server.getPlayerList().getPlayerCount() == 0) {
			return; // 没人时不动（不发板不发图）
		}

		if (config.enableItemBingo && !itemBoardActive) {
			startItemBoard(server, config);
		}
		if (config.enableKillBingo && !killBoardActive) {
			startKillBoard(server, config);
		}

		// 物品盖章：每秒扫一次在线玩家背包
		if (itemBoardActive && server.getTickCount() % 20 == 0) {
			scanInventories(server, config);
		}

		handMapsToPlayers(server, config);
	}

	// ------------------------------------------------------------ 开板

	private static void startItemBoard(MinecraftServer server, RandomDropsConfig config) {
		ServerLevel level = server.overworld();
		List<String> pool = DropRandomizer.samplePoolForBingo(level, BOARD * BOARD, RANDOM);
		if (pool.size() < BOARD * BOARD) {
			return; // 池子异常（极端黑名单），下个 tick 再试
		}

		itemTargets = pool.toArray(new String[0]);
		itemDone = new boolean[BOARD * BOARD];
		itemRewardedLines.clear();
		itemBoardActive = true;
		handedOut.clear();
		itemMapStack = paintMap(level, itemDone, "物品 Bingo");

		server.getPlayerList().broadcastSystemMessage(Component.literal(
				"§b[Bingo] §f物品 Bingo §b开板！随机 25 件物品已经上路 —— 挖一挖、捡一捡，"
						+ "手持 §fBingo 地图§b 实时查看进度，连线有奖！"), false);
	}

	private static void startKillBoard(MinecraftServer server, RandomDropsConfig config) {
		ServerLevel level = server.overworld();
		List<EntityType<?>> candidates = new ArrayList<>();
		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (type.getCategory() == MobCategory.MISC) {
				continue;
			}
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (id != null && config.entityBlacklist.stream().noneMatch(b ->
					b.endsWith("*") ? id.toString().startsWith(b.substring(0, b.length() - 1))
							: b.equals(id.toString()))) {
				candidates.add(type);
			}
		}
		if (candidates.size() < BOARD * BOARD) {
			return;
		}

		for (int i = killTargets.length - 1; i > 0; i--) {
			int j = RANDOM.nextInt(i + 1);
			EntityType<?> tmp = candidates.get(i);
			candidates.set(i, candidates.get(j));
			candidates.set(j, tmp);
		}
		killTargets = candidates.subList(0, BOARD * BOARD).toArray(new EntityType[0]);
		killDone = new boolean[BOARD * BOARD];
		killRewardedLines.clear();
		killBoardActive = true;
		handedOut.clear();
		killMapStack = paintMap(level, killDone, "击杀 Bingo");

		server.getPlayerList().broadcastSystemMessage(Component.literal(
				"§d[Bingo] §f击杀 Bingo §d开板！随机 25 种生物等着被清 —— 手持 §fBingo 地图§d 查看进度，"
						+ "连线有奖！"), false);
	}

	// ------------------------------------------------------------ 盖章与连线

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!killBoardActive || entity instanceof Player
				|| !(source.getEntity() instanceof ServerPlayer killer)) {
			return;
		}

		for (int i = 0; i < killTargets.length; i++) {
			if (!killDone[i] && killTargets[i] == entity.getType()) {
				killDone[i] = true;
				repaintKillMap(killer.level());
				checkLines(true, killer, killer.level().getServer());
				return;
			}
		}
	}

	private static void scanInventories(MinecraftServer server, RandomDropsConfig config) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			var inv = p.getInventory();
			for (int slot = 0; slot < inv.getContainerSize(); slot++) {
				ItemStack stack = inv.getItem(slot);
				if (stack.isEmpty()) {
					continue;
				}
				Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (id == null) {
					continue;
				}
				String itemId = id.toString();

				for (int i = 0; i < itemTargets.length; i++) {
					if (!itemDone[i] && itemId.equals(itemTargets[i])) {
						itemDone[i] = true;
						repaintItemMap(p.level());
						checkLines(false, p, server);
					}
				}
			}
		}
	}

	/** 新盖章后检查包含该格的线（简化：全表检查，奖励过的线跳过）。 */
	private static void checkLines(boolean killBoard, ServerPlayer trigger, MinecraftServer server) {
		RandomDropsConfig config = RandomDropsConfig.get();
		boolean[] done = killBoard ? killDone : itemDone;
		Set<Integer> rewarded = killBoard ? killRewardedLines : itemRewardedLines;
		int doneCount = 0;
		for (boolean b : done) {
			if (b) {
				doneCount++;
			}
		}

		for (int lineIdx = 0; lineIdx < LINES.length; lineIdx++) {
			if (rewarded.contains(lineIdx)) {
				continue;
			}
			boolean full = true;
			for (int cell : LINES[lineIdx]) {
				if (!done[cell]) {
					full = false;
					break;
				}
			}
			if (!full) {
				continue;
			}

			rewarded.add(lineIdx);
			int rewardCount = config.bingoLineRewardCount;
			dropRewards(trigger, rewardCount);
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"§a[Bingo] §f" + trigger.getName().getString() + "§a 点亮了一条 Bingo 线！"
							+ "全员获得 §e" + rewardCount + " 件宝藏§a（掉在触发者脚下）"), false);
		}

		// 全清大奖 + 换新一局
		if (doneCount == BOARD * BOARD) {
			int grand = config.bingoClearRewardCount;
			dropRewards(trigger, grand);
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"§6[Bingo·全清] §f" + trigger.getName().getString() + "§6 集齐了整张 "
							+ (killBoard ? "击杀" : "物品") + " 板！大奖 §e" + grand
							+ " 件宝藏§6！新一局即将开始…"), false);

			if (killBoard) {
				killBoardActive = false;
				killMapStack = null;
			} else {
				itemBoardActive = false;
				itemMapStack = null;
			}
			handedOut.clear(); // 新一局给所有人发新地图
		}
	}

	private static void dropRewards(ServerPlayer trigger, int count) {
		ServerLevel level = trigger.level();
		for (int i = 0; i < count; i++) {
			ItemStack reward = DropRandomizer.randomTreasure(level, level.getRandom());
			if (!reward.isEmpty()) {
				Block.popResource(level, trigger.blockPosition(), reward);
			}
		}
	}

	// ------------------------------------------------------------ 地图绘制

	/**
	 * 画一张 5×5 的 Bingo 板地图：128×128 像素上 25 个色块（每格约 25×25），
	 * 未达成 = 金属灰，达成 = 草地绿；外圈一圈白色边框。
	 *
	 * <p>修「板子空白」bug：不能用 {@code MAP_POST_PROCESSING=LOCK} —— vanilla 对
	 * 锁定地图完全停止同步（MapItem.update 直接返回，HoldingPlayer 建立不起来），
	 * 玩家一张数据都收不到。改为<b>全图涂满</b>（无 0 像素，地形更新无处可写），
	 * 不锁定，玩家手持时由 vanilla 的携带同步机制正常推送颜色。
	 */
	private static ItemStack paintMap(ServerLevel level, boolean[] done, String title) {
		ItemStack map = net.minecraft.world.item.MapItem.create(level, 0, 0, (byte) 2, false, false);
		if (map.isEmpty()) {
			return map;
		}
		MapItemSavedData data = net.minecraft.world.item.MapItem.getSavedData(map, level);
		if (data == null) {
			return map;
		}

		paintCells(data, done);

		// 地图名（铁砧/物品名展示用）
		map.set(DataComponents.CUSTOM_NAME, Component.literal("§b" + title));
		return map;
	}

	private static void paintCells(MapItemSavedData data, boolean[] done) {
		byte frame = MapColor.WOOL.getPackedId(MapColor.Brightness.NORMAL);
		byte idle = MapColor.METAL.getPackedId(MapColor.Brightness.LOW);
		byte hit = MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL);

		// 先整体铺白框
		for (int x = 0; x < 128; x++) {
			for (int z = 0; z < 128; z++) {
				data.setColor(x, z, frame);
			}
		}

		for (int i = 0; i < BOARD * BOARD; i++) {
			int row = i / BOARD;
			int col = i % BOARD;
			int x0 = col * 25 + 1;
			int z0 = row * 25 + 1;
			byte color = done[i] ? hit : idle;
			for (int dx = 0; dx < 23; dx++) {
				for (int dz = 0; dz < 23; dz++) {
					data.setColor(x0 + dx, z0 + dz, color);
				}
			}
		}
	}

	private static void repaintItemMap(ServerLevel level) {
		if (itemMapStack == null) {
			return;
		}
		MapItemSavedData data = net.minecraft.world.item.MapItem.getSavedData(itemMapStack, level);
		if (data != null) {
			paintCells(data, itemDone);
		}
	}

	private static void repaintKillMap(ServerLevel level) {
		if (killMapStack == null) {
			return;
		}
		MapItemSavedData data = net.minecraft.world.item.MapItem.getSavedData(killMapStack, level);
		if (data != null) {
			paintCells(data, killDone);
		}
	}

	/** 给在线玩家补发地图（每人每局一张物品图 + 一张击杀图）。 */
	private static void handMapsToPlayers(MinecraftServer server, RandomDropsConfig config) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (handedOut.contains(p.getUUID())) {
				continue;
			}
			handedOut.add(p.getUUID());

			if (config.enableItemBingo && itemBoardActive && itemMapStack != null) {
				ItemStack copy = itemMapStack.copy();
				copy.setCount(1);
				if (!p.getInventory().add(copy)) {
					Block.popResource(p.level(), p.blockPosition(), copy);
				}
			}
			if (config.enableKillBingo && killBoardActive && killMapStack != null) {
				ItemStack copy = killMapStack.copy();
				copy.setCount(1);
				if (!p.getInventory().add(copy)) {
					Block.popResource(p.level(), p.blockPosition(), copy);
				}
			}
			p.sendSystemMessage(Component.literal("§b[Bingo] 已把 Bingo 地图放进你的背包 —— 手持即可查看进度"));
		}
	}

	// ------------------------------------------------------------ 自检辅助

	/** 自检用：读取板子地图上某像素的颜色 id（0 = 未涂色；-1 = 地图数据缺失）。 */
	static int mapPixelForTest(ServerLevel level, boolean killBoard, int x, int z) {
		ItemStack stack = killBoard ? killMapStack : itemMapStack;
		if (stack == null) {
			return -1;
		}
		MapItemSavedData data = net.minecraft.world.item.MapItem.getSavedData(stack, level);
		return data == null ? -1 : (data.colors[z * 128 + x] & 255);
	}

	/** 自检用：强制开两块板（绕过在线玩家检查；空服 broadcast 无害）。 */
	static void forceStartBoardsForTest(MinecraftServer server, RandomDropsConfig config) {
		if (config.enableItemBingo && !itemBoardActive) {
			startItemBoard(server, config);
		}
		if (config.enableKillBingo && !killBoardActive) {
			startKillBoard(server, config);
		}
	}

	static boolean itemBoardActiveForTest() {
		return itemBoardActive;
	}

	static boolean killBoardActiveForTest() {
		return killBoardActive;
	}

	static int itemDoneCountForTest() {
		int n = 0;
		for (boolean b : itemDone) {
			if (b) {
				n++;
			}
		}
		return n;
	}

	static boolean[] itemDoneForTest() {
		return itemDone;
	}

	static int[][] linesForTest() {
		return LINES;
	}

	/** 自检用：强制重开物品板（清发图记录，模拟全清后的新一局）。 */
	static void restartItemBoardForTest() {
		itemBoardActive = false;
		itemMapStack = null;
		handedOut.clear();
	}
}
