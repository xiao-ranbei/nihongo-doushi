# 底部栏四标签框架 · 界面设计说明

> 日期：2026-10-17
> 状态：**设计定稿，待实现**（依据 `docs/android-four-tab-spec.md` §5 产出，不超出规格范围）
> 关联：`android-four-tab-spec.md`（本期规格，已锁定）、`android-two-tab-spec.md`（视觉风格与信息优先级通则，沿用）
> 范围：主页 / 学习 / 阅读 / 我的 四个一级页 + 底部四标签栏 + 跨页共用组件（规划中徽标、规划中信息行）
> 本文档所有文案字符串为**最终交付文案**，前端直接取用；改动任何一端文案后须重跑 `python web/font/subset.py`（D-029 纪律）

---

## 0. 文档定位与合规自检

本文档只补足规格 §5 未定的视觉细节（分区顺序、间距、字号、图标选型、状态、组件样式），规格已锁定的内容（IA、数据结构、验收标准、扩展点）不重复、不更改。

| P0 自检项 | 结果 | 说明 |
| --- | --- | --- |
| 无 emoji 作功能图标 | 通过 | 全部图标来自 Material Icons core 填充风格 + 两枚新增 24dp 单色 VectorDrawable，逐位置指定见 §1.5 |
| 无紫色→粉色渐变 | 通过 | 全项目零渐变；强调色唯一为纸感绿 `primary` |
| 无硬编码颜色 | 通过 | 全部颜色引用 `Theme.kt` 令牌（清单见 §1.2）；唯一例外是新增 VectorDrawable 的 `#FF000000` tint 基底（Material 图标惯例，渲染时被 `Icon` tint 覆盖，非 UI 可见色） |
| 无空洞占位文案 | 通过 | 无 "Welcome to" / "Lorem ipsum"；全部简体中文真实文案，逐条锁定见各页文案表 |
| 无弹跳/弹性缓动 | 通过 | 动效全部 ≤200ms，无 spring / overshoot，见 §9 |
| 触控热区 ≥48dp | 通过 | 逐项见 §10；规划中行刻意不设热区（不可点） |

---

## 1. 全局设计基线

### 1.1 三轴设计刻度（本项目的调盘）

| 参数 | 取值 | 理由 |
| --- | --- | --- |
| DESIGN_VARIANCE | 4 | 产品型寄存器（app UI），列表式熟悉感优先；不做非对称实验 |
| MOTION_INTENSITY | 2 | 仅切换淡入与水波，无装饰动画、无持续微动画 |
| VISUAL_DENSITY | 5 | 日常应用模式：标准 12dp 段距 + 16dp 圆角纸卡，阅读/我的页信息量增加后仍可一屏纵览 |

### 1.2 令牌使用清单（唯一颜色来源：`Theme.kt`）

| 令牌（Theme.kt） | 色值 | 本设计用途 | 出现页 |
| --- | --- | --- | --- |
| `colorScheme.background`（`PaperTokens.Paper`） | #F6F5F0 | 页面底色 | 四页 |
| `colorScheme.surface`（`PaperTokens.Surface`） | #FFFEFA | 纸白卡、底栏、对话框容器 | 四页 |
| `colorScheme.onBackground` / `onSurface`（`PaperTokens.Ink`） | #26332E | 主文本：页头主标题、卡内标题、谚语原文、昵称 | 四页 |
| `paperColors().muted`（`PaperTokens.Muted`） | #7B867E | 三级信息：日期、读音、副信息、SectionLabel、未选标签、小结行口径 | 四页 |
| `paperColors().textStrong`（`PaperTokens.TextStrong`） | #657268 | 副信息：谚语大意、本周小结行、建议行动副文案、规划中徽标文字、中性建议图标 | 四页 |
| `colorScheme.outlineVariant`（`PaperTokens.Line`） | #E7E9E1 | 卡片 1dp 描边、行间 1dp 分隔线、底栏上边框 | 四页 |
| `colorScheme.primary`（`PaperTokens.Green`） | #326151 | 主操作：「换一句」文字、编辑按钮、选中态图标、进度条（沿用） | 主页 / 我的 |
| `colorScheme.onPrimaryContainer`（`PaperTokens.GreenDark`） | #254A3D | 选中态标签文字、首字圆标字、关键数字（沿用） | 底栏 / 我的 |
| `colorScheme.primaryContainer`（`PaperTokens.GreenPale`） | #EDF3EB | 底栏选中 pill 底、模块入口图标盒（动词/形容词/助词）、首字圆标底、阅读说明卡图标盒 | 底栏 / 学习 / 阅读 / 我的 |
| `colorScheme.tertiaryContainer`（`PaperTokens.OrangePale`） | #F8EFE6 | 暖色图标盒：错题本入口、可执行知识点建议 | 学习 / 主页 |
| `colorScheme.onTertiaryContainer`（`PaperTokens.OrangeTextDeep`） | #895936 | 暖色盒内图标 | 学习 / 主页 |
| `colorScheme.tertiary`（`PaperTokens.Orange`） | #B8784F | 错题掌握进度条（沿用，不新增用途） | 我的 |
| `colorScheme.surfaceVariant` | #EEF0EB | 中性图标盒：规划中行图标底、书签空态图标底 | 学习 / 阅读 / 我的 |
| `colorScheme.error`（`PaperTokens.Red`） | #A54E45 | 昵称校验失败：输入框描边 | 我的 |
| `paperColors().textRed`（`PaperTokens.TextRed`） | #9C5B50 | 昵称校验失败：错误文案 | 我的 |

