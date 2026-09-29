# yg-enchants — 更多附魔

| | |
| --- | --- |
| **jar** | `yg-enchants-1.15.0.jar` |
| **mod id** | `yg_enchants` |
| **配置文件** | `config/yg-enchants.json` |
| **自检项** | ⑫ ⑱ ⑲ ㉑ ㉒ ㉓ ㉔ ㉖ ㉗ ㉞ ㊱（本包自己的编号，装本包才跑） |
| **环境** | 只在服务端做判定，玩家用原版客户端可直连 |

十个自定义附魔 + 击杀升级 + 图书管理员交易重做。

---

## 一、附魔清单

附魔由数据包 JSON 定义（`data/yg/enchantment/*.json`），效果在 `EnchantmentEffects` 里运行期结算。

### 附魔突破一期（v1.12）

| 附魔 | 槽位 | 效果 |
| --- | --- | --- |
| **雷霆万钧** | 头盔 | 定时召雷劈周围生物；雷雨天更频（间隔 100 刻 → 20 刻） |
| **臭脚** | 靴子 | 枯萎周围花草 + 附近玩家反胃 + 吸引/额外生成亡灵 |
| **碎裂** | 武器 / 工具 | 攻击 / 挖掘时概率秒杀秒破，或厄运碎全身 |

### 附魔突破二期（v1.13）

| 附魔 | 槽位 | 效果 |
| --- | --- | --- |
| **磁石** | 护甲 | 把附近的掉落物吸向自己 |
| **贪婪** | 工具 | 挖掘时额外随机掉一件 |
| **负重**（诅咒） | 护甲 | 穿上挂 `MOVEMENT_SPEED` 减益 |
| **易碎**（诅咒） | 护甲 | 受击时按概率碎裂 |

### 附魔三期（v1.14）

| 附魔 | 槽位 | 效果 |
| --- | --- | --- |
| **汲取** | 武器 | 命中回血（每级 1.5 点） |
| **疾风** | 靴子 | 加速 |
| **威压** | 头盔 | 震慑敌对生物 |

> **诅咒红字怎么来的**：26.2 的附魔 JSON **没有** `curse` 字段，靠
> `data/minecraft/tags/enchantment/curse.json` 这个附魔标签（merge，不覆盖 vanilla）实现红字。

## 二、击杀升级附魔

击杀生物有 **15%** 概率把身上的附魔**升一级**：I → II → III，**只升不降、各自 `max_level` 封顶**。
诅咒附魔也会被升 —— 那是真实的抉择。

v1.14.1 起扩展到**全部附魔（含原版）**，例如原版锋利 III → IV；诅咒整体排除。

## 三、图书管理员交易重做

每次打开图书管理员的交易界面都会**随机刷新**，且全部是**该附魔的顶级书**
（写入 `STORED_ENCHANTMENTS` 组件 —— 铁砧实际读取的那个），代价是随机池物品 ×1~3
（`librarianMaxCost` 控制上限）。

## 四、配置（`config/yg-enchants.json`）

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enableEnchantmentBreakthrough` | `true` | 附魔突破总开关 |
| `thunderIntervalTicks` | `100` | 雷霆万钧召雷间隔（刻） |
| `thunderStormIntervalTicks` | `20` | 雷雨天间隔 |
| `thunderRadius` | `24.0` | 雷击搜索半径（格） |
| `stinkyRadius` | `8.0` | 臭脚影响半径 |
| `stinkyNauseaSeconds` | `8` | 反胃时长（秒） |
| `stinkyTickInterval` | `20` | 臭脚结算间隔（刻） |
| `stinkyUndeadSpawnChance` | `0.05` | 额外生成亡灵的几率 |
| `stinkyUndeadCap` | `4` | 额外亡灵数量上限 |
| `shatterApplyChance` | `0.10` | 随机掉落的武器/工具自带碎裂的概率 |
| `shatterProcChance` | `0.15` | 碎裂触发几率 |
| `shatterNegativeChance` | `0.25` | 碎裂「厄运」分支的几率（碎全身） |
| `shatterSelfShatterChance` | `0.5` | 碎裂时自伤/自碎几率 |
| `enableMagnet` | `true` | 磁石开关 |
| `magnetRadius` | `8.0` | 吸附半径 |
| `magnetIntervalTicks` | `5` | 吸附结算间隔 |
| `magnetPullStrength` | `0.35` | 吸附强度 |
| `enableGreed` | `true` | 贪婪开关 |
| `greedExtraChance` | `0.10` | 额外掉落几率 |
| `enableCursedEnchantments` | `true` | 诅咒附魔开关 |
| `curseBurdenSpeedPenalty` | `0.20` | 负重诅咒移速惩罚 |
| `curseFrailtyBreakChance` | `0.10` | 易碎诅咒碎裂几率 |
| `enableEnchantLevelUp` | `true` | 击杀升级开关 |
| `killEnchantLevelUpChance` | `0.15` | 击杀升级几率 |
| `enableLeech` | `true` | 汲取开关 |
| `leechHealPerLevel` | `1.5` | 汲取每级回血 |
| `enableSwift` | `true` | 疾风开关 |
| `enableDread` | `true` | 威压开关 |
| `enableLibrarianRefresh` | `true` | 图书管理员随机刷新 |
| `librarianMaxCost` | `3` | 交易代价物品数量上限 |
| `debugLog` | `false` | 调试日志（基类字段） |
| `selfTestRolls` | `0` | 开服自检掷骰次数 |

## 五、自检

`selfTestRolls > 0` 时开服跑这 11 项：

| 编号 | 检查内容 |
| --- | --- |
| ⑫ | 附魔突破·注册 + 核心定义 |
| ⑱ | 雷霆万钧·雷击生成（闪电实体类型可解析 + `addFreshEntity` 成功） |
| ⑲ | 臭脚·花草枯萎 + 亡灵生成 |
| ㉑ | 磁石·注册 + 掉落物被吸附 |
| ㉒ | 贪婪·注册 + 额外掉落（连抽 20 次至少 15 次非空） |
| ㉓ | 负重诅咒·注册 + 移速减益（盔甲架实测：穿上挂上、脱下摘除） |
| ㉔ | 易碎诅咒·注册 + 护甲碎裂（概率拉满实测） |
| ㉖ | 新附魔·汲取 / 疾风 / 威压 |
| ㉗ | 击杀升级·只升不降 + 满级封顶 |
| ㉞ | 升级全附魔·含原版 + 排除诅咒 + 15% |
| ㊱ | 图书管理员·随机顶级附魔书交易 |

> **编号是包内局部的**：本包的 ⑫ 与 yg-drops 的 ⑫（掉落物自动合并）会相撞，
> 装了多个包时按步骤名读日志即可。

## 六、与其它包的联动

本包**零跨包依赖**。与 yg-drops 只有一条**软引用**：
yg-drops 掉出的武器/工具会按 `shatterApplyChance` 附带碎裂附魔 ——
查不到 `yg:shatter` 时（没装本包）自然跳过，不会报错。

## 七、已知限制

- **诅咒附魔的效果开关管不到附魔书池子**：`enableCursedEnchantments: false` 后诅咒书
  仍可能从随机附魔书里掉出（穿了没副作用）；想连书都绝迹需要删除数据包里的两份诅咒 json。
- **碎裂的「厄运」会清空全身 6 格装备且不返还材料**：这是设计好的重罚，不是丢东西 bug；
  觉得太狠就把 `shatterNegativeChance` 调低。
- 附魔 JSON 是**数据包定义**：改数值要改 `src/main/resources/data/yg/enchantment/*.json`
  并重新打包（不是配置项）。
