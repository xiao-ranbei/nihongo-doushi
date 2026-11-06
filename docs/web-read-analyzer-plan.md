# Web 端文章分析 · 分批实施计划

> 日期：2026-11-06
> 状态：**计划，不是已实现规格**。本文描述准备建设什么，当前代码状态以 `docs/current-state.md` 为准。
> 目标：对齐 `E:/01_Projects/japanese-analyzer` 的功能面全能力，适配本项目 Web 纯静态架构，分批做到可上线。
> 基准：`web/` 全量测试 **122/122**（2026-11-06 实测，HEAD `9957147`，工作区干净）；Android 单测 264 项 0 失败。
> 契约基准：本项目 `android/.../engine/read/ReadModels.kt`，不是参考项目。
> 许可边界：D-036。只借鉴参考项目的功能面、契约形状、工程思路；不复制源码、提示词文本、常量表与文案。所有提示词按日语学校文法的客观规则重新表述。
> 关联：`docs/android-read-analyzer-spec.md`、`docs/decisions.md`（D-003 / D-006 / D-011 / D-033～D-037 / D-036）、`docs/module-guide.md`、`docs/deployment-github-pages.md`。

---

## 1. 已确认的决策（2026-11-06 关子答复）

| 编号 | 答复 | 落到本计划的结论 |
|---|---|---|
| Q1 | 2 · 对齐参考项目全能力 | §2 逐项适配，能力面不裁剪功能，只改实现形态 |
| Q2 | 2 · 加句型与句子结构 | 新增**第 4 次调用 `grammar`**，结构化输出；两个参考源都没有，属全新能力（§4.4、B5） |
| Q3 | 1 · 纯静态直连 | 沿用 D-033，**不重开 D-033**、不引入服务端、不引代理、不内置 Key；保住 GitHub Pages 部署 |
| Q4 | 需要 | 默认关思考、不做词详情预取、不自动全文、单次解析费用确认、每篇/每日预算上限（§5） |
| Q8 | 需要 · 利用现有错题练习完善 | 走**现有引擎题目 key 映射 + 现有「练这题」通道**，不把解析结果塞进错题本（§6、B6） |

### Q8 的冲突已消解（重要）

上一轮我判断「深度融入需修订 `module-guide.md:9` 错题口径」，按"利用现有功能完善"这条路径**不需要修订**：

- 进错题本的不是「文章解析结果」，而是**现有引擎能生成、可判定、有稳定 key 的题目**：动词 `verbId + voiceId + inflectionId`、形容词 `adjectiveId + formId`、助词 `questionId + lessonId`。
- 文章只作为题目的**来源与语境**，题目本身完全落在既有 `verb-engine.js` / `adjective-engine.js` / `particle-engine.js` 的规则与错题结构里。
- 因此 `module-guide.md:9`「只收稳定、可复现且可判定的题目」与 `spec.md:311`「解析结果不参与统计与错题」两条口径**都保持原样**；新增的只是「从文章词形反查引擎 id」这一层纯逻辑映射。
- 需要新决策的只剩：什么算一次有效活动、主页摘要口径（D-045 预留）。

---

## 2. 范围锁定：参考项目全能力逐项适配

「对齐全能力」= 功能面一项不缺；「适合 Web」= 实现形态按纯静态重做。

| # | 参考项目能力 | Web 适配 | 批次 | 判断依据 |
|---|---|---|---|---|
| 1 | 分词 + 假名 + 词性 + 中文释义 | 保留，契约用本项目 6 字段 / 13 个 pos 标签 | B1/B4 | Android 已验证；不复制参考的 8 字段名（曾致三字段静默为空） |
| 2 | 整句翻译 | 保留，与 analyze 并发、失败降级为空 | B4 | 沿用 Android 三次调用口径 |
| 3 | 单词详解 | 保留，6 字段 + 上下文裁剪 + LRU 80 | B5 | 省 token 手段借鉴工程思路 |
| 4 | 罗马音（本地生成） | 保留，`read-engine.js` 纯函数 | B1 | Android 未做；模型不输出罗马音 |
| 5 | 假名/罗马音开关、词性图例、逐词点击 | 保留，呈现形式见 U2 | B2/B4 | 与现有纸感令牌融合 |
| 6 | 长文分块并发 + 顺序合并 | 保留，参数自行推导（不照搬 280/420/180、并发 3） | B7 | D-036：常量表不可搬 |
| 7 | 流式增量解析（SSE） | 保留，Web `fetch` + `ReadableStream` 原生可做 | B7 | Android 因 `HttpURLConnection` 未做，Web 反而更简单 |
| 8 | 图片 OCR | 改造：canvas 压缩 → dataURL → 用户自填端点的视觉模型；OCR 后须用户确认 | B8 | 无服务端可做；需视觉模型支持 |
| 9 | 朗读 TTS | 改造：**优先浏览器 `speechSynthesis`**（零密钥零成本）；Edge/Gemini TTS 降为可选、需自填端点 | B8 | 参考的 Edge TTS 直连第三方不带密钥，在 Web 上不如原生 API 稳 |
| 10 | AI 日语助手 | 保留但默认关，当前段落注入首轮上下文，消息仅内存 | B8 | 费用最高，放最后一批 |
| 11 | 双模型服务商 | 改造：不绑定厂商，用户自填 `baseUrl / model / apiKey`，可提供纯文本预设 | B3 | 沿用 D-033「不内置任何 Key、不预填厂商名」 |
| 12 | 浏览器本地设置（各自 API Key） | 保留；密钥 `localStorage` 明文 + 掩码回显 + 常驻隐私告知 | B3 | Web 无 filesDir 等价私有目录，暴露面更大，必须如实告知 |
| 13 | 思考摘要 / 深度思考 | **默认关**（Q4），不实现思考摘要 | — | 参考项目自己已强制关思考（成本与延迟） |
| 14 | 深浅色主题 | 保留，跟随系统，不提供手动开关 | B2/B8 | 对齐 Android D-041 |
| 15 | 全链路可中断 + 双超时 | 保留，`AbortController` + 连接/空闲超时 | B3 | 借鉴工程思路 |
| 16 | 埋点（Umami） | **改造为纯本地统计**：只记次数/耗时/错误分类，不出网、不含原文与密钥 | B8 | D-037 无遥测；离线优先 |
| 17 | 访问密码 `CODE` | **不做** | — | 纯静态无服务端，密码形同虚设 |
| 18 | Docker / Vercel 部署 | **不做**，保持 GitHub Pages 静态发布 | — | 部署方式不变，Q3=1 的直接结论 |
| 19 | — | **新增**：结构化句型 / 句子结构 / 敬语文体（Q2=2） | B5 | 两个参考源都没有，需自写提示词与契约 |
| 20 | — | **新增**：与现有错题/练习/统计联动（Q8） | B6 | 利用现有功能完善 |

---

## 3. 架构与文件落点

### 3.1 形态

保持 `web/` 纯静态、零构建、零依赖、`<script defer>` 顺序加载、`file://` 可直接打开、GitHub Pages 可发布。**不引入 ESM、class、箭头函数、构建工具、框架**（`app.js` 现为 IIFE + `"use strict"` + `const/let` + 函数表达式）。