本期不新增用途的既有令牌（预留，禁止为「新鲜感」启用）：`ReadingText` `#839087`、`MeaningText` `#627967`、`StatCorrectText`、`StatIncorrectText`、`RedPale`、`TextGreen`、`LineAccent`、`BgPaleGreen`、`BgPaleStrong`、`LineControl`。

> 对比度注记：`muted`（#7B867E）on `surface` ≈ 4.6:1，达 4.5:1 正文线；11sp（labelSmall）全部文字不低于此比值；图标（非文字）按 ≥3:1 执行。

### 1.3 字体层级（全项目 7 级，LXGW 楷体子集，M3 Typography）

| 层级 | 样式 | 字重 | 用途 |
| --- | --- | --- | --- |
| 页头主标题 | headlineMedium（24sp） | Bold | 今天继续 / 学习 / 阅读 / 我的 |
| 谚语原文 | titleLarge（22sp） | Bold | 谚语卡第一层级 |
| 大数字锚点 | displaySmall（36sp） | Bold | 我的页学习时长（沿用） |
| 卡内标题 / 昵称 | titleMedium（16sp） | Bold | EntryCard 标题、资料卡昵称、说明卡标题、DetailRow 数值 |
| 行标题 | bodyLarge（16sp） | Bold | 建议行、规划中行、明细行标题 |
| 正文 / 大意 | bodyMedium（14sp） | Normal | 谚语大意、阅读说明卡正文 |
| 标签 / 副信息 | labelMedium（12sp） | Normal / Bold（选中） | 日期、SectionLabel、小结行、底栏标签、换一句、编辑 |
| 三级信息 | labelSmall（11sp） | Normal | 读音、EntryCard 副信息、口径注释、徽标、错误文案 |

字距：正文 0；标题 headlineMedium -0.01em；谚语原文 titleLarge -0.01em（大号中文无需额外收紧，楷体避免负字距过度）。字重三级：Normal（正文）/ Bold（强调，本项目既有模式）/ 无 Medium——保持与现有页面一致。

### 1.4 间距 / 圆角 / 阴影

| 项 | 值 | 说明 |
| --- | --- | --- |
| 页面水平内边距 | 16dp | 四页一致（沿用） |
| 页面垂直内边距 | 6dp | 四页一致（沿用） |
| 段间距 | 12dp | Column `spacedBy(12.dp)`（沿用） |
| SectionLabel 偏移 | start 2dp / top 2dp | 与卡左缘形成 2dp 呼吸差（沿用） |
| 卡片圆角 | 16dp | 全项目卡片统一（沿用） |
| 图标盒圆角 | 12dp（40dp 盒）/ 10dp（36dp 盒、周历格） | 沿用既有 |
| 选中 pill / 进度条圆角 | 999dp | 沿用 |
| 徽标圆角 | 6dp | 与 999dp pill 明确区隔：读作「状态标签」不读作「按钮」 |
| 对话框圆角 | 16dp | 偏离 M3 默认 28dp，归入纸卡体系 |
| 卡片阴影 | 2dp | 纸卡三层层次的第二层（沿用） |
| 底栏 | 无阴影，仅 1dp 上边框 | 沿用两标签规格 |

### 1.5 图标系统锁定（逐位置指定，全项目一套）

