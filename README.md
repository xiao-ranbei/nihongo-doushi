# 日语学习工具集（nihongo-doushi）

离线优先的日语学习工具集。同一仓库下并列两个客户端：纯静态网页端（`web/`）与 Kotlin 原生 Android 客户端（`android/`）。运行时不需要服务器、构建工具、外部 API 或第三方题库，学习记录只存在本机。

> 文中标注 **待补** 的条目是尚未定稿的命令参数、本机路径或线上地址，等确认后填写；其余命令、路径与测试结果均按当前仓库实际执行核实。

## 1. 项目用途

面向自学者的日语活用训练工具，解决三件事：背不住活用、分不清易混助词、错了还会再错。

| 能力 | 说明 |
| --- | --- |
| 动词练习 | 79 个动词（五段／一段／不规则），按语态 × 活用形式组合出题，可按类别、变形类型、活用形式、基础／进阶难度筛选 |
| 形容词练习 | 15 个い／な形容词，覆盖现在／过去、肯定／否定、て形、状语、名词修饰等形式 |
| 助词学习 | 9 个专题、48 道题，两类题型：选助词、判断句中助词的作用 |
| 活用速查 | Web 端按动词／形容词查完整活用表，每行可直接「练这题」转成一道题 |
| 错题与复习 | 全局错题本按模块分组，简化间隔复习（1/3/7/14/30 天，连续 6 次正确毕业），支持 JSON 导入导出 |
| 学习主页 | 今日答题量、连续学习天数、各模块当日状态 |

明确不做：账号、云同步、在线题库抓取、社交功能。所有判题规则由本仓库自行实现。

## 2. 安装步骤

### 2.1 只用网页端

无需安装。仓库不含 `package.json`、无 `node_modules`、无构建步骤，`web/` 下的文件即为可运行产物。

```bash
git clone <待补：仓库地址>
cd <待补：仓库目录名>
```

### 2.2 跑测试 / 改网页端

前置：Node.js（实测 v22.22.2；测试用内置 `node:test`，无需 `npm install`）。

```bash
node --version   # 确认版本
```

### 2.3 构建 Android

前置：

| 依赖 | 要求 | 备注 |
| --- | --- | --- |
| JDK | 17 及以上 | 实测 Temurin 21.0.12；`JAVA_HOME` 指向 JDK 根目录 |
| Android SDK | Platform 35 + Build-Tools 35.0.1 | `compileSdk = 35`、`buildToolsVersion = "35.0.1"` |
| 网络 | 能访问 Maven 仓库 | `settings.gradle.kts` 已把阿里云镜像排在 google()/mavenCentral() 之前 |

新建 `android/local.properties`（该文件不入库，需本机自建）：

```properties
sdk.dir=<待补：本机 Android SDK 路径>
```

### 2.4 改字体子集（仅新增界面文案或词库时）

```bash
pip install fonttools brotli
python font/subset.py     # 在 web/ 目录下执行
```

依赖 `web/font/LXGWZhenKaiGB-Regular.ttf`（全量字体，约 16.7MB）与 `web/font/collect-chars.js`；脚本同时更新 `web/font/fonts.js` 清单与 Android 端子集 TTF（`android/app/src/main/res/font/lxgw_zhenkai_subset.ttf`，两端共用同一份 1077 字符表）。

## 3. 运行方式

### 3.1 网页端

直接用浏览器打开 `web/index.html`（`file://` 即可，无需服务器）。需要 HTTP 环境时自行起静态服务，仓库不内置启动脚本：

```bash
cd web
python -m http.server <待补：端口>
# 访问 http://localhost:<待补：端口>/index.html
```

### 3.2 Android

```bash
cd android
export JAVA_HOME=<待补：本机 JDK 路径>
./gradlew :app:assembleDebug        # 调试包
./gradlew :app:testDebugUnitTest    # JVM 单元测试
```

产物：

| 命令 | APK 路径 | 签名 |
| --- | --- | --- |
| `:app:assembleDebug` | `android/app/build/outputs/apk/debug/app-debug.apk` | debug |
| `:app:assembleRelease` | `android/app/build/outputs/apk/release/app-release.apk` | release（需 `android/keystore/keystore.properties`） |