```text
web/index.html          + view-read 三态结构、侧栏项、主页卡、aria-live 节点
web/styles.css          + :root pos-* 令牌、ruby 注音、逐词块、抽屉、五态样式
web/read-engine.js      新增 · UMD 纯逻辑（禁 DOM / 禁 IO / 禁 fetch）
web/read-prompts.js     新增 · 四条自写提示词（D-036，不进字体收集白名单）
web/read-client.js      新增 · 网络层，http transport 可注入（便于 Fake 测试）
web/read-view.js        新增 · 阅读模块自己的状态与渲染，app.js 只做注册与转发
web/app.js              改 · elements 登记、views 白名单、VIEW_ANNOUNCE_LABELS、showView 分支
web/font/collect-chars.js 改 · 见 §7 隐性约束
web/tests/              新增 read-engine.test.js / read-client.test.js / read-view.test.js
web/tests/helpers/dom-stub.js 改 · 沙箱补 fetch / AbortController / ReadableStream
web/tools/visual-check.js 改 · 补阅读页状态截图
```

`read-view.js` 独立成文件而不是继续堆进 `app.js`：现 `app.js` 已 3719 行，再塞四个子视图会破坏可读性；红线「编排在 app.js」仍成立——视图注册、切换、全局状态由 `app.js` 持有，`read-view.js` 只处理阅读模块内部渲染，通过 `window.ReadView` 挂接。这是内部实现判断，不涉及用户可感知差异。

### 3.2 脚本顺序

`fonts.js → verb-engine.js → adjective-engine.js → particle-engine.js → read-engine.js → read-prompts.js → read-client.js → read-view.js → app.js`。四个引擎都必须在 `app.js` 前，UMD 同既有三个引擎口径。

### 3.3 与既有决策的关系

| 决策 | 影响 |
|---|---|
| D-003 | 不变：桌面侧栏 + 窄屏抽屉，新增一项导航 |
| D-006 | 逐阶段决定：本次把阅读能力补到 Web，Android 保持现状不回改 |
| D-011 / D-037 | 首次落到 **Web**：权限、隐私、数据流、离线边界四条在 B3 显式声明；无遥测 |
| D-033 | **沿用，不重开**。`spec.md:98` 说「改服务端代理需重开 D-033」，Q3=1 正是避开那条 |
| D-036 | 全程适用：四条提示词自写；参考项目常量只作设计参考 |
| D-043 | 字体不换；B2/B9 处理正文区缺字问题 |

---

## 4. 数据契约

### 4.1 四次调用（在 Android 三次调用基础上加 Q2=2 的第四次）

| # | 调用 | 触发 | 返回 | 缓存 |
|---|---|---|---|---|
| 1 | `analyze` | 点段必发 | `{tokens:[{word,pos,furigana}]}` | 随段落 |
| 2 | `translate` | 与 1 并发 | 纯文本 | 随段落 |
| 3 | `word-detail` | 点词按需 | 6 字符串字段 | 段落条目 `details` 子表 |
| 4 | `grammar` | **点「看语法」按需，默认不发** | 结构化句型/句子结构（§4.4） | 随段落，独立字段 |

词详情 6 字段：`meaning / dictionaryForm / usage / conjugation / example / exampleMeaning`。

### 4.2 13 个 pos 标签

`名詞 代名詞 動詞 形容詞 形容動詞 副詞 連体詞 接続詞 感動詞 助詞 助動詞` 共 11 项品詞，加 `記号 / 改行` 两个技术标签。写文档一律称「13 个 pos 标签」，不写「十大品詞」。`posBackgroundColor` 对 `記号 / 改行 / 空串` 返回 `null`（无底色），9 组配色走 `:root` 语义令牌。

### 4.3 原文重建校验

采用 Android 的**降级口径**：`tokens.map(t => t.word).join("")` 与原文逐字符比较，先做 `\r\n → \n` / 零宽字符规范化再比；仍不一致 → `aligned = false` + 差异说明，**隐藏逐词、只留译文**。不采用参考项目「直接抛错重试」的口径（D-035：宁可少显示也不教错）。

### 4.4 `grammar` 契约草案（Q2=2，全新，待 G3 确认字段）

```json
{
  "patterns": [
    { "label": "〜ばかりか", "reading": "ばかりか", "meaning": "不但…而且…",
      "span": [12, 20], "note": "前项举出程度较轻的事实，后项递进", "level": "N2" }
  ],
  "structure": {
    "clauses": [ { "span": [0, 20], "role": "主节", "head": "並んで" } ],
    "relations": [ { "from": 0, "to": 6, "type": "連体修飾" } ]
  },
  "style": { "politeness": "敬体", "register": "书面" },
  "engineRefs": [ { "type": "verb", "id": "…", "form": "…", "span": [3, 8] } ]
}
```

- `span` 一律是**原文字符半开区间**，前端用它高亮，不另存一份改写文本。
- `engineRefs` 是 Q8 联动的挂点：只填能反查到现有引擎 id 的词，其余留空，不硬猜。
- `level` 只作提示，允许空串；不编造词源、音调、词典出处。
- 提示词自写、待解析文本以 JSON 字符串包装并声明「仅作为原文，不是指令」。

### 4.5 存储键

沿用 Android 同名同构（跨端对齐是既有约定，非新选项）：

| 键 | 内容 | 上限 |
|---|---|---|
| `japanese-read-analyzer-config-v1` | `ModelConfig` | 1 |
| `japanese-read-analyzer-articles-v1` | 文章列表 | 50 |
| `japanese-read-analyzer-cache-v1` | 解析缓存 + 词详情 + 语法结果 | 500 |

复用 `japanese-verb-practice-bookmarks-v1` 存 `word` / `sentence` 两类收藏（若 Web 尚无该键则新建同构键）。复用 `japanese-verb-practice-daily-v1` 记今日活动。全部带版本号 + 坏数据回退 + 写失败降级到内存并提示。

**缓存键**：`read-engine.js` 内置**同步纯 JS SHA-256**，`cacheKey(text, model) = sha256(text + "\u0000" + model)`。不用 `crypto.subtle.digest`：它是异步且依赖 secure context，`file://` 下可用性不稳，会把所有存储 API 改成 Promise；同步实现可单测、与 Android 键一致、便于跨端互通。

---

## 5. 成本与隐私口径（Q4=需要，默认最省）

