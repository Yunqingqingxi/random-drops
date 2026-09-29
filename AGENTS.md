# AGENTS.md — yunxigames 开发规范与协作约定

> 本文件是所有开发者（人类与 AI 助手）参与本项目的共同入口。
> 开工前请通读；提交前请自查「提交清单」一节。

---

## 1. 项目概览

**yunxigames** 是 Minecraft 26.2 的 Fabric **玩法包系列**：一个仓库、**五个完全独立的 gradle 项目**（非聚合）、五份 jar。
每个项目**自包含**（同名基础类各持一份源码副本）、零跨包依赖，可单独安装、任意组合；
每个项目有自己的 `settings.gradle` / `build.gradle` / `gradle.properties`（**独立版本号**）/ gradle wrapper，
`cd <项目> && ./gradlew build` 即可单独构建，互不影响。

| 玩法包 | 项目目录 | mod id | jar | 配置文件 | 内容 |
| --- | --- | --- | --- | --- | --- |
| 随机掉落 | `random_drops/` | `yg_drops` | `yg-drops` | `config/yg-drops.json` | 掉落随机化引擎（物品 67% / 生物 8% / 空 25%）、暴击宝藏池、保底、精英怪、击杀赌注、进度 / 维度 / 群系调概率、末影龙通关结算、五组地面规则、`/yg` 命令 |
| 更多附魔 | `more_enchants/` | `yg_enchants` | `yg-enchants` | `config/yg-enchants.json` | 十个自定义附魔（雷霆万钧 / 臭脚 / 碎裂 / 磁石 / 贪婪 / 负重与易碎诅咒 / 汲取 / 疾风 / 威压）、击杀升级、图书管理员重做 |
| 事件 / 悬赏 | `world_events/` | `yg_events` | `yg-events` | `config/yg-events.json` | 全局事件（青蛙雨 / 天降陨石 / 雷池 / 血月 / 福到）、猎杀悬赏、Boss 条 HUD |
| Bingo | `bingo/` | `yg_bingo` | `yg-bingo` | `config/yg-bingo.json` | 物品 / 击杀双板集卡，5×5 板画在地图上，连线发奖 |
| 更多生物 | `more_mobs/` | `yg_mobs` | `yg-mobs` | `config/yg-mobs.json` | 「苦力怕幻翼」：幻翼保留原生翅膀 / 尾巴 / 飞行姿态 / 眼睛层，头与躯干换成苦力怕；俯冲命中爆炸 + 俯冲开始播自定义音效 |

- **根目录不是 gradle 构建**：根只有本文档、`README.md`、`AI_ONBOARDING.md`、`LICENSE` 和五个项目目录。
  任何 gradle 命令都要先 `cd` 进对应项目；仓库根没有 `gradlew`。
- **`more_mobs/mobkit/`**：生物外观预览工具（开发期专用，不发布）。它是**另一个独立 gradle 构建**
  （不是 more_mobs 的子项目），靠 `../build/libs/yg-mobs-<版本>.jar` 拿模型类；
  more_mobs 升版本时要同步改 `more_mobs/mobkit/gradle.properties` 的 `mobs_version`。
- **`more_mobs/assets/`** 是**参考素材**（原版贴图副本），**不在资源路径上、不进 jar**；
  绝不能把它加进 `sourceSets`——那是「覆盖 assets/minecraft」事故的引线（见 §4）。
- **mod id / jar 名 / 配置文件名跨项目保持稳定**（`yg_*` / `yg-<包>` / `yg-<包>.json`）：
  目录名可以改玩法语义名，这三样一改就断已发布版本的兼容。

**第三方 mod 兼容设计约定**（设计任何新玩法前先对照）：

1. **注册表动态扫描，不硬编码名单**：物品 / 实体 / 药水 / 效果池一律运行时扫
   `BuiltInRegistries`（如 `BuiltInRegistries.ITEM.stream()`），别人 mod 加的物品、生物、
   附魔会**自动进池**，无需本系列做适配；