| 位置 | 图标 | 尺寸 | 来源 |
| --- | --- | --- | --- |
| 底栏 · 主页 | `Icons.Filled.Home` | 24dp | Material Icons core（ImageVector） |
| 底栏 · 学习 | `ic_tab_study`（学士帽） | 24dp | 新增 VectorDrawable（painterResource） |
| 底栏 · 阅读 | `ic_tab_read`（打开的书） | 24dp | 新增 VectorDrawable（painterResource） |
| 底栏 · 我的 | `Icons.Filled.Person` | 24dp | Material Icons core（ImageVector） |
| 谚语卡 · 换一句 | `Icons.Filled.Refresh` | 16dp | core |
| 建议 R1 到期错题 | `Icons.Filled.Refresh` | 20dp | core（与错题本入口同图标，保持「复习=循环」心智） |
| 建议 R2 今日未学 | `Icons.Filled.Create` | 20dp | core |
| 建议 R3 最弱模块 | `Icons.Filled.Warning` | 20dp | core |
| 建议 R4 连续鼓励 | `Icons.Filled.ThumbUp` | 20dp | core |
| 建议 R5 新用户引导 | `Icons.Filled.Star` | 20dp | core |
| 建议 R6 兜底 | `Icons.Filled.CheckCircle` | 20dp | core |
| 学习入口 · 动词 | `Icons.Filled.Create` | 24dp（盒内） | core（沿用） |
| 学习入口 · 形容词 | `Icons.Filled.List` | 24dp（盒内） | core（沿用） |
| 学习入口 · 助词 | `Icons.Filled.Info` | 24dp（盒内） | core（沿用） |
| 学习入口 · 错题本 | `Icons.Filled.Refresh` | 24dp（盒内） | core（沿用） |
| 学习 · 查词（规划中） | `Icons.Filled.Search` | 20dp | core |
| 阅读 · 说明卡 | `Icons.Filled.Info` | 20dp | core |
| 阅读 · AI 阅读（规划中） | `Icons.Filled.Info` | 20dp | core |
| 阅读 · 排版（规划中） | `Icons.Filled.List` | 20dp | core |
| 阅读 · 撰写（规划中） | `Icons.Filled.Create` | 20dp | core |
| 阅读 · 语法单词解析（规划中） | `Icons.Filled.Search` | 20dp | core |
| 我的 · 编辑昵称 | `Icons.Filled.Create` | 16dp | core |
| 我的 · 书签空态 | `Icons.Filled.Star` | 24dp | core |
| 我的 · 模型/API 设置（规划中） | `Icons.Filled.Settings` | 20dp | core |
| 我的 · 发音音色（规划中） | `Icons.Filled.Star` | 20dp | core（core 集内无麦克风/喇叭，Star 为最接近的弱语义，见决策清单 D-17） |
| 我的 · 词库导入导出（规划中） | `Icons.Filled.List` | 20dp | core |
| 行尾箭头（可点击行） | `Icons.Filled.KeyboardArrowRight` | 24dp | core（沿用；规划中行禁止出现） |

同一图标跨页复用允许（如 Create 在动词入口与昵称编辑），但**同一列表内不得重复**（保证一屏内图标可区分）。

**新增 VectorDrawable 规格**（`res/drawable/ic_tab_study.xml` / `ic_tab_read.xml`）：

| 项 | 规格 |
| --- | --- |
| 画布 | 24×24dp viewport（`android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24"`） |
| 填充 | 单色填充路径，`android:fillColor="#FF000000"`（tint 基底，Material 图标惯例；渲染时被 `Icon` 的 tint 覆盖为 `primary` / `muted`） |
| 风格 | 与 Material Icons Filled 视觉重量一致（实心、圆角端），不得混入描边风格 |
| 语义 | 学士帽（学习）/ 打开的书（阅读）；未经设计师确认不得改路径 |

---

## 2. 底部四标签栏（AppBottomBar）

| 项 | 规格 |
| --- | --- |
| 容器高度 | 68dp + `navigationBarsPadding`（底部安全区） |
| 背景 | `colorScheme.surface` + 上边框 1dp `colorScheme.outlineVariant`，无阴影 |
| 标签数 | 固定 4 个，等宽（`weight(1f)`）：主页 / 学习 / 阅读 / 我的 |
| 标签内边距 | 垂直 8dp；水平由等宽自然分摊（360dp 屏每标签 90dp，320dp 屏 80dp，均 ≥48dp 热区） |
| 选中态 | 图标外包 pill：底 `primaryContainer`、圆角 999dp、内距 16×4dp；图标 24dp 着 `primary`；文字 labelMedium Bold `onPrimaryContainer` |
| 未选中态 | 图标 24dp 着 `muted`；文字 labelMedium Normal `muted`；无底色 |
| 文字位置 | 图标下方 4dp，标签 Column 居中 |
| 切换反馈 | 点选即切换；pill/颜色/字重 150ms 内完成；页面内容 200ms 上移淡入（沿用两标签行为） |
| 二级页 | 进入二级页隐藏底栏（既有逻辑不变），返回落在触发它的标签 |

**图标解析（统一在 AppBottomBar 一处）**：`AppTab` 枚举（HOME / STUDY / READ / MINE）在 `AppBottomBar` 内集中解析为 `Painter`：

| 标签 | 解析方式 | 归一路径 |
| --- | --- | --- |
| 主页 | `Icons.Filled.Home`（ImageVector） | `rememberVectorPainter(imageVector)` |
| 学习 | `painterResource(R.drawable.ic_tab_study)` | 直接 Painter |
| 阅读 | `painterResource(R.drawable.ic_tab_read)` | 直接 Painter |
| 我的 | `Icons.Filled.Person`（ImageVector） | `rememberVectorPainter(imageVector)` |

四标签最终都走 `Icon(painter = …, tint = …)` 同一渲染路径；新增第 5 个标签只改 `AppBottomBar` 内的 `when` 分支（规格 §8 扩展点）。

**无障碍**：每个标签项 `Modifier.selectable(selected = …, role = Role.Tab)`，`contentDescription` = 标签文字（「主页」「学习」「阅读」「我的」）；选中态由 TalkBack 朗读「已选中」。底栏不加 `clearAndSetSemantics`，保留系统导航语义。

---

## 3. 主页（`ui/home/HomeScreen.kt` 重写）

### 3.1 分区结构表

