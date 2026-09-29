# yunxigames — Minecraft 26.2 玩法包系列

**一个系列，六个玩法包，六份 jar**。每个包自包含、按需安装 —— 装哪个玩哪个，包与包之间**零硬依赖**。

仓库是**五个完全独立的 gradle 项目**（非聚合）：每个项目有自己的构建脚本和**独立版本号**，
`cd <项目> && ./gradlew build` 即可单独构建；mod id / jar 名 / 配置文件跨项目保持稳定。

| 玩法包 | 项目目录 | jar / mod id | 文档 | 一句话 |
| --- | --- | --- | --- | --- |
| **随机掉落** | `random_drops/` | `yg-drops-<版本>.jar` / `yg_drops` | [random_drops/README.md](random_drops/README.md) | 每一次掉落都换成随机结果：物品 67% / 生物 8% / 空 25%，含暴击宝藏池、保底、精英怪、击杀赌注、地面规则、通关结算。**本系列的核心玩法** |
| **更多附魔** | `more_enchants/` | `yg-enchants-<版本>.jar` / `yg_enchants` | [more_enchants/README.md](more_enchants/README.md) | 十一个自定义附魔（雷霆万钧 / 臭脚 / 碎裂 / 磁石 / 贪婪 / 诅咒系 / 汲取 / 疾风 / 威压 / 蓝银撑杆跳）+ 击杀升级 + 图书管理员重做 |
| **事件 / 悬赏** | `world_events/` | `yg-events-<版本>.jar` / `yg_events` | [world_events/README.md](world_events/README.md) | 全局事件（青蛙雨 / 天降陨石 / 雷池 / 血月 / 福到）+ 猎杀悬赏 + Boss 条 HUD |
| **Bingo** | `bingo/` | `yg-bingo-<版本>.jar` / `yg_bingo` | [bingo/README.md](bingo/README.md) | 物品 / 击杀双板集卡，5×5 板画在地图上，连线发奖 |
| **更多生物** | `more_mobs/` | `yg-mobs-<版本>.jar` / `yg_mobs` | [more_mobs/README.md](more_mobs/README.md) | 「苦力怕幻翼」—— 幻翼的翅膀 / 尾巴 / 飞行姿态全保留，头与躯干换成苦力怕，俯冲命中爆炸 + 自定义俯冲音效 |
| **随机换位** | `random_swap/` | `yg-swap-<版本>.jar` / `yg_swap` | [random_swap/README.md](random_swap/README.md) | 受伤随机互换位置：每次受伤按概率与附近一个随机生物（或玩家）瞬间互换，自带落点保护与黑名单 |

每包自带：

- **一份独立配置** —— `config/yg-<包名>.json`（装哪个包就只生成哪份配置，互不干扰）；
- **一套独立自检** —— 见各包文档的「自检」一节；
- **自己的入口与基础库副本** —— 五包同名类各持一份，因此可以单独安装、任意组合；
- **自己的完整 gradle 构建** —— 独立 `settings.gradle` / `build.gradle` / `gradle.properties` / wrapper。

**第三方 mod 兼容**：所有物品 / 生物 / 药水 / 效果池都是运行时动态扫描注册表
（`BuiltInRegistries`），装了「更多物品」之类的 mod，它的东西会**自动进池**，无需适配；
过滤名单按命名空间 / tag 可配置。设计约定详见 [AGENTS.md](AGENTS.md) §1。

> 想了解具体玩法数值、配置字段、自检覆盖范围、命令，请点进对应包的文档。
> 五个包可以全装，也可以只装一个 —— 包与包之间唯一的耦合是一条**软引用**：
> yg-drops 掉出的武器/工具有概率自带「碎裂」附魔，而「碎裂」由 yg-enchants 提供；
> 没装 yg-enchants 时这条自然跳过（不报错、不影响掉落）。

---

## 版本要求

| 组件 | 版本 |
| --- | --- |
| Minecraft | **26.2** |
| Fabric Loader | **0.19.5** |
| Fabric API | **0.159.0+26.2** |
| Java | **25** |

> Fabric Loader 版本号写在 `gradle.properties`（`loader_version=0.19.5`），
> 模组元数据 `fabric.mod.json` 里声明为 `~0.19.5`（0.19.5 及以上、0.20 以下可用）。
> **Java 必须是 25** —— Fabric API 0.159.0+26.2 硬性要求 `java >= 25`，JDK 24 会在模组解析阶段被拒。

