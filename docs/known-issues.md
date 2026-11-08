# 遗留问题登记册（Known Issues）

本文件登记全项目代码库（`web/` + `android/` + `docs/`）已识别但未解决的遗留问题，作为单一追踪入口。每条含位置、性质、影响与建议处理时机；处理完毕整条移入「已关闭」节并注明关闭提交。

- 盘点时间：2026-10-17（commit 72d9377，工作区干净）
- 测试基线（盘点当时实测复跑）：Web 113/113 全绿；Android 101/101 全绿（0 失败）
- 盘点方式：全库标记扫描（TODO/FIXME/XXX/HACK 零命中）、跨端数据比对、编译警告收集、文档与实现交叉核对、单文件行数审计
- 复核：2026-10-19 AI 阅读落地后（Web 121/121、Android 214 单测全绿、仪器化 4/4 未变）；IK-04/06/08/12/16/20 关闭
- 复核：2026-11-05 推送 578b93e 前（Web 121/121、Android 25 类 264 项 0 失败、`compileDebugKotlin --rerun` 强制重编 0 警告）；IK-01/02/03/05/07/14 关闭
- 复核：2026-11-05 第二批（Web 122/122）；IK-10/18 关闭（实现此前已落地，本批补断言并核实）
- 复核：2026-11-06 Web 文章阅读 B0–B9 收口（Web 396/396）；IK-11 收窄为独立查词入口；新增 IK-21/IK-22

## 优先级定义

| 级别 | 含义 |
| --- | --- |
| P0 | 影响判题正确性或数据一致性，随相关改动必然触发 |
| P1 | 规范/体验债务，不影响当前功能正确性 |
| P2 | 路线图项（预留位转正、增强能力），按产品节奏推进 |

## 总览