安装到设备的方式待补（未实测 `adb` 通路）。

### 3.3 线上部署

`web/**` 的变更推送到 `master` 会触发 `.github/workflows/deploy-pages.yml`，把 `web/` 目录整个发布到 GitHub Pages。首次启用需在仓库 Settings → Pages → Source 选择 GitHub Actions，详见 [docs/deployment-github-pages.md](docs/deployment-github-pages.md)。

线上地址：待补。

## 4. 使用示例

### 4.1 网页端练一道动词题

1. 打开 `web/index.html`，默认进入学习主页；侧栏选「动词」。
2. 练习控制面板选筛选条件（例如类别=五段、活用形式=て形），选具体活用形式时难度筛选自动停用。
3. 题面给出动词与目标形式，读音和类别默认隐藏，点击卡片可展开。
4. 输入答案，汉字、假名或常见罗马字均可。例如目标为「食べる」的ます形，输入 `たべます` 或 `tabemasu` 都判对。
5. 提交后整张练习卡绿色／红色扫光，显示正确形态、读音、大意与变化规则；答错同时进入错题本。

### 4.2 从速查表直接练

动词 →「活用速查」→ 选动词 → 展开某个变形类型的表格 → 点该行「练这题」，跳到练习并锁定为这一题。

### 4.3 错题复习与导出

错题本 → 按动词／形容词分组 →「重练」进入复习，页面顶部显示本轮待练题数的常驻横幅；「导出」得到 JSON，可用于备份或换设备（换浏览器需手动导入）。

### 4.4 Android

底栏两个一级标签：「主页」看今日答题与连续天数，「我的」看累计统计并支持统计清零；动词、形容词练习和错题本是二级页。答题判定为选择题，判定结果在底部抽屉展示。

### 4.5 测试

```bash
# 网页端（在 web/ 目录下；Windows 必须显式列出文件，不能只传 tests/ 目录）
node --test tests/verb-engine.test.js tests/adjective-engine.test.js tests/particle-engine.test.js tests/styles.test.js tests/font.test.js tests/ui.test.js

# Android（在 android/ 目录下）
./gradlew :app:testDebugUnitTest
```

当前基线（2026-10-17 实测）：网页端 111/111 通过；Android 70 项、0 失败（VerbEngine 10、AdjectiveEngine 9、QuizBuilder 9、ActivityAggregation 5、CrossSampleConsistency 3、ParticleEngine 19、ParticleCrossSample 5、Mastery 4、ParticleReview 6）。改动后不得低于该基线。

## 5. 关键配置说明

### 5.1 运行时配置

当前没有任何环境变量、`.env`、后端服务地址或密钥配置——网页端与 Android 端都是纯本地运行。需要配置的只有构建环境与本地存储。

### 5.2 本地存储键

网页端用 `localStorage`，Android 端用 `filesDir` 下的同名 `.json` 文件（`android/.../storage/StorageKeys.kt`）。同名键结构对齐，但不做自动迁移。

| 键 | 内容 |
| --- | --- |
| `japanese-verb-practice-mistakes-v1` | 动词与形容词错题、复习进度 |
| `japanese-particle-practice-mistakes-v1` | 助词错题（仅网页端） |
| `japanese-verb-practice-stats-v1` | 动词答对／答错计数 |
| `japanese-verb-practice-adj-stats-v1` | 形容词答对／答错计数 |
| `japanese-particle-practice-stats-v1` | 助词答对／答错计数（仅网页端） |
| `japanese-verb-practice-daily-v1` | 最近练习日期、连续天数、分模块当日题数 |
| `japanese-verb-practice-history-v1` | 近期每日学习行为（仅 Android，供主页周历与统计） |
| `japanese-verb-practice-progress-v1` | 已练词条集合，用于词库覆盖进度（仅 Android） |

### 5.3 Android 构建参数

`android/app/build.gradle.kts`：

| 项 | 值 |
| --- | --- |
| applicationId / namespace | `com.nihongodoushi.app` |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| versionCode / versionName | 1 / 0.1.0 |
| Java / Kotlin target | 17 |
| UI | Jetpack Compose + Material 3 |
| 依赖版本 | 集中在 `android/gradle/libs.versions.toml`（D-015 锁定，改动需同步 `docs/decisions.md`） |

