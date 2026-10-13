# 日语动词活用练习（nihongo-doushi）项目总结

| 项 | 内容 |
| --- | --- |
| 日期 | 2026-10-13 |
| 代码基线 | commit 64033c6（master，与 origin/master 同步） |
| 规模 | index.html 285 行 / app.js 1206 行 / verb-engine.js 641 行 / styles.css 1785 行 / 测试 752 行 + DOM 桩 455 行 |
| 文档用途 | 项目思路与风格的权威描述；后续新增功能或新模块时**照此文档的模式仿写**，保持一致性 |

**测试基线**：`node --test tests/verb-engine.test.js tests/styles.test.js tests/font.test.js tests/ui.test.js` = **37/37** 通过（10 条引擎 + 4 条样式契约 + 2 条字体契约 + 21 条 UI 冒烟）。任何改动后复跑，基线不许劣化（历史：8/8 → 12/12 → 29/29 → 30/30 → 37/37）。Windows 下必须显式列出文件，不能只传 `tests/` 目录。

---

## 1. 项目目标与核心功能

**一句话**：一个独立的、离线可用的日语动词活用练习小工具——纯静态页面，浏览器直接打开 `index.html` 即用，不依赖任何外部服务、构建工具或第三方题库。

核心功能（README 与代码一致）：

| 功能 | 说明 | 实现位置（app.js 除注明外） |
| --- | --- | --- |
| 随机出题 | 五段/一段/不规则动词 × 语态 × 活用形式 × 难度，随机组合；一轮内不重复相同组合，近 3 题不重复同一动词，题池用尽重新洗牌 | `chooseRandomQuestion` / `buildTargets` |
| 到期题优先插入 | 随机练习时先查「今天到期」的错题（尊重当前筛选条件），有则优先出，没有才走随机题池 | `chooseDueReviewQuestion` → `isReviewDue` |
| 答案判定 | 接受汉字、假名、罗马字三种输入；归一化管线（`engine.normalizeAnswer`）容忍 NFKC 全半角、中英标点、片假名（含半角）与罗马字长音符号；汉字形态与其假名读音都算对 | verb-engine `normalizeAnswer` + `submitAnswer` |
| 规律提示与讲解 | 每题可展开「规律提示」；判定后展示变化规则，规则文本中的假名段/「」术语自动高亮 | verb-engine `explain` + `renderRuleExplanation` |
| 练习统计 | 答对/答错计数、正确率、比例条；答题后统计卡有扫光反馈（见 §5.2）；可单独清零，**不影响错题集** | `renderPracticeStats` / `updatePracticeStats` |
| 错题本与间隔复习 | 答错自动入库（错误答案、次数、时间、复习进度）；答对按 1/3/7/14/30 天拉长复习间隔，连续 6 次正确复习后毕业移出；支持随机重练、单题重练、按到期复习、手动清空（带 confirm） | `recordMistake` / `recordSuccessfulReview` / `chooseDueReviewQuestion` |
| 活用速查 | 按动词/语态查完整活用表（变形结果 + 大意 + 变化提示），表格按 基础活用/进阶活用 分组（组头标注项数），附「变形类型规则」面板，支持汉字/假名/中文搜索，每行可「练这题」 | `renderReferenceTable` / `buildReferenceRow` / `renderReferenceVoiceGuide` |

设计底线（README 明示）：题目与活用规则**全部独立实现**，不使用任何外部网站的源代码、素材或动词数据。

## 2. 页面结构与交互流程

### 2.1 页面结构（index.html）

三视图单页，顶栏 tab 切换，无路由库、无 hash 路由；`icon/note-icon.svg` 作 favicon：

```
app-shell
├── topbar：品牌（「活」字标 + 中英文名）+ 三个 tab（练习 / 活用速查 / 错题[计数角标]）
├── page-intro：标语区（"看见动词，想起变化。"）+ 离线说明徽章
├── view-practice（默认）
│   ├── filter-bar：动词类别 / 变形类型 / 活用形式 / 难度 四个下拉 + 换一题按钮
│   ├── review-banner：错题重练模式的常驻提示条（剩余题数 + 退出重练），非重练模式时 hidden
│   ├── practice-layout（双栏，840px 以下堆叠）
│   │   ├── practice-card：题面卡片（role=button 可折叠/展开：词、读音、类别、释义）
│   │   │   → 题目要求 → 答案表单（输入 + 提交 + 规律提示折叠）
│   │   │   → 反馈面板（对错、正确形态、语态/活用大意、规则、下一题）
│   │   └── practice-sidebar：练习统计卡（计数 + 比例条 + 答题扫光 + 清零）+ 学习方法卡（三分类口诀）
├── view-reference：搜索 + 动词/语态下拉 → 动词概要 → 「变形类型规则」面板（details/summary，可收起）
│   → 五列速查表（按 基础活用/进阶活用 分组，组头跨列并标注项数，每行带「练这题」）
├── view-mistakes：操作区（随机重练 / 清空）→ 错题卡列表（含正确答案、上次作答、错几次、下次复习日期）→ 空态
└── footer + 全局 toast（role=status，aria-live=polite）
```

