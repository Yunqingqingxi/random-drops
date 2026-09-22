package com.yunxigames;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;

/**
 * 生物改变模块的开服自检：把这一批功能跑一遍，结论写进日志。
 *
 * <p>为什么在<b>真服务器</b>上跑而不是写单元测试：要摸到 {@code ServerLevel} / 实体生成 / 声音注册表，
 * 纯 mock 测不出「真的能用」。自检挂在 {@code SERVER_STARTED} 上，拿真实的 {@code overworld} 当实验场。
 *
 * <p>触发方式：配置里把 {@code selfTestRolls} 设成大于 0（比如 200），开服时即跑；跑完改回 0 关闭。
 */
public final class MobSelfTest {

	private MobSelfTest() {
	}

	/** ㊲ 幻翼 × 苦力怕混合：配置默认开启 + 幻翼类型存在 + 能生成且存活。 */
	static void checkPhantomCreeper(MinecraftServer server, ServerLevel level, MobsConfig config) {
		boolean cfgOk = config.phantomCreeperEnabled
				&& config.phantomCreeperExplosionPower > 0.0F
				&& config.phantomSoundBz;

		boolean typeOk = false;
		boolean spawnOk = false;
		boolean aliveOk = false;

		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:phantom"));
		if (type != null) {
			typeOk = true;
			Entity e = type.create(level, EntitySpawnReason.EVENT);
			if (e instanceof Phantom phantom) {
				try {
					BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(0, 64, 0));
					phantom.setPos(at.getX() + 0.5, at.getY() + 3.0, at.getZ() + 0.5);
					spawnOk = level.addFreshEntity(phantom);
					aliveOk = phantom.isAlive();
				} finally {
					phantom.discard();
				}
			}
		}

		check("㊲ 生物改动·幻翼×苦力怕混合",
				cfgOk && typeOk && spawnOk && aliveOk,
				"配置启用=" + cfgOk + " 幻翼类型存在=" + typeOk + " 生成=" + spawnOk + " 存活=" + aliveOk);
	}

	/** ㊳ 自定义音效：yg_mobs:phantom_creeper 必须已在声音注册表里（由 YunxiGamesMobs 注册）。 */
	static void checkSoundRegistered(MinecraftServer server, ServerLevel level, MobsConfig config) {
		boolean registered = BuiltInRegistries.SOUND_EVENT.get(YunxiGamesMobs.PHANTOM_CREEPER_SOUND).isPresent();
		check("㊳ 生物改动·自定义音效注册",
				registered,
				"yg_mobs:phantom_creeper 已注册=" + registered);
	}

	// 复用 core 的统一自检记录器（通过静态导入）
	static void check(String name, boolean ok, String detail) {
		SelfTest.check(name, ok, detail);
	}
}
