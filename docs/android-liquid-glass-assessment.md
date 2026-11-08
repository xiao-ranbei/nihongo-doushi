# 酷安「液体玻璃」调研评估 —— 面向安卓端全量 UI 翻新与底层重构

> 2026-11-07 · 状态：调研评估，**不含实现、不含 UX 定案**
> 范围：仅 `android/` 端。Web 端不在本次范围。
> 边界：只在已提及的「液体玻璃 / AndroidLiquidGlass」与项目现有结构内讨论，**不引入未提及的技术选型**。

## 0. 结论先行与证据分级

| 问题 | 结论 |
| --- | --- |
| 酷安液体玻璃是不是 AndroidLiquidGlass 的应用？ | **不是独立方案；官方明确以该开源项目为来源／致谢对象。但「同一实现」（直接集成同一份库代码或二进制）无法确认，倾向可能、缺硬证据。** |
| 能否全量翻新当前安卓端？ | 技术上可分层推进，但**不能直接照搬**：库不含高层组件、无版本降级、官方无性能数据、且现有顶圆角形状与 lens 约束冲突。需先做 P0 验证。 |
| 最大风险 | 多玻璃面叠加的 GPU 开销、RenderThread SIGSEGV、API 26–30 无效果、折射在顶圆角面板上失效。 |

**证据等级**（全文标注）：

| 级 | 含义 |
| --- | --- |
| A | 一手资料：库官方 GitBook 文档、GitHub README／LICENSE／源码、本项目代码实读 |
| B | 多源一致的官方更新日志转载（Coolapk-Fan wiki、豌豆荚历史版本、多个下载站） |
| C | 媒体／社区单一表述、第三方整理、口碑 |
| D | 基于 A/B 的逻辑推导，未实机验证 |

标记：`[已确认]` = A/B；`[推测]` = C/D；`[未确认]` = 公开资料不足以判定。

---

## 一、调研结论

### 1.1 关系澄清：酷安 vs AndroidLiquidGlass

**`[已确认]` 事实（B，多源一致）**

| 项 | 内容 | 来源 |
| --- | --- | --- |
| 版本 | 酷安 16.6.0 / 16.6.1 引入「全新液态玻璃设计风格」 | Coolapk-Fan wiki「精选版本更新日志」、豌豆荚 v16.6.1 历史版本页 |
| 门槛 | 「需要安卓 13 及以上系统」 | 同上 |
| 致谢 | 「向 @Kyant0 的 AndroidLiquidGlass 开源致谢」 | 同上，多个下载站转载同一更新日志 |
| 材质 | 「支持材质选择：液态玻璃 / 背景模糊 / 半透明」三档 | 同上 |
| 落点 | 16.6.2「优化 液态玻璃底栏性能」 | Coolapk-Fan wiki |
| 低版本 | 应用本身仍标注 Android 7.0 以上；液态玻璃低版本不生效 | 豌豆荚应用信息 + 下载站「低版本不会受影响」 |

**`[已确认]` 库本体（A）**

- `Kyant0/AndroidLiquidGlass`，LICENSE 为 **Apache-2.0**，Copyright 2025 Kyant。
- 1.x README（镜像）：自称 "Apple's Liquid Glass effect for Android **Jetpack Compose**"，Demo 明确 "Android 13 and above is required"，标注 alpha、API 可能随时变；JitPack 坐标 `com.github.Kyant0:AndroidLiquidGlass:<version>`。
- 2.x 已更名为 **Backdrop**，转向 **Compose Multiplatform**，Maven Central 坐标 `io.github.kyant0:backdrop:<version>`；README 原文：「The library **does not include any high-level components**; you will need to create your own.」示例组件 `LiquidButton / LiquidToggle / LiquidSlider / LiquidBottomTabs` 都在 catalog 工程里，不在库中。
- `[推测]` 版本时间线（C）：第三方整理列出 2.0.0-rc01（2026-05-26）、2.0.0（05-28）、2.0.1（08-26）。酷安 16.6.x 的版本号编码指向 2026-08 中下旬，若成立则当时**最多用到 2.0.0 或 1.x**，不可能是 2.0.1。此推论依赖第三方日期，未由仓库 release 页直接核实。

**判定（对三个选项逐一表态）**

