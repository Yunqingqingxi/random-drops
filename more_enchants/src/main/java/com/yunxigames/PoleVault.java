package com.yunxigames;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「蓝银撑杆跳」玩法：木棍附魔后右键，把杆立在地上、人撑起来向前飞出去。
 *
 * <h2>动作分解（三步，每一步都能单独解释）</h2>
 * <ol>
 *   <li><b>立杆</b>：校验「踩在地上 / 有助跑速度 / 头顶有净空」→ 在起跳点前方 {@value #PLANT_AHEAD}
 *       格立起一根杆，高度 = {@code min(配置杆长, 头顶净空)} —— 杆穿不过石头，洞穴里就只能立矮杆。</li>
 *   <li><b>起跳</b>：给玩家一个冲量，之后完全交给原版抛物线（客户端自己积分，服务端只发速度，
 *       与三叉戟激流、风弹同一条通道）。竖直初速由 {@link PoleVaultPhysics#solveLaunch} 反解，
 *       水平速度照搬助跑 —— <b>所以跑得越快飞得越远，站着不动根本撑不起来</b>。</li>
 *   <li><b>倒杆</b>：杆绕底端做匀质细杆的刚体倒伏（α = 3g/2L·sinθ）。倒向与起跳方向<b>相反</b>：
 *       人向前上方翻过杆顶，杆受到的反向角冲量把它推回助跑方向 —— 角动量守恒，现实里的撑杆跳
 *       也是人过杆、杆往跑道那边倒。</li>
 * </ol>
 *
 * <h2>为什么杆是粒子而不是实体</h2>
 * <p>26.2 里 {@code Display.BlockDisplay#setBlockState}、{@code Display#setTransformation} 全是
 * private，想做「实体杆」得再加一个 {@code @Invoker} mixin，而且实体是<b>会进存档</b>的：
 * 服务端异常退出就会在世界里留下残骸。粒子杆零状态、不需要客户端 mod、进程没了也不会留东西，
 * 而且倒伏时粒子跟着杆转，观感反而更「蓝银草」。代价是杆不挡路 —— 这是刻意的取舍。
 *
 * <h2>零持久化</h2>
 * <p>杆与落地缓冲都只活在内存里（{@link #POLES} / {@link #CUSHION}），关服即清；
 * 冷却走原版物品冷却，不写任何 NBT。
 */
public final class PoleVault {
	private PoleVault() {
	}

	/** 立杆点在起跳点前方多少格：踩在脚尖前，人升起来后杆就落到身下、身后。 */
	static final double PLANT_AHEAD = 0.7D;

	/** 杆倒平之后的淡出时长（刻）。 */
	static final int FADE_TICKS = 15;

	/** 撒粒子的节奏：每几刻沿杆撒一趟（每刻都撒会刷屏，也费带宽）。 */
	static final int PARTICLE_INTERVAL = 2;

	/** 落地缓冲窗口（刻，3 秒）：够覆盖整段腾空，且不会变成长期免摔落。 */
	static final int CUSHION_TICKS = 60;

	/** 起跳失败后的短冷却（刻）：只为防刷屏，不占用完整冷却。 */
	static final int FAIL_COOLDOWN_TICKS = 10;

	/** 杆倒平的角度（弧度）。 */
	private static final double MAX_TILT = Math.PI / 2.0D;

	/**
	 * 一根立在地上的杆。纯内存态。
	 *
	 * <p>杆的姿态只有一个自由度：与竖直方向的夹角 {@link #tilt}；倒伏方向在立杆时就定死。
	 */
	static final class Pole {
		final ServerLevel level;
		/** 杆底（起跳点前方的地面）。 */
		final Vec3 base;
		/** 杆长（格）。 */
		final double length;
		/** 倒伏方向（水平单位向量，与起跳方向相反）。 */
		final Vec3 fallDir;
		/** 与竖直方向的夹角（弧度，0 = 立正）。 */
		double tilt;
		/** 角速度（弧度/刻）。 */
		double omega;
		/** 已存在的刻数（只用来控制粒子节奏）。 */
		int age;
		/** &gt;0 表示已倒平/撞墙，正在淡出。 */
		int fade;

		Pole(ServerLevel level, Vec3 base, double length, Vec3 fallDir, double omega) {
			this.level = level;
			this.base = base;
			this.length = length;
			this.fallDir = fallDir;
			this.omega = omega;
		}

		/** 杆上参数位置（0 = 杆底，1 = 杆顶）的世界坐标。 */
		Vec3 pointAt(double param) {
			double d = length * param;
			double s = Math.sin(tilt) * d;
			return new Vec3(
					base.x + fallDir.x * s,
					base.y + Math.cos(tilt) * d,
					base.z + fallDir.z * s);
		}
	}

	/** 世界里立着的杆（内存态，关服清空）。 */
	private static final List<Pole> POLES = new ArrayList<>();

	/** 正在享受落地缓冲的玩家：UUID → 剩余刻数。 */
	private static final Map<UUID, Integer> CUSHION = new HashMap<>();

	/** 挂上右键钩子。 */
	public static void register() {
		UseItemCallback.EVENT.register(PoleVault::onUseItem);
	}

	/** 关服清理：杆是粒子、缓冲是内存计数，丢掉即可。 */
	public static void reset() {
		POLES.clear();
		CUSHION.clear();
	}

	// ------------------------------------------------------------ 右键

	private static InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
		// 客户端侧的伪造调用不处理（本系列所有判定都只在服务端）
		if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		EnchantsConfig config = EnchantsConfig.get();
		if (!config.enableEnchantmentBreakthrough || !config.enablePoleVault) {
			return InteractionResult.PASS;
		}

		ItemStack stack = sp.getMainHandItem();
		// supported_items 已经把附魔限死在木棍上，这里再判一次物品是「铁砧把书拍在别的物品上」的兜底
		if (!stack.is(Items.STICK)) {
			return InteractionResult.PASS;
		}

		Holder<Enchantment> holder = ModEnchantments.poleVault(level);
		int enchLevel = ModEnchantments.getLevel(stack, holder);
		if (enchLevel <= 0) {
			return InteractionResult.PASS;
		}

		if (sp.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS; // 冷却中：静默，别刷屏
		}

		// 骑乘 / 滑翔 / 游泳时没有「地面」可以借力，直接不响应
		if (sp.isPassenger() || sp.isFallFlying() || sp.isInWater()) {
			return InteractionResult.PASS;
		}

		if (!sp.onGround()) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 得先踩在地上才立得住杆");
		}

		// 助跑：方向取「助跑方向」而不是视线方向 —— 撑杆跳是沿动量方向飞出去的
		Vec3 runUp = sp.getKnownMovement();
		double speed = Math.sqrt(runUp.x * runUp.x + runUp.z * runUp.z);
		if (speed < config.poleVaultMinRunUp || speed < 1.0E-4D) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 助跑不足 —— 撑杆跳靠的是跑起来的动量，站着撑不动");
		}
		Vec3 dir = new Vec3(runUp.x, 0.0D, runUp.z).normalize();

		// 立杆点：脚尖前方；杆穿不过石头，净空多少就立多高
		BlockPos column = BlockPos.containing(
				sp.getX() + dir.x * PLANT_AHEAD, sp.getY(), sp.getZ() + dir.z * PLANT_AHEAD);
		int clearance = measureClearance(level, column, 8);
		if (clearance < 2) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 头顶只剩 " + clearance + " 格，杆立不起来");
		}
		double poleLength = Math.min(Math.max(2.0D, config.poleVaultLength), clearance);

		PoleVaultPhysics.Launch launch = PoleVaultPhysics.solveLaunch(
				speed, enchLevel, poleLength,
				config.poleVaultApexPerLevel, config.poleVaultRunUpEfficiency,
				config.poleVaultMinRunUp, config.poleVaultForwardRetain);
		if (launch.refused()) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 这一跳没撑起来");
		}

		Vec3 base = new Vec3(column.getX() + 0.5D, sp.getY(), column.getZ() + 0.5D);

		// 1) 立杆：倒向与起跳方向相反（角动量守恒）
		plant(level, base, poleLength, dir.scale(-1.0D), config.poleVaultToppleNudge);

		// 2) 起跳：竖直初速来自「杆的弹性能 + 助跑动能」预算，水平速度照搬助跑动量
		sp.setDeltaMovement(
				dir.x * launch.horizontalSpeed(), launch.vy(), dir.z * launch.horizontalSpeed());
		sp.hurtMarked = true;   // 与击退同一条通道：让客户端把速度换成这一份（玩家速度是客户端权威）
		sp.resetFallDistance();

		// 3) 演出与冷却
		level.playSound(null, base.x, base.y, base.z,
				SoundEvents.BAMBOO_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
				SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.PLAYERS, 1.0F, 1.0F);
		sp.getCooldowns().addCooldown(stack, Math.max(1, config.poleVaultCooldownTicks));
		if (config.poleVaultCushionedLanding) {
			CUSHION.put(sp.getUUID(), CUSHION_TICKS);
		}

		sp.sendSystemMessage(Component.literal("§b[蓝银撑杆跳] §7杆立起 " + SelfTest.trim(poleLength)
				+ " 格，腾空 " + SelfTest.trim(launch.apex()) + " 格！"), true);

		return InteractionResult.SUCCESS;
	}

	/** 起跳失败：给一句人话 + 一个短冷却（不占用完整冷却，方便立刻重试）。 */
	private static InteractionResult fail(ServerPlayer player, ItemStack stack, String message) {
		player.sendSystemMessage(Component.literal(message), true);
		player.getCooldowns().addCooldown(stack, FAIL_COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------ 每刻

	/** 每刻推进：倒杆积分 + 落地缓冲。由入口挂在 END_SERVER_TICK 上。 */
	static void tick(MinecraftServer server) {
		EnchantsConfig config = EnchantsConfig.get();
		if (!config.enableEnchantmentBreakthrough || !config.enablePoleVault) {
			// 玩法被关掉：内存里的杆直接丢掉（它们本来就只是粒子，不留世界状态）
			POLES.clear();
			CUSHION.clear();
			return;
		}

		tickPoles(config);
		tickCushion(server, config);
	}

	/** 倒杆：刚体倒伏 + 撞墙停住 + 倒平后淡出。 */
	static void tickPoles(EnchantsConfig config) {
		if (POLES.isEmpty()) {
			return;
		}

		Iterator<Pole> it = POLES.iterator();
		while (it.hasNext()) {
			Pole pole = it.next();
			pole.age++;

			if (pole.fade > 0) {
				pole.fade--;
				// 淡出时从杆顶往下收：像草叶散掉，而不是整体变淡
				emitParticles(pole, config, (double) pole.fade / FADE_TICKS);
				if (pole.fade <= 0) {
					it.remove();
				}
				continue;
			}

			double[] next = PoleVaultPhysics.stepTopple(pole.tilt, pole.omega, pole.length, MAX_TILT);
			pole.tilt = next[0];
			pole.omega = next[1];

			boolean flat = pole.tilt >= MAX_TILT - 1.0E-9D;
			// 杆头扎进实心方块当撞墙处理：停住姿态然后淡出（粒子不挡路，但倒伏姿态要老实）
			boolean blocked = !flat && tipBlocked(pole);
			if (flat || blocked) {
				pole.fade = FADE_TICKS;
				Vec3 tip = pole.pointAt(0.9D);
				pole.level.playSound(null, tip.x, tip.y, tip.z,
						SoundEvents.BAMBOO_BREAK, SoundSource.PLAYERS, 0.8F, flat ? 0.7F : 1.2F);
			}

			emitParticles(pole, config, 1.0D);
		}
	}

	/** 落地缓冲：腾空期间把摔落距离按住 —— fallDistance 是摔伤的唯一输入。 */
	private static void tickCushion(MinecraftServer server, EnchantsConfig config) {
		if (CUSHION.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Integer>> it = CUSHION.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int left = entry.getValue() - 1;
			if (player == null || left <= 0) {
				it.remove();
				continue;
			}
			entry.setValue(left);

			// 起跳那一两刻服务端还以为人站在地上，这里只对「真的在空中」的刻做卸力
			if (config.poleVaultCushionedLanding && !player.onGround()) {
				player.resetFallDistance();
			}
		}
	}

	// ------------------------------------------------------------ 杆的工具

	/** 在世界里立一根杆（右键路径与自检共用）。 */
	static Pole plant(ServerLevel level, Vec3 base, double length, Vec3 fallDir, double nudge) {
		Pole pole = new Pole(level, base, length, fallDir, nudge);
		POLES.add(pole);
		return pole;
	}

	/** 当前立着的杆数量（自检用）。 */
	static int poleCount() {
		return POLES.size();
	}

	/**
	 * 从 {@code base} 往上数「能立几格杆」：碰到有碰撞体积的方块就封顶。
	 *
	 * <p>用碰撞体积而不是 {@code isSolid()} —— 高草、火把这类「不是实心但不该穿」的东西
	 * 也该挡住杆。
	 */
	static int measureClearance(ServerLevel level, BlockPos base, int max) {
		int free = 0;
		for (int i = 0; i < max; i++) {
			BlockPos pos = base.above(i);
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty()) {
				break;
			}
			free++;
		}
		return free;
	}

	/** 杆头（离杆顶留一点余量，免得贴着天花板的杆一开始就判定撞墙）是否扎进了方块。 */
	private static boolean tipBlocked(Pole pole) {
		Vec3 tip = pole.pointAt(0.97D);
		BlockPos pos = BlockPos.containing(tip.x, tip.y, tip.z);
		BlockState state = pole.level.getBlockState(pos);
		return !state.getCollisionShape(pole.level, pos).isEmpty();
	}

	/** 沿杆撒「蓝银草」粒子：蓝（灵魂火）+ 银（末地烛），杆顶偶尔来一星电火花。 */
	private static void emitParticles(Pole pole, EnchantsConfig config, double coverage) {
		if (!config.enablePoleVaultParticles || pole.age % PARTICLE_INTERVAL != 0) {
			return;
		}

		ServerLevel world = pole.level;
		int samples = Math.max(1, (int) Math.round(pole.length * 2.0D * Math.max(0.0D, coverage)));
		for (int i = 0; i <= samples; i++) {
			Vec3 p = pole.pointAt((double) i / samples);
			world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			if (i % 2 == 0) {
				world.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			}
		}

		if (pole.age % (PARTICLE_INTERVAL * 4) == 0) {
			Vec3 top = pole.pointAt(1.0D);
			world.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 3,
					0.12D, 0.12D, 0.12D, 0.01D);
		}
	}
}
