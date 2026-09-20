# AI 交接提示词（给队友的 AI 看的）

> 队友使用方法：将下面「提示词正文」整段复制发给你的 AI 助手即可（任何能执行命令的 AI）。
> 仓库是公开的，不需要 GitHub 权限邀请。
> AI 完成后你会得到：一个编译通过的本地项目 + 自检报告 + 可部署的 jar。

---

## 提示词正文（从这里开始复制）

```
你要帮我接手一个 Minecraft Fabric 模组项目「random-drops」，目标：从 git 拉取到本地，
配置到「可运行 + 自检通过」，最后打包出可部署的 jar。请严格按下面的步骤执行，每步验证后再进下一步。

【项目基本信息】
- 仓库（公开，直接 HTTPS 克隆）：https://github.com/Yunqingqingxi/random-drops.git
- 主开发分支名是 26.2（跟随 MC 版本，不是 main）
- 项目规范文件 AGENTS.md 在仓库根目录，开工前必须先读它，以它的约定为准

【硬性环境要求】
- 我的系统是 Windows + Git Bash（命令按 bash 语法给）
- 必须用 JDK 25（Fabric API 0.159.0+26.2 硬性要求 java >= 25，JDK 24 会在模组解析阶段被拒）。
  先检测我机器上有没有 JDK 25（常见位置 D:\Java\jdk-25），没有就提示我安装（Temurin Adoptium 25）
- Minecraft 26.2 / Fabric Loader 0.19.5 / Fabric API 0.159.0+26.2（版本都写在 gradle.properties，不要改）

【步骤 1：克隆】
git clone https://github.com/Yunqingqingxi/random-drops.git
cd random-drops
git checkout 26.2
- 验证不是浅克隆：git rev-parse --is-shallow-repository 必须是 false；
  如果是 true，用 git fetch --unshallow origin 补全（否则后面 push 会报 remote unpack failed）

【步骤 2：首次构建（重要：第一次不要加 --offline）】
- 项目文档里的 --offline 是针对「gradle 依赖缓存已热」的老开发者；你在我这台新机器上必须联网拉依赖：
  JAVA_HOME='<你的JDK25路径>' ./gradlew compileJava（不带 --offline）
- 如果下载慢/卡死，给 gradle 配国内镜像：在 ~/.gradle/init.gradle 里把 mavenCentral 换成
  https://maven.aliyun.com/repository/public，Fabric maven 可用 https://maven.fabricmc.net 原地址
- 构建成功标准：BUILD SUCCESSFUL
- 从第二次构建开始再改用 --offline（缓存已热，避免联网卡住）

【步骤 3：跑一遍真服务器自检】
- 按仓库 AGENTS.md 第 6 节的流程：
  1. 把 run/config/random-drops.json 的 "selfTestRolls": 0 改成 200
  2. JAVA_HOME='<你的JDK25路径>' ./gradlew runServer --offline > selftest-local.log 2>&1
     （如果服务器起不来提示缺 eula，把 run/eula.txt 里改成 eula=true 再跑；
      如果 run/config 目录不存在就先跑一次让它自动生成）
  3. 等「===== 自检结束：N 项全部通过 =====」字样，逐项核对有没有 ❌
  4. 把 selfTestRolls 改回 0
  5. 跑完 runServer 不会自动退出，手动结束残留的 java 进程（否则 run/ 目录被锁，后续命令全挂）
- 常见坑：如果报 journal-1.lock / DirectoryLock 拒绝访问，就是有残留 java 进程，全杀掉重跑

【步骤 4：打包】
- JAVA_HOME='<你的JDK25路径>' ./gradlew build --offline
- 产物在 build/libs/random-drops-<版本>.jar（-sources 是源码包，部署用不带 sources 的那个）

【完成标准（缺一不可）】
1. git clone 成功且非浅克隆
2. compileJava 通过
3. 自检全部 ✅（项数以日志为准）
4. build/libs 下有 jar

最后给我一份简报：每步的结果、遇到的问题与解决办法、jar 的完整路径。
如果任何一步卡死，停下来把报错原文给我看，不要瞎猜乱改仓库文件。
```

## 队友自己要做的（AI 替代不了的事）

1. **装 JDK 25**：https://adoptium.net（选 25 / Windows / JDK）；
2. **注册 GitHub 账号**（想改代码并推回仓库才需要；只克隆不用）——提示词见下节。

## 附赠提示词：让 AI 引导我注册 GitHub 账号

```
引导我一步步注册一个 GitHub 账号（我是纯新手），每一步等我完成并确认后再进行下一步：

【背景】我要参与一个 Minecraft 模组项目（仓库已公开），注册 GitHub 是为了之后
被添加为协作者、推送代码。只克隆仓库的话不需要注册。

【要求】
1. 用浏览器打开 https://github.com/signup ，逐项指导我填写：
   - 邮箱：普通邮箱即可（QQ 邮箱 / 163 都可以，需要能收验证邮件）
   - 密码：强密码（16 位以上，字母+数字+符号），提醒我保存好
   - 用户名：建议用「好记的英文/拼音」，这是以后我在团队里的标识；
     被占用会报红，换一个即可
2. 邮箱验证：GitHub 会发一封带 8 位验证码的邮件，指导我查收（可能在垃圾箱）
3. 人机验证：按页面提示完成拼图
4. 免费计划（Free）就够用，不要选付费
5. 引导我开启两步验证（2FA）：推荐用「认证器 App」方式（微软 Authenticator /
   Google Authenticator），提醒我把恢复码截图保存 —— 手机丢了账号还能找回来
6. 完成后：把我的 GitHub 用户名发给仓库主人（云兮），让他把我加为仓库协作者；
   然后按仓库 AI_ONBOARDING.md 的「步骤 1」验证克隆权限

【注意】
- 注册全程不需要给 GitHub 付钱
- 不要在任何界面输入其它网站的密码
- 如果我还没装 JDK 25（本项目必需），提醒我按 AI_ONBOARDING.md 先装
```

## 部署 jar 到服务器

- 把 `build/libs/random-drops-<版本>.jar` 放进服务器的 `mods/` 目录，重启即可；
- 客户端不强制安装（模组纯服务端判定），装了只是能看附魔中文翻译；
- 改完代码想分享成果：commit 后 `git push origin 26.2`（公开仓库，协作者用各自账号推送）。