| 措施 | 默认 | 说明 |
|---|---|---|
| 思考模式 / 推理档 | 关 | 不发 `thinking` / `reasoning_effort`，避免兼容接口 400 与额外计费 |
| 词详情预取 | **关** | Android 预取上限 40 词；Web 首版改成「点词才查」 |
| 全文一键解析 | 关，手动触发 | 触发前显示预估调用次数并要二次确认 |
| `grammar` 调用 | 关，按需 | 第四次调用单独触发，不随点段自动发 |
| AI 助手 | 关 | 最后一批，用户主动开启 |
| 单次解析确认 | 开 | 显示「本次约 2 次请求」，可设置「本次会话不再提示」 |
| 每篇 / 每日预算上限 | 开 | 达上限后只出缓存、不发新请求，明确提示 |
| 缓存 | 开 | 命中零请求；换模型不复用；失败/截断/未对齐不写缓存 |
| 密钥 | 用户自填，明文存 `localStorage` | 设置页与输入页常驻告知；回显掩码末 4 位；不进日志、不进埋点 |
| 出网目标 | 只有用户填的地址 | 无遥测、无第三方 TTS 默认调用 |
| 离线 | 正文与已缓存结果始终可读 | 未缓存段提示「需要联网解析」，不影响其他模块 |
| 真实调用 | **需 G2 单独授权** | 未授权时全部验证走 Fake transport，零费用 |

---

## 6. Q8 联动：复用现有练习体系（B6 详设）

```text
文章中的词 / 语法结果 engineRefs
      ↓  read-engine.js 纯逻辑反查（可单测）
  ┌───┴────┬─────────────┐
动词 id   形容词 id      助词 lessonId
  │         │              │
  └── 现有「练这题」通道 ──┘
      ↓
现有题目 key → 现有判题 → 现有错题本 → 现有间隔复习 → 现有今日统计/连续学习
```

- 反查表来自三个既有引擎的数据（`VERBS / VOICES / INFLECTIONS`、`ADJECTIVES / FORMS`、`particle` 的 `questionId / lessonId`），不放进 AI 输出；AI 只提供原文片段与 pos，映射由本地规则完成，出错也不会污染错题。
- 需要新增的只有：① 反查纯函数 + 单测；② 「由文章进入的一题」计入今日活动的口径；③ 主页一条最小摘要（今日阅读段数 / 新增生词数）。
- **不新建学习记录体系、不改错题结构、不改复习排期**。

---

## 7. 隐性约束（逐条落，否则测试或视觉回归）

| 位置 | 约束 | 批次 |
|---|---|---|
| `web/font/collect-chars.js:68` | 白名单写死 5 个文件；`read-engine.js` / `read-client.js` / `read-view.js` 默认**不进**收集器。因此中文 UI 文案一律写在 `index.html` / `app.js` / `styles.css`（已在白名单），新增 JS 只放纯逻辑、错误枚举与提示词，不放界面文案。提示词文件绝不入白名单 | B1/B2 |
| `web/font/subset.py` | 新增界面文案后重跑，同时产出 Web woff2 与 Android TTF；`font.test.js` 会拦 | B2/B9 |
| 正文区字体 | 用户文章字符集不可预知，固定 `line-height` 三档（正文/译文/逐词），不把正文塞进子集 | B2 |
| `web/app.js:277` `elements.views` | 写死 5 个视图 id，新增 `read` 必须补 | B2 |
| `web/app.js:563` `VIEW_ANNOUNCE_LABELS` | 补 `read`，否则切换视图读屏不播报 | B2 |
| `web/app.js:2323` `showView` | 白名单校验，未知 viewId 抛 `RangeError` | B2 |
| `web/tests/ui.test.js:120-126` | 5 视图 hidden 断言，补第 6 个 | B2 |
| `web/tests/helpers/dom-stub.js` | vm 沙箱只有 `window/document/console`，补 `fetch` / `AbortController` / `ReadableStream` / `TextDecoder` | B3 |
| `web/tests/styles.test.js` | 所有 `var(--x)` 必须在 `:root` 定义，组件内禁硬编码色值 | B2 |
| 代码风格 | ES5 函数表达式 + `const/let`，UMD，禁 class / 箭头 / 可选链 / ESM | 全程 |
| 基线 | Web 122/122 只升不降；Android 264 不动（重跑字体子集会改 TTF，需复核字体契约） | 全程 |

---

## 8. 分批计划

每批独立可编译、可测试、可回滚；每批结束跑一次全量测试。除标注外**不发起真实 AI 调用**。

### B0 · 闸门与基线（2026-11-06 已完成）

- 产出：本计划文档、`docs/README.md` 索引登记；实测 Web 122/122、工作区干净、HEAD `9957147`。
- 已答 Q1/Q2/Q3/Q4/Q8；Q3=1 使 `spec.md:98` 的「重开 D-033」路径被绕开。
- 真实调用：无。

### B1 · 纯逻辑引擎（零网络、零 UI、零闸门依赖）

**新增** `web/read-engine.js`、`web/read-prompts.js`、`web/tests/read-engine.test.js`。

函数清单：`splitParagraphs` / `split` / `chunkParagraphs` / `mergeText` / `normalizeEndpoint` / `cacheKey`（同步 SHA-256）/ `validateConfig` / `trimCache` / `trimArticles` / `parseAnalysis`（围栏、花括号、字段降级、空 tokens、逐字符重建）/ `parseWordDetail`（严格 + 半截 JSON 兜底）/ `parseGrammar` / `groupTokens`（禁则）/ `toRomaji`（拗音、促音、拨音、长音、片假名、助词 は/へ/を）/ `normalizePos`（13 标签 → 9 组）/ `wrapUserText`（提示注入防护）/ `resolveEngineRef`（Q8 反查）。

测试约 40 项，场景直接移植 Android 五个测试类：引号内句末标点不断句、括号配对、空行分段、连续句末标点、500 字硬切与 `hardCut`、批拼接与实际发送同一拼法、URL 规范化 4 例、缓存键含模型名/稳定/与 Android 一致、500 条与 50 篇裁剪、配置校验、解析围栏/前后解说/缺字段/空 tokens/非 JSON/差 1 个助词/`\r\n`/零宽字符、词详情 6 字段与键名事故回归、语法契约、禁则分组、罗马音全部特例、13 个 pos 标签归一化、提示词四项关键约束与注入包装。

验收：`node --test` 七个文件 ≥122 项 0 失败；变异验证（改坏重建校验与 URL 规范化，确认测试失败）；`git diff` 不含 `document` / `fetch`。
依赖：无。可逆：删三个文件。真实调用：无。

**实施记录（2026-11-06，已完成）**：新增 `web/read-engine.js`、`web/read-prompts.js`、`web/tests/read-engine.test.js`（90 项）。全量七个文件 **212/212**（原基线 122 + 新增 90），0 失败。变异验证实测：改坏端点规范化抓到 1 项失败，改坏重建校验抓到 2 项失败，恢复后复跑 212/212。
实现期发现并修正两处契约问题：① `WORD_DETAIL_SYSTEM` 曾把字段名误写成参考仓库的 `exampleTranslation`，测试「旧键名不会伪装成有效详情」抓到，已改为 `exampleMeaning`（正是 Android `Analyzer.kt` 记录过的键名事故）；② 超长硬切点原会切出 501 字符，已改为 ≤500。
另按 §7 约束做了两处口径微调：`validateConfig` 只返回错误码枚举（中文文案留给 `app.js`），`parseAnalysis` 返回结构化差异数据而非中文说明；`grammar.engineRefs` 由模型给 `type/surface/span`，词库 `id` 留到 B6 本地反查补齐（模型不可能知道本项目词库 id）。`web/README.md` 已登记新测试文件与全量命令。