| 选项 | 判定 | 依据强度 |
| --- | --- | --- |
| 独立方案 | **排除** | B：官方更新日志点名致谢该开源项目；C：媒体表述「采用三方开源的 UI 库」 |
| 参考来源／基于该开源实现 | **已确认** | B：致谢 + 三档材质 + Android 13 门槛，与库的能力边界高度吻合 |
| 同一实现（直接依赖同一库二进制／同一份代码） | **无法确认，倾向可能** | 缺反编译依赖清单、酷安技术栈说明、官方技术公告。三种可能公开资料无法区分：① 直接依赖 `io.github.kyant0:backdrop`；② fork／移植源码；③ 借鉴 lens／shader 思路自绘 |

`[未确认]` 关键缺口：酷安 App 是否 Compose 技术栈、具体库版本、液态玻璃是否覆盖底栏以外的页面、三档材质是自动按 API 降级还是纯用户手动选择。16.6.2 只点名「底栏」，其余「全新设计风格」措辞宽泛，**不能当作全 App 覆盖的证据**。

### 1.2 视觉表现

**`[已确认]`（A，官方教程与 API 文档）**

- 核心：把背景（backdrop）的一份副本绘制到前景，再对其施加效果链。
- 效果链有强制顺序：`color filter ⇒ blur ⇒ lens`。
  - color filter：`vibrancy()`（饱和度 ×1.5）、`colorControls`、`exposureAdjustment`、`gammaAdjustment`（13+）
  - blur：`blur(radius, edgeTreatment)`
  - lens：`lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)`
- 官方底栏教程最终配方：`vibrancy() + blur(4dp) + lens(16dp, 32dp)` + `onDrawSurface = { drawRect(Color.White.copy(alpha = 0.5f)) }`；原话「You must balance between beauty and readability」。
- 绘制层次（C，第三方解析）：`onDrawBehind → onDrawBackdrop → onDrawSurface → drawContent → onDrawFront`。
- 必须把内容之外的背景也画进 backdrop，否则玻璃条出现透明像素：`rememberLayerBackdrop { drawRect(backgroundColor); drawContent() }`。
- 视觉语言：半透明表面 + 模糊 + 饱和度提升 + **边缘折射／厚度感** + 色散（chromatic aberration）+ 高光 + 阴影。1.x 另有 `refractionWithDispersion`、`onDrawSurfaceWithHighlight`。

**`[推测]`（C，媒体观感）**：「晶莹剔透的半透明质感」「滑动时边缘光影过渡丝滑」「果味十足」；同一批来源里也有「流畅度欠佳」。两者矛盾，指向设备／场景差异，不是定论。

### 1.3 模块边界

| 层 | 边界 | 等级 |
| --- | --- | --- |
| 库 | 只提供 `Backdrop` 抽象（`rememberLayerBackdrop` / `rememberCombinedBackdrop` / `rememberCanvasBackdrop` / `emptyBackdrop`）、`Modifier.layerBackdrop`、`Modifier.drawBackdrop`、effects。**零高层组件** | A |
| 应用 | 每个按钮／标签栏／弹层／滑块都得自己写玻璃版本，自己处理选中态、交互、版本分支、降级、可读性 | A |
| 酷安 | 至少底栏（16.6.2 与媒体均点名）；其余覆盖范围未确认 | B/C |
| 本项目 | `ui/theme/Theme.kt` 单文件 483 行（双皮肤 + `ModuleGeometry` + 四个取色入口）；`ui/common/` 仅 2 文件；导航是 `Scaffold` + `when` 状态机，无 Navigation Compose；**全源码 0 处 blur / graphicsLayer / RenderEffect / shader** | A（本地实读） |

### 1.4 所依赖的系统能力

| 能力 | API 门槛 | 作用 | 对本项目 `minSdk 26` 的影响 |
| --- | --- | --- | --- |
| `RenderEffect` | Android 12 / API 31 | 全部 backdrop effects 的载体 | 26–30 完全无效果 |
| `RuntimeShader`（AGSL） | Android 13 / API 33 | lens 折射、色散、gamma | 31–32 只能 color filter + blur |
| `GraphicsLayer` / 离屏缓冲 | Compose 层 | 捕获背景内容供前景复用 | 需重排根布局层级 |
| GPU / RenderThread 硬件加速 | 设备相关 | shader 执行 | 低端机成本未知 |
| `Build.VERSION.SDK_INT` 分支 | 应用自行实现 | 官方教程**没有**版本分支与 fallback | 1.x 示例曾用 `SDK_INT >= TIRAMISU` 包住 refraction，降级策略必须自建 |

