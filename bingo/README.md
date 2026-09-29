# yg-bingo — Bingo

| | |
| --- | --- |
| **jar** | `yg-bingo-1.15.0.jar` |
| **mod id** | `yg_bingo` |
| **配置文件** | `config/yg-bingo.json` |
| **自检项** | ㉛（本包自己的编号，装本包才跑） |
| **环境** | 只在服务端做判定，玩家用原版客户端可直连 |

物品与击杀双板集卡：5×5 的 Bingo 板**画在地图上**，集齐连线发奖。

---

## 一、玩法

- **双板**：物品板（收集指定物品）+ 击杀板（击杀指定生物），各有 25 格（5×5）。
- **画在地图上**：整块板绘制在**锁定地图**上（`MAP_POST_PROCESSING`），玩家拿在手里就能看。
- **连线发奖**：集齐一整行 / 一列 / 一条对角线发 `bingoLineRewardCount`（默认 2 件）；
  全板清空发 `bingoClearRewardCount`（默认 8 件）。
- **一局制**：关服清空缓存，下次开服就是新的一局（不写存档）。

## 二、配置（`config/yg-bingo.json`）

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enableItemBingo` | `true` | 物品板开关 |
| `enableKillBingo` | `true` | 击杀板开关 |
| `bingoLineRewardCount` | `2` | 连一条线的奖励件数 |
| `bingoClearRewardCount` | `8` | 全板清空的奖励件数 |
| `entityBlacklist` | 见下 | 不进击杀板的生物黑名单 |
| `debugLog` | `false` | 调试日志（基类字段） |
| `selfTestRolls` | `0` | 开服自检掷骰次数 |

击杀板默认排除的实体（`entityBlacklist`）：`ender_dragon` / `wither` / `giant` /
`illusioner` / `player` —— Boss 与特殊生物不该被集卡。支持 `命名空间:*` 通配。

> 全清（Bingo）拿完大奖后会**换新一局**（重新抽 25 格）。

## 三、自检

`selfTestRolls > 0` 时开服跑 **㉛ Bingo·双板 + 地图 + 连线结构**：
双板都能构建、地图绘制链路可用、连线判定结构正确。

> **编号是包内局部的**：装了多个包时可能出现重复编号，按步骤名读日志即可。

## 四、与其它包的联动

**零跨包依赖**。物品池优先借用 yg-drops 的物品池接口，**没装 yg-drops 也能跑**
（退回原版物品池）。

## 五、踩坑记录（写给后来的维护者）

- **Bingo 板空白 bug（v1.14.1 已修）**：`MAP_POST_PROCESSING=LOCK` 会让 vanilla 停止地图同步
  （锁定地图不建 `HoldingPlayer`），整块板就是空白。修法是**全图涂底但不锁定**。
- Bingo 地图绘制依赖 `MAP_POST_PROCESSING` 这个组件路径，26.2 上改动过，升级版本时要重新核对。