### B2 · 外壳与静态 UI（无网络）

- `index.html`：`#view-read` 三个子视图（输入 / 阅读器 / 设置）、侧栏 `data-view="read"`、主页卡、`aria-live`。
- `app.js`：`elements` 登记、`views` 白名单、`VIEW_ANNOUNCE_LABELS`、`showView` 分支。
- `styles.css`：`:root` 加 `pos-*` 9 组令牌、ruby 注音、逐词块、抽屉；加载 / 空 / 错误 / 离线 / 未配置五态；正文区固定行高；深浅色跟随系统。
- `ui.test.js` 补新视图与导航断言；`styles.test.js` 补令牌断言；`visual-check.js` 补阅读页状态；重跑 `font/subset.py` + `font.test.js`。
- 验收：122+N 全绿、`visual-diff.py` 无回归、字体子集两端一致。
- **UX 闸门 U1/U2/U3 需关子选**（§9），不影响 B1。
- 真实调用：无。

**实施记录（2026-11-06，已完成，闸门按 U1/U2/U3 推荐形态实现）**：

- 新增 `web/read-view.js`（视图渲染；界面文案由 `app.js` 的 `labels` 注入，因此不必改 `collect-chars.js` 白名单）。改动 `index.html`（defer 顺序、侧栏「阅读」分组、主页第四张卡、`#view-read` 输入/阅读器两态结构）、`app.js`（`elements.read` 集中登记、`views` 白名单、`INTRO_COPY.read`、`VIEW_ANNOUNCE_LABELS.read`、`showView` 分支、初始化接线）、`styles.css`（9 组 `--pos-*` 令牌、ruby 逐词块、五态样式、正文固定 30px 行高）、`tests/helpers/dom-stub.js`（注入 `ReadEngine` / `ReadPrompts` / `ReadView` 三个全局，并删掉既有的重复 `JapaneseParticleEngine` 行）、`tools/visual-check.js`（补 19-read-input / 20-read-reader 两个状态）。
- 阅读页用**新类名**（`read-subnav` 体系），不复用 `.module-subnav` / `.module-subtab`，因此不破坏 `ui.test.js` 里「三个模块共用页签」的既有断言。
- B2 的真实能力是「贴文 → 本地切段 → 读原文」，接 B1 的 `splitParagraphs`，**零网络请求**；逐词与译文容器已就位、默认隐藏，由 B4 填充。
- 新增 10 项 UI / 样式断言，全量 **222/222**（B1 的 212 + 10），0 失败。
- 字体子集重跑：1301 字符（+2），Web woff2 383KB / Android TTF 695KB，24 个抽样字形轮廓与步进宽度一致，`font.test.js` 绿。Python 依赖按本机既有踩坑路径从清华镜像直接抓 wheel 本地安装（fonttools 4.55.0 + brotli 1.1.0）。
- 真实浏览器冒烟（Playwright，`file://` 打开）：三个新全局均挂载、`#view-read` 可见、输入达标后按钮启用、切出 2 张段落卡、行高 30px、`--pos-v` 解析为 `rgb(231, 240, 238)`、**零 console / pageerror**；输入态与阅读态截图目视无重叠、无溢出、无缺字方框。
- **两处范围调整**：① Web 现有 `:root` 只有 `color-scheme: light` 与一整套浅色令牌，**没有** `prefers-color-scheme` 基础；整体加深色会动到既有 18 个视觉状态，故「深浅色跟随系统」从 B2 挪到 B8 与参考项目主题能力一并评估。② 设置页原列在 B2，改随 B3 网络层一起交付：单独摆一个不能保存、不能测试连接的表单属于「不可操作的占位入口」，违反 `module-guide.md:39`。
- `visual-diff.py` 未跑：本批没有改动前的像素基线，本批输出只作为后续基线，不宣称无回归。

### B3 · 网络层与设置页（可注入，零真实调用）

- `read-client.js`：`createReadClient(http)` 注入 transport；`analyze/translate/wordDetail/grammar/ocr/chat`；`fetch` + `AbortController`；SSE `ReadableStream` 增量；11 类错误映射；`finish_reason` 与截断；`response_format` 400 去字段重试一次；连接/读取/流空闲超时；取消三态 `user / superseded / unmount`。
- `tests/read-client.test.js`：Fake transport 驱动，零真实调用；`dom-stub.js` 补沙箱能力。
- 设置页：地址 / 模型 / Key / 温度、实时显示拼接后 URL、掩码末 4 位、测试连接（不写缓存）、隐私告知常驻。
- 声明 D-011 四条：权限（无系统权限，仅浏览器出网）、数据流（只发当前段落原文到用户填的地址）、隐私、离线边界。
- 验收：Fake 全绿、日志无密钥与正文。**G2 授权后**才做一次真实端点 CORS / 鉴权冒烟。
- 依赖：B1。

**实施记录（2026-11-06，已完成）**：

- 新增 `web/read-client.js`：`buildRequest`（四次调用、注入防护、密钥只在 headers、不发思考档位）、`mapHttpError`（11 类）、`handleResponse`（finish_reason / 空内容 / 结构异常 / 解析失败）、`response_format` 400 自动去字段重试一次且只重试一次、取消三态 `user / superseded / unmount`、`parseSSEChunk`（B7 流式预留）、`createFetchTransport(win)`（浏览器默认 transport，`win.fetch` 可替换）。全部通过 `createReadClient(http, deps)` 注入，零全局 `fetch` 依赖。
- 新增 `web/tests/read-client.test.js`（34 项，全 Fake transport）。新增 9 项设置页 UI 断言。全量 **265/265**（B2 的 222 + 34 + 9）。
- 设置页落地：`index.html` 第三子视图 `#read-view-settings`（地址 / 模型名 / 密钥 / 温度、实时显示规范化后的实际请求 URL、掩码末 4 位、保存、测试连接、常驻隐私硬文案）；`app.js` 加 `japanese-read-analyzer-config-v1` 读写（坏数据回退、写失败降级）、11 类中文错误文案、4 类配置错误文案；`styles.css` 加表单样式；`dom-stub.js` 加 `ReadClient` 全局、可覆盖的假 `fetch` 与 `AbortController`。
- **G2 未授权，本批零真实调用**：浏览器冒烟刻意不点「测试连接」，用 `page.on("request")` 实测 **outboundRequests = []**。测试连接按钮交由用户自己填写 Key 后主动点击，不属于「我发起的调用」；UI 冒烟里走的是桩的假 fetch（含 401 错误态）。
- 真实浏览器冒烟（Playwright，`file://`）：三个全局挂载、设置页显示且输入页隐藏、URL 预览 `https://api.example.com/v1/chat/completions`、保存写入 localStorage、密钥只回显 `••••••1234` 且输入框明文为空、零 console / pageerror；设置页截图目视无重叠、无溢出、无缺字方框。
- 字体子集重跑（1301 字符，woff2 383KB / Android TTF 695KB），`font.test.js` 绿。
- **一处口径澄清**：`spec.md:2.3` 的隐私硬文案在 Web 上改为「密钥以明文保存在当前浏览器」，因为 Web 没有 Android 的 filesDir 私有目录；不假装加密。D-011 四条中的「权限」在 Web 上无系统权限可申请，改为「仅浏览器出网、目标只有用户填写的地址」，已在设置页与输入页常驻说明。
- `TextDecoder` / `ReadableStream` 未补进 DOM 桩：B3 的 `parseSSEChunk` 是纯字符串函数，不需要浏览器流 API；留到 B7 接 SSE 时再补。