| 段 | 区域 | 内容 | 视觉层级 | 令牌 |
| --- | --- | --- | --- | --- |
| ① | 页头 | 日期行 + 主标题「今天继续」（沿用现有） | 第三权重：labelMedium muted + headlineMedium Bold onBackground | muted / onBackground |
| ② | 谚语卡 | 「每日一句」标记 + 原文/读音/大意 + 右上「换一句」 | **第一权重**：纸白卡，原文 22sp Bold | surface / outlineVariant / onSurface / muted / textStrong / primary |
| ③ | 今日概览 | 一周打卡条（七格，今天实心主色）+ 1dp 分隔线 + 「本周 N 题 · 连续 N 天 · 正确率 X%」 | 第二权重：纸白卡，**沿用现有实现不改动** | surface / outlineVariant / primary / primaryContainer / onPrimary / onPrimaryContainer / textStrong / muted |
| ④ | 知识点建议 | SectionLabel「知识点建议」+ 一张卡内 ≤3 行建议 | 第二权重：纸白卡，行间 1dp 分隔 | surface / outlineVariant / tertiaryContainer + onTertiaryContainer（可执行）/ surfaceVariant + textStrong（鼓励）/ muted / textStrong |

段间距 12dp；SectionLabel（段④）上 2dp 左 2dp。主页只保留**一个**大数字锚点议题：本周小结行（段③）；谚语卡是「内容锚点」而非数字锚点，二者不冲突。

### 3.2 谚语卡详规

| 项 | 规格 |
| --- | --- |
| 容器 | Surface 16dp 圆角 + 1dp `outlineVariant` 描边 + 2dp 阴影，底色 `surface` |
| 内边距 | 16dp 四向 |
| 顶行 | 左：「每日一句」labelSmall `muted`；右：「换一句」按钮（见下），两端对齐 |
| 原文 | titleLarge（22sp）Bold `onSurface`，与顶行间距 10dp |
| 读音 | labelSmall（11sp）`muted`，与原文间距 6dp（字色遵循规格 §5.1，不引入 reading 令牌） |
| 大意 | bodyMedium（14sp）`textStrong`，与读音间距 8dp |
| 长文本 | 谚语文案短（内置 24 条），一律允许换行，卡高自适应；不截断、不省略号 |

**「换一句」按钮**：无边框 TextButton 形态——`Icons.Filled.Refresh` 16dp + 文字「换一句」labelMedium，整体着色 `primary`；图标与文字间距 6dp；`Modifier.defaultMinSize(minHeight = 48.dp)` 保证热区；点击系统水波 150ms。语义 `contentDescription = "换一句"`（图标本身 null，文字已表达）。

**交互**：进入主页随机一条；点「换一句」150ms 淡入新内容（不连续重复由 `ProverbPicker.nextIndex` 保证）；不持久化选中。

### 3.3 今日概览（段③）

周历条七格 34×34dp、今天实心 `primary`、有答题日用 `primaryContainer`、未来日「–」、1dp 分隔线、小结行 labelMedium `textStrong`——**全部沿用现有 `HomeScreen.kt` 实现，仅位置从段②变为段③（谚语卡之下），不改一行样式**。

### 3.4 知识点建议详规（段④）

| 项 | 规格 |
| --- | --- |
| 容器 | 一张 Surface 纸白卡（16dp 圆角 / 1dp 边 / 2dp 阴影），最多 3 行，行间 1dp `outlineVariant` 分隔 |
| 行布局 | padding 15×13dp：左 36dp 圆角 10dp 图标盒（间距 13dp）→ 中标题+副文案 Column（weight 1f）→ 右箭头（仅可执行项） |
| 图标尺寸 | 20dp |
| 可执行项色调（暖色） | 图标盒底 `tertiaryContainer`，图标 `onTertiaryContainer` |
| 鼓励项色调（中性） | 图标盒底 `surfaceVariant`，图标 `textStrong` |
| 标题 | bodyMedium（14sp）Normal `onSurface`（建议文案本身是句子，不用 Bold，避免与 EntryCard 标题抢层级） |
| 副文案 | labelSmall `muted`，仅可执行项渲染（动作去向）；鼓励项无副文案 |
| 箭头 | `Icons.Filled.KeyboardArrowRight` 24dp `muted`，仅可执行项 |
| 点击 | 可执行项整行 `clickable` + 系统水波；鼓励项**无 clickable、无水波** |
| 空态 | 引擎返回空列表（R6 兜底下不应发生）：隐藏整节（SectionLabel + 卡），不留空壳 |

**建议规则 → 图标 / 色调 / 文案 / 动作对照表**（文案为最终字符串，N/X 为运行时值）：

