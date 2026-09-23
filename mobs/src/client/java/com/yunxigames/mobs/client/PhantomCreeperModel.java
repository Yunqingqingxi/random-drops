package com.yunxigames.mobs.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.phantom.PhantomModel;
import net.minecraft.client.renderer.entity.state.PhantomRenderState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「苦力怕幻翼」的混合模型（yg-mobs 客户端）：<b>保留幻翼的翅膀与尾巴，头和身体换成苦力怕</b>。
 *
 * <p>做法不是重新画模型，而是把两个官方模型装到一棵新树上：
 * <ul>
 *   <li>新树的根下只放两样东西：幻翼的 {@code body} 与苦力怕的整块（键名 {@code yg_creeper}）；</li>
 *   <li>幻翼的翅膀与尾巴本来就是 {@code body} 的子部件，跟着一起进来 —— 于是拍打、摆动动画
 *       原封不动（{@link PhantomModel#setupAnim} 操作的还是同一批 ModelPart 实例）；</li>
 *   <li>幻翼自己的躯干与头要藏起来。躯干用 {@code skipDraw}（<b>只跳过自身方块，子部件照常渲染</b>），
 *       所以翅膀不会被误伤；头是 {@code body} 的子部件，直接 {@code visible = false}；</li>
 *   <li>苦力怕只要头 + 身体，四条腿藏掉。</li>
 * </ul>
 *
 * <p><b>为什么根必须留着幻翼的 {@code body}</b>：父类 {@link PhantomModel} 的构造是
 * {@code root.getChild("body")} 再 {@code body.getChild("tail_base" / "left_wing_base" / "right_wing_base")}，
 * 自己另建一棵不含 {@code body} 的树会直接抛「找不到部件」——渲染器构造失败就是黑屏。
 *
 * <p>两个模型的坐标原点不同，所以每帧把苦力怕那块对齐到幻翼躯干的位置。
 * 观感不合适只需改 {@link #BODY_Y_OFFSET}（上下）与 {@link #BODY_SCALE}（大小）这两个常量。
 */
public class PhantomCreeperModel extends PhantomModel {

	/** 苦力怕的四条腿：只要头和身体，腿不要。 */
	private static final String[] CREEPER_LEGS = {
			"right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg",
	};

	/** 新树里挂苦力怕那一块的键名。 */
	private static final String CREEPER_KEY = "yg_creeper";

	/**
	 * 苦力怕块相对幻翼躯干原点的垂直偏移（模型单位，16 单位 = 1 格）。
	 *
	 * <p>苦力怕模型以脚底为原点（身体在其上方），幻翼躯干原点在身体中心，
	 * 所以要往下压一点才对得上。负数 = 往下移。
	 */
	private static final float BODY_Y_OFFSET = -6.0F;

	/** 苦力怕块的缩放（1.0 = 原尺寸）。幻翼躯干比苦力怕小，需要缩小时改这里。 */
	private static final float BODY_SCALE = 1.0F;

	/** 幻翼的躯干部件（本体不渲染，只用来取坐标）。 */
	private final ModelPart phantomBody;

	/** 苦力怕的整块（它本身就是身体，{@code head} 挂在其下 —— 苦力怕模型没有单独的 body 部件）。 */
	private final ModelPart creeper;

	public PhantomCreeperModel(ModelPart phantomRoot, ModelPart creeperRoot) {
		super(buildRoot(phantomRoot, creeperRoot));

		this.phantomBody = phantomRoot.getChild("body");
		this.creeper = creeperRoot;

		// 藏掉幻翼躯干的方块。用 skipDraw 而不是 visible=false：
		// visible=false 会连子部件（翅膀 / 尾巴）一起不渲染，翅膀就没了；
		// skipDraw 只跳过它自己的方块，子部件照常渲染 —— 这就是"翅膀保留、躯干消失"的关键。
		this.phantomBody.skipDraw = true;

		// 幻翼的头是 body 的子部件（见 PhantomModel#createBodyLayer），本体没有子部件，直接不可见。
		if (this.phantomBody.hasChild("head")) {
			this.phantomBody.getChild("head").visible = false;
		}

		// 苦力怕只要头 + 身体：四条腿藏掉。
		for (String leg : CREEPER_LEGS) {
			if (creeperRoot.hasChild(leg)) {
				creeperRoot.getChild(leg).visible = false;
			}
		}

		this.creeper.xScale = BODY_SCALE;
		this.creeper.yScale = BODY_SCALE;
		this.creeper.zScale = BODY_SCALE;
	}

	/**
	 * 拼出新模型树：幻翼的 {@code body}（翅膀 / 尾巴都在它下面）+ 苦力怕整块。
	 *
	 * <p>幻翼的 {@code body} 必须原样放进来，否则父类构造取部件时找不到 → 抛异常。
	 */
	private static ModelPart buildRoot(ModelPart phantomRoot, ModelPart creeperRoot) {
		Map<String, ModelPart> children = new LinkedHashMap<>();
		children.put("body", phantomRoot.getChild("body"));
		children.put(CREEPER_KEY, creeperRoot);
		return new ModelPart(List.of(), children);
	}

	@Override
	public void setupAnim(PhantomRenderState state) {
		// 幻翼的翅膀拍打 + 尾巴摆动（沿用原版逻辑，操作的是同一批部件实例）
		super.setupAnim(state);

		// 把苦力怕的头身摆到幻翼躯干的位置
		creeper.setPos(phantomBody.x, phantomBody.y + BODY_Y_OFFSET, phantomBody.z);
	}
}