### 2.2 交互流程（练习主循环 + 间隔复习）

```
选筛选条件（change 即自动换题）
→ 出题：到期错题优先（尊重筛选）→ 否则题池洗牌 + 防重复交换 → 渲染题面（默认收起）
→ 提交：归一化输入 → 与 {汉字形态, 假名形态} 集合比对
→ 反馈面板展开（aria-live 播报）+ 统计卡扫光
   对 → 走间隔复习调度：correctStreak+1；满 6 次毕业移出，否则按 1/3/7/14/30 天推迟 dueAt
   错 → recordMistake 入库/计数，dueAt 重置为明天，correctStreak 归零
→ 统计更新 + toast 提示 → 焦点移到「下一题」
→ 下一题：随机模式回题池；错题重练模式按一轮清单遍历（答完提示「剩余按间隔继续复习」）
```

交互细节约定（新功能须遵循）：

- **判定后锁定输入**（input/submit disabled），防重复提交；「下一题」按钮获得焦点，回车可继续。
- **输入法组字期间不提交**：`compositionstart/end` 维护 `composing` 标志，提交入口同时检查 `event.isComposing`，避免日文输入法下回车确认候选词时把半截答案提交入库。
- **筛选变更立即出新题**，题池按筛选键（JSON.stringify 四条件）缓存，条件变了才重建。
- **具体活用形式优先于难度**：指定了某个活用形式时「难度」下拉置灰（`disabled` + `title` 说明），避免两个维度叠加筛出空题库；条件冲突的兜底仍在（题池为空时 toast 提示）。
- **重练模式必须有常驻可见状态**：进入错题重练后显示 `review-banner`（剩余题数 + 退出重练）；任何退出路径（换一题 / 退出按钮 / 答完一轮）都要显式提示，不允许静默改变模式。状态迁移统一走 `setReviewingMistakes()`，不直接赋值。
- **间隔复习的会话内调度**：`SESSION_RETRY_AFTER_ANSWERS = 4`——刚答错的题不当场重复，而是同会话内再答 4 题后重新插入（`sessionDueAtAnswerCount` 按答题计数调度），跨会话则按 `dueAt` 日期调度；两类到期任一满足即视为「该复习了」。
- **题面默认收起**：verb-prompt 是 `role=button` + `aria-expanded` 的可折叠卡片，点击或键盘操作切换（`toggleVerbPromptDetails`）；出题时重置为收起态，让「先回忆、再看词面细节」成为默认动线。
- **速查可反向进入练习**：速查表每行「练这题」直接构造该动词×语态×活用形式的题目并切到练习页，复用 `buildQuestion()`（与错题重练同一构造入口）。
- **所有破坏性操作有确认**（清空错题用 `window.confirm`）或天然可逆（清零统计不动错题，UI 文案明确区分）。
- **反馈永远三通道**：面板内联 + toast 轻提示 + 统计条/扫光变化；面板用 `aria-live="polite"`。
- **localStorage 全程 try/catch 包裹**，读写失败降级为「仅当前页面保留」并 toast 告知，不抛错不白屏。

## 3. 功能模块划分

```
verb-engine.js   ←—— 纯逻辑层：数据 + 变形规则 + 解释文本 + 答案归一化 + 罗马字转换（零 DOM）
app.js           ←—— UI 编排层：DOM 引用集中管理 + 状态 + 渲染 + 事件（零变形规则）
icon/            ←—— 图标资源：note-icon.svg（favicon）
font/fonts.js    ←—— 资源清单层：字体文件清单，运行时注入 @font-face
font/subset.py   ←—— 构建脚本：字体子集化与字形校验（开发用，运行时不依赖）
styles.css       ←—— 表现层：语义令牌 + 组件样式 + 响应式 + 动效
tests/           ←—— 覆盖 verb-engine（纯函数）、styles 契约（令牌/色值红线）、font 契约、app.js UI 冒烟
tests/helpers/   ←—— 测试辅助：最小 DOM 桩（解析 index.html 建树，零依赖跑 app.js）
tools/           ←—— 开发工具：色值审计与令牌化、Playwright 视觉回归
```

