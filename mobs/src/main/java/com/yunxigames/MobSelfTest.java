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

import com.yunxigames.mobs.PhantomSound;

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

	/**
	 * ㊴ 俯冲音效注入：确认 mixin 真的织进了原版「俯冲执行者」内部类，且音效可解析。
	 *
	 * <p>为什么用反射查方法名：{@code Phantom$PhantomSweepAttackGoal} 是包级私有的内部类，
	 * 外部源码引用不到；而 Mixin 织入的处理方法会以 {@code handler$...$yg$playSoundOnDiveStart}
	 * 的形式出现在目标类的<b>声明方法</b>里 —— 只要它在那儿，就证明「俯冲开始播音效」这条链路
	 * 在运行时确实挂上了（而不是编译通过、运行静默失效）。
	 */
	static void checkSwoopSoundMixin(MinecraftServer server, ServerLevel level, MobsConfig config) {
		boolean injected = false;
		try {
			Class<?> goal = Class.forName("net.minecraft.world.entity.monster.Phantom$PhantomSweepAttackGoal");
			for (java.lang.reflect.Method method : goal.getDeclaredMethods()) {
				if (method.getName().contains("yg$playSoundOnDiveStart")) {
					injected = true;
					break;
				}
			}
		} catch (Throwable ignored) {
			// 类没加载 / 名字对不上都算没织上
		}

		boolean soundOk = PhantomSound.resolvable();
		check("㊴ 俯冲音效·注入生效 + 音效可解析",
				injected && soundOk,
				"mixin 已织入俯冲目标类=" + injected + " 音效可解析=" + soundOk);
	}

	// 复用 core 的统一自检记录器（通过静态导入）
	static void check(String name, boolean ok, String detail) {
		SelfTest.check(name, ok, detail);
	}
}
