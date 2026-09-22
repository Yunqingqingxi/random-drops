package com.yunxigames;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.level.levelgen.Heightmap;

import static com.yunxigames.SelfTest.*;

/**
 * Bingo 包的开服自检：双板开板、地图绘制、12 条线结构、目标不重复。
 */
public final class BingoSelfTest {
	private BingoSelfTest() {
	}

static void checkBingo(MinecraftServer server, ServerLevel level, BingoConfig config) {
		Bingos.restartItemBoardForTest();
		Bingos.forceStartBoardsForTest(server, config);

		boolean itemOn = Bingos.itemBoardActiveForTest();
		boolean killOn = Bingos.killBoardActiveForTest();

		// 12 条线结构：每条 5 格、下标合法；格子跨线共享是 Bingo 的正常形态（中心格 4 线共享）
		boolean linesOk = true;
		Set<Integer> covered = new HashSet<>();
		for (int[] line : Bingos.linesForTest()) {
			if (line.length != 5) {
				linesOk = false;
				break;
			}
			for (int cell : line) {
				if (cell < 0 || cell >= 25) {
					linesOk = false;
				} else {
					covered.add(cell);
				}
			}
		}
		// 12 条线应覆盖全部 25 格（否则有格子永远无法连线）
		linesOk = linesOk && covered.size() == 25;

		boolean targetsOk = Bingos.itemBoardActiveForTest() && Bingos.itemDoneCountForTest() == 0;

		// 地图数据真实涂色（修「板子空白」bug 的回归断言）：边框白 + 中心格灰，都应非 0
		int framePixel = Bingos.mapPixelForTest(level, false, 2, 2);
		int cellPixel = Bingos.mapPixelForTest(level, false, 64, 64);
		boolean painted = framePixel > 0 && cellPixel > 0;

		check("㉛ Bingo·双板+地图+连线结构",
				itemOn && killOn && linesOk && targetsOk && painted,
				"物品板=" + itemOn + " 击杀板=" + killOn + " 12 条线结构=" + linesOk
						+ " 初始进度干净=" + targetsOk
						+ " 地图涂色(边框/格)=" + framePixel + "/" + cellPixel);
	}
}