**engine 内部**（verb-engine.js）：

- 数据常量：`VOICES`（7 种语态，含中文大意）、`INFLECTIONS`（18 种活用，含 `level: basic/advanced` 与大意；12 基础 + 6 进阶）、`VERBS`（44 词，`group` 三分类 + `kind` 变形类型五分类：godan/iku/ichidan/suru/kuru）。
- 变形管线：`conjugate(verb, voiceId, inflectionId)` = `inflect(deriveVoice(text, kind, voiceId), formId)`——**先派生语态（返回新形态+新 kind），再在派生结果上做活用**。两段各自查表（A/I/E/O 段映射、GODAN_TE/GODAN_PAST 音便表），特例（行く→行って、す结尾短使役受身、来る汉字/假名双表示）就地处理。
- 双表示：所有结果返回 `{ written（汉字混写）, kana（纯假名） }`，判定与展示都吃两个形态。
- 规则解释：`voiceRule`/`inflectionRule` 生成中文规则文本，`explain` 拼接，`explainVoice`（按动词类别返回单条语态规则，供速查页规则面板使用）；文本约定用「」包术语、用「X段」表述，供 UI 层高亮解析。
- 罗马字：`ROMAJI` 映射表（含 si/shi、tya/cha 等多式拼写）+ `toHiraganaFromRomaji`（长音促音处理）。
- 导出：UMD 双端——浏览器挂 `window.JapaneseVerbEngine`，Node 走 `module.exports`（这是测试能直接 require 的关键）。

**app.js 内部**（单 IIFE，"use strict"）：

1. 字体注入（读 fonts.js 清单生成 @font-face）；
2. `elements` 一次性集中收集全部 DOM 引用（60+ 个，新功能照此登记，不散落 getElementById）；
3. 状态：mistakes / practiceStats（均从 localStorage 防御性加载）+ currentQuestion / questionDeck / reviewingMistakes（只经 `setReviewingMistakes()` 变更，保证横幅同步）+ sessionAnswerCount / reviewedMistakeKeys（会话内复习调度与一轮遍历）；
4. 常量：`REVIEW_INTERVALS_DAYS = [1, 3, 7, 14, 30]`、`MASTERY_CORRECT_STREAK = 6`（毕业线 = 间隔数 + 1）、`SESSION_RETRY_AFTER_ANSWERS = 4`；
5. 存取层：load/save 各一组，键名带版本号 `japanese-verb-practice-{mistakes|stats}-v1`；错题 schema 为增量演进（新增字段带默认值兼容旧存档，见 §4 存储行）；
6. 渲染函数：renderPracticeStats / renderMistakes / renderReferenceTable / renderReferenceVoiceGuide / renderRuleExplanation / renderReviewBanner（纯函数化，replaceChildren 重建，不拼 HTML 字符串——题干 instruction 例外用 innerHTML 但内容全部来自 engine 常量，无用户输入注入面）；
7. 事件绑定集中在文件尾部 + 启动序列（populate 筛选 → 渲染 → 首题）。

## 4. 代码组织方式（新模块仿写清单）

