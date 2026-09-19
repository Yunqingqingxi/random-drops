# AGENTS.md — random-drops 开发规范与协作约定

> 本文件是所有开发者（人类与 AI 助手）参与本项目的共同入口。
> 开工前请通读；提交前请自查「提交清单」一节。

---

## 1. 项目概览

**random-drops** 是 Minecraft 26.2 的 Fabric 随机掉落模组：

- **每一次掉落都被替换成随机结果**（物品 67% / 生物 8% / 空 25%），含暴击宝藏池、
  保底、击杀药水赌注、精英怪、按进度/维度/群系调概率、末影龙通关结算；
- v1.11 起有「地面规则」（刷怪蛋禁用、TNT 引燃、掉落合并、徒手伐木、断肢、分层掉落、终极物资）；
- v1.12 起有「附魔突破」（雷霆万钧/臭脚/碎裂）+ 全局事件（青蛙雨/天降陨石）+ Boss 条 HUD；
- v1.13 起附魔二期（磁石/贪婪/负重与易碎诅咒/雷碎组合）。

**三条不可动摇的设计底线**（改功能前先对照）：

1. **只在服务端做判定** —— 无自定义渲染、无自定义网络包，玩家用原版客户端可直连；
2. **一局制、零持久化** —— 不写存档、不建排行榜、不做经济，重启即清零；
   需要状态就放内存（UUID 集合、瞬态属性修饰符）；
3. **物品不凭空消失** —— 只有被完全吸收/被完全筛掉才取消生成；宁可这一次什么都不掉。

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
# 队友克隆（SSH；推荐先配好 GitHub SSH key）
git clone git@github.com:Yunqingqingxi/random-drops.git
cd random-drops
# 主开发分支叫 26.2（跟随 MC 版本），克隆后即在本地
git checkout 26.2
```

```bash
# 编译检查（开发期每个功能写完就跑，~20 秒）
./gradlew compileJava --offline

# 真服务器自检（见第 6 节的完整流程，~8 分钟）
JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1