### B4 · 核心解析闭环

点段 → analyze + translate 并发 → 重建校验 → 逐词渲染（假名 / 罗马音开关、9 组底色与图例）→ 译文 → 按段缓存 → 错误 / 重试 / 离线降级。落 §5 全部成本控制。

验收：缓存命中零请求、换模型重新请求、未对齐隐藏逐词、失败与截断不写缓存、取消不回调、离线正文可读。真实调用：仅 G2 授权后的冒烟。依赖：B1/B2/B3。

**实施记录（2026-11-06，已完成）**：

- `read-client.js` 新增 `analyzeParagraph`：analyze + translate 并发、译文失败降级为空串、只有 analyze 成功才写缓存、同一段重复点击复用在飞 Promise、缓存/预算/时间全部注入（可 Fake 单测）。
- `read-view.js` 扩展：逐词块（ruby 注音 + 罗马音 + 9 组 `data-pos-group` 底色）、词性图例、三段显示开关、五态（idle / loading / parsed / error / offline / budget）、原文对齐后隐藏且未对齐时保底显示、段内 `改行` token 独立断行组。
- `index.html` 加显示设置栏与图例容器；`styles.css` 加对应样式与状态描边；`app.js` 加按段缓存 `japanese-read-analyzer-cache-v1`（坏条目过滤、500 条上限）、每日 100 / 每篇 20 次请求预算、点段编排接线、显示开关联动。
- 新增 7 项编排单测 + 12 项 UI 断言，全量 **284/284**（B3 的 265 + 19），0 失败。字体子集重跑 1303 字符（woff2 383KB / Android TTF 696KB）。
- 真实浏览器冒烟（Playwright + `addInitScript` 注入全假 fetch）：2 张段落卡、第一段 21 个逐词 / 7 个 ruby / 2 项图例、译文同卡、2 次假请求、缓存 1 条、**第二次点同一段 fetch 调用数仍为 2**、关闭注音后 ruby 归零、开启罗马音生成 19 条、`outboundRequests = []`、零 console / pageerror。截图目视无重叠、无溢出、无缺字方框。
- 目视发现并修一处：段内 `改行` token 原本挤在同一行，现给独立组加 `is-paragraph-break`（`flex-basis:100%`），浏览器实测 `flexBasis: 100%`，并补了 UI 断言守护。
- **三处口径调整**：① 计划里「单次解析确认」改为「状态行常驻显示『本次约 2 次请求』+ 每篇/每日预算硬上限」，不做每次弹窗——点段是高频动作，逐步确认会打断阅读流，预算上限已能兜住费用（Q4 目标不变）。② 译文默认**开**（对齐参考项目全能力），Android 是默认关。③ 设置页「返回」现在回到进入前的视图（阅读器或输入页），不再一律回输入页。
- `visual-diff.py` 仍未跑：无改动前像素基线，本批截图只作后续基线。

### B5 · 词详情与结构化语法分析（Q2=2）

- 第三次调用 6 字段、上下文裁剪（>1000 字收敛段落，再超取前后各 2 句）、LRU 80 只缓存已完成、`force` 重生成。
- 第四次调用 `grammar`：§4.4 契约、自写提示词（D-036）、按需触发、按段缓存、span 高亮、`engineRefs` 预埋。
- `read-prompts.js` 四条提示词 + `ReadPromptTest` 类契约单测（13 个 pos 标签、逐字符保真、不输出罗马音、注入防护、grammar 字段顺序）。
- 验收：结构校验、提示词契约、注入防护全绿，`grammar` 默认不发请求。真实调用：无。依赖：B1/B3。

**实施记录（2026-11-06，已完成）**：

- `read-engine.js` 加 `selectWordDetailContext`（≤1000 字发整篇，超过收敛到目标词所在段落，段落仍超取前后各 2 句）与 `createLru(max)`（插入顺序淘汰、命中提升、只缓存已完成）。
- `read-client.js` 加 `wordDetailParagraph`（上下文裁剪 + LRU 80 + `force` 重生成）与 `grammarParagraph`（按需触发、按段缓存、离线与预算先拦截）；`analyzeParagraph` 写的缓存条目补 `grammar: null` 字段。
- `read-view.js` 加逐词点击（带 `stopPropagation` 与 `data-start/end` 字符区间）、`showSheet/hideSheet/highlightGrammar`、解析成功后才显示的「看语法」按钮；`index.html` 加底部抽屉（U3），`styles.css` 加遮罩与 `is-grammar-hit` 描边；`app.js` 接第 3/4 次调用、语法结果挂同一条按段缓存、切走视图自动收起抽屉。
- 新增 8 项编排单测 + 7 项 UI 断言，全量 **299/299**，0 失败。字体子集重跑 1306 字符（woff2 384KB / Android TTF 698KB）。
- 真实浏览器冒烟（Playwright + 全假 fetch）：词详情抽屉 6 行、标题「词详情 · 今」、总请求 3；二次点同一词请求数仍 3（LRU 命中）；语法抽屉 4 行、3 个逐词橙色高亮、总请求 4、缓存条目已写入 `grammar`；`outboundRequests = []`、零 console / pageerror。
- **发现并修一处布局问题**：抽屉原放在 `#view-read` 内时 `position: fixed` 被限制在主内容区，遮罩盖不住侧栏、面板浮在页面中部。已把抽屉移到 `body` 末尾（外壳之外），实测遮罩覆盖全屏、面板贴底；同时在 `showView` 切到非阅读视图时自动收起抽屉。
- 一处交互事实：抽屉是**模态**的，打开时遮罩拦截背后点击，二次点同一个词需先关闭抽屉；LRU 缓存不受影响。这与 U3「词详情/语法走底部抽屉」的语义一致，已记入验收口径。
- `grammar` 仍严格按需（只由「看语法」按钮触发），不随点段自动发请求；`engineRefs` 目前只在抽屉里展示，真正的引擎反查与「练这题」跳转留到 B6。
- `visual-diff.py` 仍未跑：无改动前像素基线，本批截图只作后续基线。

### B6 · 学习闭环（Q8，复用既有体系）

`resolveEngineRef` 反查 → 现有「练这题」通道 → 现有错题 / 复习 / 统计；书签复用既有键；今日活动与主页最小摘要；口径写入 D-045。

验收：映射单测（含同形异义、活用形还原、无法映射时不产题）、既有错题与统计零回归、122 基线不劣化。**不改错题结构、不改复习排期、不修订 `module-guide.md:9`**。依赖：B1/B4/B5。