2. **过滤用 `Identifier` 命名空间 + tag，不做类型假设**：黑名单按 `modid:` 前缀或
   `#tag` 可配置（写进本包 Config），判断走 tag / 注册表，不 `instanceof` 原版物品类；
3. **数据包资源 merge 不覆盖**：自定义 tag / 附魔 JSON 放**自己的** `data/<id>/`，
   需要挂原版 tag（如 `curse`）时放 `data/minecraft/tags/...`（tag 天然 merge）；
   **永远不覆盖 `assets/minecraft/**`**；
4. **ItemStack / 实体处理保持通用**：写 NBT、拿 id、比标签都用注册表 API，
   `makeStack` 式构造对 modded 物品同样成立；
5. **新功能必须回答**：「装了『更多物品』类 mod 后，它的东西会不会进我的池子？会不会崩？」
   ——答案必须是「自动进、不崩」，否则回到第 1 条重写。

**三条不可动摇的设计底线**（改功能前先对照）：

1. **只在服务端做判定** —— 唯一例外是 `mobs` 的外观改造（纯客户端资源 + 渲染器，无自定义网络包），
   玩家用原版客户端可直连；
2. **一局制、零持久化** —— 不写存档、不建排行榜、不做经济，重启即清零；
   需要状态就放内存（UUID 集合、瞬态属性修饰符）；
3. **物品不凭空消失** —— 只有被完全吸收/被完全筛掉才取消生成；宁可这一次什么都不掉。

**文档分工**：根 `README.md` 是系列总览（安装 / 构建 / 系列级更新记录），
每个包的玩法、配置字段、自检覆盖写在自己的 `<包名>/README.md`。

---

## 2. 环境要求（硬性）

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Minecraft | 26.2 | 不做向后兼容 |
| Fabric Loader | 0.19.5 | `gradle.properties` 的 `loader_version` |
| Fabric API | 0.159.0+26.2 | 依赖已在本地 gradle 缓存 |
| **JDK** | **25** | Fabric API 0.159.0+26.2 硬性要求 `java >= 25`，JDK 24 会在模组解析阶段被拒 |

- 本机 JDK 25 路径：`D:\Java\jdk-25`。
- **所有 gradle 命令加 `--offline`**（依赖已缓存，联网会卡死）。
- **runServer 必须显式指定 JDK 25**（见下），否则 fork 进程默认拿 JDK 24 直接启动失败。

---

## 3. 常用命令

```bash
# 队友克隆（仓库公开，HTTPS 即可）
git clone https://github.com/Yunqingqingxi/random-drops.git
cd random-drops
# 主开发分支叫 26.2（跟随 MC 版本），克隆后即在本地
git checkout 26.2
# 新手 / AI 接手提示词见 AI_ONBOARDING.md
```

```bash
# ⚠️ 五个项目互相独立，所有 gradle 命令先 cd 进对应项目目录：
cd random_drops    # 或 more_enchants / world_events / bingo / more_mobs（工具：more_mobs/mobkit）

# 编译检查（开发期每个功能写完就跑，~20 秒）
./gradlew compileJava --offline

# more_mobs 是拆分源集项目，改了客户端代码连客户端源集一起编
./gradlew compileJava compileClientJava --offline

# 真服务器自检（见第 6 节的完整流程，~8 分钟）
JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1

# 打包（jar 落在本项目 build/libs/）
JAVA_HOME='D:\Java\jdk-25' ./gradlew build --offline

# mobkit 外观预览（先确保上层 more_mobs build 出过 jar，版本与 mobs_version 一致）
cd more_mobs && ./gradlew build --offline
cd mobkit && JAVA_HOME='D:\Java\jdk-25' ./gradlew shot --offline
```

- 编译 `compileJava` 不强制 JAVA_HOME（走 `options.release = 24` 工具链），
  但 **runServer / build 建议都带上** `JAVA_HOME='D:\Java\jdk-25'`；