`[已确认]`（A）：这是**应用内自绘**，不需要新增权限、不需要 root、不依赖厂商 ROM 或系统私有 API、不联网。官方 Get started 只给一行 Gradle 依赖，无 manifest／权限／平台初始化要求。

`[已确认]` 三档材质与三段 API 天然同构，可作为分级降级的骨架：

| 档 | 可用范围 | 能力 |
| --- | --- | --- |
| 液态玻璃 | API 33+ | color filter + blur + lens（折射/色散/深度） |
| 背景模糊 | API 31–32 | color filter + blur，无 lens |
| 半透明 | API 26–30（及低性能设备） | 纯色 + alpha，无 RenderEffect |

### 1.5 性能与兼容性影响

**`[已确认]` 的硬事实**

1. 官方**没有**性能专页、没有 benchmark、没有低端机数据、没有 minSdk／Compose 版本要求说明：文档索引仅 10 页（5 教程 + 2 API + Get started + FAQ），FAQ 只有两条排障问答。（A）
2. FAQ 明确存在 **RenderThread `Fatal signal 11 (SIGSEGV)`** 崩溃；Glass Bottom Sheet 教程给出根因：在 `drawBackdrop` 之后再叠 `layerBackdrop` 会形成「内容画 backdrop、backdrop 又画内容」的循环。正解是用 `exportedBackdrop`，它「will skip drawing the content」。即**玻璃上再放玻璃必须走导出层**。（A）
3. 酷安 16.6.2 专门「优化 液态玻璃底栏性能」；媒体同期表述「流畅度欠佳」。二者交叉印证首次落地确有性能代价。（B/C）
4. 官方文档对低版本只说「only take effect with Android 12 and above」，**没给任何降级实现**。（A）

**`[推测]` 成本模型（D，未实测）**

- 每个玻璃面 ≈ 一次背景捕获 + 一条逐效果 RenderEffect 链；同屏玻璃面数量与滚动重绘频率是主要变量，成本近似线性增长。
- 最危险组合：滚动列表上方叠玻璃底栏、玻璃弹层内再放玻璃控件、多层 drawer/sheet 同时存在。
- 本项目阅读器 `LazyColumn` 段落卡 + 侧边抽屉 + 两个底部弹层，正是上述组合。

**`[推测]` 兼容性冲突（D）**

- 深色「墨夜」皮肤 `cardShadow = 0`，靠 1dp 细边分层；玻璃语言依赖高光与阴影，两者视觉体系相冲。
- 霞鹜臻楷子集字体叠在半透明折射表面上，笔画对比度需实测。
- 库 2.x 是 CMP 且版本较新，与本项目 Compose BOM 2025.08.01 / Kotlin 2.2.21 / AGP 8.13.2 的依赖解析兼容性未知。

---

## 二、复用评估

### 2.1 可复用（直接搬）

| 项 | 说明 |
| --- | --- |
| 效果链与参数语义 | `color filter ⇒ blur ⇒ lens`、`refractionHeight/Amount/depthEffect/chromaticAberration` |
| 三档材质分级思路 | 与 API 26/31/33 三段对齐，天然是降级路径 |
| 库本体 | Apache-2.0 允许商用／修改／再分发；需保留 LICENSE 与归属声明（含 NOTICE，若有） |
| 官方底栏 / 底弹层骨架 | `rememberLayerBackdrop { drawRect(bg); drawContent() }` + `layerBackdrop` 标内容层 + `drawBackdrop` 画前景，对应本项目自绘底栏与底部判定抽屉 |
| 现有主题体系 | `Theme.kt` 的四取色入口、`ModuleGeometry`、`themedTone()`、双皮肤背景色（`#FAFAF8` / `#0F1114`）可直接作为 backdrop 背景色与降级配色源，**不必推翻** |
| 现有测试护栏 | `ThemeTokensTest`、`TalkBackMergeTest`、Web 端静态守卫可作为回归基线 |

### 2.2 需改造