**实施记录（2026-11-06，已完成）**：

- `read-engine.js` 加 `buildEngineCatalogs(verbEngine, adjEngine, particleEngine)`：把三个既有引擎的词库展开成可由文章词面反查的目录（动词全活用形、形容词全形式含变体、助词专题词面）。只读引擎导出的纯数据，返回新数组。
- `app.js` 接上闭环：初始化时构建目录；`startWordDetail` / `startGrammar` 用 `resolveEngineRef` 反查；`startReadPractice(ref)` 分三路进入既有练习——动词直接复用 `practiceFromReference`，形容词用 `buildAdjectiveQuestionById`，助词用 `getQuestions` 取该专题首题构造单题队列。**三个既有出题函数都没改**；形容词既有的 `practiceFromAdjectiveReference` 只收 formId 且随机取示例词、助词既有的 `startParticlePractice` 是随机题，因此另开了两个「指定词 / 指定题」入口。
- 抽屉新增动作区：可反查的词显示「练这个词」，语法结果有 `engineRefs` 时显示「练这组词」，另有「收藏这个词 / 收藏这一段」。书签复用 `japanese-verb-practice-bookmarks-v1`（与 Android 同构的 `word` / `sentence` 两类），可取消收藏。
- 今日答题统计**无需新增代码**：由文章进入的题走既有判题通道，自动进既有错题与间隔复习。新增的只有 `japanese-read-analyzer-activity-v1`（今日阅读段数 / 收藏词数）与主页卡一条最小摘要。
- 新增 4 项引擎单测 + 6 项 UI 断言，全量 **309/309**，0 失败。字体子集重跑 1308 字符（woff2 385KB / Android TTF 700KB）。
- 真实浏览器冒烟（Playwright + 全假 fetch）：词详情抽屉 6 行 + 三动作按钮；收藏写入 2 条书签（词 / 段，note 用译文）；点「练这个词」切到助词练习并**真实渲染出既有题目**「私＿学生です。」与四个选项、侧栏高亮同步、抽屉自动收起；主页显示「今日已读 1 段 · 收藏 1 词」；`outboundRequests = []`、零 console / pageerror。两张截图目视无重叠无溢出。
- 未做（沿用既有边界）：书签浏览页、跨端数据同步、把解析结果直接写进错题本——后者正是 Q8 明确排除的路径。

### B7 · 长文与流式增强

分块并发（参数自行推导）+ 连续完成前缀顺序合并 + 原文一致性校验；SSE 增量 JSON 扫描 + 收尾严格校验 + 宽松兜底；一键全文解析（费用二次确认 + 预算）；划选区间合并分析（按段界分批串行、结果在弹层、列表不动）。

验收：顺序与原文一致、部分失败整体失败且不写半截缓存、取消能中止全部在途块。依赖：B4/B5。

**实施记录（2026-11-06，已完成，交互形态按关子选定的四项推荐）**：

- `read-engine.js`：`LONG_TEXT_CONCURRENCY = 2`（2 块 = 4 并发 HTTP 峰值）、`SELECTION_BATCH_CHARS = MAX_UNIT_LENGTH`（D-036 自推）；`contiguousPrefixLength`（顺序合并进度口径）、`recoverTokenObjects`（宽松兜底，只收完整 token 对象，半截天然落选）、`scanIncrementalTokens`（SSE 增量 JSON 扫描，仅进度显示）、`parseAnalysisWithFallback`（严格失败才降级，宽松结果仍过原文一致性校验）、`splitSelectionBatches` + `mergeChunkResults`（批界跨段落补「改行」token，合并结果与选区原文逐字一致）。
- `read-client.js`：`buildRequest` 加 `options.stream`（仅 analyze）；`retryStripFields` 泛化 400 去字段重试（response_format / stream 一次各去）；`assembleSSEContent`（帧序列拼 content，端点无视 stream 时回落普通 JSON 解析）；上游 error 帧优先报错（D-035）；`createFetchTransport` 流式响应走 `ReadableStream` 逐块喂 `onChunk`；`analyzeParagraph` 透传 stream 选项；`analyzeLongText`（分块并发 + 连续前缀顺序合并 + **原子缓存**：全部成功才落缓存，部分失败整体失败且不写半截缓存 + cancel 中止全部在途块 + 预算预检只按未缓存块计）；`analyzeSelection`（按段界分批**串行**，跨批换行合并，结果在弹层、列表不动）。
- UI（四项推荐全通过）：「解析全文」在阅读头部右侧；费用二次确认复用底部弹层（写明段数/请求数/预算）；进度状态条「解析中 x / y 段…」+ 取消按钮；划选后选区旁浮现「分析选区」按钮。长文块默认 `stream: true`（截断时宽松兜底可救回完整 token）。预算内取原文序最长前缀（Q4 每篇 20 次请求约束下超长文可分次跑，预算上限不变）。
- 全量 **348/348**（309 → 341 → 348）。变异验证：改坏 `contiguousPrefixLength` 抓到 2 项、改坏原子缓存抓到 2 项（含强化后的「有块成功后再失败」场景）。
- Playwright 真浏览器冒烟（file:// + addInitScript 全假 fetch）：场景 A 全文（确认层零请求 → 6 次假请求、3 个 analyze 走 SSE 流式、三段按原文序落位）、场景 B 划选（按钮浮现 → 弹层合并逐词、列表不动、合批 2 次请求）、场景 C 取消（4 个在途请求全部中止、卡片复位）；零 console/pageerror、零真实出网；6 张截图目视正常，存 `C:/Users/xiao_/AppData/Local/Temp/nihongo-read-b7/`。
- 冒烟抓到两个真 bug（已修 + ui.test.js 锁回归）：① 划选按钮 `mousedown` 不 `preventDefault` 会折掉选区 → `selectionchange` 藏按钮 → click 永远落空；② 按钮用 `transform: translate(-50%)` 定位时，CDP 命中检测按布局盒算、中心点漂出按钮，改 JS 直接算 left。
- 字体子集 1317 字符 / woff2 389KB / Android TTF 706KB。

### B8 · 扩展能力（补齐参考项目剩余功能面）

图片 OCR（canvas 长边 1600 / 质量 0.7 → dataURL → 视觉模型 → 用户确认后再解析）；朗读（优先 `speechSynthesis`，可选第三方 TTS 需自填端点并告知风险）；AI 助手（当前段落上下文、消息仅内存、默认关）；深浅色与三个显示开关收进抽屉；纯本地埋点（次数 / 耗时 / 错误分类，不出网、不含原文与密钥）。

验收：每项可独立开关，任一失败降级不影响阅读。依赖：B4/B7。

**实施记录（2026-11-06，已完成，五项交互形态按关子选定：OCR 双入口 / TTS 单段+播放条都要 / 助手底部弹层 / 弹层收纳+全站主题 / 埋点设置页统计区）**：