| 规则 | 图标 | 色调 | 主文案 | 副文案 | 动作 |
| --- | --- | --- | --- | --- | --- |
| R1 到期错题 | Refresh | 暖色 | 「有 N 道错题今天到期，复习一遍更牢」 | 「进入错题本」 | 直达错题本二级页（返回落主页） |
| R2 今日未学 | Create | 暖色 | 「今天还没练习，去学习页开始一组吧」 | 「切换到学习页」 | 切到「学习」标签 |
| R3 补最弱模块 | Warning | 暖色 | 「X 掌握度最低（N%），优先补这里」 | 「进入X练习」（X=动词/形容词；助词时「进入助词学习」） | 直达对应二级页 |
| R4 连续鼓励 | ThumbUp | 中性 | 「已连续学习 N 天，保持节奏」 | 无 | 无（不可点） |
| R5 新用户引导 | Star | 暖色 | 「从动词练习开始，先认识 44 个基础词条」 | 「进入动词练习」 | 直达动词练习 |
| R6 兜底 | CheckCircle | 中性 | 「今天已练 N 题，状态不错」 | 无 | 无（不可点） |

> 图标随规则固定（R1–R6 一一对应），用户第二次进主页即可凭图标识别建议类型；规则扩充时同步新增图标并更新本表。

---

## 4. 学习页（`ui/study/StudyScreen.kt` 新增）

### 4.1 分区结构表

| 段 | 区域 | 内容 | 视觉层级 | 令牌 |
| --- | --- | --- | --- | --- |
| ① | 页头 | 日期行 + 主标题「学习」+ 副标题「按模块练习，错题会按间隔复习」 | 第三权重：muted 日期 / Bold 标题 / labelMedium muted 副标题 | muted / onBackground |
| ② | 练习模块 | SectionLabel「练习模块」+ 四个 EntryCard | **第一权重**：大卡、热区 ≥64dp、右箭头 | surface / outlineVariant / primaryContainer + onPrimaryContainer / tertiaryContainer + onTertiaryContainer / muted |
| ③ | 更多工具 | SectionLabel「更多工具」+ 查词规划中行 | 第四权重：中性、不可点 | surface / outlineVariant / surfaceVariant / textStrong / muted |

副标题与主标题间距 2dp；「学习」二字标题与主页「今天继续」同为 headlineMedium Bold，保持四页页头一致律。

### 4.2 模块入口卡（EntryCard，迁入 `ui/common/ModuleUi.kt`）

**样式与副信息口径与原主页完全一致，一行不改**：

| 卡 | 图标 | 图标盒 | 副信息口径 |
| --- | --- | --- | --- |
| 动词练习 | Create | primaryContainer / onPrimaryContainer | 「44 个词条 · 已练 N · 掌握度 X%」 |
| 形容词练习 | List | primaryContainer / onPrimaryContainer | 「15 个词条 · 已练 N · 掌握度 X%」 |
| 助词学习 | Info | primaryContainer / onPrimaryContainer | 「9 个专题 · 已掌握 X% · 错题 N 道」 |
| 错题本 | Refresh | tertiaryContainer / onTertiaryContainer | 沿用现有分支文案（暂无错题 / N 道待复习 · N 道今天到期 · 助词 N 道） |

卡规格（沿用）：16dp 圆角、1dp `outlineVariant`、2dp 阴影、padding 14×15dp、40dp 图标盒 12dp 圆角、标题 titleMedium Bold `onSurface`、副信息 labelSmall `muted`、右箭头 KeyboardArrowRight `muted`。点击进二级页（行为与改前一致，返回落「学习」标签）。

### 4.3 查词预留行（段③）

`PlannedRow` 共用组件（样式见 §7.2）：图标 `Icons.Filled.Search` 20dp、标题「查词」、描述「查日语单词的读音、释义和用法」、徽标「规划中」。不可点击、无箭头、无水波。

---

## 5. 阅读页（`ui/read/ReadScreen.kt` 新增）

### 5.1 分区结构表

| 段 | 区域 | 内容 | 视觉层级 | 令牌 |
| --- | --- | --- | --- | --- |
| ① | 页头 | 日期行 + 主标题「阅读」 | 第三权重 | muted / onBackground |
| ② | 框架说明卡 | Info 图标盒 + 标题「阅读工具」+ 说明段 | 第二权重：纸白卡，全页唯一「可读长文」 | surface / outlineVariant / primaryContainer + onPrimaryContainer / textStrong |
| ③ | 工具列表 | 一张卡内 4 个规划中工具位，行间 1dp 分隔 | 第四权重：中性、不可点 | surface / outlineVariant / surfaceVariant / textStrong / muted |

### 5.2 框架说明卡（段②）

| 项 | 规格 |
| --- | --- |
| 容器 | Surface 16dp 圆角 + 1dp `outlineVariant` + 2dp 阴影（与全页卡片同式样，不另造层级） |
| 布局 | Row：36dp 圆角 10dp 图标盒（底 `primaryContainer`，Info 20dp `onPrimaryContainer`）→ Column（间距 13dp） |
| 标题 | titleMedium Bold `onSurface`：「阅读工具」 |
| 正文 | bodyMedium `textStrong`：「这里将提供 AI 辅助的日语文章阅读与写作工具。联网能力与模型配置就绪后逐个开放，模型配置入口会先出现在「我的」页。」 |
| 内边距 | 16dp 四向 |

### 5.3 工具列表（段③，数据驱动 `ReadTool` 清单）

四个 `PlannedRow`（样式见 §7.2），同一张卡、1dp 分隔；每项 = 图标 + 名称 + 一句话描述 + 「规划中」徽标：

