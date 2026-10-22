# 遗留问题登记册（Known Issues）

本文件登记全项目代码库（`web/` + `android/` + `docs/`）已识别但未解决的遗留问题，作为单一追踪入口。每条含位置、性质、影响与建议处理时机；处理完毕整条移入「已关闭」节并注明关闭提交。

- 盘点时间：2026-10-17（commit 72d9377，工作区干净）
- 测试基线（盘点当时实测复跑）：Web 113/113 全绿；Android 101/101 全绿（0 失败）
- 盘点方式：全库标记扫描（TODO/FIXME/XXX/HACK 零命中）、跨端数据比对、编译警告收集、文档与实现交叉核对、单文件行数审计
- 复核：2026-10-19 AI 阅读落地后（Web 121/121、Android 214 单测全绿、仪器化 4/4 未变）；IK-04/06/08/12/16/20 关闭

## 优先级定义

| 级别 | 含义 |
| --- | --- |
| P0 | 影响判题正确性或数据一致性，随相关改动必然触发 |
| P1 | 规范/体验债务，不影响当前功能正确性 |
| P2 | 路线图项（预留位转正、增强能力），按产品节奏推进 |

## 总览

| 编号 | 问题 | 位置 | 级别 |
| --- | --- | --- | --- |
| IK-01 | Android 形容词缺 3 个进阶形式 | engine/adjective/AdjectiveData.kt:52 | P0 |
| IK-02 | Android 动词词条 44 vs Web 79 | engine/verb/VerbData.kt | P0 |
| IK-03 | 跨端样例生成器陷阱（ adjectives 105→150 断言崩） | web/tools/generate-cross-samples.js + CrossSampleConsistencyTest.kt:100 | P0 |
| IK-05 | 编译警告 7 条（图标 AutoMirrored ×6、LocalLifecycleOwner ×1） | 见明细 | P1 |
| IK-07 | 文档滞后：D-016 与 particle spec 状态仍写「待实现」 | docs/decisions.md、docs/README.md | P1 |
| IK-09 | Android 应用图标未接入自绘资产 | AndroidManifest.xml:6 + docs/design/icon-concepts/ | P1 |
| IK-10 | Web romaji 归一化失败静默回退 | web/verb-engine.js:641 | P1 |
| IK-11 | 查词入口为规划中位 | ui/study/StudyScreen.kt:142 | P2 |
| IK-13 | 我的页两个预留设置位（模型/API 已转正） | ui/mine/MineScreen.kt:92 | P2 |
| IK-14 | 书签已有写入入口，缺浏览页 | storage/Models.kt:146 + ui/mine | P2 |
| IK-15 | Android 缺活用速查与错题导入/导出（D-008 暂缓） | —（Web 已有） | P2 |
| IK-17 | Web PWA 与打印样式缺失 | web/（无 manifest/sw、无 @media print） | P2 |
| IK-18 | Web aria-live 与焦点管理漏点 | web/index.html（10 处已覆盖，切换场景漏） | P2 |
| IK-19 | 速查页难度过滤未做 | web/app.js:3336 | P2 |

## 明细

### A. 数据一致性（P0）

#### IK-01 Android 形容词缺 3 个进阶形式

- 位置：`android/app/src/main/java/com/nihongodoushi/app/engine/adjective/AdjectiveData.kt:52-60`（FORMS 仅 7 项）
- 对照：`web/adjective-engine.js` FORMS 为 10 项，缺 `conditional`（假定形）、`mizen`（未然形）、`imperativeRequest`（命令/请求形）
- 影响：Android 形容词练习覆盖度低于 Web；`AdjectivePracticeScreen` 形式筛选列表与 Web 不一致；将来若手动对齐需同步 `I_SUFFIXES`/`NA_SUFFIXES`/`II_RESULTS` 映射与讲解文案
- 建议时机：形容词模块下一次扩容时一并补齐（先 Android 后考虑 Web 助手段）

#### IK-02 Android 动词词条缺口