| 约定 | 具体做法 |
| --- | --- |
| 零依赖、零构建 | 不引入框架/打包器；脚本 `<script defer>` 顺序加载：fonts → engine → app。开发工具（tools/、font/subset.py）依赖 Playwright / fontTools，但不进入运行时 |
| 逻辑与 UI 严格分层 | 新增任何"规则/数据/归一化"进 verb-engine.js 并配测试；app.js 只做编排。禁止 UI 层写变形规则，禁止 engine 碰 DOM。答案归一化（含标点、片假名、长音符号处理）即按此归属 engine |
| engine 双端可用 | 新的纯逻辑模块沿用 `(function (root) { ... root.X = api; })(typeof self !== "undefined" ? self : globalThis)` + `module.exports` 判断的模式，保证 `node --test` 可测 |
| ES5 风格函数表达式 | `function foo() {}` + `const/let`，不用 class/箭头函数/可选链——与存量代码保持一致 |
| DOM 引用集中 | 新元素一律登记进 `elements` 对象，命名 camelCase |
| 视图切换 | `showView(viewId)`：`hidden` 属性 + tab `aria-current`；切到某视图时触发该视图的 render（懒渲染） |
| 本地持久化 | 每个独立的存储关注点一个键、一个版本号、一对 load/save、一套防御性校验（结构错→toast + 空值起步，绝不 throw）；**加字段**用「缺失即默认值」的增量迁移（如 dueAt 缺失→视为立即到期），**改语义**才升版本号 |
| 间隔复习调度 | 到期判定统一走 `isReviewDue()`（会话内计数 + 跨会话日期双通道）；改间隔/毕业线只动 `REVIEW_INTERVALS_DAYS` 与 `MASTERY_CORRECT_STREAK` 两个常量 |
| 用户输入 | 一律 `engine.normalizeText`（NFKC + 去空白）后比对；答案判定用 `engine.normalizeAnswer`；展示用户原始输入时区分「归一化后」与「原文」 |
| 错误提示 | toast（3s 自动消失，`is-error` 红色变体）；不使用 alert |
| 样式 | 颜色一律引用 :root 语义令牌；新增令牌按「文字/线条/浅底/状态」分组命名并写注释；改动后跑 `tests/styles.test.js` |
| 视觉回归 | 大范围样式或交互改动后，用 `tools/visual-check.js` + `tools/visual-diff.py` 对比改动前后截图（超容差像素 ≤500 且平均通道差 ≤0.5 视为无可见回归） |
| 测试 | `node --test tests/verb-engine.test.js tests/styles.test.js tests/font.test.js tests/ui.test.js`（Windows 下必须列文件，不能只给目录）；引擎断言 `written` 形态为主，覆盖特例（行く、一段可能形、さ结尾等）；新规则必须先补测试再实现 |
| UI 改动自测 | 任何改动 `app.js` 交互逻辑或 `index.html` 结构后，必须让 `tests/ui.test.js` 覆盖到该路径：DOM 桩会把 `getElementById` 解析不到、状态迁移漏刷节点直接变成失败 |
| 新增界面文案 | 必须重跑 `font/subset.py`（`tests/font.test.js` 会拦截漏字，含 app.js 注释里的汉字）；漏字的表现是回退字体行高不同、整页位移，而不是简单的"字形不好看" |

## 5. 视觉与交互风格特点

### 5.1 设计语言：纸感学习手帐

- **配色**（styles.css :root 语义令牌，全站唯一样式来源，组件内禁止写死色值）：
  - 基础调色板：纸底 `--paper #f6f5f0` + 卡面 `--surface #fffefa` + 墨色 `--ink #26332e`，辅 `--muted` 弱化文字；
  - 语义三色系：绿 `--green #326151`（正确/主按钮/品牌）、橙 `--orange #b8784f`（提示/强调）、红 `--red #a54e45`（错误），每个都配 `*-pale` 浅底变体——对错反馈用「浅底+深字」而非饱和色块；
  - **细分语义令牌**（2026-10-12 归并，替代原 101 处组件内硬编码色值）：按「文字灰阶（弱→强）/ 绿色文字 / 语义色 / 线条 / 浅底 / 渐变与状态点」六组命名，共 59 个；令牌值取该用途的众数，组内色差 ≤5，经 Playwright 逐像素回归验证为不可见；扫光使用独立的 `--stat-*-sweep` 半透明令牌；
  - 页面背景在 paper 上叠一层极淡的径向绿晕（radial-gradient），营造纸张光照感；
  - 契约由 `tests/styles.test.js` 守卫：任何 `var(--x)` 必须有定义、组件内不得出现裸色值（`#fff`/`#000` 例外）。
- **字体**：`LXGW ZhenKai GB`（霞鹜楷体，本地 font/ 目录自带）→ Yu Gothic → Noto Sans JP 兜底；`font-synthesis: none` 禁止伪粗体。字体路径由 fonts.js 清单声明、运行时注入 @font-face，CSS 不写死路径。
  - **运行时加载子集**：`font/subset.py` 按当前词库与界面文本收集字符（当前 673 个，其中 CJK 567），生成 woff2 子集 186KB（原 16.7MB），字形轮廓与步进宽度与原字体逐点一致（脚本内自动校验）；新增动词或文本后重跑脚本即可，字体路径由 fonts.js 清单切换。
