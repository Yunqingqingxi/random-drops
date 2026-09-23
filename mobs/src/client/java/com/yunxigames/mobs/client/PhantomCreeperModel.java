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
 * <p>做法不是重新画模型，而是把两个官方模型的部件拼到一棵新的模型树上：
 * <ul>
 *   <li>幻翼的 {@code left/right_wing_base/tip} 与 {@code tail_base/tip} 直接搬过来 —— 翅膀拍打、尾巴摆动
 *       仍由 {@link PhantomModel#setupAnim} 驱动；</li>
 *   <li>苦力怕整棵根部件搬过来（它本身就是身体，{@code head} 挂在它下面，四条腿隐藏）；</li>
 *   <li>幻翼自己的 {@code body} / {@code head} <b>不放进新树</b>，于是自动消失 —— 这就是"头身被替换"。</li>
 * </ul>
 *
 * <p>两个模型的坐标原点不同（幻翼悬空、苦力怕以脚底为原点），所以每帧把苦力怕那块
 * 对齐到幻翼躯干的位置（见 {@link #BODY_Y_OFFSET}，偏了只需改这一个常量）。
 */
public class PhantomCreeperModel extends PhantomModel {

	/** 从幻翼模型里保留的部件：躯干（只当坐标参照）+ 翅膀 + 尾巴。 */
	private static final String[] KEEP = {
			"body",
			"left_wing_base", "left_wing_tip", "right_wing_base", "right_wing_tip",
			"tail_base", "tail_tip",
	};

	/** 苦力怕的四条腿：只要头和身体，腿不要。 */
	private static final String[] CREEPER_LEGS = {
			"right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg",
	};

	/** 新树里挂苦力怕那一块的键名。 */
	private static final String CREEPER_KEY = "yg_creeper";

	/**
	 * 苦力怕块相对幻翼躯干原点的垂直偏移。
	 *
	 * <p>苦力怕模型以脚底为原点（身体在其上方约 6 格），幻翼躯干原点在身体中心，
	 * 所以要把苦力怕往下压一点才对得上。调这个数字就能上下移动苦力怕的头身。
	 */
	private static final float BODY_Y_OFFSET = -6.0F;

	/** 幻翼的躯干部件（不渲染，只用来取坐标）。 */
	private final ModelPart phantomBody;

	/** 苦力怕的整块（根部件 = 身体，head 挂在其下）。 */
	private final ModelPart creeper;

	public PhantomCreeperModel(ModelPart phantomRoot, ModelPart creeperRoot) {
		super(buildRoot(phantomRoot, creeperRoot));

		this.phantomBody = phantomRoot.getChild("body");
		this.creeper = creeperRoot;

		// 幻翼自己的躯干与头不参与渲染（头根本没放进新树，这里再把躯干藏掉）
		this.phantomBody.visible = false;

		// 苦力怕只要头 + 身体：四条腿藏掉
		for (String leg : CREEPER_LEGS) {
			if (creeperRoot.hasChild(leg)) {
				creeperRoot.getChild(leg).visible = false;
			}
		}
	}

	/**
	 * 拼出新模型树：幻翼的翅膀 / 尾巴（同一批 ModelPart 实例，动画照旧）+ 苦力怕的身体。
	 *
	 * <p>注意幻翼的 {@code body} 也放进来了 —— {@link PhantomModel} 的构造会去取它，
	 * 取不到会抛异常；放进来之后我们只是把它设为不可见，不影响渲染。
	 */
	private static ModelPart buildRoot(ModelPart phantomRoot, ModelPart creeperRoot) {
		Map<String, ModelPart> children = new LinkedHashMap<>();

		for (String name : KEEP) {
			if (phantomRoot.hasChild(name)) {
				children.put(name, phantomRoot.getChild(name));
			}
		}

		children.put(CREEPER_KEY, creeperRoot);
		return new ModelPart(List.of(), children);
	}

	@Override
	public void setupAnim(PhantomRenderState state) {
		// 幻翼的翅膀拍打 + 尾巴摆动（沿用原版逻辑）
		super.setupAnim(state);

		// 把苦力怕的头身摆到幻翼躯干的位置
		creeper.setPos(phantomBody.x, phantomBody.y + BODY_Y_OFFSET, phantomBody.z);
	}
}