- 位置：`android/app/src/main/java/com/nihongodoushi/app/engine/verb/VerbData.kt`（44 词条）
- 对照：`web/verb-engine.js:56` VERBS 79 词条，差 35 条
- 影响：Android 动词练习抽题池小于 Web；`CrossSampleConsistencyTest` 只校验共有词条的判题一致性，不校验覆盖度，缺口无测试兜底
- 建议时机：与 IK-01 同批处理；移植时逐字段照抄 Web（dictionary/reading/meaning/group/kind）

#### IK-03 跨端样例生成器陷阱

- 位置：`web/tools/generate-cross-samples.js`（形容词样例源）；断言在 `android/app/src/test/java/com/nihongodoushi/app/engine/CrossSampleConsistencyTest.kt:100`（硬编码 `assertEquals(105, samples.adjective.size)`）
- 影响：**在 IK-01 未补齐前重跑该生成器**，Web  Adjectives 扩展到 10 形式后样例将从 105 条变 150 条，Android 侧测试直接失败；verb 样例 4788 条同理受 IK-02 影响
- 建议时机：随 IK-01/IK-02 修复时同批重跑并更新断言；平时不要单独重跑（助词样例已独立为 `generate-particle-data.js` 不受影响）

### B. 工程规范债务（P1）

#### IK-05 编译警告 7 条

| 位置 | 警告 | 处理 |
| --- | --- | --- |
| ui/common/ModuleUi.kt:204 | Icons.Filled.KeyboardArrowRight deprecated | 换 Icons.AutoMirrored.Filled.KeyboardArrowRight |
| ui/home/SuggestionSection.kt:164 | 同上 | 同上 |
| ui/study/StudyScreen.kt:112 | Icons.Filled.List deprecated | 换 Icons.AutoMirrored.Filled.List |
| ui/read/ReadScreen.kt:60 | 同上 | 同上 |
| ui/mine/MineScreen.kt:125 | 同上 | 同上 |
| ui/practice/PracticeUi.kt:156 | 同上 | 同上 |
| ui/practice/PracticeUi.kt:666 | LocalLifecycleOwner deprecated | 迁到 androidx.lifecycle.compose 的 LocalLifecycleOwner |

- 影响：无功能影响；RTL 语言下非 AutoMirrored 图标不镜像（产品当前无 RTL locale，属规范债务）；后续 Compose 升级可能升级为错误
- 建议时机：随手批处理，零风险；LocalLifecycleOwner 迁移需确认 lifecycle-runtime-compose 依赖已声明

#### IK-07 文档滞后

- 位置：`docs/decisions.md` D-016 行末仍写「待实现」；`docs/README.md` 文档地图 `android-particle-module-spec.md` 行状态写「待实现」
- 事实：助词模块已实现并提交（8b9d040 数据+UI、1ba8b4c D-022 错题排期），四标签后仍可访问
- 影响：文档与实现不同步会误导后续协作（人或 AI）以为助词模块待建
- 建议时机：本登记册建立时同批修正

#### IK-09 应用图标未接入

- 位置：`android/app/src/main/AndroidManifest.xml:6` 仍指向 `@mipmap/ic_launcher`（AS 默认模板资产）；自绘资产在 `docs/design/icon-concepts/`（方向 1「あ・纸页」，已轮廓化含 VectorDrawable）
- 影响：发布包装使用默认图标，与品牌视觉不符
- 建议时机：下一个发版周期；接入时需重生成 mipmap 各密度并保留 monochrome（Android 13 主题图标）

### C. Web 端功能缺口（P1/P2）

#### IK-10 romaji 归一化失败静默回退

- 位置：`web/verb-engine.js:641`（`toHiraganaFromRomaji` 遇未知序列 `return source`，调用方 `normalizeAnswer` 无失败信号）
- 影响：用户输入无法识别的罗马字（如含数字/特殊串过了 `/^[a-z']+$/i` 之外的路径不触发；纯字母但表外组合如 "lta"）时按原串比对，直接判错且无「无法识别输入」提示，用户误以为是拼写错误
- 建议时机：低优先级体验修复；可在归一化出口返回 `{ text, converted }` 或对纯 romaji 输入给出轻提示

#### IK-11～IK-14 预留位转正清单（P2）