| 编号 | 问题 | 位置 | 级别 |
| --- | --- | --- | --- |
| ~~IK-01~~ | Android 形容词缺 3 个进阶形式 | — | 已关闭（52731c9，批次 A） |
| ~~IK-02~~ | Android 动词词条 44 vs Web 79 | — | 已关闭（52731c9，批次 A） |
| ~~IK-03~~ | 跨端样例生成器陷阱（ adjectives 105→150 断言崩） | — | 已关闭（52731c9，批次 A） |
| ~~IK-05~~ | 编译警告 7 条（图标 AutoMirrored ×6、LocalLifecycleOwner ×1） | — | 已关闭（2026-11-05 实测 0 警告） |
| ~~IK-07~~ | 文档滞后：D-016 与 particle spec 状态仍写「待实现」 | — | 已关闭（6e5109a + 本次 spec 标题修正） |
| ~~IK-09~~ | Android 应用图标未接入自绘资产 | — | 已关闭（2026-10-22） |
| ~~IK-10~~ | Web romaji 归一化失败静默回退 | — | 已关闭（52731c9 实现，本批核实） |
| IK-11 | 查词入口为规划中位（文章内词详情已随 AI 文章解析两端落地，本条收窄为不依托文章的独立查词） | ui/study/StudyScreen.kt:142 | P2 |
| IK-13 | 我的页两个预留设置位（模型/API 已转正） | ui/mine/MineScreen.kt:92 | P2 |
| ~~IK-14~~ | 书签已有写入入口，缺浏览页 | — | 已关闭（b75c6c1，2026-10-21） |
| IK-15 | Android 缺活用速查与错题导入/导出（D-008 暂缓） | —（Web 已有） | P2 |
| IK-17 | Web PWA 与打印样式缺失 | web/（无 manifest/sw、无 @media print） | P2 |
| ~~IK-18~~ | Web aria-live 与焦点管理漏点 | — | 已关闭（本批核实 + 补断言） |
| IK-19 | 速查页难度过滤未做 | web/app.js:3336 | P2 |
| IK-21 | Web 书签只有写入/取消，无浏览页 | web/（B6 收藏动作已落，浏览 UI 未做） | P3 |
| IK-22 | 朗读仅 speechSynthesis，无第三方 TTS 端点（计划 B8 可选项） | web/read-tts.js | P3 |
| IK-23 | 弹层窗口拿不到主窗口 backdrop，玻璃模糊/折射缺席（方案 B 限制，候选解法见正文） | android/ui/**（4 sheet + 7 dialog + 1 drawer） | P2 |

## 明细

### A. 数据一致性（P0）

> IK-01/02/03 已全部随批次 A（52731c9）关闭，明细移入「已关闭」节。

### B. 工程规范债务（P1）

#### ~~IK-05 编译警告 7 条~~（已关闭，2026-11-05 实测确认）

- 处理：AutoMirrored 替换与 LocalLifecycleOwner 迁移在 8b9d040 起的各批次中陆续完成；2026-11-05 `./gradlew :app:compileDebugKotlin --rerun` 强制重编实测 `w:` 零命中，7 条警告全部清零
- 验证：重编任务实际执行（非 UP-TO-DATE 缓存），警告输出为空

#### ~~IK-07 文档滞后~~（已关闭）

- 处理：`docs/decisions.md` D-016 与 `docs/README.md` 文档地图的「待实现」已在 6e5109a 文档回填时修正；`docs/android-particle-module-spec.md:544` 残留的「待实现（UI 与存储层）：」标题系写作时遗留（其下内容全为已勾选实测项），2026-11-05 修正为「已实现」表述，至此三处全部清零

### C. Web 端功能缺口（P1/P2）

#### ~~IK-10 romaji 归一化失败静默回退~~（已关闭，52731c9 实现、2026-11-05 核实）

- 处理：批次 A（52731c9）已按本条建议落地——`normalizeAnswerDetailed` 返回 `{ answer, romajiFailed }`（verb-engine.js），动词（app.js 判题）与形容词/接续两条路径均对 `romajiFailed` 输入明示「你的答案：…（罗马字未能识别，按原样比对）」并 toast「无法识别该罗马字拼写，已按原样比对；可改用假名输入。」
- 验证：`tests/ui.test.js`「罗马字无法识别时明示按原样比对（IK-10）」守护 toast 与反馈面板双提示；2026-11-05 实现与断言均在位，Web 全量 122/122

#### IK-11/IK-13 预留位转正清单（P2）

| 编号 | 位置 | 当前形态 | 转正条件 |
| --- | --- | --- | --- |
| IK-11 查词 | ui/study/StudyScreen.kt:142 | PlannedRow 不可点 | 词典数据源确定后翻 AVAILABLE + 二级页 StudyDestination.DICTIONARY；文章内点词查详情已随 Web B5 / Android 批 1–3 落地（word-detail 调用），本条只管独立查词入口 |
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

#### ~~IK-18 aria-live 与焦点管理漏点~~（已关闭，2026-11-05 核实 + 补断言）

- 处理：登记册所列缺口此前批次已实际闭合——
  - 播报通道：`#view-announcer`（sr-only、role=status、aria-live=polite）+ `announce()`（先清空再写入，规避 live region 同文不重报）
  - 视图切换：home/adjective/mistakes 播报「已进入…」；verb/particle 进入时经子视图播报更具体口径（「动词 · 练习」），**不叠加「已进入动词模块」属防双重播报的设计取舍，非缺口**
  - subtab 切换：动词练习/速查、形容词练习/速查、助词各专题均有播报
  - 清空确认：`window.confirm` 原生对话框（SR 可读）+ 结果经 toast（role=status）播报「错题已清空。」
  - 焦点管理：`h1#intro-title` 设 `tabindex="-1"`，`showView` 后 `focus()` 移入新视图主标题
- 验证：`tests/ui.test.js` 无障碍块原有 6 条播报/焦点断言（含 `document.activeElement` 落点）；本批补「清空错题结果经 toast 播报」断言并变异验证（改坏文案 → 1 红 → 还原 → 全绿）；Web 全量 122/122

#### IK-19 速查页难度过滤

- 位置：`web/app.js:3336` 速查表按「基础活用/进阶活用」分组展示，无按难度筛选
- 影响：查找特定难度形式需滚动
- 建议时机：低优先级

#### IK-21 Web 书签无浏览页（P3）

- 位置：Web 阅读抽屉「收藏这个词／收藏这一段」写入 `japanese-verb-practice-bookmarks-v1`（B6），但 Web 端没有书签浏览/管理页（Android「我的」页已有）
- 影响：Web 收藏后只能取消，不能回看列表；数据不丢（键与 Android 同构）
- 建议时机：随 Web「我的/资料」类页面一起做，或复用 Android 我的页交互

#### IK-22 朗读无第三方 TTS 端点（P3）

- 位置：`web/read-tts.js` 只实现 `speechSynthesis` 通道（零密钥零成本零出网）
- 影响：无 TTS 的浏览器/环境无法朗读；计划 B8 的「可选第三方 TTS 自填端点」未实现
- 建议时机：出现真实需求再做；做的时候沿用 D-045 联网边界（自填地址、明文告知、可关）

## 已关闭

#### IK-10 Web romaji 失败可感知（2026-11-05 关闭，实现见 52731c9）

- 处理与验证见明细节标注；`normalizeAnswerDetailed` 失败信号 + 双判题路径提示 + 专项测试，三项均在位

#### IK-18 Web aria-live 与焦点管理（2026-11-05 关闭）

- 播报/焦点实现此前批次已落地，登记册状态滞后；本批逐项核实并补清空播报断言（变异验证），见明细节

#### IK-01/IK-02/IK-03 跨端数据一致性三项（关闭：批次 A 52731c9）

- 处理：Android 词库对齐 Web——动词词条 44→79、形容词形式 7→10（补 `conditional`/`mizen`/`imperativeRequest` 及配套映射与讲解文案）；跨端样例生成器陷阱随词库补齐后重跑样例消除，`CrossSampleConsistencyTest` 硬编码断言（105/150 问题）改为 meta 驱动派生断言（`samples.meta.verbSampleCount` 等），不再有重跑生成器即崩的陷阱
- 验证：2026-11-05 实测 `VerbData.kt` 79 词条、`AdjectiveData.kt` 三进阶形式在位、断言无硬编码条数；Android 单测 264 项全绿
- 注：本条原明细中的路径为旧包名 `com/nihongodoushi/app/`，D-039 改名后已不存在，现行为 `com/kotonoha/app/`

#### ~~IK-14 书签浏览页~~（2026-10-21 关闭，b75c6c1）

- 处理：写作侧——阅读卡片右下角的星标已删除，两个收藏入口移进词详情弹层底部：「收藏这个词」（`type="word"`，refId 为「段落缓存键|词面」）与「收藏这一段」（`type="sentence"`，refId 为段落缓存键，沿用 `sentence::` 前缀以兼容既有数据），已收藏时文案变「已收藏 · 取消」再点移除。浏览侧——`ui/mine/MineScreen.kt` 段三由「无条件渲染空态卡」改为按 `app.bookmarks.items` 分流：为空才渲染空态卡（文案指向词详情弹层，不再提已删除的星标），非空则 `BookmarkListSection` 按 `createdAt` 倒序渲染真实列表，每条带类型标记（单词/段落）+ title + note + 删除入口（`AppState.removeBookmark`），删除后计数与列表同源刷新
- 依据：`docs/android-read-analyzer-spec.md` §9.4/§9.5、F-10、AC-18
- 验证：新增 `ui/mine/MineBookmarkListTest.kt`（排序 + 类型映射 4 例），Android 单测全绿；真机 emulator-5554 实测「1 条 → 列表显示 → 点✕删除 → 空态卡，落盘 `{"items":[]}`」，空态与列表互斥、计数同步
- 未做（另立）：点书签条目跳回原文（需处理缓存已清、段落定位、阅读会话判断，属跨页面导航改造）

#### IK-09 应用图标未接入（关闭：已自绘并接入）

- 处理：不是接入 `docs/design/icon-concepts/` 里的「あ・纸页」旧方向，而是随「言の葉」改名重画了一枚**「言」字印**自适应图标：`res/drawable/ic_launcher_background.xml`（墨绿印底）+ `ic_launcher_foreground.xml`（纸白「言」+ 右下叶脉缺口，viewport 108x108，全部内容落在系统保证的 72x72 安全区里：印章 44x44 居中、四角外接圆半径 31.1 < 安全圆半径 33）+ `ic_launcher_monochrome.xml`；`res/mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml` 用 `<adaptive-icon>` 三层串起来。Manifest 已是 `android:icon="@mipmap/ic_launcher"` `android:roundIcon="@mipmap/ic_launcher_round"` `android:label="言の葉"`。
- 影响：无（minSdk=26，所有支持设备都走 adaptive-icon 这一套，不再需要各密度 PNG）
- 遗留：`docs/design/icon-concepts/` 目录里的「あ・纸页」资产已不是现行方向，保留作过程记录

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

#### IK-23 弹层（sheet / dialog / drawer）拿不到主窗口的 backdrop（P2，方案 B 已知限制）

- 现象：`ModalBottomSheet` / `AlertDialog` / `ModalDrawerSheet` 各自在独立窗口（Dialog）里绘制，
  Compose 的 `CompositionLocal` 能传进去，但 `Backdrop` 指向的 RenderNode 属于主窗口的渲染树，
  跨窗口采样拿不到内容。当前实现：弹层用**不透明 `surface` 底 + 四角 24 + 无抓手**，
  玻璃的模糊/折射在弹层上缺席，只有主窗口内的卡片、悬浮岛、吸底条是真玻璃。
- 影响面：4 sheet + 7 dialog + 1 drawer。
- 候选解法（按代价排序）：① 弹层改为**主窗口内的自绘浮层**（不用 Dialog 窗口），复用 `Layer.Content` 采样；
  ② 开弹层时先 `capture` 一帧作为静态 backdrop（内容是静止的，模糊等效）；
  ③ 接受现状并写进 UI 规格。② 最省事且视觉接近，但需要主窗口截图能力。
- 状态：**待定**。方案 B 的「不简化」条款下这是一处必须收口的缺口，但优先度低于全量页面走查。

## 复核方法

- 标记扫描：`grep -rn "TODO\|FIXME\|XXX\|HACK" web android --include="*.js" --include="*.kt" --include="*.html" --include="*.css"`
- 跨端比对：动词/形容词词条数与形式数以本文 IK-01/02 引用的文件行为准
- 测试基线：Web 在 `web/` 下 `node --test tests/<六个文件>`；Android `./gradlew :app:testDebugUnitTest`（需先 export JAVA_HOME）
- 编译警告：`./gradlew :app:compileDebugKotlin --rerun-tasks 2>&1 | grep '^w:'`