- **loom 的 runServer 工作目录是「本项目自己的 `run/`」**：
  在 `random_drops/` 里跑就是 `random_drops/run/`。`eula.txt`、`config/`、`mods/`、`world/` 都在里面；
  **每个项目要单独同意一次 EULA**（首次跑会自动生成 `eula=false`，改成 `true` 再跑）；
  配置也只读本项目 `run/config/` 下的那份 json；
- 跑完 runServer 记得确认 java 进程已退出，否则 `run/` 目录被锁，后续命令全部失败；
- 仓库根那个 `run/` 是聚合时代（2026-09-29 拆分前）的遗留（已被 `.gitignore` 忽略），不要误用。


---

## 4. 架构导览（改代码前先找到对应类）

**每个包都是「入口 + 各功能一个类 + 自己的配置 + 自己的自检」**，
基础类（`Yg` / `YgConfig` / `SelfTest` / `SessionStats` / `LootSupply`）在五个包里**各有一份副本**
（都在 `com.yunxigames` 包下，同名类各 jar 一份，零跨包依赖）——
改基础类行为时**五个包都要同步改**，这是「自包含」换来的代价。

### 公共骨架（每包一份副本）

| 类 | 职责 |
| --- | --- |
| `YunxiGames<包名>` | 该包 mod 入口：注册本包系统、接 Fabric 事件、`SERVER_STOPPING` 清理、挂自检步骤 |
| `Yg` | 本包 `MOD_ID` 与 `LOGGER` |
| `YgConfig` | 配置基类：`debugLog` / `selfTestRolls` + id 列表解析工具（`parseIds` / `parseFilter`） |
| `SelfTest` | 自检**框架**（Step 注册表 + 前/后钩子 + `check` 记录器），不含任何玩法检查 |
| `SessionStats` | 本局战绩（自检期间自动暂停） |
| `LootSupply` | 随机物品供给（全量物品池 / 宝藏池 / 抽样），drops 之外三个包各带一份 |

### `random_drops/`（最大的一包）

| 类 | 职责 |
| --- | --- |
| `DropsConfig` | 本包**全部**配置项 + `validate()` 钳制（新字段必须加默认值与钳制） |
| `DropRandomizer` | 掉落核心：抽物品/生物、保底、暴击、`makeStack`（附魔书/药水写真实数据）、`randomLootOne` |
| `Progression` | 进度分档（EARLY/MID/LATE）、维度 / 群系池 |
| `SpawnerEggGuard` / `TieredDrops` / `EliteMobs` / `Finale` | 刷怪蛋禁用 / 分层掉落 / 精英怪 / 通关结算 |
| `TntIgnition` / `DropMerger` / `DropTally` / `HarvestEvents` / `FallInjury` / `LimbInjury` | 地面规则（引燃 / 合并 / 播报 / 徒手伐木 / 跌落断肢） |
| `KillEffects` / `MobStun` / `Feedback` / `DropTally` | 击杀药水赌注 / 落地僵直 / 反馈演出 / 统计 |
| `command/YunxiGamesCommand` | `/yg` 命令（其它包不自带命令） |
| `DropsSelfTest` | 本包自检（①~⑮ + ⑰ + ㉙ ㉝ ㉟） |
| mixin（`random_drops/src/main/java/com/yunxigames/drops/mixin/`） | 方块掉落、实体合并、摔落伤害的注入点 |

### `more_enchants/` / `world_events/` / `bingo/` / `more_mobs/`