| 工具 | 图标 | 描述（最终文案） |
| --- | --- | --- |
| AI 阅读 | Info | 「贴入日语文章，边读边查词句」 |
| 排版 | List | 「调整字体、行距与注音的显示方式」 |
| 撰写 | Create | 「写日语短文，实时检查语法与用词」 |
| 语法单词解析 | Search | 「解析句子结构，标注单词读音与词性」 |

新增工具 = `ReadTool` 加一条数据 + 状态翻 `AVAILABLE` + 补路由（规格 §8）；翻 `AVAILABLE` 后该行转为可点击行（加箭头 + 水波 + 移除徽标），组件形态同 EntryCard 行。

---

## 6. 我的页（`ui/mine/MineScreen.kt` 扩展）

### 6.1 分区结构表

| 段 | 区域 | 内容 | 视觉层级 | 令牌 |
| --- | --- | --- | --- | --- |
| ① | 资料卡 | 首字圆标 + 昵称 + 副行 + 「编辑」 | 新增区块，第二权重 | surface / outlineVariant / primaryContainer + onPrimaryContainer / onSurface / muted / primary |
| ② | 学习统计 | 时长/任务紧凑条 + 学习进度 + 今日明细 | **第一权重**：时长大数字锚点（沿用现有，整体不动） | surface / outlineVariant / onPrimaryContainer / primary / tertiary / textStrong / muted |
| ③ | 书签收藏夹 | SectionLabel（右置计数）+ 空态卡 | 第四权重：quiet 呈现 | surface / outlineVariant / surfaceVariant / textStrong / muted |
| ④ | 更多设置 | SectionLabel「更多设置」+ 3 个规划中行 | 第四权重：中性、不可点 | surface / outlineVariant / surfaceVariant / textStrong / muted |
| ⑤ | 统计清零 | TextButton + 确认弹窗 | 第五权重（沿用现有，不动） | primary |

### 6.2 资料卡详规（段①）

| 项 | 规格 |
| --- | --- |
| 容器 | Surface 16dp 圆角 + 1dp `outlineVariant` + 2dp 阴影，padding 16dp |
| 首字圆标 | 48dp 正圆（RoundedCornerShape 999dp），底 `primaryContainer`，字 = 昵称首字（titleMedium Bold `onPrimaryContainer`），居中 |
| 昵称 | titleMedium Bold `onSurface`，与圆标间距 14dp |
| 副行 | labelSmall `muted`：「本地资料，仅存本机」，与昵称间距 3dp |
| 编辑按钮 | 无边框 TextButton：Create 16dp + 「编辑」labelMedium，着色 `primary`，`defaultMinSize(minHeight = 48.dp)` |
| 缺省值 | 昵称「日语学习者」→ 首字「日」 |

**昵称编辑对话框**（AlertDialog，容器 `surface`、圆角 16dp）：

| 状态 | 表现 |
| --- | --- |
| 缺省态 | 打开即预填当前昵称；输入框 label「昵称」、placeholder「日语学习者」；不自动聚焦（不主动弹键盘，避免遮挡） |
| 编辑态 | 单行输入，实时反映；不做输入截断（校验放在保存时，符合规格「非法拒绝并保持原值」） |
| 校验失败态 | 点「保存」时 trim 后为空 → 错误文案「昵称不能为空」；长度 >12 → 「昵称长度不能超过 12 个字」；输入框 `isError = true`（描边 `error`）；错误文案 labelSmall `textRed` 置于输入框下方 4dp（错误近字段）；**对话框不关闭、输入不清空、原值不变** |
| 保存成功 | 合法值写入并持久化，关闭对话框，资料卡即时更新；不弹 toast（状态已可见） |

按钮：「保存」（Button，`primary` 底 / `onPrimary` 字，defaultMinSize 48dp）+「取消」（TextButton）。对话框标题「编辑昵称」titleLarge？——用 M3 AlertDialog 默认 headlineSmall 即可，文案「编辑昵称」。

### 6.3 书签收藏夹（段③）

| 项 | 规格 |
| --- | --- |
| SectionLabel | 左「书签收藏夹」labelMedium `muted`；右 caption「N 条」labelSmall `muted`（N = `bookmarks.items.size`，本期恒 0） |
| 空态卡 | Surface 纸白卡，居中 Column：48dp 正圆 `surfaceVariant` + Star 24dp `textStrong`；下方 10dp 文案 labelMedium `muted`「还没有收藏。阅读功能上线后，可收藏句子与单词」；padding 20×16dp |
| 防御态 | 存储损坏 → 计数回退 0 + 空态（`lastLoadInvalid` 置位，不崩溃） |
| Edge | N > 99 显示「99+ 条」；N > 0 的列表形态本期不实现（无收藏入口），仅计数与空态文案随 N 切换 |

### 6.4 预留设置位（段④）

SectionLabel「更多设置」+ 一张卡内 3 个 `PlannedRow`（样式见 §7.2）：