### 5.4 签名配置

`android/keystore/keystore.properties`（**不入库**，由 `android/.gitignore` 排除）：

```properties
storeFile=release.keystore
storePassword=<待补>
keyAlias=<待补>
keyPassword=<待补>
```

`build.gradle.kts` 读该文件；文件缺失时不配置 release 签名配置。密钥丢失后无法覆盖安装同包名应用，只能卸载重装并丢数据，需自行备份。

### 5.5 字体清单

`web/font/fonts.js` 声明运行时字体，由 `subset.py` 自动改写。当前子集 1068 个字符（woff2 约 312KB）。新增任何界面文案（含源码注释里的汉字也会被收集）都要重跑子集，否则回退字体行高不同会导致整页位移。

### 5.6 开发约定（改动前必读）

- 规则写在引擎（`web/*-engine.js` / `android/.../engine/`），`web/app.js` 只做编排，引擎禁止访问 DOM。
- 颜色只能用 `web/styles.css` 的 `:root` 语义令牌；禁 emoji 图标、禁硬编码色值。
- 新增 Web 模块的检查清单见 [docs/module-guide.md](docs/module-guide.md)；架构决策编号见 [docs/decisions.md](docs/decisions.md)。

## 6. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `node --test tests/` 报 `MODULE_NOT_FOUND` | Windows 下不能只传目录 | 显式列出每个测试文件 |
| 加了新文案后整页下移 / 字重不一致 | 子集缺字，回退到系统字体 | `python font/subset.py` 重跑；`tests/font.test.js` 会拦截 |
| 字体测试报字符缺失 | 同上 | 同上 |
| Android 构建报 JDK 版本或找不到 `javac` | `JAVA_HOME` 未指向 JDK 17+ | 导出 `JAVA_HOME` 后重试 |
| 构建卡在下载依赖 / `dl.google.com` 超时 | 本机代理或网络限制 | `settings.gradle.kts` 已配阿里云镜像、wrapper 走腾讯镜像；必要时关代理直连 |
| release 包签名不对或装不上 | `keystore.properties` 缺失或与旧包不一致 | 补回原密钥后重建；签名不一致只能卸载重装（丢数据） |
| 页面提示存储数据无法解析 | `localStorage` 内容被改坏 | 清除对应键（见 5.2）后重新开始 |
| 本地打开和线上站点的记录不互通 | `file://` 与 Pages 的 origin 不同 | 用「导出／导入」JSON 搬运 |
| 线上地址访问 404 | Pages 未启用或 Source 未选 GitHub Actions | 按部署文档设置后重跑 workflow |

## 7. 已知限制

- **无同步与备份**：不做账号、云同步、自动备份；只能手动导出 JSON（Android 端暂不支持导入导出，D-008 延后）。
- **两端数据不通**：Web 与 Android 存储结构对齐但不自动迁移，换端等于从零开始。
- **功能不对齐**：助词模块、活用速查、错题导入导出目前仅 Web 端；Android MVP 只有主页／我的、动词／形容词练习和错题本。
- **设备范围窄**：Android 只按手机竖屏设计（D-010），平板、横屏、折叠屏未适配；Web 桌面端有侧栏、窄屏有抽屉，未做平板专门布局。
- **无 PWA**：没有 Service Worker 与应用清单，网页端无法「安装」到桌面，离线依赖本地文件；Android 端本身离线可用。
- **词库规模有限**：79 动词、15 形容词、9 个助词专题 48 题；不是完整 JLPT 词表。
- **复习模型是简化版**：固定 1/3/7/14/30 天间隔 + 6 次毕业，不是 SM-2／FSRS 之类算法，不按个人记忆强度自适应。
- **只有 JVM 单元测试**：Android 侧没有 instrumented／UI 测试；网页端 UI 测试用零依赖 DOM 桩，不是真实浏览器；视觉回归靠 `web/tools/visual-check.js` + `visual-diff.py` 手动跑（需 Playwright）。
- **界面只中文**：界面文案为中文，日语仅作为学习内容；无多语言切换。
- **线上地址与分发方式未定**：Pages 站点地址、APK 分发渠道待补；签名包按 D-009 仅小范围手动安装。