| 类 | 所属 | 职责 |
| --- | --- | --- |
| `ModEnchantments` / `EnchantmentEffects` / `EnchantmentLevelUps` / `LibrarianTrades` | more_enchants | 自定义附魔解析 + 运行期效果（十个附魔）/ 击杀升级 / 图书管理员 |
| `GlobalEvents` / `Bounties` | world_events | 全局事件调度（青蛙雨/陨石/雷池/血月/福到）+ Boss 条 HUD / 猎杀悬赏 |
| `Bingos` | bingo | 双板集卡 + 地图绘制 + 连线判定 |
| `mobs/PhantomSound` | more_mobs | 自定义音效的懒加载解析与播放（俯冲开始） |
| `PhantomCreeperMixin` | more_mobs | **服务端**：幻翼俯冲命中引发苦力怕爆炸（挂 `Mob#doHurtTarget` + `instanceof Phantom` 过滤） |
| `PhantomSweepSoundMixin` | more_mobs | **服务端**：俯冲开始播自定义音效（挂内部类 `Phantom$PhantomSweepAttackGoal`） |
| `PhantomCreeperModel` / `PhantomCreeperRenderer` / `YunxiGamesMobsClient` | more_mobs（client 源集） | **客户端**：翅膀/尾巴用原生幻翼、头身用苦力怕（见 `more_mobs/README.md`） |

- **附魔 JSON**：`more_enchants/src/main/resources/data/yg/enchantment/*.json`（数据包定义）；
- **诅咒红字**：26.2 无 `curse` json 字段，靠 `data/minecraft/tags/enchantment/curse.json` 附魔标签；
- **客户端源集**：只有 `more_mobs` 有 `src/client/java` —— 需要在 `build.gradle` 里
  `loom { splitEnvironmentSourceSets() }`、mods 声明两个源集、并 `jar { from sourceSets.client.output }`
  （**客户端资源不会自动进 jar**，漏了就静默缺贴图）；
- **不要覆盖 `assets/minecraft/**`**：那会连原版资源一起改掉（曾经的 phantom 贴图事故），
  自定义资源一律放 `assets/<自己的 mod id>/**`。`more_mobs/assets/`（参考素材目录）不在资源路径上，
  不要把它挂进 `sourceSets`。

---

## 5. 代码规范

1. **一个功能一个类**，类头 javadoc 写清「是什么 + 为什么这么做」（设计取舍比实现更重要）；
2. **一切数值可配置**：概率/数量/名单进**本包的** `<包名>Config`，带中文注释说明默认值的意图，
   且**每个功能都有独立开关**（默认值原则：「爽但不劝退」）；
3. 新配置项必须同时在 `validate()` 里做钳制（防 NaN/负数/离谱值），
   写法参考 `MobsConfig.validate()`：用 `!(x >= lo && x <= hi)` 顺带把 NaN 也落到默认值；
4. 面向 `ServerLevel`/`LivingEntity` 写逻辑，能用泛化签名就泛化（自检要在无玩家服务器上复用）；
5. **中文回复/注释/文档**；代码里的消息文案用中文（§ 颜色码），lang 键值也是中文；
6. 遇到 26.2 API 不确定：**先查反混淆 jar，别猜**（见第 7 节）。

---

## 6. 测试节奏（团队既定，严格遵守）

- **不写单元测试**。掉落/实体/爆炸/属性全要真服务器，mock 测不出「真的能用」；
- **批量开发新功能时只跑 `compileJava --offline`**，攒一批做完后**统一跑一次 runServer 自检**；
  单点 bug 修复可顺手跑一次自检确认；
- **自检完整流程**（在**目标项目目录里**执行）：
  1. 把本项目 `run/config/yg-<包名>.json` 里 `selfTestRolls` 改成 `200`；
  2. `JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1`；
  3. 等 `===== 自检结束：N 项全部通过 =====`，逐项核对；
  4. **把 `selfTestRolls` 改回 `0`**（别提交带 200 的配置）；
  5. 自检完 runServer 不会自己退出（空转 pausing），手动结束进程；
- **每个包只跑自己的自检**：`SelfTest` 框架是「谁注册谁被跑」，只装一个包时就只跑那一个包的步骤；
- **新增功能必须同步新增自检项**（**包内**编号递进），并更新**本包 `README.md`** 的自检表；
- **自检编号是包内局部的、不全局唯一**：装了多个包时日志里会出现重复编号
  （drops 与 enchants 都有 ⑫），这是拆包后的既定事实，按步骤名读日志；