- **B8-a 深浅色双主题**：`styles.css` 用 `@media (prefers-color-scheme: dark) { :root { … } }` 只覆盖颜色令牌值，组件层零改动（全经 `var()` 取色）；对齐 Android D-041 墨夜「暗底/柔化/减阴影」原则但 Web 令牌自成体系。先收编后覆盖：组件层 30 处硬编码 `rgba` 精确值收编为 22 个语义令牌（`--glow-*`/`--surface-glass-*`/`--hairline*`/`--shadow-*-tint` 等），浅色像素零变化后再叠深色值。`styles.test.js` 守卫升级：多 `:root` 块（含 @media 覆盖块）视为令牌定义区；硬编码拦截收掉 `#fff/#000` 例外并加 rgb/rgba/hsl/hsla；新增深色契约测试（覆盖块存在且换核心令牌 + `color-scheme: dark`）。
- **B8-a 抓到真 bug**：`.answer-row input` 与 `.mistake-card-actions button` 硬编码 `background: #fff`（旧守卫对 `#fff` 开例外而漏检），深色下白底残留 → 引入 `--field-bg` 令牌，守卫同步收掉例外。
- **B8-b 显示弹层**：三开关（注音/罗马音/译文）+ 语速滑杆（0.5–2.0×）+ 词性图例整体收进 `#read-display-sheet` 底部弹层（显示按钮在阅读头部）；`.read-paragraph-card` 加 `position: relative` 承载喇叭。
- **B8-c 朗读 TTS**：`read-tts.js` `createSpeaker(win)` 优先 `speechSynthesis`（零密钥零成本零出网），`lang: "ja-JP"`、语速取显示弹层滑杆；单段喇叭（内联 SVG，红线禁 emoji）+ 全文播放条（播放/暂停/上一段/下一段，三态按钮「播放→暂停→继续」）。代际号 `readSpeakGeneration` 让 cancel/替换后旧 utterance 的 `onend` 失效；顺序接续只在 `readPlaySequential` 下推进。
- **B8-c 抓到真 bug**：`stop()` 原来无条件 `synth.cancel()`，空闲 stop 会白打 cancel 且计数脏 → 改为只在确有在读 utterance 时 cancel。
- **B8-d 图片 OCR**：`read-ocr.js`（`MAX_EDGE = 1600`、`JPEG_QUALITY = 0.7`，无 canvas/解码失败降级原始 dataURL）；双入口共用流程（输入页「识图导入」按钮 + 阅读器拖拽/粘贴）；识别结果走确认弹层（转写文本预览 +「导入并切段/取消」），确认后才入文本解析流；`visionModel` 配置项留空回退主模型；多模态 user 消息（text + image_url），图片文字声明为待转写内容（注入防护延续）。空转写按 `PARSE_FAILED` 报（没识别到文字不静默导入空文章）。
- **B8-e AI 助手**：`assistantEnabled` 配置项默认关，设置页开关启用后头部才显示「问助手」；底部弹层多轮对话，消息仅内存不落盘；首轮注入【当前段落】原文+译文上下文（wire 里带、气泡只显示纯提问），之后多轮续聊（`[{role:"system"}].concat(完整历史)`）；每次提问计预算 1。`read-prompts.js` 加 `OCR_SYSTEM`/`ASSISTANT_SYSTEM`。
- **B8-f 本地埋点（D-037 无遥测）**：`read-stats.js`（`japanese-read-analyzer-stats-v1`，schema v1 带版本号、坏数据逐项回退）只记解析次数/缓存命中/耗时/错误分类，不出网、不含原文与密钥（红线单测钉住）；错误码归并 7 组（network/auth/server/parse/budget/config/other）；`analyzeParagraph` 所有出口（缓存/离线/预算/网络/失败/catch）经统一 `report()` 上报 `onOutcome`，`durationMs` 只测网络段墙钟；设置页「本地统计」卡展示 + 清零。
- 全量 **395/395**（348 → 349 深色契约 → 365 → 378 → 390 → 395），字体子集 1425 字符 / woff2 422KB / Android TTF 766KB（`collect-chars.js` 白名单补上此前漏扫的全部 `read-*.js`）。
- **冒烟抓到桩泄漏真 bug（已修 + 静态守卫锁回归）**：`confirm-ocr` 分支调用 `textarea.dispatch("input")`——`dispatch` 是 dom-stub 测试桩专属 API，真浏览器会 TypeError 导致 OCR 导入整体失败，单测因桩也有该 API 而漏检。改标准 `dispatchEvent(new window.Event("input"))`，dom-stub 补 `Event`/`dispatchEvent`；`ui.test.js` 新增静态守卫（生产代码禁 `.dispatch(`，变异验证拦截有效）。
- Playwright 真浏览器冒烟（file:// + addInitScript 全假 fetch + 全假 speechSynthesis）：六场景 42 断言全过——A 显示弹层（开关/滑杆联动）、B 朗读（单段 ja-JP/语速透传、播放中同段再点停止、顺序接续段 1→2→3、暂停/继续）、C OCR（按钮桥接 file input、确认弹层、导入切段、只发 1 次转写、走 visionModel、多模态消息）、D 助手（首轮上下文注入且气泡不泄漏、多轮 system+3、Enter 发送）、E 埋点（解析次数入账、清零）、F 深色（无缺字、零报错）；零 console/pageerror、零真实出网；6 张截图存 `C:/Users/xiao_/AppData/Local/Temp/nihongo-read-b8/`。
- 浏览器桩经验：`window.speechSynthesis` 是原生只读 getter，假 synth 必须 `Object.defineProperty` 覆盖（赋值静默失败）；headless 下隐藏 file input 的程序化 `click()` 不产生 Playwright `filechooser` 事件，按钮链路用 hook 计数断言、文件用 `setInputFiles` 注入；假 synth 的 `onend` 不要定时自动触发（与点击开销赛跑），手动 `__ttsFinish()` 收尾才确定。

### B9 · 收口

全量测试（Web 122+N、Android 264 不劣化）；视觉验收 12+ 态与像素差（≤500 像素且平均通道差 ≤0.5）；字体子集两端一致；文档同步（§8 表）；两种运行形态走查（`file://` 双击与 GitHub Pages `https://` 的 Origin / CORS / mixed-content / 缓存）；GitHub Pages 部署验证；成本报告（每篇调用次数、缓存命中率、预算触发次数）。

**实施记录（2026-11-06，已完成）**：