| 设置项 | 图标 | 描述（最终文案） |
| --- | --- | --- |
| 模型/API 设置 | Settings | 「配置阅读工具使用的模型与服务地址」 |
| 发音音色 | Star | 「选择单词与句子的朗读音色」 |
| 词库导入导出 | List | 「导入或导出本地词库数据」 |

### 6.5 统计清零（段⑤）

TextButton + AlertDialog 确认（文案与行为）**沿用现有实现，不动**。

---

## 7. 共用组件详规（新增于 `ui/common/ModuleUi.kt`）

### 7.1 「规划中」徽标（PlannedBadge）

| 项 | 规格 |
| --- | --- |
| 文字 | 「规划中」 |
| 形状 | 圆角矩形 6dp（**非 pill**，与 999dp 选中 pill 明确区隔） |
| 底色 | `colorScheme.surface`（继承所在卡底，不另起色块） |
| 描边 | 1dp `colorScheme.outlineVariant` |
| 文字 | labelSmall（11sp）`paperColors().textStrong`，字重 Normal |
| 内边距 | 水平 7dp × 垂直 2dp |
| 对比度 | textStrong on surface ≈ 5.2:1，达标 |
| 位置 | 行内右端，垂直居中；不与箭头共存（规划中行永无箭头） |

### 7.2 规划中信息行（PlannedRow）

| 项 | 规格 |
| --- | --- |
| 签名 | `PlannedRow(icon: ImageVector, title: String, description: String)` |
| 布局 | padding 15×13dp：36dp 圆角 10dp 图标盒（底 `surfaceVariant`，图标 20dp `textStrong`，间距 13dp）→ Column（标题 + 描述，weight 1f）→ PlannedBadge |
| 标题 | bodyLarge（16sp）Bold `onSurface` |
| 描述 | labelSmall `muted`，与标题间距 3dp |
| 交互 | **不包 `clickable`、无水波（indication = null）、无箭头、不设 role=Button** |
| 语义 | 不做整行语义合并；TalkBack 按子节点顺序朗读「标题 / 描述 / 规划中」，诚实且不误报可点 |
| 唯一态 | 规划中态即唯一态；落地时由数据状态翻 `AVAILABLE` 转为可点击行（加箭头 + 水波 + 去徽标），组件不改结构 |

### 7.3 SectionLabel（沿用）

既有模式：labelMedium `muted`，start 2dp / top 2dp；本设计新增「右 caption」用法（书签计数），参数化 `caption: String? = null`（`ModuleSectionLabel` 已有同形能力，可复用或对齐样式）。

---

## 8. 组件状态矩阵（5 态）

| 组件 | Loading | Empty | Error | Populated | Edge |
| --- | --- | --- | --- | --- | --- |
| 谚语卡 | 不适用（本地同步数据，无异步） | 不可能（内置 24 条；数据异常回退首条） | 不适用 | 原文/读音/大意齐全 | 长句换行，卡高自适应 |
| 今日概览 | 不适用 | 全周 0 题：今日格 0、未来格「–」 | 不适用 | 周历条 + 小结行（沿用） | 历史仅 14 天上限裁剪（沿用） |
| 知识点建议 | 不适用 | 引擎返回空 → 隐藏整节 | 不适用 | 1–3 行（R6 兜底保证 ≥1） | 恒 ≤3（优先级截断） |
| 资料卡 | 不适用 | 无昵称 → 缺省「日语学习者」/ 首字「日」 | 存储损坏 → 回退缺省 + invalid 标记 | 昵称 + 编辑入口 | 昵称 12 字上限，保存拒绝 |
| 昵称对话框 | 不适用 | 输入为空（校验失败态） | 校验失败：错误文案近字段 + isError | 预填当前值 | 12 字上限；首字为非中日字符时原样显示 |
| 书签收藏夹 | 不适用 | 计数 0 + 空态文案（本期唯一可见态） | 存储损坏 → 空态 + invalid | 预留（阅读上线后） | 计数 >99 → 「99+ 条」 |
| 规划中行 | 不适用 | 不适用 | 不适用 | 不适用（唯一定态） | 不适用 |
| 底栏 | 不适用 | 不适用 | 不适用 | 4 标签 | 第 5 标签按规格 §8 扩展点预留 |

---

## 9. 动效规格

| 场景 | 时长 | 曲线 | 说明 |
| --- | --- | --- | --- |
| 底栏切换页面 | 200ms | 上移淡入（沿用两标签） | 四页各自保留滚动位置与状态 |
| 底栏选中 pill/颜色/字重 | 150ms | 标准缓动 | 仅颜色与字重过渡 |
| 「换一句」内容刷新 | 150ms | alpha 0→1 淡入 | 无位移、无缩放 |
| 可点击行点击 | 系统水波 150ms | Material 默认 | EntryCard / 建议行 / 编辑 / 保存 |
| 对话框进出 | 200ms | M3 默认淡入 | 无缩放弹跳 |
| 规划中行 | 无 | — | 不可点，无任何按压反馈 |

全部 ≤200ms（除页面切换 200ms 收敛值）、无 spring/overshoot/循环动画——天然满足 `prefers-reduced-motion` 精神（Compose 侧无 CSS 媒体查询，以「短促 + 无弹性 + 无循环」等价实现）。