- **质感**：大而软的阴影 `--shadow: 0 13px 36px rgba(43,55,47,0.055)`、6-16px 圆角、细线 `--line #e7e9e1` 分隔；无渐变按钮、无发光、无 emoji 图标（图标用文本符号 ↻ → ✓ ! ◎ 和文字 pill，favicon 用自带 SVG）。
- **排版节奏**：小号 `eyebrow` 英文眉题（如 REFERENCE / REVIEW）+ 中文大标题的组合贯穿全站；信息用 pill 徽章（类别、目标形态）压在标题行。

### 5.2 动效与响应式

- 动效克制：出题换卡有轻量入场动画（`is-question-entering`，重排 hack 重触发）；hover 效果包在 `@media (hover:hover) and (pointer:fine)` 里，触屏不残留悬停态；
- **答题扫光**：`.practice-stats::after` 一次 1200ms 扫光，由 `is-answer-correct` / `is-answer-incorrect` 触发、出题时移除——把「答完题看一眼统计」变成可感知的反馈时刻；
- `prefers-reduced-motion: reduce` 全量关闭动画，**唯一例外是答题扫光保留完整时长**（styles.css 内有注释说明）——对错反馈属于信息而非装饰；
- 断点阶梯完整：840px 双栏堆叠 → 580px 紧凑布局；**大屏也有专门适配**（1150/1600/2200/3000px 多级放大字号与留白）——这是该项目的特色，新页面应同样"向下堆叠、向上放大"；
- 可达性基线：全交互元素 focus-visible 外环（3px 半透明绿）、`:active` 缩放反馈、统计条 `role=img` + 动态 aria-label、题面折叠 `role=button` + `aria-expanded`、错题角标/计数 aria-live。

## 6. 可扩展方向建议（按现有思路自然延伸）

1. **形容词/名词活用模块**——い形容词的活用表与动词同构（词尾段变换 + 特例「いい→よく」），可直接复用「数据常量 + 派生管线 + explain」三件套，engine 加 `adjective-engine.js`（同 UMD 模式），UI 复用 practice-card 全套。
2. **错题智能度（剩余部分）**——间隔复习的毕业机制已上线；还可做「按错次加权出题」：题池构建时按 `mistake.count` 重复插入 N 次，让高频错题更早出现，属最小改动。
3. **每日一练 / 连续打卡**——统计键已按版本化设计，加一个 `daily-v1` 键存最近练习日期与连续天数，intro 区展示；不引入新依赖。
4. **题库扩容**——VERBS 是纯数据数组，扩到 100+ 词零成本；注意保持 `kind` 与词尾一致性由测试兜底（每加一批词，补一条全量 conjugate 冒烟断言）。
5. **罗马字输入增强**——已支持 "nn→ん"、双写辅音促音（kk→っか）、长音符号（tōkyō→とうきょう）与片假名/半角片假名归一（见 `engine.normalizeAnswer`）。剩余可补：假名罗马字混合输入（如「書きます」与 "kakimasu" 混写）、"oo/ou" 等少见长音拼法变体，规则落在 engine 并补测试。
6. **导出/导入错题**——错题结构已是纯 JSON（含复习进度字段），加「导出 JSON / 粘贴导入（走同一套防御性校验）」按钮即可，校验逻辑直接复用 loadMistakes 的过滤函数。

**拓展时的红线**：不引入构建工具与框架；不改「逻辑在 engine、编排在 app」的边界；新存储键必须带版本号与防御性校验；新配色必须走 :root 令牌；新交互必须补 aria 与 focus 管理；新增界面文案必须重跑字体子集。

## 7. 变更记录

