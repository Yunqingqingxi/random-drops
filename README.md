# yunxigames — Minecraft 26.2 玩法包系列

**一个系列，五个玩法包，五份 jar**。每个包自包含、按需安装 —— 装哪个玩哪个，包与包之间**零硬依赖**。

| 玩法包 | jar / mod id | 文档 | 一句话 |
| --- | --- | --- | --- |
| **随机掉落** | `yg-drops-1.15.0.jar` / `yg_drops` | [drops/README.md](drops/README.md) | 每一次掉落都换成随机结果：物品 67% / 生物 8% / 空 25%，含暴击宝藏池、保底、精英怪、击杀赌注、地面规则、通关结算。**本系列的核心玩法** |
| **更多附魔** | `yg-enchants-1.15.0.jar` / `yg_enchants` | [enchants/README.md](enchants/README.md) | 十个自定义附魔（雷霆万钧 / 臭脚 / 碎裂 / 磁石 / 贪婪 / 诅咒系 / 汲取 / 疾风 / 威压）+ 击杀升级 + 图书管理员重做 |
| **事件 / 悬赏** | `yg-events-1.15.0.jar` / `yg_events` | [events/README.md](events/README.md) | 全局事件（青蛙雨 / 天降陨石 / 雷池 / 血月 / 福到）+ 猎杀悬赏 + Boss 条 HUD |
| **Bingo** | `yg-bingo-1.15.0.jar` / `yg_bingo` | [bingo/README.md](bingo/README.md) | 物品 / 击杀双板集卡，5×5 板画在地图上，连线发奖 |
| **更多生物** | `yg-mobs-1.15.0.jar` / `yg_mobs` | [mobs/README.md](mobs/README.md) | 「苦力怕幻翼」—— 幻翼的翅膀 / 尾巴 / 飞行姿态全保留，头与躯干换成苦力怕，俯冲命中爆炸 + 自定义俯冲音效 |

每包自带：

- **一份独立配置** —— `config/yg-<包名>.json`（装哪个包就只生成哪份配置，互不干扰）；
- **一套独立自检** —— 见各包文档的「自检」一节；
- **自己的入口与基础库副本** —— 五包同名类各持一份，因此可以单独安装、任意组合。

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

```powershell
# 需要 JDK 25 —— Fabric API 0.159.0+26.2 硬性要求 Java >= 25，
# 用 JDK 24 跑 runServer 会在模组解析阶段就被拒绝
$env:JAVA_HOME = 'D:\Java\jdk-25'   # 换成你自己的 JDK 25 路径

# 编译检查（开发期每个功能写完就跑，约 20 秒）
.\gradlew.bat compileJava --offline

# 打包：五个 jar 各自落在 <包名>/build/libs/
.\gradlew.bat build --offline
```

产物：

```
drops/build/libs/yg-drops-1.15.0.jar
enchants/build/libs/yg-enchants-1.15.0.jar
events/build/libs/yg-events-1.15.0.jar
bingo/build/libs/yg-bingo-1.15.0.jar
mobs/build/libs/yg-mobs-1.15.0.jar
```

第一次构建（缓存未热）要联网拉 Minecraft 26.2、Fabric Loom 1.17 与 Fabric API，去掉 `--offline` 即可。

**跑开发用服务器**：

```powershell
$env:JAVA_HOME = 'D:\Java\jdk-25'
.\gradlew.bat :drops:runServer --offline
```

⚠️ 多子项目下 loom 的 runServer 工作目录是**子项目自己的 `run/`**
（例如 `drops/run/`），不是仓库根 —— `eula.txt`、`config/`、`mods/`、`world/` 都在那里。
首次要先在 `drops/run/eula.txt` 里同意 EULA。

---

## 更新记录

只列系列级的变化；各包自己的功能历史见各包文档。

| 版本 | 变化 |
| --- | --- |
| **1.15.0（当前）** | **yunxigames 系列化**：项目整体改名 yunxigames，按玩法拆成五个**自包含**玩法包（`yg-drops` / `yg-enchants` / `yg-events` / `yg-bingo` / `yg-mobs`），各自一份 jar、一份 `config/yg-<包名>.json`、各带自己的自检步骤，**零跨包硬依赖**；命令统一为 `/yg`；新增**更多生物**包 —— 「苦力怕幻翼」（幻翼保留原生翅膀/尾巴/飞行姿态与眼睛层，头与躯干换成苦力怕；俯冲命中爆炸 + 俯冲开始播自定义音效） |
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