- **全量测试**：Web 11 文件 **396/396**（含静态守卫）；Android `:app:testDebugUnitTest --rerun` 强制重跑实测 25 类 **264 项 0 失败**（聚合 XML `files=25 tests=264 failures=0 errors=0`）。全程 Fake transport，零真实 AI 调用（G2 未授权）。
- **视觉验收**：`visual-check.js` 20 态全产出；`visual-diff.py` 对比 B1–B7 基线（`27b4e99` 的 `git archive` 提取物，同一 harness 双跑）——**01–19 全 PASS**（其中 16 态像素级零差；04/09/14 三态 ≤5 像素、≤1 超容差像素的渲染噪声），实证 B8 令牌收编「浅色像素零变化」；**20-read-reader FAIL**（111482 超容差像素）为 B8 阅读器新控件（单段喇叭/显示入口/播放条）造成的有意布局变化，热图显示差异全部落在新控件与卡片位移上，目视成品完整、无重叠无溢出。
- **字体子集**：woff2 与 Android TTF 的 cmap 对称差 **0**（各 1422 字形；字符表 1424 项 = cmap 1422 + `\n` + `↻` 两个不产字形项），woff2 422KB / TTF 766KB，`font.test.js` 绿。
- **两种运行形态走查**：同链路冒烟（粘贴 → 切段 → 点段（逐词/译文）→ 点词（6 字段词详情）→ 语法 → 练这组词进练习）在 `file://` 与 Pages `https://` **各 17 断言全过**，出站 4 次调用（analyze/translate/detail/grammar）全部命中假 transport。形态差异事实：① Origin——`file://` 为 `null`（用户 API 的 CORS 白名单需放行 `*` 或 `null`），Pages 需放行 `https://xiao-ranbei.github.io`；② mixed content——https 页面下用户端点必须 https（http 被拦），`file://` 无此限制；③ 缓存——Pages 响应头 `Cache-Control: max-age=600` + ETag/Last-Modified（新版本最长 10 分钟全局可见），HSTS 已开；应用数据 localStorage 按 origin 隔离，`file://` 与线上记录互不相通；④ 静态扫描 `http://` 子资源为零（唯一命中是校验文案字符串）。
- **GitHub Pages 部署验证**：`00f9be0` 推送触发 Deploy workflow **success**（21s）；线上 HTML 实测含 `read-tts.js/read-ocr.js/read-stats.js/read-view.js` 全部 B8 标记，响应头如上。
- **文档同步（§10 清单十项全部落地）**：`decisions.md` 新增 D-045～D-049 且 D-036 补 commit `f203982`/`fb57ddc` 标注；`current-state.md` 阅读模块/结构/存储键/测试基线；根 `README.md`（线上地址、Web 能力、Android 阅读、联网限制、测试命令）；`information-architecture.md`；`docs/README.md` 索引；`module-guide.md` 既有模块登记与 Web 约定；`known-issues.md`（IK-11 收窄 + 新增 IK-21/IK-22）；`web/README.md`（字体数字、测试表补三行）；`android-read-analyzer-spec.md`（即本文所指 spec.md：许可证标注、两处「13 项品詞」→「13 个 pos 标签」）。
- **成本报告（设计口径 + Fake 实测；真实调用 0 次）**：
  - 每篇调用次数：每段 2 次（analyze+translate 并发）；点词详情 1 次（LRU 80 内命中 0 次）；`grammar` 1 次（按需触发、按段缓存后 0 次）；一键全文 = 未缓存段数 × 2（费用二次确认 + 预算预检只按未缓存块计）；助手每问 1 次；OCR 每图 1 次。3 段样例全解析 + 词详情 + 语法 = 8 次。
  - 缓存命中率：命中即零请求（B4 冒烟实测「二次点同一段 fetch 不增」、B5「二次点同一词 LRU 命中」）；收口链路冒烟 4 次出站全部为必要调用，零重复。
  - 预算触发次数：**0**（每篇 20 / 每日 100 硬上限由单测守护；全程零真实调用，无真实触发）。
- **范围调整与遗留登记**：① 计划 B8 的「可选第三方 TTS 自填端点」未实现，只落 `speechSynthesis` → 登记 IK-22；② Web 书签无浏览页 → 登记 IK-21；③ 识图导入按钮在配置无效时隐藏（未配置态不展示不可用入口——19 态像素零差的直接原因），属实现细节非闸门。
- **会话事故记录（非产品事实）**：清理变异探针时误用 `git checkout -- web/app.js` 把未提交的 B8 改动回滚到 HEAD，经会话转录（`~/.workbuddy/projects/…jsonl`）导出 49 条 Edit 按 timestamp 重放 100% 复原，396/396 复跑全绿；教训「清理变异探针禁用 git checkout」已写入跨项目长期记忆。

---

## 9. 需关子确认的闸门（最少化）

| 编号 | 问题 | 推荐 | 若选其他 |
|---|---|---|---|
| G2 | 是否授权一次真实端点实测（会产生费用） | 不授权，全程 Fake；等你自己拿 Key 冒烟 | 授权则 B3 做一次 CORS / 鉴权实测，记录结果与费用 |
| G3 | `grammar` 契约字段是否按 §4.4 草案 | 按草案 | 若要加依存树 / 主谓宾可视化，B5 工作量与提示词风险另估 |
| U1 | 入口形态 | 侧栏新增「文章阅读」一项 + 主页一张卡 | 只侧栏 / 只主页卡 / 独立顶栏页签 |
| U2 | 逐词呈现 | ruby 注音在上 + 词性淡底色 + 图例（对齐 Android 现版） | 词性下划线 + 图例（参考项目现版）/ 双栏对照列表 |
| U3 | 结果面板 | 译文与逐词同卡，语法与词详情走底部抽屉 | 桌面右侧固定侧栏 / 点词弹模态 / 上下分栏 |

按你的长期约定，U1～U3 属交互形态，不代为拍板；回一句「按推荐」即视为全部通过。G1（架构）已由 Q3=1 关闭。

---

## 10. 文档同步清单（B9）

| 文件 | 改动 |
|---|---|
| `docs/decisions.md` | 新增 D-045～D-049：Web 联网边界、四次调用契约、成本口径、今日活动口径、`grammar` 能力定位 |
| `docs/current-state.md` | 从「阅读标签是规划中占位」改为 Web 已实现，补全文结构、存储键与测试基线 |
| `README.md`（根） | 修 `:122` 与 `:217` 自相矛盾，补 Web 能力 |
| `docs/information-architecture.md` | 修 `:69` 四项占位清单 |
| `docs/README.md` | 索引状态更新；本计划文档登记 |
| `docs/module-guide.md` | 登记 `read-analyzer` 模块 ID 与接入结论 |
| `docs/known-issues.md` | 关闭 IK-11（查词）相关项或重定义；登记新增遗留 |
| `web/README.md` | 修 `:55` 字体数字；补阅读模块运行说明与测试文件 |
| `spec.md:6`、`decisions.md:42` | 补许可证 commit/tag 标注（`f203982` AGPL / `fb57ddc` MIT） |
| `spec.md:411`、`:457` | 「13 项品詞」统一为「13 个 pos 标签」 |

---

## 11. 完成判据（Done）

1. §2 表中 20 项能力按标注批次全部落地，四项「不做」有书面理由。
2. Web 全量测试只升不降（基线 122/122），Android 264 项 0 失败；新增测试均做过变异验证。
3. `file://` 与 GitHub Pages `https://` 两种形态都能完成「粘贴 → 点段 → 逐词/译文 → 点词 → 语法 → 进练习」全链路。
4. 未配置 / 离线 / 失败 / 截断 / 未对齐 / 预算耗尽六态都有中文可读反馈，正文始终可读。
5. 未经 G2 授权，整个实施过程零真实 AI 调用。
6. §10 文档同步完成，仓库无「规划中占位」与已实现能力冲突的描述。