| 日期 | 变更内容 | 原因 | 影响范围 |
| --- | --- | --- | --- |
| 2026-10-13 | **间隔复习（毕业机制）**：错题新增 correctStreak/intervalDays/dueAt/sessionDueAtAnswerCount 字段；答对按 1/3/7/14/30 天拉长间隔、连续 6 次正确复习后移出；会话内答错的题隔 4 题后重排；到期题在随机练习中优先插入；错题卡显示下次复习日期 | 固定「答对即移出」让易错题没有二次巩固机会，复习节奏全靠用户自觉 | app.js、index.html、styles.css、tests（KK） |
| 2026-10-13 | 题面卡片可折叠（role=button + aria-expanded，出题默认收起）；速查页新增「变形类型规则」面板（`engine.explainVoice` 按类别输出七种语态的辞书形规则）；新增 favicon | 先回忆再看细节的练习动线；速查页需要解释「变形类型」这一层概念；标签页缺图标 | app.js、verb-engine.js、index.html、icon/、tests（KK） |
| 2026-10-13 | 练习统计卡新增答题扫光反馈（`--stat-*-sweep` 令牌；reduced-motion 下保留扫光） | 答题反馈需要可感知的视觉确认，且扫光承载对错信息而非纯装饰 | styles.css、tests/styles.test.js（KK） |
| 2026-10-13 | 合并外部功能后重建字体子集（657→673 字符，181KB→186KB），commit 64033c6 | 新文案 16 字不在子集，`tests/font.test.js` 拦截 | font/ |
| 2026-10-12 | 「难度」筛选接线：`INFLECTIONS.level` 从死字段变成练习筛选维度（全部/基础/进阶）；指定具体活用形式时自动置灰难度下拉 | 引擎里早有 level 字段但 UI 从未使用，练习范围只能靠"全部活用"或逐项指定 | index.html、app.js、styles.css |
| 2026-10-12 | 速查表按 基础活用/进阶活用 分组：每组一行跨列表头（含项数），与练习页「难度」筛选共用同一套 `LEVELS`/`LEVEL_LABELS` | 速查表 18 行平铺无层次；分类体系必须单源，避免两处标注各自漂移 | app.js、styles.css、tests/ui.test.js |
| 2026-10-12 | 速查表每行新增「练这题」：把「查到的形式」一键变成题目并切到练习页（复用 `buildQuestion()`） | 速查是只读的死胡同，看完无法立刻验证；速查→练习本应是同一动作的两半 | index.html、app.js、styles.css |
| 2026-10-12 | 错题重练模式新增常驻横幅（剩余题数 + 退出重练）；状态迁移统一走 `setReviewingMistakes()`；退出路径均给 toast | 「换一题」会静默退出重练模式，用户不知道自己已离开错题集 | index.html、app.js、styles.css |
| 2026-10-12 | 新增 UI 冒烟测试：零依赖 DOM 桩（解析 index.html 建树）+ 15 条交互断言 | 纯静态页面此前只有引擎与样式有测试，DOM 接线断裂（改了 id、漏刷节点）只能靠手点发现 | tests/ui.test.js、tests/helpers/dom-stub.js |
| 2026-10-12 | 新增字体契约测试 `tests/font.test.js`；重新生成字体子集（599→648 字符，161KB→178KB） | 新增「难度」二字不在子集里，回退字体行高不同导致筛选栏高 5px、整页下移；需静态守卫 | font/、tests/font.test.js |
| 2026-10-12 | 视觉回归工具新增 `07-mistake-review` 状态；测试基线 12/12 → 29/29 | 新增组件需要纳入回归覆盖面 | tools/visual-check.js、tests/ |
| 2026-10-12 | 字体子集化：运行时加载 161KB woff2 子集（原 16.7MB），新增 `font/subset.py` 生成与校验脚本 | 首屏等待大字体读取，低端机可感知卡顿 | font/、README |
| 2026-10-12 | 答案归一化管线移入 engine 并增强：去首尾中英标点、片假名/半角片假名转平假名、罗马字长音符号展开；新增 1 条测试 | 句尾「。」、片假名、带标点罗马字、长音符号四类合法输入此前被判错 | verb-engine.js、app.js、tests |
| 2026-10-12 | 提交入口增加输入法组字守卫（`composing` + `event.isComposing`） | 日文输入法回车确认候选时可能提交半截答案 | app.js |
| 2026-10-12 | 硬编码色值令牌化：101 处替换为 59 个语义令牌，移除 1 处 `!important`（改为提升选择器特异性） | 兑现自定红线「组件内禁止写死色值」，为后续换肤/深色模式留接口 | styles.css、tests/styles.test.js |
| 2026-10-12 | 新增样式契约测试 `tests/styles.test.js`（变量必须有定义、禁止裸色值、令牌分组注释）；测试基线 8/8 → 12/12 | 令牌块曾误插到 :root 之外导致 59 个变量失效，需静态护栏 | tests/ |
| 2026-10-12 | 新增视觉回归工具（Playwright 截图 + 逐像素对比 + 差异热图） | 大范围样式改动需要可量化的验收证据，而非目测 | tools/ |