- **画面类改动自检覆盖不到**：`mobs` 的外观（头身对位、缩放）只能进游戏目视确认，
  自检只能查「贴图在不在 jar 里、有没有误覆盖原版资源、部件字段还在不在」；
- 自检期间 `SessionStats` 自动暂停，假掉落不会污染本局战绩——不用处理。

---

## 7. 26.2 API 踩坑速查（查证方法：见「工具」）

| 要做什么 | 别用（不存在/会错） | 用这个 |
| --- | --- | --- |
| 实体传送/生成定位 | `moveTo(x,y,z)`（3 参不存在） | `setPos(double,double,double)` |
| 判断实体标签（如亡灵） | `entityType.is(TagKey)`（不存在） | `Registry.getTagOrEmpty(tag)` 遍历比 `holder.value().equals(type)` |
| `Identifier` | `net.minecraft.core.Identifier` | `net.minecraft.resources.Identifier` |
| `ServerBossEvent` | `net.minecraft.world.level.…` | `net.minecraft.server.level.ServerBossEvent` |
| `EntityTypeTags` | `net.minecraft.world.entity.…` | `net.minecraft.tags.EntityTypeTags` |
| 属性修饰符 | `AttributeModifier(UUID, …)` | `AttributeModifier(Identifier, amount, Operation)`；`hasModifier/removeModifier(Identifier)` |
| 诅咒附魔红字 | json 里写 `"curse": true` | 加进 `data/minecraft/tags/enchantment/curse.json`（merge 不覆盖 vanilla） |
| 玩家破坏方块回调 | 直接当 ServerPlayer 用（参数是 `Player`） | `instanceof ServerPlayer sp` 过滤后再传 |
| 实体标签存在性 | `EntityType` 静态常量（`LIGHTNING_BOLT` 等） | `BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:…"))` |
| 掉落物生成 | 手动 `new ItemEntity` 后忘了延迟 | `setDefaultPickUpDelay()`；磁石类功能用 `hasPickUpDelay()` 豁免玩家丢弃 |

### 26.2 客户端渲染 / 音效（只有 `mobs` 包会碰）

| 要做什么 | 别用（不存在/会错） | 用这个 |
| --- | --- | --- |
| 改实体外观 | Mixin `@Shadow` 改 `LivingEntityRenderer.model`（父类字段，**`@Shadow` 只在目标类自身查字段**，报 `field model was not located in the target class` → 渲染线程死 → **能启动但全程黑屏**） | **继承原版渲染器**（子类直接访问 protected 字段）+ `EntityRendererRegistry.register(...)` |
| 一个模型用两张贴图 | 指望渲染器分部件换贴图（一条通道一张贴图） | 26.2 是**延迟提交**渲染：在 `submit(...)` 里先 `super.submit(...)`，再自己往 `SubmitNodeCollector` 补一趟 `submitModel(...)` / `submitModelPart(...)`，各用各的 `RenderType` |
| 隐藏模型部件但保留子部件 | `visible = false`（**连子部件一起不渲染**） | `ModelPart.skipDraw = true`（只跳自身方块，子部件照常渲染） |
| 自定义实体贴图 | 覆盖 `assets/minecraft/textures/...`（连原版资源一起改掉，曾把幻翼眼睛层抹成透明） | 放 `assets/<自己的 mod id>/textures/entity/...`，只有自己的渲染器引用它 |
| 客户端源集的资源进 jar | 以为会随 main 一起打包 | `jar { from sourceSets.client.output }` 显式打包（loom `splitEnvironmentSourceSets()` 之后不会自动进） |
| 内部类 Mixin | 直接引用包级私有的内部类 | `@Mixin(targets = "全限定$内部类名")`；取外部实例用 `@Shadow @Final` 字段（**可见性必须与目标一致**，包级私有就不加修饰符） |

**查 API 的方法**（26.2 已去混淆，但包结构变动大，先查再写）：