| 现状 | 改造点 |
| --- | --- |
| `MainActivity.AppRoot`：`Scaffold(bottomBar = …) { Box { when(sub) … } }`，底栏与内容分属两层 | 需新增根 backdrop 层：把内容层包进 `Modifier.layerBackdrop`，并重排底栏与 backdrop 的层级／绘制顺序；`bottomBar` 中的玻璃面要能读到内容层 |
| `MainActivity.kt:170–183` `bottomInset` 特判（带底部条的二级页取 `0.dp`） | 与官方教程 `safeContentPadding()` 口径冲突；两套 inset 逻辑必须合并成一套 |
| 底部面板形状 `RoundedCornerShape(topStart = g.cardRadius, topEnd = g.cardRadius)`（`ModuleUi.kt:101`）、`JudgementDock`、`ParticleDock` 同款 | **关键冲突**：lens 文档要求 `shape` 为 `CornerBasedShape` 且 `height ∈ [0, shape.minCornerRadius]`；顶圆角形状底角为 0 → `minCornerRadius = 0` → 折射高度只能取 0，lens 实质失效。`[推测]`（D，按文档约束推导，未实机验证） |
| 4 个 M3 `ModalBottomSheet`（`ReferencePickerSheet` / `FilterSheet` / `MergedResultSheet` / `WordDetailSheet`）无自定义 shape、scrim、inset 覆盖 | 玻璃化需接管形状与遮罩；M3 弹层是独立窗口层级，`layerBackdrop` 能否直接叠加需验证 |
| 7 处 `AlertDialog` + 1 处 `ModalNavigationDrawer`（RTL 镜像右抽屉） | 同上；抽屉与弹层叠加时还有两层遮罩抢返回键的既有约束（`ReadSideMenu.kt` 注释） |
| 弹层双轨：4 个 M3 sheet + 多处自绘 `Surface`（顶圆角 / 1dp 边框 / `cardShadow` / `navigationBarsPadding`） | 全量翻新前应先收敛为一套规格，否则玻璃参数无法统一 |
| 底栏两态：糖果卡悬浮药丸（`navFloating=true`）vs 墨夜平铺全宽 | 需要两套玻璃参数，不是一套配置；`themedTone()` 循环渲染口径（D-044）必须保留 |
| `selectable` + `semantics { selected }`、`onLongClickLabel`（D-030 / IK-18） | 玻璃层重绘后焦点站与合并语义必须原样保持，`TalkBackMergeTest` 4 项是硬护栏 |
| `ui/common/` 仅 2 文件；`FilterPill`、`Chip`、`JudgementDock` 散在 `ui/practice/`；`ParticleTopBar` 与 `ModuleTopBar` 并存 | 全量覆盖前先合并组件层，否则同一效果要写 N 遍 |
| 新增文案 | D-029：字体子集收集器覆盖 Android `.kt` 字面量，新增设置项／提示文案必须重跑 `subset.py` 两端字体 |

### 2.3 不可直接复用

| 项 | 原因 |
| --- | --- |
| 库的任何「现成组件」 | README 明确不含高层组件；catalog 里的 `LiquidBottomTabs` 等是示例，不是 artifact |
| 官方教程代码直接进生产 | 教程**无** API 版本检查、无 fallback、无选中态、无交互、无性能说明；照抄会在 API 26–30 上出透明像素或无效效果 |
| 网上 1.x 片段（`liquidGlassProvider` / `GlassStyle` / `refraction()`）与 2.x Backdrop API 混用 | 1.0.0-alpha09 起 API 已换代，1.x 标注 alpha「every API may be changed」；片段必须按所选版本核对 |
| 酷安的具体参数、覆盖范围、降级逻辑 | 未公开；且「同一实现」本身未确认 |
| Web 端 | 用户明确限定安卓端；且 Web 是 `file://` / Pages 双形态，与本评估无关 |
| 用户未提及的技术选型 | 本次不引入导航库、不引入其它玻璃／模糊实现、不更换主题系统；现有 `when` 状态机只作为结构事实描述 |

### 2.4 全量覆盖范围清单（明确锁定）

用户要求范围覆盖**所有页面、弹层与共用组件**。以下为实读 `android/app/src/main/java/` 后的清单，不遗漏：