# 打包（jar 落在 build/libs/）
JAVA_HOME='D:\Java\jdk-25' ./gradlew build --offline
```

- 编译 `compileJava` 不强制 JAVA_HOME（走 `options.release = 24` 工具链），
  但 **runServer / build 建议都带上** `JAVA_HOME='D:\Java\jdk-25'`；
- 跑完 runServer 记得确认 java 进程已退出，否则 `run/` 目录被锁，后续命令全部失败；
- 服务器未同意 EULA 前无法启动：`run/eula.txt` 里 `eula=true`。

---

## 4. 架构导览（改代码前先找到对应类）

| 类 | 职责 |
| --- | --- |
| `RandomDrops` | mod 入口：注册各系统、接 Fabric 事件、SERVER_STOPPING 清理 |
| `RandomDropsConfig` | **所有**配置项 + `validate()` 钳制（新字段必须加默认值与钳制） |
| `DropRandomizer` | 掉落核心：抽物品/生物、保底、暴击、`makeStack`（附魔书/药水写真实数据）、`randomLootOne` |
| `PityTracker` / `SessionStats` / `DropTally` | 保底计数 / 本局战绩 / 播报统计 |
| `Progression` | 进度分档（EARLY/MID/LATE）、维度/群系池 |
| `SpawnerEggGuard` / `TieredDrops` / `EliteMobs` / `Finale` | 刷怪蛋禁用 / 分层掉落 / 精英怪 / 通关结算 |
| `TntIgnition` / `DropMerger` / `HarvestEvents` / `FallInjury` / `LimbInjury` | v1.11 地面规则 |
| `ModEnchantments` | 自定义附魔的注册表解析（缓存 Holder）+ 判定工具 |
| `EnchantmentEffects` | 七个自定义附魔的运行期效果（tick + Fabric 事件） |
| `GlobalEvents` | 全局事件调度（青蛙雨/陨石）+ Boss 条 HUD |
| `SelfTest` | 真服务器自检（26 项），`selfTestRolls` 触发或 `/randomdrops selftest` |
| mixin（`src/main/java/com/randomdrops/mixin/`） | 方块掉落、实体合并、摔落伤害的注入点 |

- **附魔 JSON**：`src/main/resources/data/randomdrops/enchantment/*.json`（数据包定义）；
- **诅咒红字**：26.2 无 `curse` json 字段，靠 `data/minecraft/tags/enchantment/curse.json` 附魔标签；
- **翻译**：`src/main/resources/assets/randomdrops/lang/en_us.json`（服务端侧中文即用它）。

---

## 5. 代码规范

1. **一个功能一个类**，类头 javadoc 写清「是什么 + 为什么这么做」（设计取舍比实现更重要）；
2. **一切数值可配置**：概率/数量/名单进 `RandomDropsConfig`，带中文注释说明默认值的意图，
   且**每个功能都有独立开关**（默认值原则：「爽但不劝退」）；
3. 新配置项必须同时在 `validate()` 里做钳制（防 NaN/负数/离谱值），参考 v1.13 区块的写法；
4. 面向 `ServerLevel`/`LivingEntity` 写逻辑，能用泛化签名就泛化（自检要在无玩家服务器上复用）；
5. **中文回复/注释/文档**；代码里的消息文案用中文（§ 颜色码），lang 键值也是中文；
6. 遇到 26.2 API 不确定：**先查反混淆 jar，别猜**（见第 7 节）。

---

## 6. 测试节奏（团队既定，严格遵守）

- **不写单元测试**。掉落/实体/爆炸/属性全要真服务器，mock 测不出「真的能用」；
- **批量开发新功能时只跑 `compileJava --offline`**，攒一批做完后**统一跑一次 runServer 自检**；
  单点 bug 修复可顺手跑一次自检确认；
- **自检完整流程**：
  1. 把 `run/config/random-drops.json` 的 `selfTestRolls` 改成 `200`；
  2. `JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1`；
  3. 等 `===== 自检结束：N 项全部通过 =====`，逐项核对；
  4. **把 `selfTestRolls` 改回 `0`**（别提交带 200 的配置）；
  5. 自检完 runServer 不会自己退出（空转 pausing），手动结束进程；
- **新增功能必须同步新增自检项**（编号递进：㉔ 之后是 ㉕…），并更新 README 的自检表；
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

1. **发版流程**：功能做完 → 自检全绿 → `gradle.properties` 的 `version` bump
   （三段语义化：功能+1 的 minor，修复+1 的 patch）→ `build --offline` →
   更新 `README.md`（版本表/配置/自检表）与开发日志 → 一个 commit；
2. **提交信息**：`v1.x.y — 一句话主题`，正文列要点（新增/修复/配置项/自检结论）；
3. **提交清单（自查）**：
   - [ ] `compileJava --offline` 通过；
   - [ ] runServer 自检全绿（或明确说明未跑的原因）；
   - [ ] `selfTestRolls` 已改回 `0`；
   - [ ] `README.md` 版本表与自检表同步；
   - [ ] 日志文件（`selftest-*.log` 等）未被加入提交（`.gitignore` 已覆盖，`git status` 确认）；
4. **README.md 与开发日志（`D:\windows\Fabric_Drop_Mod_Dev_Log.md`）随版本更新**，
   踩的 API 坑必须记进日志的「踩坑记录」，同时回填本文件第 7 节。

---

## 9. 分发约定（回答「别人要不要装 jar」）

- **服务器必须装**：全部判定在服务端；
- **玩家不强制**：无客户端代码，原版客户端可玩；
- **建议装**：只为拿附魔/事件的中文翻译（语言文件随 jar 走）。

---

## 10. 路线图（当前状态与下一步候选）

- 已完成：1.0 随机掉落核心 → 1.8 进度/维度/精英 → 1.9–1.10 击杀赌注 → 1.11 地面规则 →
  1.12 附魔突破+全局事件+HUD → 1.13 附魔二期；
- 候选方向（性价比排序）：
  1. **全局事件扩展包**（血月、宝藏哥布林、物品雨、陨石坑遗迹——事件框架现成，一个事件一两百行）；
  2. 世界 Boss（复用 HUD 与宝藏雨演出）；
  3. 本局成就/全服合作计数（无持久化，内存记录）；
- **不做**：图鉴/存档/经济（违反零持久化底线）。