---

## 安装

**服务端**（主要用法）：

1. 装好 Fabric Loader 0.19.5 的服务端；
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api) 和想玩的玩法包 jar 一起放进 `mods/`；
3. 启动。首次启动会按装的包生成对应的 `config/yg-<包名>.json`。

**朋友那边要不要装？**

| 玩法包 | 玩家需要装吗 |
| --- | --- |
| yg-drops / yg-enchants / yg-events / yg-bingo | **不用**。只在服务端做判定，无自定义渲染、无自定义网络包，原版客户端直连即可 |
| yg-mobs | **要装才看得见**。外观改造（苦力怕头身）是纯客户端资源 + 渲染器，不装也能连服正常玩、爆炸照旧，只是看到的还是原版幻翼外观 |

---

## 三条设计底线

1. **判定只在服务端** —— 除 yg-mobs 的外观（纯客户端资源）外，无自定义网络包，原版客户端可直连；
2. **一局制、零持久化** —— 不写存档、不建排行榜、不做经济，重启即清零；需要状态就放内存
   （UUID 集合、瞬态属性修饰符）；
3. **物品不凭空消失** —— 只有被完全吸收 / 被完全筛掉才取消生成；宁可这一次什么都不掉。

---

## 从源码构建

**每个项目都是独立构建**：先 `cd` 进项目目录，再跑 gradle。

```powershell
# 需要 JDK 25 —— Fabric API 0.159.0+26.2 硬性要求 Java >= 25，
# 用 JDK 24 跑 runServer 会在模组解析阶段就被拒绝
$env:JAVA_HOME = 'D:\Java\jdk-25'   # 换成你自己的 JDK 25 路径

# 例：构建随机掉落（其余项目同理，把目录名换掉即可）
cd random_drops
.\gradlew.bat compileJava --offline   # 编译检查（开发期每个功能写完就跑，约 20 秒）
.\gradlew.bat build --offline         # 打包：jar 落在本项目 build/libs/
```

产物（版本号在各自 `gradle.properties` 的 `mod_version`，各项目独立演进）：

```
random_drops/build/libs/yg-drops-<版本>.jar
more_enchants/build/libs/yg-enchants-<版本>.jar
world_events/build/libs/yg-events-<版本>.jar
bingo/build/libs/yg-bingo-<版本>.jar
more_mobs/build/libs/yg-mobs-<版本>.jar
```

第一次构建（缓存未热）要联网拉 Minecraft 26.2、Fabric Loom 1.17 与 Fabric API，去掉 `--offline` 即可。

**跑开发用服务器**（以随机掉落为例）：

```powershell
$env:JAVA_HOME = 'D:\Java\jdk-25'
cd random_drops
.\gradlew.bat runServer --offline
```

⚠️ loom 的 runServer 工作目录是**本项目自己的 `run/`**（例如 `random_drops/run/`），不是仓库根 ——
`eula.txt`、`config/`、`mods/`、`world/` 都在那里。首次要先在 `random_drops/run/eula.txt` 里同意 EULA。

**mobkit 外观预览工具**（开发期专用，在 `more_mobs/mobkit/`，也是独立构建）：

```powershell
cd more_mobs
.\gradlew.bat build --offline      # 先出 yg-mobs jar（mobkit 靠它拿模型类）
cd mobkit
.\gradlew.bat shot --offline       # 渲染三视图到 more_mobs/mobkit-out/
```

---

## 更新记录

只列系列级的变化；各包自己的功能历史见各包文档。