| 类 | 数量 | 明细 |
| --- | ---: | --- |
| 一级页 | 4 | `HomeScreen`（含 `ProverbCard`、`SuggestionSection`）、`StudyScreen`、`ReadScreen`、`MineScreen`（含 `MineProfileCard`、`MineStats` 三卡） |
| 二级页 | 20 | `MistakesScreen`；动词 `VerbHome/Lesson/Reference/ThemeCards`；形容词 `AdjectiveHome/Lesson/Reference`；助词 `ParticleHome/Lesson/Practice/Mistakes`；练习 `VerbPracticeScreen`、`AdjectivePracticeScreen`、`PracticeUi`（10 个组件）；阅读 `ReadInputScreen`、`ReadReaderScreen`、`ModelSettingsScreen`、`ReadSideMenu` |
| 导航容器 | 5 | `AppRoot`（`MainActivity`）+ `VerbFlow` / `AdjectiveFlow` / `ParticleFlow` / `ReadFlow` |
| 底栏 | 1 | `AppBottomBar` + `BottomTabItem`（自绘，两态） |
| M3 弹层 | 4 | `ReferencePickerSheet`、`FilterSheet`、`MergedResultSheet`、`WordDetailSheet` |
| 对话框 | 7 | 3 个「退出本轮？」、清空错题、统计清零、`EditNicknameDialog`、删除文章 |
| 抽屉 | 1 | `ReadSideMenu`（RTL 镜像） |
| 自定义底部面板 | 7+ | `ModuleBottomSurface`（4 处引用）、`JudgementDock`、`ParticleDock`、`ParticleHomeScreen` / `ParticleLessonScreen` / `ParticlePracticeScreen` 内联同类 `Surface` |
| 共用组件 | 11 | `ui/common/ModuleUi.kt` 9 个（`ModuleTopBar` / `ModuleBottomSurface` / `ModuleProgressBar` / `ModuleSectionLabel` / `ModuleHeader` / `ModuleGlyphBox` / `EntryCard` / `PlannedBadge` / `PlannedRow`）+ `ReferencePickerSheet` + `FilterSheet`（在 `ui/practice/`） |
| 主题层 | 1 | `ui/theme/Theme.kt`（483 行）+ `values/themes.xml` / `values-night/themes.xml` 冷启动第一帧 |

---

## 三、重构规划

### 3.1 优先级

**P0 · 前置验证（不写生产代码，先回答「能不能做」）**

1. 依赖解析实测：锁定具体库版本，验证与 Compose BOM 2025.08.01 / Kotlin 2.2.21 / AGP 8.13.2 能否解析、APK 体积增量。
2. 三档能力矩阵：API 26 / 31 / 32 / 33 / 35 上分别确认「无效果 / blur / 全效果」的实际表现与是否崩溃。
3. 形状约束：实测顶圆角 `RoundedCornerShape(topStart/topEnd)` 上 lens 是否失效；确认 `CircleShape` 与 `pillRadius=999.dp` 可用。
4. 窗口层级：验证 `ModalBottomSheet` / `AlertDialog` / `ModalNavigationDrawer` 能否叠加 `drawBackdrop`，以及 glass-on-glass 用 `exportedBackdrop` 后是否还有 SIGSEGV。
5. 性能基线：先测现有页面帧率／渲染耗时，再测单玻璃面、N 个玻璃面、滚动中、弹层叠加四种场景。
6. 双皮肤可读性：糖果卡 / 墨夜 × 玻璃表面下的文字对比度、霞鹜臻楷笔画清晰度。

**P1 · 底层重构**

1. `Theme.kt` 增加材质令牌层（效果参数、表面 alpha、高光／阴影、三档枚举），保留现有四个取色入口与 `geometry()`，不推翻 D-041～D-044。
2. `AppRoot` 根 backdrop 层 + 版本分支 + 统一降级入口；合并 `bottomInset` 与 safe content padding 两套 inset 逻辑。
3. 收敛弹层双轨为一套规格；统一 `ui/common/` 组件层（合并散落的 `FilterPill` / `Chip` / `JudgementDock` / `ParticleTopBar`）。
4. 补 UI 层测试护栏：当前 `src/test` 无任何 Composable 渲染测试，`src/androidTest` 仅 `TalkBackMergeTest` 1 类 4 用例。

**P2 · 全量铺开（按依赖顺序分批，每批跑回归）**