| 编号 | 位置 | 当前形态 | 转正条件 |
| --- | --- | --- | --- |
| IK-11 查词 | ui/study/StudyScreen.kt:142 | PlannedRow 不可点 | 词典数据源确定后翻 AVAILABLE + 二级页 StudyDestination.DICTIONARY |
| IK-13 设置预留位 | ui/mine/MineScreen.kt:92（发音音色、词库导入导出） | PlannedRow 不可点 | 「模型/API 设置」已转正（可点入口，接通 MainActivity 的 MODEL_SETTINGS）；剩两项：发音音色依赖 TTS 引擎、词库导入导出依赖词库数据源，仍待定 |
| ~~IK-14 书签浏览页~~ | — | 已关闭（2026-10-21） | 见「已关闭」节 |

> 更新：IK-12/IK-13 的代码已落地（批 1–3），IK-12 已关闭（见「已关闭」节）；IK-13 从 3 个预留位收窄为 2 个（「模型/API 设置」已转正为可点入口）。**IK-14 已关闭**（2026-10-21）：收藏入口搬进词详情弹层（「收藏这个词」/「收藏这一段」），我的页书签区按数据渲染真实列表并支持删除。

#### IK-15 Android 速查与错题导入导出

- 位置：D-008 暂缓项；Web 已有活用速查表与错题 JSON 导入导出（cd6e4a7），Android 均无
- 影响：Android 用户无法离线查活用表、无法迁移错题数据
- 建议时机：跟随动词/形容词数据补齐批次（IK-01/02）后评估

#### IK-17 PWA 与打印样式

- 位置：`web/` 无 manifest.json / service worker；`web/styles.css` 无 `@media print`
- 影响：无法离线安装为桌面应用；错题本/速查表无法打印
- 建议时机：Web 端下一轮增强；PWA 需先定缓存策略（词库 JSON 可全量缓存）

#### IK-18 aria-live 与焦点管理漏点

- 位置：`web/index.html` 已有 10 处 aria-live（计数/反馈面板/toast）
- 缺口：模块切换（subtab）、视图切换（练习/速查）、错题清空确认的结果未播报；切换后焦点管理未处理
- 影响：屏幕阅读器用户错过状态变化
- 建议时机：下一轮无障碍专项（建议先补自动化断言进 ui.test.js）

#### IK-19 速查页难度过滤

- 位置：`web/app.js:3336` 速查表按「基础活用/进阶活用」分组展示，无按难度筛选
- 影响：查找特定难度形式需滚动
- 建议时机：低优先级

## 已关闭

#### IK-12 阅读工具四项全为规划中位（关闭：AI 阅读已落地）

- 处理：`ui/read/ReadScreen.kt` 由四项收为三项——`ai-reading` 翻 AVAILABLE 渲染为可点行（右侧箭头 + 水波 + contentDescription「打开 AI 阅读」），`analysis`（语法单词解析）并入后删除（句子解析即 AI 阅读核心能力，单列会形成两个入口指向同一能力），`layout`/`writing` 保持 PLANNED。链路：ReadScreen → MainActivity `SubPage.READ_AI` → `ReadFlow`（输入页/阅读器页/设置页三路由）→ `net/HttpAnalyzer` → 用户自填的 OpenAI 兼容地址。
- 依据：`docs/android-read-analyzer-spec.md` §9–§10、§15（批 3）；决策 D-033～D-037
- 验证：`engine/read` + `net` + `ui/read/ReadUiState` 单测全绿（全量纯 JVM 回归 OK(214)）；`ReadUiStateTest` 17 条含并发与取消断言。**真机手工验收未做**（本机无模拟器与 Gradle），留给用户侧。

#### IK-14 书签浏览页（2026-10-21 关闭）