| 版本 | 变化 |
| --- | --- |
| **yg-enchants 1.2.1** | 蓝银撑杆跳的粒子时机修正：**蓄力期间不再画杆**（杆还只是手里的蓝银草，人正站在立杆点上，画一整根粒子柱会像挂在人身上），**松手起跳那一刻杆才沿杆打一束粒子「现形」**，之后钉在立杆点当「这是我的杆」的标记 —— 人飞出去、粒子留在原地，全程不跟随玩家 |
| **yg-enchants 1.2.0** | **蓝银撑杆跳支持蓄力**：按住右键蓄力，蓝银草每秒长高（等级越高长得越快），一路把挡路的方块顶碎 —— 6 秒起能顶碎泥土木头这类**易碎方块**、30 秒起连**石头类**也顶得动（基岩/黑曜石永远顶不动，杆就停在它下面）；**起跳高度不再有上限**，杆有多长就撑多高，蓄力硬上限 5 分钟到点自动起跳。松手信号走原版「使用中 → RELEASE_USE_ITEM」通道（≤1 刻延迟），另有右键包心跳与到顶自动起跳两道兜底。落地缓冲改为「按住直到真的落地」 |
| **yg-enchants 1.1.0** | **新附魔「蓝银撑杆跳」**（只能附在木棍上）：右键立起 5 格高的杆，人按物理弧线撑起来向前飞出，杆随后按刚体倒伏反向倒下（α = 3g/2L·sinθ，角动量守恒）；起跳高度由「杆的弹性能 + 助跑动能」预算二分反解 MC 的积分器得到，杆顶是硬上限，水平动量守恒 —— 跑多快飞多远，站着不动撑不起来。另补齐了全部 11 个自定义附魔的中文/英文语言文件（此前附魔名在游戏里显示为原始 key），并修掉自检 ⑲ 的亡灵生成判定 |
| **结构重构（2026-09-29）** | **拆分为五个完全独立的 gradle 项目（非聚合）**：目录按玩法语义改名（`random_drops/` / `more_enchants/` / `world_events/` / `bingo/` / `more_mobs/`），每个项目有自己的构建脚本与**独立版本号**（mod id / jar 名 / 配置文件名不变，兼容已发布版本）；mobkit 外观预览工具与参考素材并入 `more_mobs/`；根目录不再是 gradle 构建；固化**第三方 mod 兼容设计约定**（注册表动态扫描、命名空间过滤、tag merge、不覆盖 `assets/minecraft`）。各项目版本号自此从 `1.0.0` 重新起版 |
| **1.15.0** | **yunxigames 系列化**：项目整体改名 yunxigames，按玩法拆成五个**自包含**玩法包（`yg-drops` / `yg-enchants` / `yg-events` / `yg-bingo` / `yg-mobs`），各自一份 jar、一份 `config/yg-<包名>.json`、各带自己的自检步骤，**零跨包硬依赖**；命令统一为 `/yg`；新增**更多生物**包 —— 「苦力怕幻翼」（幻翼保留原生翅膀/尾巴/飞行姿态与眼睛层，头与躯干换成苦力怕；俯冲命中爆炸 + 俯冲开始播自定义音效） |
| 1.14.1 | Bingo 地图修复；植被不掉落；击杀升级扩展到全部附魔（含原版）；幸运加成；图书管理员交易重做 |
| 1.14.0 | 附魔三期（汲取/疾风/威压）+ 击杀随机升级附魔；事件轮空 bug 修复；陨石真实化；新事件雷池/血月/福到；猎杀悬赏 + Bingo |
| 1.13.0 | 附魔二期（磁石 / 贪婪 / 负重与易碎诅咒 / 雷碎组合） |
| 1.12.0 | 附魔突破一期（雷霆万钧 / 臭脚 / 碎裂）+ 全局事件（青蛙雨 / 天降陨石）+ Boss 条 HUD |
| 1.11.x | 五组「地面规则」（刷怪蛋禁用、TNT 引燃甩射、掉落合并 + 播报、徒手伐木 + 断肢、分层掉落 + 终极物资）；修「空壳附魔书」bug |
| ≤ 1.10 | 随机掉落核心：暴击宝藏池、保底、击杀赌注、精英怪、进度 / 维度 / 群系调概率、末影龙通关结算 |

---

## 已知限制（系列级）

- **只针对 26.2**。Minecraft 从 26.x 起不再使用 Yarn 映射、官方 jar 也不再混淆，
  但类名和包结构仍在剧烈变动，换版本必须重新核对 Mixin 目标签名。
- **各包的自检编号是包内局部的、不全局唯一**：装了多个包时日志里可能出现重复编号
  （例如 yg-drops 与 yg-enchants 都有 ⑫）。按步骤名读日志即可。
- 各包自己的坑与限制写在各包文档的「已知限制」一节。

---

## 开发与协作

- 协作规范、环境要求、命令、架构导览、26.2 API 踩坑速查、提交清单：见 [AGENTS.md](AGENTS.md)；
- 纯小白 / AI 接手提示词：见 [AI_ONBOARDING.md](AI_ONBOARDING.md)。