底栏 → 4 个一级页 → 5 个导航容器 → 20 个二级页 → 4 个 M3 sheet → 7 个对话框 + 1 个抽屉 → 7+ 自定义底部面板 → 11 个共用组件 → 冷启动 themes.xml 复核。
每批验收通过再进下一批；不做一次性全量替换。

### 3.2 风险

| 风险 | 触发条件 | 影响 | 缓解 | 等级 |
| --- | --- | --- | --- | --- |
| 性能塌方 | 同屏多玻璃面 + `LazyColumn` 滚动 + 弹层叠加 | 掉帧、卡顿；酷安 16.6.2 已专门优化过底栏性能 | P0 建立基线，限定同屏玻璃面数量，必要时整档降级 | B/C |
| RenderThread SIGSEGV | glass-on-glass 误用 `layerBackdrop` 形成绘制环 | 原生崩溃，非可捕获异常 | 强制 `exportedBackdrop`，静态守卫禁止在 `drawBackdrop` 后叠 `layerBackdrop` | A |
| 版本碎片 | `minSdk 26` | 26–30 用户看不到任何效果，31–32 只有模糊 | 三档分级 + 每档视觉自查；不抬 minSdk | A |
| 折射失效 | 顶圆角面板 `minCornerRadius = 0` | 底部操作条／判定抽屉只有模糊，没有玻璃折射 | P0 实测；必要时调整形状策略 | D（待验） |
| 视觉体系冲突 | 纸感三级层次 / 1dp 细边 / 零阴影墨夜 vs 玻璃高光语言 | 双皮肤观感不一致 | 材质参数按皮肤分套，保留令牌化 | D |
| 无障碍回归 | 玻璃层重绘改变语义树合并 | `selected` 语义、焦点站、长按标签丢失 | `TalkBackMergeTest` 4 项连设备跑；禁手工 `mergeDescendants`（D-030） | A |
| 测试基线劣化 | 主题令牌与几何改动 | `ThemeTokensTest`、静态守卫（`Color(0x…)` 仅 Theme.kt、`RoundedCornerShape(数字.dp)` 0 处）失效 | 每批复跑全量单测 | A |
| 字体缺字 | 新增材质设置项文案 | 整页下移 5px（项目既有实测） | 重跑 `web/font/subset.py` + `font.test.js` 双端守护 | A |
| 依赖／许可 | 库版本漂移、API 换代 | 编译失败或效果突变 | 钉死精确版本；保留 Apache-2.0 归属；1.x/2.x 片段不混用 | A |
| 舆情分歧 | 「丝滑」与「流畅度欠佳」并存 | 预期不可靠 | 只信本机实测，不以口碑定验收 | C |

### 3.3 需验证的不确定项

**外部事实（`[未确认]`，需酷安 APK 依赖清单／反编译或官方技术说明）**

1. 是否直接依赖 `io.github.kyant0:backdrop`，还是 fork 源码／仅借鉴 shader 思路。
2. 所用库的具体版本；酷安 App 是否 Compose 技术栈。
3. 液态玻璃覆盖范围是否超出底栏；三档材质是自动按 API 降级还是纯手动选择。
4. 官方未给出的库 minSdk、Compose 版本要求、低端机性能数据。

**本项目（`[推测]`，需 P0 实测）**

5. lens 在顶圆角 shape 上的实际折射表现。
6. M3 `ModalBottomSheet` / `AlertDialog` 独立窗口层级能否承载 `drawBackdrop`。
7. `Scaffold` 的 `bottomBar` 与内容层 backdrop 的绘制顺序。
8. 多玻璃面在同屏／滚动／弹层叠加下的帧率与崩溃率。
9. 双皮肤 × 半透明表面 × 子集字体的对比度。
10. 玻璃化后现有 264 项单测与 `TalkBackMergeTest` 4 项是否仍成立。

### 3.4 验收标准

工程性口径可先定；**具体视觉参数与性能阈值不擅自定案**，见 3.5。