- 处理：写作侧——阅读卡片右下角的星标已删除，两个收藏入口移进词详情弹层底部：「收藏这个词」（`type="word"`，refId 为「段落缓存键|词面」）与「收藏这一段」（`type="sentence"`，refId 为段落缓存键，沿用 `sentence::` 前缀以兼容既有数据），已收藏时文案变「已收藏 · 取消」再点移除。浏览侧——`ui/mine/MineScreen.kt` 段三由「无条件渲染空态卡」改为按 `app.bookmarks.items` 分流：为空才渲染空态卡（文案指向词详情弹层，不再提已删除的星标），非空则 `BookmarkListSection` 按 `createdAt` 倒序渲染真实列表，每条带类型标记（单词/段落）+ title + note + 删除入口（`AppState.removeBookmark`），删除后计数与列表同源刷新。
- 依据：`docs/android-read-analyzer-spec.md` §9.4/§9.5、F-10、AC-18
- 验证：新增 `ui/mine/MineBookmarkListTest.kt`（排序 + 类型映射 4 例），Android 单测 **files=24 tests=248 failures=0 errors=0**；真机 emulator-5554 实测「1 条 → 列表显示『单词 · 私 · 私は毎朝七時に起きます。』→ 点✕删除 → 『0 条』+ 空态卡，落盘 `{"items":[]}`」，空态与列表互斥、计数同步。
- 未做（另立）：点书签条目跳回原文（需处理缓存已清、段落定位、阅读会话判断，属跨页面导航改造）。

#### IK-04 练习屏无状态保存（2026-10-18 关闭）

- 处理：四个练习屏接入 `rememberSaveable`（`PracticeStateSaver.kt` / `verbStateSaver` 等）；qa36 模拟器「HOME → am kill → 重启」实测两轮，题目进度、选中态、判题反馈态均完整恢复，流程可续行（验收截图 `docs/design/particle-ui/qa-restart-1~3*.png`）

#### IK-06 AppState.kt 超长（2026-10-18 关闭）

- 处理：按领域拆为 5 文件（D-032）——主文件 128 行（状态声明 + internal set + 会话核心 + companion），`AppStateMistakes.kt` 119 / `AppStateStats.kt` 103 / `AppStateParticle.kt` 93 / `AppStateMastery.kt` 91；调用语法不变，行为经单测 111/111 + 仪器化 4/4 验证

#### IK-08 提交未推送（2026-10-18 关闭）

- 处理：`git push origin master` 成功（1ba8b4c → 9f9d952，批次 A/B/C 共 6 个提交），远端已同步

#### IK-16 Web 看答案通道缺失（2026-10-18 关闭）

- 处理：Web 练习反馈区新增「查看答案」通道，与既有规律提示并列；测试基线随批次升级（Web 121/121）

#### IK-20 Android 助词模块实测缺口（2026-10-18 关闭）

- ① TalkBack 语义树实测：仪器化 `TalkBackMergeTest` 4 用例（合并树口径 + picker 选中态，变异验证非空断言），并落实 D-030 真修复（selected 语义 / onLongClickLabel）
- ② 进程重启状态验证：qa36 两轮 `HOME → am kill → 重启` 实测，练习屏题目进度与选中/判题态均经 rememberSaveable 恢复（截图 `qa-restart-1~3`）
- ③ Web↔Android mistakes-v2 互通实测：qa36 真实答错产生错题（`run-as cat files/japanese-particle-practice-mistakes-v2.json` 取出）→ Playwright 将该 JSON 原样注入 Web localStorage 同名键 → 10 项断言全过：纳秒精度 ISO 时间戳可解析、角标计数、复习列表 1 条（错 1 次/下次复习 10/19）、function 题句 before+mark+after 正确拼接、到期判定（dueAt 明天 + session=5 → 今天 0 道到期）、复习按钮可用（截图 `qa-ik20-web-read.png`）

## 复核方法

- 标记扫描：`grep -rn "TODO\|FIXME\|XXX\|HACK" web android --include="*.js" --include="*.kt" --include="*.html" --include="*.css"`
- 跨端比对：动词/形容词词条数与形式数以本文 IK-01/02 引用的文件行为准
- 测试基线：Web 在 `web/` 下 `node --test tests/<六个文件>`；Android `./gradlew :app:testDebugUnitTest`（需先 export JAVA_HOME）
- 编译警告：`./gradlew :app:compileDebugKotlin --rerun-tasks 2>&1 | grep '^w:'`