```bash
# 反混淆 jar（class version 69，必须用 JDK 25 的 javap）
JAR=$(cygpath -w "C:\Users\Y1116\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-common-deobf\26.2\minecraft-common-deobf-26.2.jar")
/d/Java/jdk-25/bin/javap -classpath "$JAR" net.minecraft.world.entity.Entity | grep -i setPos
# 找类位置：
/d/Java/jdk-25/bin/jar tf "$JAR" | grep -i "ServerBossEvent"
```

> 系统 PATH 上的 javap 版本旧，读 26.2 jar 会报 `Unsupported class file version: 69`。

---

## 8. 版本与提交规范

1. **发版流程**：功能做完 → 自检全绿 → **本项目** `gradle.properties` 的 `mod_version` bump
   （三段语义化：功能+1 的 minor，修复+1 的 patch；**每个项目独立版本号，各自演进**）→ `build --offline`
   （产物是本项目一份 jar）→ 更新根 `README.md`（系列更新记录）与**本项目的** `README.md`
   （配置表 / 自检表）与开发日志 → 一个 commit；
   ⚠️ 若升的是 `more_mobs`，同步改 `more_mobs/mobkit/gradle.properties` 的 `mobs_version`；
2. **提交信息**：`<项目> v1.x.y — 一句话主题`，正文列要点（新增/修复/配置项/自检结论）；
3. **提交清单（自查）**：
   - [ ] 改动项目的 `compileJava --offline` 通过（改了 `more_mobs` 的话连 `compileClientJava` 一起）；
   - [ ] runServer 自检全绿（或明确说明未跑的原因；画面类改动必须写明「需游戏内目视」）；
   - [ ] 各包 `run/config/yg-<包名>.json` 的 `selfTestRolls` 已改回 `0`；
   - [ ] 根 `README.md` 与改动包的 `README.md` 已同步；
   - [ ] 日志文件（`*.log`）未被加入提交（`.gitignore` 已覆盖，`git status` 确认）；
   - [ ] 没有往 `assets/minecraft/**` 里塞东西；
4. **README.md 与开发日志（`D:\windows\Fabric_Drop_Mod_Dev_Log.md`）随版本更新**，
   踩的 API 坑必须记进日志的「踩坑记录」，同时回填本文件第 7 节。

---

## 9. 分发约定（回答「别人要不要装 jar」）

- **服务器必须装**：全部玩法判定都在服务端；
- **玩家默认不装也能玩**：除 `yg-mobs` 的外观外无客户端代码，原版客户端可直连；
- **`yg-mobs` 的外观要玩家也装**：苦力怕头身是纯客户端资源 + 渲染器
  （不装也能连服，爆炸照旧，只是看到原版幻翼外观）；
- **建议装**：只为拿附魔 / 事件的中文翻译（语言文件随 jar 走）；
- 五个包可以只发一个：每包自包含，不装其它包也能跑。

---

## 10. 路线图（当前状态与下一步候选）

- 已完成：1.0 随机掉落核心 → 1.8 进度/维度/精英 → 1.9–1.10 击杀赌注 → 1.11 地面规则 →
  1.12 附魔突破+全局事件+HUD → 1.13 附魔二期 → 1.14 附魔三期 + 事件大版本 + Bingo →
  **1.15 yunxigames 系列化（五个自包含玩法包）+ 更多生物包**；
- 待办（1.15.0 发版前）：
  1. runServer 真服务器自检（各包配置 `selfTestRolls=200`，跑完全绿改回 `0`）；
  2. `yg-mobs` 外观游戏内目视 + 头身对位微调（`phantomCreeperBodyYOffset` 等三个配置项）；
  3. GitHub Release 附五份 jar；
- 候选方向（性价比排序）：
  1. **全局事件扩展**（宝藏哥布林、物品雨、陨石坑遗迹——事件框架现成，一个事件一两百行）；
  2. 世界 Boss（复用 HUD 与宝藏雨演出）；
  3. 本局成就 / 全服合作计数（无持久化，内存记录）；
- **不做**：图鉴/存档/经济（违反零持久化底线）。