| 维度 | 标准 |
| --- | --- |
| 测试基线 | Android 单测 **264 项 0 失败**（项目记忆基线 2026-11-05，本次未重跑）；Web 396/396 不受影响；`TalkBackMergeTest` 4 项连设备补跑通过（该项此前无设备未跑） |
| 静态守卫 | `Color(0x…)` 仍只允许出现在 `Theme.kt`；`RoundedCornerShape(<数字>.dp)` 仍 0 处；新增材质参数必须走令牌，禁止散落硬编码 |
| 兼容矩阵 | API 26 / 31 / 32 / 33 / 35 五档，每档走完：冷启动 → 四标签切换 → 四类弹层开关 → 滚动列表，无崩溃、无透明像素、无空白玻璃面 |
| 稳定性 | 弹层叠加 + 滚动连续 30 分钟，RenderThread SIGSEGV 为 0；glass-on-glass 一律经 `exportedBackdrop` |
| 性能 | **阈值待 P0 建立现有页面基线后由关子确认**；至少覆盖单面／多面／滚动／弹层四场景，且降级档达标 |
| 无障碍 | TalkBack 焦点站数量与顺序不劣化；`selected` 语义、`onLongClickLabel` 保留；双皮肤文字对比度需实测达标（阈值待定） |
| 视觉 | 每批按 2.4 清单逐项走查，双皮肤各一遍；**具体材质参数由关子确认** |
| 字体 | 新增文案后重跑 `subset.py` 与 `font.test.js`，两端字体同时更新 |
| 许可 | 保留 Apache-2.0 LICENSE 与版权／归属声明；若库含 NOTICE 文件，随分发附带 |

### 3.5 需关子拍板的 UX 决策点

按项目约定，交互与视觉方案不自定。以下只列选项，不替选：

| # | 决策点 | 选项（待选） |
| --- | --- | --- |
| Q1 | 三档降级如何呈现 | ① 完全按 API 自动；② 提供用户手动开关（沿用酷安三档）；③ 自动为主 + 高端机可手动覆盖 |
| Q2 | 是否保留 D-041「完全跟随系统、App 内无手动开关」 | ① 保留，材质只跟随系统皮肤；② 破例新增材质设置项（会带来新文案 → 字体子集重跑） |
| Q3 | 双皮肤覆盖范围 | ① 两套都玻璃化；② 仅糖果卡；③ 墨夜只做「背景模糊」档 |
| Q4 | 与现有纸感三级层次的关系 | ① 玻璃只替换底栏与弹层，页面卡片保持纸感；② 全量替换为玻璃语言；③ 分层混用 |
| Q5 | 性能不达标时 | ① 自动降档到背景模糊／半透明；② 该页面禁用玻璃；③ 全局关闭 |
| Q6 | 验收阈值 | 帧率下限、同屏玻璃面上限、对比度下限，由 P0 实测数据出来后定 |

> 范围已由本次需求锁定为「所有页面、弹层与共用组件」，Q1–Q6 不涉及范围变更。

---

## 附：证据源

**A · 一手**

- `https://github.com/Kyant0/AndroidLiquidGlass`（README、LICENSE、目录）
- `https://kyant.gitbook.io/backdrop`：`get-started.md` / `llms.txt` / `faq.md` / `api/backdrops.md` / `api/backdrop-effects.md` / `tutorials/glass-bottom-bar.md` / `tutorials/glass-bottom-sheet.md`
- 1.x README 镜像：`http://github.cc/MeyouOSS/AndroidLiquidGlass`
- 本项目实读：`android/app/build.gradle.kts`、`gradle/libs.versions.toml`、`MainActivity.kt`、`ui/theme/Theme.kt`、`ui/common/ModuleUi.kt`、`ui/practice/PracticeUi.kt` 等

**B · 官方更新日志转载**

- `https://github.com/Coolapk-Fan/wiki/wiki/[精选]-酷安版本更新日志-(Android)`（16.6.0 / 16.6.1 / 16.6.2）
- `https://www.wandoujia.com/apps/998339/history_v2608212`
- 9k9k / 单机100 / 微风下载 / 手游TV 等 v16.6.1 更新页

**C · 媒体／第三方整理**

- `http://www.sina.cn/news/detail/5335070143681169.html`（「采用三方开源的 UI 库」+「流畅度欠佳」）
- `https://www.toutiao.com/w/1874539058215939/`（底栏观感）
- `https://hysenlabs.com/projects/kyant0-androidliquidglass`（版本时间线、README 评价）
- CSDN《Compose 调用 - 液态玻璃 Backdrop》《深度解析 Android Liquid Glass》（绘制层次、API 片段，二手）

**D · 推导**：性能成本模型、`minCornerRadius = 0` 导致 lens 失效、双皮肤视觉冲突、本项目层级改造点——均未实机验证。