---

## 10. 无障碍规格

| 项 | 规格 |
| --- | --- |
| 触控热区 | 底栏标签 68dp 高 × ≥80dp 宽；「换一句」48dp；「编辑」48dp；EntryCard 行 ≥64dp；对话框按钮 48dp（`defaultMinSize`）；统计清零 48dp |
| 规划中行 | **不设热区、不设 clickable、无水波**——不可点击就不给可点击的 affordance（视觉与语义双重诚实） |
| contentDescription | 底栏四标签 = 标签文字；行内装饰图标（EntryCard/规划中行/书签空态/换一句）均为 null（相邻文字已表达）；首字圆标由文本自然朗读 |
| 焦点顺序 | 页内自上而下；对话框打开后焦点困于对话框内（M3 默认）；校验失败时错误文案可被聚焦朗读 |
| 字号 | 全项目最小 11sp（labelSmall），sp 单位随系统缩放 |
| 对比度 | 正文 ≥4.5:1（最低点 muted on surface ≈4.6:1）；图标 ≥3:1；徽标 5.2:1 |
| 颜色不是唯一信号 | 选中态 = 底色 + 颜色 + 字重三重表达；可执行/鼓励 = 色调 + 箭头 + 副文案三重区分；规划中 = 徽标文字明示 |

---

## 11. 设计决策清单

| # | 决策 | 一句话理由 |
| --- | --- | --- |
| D-01 | 谚语卡置于页头之下、概览之上 | 主页转型为「每日第一屏」，谚语是打开理由，统计退居第二锚点 |
| D-02 | 谚语读音用 labelSmall + `muted` | 规格 §5.1 已锁字号字色，不另造 reading 色，避免同语义两套色 |
| D-03 | 谚语大意用 bodyMedium + `textStrong` | 介于 ink 与 muted 之间的既有副信息令牌，三级层级不引入新色 |
| D-04 | 「换一句」用无边框 TextButton + Refresh 16dp | 次级操作不抢谚语视觉，图标+文字双编码，48dp 热区 |
| D-05 | 可执行建议用 tertiary 系、鼓励用 surfaceVariant + textStrong | 沿用「需要行动的用暖色、纯展示用中性」通则（两标签规格 §5） |
| D-06 | R1–R6 各配一枚固定 core 图标 | 凭图标即可复认建议类型，规则与图标一一对应防漂移 |
| D-07 | 建议区收进一张卡 + 1dp 分隔 | 与「今日明细」同构，避免主页第三张并列卡造成的卡片疲劳 |
| D-08 | 选中 pill 只包图标（16×4dp 内距） | 四等分标签下为文字留足宽度，同时保留「底色+颜色+字重」三重选中表达 |
| D-09 | 学习/阅读图标经 painterResource 在 AppBottomBar 单点归一为 Painter | ImageVector 用 rememberVectorPainter 转换，四标签同一渲染路径，扩展只改一处 |
| D-10 | 新增两枚 24dp 单色填充 VectorDrawable | 与 Material Icons Filled 同视觉重量，tint 可着，24dp 与 core 图标一致 |
| D-11 | 徽标用 6dp 圆角 + surface 底 + 1dp 边 + textStrong 字 | 与 999dp 选中 pill 区隔，读作状态标签而非按钮；5.2:1 对比达标 |
| D-12 | 规划中行不包 clickable、无水波、无箭头 | 交互层诚实：读屏不报可点、视觉无欺骗，符合规格 §4.4 |
| D-13 | 昵称对话框圆角 16dp | 偏离 M3 默认 28dp，归入纸卡圆角体系（卡片上限 16dp 红线） |
| D-14 | 校验失败文案贴输入框下方、不清空、不关对话框 | 错误近字段；符合规格「非法拒绝并保持原值」 |
| D-15 | 首字圆标复用 primaryContainer 色系 | 资料卡不引入新色，与选中态同族 |
| D-16 | 书签空态 Star + surfaceVariant 底 | 收藏语义预留，quiet 呈现不抢统计区 |
| D-17 | 发音音色用 Star（core 集内弱语义） | core 图标集无麦克风/喇叭；待引入 extended 图标库后可换 `Mic`，属已知妥协 |
| D-18 | 三轴刻度 VARIANCE 4 / MOTION 2 / DENSITY 5 | 产品型寄存器：熟悉感优先、动效收敛、日常密度 |

---

## 附：实现取用速查

| 前端要什么 | 去哪取 |
| --- | --- |
| 四页分区顺序与间距 | §3.1 / §4.1 / §5.1 / §6.1 |
| 谚语卡与换一句 | §3.2 |
| 建议规则文案/图标/色调 | §3.4 对照表 |
| 学习入口卡副信息口径 | §4.2 |
| 阅读工具清单与描述 | §5.3 |
| 资料卡与对话框三态 | §6.2 |
| 书签空态 | §6.3 |
| 规划中徽标/信息行 | §7.1 / §7.2 |
| 底栏与图标解析 | §2 |
| 动效与无障碍 | §9 / §10 |
