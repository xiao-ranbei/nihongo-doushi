# 已确认决策记录

本页记录需求讨论中明确接受的方向。状态列区分设计确认与代码实现；影响范围较大的变更应在此记录新决定，并同步更新产品或技术文档。

| ID | 决策 | 状态 |
| --- | --- | --- |
| D-001 | 项目定位为日语学习工具集；后续工具仍服务日语学习，但交互形态可以不同 | 已确认 |
| D-002 | 学习主页是启动默认页，提供整体及分模块今日活动、连续学习与模块状态；详细统计留在模块内 | Web 已实现 |
| D-003 | Web 桌面使用可折叠侧栏，窄屏使用抽屉；Android 的原生全局导航等原型阶段再决定 | Web 已实现；Android 已实现（底栏两标签 + 二级页承载模块） |
| D-004 | 动词、形容词等模块独立管理内容、规则、筛选和详细统计；共享错题框架与全局连续学习记录 | 已确认 |
| D-005 | 设全局错题本并按模块分组；模块内可以提供快捷复习入口 | 已确认 |
| D-006 | 当前动词和形容词能力在 Web 与 Android 对齐；后续工具是否跨端实现逐阶段决定 | 已确认 |
| D-007 | Android 使用 Kotlin 原生界面和引擎，Web 继续使用 JavaScript；共同规则以跨端样例校验 | 已确认；MVP 已实现 |
| D-008 | Android MVP 为主页、动词／形容词练习和共享错题复习；速查、导入／导出后续补充 | 已确认 |
| D-009 | Android MVP 完全离线、本地独立存储，不做账号、云同步、Web 迁移或备份；签名 APK 先小范围手动安装 | 已确认 |
| D-010 | Android MVP 不额外提供通知、语音、桌面组件或后台任务；以手机竖屏为首发设备范围 | 已确认 |
| D-011 | 未来模块可以按需联网，但必须明确权限、隐私、数据流和离线边界 | 已确认原则；具体服务待定 |
| D-012 | 目标仓库为 `web/` 与 `android/` 并列；Android 工程开始时直接新增到 `android/` | 已落地（`web/` 与 `android/` 并列） |
| D-013 | 当前不做插件注册框架，先用统一模块约定显式接入 | 已确认 |
| D-014 | Android 保留品牌纸感配色，同时遵循原生系统交互；Android 导航样式留待原型决定 | ~~已确认原则；部分细节待定~~ → 配色部分被 D-041 修订（2026-10-22 改为糖果卡/墨夜双皮肤跟随系统）；「遵循原生系统交互」仍然有效 |
| D-015 | Android MVP 工程选型：minSdk 26（Android 8.0+）、UI 用 Jetpack Compose（Material 3）、本地存储用 kotlinx-serialization 版本化 JSON 文件（结构对齐 Web localStorage 键）、applicationId ~~`com.nihongodoushi.app`~~ → `com.kotonoha.app`（见 D-039）；工具链 AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.2.21 + Compose BOM 2025.08.01（与本机 SDK platform 35 同代） | 已确认 |
| D-016 | Android 助词模块接入形态：主页第三张入口卡 → 助词二级模块（专题地图首页 → 专题讲解 → 练习屏）；不新增底栏标签 | 已实现（2026-10-17，8b9d040；D-022 补错题排期，四标签改版后入口迁至「学习」页） |
| D-017 | ~~助词错题沿用纯题目 ID 数组、不做排期~~ → 被 D-022 修订 | 已被 D-022 取代 |
| D-018 | 助词练习不做难度分层：48 题同池，取消入门／进阶选择器；题目 `level` 只决定解析深度（答对给入门解析，答错补进阶） | 已实现（2026-10-17） |
| D-019 | 助词内容补字段：`contrastGroup` / `contrastNote`（最小对立对，9 组 18 条）与 `optionNotes`（逐项干扰说明）；先在 `web/particle-engine.js` 补数据与测试，再由生成器产 Kotlin 数据 | 已实现（2026-10-17） |
| D-020 | 助词模块掌握度显示为环形图 + 百分比（`x/48`）；掌握度只统计「未用提示答对过」的题目并去重 | 已实现（2026-10-17） |
| D-021 | 助词专题卡支持长按直接开练，同时保留显式「练」按钮；长按不得作为唯一路径 | 已实现（2026-10-17） |
| D-022 | 助词错题升级为带排期的记录（修订 D-017）：Web 与 Android 同步改用新键 `japanese-particle-practice-mistakes-v2`（对象数组：`questionId / count / wrongAnswer / updatedAt / correctStreak / intervalDays / dueAt / sessionDueAtAnswerCount`），v1 纯 ID 数组仅作迁移来源原样保留；复习答对按 1/3/7/14/30 天推进、连续 6 次毕业；首页与错题本显示「下次复习」，不再使用「每轮首题插错题」补偿 | 已实现（2026-10-17） |
| D-023 | 动词/形容词模块改造为三层（模块首页 + 讲解页 + 练习屏），与助词模块同构：首页按活用形式（动词另含语态段）组织；掌握度 = 形式下「未用提示答对过」的词条覆盖数（分母 = 词条总数）；讲解页不自造通用规则文案，用三个真实例词 + 引擎 explain；练习屏逻辑不动，仅加 scope 参数与提示记账 | 已实现（2026-10-17） |
| D-024 | Android 底栏从两标签扩展为四标签（主页/学习/阅读/我的）：模块入口从主页迁至「学习」；主页改为谚语 + 今日统计 + 知识点建议；「阅读」本期只搭工具框架（规划中位）；Web 维持侧栏 + 抽屉（D-003 不变），产品级 IA 在 information-architecture.md 描述 | 已实现（2026-10-17） |
| D-025 | 主页谚语内置 24 条日本谚语（原文/读音/大意），作为引擎数据管理（`engine/proverb/`），随机抽取不连续重复，纯函数可单测；后续可扩充 | 已实现（2026-10-17） |
| D-026 | 预留功能位呈现为可见的「规划中」信息入口：可见、标注、不可点击、不提供假交互；与 module-guide「不用占位入口模拟已发布能力」的边界是——仅作路线图沟通，落地条件写入规格扩展点 | 已实现（2026-10-17） |
| D-027 | 「我的」新增两个本地存储键：`japanese-verb-practice-profile-v1`（昵称，1–12 字符，空白/超长拒绝保存）与 `japanese-verb-practice-bookmarks-v1`（书签骨架，本期无写入入口）；均带版本号 + 防御性校验 + 坏数据回退 | 已实现（2026-10-17） |
| D-028 | 学习/阅读两枚标签图标用手绘 VectorDrawable（学士帽 / 打开的书）：material-icons-core 无 School/MenuBook 语义图标，icons-extended 在 release 未开 R8（`isMinifyEnabled = false`）时会显著增大 APK；待启用 minify 后评估切回 Material Icons Extended | 已实现（2026-10-17） |
| D-029 | 字体收集器 `collect-chars.js` 扩展为同时收集 `android/app/src/main/java/**/*.kt` 的双引号字符串字面量，修复「Android 独有文案不进子集」的缺口（本轮 24 条谚语日文 + 新中文 UI 词）；Web woff2 会纳入少量 Android 独有字符（约 10–20KB）换取两端不再漏字 | 已实现（2026-10-17） |
| D-030 | 读屏无障碍口径（IK-18）：Compose 的 clickable/selectable/combinedClickable 自带合并子语义（`AbstractClickableNode.getShouldMergeDescendantSemantics()=true`，字节码实测），卡片/选项/底栏本就是单一焦点站，禁止手工堆 `mergeDescendants=true`；uiautomator dump 只能拿未合并语义树，不得作为合并性验证手段。真缺口补语义属性：速查词条行与筛选胶囊补 `selected`（读屏可听出选中态）、长按「直接练习」补 `onLongClickLabel`；仪器化回归测试 `TalkBackMergeTest`（4 用例）守护合并树口径，经变异测试验证非空断言 | 已实现（2026-10-18） |
| D-031 | androidTest 显式声明 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`：AGP 8.13 不显式指定时往 androidTest manifest 注入旧版 `android.test.InstrumentationTestRunner`，不认 JUnit4 注解，仪器化测试「Starting 0 tests」静默假通过 | 已实现（2026-10-18） |
| D-032 | `AppState.kt`（497 行）按领域拆为 5 文件：主文件只留状态声明（setter 收窄 `internal set`）、会话核心与 companion；错题/统计采集/助词/掌握度四个领域做成同包顶层扩展文件（`AppStateMistakes/Stats/Particle/Mastery.kt`），调用语法不变，子包调用点按函数名补 import；跨文件共享的内部实现（`recordMistake`/`bumpStats`/`updateToday`/`todayKey`）标 `internal` | 已实现（2026-10-18） |
| D-033 | Android 阅读模块首个联网能力「AI 文章解析」采用**客户端直连** OpenAI 兼容接口：不新增服务器、不代理转发、不内置任何密钥；用户在自己的设备上填写服务地址／模型名／API Key，存在 filesDir 版本化 JSON（`japanese-read-analyzer-config-v1`），明文保存并在界面如实告知；请求只发用户填写的地址 | 已实现（[android-read-analyzer-spec.md](android-read-analyzer-spec.md)，批 1–3） |
| D-034 | 解析粒度为**点句解析**：文章按本地规则切句（不动网络），用户点哪句才解析哪句；每次请求只发送该句原文（≤500 字符，超长句先硬切——切分与请求上限同为 500，见 spec §6.2）；不做全文批量上传 | 已实现（批 1） |
| D-035 | 解析结果分三次调用取得：`analyze` 返回 `{tokens:[{word,pos,furigana}]}`、`translate` 返回纯文本译文（前两者并发、按句缓存）、`word-detail` 按需返回 6 字段词详情并缓存在句子条目下。**逐词拼接必须与原句逐字符一致**（重建校验为 P0）；不一致时隐藏逐词、只显示译文并标注，宁可少显示也不教错 | 已实现（批 1–3） |
| D-036 | 参考仓库 `cokice/japanese-analyzer` 为 AGPL-3.0-only（历史 MIT 仅覆盖 tag `mit-final`／`fb57ddc`），本项目根目录无 LICENSE：**只借鉴功能面、契约形状与工程思路，不复制其任何源码与提示词文本**；仓库留在临时目录阅读，不 clone 进本仓库 | 已确认（2026-10-19） |
| D-037 | 联网边界（D-011 落地）：Manifest 只新增 `INTERNET`；全局不开 `usesCleartextTraffic`，用 `network_security_config.xml` 仅对 localhost/127.0.0.1/10.0.2.2 放行明文；除用户填写的地址外无其他出网目标、无遥测；离线时文章正文与已缓存解析仍可读，联网失败不影响阅读本身 | 已实现（批 2，`res/xml/network_security_config.xml`） |
| D-038 | 产品名改为「言の葉」（ことのは）：古典语里即「词语／言语」，同时覆盖动词活用训练与 AI 文章阅读两种能力，故取一整名替代原「日语学习」通称。影响面＝Android `android:label`、Web 页面标题与品牌区、README、文档索引；不改仓库名 `nihongo-doushi`、不改命令与路径（`applicationId` 另由 D-039 单独决定） | 已完成（2026-10-21）：Web 标题/品牌区、Android `android:label="言の葉"`、README、文档索引 |

| D-039 | Android 包名从 `com.nihongodoushi.app` 改为 `com.kotonoha.app`（`namespace` 与 `applicationId` 一起换），主题符号 `NihongoDoushiTheme` → `KotonohaTheme`、`Theme.NihongoDoushi` → `Theme.Kotonoha`；**不做旧数据迁移**——用户侧只装过 debug 包，重装即重建 `files/` 目录下的版本化 JSON，迁移代码的复杂度与风险不划算 | 已完成（2026-10-21）：`build.gradle.kts` `:17`/`:22`、`src/{main,test,androidTest}/java/com/kotonoha` 三层目录、全仓 96 个文件文本替换、Manifest 主题引用 |
| D-040 | 阅读器解析单元从「一句」改为「一个段落」（整行空白为段界，段内不再切句）；`SentenceSplitter.split` 保留给文章标题等既有调用方，新增 `splitParagraphs` 与 `ReadParagraph`。理由：用户反馈「一句话分了三部分太乱」「段落太分开」，逐句一卡会把一段话拆成互不相邻的三四张卡片，读起来失去语流；整段解析还能让模型在句间上下文里判词性。代价：单次请求输出变长（500 字符段落要输出上百 token，逼近 `finish_reason=length` 截断），`ChatCompletionClient` 已把 length 映射成「返回被截断」错误，不会误存脏缓存 | 已完成（2026-10-21）；缓存键随之由句变段，旧句子缓存自然 miss 并按 `trimCache` 淘汰 |
| D-041 | Android 主题改为**双皮肤完全跟随系统深浅色**，App 内不提供手动开关：浅色=「糖果卡」（白卡 / 20dp 圆角 / 柔投影 / 悬浮药丸底栏），深色=「墨夜」（墨黑 / 13dp 圆角 / 零阴影 / 细线平铺底栏）。来自 2026-10-22 用户从五版界面风格原型里选定的两版 | 已实现（2026-10-22） |
| D-042 | 圆角与阴影**必须令牌化**，否则只有配色跟随系统、几何仍停在糖果卡一套，深色下会出现「墨夜配色 + 糖果卡大圆角柔影」的混搭。新增 `ModuleGeometry(cardRadius, smallRadius, cardShadow, pillRadius, navFloating)` 与 `@Composable fun geometry()` | 已实现（2026-10-22） |
| D-043 | 字体**不跟着换**：继续用霞鹜臻楷子集。换无衬线就等于退掉 `web/font/subset-chars.txt` + `web/tests/font.test.js` 整条子集管线，而楷体是本项目唯一的品牌记忆点 | 已确认（2026-10-22） |
| D-044 | `ModuleTones` 四个实例是 `object` 里的 `val`（写死浅色值），深色映射改由 `ProvideModuleTone` 与新增的 `themedTone(tone)` 承担。**按 tab／按模块循环渲染处必须走 `themedTone()`**，直接引用 `ModuleTones.X` 会拿到浅色常量（曾导致深色底栏选中胶囊仍是 `#E9F7EF`） | 已实现（2026-10-22，规范见 [android-theme-spec.md](android-theme-spec.md)） |

## 明确延后的决定

- Android 最低支持版本、Kotlin UI 框架、本地存储技术和包名。
- Android 原生主页／侧栏／抽屉／底部导航的最终组合。
- 平板、横屏和折叠屏支持。
- ~~未来联网工具的服务提供方、发送数据、权限和本地／远程处理方式。~~ → 已由 D-033～D-037 部分落地（AI 文章解析）；朗读／TTS、图片识别等服务提供方仍延后。
- 学习主页对非答题型工具的“今日活动量”计算规则。
