# 方案 B · 安卓端全量液态玻璃重构计划

> 2026-11-07 建立 · 2026-11-08 更新 · 状态：**待执行**（本文件是计划，不含实现代码）
> 决策依据：关子已拍板「必须采用方案 B，仅以方案 B 为唯一依据」。
> 唯一视觉基准：`docs/design/liquid-glass/option-b.html`（`glass` 档 / 糖果卡）+ 同文件墨夜态。原型里的三档材质切换是**预览工具，不移植进产品**。
> 关联：`docs/android-liquid-glass-assessment.md`（调研评估）、`docs/design/liquid-glass/index.html`（三方案对照，A/C **不再是候选项**）。
> **Q-B1 / Q-B2 / Q-B3 已确认（2026-11-08）**：工具链允许全面升级；App 内不要手动开关、用户可见风格只要液态玻璃；性能阈值按现代手机性能定，见 §4.3。
> **最高约束（2026-11-08 关子重申）**：「最终的结果要做到跟方案 B 一样」。据此 §0.6 定为 `minSdk 33`、产品**只呈现 `glass` 一种材质**、不设任何可见降级——所有受支持设备结果一致，不再保留「低版本换实色」的例外。

---

## 0. 基准冻结与防偏离契约

### 0.1 冻结规则

| 项 | 规则 |
| --- | --- |
| Golden 文件 | `docs/design/liquid-glass/option-b.html`、`src/theme-b.css`、`src/engine.js`（场景与状态定义） |
| Golden 版本 | **v2 · 2026-11-08**：底栏改四角同 r 悬浮岛（三种浮法可切）、圆角收敛为单值 24、弹层四角同 r + 离屏 12。v1（全宽玻璃条 + 28/18/32/14/999）废止 |
| 冻结期 | 重构启动至 AC-01 通过前，**不得修改**这三个文件 |
| 规格机器化 | R0.0 从 `theme-b.css` 提取令牌生成 `spec-b.json`，作为自动比对基准 |
| 视觉变更 | 任何一处视觉要改，必须先改 Golden → 关子确认 → 再改实现；禁止「实现先行、原型补记」 |
| 一致性口径 | 浏览器与 Android 无法逐像素对齐（渲染管线不同）。判定 = **§0.2 规格逐项核对 + §0.3 禁止清单零命中 + 双皮肤 × 9 视图 `glass` 档截图人工走查**，三者同时通过；产品无第二套材质可对照 |

### 0.2 方案 B 规格基线（逐值摘自 `theme-b.css`，实现必须同值）

| 类别 | 令牌 | 浅色（糖果卡） | 深色（墨夜） | Android 落点 |
| --- | --- | --- | --- | --- |
| 页面底 | `--screen-texture` | 3 处 radial 光斑（16%/14% 青绿 .24、88%/40% 蓝 .22、28%/88% 绿 .20）+ `linear-gradient(165deg,#EAF2F4,#DFEBEE)` | 同位置冷蓝光斑 + `linear-gradient(165deg,#0C1720,#081016)` | 根 backdrop 的背景绘制层 |
| 卡片表面 | `--surface` / `--glass-surface` | `rgba(255,255,255,.58)` / `.55` | `rgba(24,40,50,.58)` / `.55` | 玻璃面板 surface alpha |
| 描边 | `--line` / `--glass-line` | `rgba(255,255,255,.68)` / `.82` | `rgba(150,200,215,.20)` / `.28` | 1dp 高光边 |
| 主色 | `--brand` | `#14857F` | `#7FD3C7` | `ColorScheme.primary` / 模块 tone |
| 文本 | `--text` / `-2` / `-3` | `#143036` / `#3D5A60` / `#6D8A8E` | `#E8F3F5` / `#AFC6CD` / `#7E9AA3` | `Palette` |
| 语义色 | ok / warn / danger | `#14857F` / `#A8761B` / `#B4514A` | `#7FD3C7` / `#E0BE79` / `#E79B92` | 判定与状态反馈 |
| 圆角 | `--radius` **单值 24dp**；`--radius-card/-btn/-sheet/-glyph/-pill` 全部 `var(--radius)` | **同一个 24，不随皮肤/组件变** | 单套 `GlassGeometry.radius = 24.dp`，**不得存在第二个半径常量**；短边 < 48dp 的元素由形状自身裁切（几何事实，不是第二套值，见 §0.2.1） |
| 底栏 | **悬浮岛**：左右与底部各离屏 **16dp**、四角 24dp、`padding 8/10`、1dp 玻璃边 + 外投影 + 内高光；tab 高 50dp、glyph 26dp | 同形，仅配色/阴影不同 | `AppBottomBar`；浮法见 §0.2.1（覆盖/占位/收窄三选一，**待关子选定**） |
| 弹层 | 四角 24dp + **三边离屏 12dp**（浮起，不贴底）、1dp 玻璃边 + `0 -14 40` 投影 + 2px 高光条 | 同形 | `ModalBottomSheet` shape = 四角 24；`windowInsets` 留 12dp |
| 阴影 | card | `0 10 30 rgba(20,70,72,.10)` + `inset 0 1 0 rgba(255,255,255,.75)` | `0 10 30 rgba(0,0,0,.28)` + `inset 0 1 0 rgba(180,220,230,.16)` | 玻璃层阴影/高光 |
| 阴影 | sheet / dock | `0 -14 40 rgba(16,60,66,.18)` / `0 -8 26 rgba(16,60,66,.12)` | `0 -14 40 rgba(0,0,0,.42)` / `0 -8 26 rgba(0,0,0,.32)` | 弹层与判定抽屉 |
| 间距 | page / card / gap / section | **22 / 18 / 16 / 16 dp** | 同值 | `GlassSpacing` |
| 字号 | display / title / body / caption / label / micro / jp / number | **26 / 16 / 14 / 12 / 11 / 10 / 22 / 24 sp** | 同值 | `GlassTypography`；行高 1.75、标题字重 600 |
| 材质 glass | `blur(16px) saturate(1.6)` + 内高光 + 150° 边缘渐变 + sheet 2px 高光条 + 顶栏 blur(12px) | 同链路，高光换冷蓝 | `vibrancy() → blur() → lens()`，参数 P0 校准 |
| 材质 blur（**仅原型预览**） | `blur(10px)`、半透明、无高光/折射/饱和提升、card 阴影 none | 同 | **产品不实现**，保留在 Golden 仅供对照，不进 `GlassTokens` |
| 材质 solid（**仅原型预览**） | 实色 `#F7FBFB`（深 `#14222C`）、无 backdrop-filter、无阴影、无高光层 | `#14222C` | **产品不实现**，保留在 Golden 仅供对照，不进 `GlassTokens` |
| 交互 | hover 浮起 3px + 阴影增强；active `scale(.98)`；选中 `inset` 光晕；**无弹跳曲线** | 同 | 光感反馈，`.2s ease` |
| 状态反馈 | 玻璃 Toast（24dp 圆角、字重 500）；状态条玻璃 + 「完成 · / 失败 ·」文字前缀 | 同 | 颜色与文字双通道 |

#### 0.2.1 悬浮岛与统一圆角（2026-11-08 关子追加要求，已落进 Golden）

**一、全局圆角单值 24dp，无例外**

| 对象 | 规格 | 说明 |
| --- | --- | --- |
| 卡片 / 分组容器 / 列表卡 | 24 | 原 28，收 4 |
| 按钮（主/次/幽灵/小号）、筛选胶囊、输入框 | 24 | 原 18 / 999，统一到 24 |
| 弹层、对话框、Toast | 24 | 原 32 / 18 |
| 图标底板（38dp）/ tab 图标底板（26dp）/ badge（20dp） | 24 | 原 14 / 999 |
| 图片、缩略图、书签封面 | 24 | 新增口径，随卡片同值 |
| 底栏岛、页内吸底判定条、抽屉 | 24 | 原底栏顶圆角 24 + 底部 0 |

- **只有一个半径常量**：`GlassGeometry.radius = 24.dp`，其余别名同源；静态守卫要求 `RoundedCornerShape(<数字>.dp)` 在 `ui/` 下 0 处命中，形状一律经 `GlassShape.of()` 取。
- **短边裁切的几何事实（实测，不是例外）**：半径大于元素短边一半时，形状由自身裁到上限。44dp 高按钮渲染为 22（完全胶囊）、36dp 小按钮为 18、20dp badge 为 10（胶囊）。**这条副作用已实测并上报关子**：若要求「看起来同一种圆角」，需把 r 收到 16 或把按钮加高到 ≥52dp；Golden 控制条支持 24 / 20 / 16 一键对比，最终值以关子确认为准。
- **功能性圆形不参与收敛**（关子已拍板）：头像、状态圆点、开关滑块、环形进度保持真圆 —— 圆形是形状语言，不是圆角数值。除此之外没有豁免项。

**二、底栏改悬浮岛**

| 项 | 规格 |
| --- | --- |
| 脱离屏幕 | 左 / 右 / 底各 **16dp**；岛宽 = 屏宽 − 32dp（表宽含 1dp 玻璃边） |
| 形状 | 四角同为 24dp，取消「顶圆角 + 底部直角」 |
| 材质 | 与卡片同一条玻璃链路（`vibrancy → blur → lens`）+ 1dp 玻璃边 + 外投影 `0 12 30 rgba(20,70,72,.16)` + 内高光 1dp |
| 内部 | `padding 8/10`，tab 高 50dp、glyph 26dp、标签 `--fs-micro`；选中态保留 `selectable` + `semantics { selected }` + `themedTone()` |
| 图标 | **矢量描边图标**（24 viewBox，`stroke-width 1.75`，圆角端点，渲染 20dp 于 26dp 槽位），**无底板**，颜色一律 `currentColor` 继承 tab 文字色（未选中 `--text-3` / 选中 `--brand` / 墨夜自动换 `#7FD3C7`）；源文件 `docs/design/liquid-glass/src/icons/tab-{home,study,read,mine}.svg`，由 `build.py` 内联进 Golden（内联才能继承 `currentColor`，且零额外请求） |
| 内容避让 | 覆盖/收窄浮法下页面底部额外留白 **112dp**，保证滚到底时最后一项不被岛遮住 |
| 两皮肤 | 同形，只换 `--glass-surface` / 高光色 |

**三种浮法（关子要求全部做出后再选，Golden 控制条可切）**：

| 浮法 | 实现 | 优点 | 代价 |
| --- | --- | --- | --- |
| **覆盖**（当前默认） | 岛绝对定位贴底，内容从岛后方穿过并被折射 | 玻璃压内容，折射与模糊最可见，最像液态玻璃 | 底部内容被遮一截，需 112dp 留白补偿；滚动中岛下始终有内容变化 |
| 占位 | 岛留在布局流内，内容止于岛上方 | 零遮挡、最稳、实现最简单 | 岛下方无内容，玻璃只剩背景光斑，折射戏份最少 |
| 收窄 | 宽度收到 4 个 tab 自然宽（Golden 实测 280dp）并水平居中，`max-width = 100% − 32dp` 兜底 | 最「岛」，独立浮起感最强 | 可用宽度变窄、tab 更挤；4 个以上 tab 会撑破 |

> **与 §0.3「禁止悬浮药丸底栏」的关系**：禁的是 **A 的浅色实色胶囊 + 墨夜平铺两态**（高度小、宽度随内容、皮肤不同形）；B 的悬浮岛是**满宽减边距 + 四角同 r + 玻璃折射 + 双皮肤同形**。两者判定口径见下表，守卫按「岛宽 = 屏宽 − 32」而非「是否悬浮」断言。

| 判定项 | A（禁止） | B（必须） |
| --- | --- | --- |
| 宽度 | 随内容收缩成胶囊 | 满宽 − 32dp |
| 材质 | 浅色实色 / 墨夜平铺线框 | 玻璃 + 折射，双皮肤同形 |
| 圆角 | 999 胶囊（顶角） | 四角 24 |
| 双皮肤 | 浅悬浮 / 深平铺，两套形态 | 同一实现，只换配色 |

### 0.3 A / C 禁止清单（静态守卫 + 人工走查，零命中才算通过）

| 来源 | 禁止出现的特征 |
| --- | --- |
| 方案 A | 实色纸卡（`surface` 不透明）、**悬浮药丸底栏**（宽度随内容的实色胶囊 / 墨夜平铺两态，口径见 §0.2.1 判定表）、`navFloating` 分支、20/13dp 卡片圆角、墨夜零阴影靠 1dp 细线分层、只在底栏/弹层局部玻璃 |
| 方案 C | 4dp 直角、方形控件/胶囊、左侧色条选中、纵向线框行表、平铺带上分隔线底栏、`[ 正确 ]` / `提示 ·` / `状态 ·` 文字化前缀、零阴影、紧凑 12/10/8 间距 |
| 旧规范残留 | `ModuleGeometry.cardRadius=20/13`、`cardShadow=3/0`、`navFloating=true/false`、`CandyGeometry`/`InkNightGeometry` 双套几何、`HorizontalDivider` 分隔底栏、`#2E9463` / `#9FBBD3` / `#FAFAF8` / `#0F1114` 旧主题色 |

### 0.4 四条不可协商的约束

1. **结果一致优先**：所有受支持设备、所有页面/弹层/组件、双皮肤、任何运行状态，呈现的都是同一套 `glass` 效果。产品内**不实现 `blur` / `solid` 档、不做 `SDK_INT` 材质分支、不做性能降档**；`minSdk 33` 是这条约束的必要条件（§0.6）。
2. **双皮肤同形**：墨夜同样是 24dp 四角悬浮岛 + 内高光，不是旧 D-041 的「小圆角 / 零阴影 / 细线平铺」。
3. **遇到技术障碍不得自行简化**：某个面因 `lens` 形状约束、窗口层级或性能无法达到 B，必须在对应 Gate **停下上报 Go/No-Go**，由关子决策；禁止擅自改成「只有模糊」「改回实色」「跳过该组件」「降一档」。
4. **性能问题只靠优化，不靠改外观**：超 §4.3 阈值时优化实现（合并 backdrop、复用 `exportedBackdrop`、减少离屏捕获、控制同屏面数），不得减少玻璃面或改变材质强度。

### 0.5 既有决策冲突处置（必须写入 `decisions.md`）

| 决策 | 处置 |
| --- | --- |
| D-041 | **形态条款废止**（糖果卡大圆角柔影悬浮药丸 / 墨夜小圆角零阴影细线平铺、双皮肤不同几何）；「完全跟随系统深浅色、App 内无手动开关」保留 |
| D-042 | 几何令牌化原则保留，但 `ModuleGeometry` 的**取值与字段**由单套 `GlassGeometry` 取代；`navFloating` 字段删除 |
| D-043 | **保留**：字体家族继续霞鹜臻楷子集（原型自身已声明系统字体仅为预览，不构成偏离 B）；字号/字重/行高按 §0.2 |
| D-044 | `themedTone()` 循环渲染口径保留；`ModuleTones` 四实例色值改为 B 的青绿家族 |
| D-028 | 保留：图标只用 material-icons-core + 两枚自绘 VectorDrawable，不引 extended |
| 新增 | D-050（基准与形态锁定）、D-051（**材质唯一 + `minSdk 33` + 覆盖范围变更**）、D-052（玻璃层与 exportedBackdrop 口径）、D-053（旧规范废止清单）、D-054（工具链升级与 D-015 修订，Q-B1 已授权），编号按实际落地顺延 |
| D-015 | 工具链锁定组合**可升级**（Q-B1 已授权）；升级后须在 D-054 记录新版本矩阵并复核 D-031 的 `testInstrumentationRunner` 显式声明 | Q-B1 已确认 |

### 0.6 「结果必须跟方案 B 一样」的执行结论（Q-B7 关闭）

关子两次明确：方案 B 为唯一依据、**最终结果必须做到跟方案 B 一样**。这条要求与「`minSdk 26` + 低版本降级」无法同时成立，推导如下：

| 事实 | 证据 |
| --- | --- |
| lens 折射依赖 `RuntimeShader`，自 Android 13 / **API 33** 才有 | 库官方 API 文档（Lens / RuntimeShader 标 Android 13+） |
| 低于 33 的设备无法呈现 B 的折射；`blur` / `solid` 档外观与 B 不同 | `theme-b.css` 三档定义 + 官方 effects 文档 |
| 只要保留 `minSdk 26`，就必然存在「结果不一样」的受支持设备 | 上两条 |
| 项目尚未正式发布，用户侧只装过 debug 包、不做旧数据迁移 | D-039 |

**结论（本计划执行口径，Q-B7 不再待确认）**：`minSdk` 由 **26 → 33**，`targetSdk / compileSdk 35` 不变。所有受支持设备均为 API 33+，全部呈现 `glass` 档液态玻璃，结果一致。

| 项 | 结论 |
| --- | --- |
| 用户可见风格 | **只有液态玻璃一种**；App 内无材质选择、无档位入口、无新增设置项与文案（M13 字体子集重跑不触发） |
| 手动开关 / `SDK_INT` 材质分支 / 性能降档 | **全部不实现**；`option-b.html` 的三档切换保留在 Golden 仅供对照，不移植 |
| 低版本设备 | Android 12 及以下**不再受支持**（系统拒绝安装），不再为其设计任何降级外观 |
| 代价 | 放弃 Android 8–12 覆盖；无存量正式用户故无迁移成本，但这是**覆盖范围变更**，已在 D-051 登记 |

> 若不希望砍掉这部分设备，唯一替代是接受那些设备「结果不一样」，与本次要求直接冲突——因此不再保留该分支。取舍写明，不静默执行。

### 0.7 执行前冻结（2026-11-08，关子「确认方案无误」）

| 项 | 冻结值 | 依据 |
| --- | --- | --- |
| Golden | `option-b.html` + `src/**` 冻结在 v2（悬浮岛 + 统一 24 + 矢量图标），此后只允许「先改 Golden → 确认 → 再改实现」 | §0.1 |
| Q-B8 底栏浮法 | **覆盖（overlay）** —— 岛绝对定位贴底、内容从岛后方穿过，页面底部避让 112dp | 关子确认时预览的默认形态；三种浮法保留在 Golden 控制条可切，若要改只改 M5 一处 |
| Q-B9 统一圆角 | **24dp**（保持），已知几何副作用见 §0.2.1 与 AC-01 | 关子确认时预览为 24 |
| 动效 | 新增 M17 动效层，规格见 §4.4；不改变任何静态外观 | 关子「补充适当的过渡动画，自然流畅且与交互风格一致」 |

### 0.8 R0.1 依赖与工具链结论（实测，2026-11-08）

**结论：用 `io.github.kyant0:backdrop:1.0.6`，不用 2.0.1。** 依据：

| 候选 | 传递依赖 | 硬门槛（AAR `aar-metadata`） | 判定 |
| --- | --- | --- | --- |
| backdrop **2.0.1**（latest） | `org.jetbrains.compose.* 1.12.0` → `androidx.compose.ui:ui 1.12.0` | compose ui 1.12.x：`minCompileSdk=37`、`minAndroidGradlePluginVersion=9.1.0` | **不可用**：需 AGP 9.1 + Gradle 9 + compileSdk 37 整体迁移，与本机 SDK 35 差两代 |
| backdrop **1.0.6** | `org.jetbrains.compose.* 1.10.3` → `androidx.compose.ui:ui 1.10.3` | AAR 自身 `minCompileSdk=36`（`shapes-android 1.2.0` 同）；依赖的 compose ui 1.10.x 只要 35 | **采用**：Kotlin 2.3.10 + Compose BOM 2026.03.01 + **compileSdk 36**；AGP / Gradle 不动 |

**API 等价性（读 1.0.6 sources jar 逐条核对，非推测）**：`rememberLayerBackdrop` / `Modifier.layerBackdrop` / `Modifier.drawBackdrop(backdrop, shape, effects, layerBlock, exportedBackdrop, onDrawBehind, onDrawBackdrop, onDrawSurface, onDrawFront)` / `rememberCombinedBackdrop` / `rememberCanvasBackdrop` / `emptyBackdrop` / `BackdropEffectScope.{colorFilter, opacity, colorControls, vibrancy, exposureAdjustment, gammaAdjustment, blur, lens, effect}` / `lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` / `Highlight` + `HighlightStyle.{Plain, Default, Ambient}` / `Shadow` / `InnerShadow` —— **与计划正文所用 API 完全一致**，§2 的调用链无需改写。

**版本矩阵（本轮实际改动）**：

| 组件 | 旧 | 新 | 理由 |
| --- | --- | --- | --- |
| Kotlin / KGP / Compose 编译器插件 | 2.2.21 | **2.3.10** | backdrop 1.0.6 的 `kotlin-stdlib` 是 2.3.10，2.2 编译器读不了它的 metadata |
| Compose BOM | 2025.08.01（ui 1.9.x / material3 1.3.2） | **2026.03.01**（ui 1.10.6 / material3 1.4.0） | 必须 ≥1.10.3 才满足 backdrop 的运行时 |
| compileSdk | 35 | **36**（已装 `platforms/android-36`，buildTools 36.0.0） | backdrop 1.0.6 与 `shapes-android 1.2.0` 的 AAR 声明 `minCompileSdk=36`；首次构建由 `checkDebugAarMetadata` 拦下，实测确认 |
| `targetSdk` | 35 | **35（不变）** | 只抬 compileSdk 不动 targetSdk，避免引入新的运行时行为变更 |
| AGP / Gradle | 8.13.2 / 8.14.3 | **不变** | 8.13.2 就地支持 compileSdk 36 |
| `minSdk` | 26 | **33** | §0.6（结果一致） |
| 新依赖 | — | `io.github.kyant0:backdrop:1.0.6` | 唯一新增，Apache-2.0 |

> 2.0.1 与 1.0.6 的差异集中在 Compose 运行时版本，1.x 的 Backdrop API 已经定型（含 `exportedBackdrop` 与 `lens`）。若日后要从 1.0.6 升 2.x，等于绑定 AGP 9 + compileSdk 37，属另一次工具链迁移，不在本计划内。

---

## 1. 重构目标与范围

### 1.1 目标（可验收）

| # | 目标 |
| --- | --- |
| G1 | Android 端所有页面、弹层、共用组件呈现与 `option-b.html` 一致的**全量液态玻璃**：半透明表面 + 模糊 + 边缘折射高光 + 背景光斑 + 浮起层级 |
| G2 | 配色、字体层级、间距、组件样式、交互规则、页面间体验在 App 内统一，且不随设备 / 皮肤 / 运行状态漂移；产品只有 `glass` 一种材质 |
| G3 | 建立根 backdrop 层、统一弹层规格、合并组件层，消除当前「M3 sheet / 自绘 Surface」双轨与 `bottomInset` 特判 |
| G4 | `minSdk` 提升至 **33**（§0.6，同步 `android-requirements.md` 与 D-051）、无新增权限、不联网、无测试基线劣化，玻璃叠加无原生崩溃 |

### 1.2 范围（全量，逐项必须覆盖）

| 类 | 数量 | 明细 |
| --- | ---: | --- |
| 一级页 | 4 | `HomeScreen`（ProverbCard / SuggestionSection）、`StudyScreen`、`ReadScreen`、`MineScreen`（MineProfileCard / MineStats 三卡） |
| 二级页 | 20 | `MistakesScreen`；动词 Home/Lesson/Reference/ThemeCards；形容词 Home/Lesson/Reference；助词 Home/Lesson/Practice/Mistakes；Verb/Adjective/Particle 练习屏；阅读 Input/Reader/ModelSettings/SideMenu |
| 导航容器 | 5 | `AppRoot`（MainActivity）+ `VerbFlow` / `AdjectiveFlow` / `ParticleFlow` / `ReadFlow` |
| 底栏 | 1 | `AppBottomBar` + `BottomTabItem`（自绘） |
| M3 弹层 | 4 | `ReferencePickerSheet`、`FilterSheet`、`MergedResultSheet`、`WordDetailSheet` |
| 对话框 | 7 | 3×「退出本轮？」、清空错题、统计清零、`EditNicknameDialog`、删除文章 |
| 抽屉 | 1 | `ReadSideMenu`（RTL 镜像） |
| 自定义底部面板 | 7+ | `ModuleBottomSurface`（4 处引用）、`JudgementDock`、`ParticleDock`、3 处内联同类 `Surface` |
| 共用组件 | 11 | `ui/common/ModuleUi.kt` 9 个 + `ReferencePickerSheet` + `FilterSheet`（在 `ui/practice/`） |
| 主题层 | 1 | `ui/theme/Theme.kt`（483 行）+ `values/themes.xml` / `values-night/themes.xml` |

### 1.3 明确不在范围

Web 端（`web/`）零改动；方案 A / C 任何特征；导航库引入（沿用 `when` 状态机）；字体家族更换；新增业务功能与新存储键；用户未提及的技术选型。

---

## 2. 需调整的模块及交付物

| # | 模块 / 文件 | 调整内容 | 交付物 |
| --- | --- | --- | --- |
| M1 | `android/gradle/libs.versions.toml`、`app/build.gradle.kts` | 新增 `backdrop` 版本与 library 别名，钉死精确版本；`implementation(libs.backdrop)`；**`minSdk` 26 → 33**（§0.6）；不引 navigation / icons-extended | 依赖解析报告（P0）、改后两文件、版本矩阵 |
| M2 | `ui/theme/Theme.kt` | 拆出 `GlassTokens` / `GlassGeometry` / `GlassSpacing` / `GlassTypography` / `GlassMaterial`；替换两套 Palette 与 M3 ColorScheme 为 B 值；删 `navFloating`；`ModuleTones` 换 B 青绿家族；保留四取色入口与 `themedTone()` | 新令牌层 + 纯函数（可单测） |
| M3 | `docs/android-theme-spec.md` | 重写为「方案 B 唯一规范」：废止 §1 两态材质语言、§3 旧配色表、§4 双套几何、§4.1 底栏两态、§7 旧验收；新增玻璃规格与双皮肤矩阵（**产品无三档**） | 新版规范（唯一规范来源） |
| M4 | `MainActivity.kt` | `AppRoot` 加根 backdrop（`rememberLayerBackdrop { drawRect(bg); drawContent() }` + `Modifier.layerBackdrop`）；重排 `Scaffold(bottomBar)` 与内容层层级；合并 `bottomInset` 特判（:170–183）为一套 safe-content 口径；保留 `when` 路由与 `rememberSaveable` Saver | 根布局与导航容器 |
| M5 | `AppBottomBar` / `BottomTabItem`（MainActivity:279–379） | 改**悬浮岛**：左右底各 16dp、四角 24dp、1dp 玻璃边 + 外投影 + 内高光、tab 50dp / glyph 26dp、选中 inset 光晕、页面底部 112dp 避让；**四枚文字字形（主/学/読/我）换为矢量描边图标**（`src/icons/tab-*.svg` → `res/drawable/ic_tab_*.xml`，`Icon(tint = LocalContentColor)`，颜色走 `currentColor` 等价物）；删除 A 的胶囊/平铺两态分支与 `HorizontalDivider`；保留 `selectable` + `semantics { selected }` + `themedTone()` | 底栏组件 |
| M6 | `ui/common/ModuleUi.kt`（9 组件） | 全部玻璃化：**统一 24dp 圆角**（走 `GlassShape.of()`，禁写数字）、玻璃 surface、内高光、`onDrawSurface` 可读性层；`ModuleBottomSurface` 由贴底条改为四角同 r 的浮起条 | 共用组件层 |
| M7 | `ui/practice/PracticeUi.kt`（10 组件）、`FilterSheet.kt`、`FilterPill` | 玻璃化选项、判定抽屉、进度、Toast、筛选胶囊；选中光晕；合并散落组件到统一层 | 练习 UI 层 |
| M8 | 4 个 `ModalBottomSheet` | 接管 shape / scrim / inset，改**四角 24dp + 三边离屏 12dp**的浮起玻璃 + 2px 高光条；验证窗口层级能否承载 `drawBackdrop`（R0.4） | 统一弹层规格 |
| M9 | 7 个 `AlertDialog` + `ModalNavigationDrawer` | 同 M8；抽屉遮罩与弹层叠加时保持两层遮罩不抢返回键（`ReadSideMenu.kt` 既有注释口径） | 弹层/抽屉 |
| M10 | 7+ 自定义底部面板 | 判定抽屉/操作条改玻璃 dock；**形状必须让 lens 生效且不偏离 B**（R0.3 Gate） | 底部面板层 |
| M11 | 4 一级页 + 20 二级页 + 5 Flow | 逐页套用 B 令牌；背景光斑由根层统一提供，页面不得自绘背景色；保留全部现有交互与状态机 | 页面层 |
| M12 | `res/values/themes.xml`、`values-night/themes.xml` | `windowBackground` 换 B 冷启动底色（浅 `#EAF2F4` / 深 `#0C1720`），避免第一帧闪旧纸白/墨黑 | 平台层 |
| M13 | `web/font/subset.py`、`web/tests/font.test.js` | 产品无材质开关与档位文案 → **不触发重跑**；若后续新增任何文案仍须重跑子集与 Android TTF | 双端字体产物（条件交付） |
| M14 | `ThemeTokensTest.kt` → `GlassTokensTest.kt` | 改为钉住 B 的配色/圆角/间距/字号与 `glass` 参数（**不钉 blur / solid**）；新增 `spec-b.json` 与 Kotlin 常量自动比对 | 契约测试 |
| M15 | 新增 Composable 渲染测试、`TalkBackMergeTest.kt` | 补当前缺失的 UI 渲染测试；仪器化 4 项连设备跑 | 测试护栏 |
| M16 | `docs/decisions.md`、`android-requirements.md`、`known-issues.md`、`README.md`、`current-state.md`、文档索引 | 新增 D-050~（含 D-051 材质唯一与 `minSdk 33`）；**`android-requirements.md` 的 minSdk 26 改为 33**；登记册更新；文档地图加本计划 | 文档与决策 |
| M17 | 新增 `ui/theme/Motion.kt`（`GlassMotion`）+ 各屏过渡接线 | 动效令牌与逐场景规格见 §4.4：Tab 内容淡入、弹层进出、页面进出、判定淡入、骨架呼吸；只动 alpha / translate / scale，尊重系统动效开关 | 动效层 |

**防偏离附加交付物**：`docs/design/liquid-glass/spec-b.json`（机器可读 Golden 规格）+ 校验脚本，用于自动比对 `GlassTokens.kt` 与冻结基准。

---

## 3. 分阶段任务拆分与依赖关系

### 3.1 阶段总览

| 阶段 | 目标 | 任务数 | 出口门禁 |
| --- | --- | ---: | --- |
| R0 | 前置验证与基准机器化，回答「能不能做」 | 7 | **Gate 0**：六项验证全过 + Go/No-Go |
| R1 | 底层重构（令牌 / 根层 / 弹层规格 / 组件层） | 8 | **Gate 1**：不写任何页面代码前，底层自身过 AC-01 子集 |
| R2 | 全量铺开（按依赖分 6 批） | 6 | **Gate 2.x**：每批过 B 一致性核对 |
| R3 | 收口（文档 / 全量回归 / 发布检查） | 3 | **Gate 3**：AC-01～AC-12 全过 |

### 3.2 R0 · 前置验证（不写生产代码）

| 编号 | 任务 | 前置 | 产出 / 验证 |
| --- | --- | --- | --- |
| R0.0 | 冻结 Golden：提取 `theme-b.css` 令牌生成 `spec-b.json`，写自动比对脚本 | — | 规格 JSON + 校验脚本；三文件加冻结标注 |
| R0.1 | 依赖解析与工具链选型（Q-B1 已授权全面升级）：锁定 `io.github.kyant0:backdrop` 精确版本，并选定与之兼容的**最新稳定** Compose BOM / Kotlin / AGP / Gradle 组合；记录版本矩阵、APK 体积增量、Apache-2.0 与 NOTICE；**复核 D-031**（升级后 `testInstrumentationRunner` 仍须显式 `AndroidJUnitRunner`，否则仪器化测试 0 发现静默假通过） | R0.0 | 解析报告 + 版本矩阵 + D-015 修订草案；不再需要 Gate 0 上报许可 |
| R0.2 | **API 33 / 34 / 35 三档**验证 `glass` 效果一致、无崩溃、无透明像素；另记录 26–32 为**不受支持**（系统拒绝安装），不作为验收档 | R0.1 | 三档截图 + 一致性结论 + 覆盖范围说明 |
| R0.3 | **形状与 lens 约束**：统一 24dp 圆角后，全部形状均为四角同 r（`minCornerRadius=24`），原「顶圆角 `minCornerRadius=0` 致 lens 失效」的风险**从源头消除**，本项改为验证 ① 24dp 角 + 岛宽高比下 `lens(refractionHeight, refractionAmount)` 的可见度与取值；② 26dp 图标底板等小尺寸玻璃元素是否需要抬到最小可见阈值；③ 悬浮岛外 16dp 边缘区域是否被 backdrop 正确捕获。产出实机截图与参数表 | R0.1 | Gate 0 关键项：必须证明折射可见且形状仍为 B |
| R0.4 | **弹层窗口层级**：验证 M3 `ModalBottomSheet` / `AlertDialog` 能否叠 `drawBackdrop`；glass-on-glass 用 `exportedBackdrop` 后是否仍有 RenderThread SIGSEGV；抽屉 + 弹层叠加 | R0.1 | 结论 + 崩溃日志（若有） |
| R0.5 | 性能与稳定性基线：现有页面帧率/渲染耗时；再测单玻璃面、N 个玻璃面、`LazyColumn` 滚动、弹层叠加四场景，逐项对照 **§4.3 已定阈值** | R0.1 | 基线数据 + 达标判定；若现有基线已不满足，先记录并在 Gate 0 说明，不得擅自放宽阈值 |
| R0.6 | 双皮肤可读性：糖果卡/墨夜 × 玻璃表面下的文字对比度、霞鹜臻楷笔画清晰度、TalkBack 焦点站 | R0.1 | 对比度数据 + 走查记录 |

> **Gate 0**：R0.3 若无法在不偏离 B 的前提下让折射生效，或 R0.4 弹层无法玻璃化，**停止并上报关子**，不得以「弹层只做模糊」或「退回实色卡」绕过。

### 3.3 R1 · 底层重构

| 编号 | 任务 | 前置 | 产出 / 验证 |
| --- | --- | --- | --- |
| R1.1 | M2 令牌层 + M3 规范重写 + M14 契约测试 | Gate 0 | `GlassTokensTest` 全过；`spec-b.json` 自动比对通过 |
| R1.2 | M1 依赖落地 | R0.1、R1.1 | `./gradlew :app:assembleDebug` 通过 |
| R1.3 | M4 根 backdrop 层 + `Scaffold` 重排 + inset 合并 | R1.2 | 根层截图与 B 光斑一致；四标签切换无透明像素 |
| R1.4 | **单一 `glass` 材质实现**：无 `SDK_INT` 材质分支、无性能降档；只留性能采集与超阈值上报钩子（§0.4-4） | R1.3 | 三档设备截图完全一致；代码中无 `blur` / `solid` 分支 |
| R1.5 | M5 底栏改悬浮岛（浮法按 §0.2.1 选定结果） | R1.3、R1.4 | 双皮肤同形；三边 16dp / 四角 24dp / 内容 112dp 避让；`selected` 语义与 `themedTone()` 保持 |
| R1.6 | M8 / M9 弹层规格收敛（4 sheet + 7 dialog + 1 drawer） | R0.4、R1.3 | 四角 24dp + 三边 12dp + 高光条；无 SIGSEGV |
| R1.7 | M6 / M7 组件层合并与玻璃化（11 共用组件 + 10 练习组件） | R1.1、R1.3 | 组件清单 100% 覆盖 |
| R1.8 | M10 底部面板玻璃化（按 R0.3 结论处理形状） | R0.3、R1.7 | 判定抽屉四态与 B 一致 |

> **Gate 1**：底层组件在独立预览页逐项对照 `option-b.html`；§0.3 禁止清单零命中；`GlassTokensTest` 与静态守卫通过。

### 3.4 R2 · 全量铺开（每批独立 Gate，不做一次性替换）

| 编号 | 批次 | 前置 | 覆盖 |
| --- | --- | --- | --- |
| R2.1 | 4 个一级页 | Gate 1 | Home / Study / Read / Mine + 6 个子组件 |
| R2.2 | 模块三组的 11 页 + 3 个 Flow | R2.1 | 动词 4 / 形容词 3 / 助词 4 + `VerbFlow`/`AdjectiveFlow`/`ParticleFlow` |
| R2.3 | 练习屏与判定体系 | R2.1 | `VerbPracticeScreen`、`AdjectivePracticeScreen`、`ParticlePracticeScreen`、`PracticeUi`、`FilterSheet`、3 个 dock |
| R2.4 | 阅读链路 | R2.1 | `ReadInputScreen`、`ReadReaderScreen`、`ModelSettingsScreen`、`ReadSideMenu`、`ReadFlow` + 2 个阅读弹层 |
| R2.5 | 速查 / 错题 / 我的剩余 | R2.2 | `VerbReferenceScreen`、`AdjectiveReferenceScreen`、`ReferencePickerSheet`、`MistakesScreen`、Mine 剩余区块 |
| R2.6 | 收尾 | R2.1–R2.5 | 剩余共用组件、M12 冷启动 `windowBackground`、M13 字体子集（条件） |

> **Gate 2.x（每批）**：该批对应页 × 双皮肤 × `glass` 档各走一遍；B 一致性核对表全过；基线不劣化；不通过不得开下一批。

### 3.5 R3 · 收口

| 编号 | 任务 | 前置 | 产出 |
| --- | --- | --- | --- |
| R3.1 | M16 文档与决策：D-050~（含 D-051 `minSdk 33`、D-054 工具链矩阵）、**D-015 修订**、`theme-spec` 定稿、**`android-requirements.md` minSdk 改 33**、`known-issues`、文档索引、`current-state` | Gate 2.6 | 文档一致性复核 |
| R3.2 | M15 全量回归：单测、Composable 渲染测试、仪器化 4 项、静态守卫、视觉回归截图 | R3.1 | 测试报告与截图集 |
| R3.3 | 发布前检查：APK 体积、Apache-2.0 LICENSE/NOTICE 归属、签名、**API 33/34/35 三档兼容矩阵复跑**、`minSdk == 33` 复核 | R3.2 | 发布检查清单 |

### 3.6 依赖链

```
R0.0 ──> R0.1 ──┬──> R0.2 ──┐
                 ├──> R0.3 ──┤
                 ├──> R0.4 ──┼──> Gate 0 ──> R1.1 ──> R1.2 ──> R1.3 ──┬──> R1.4 ──> R1.5
                 ├──> R0.5 ──┤                                        ├──> R1.6
                 └──> R0.6 ──┘                                        └──> R1.7 ──> R1.8
                                                                              │
Gate 1 ──> R2.1 ──┬──> R2.2 ──> R2.5 ──┐
                   ├──> R2.3 ───────────┼──> R2.6 ──> Gate 2.6 ──> R3.1 ──> R3.2 ──> R3.3 ──> Gate 3
                   └──> R2.4 ───────────┘
```

关键串行点：**R1.3 根 backdrop 层是所有玻璃面的前置**；**R0.3 形状结论是 R1.6 / R1.8 的前置**；**R1.7 组件层是 R2 全量铺开的前置**。

### 3.7 执行进度（滚动更新）

| 任务 | 状态 | 证据 |
| --- | --- | --- |
| R0.1 依赖与工具链 | ✅ 完成 | backdrop 1.0.6 落地；`assembleDebug` 通过；结论写入 §0.8 / D-054 |
| R1.1 令牌层 M2 | ✅ 完成 | `GlassTokens.kt`（圆角/间距/模糊/折射/投影/岛几何）、`Theme.kt` 新增 `GlassColors` + `glassColors()`、`Motion.kt`；`ThemeTokensTest` 4 用例钉住 |
| R1.2 依赖落地 M1 | ✅ 完成 | `libs.versions.toml` + `app/build.gradle.kts`（minSdk 33 / compileSdk 36 / buildTools 36.0.0） |
| R1.3 根层 M4 | ✅ 完成 | `ui/glass/Glass.kt` 的 `GlassLayer`：165° 渐变 + 三团椭圆光斑（逐值对齐 Golden）；`Scaffold.containerColor` 改透明 |
| R1.4 单一材质 M3 | ✅ 完成 | 全链路只有 `vibrancy → blur → lens`，无 `SDK_INT` 分支、无 blur/solid 档 |
| R1.5 悬浮岛 M5 | ✅ 完成 | 满宽 −32dp、四角 24、`padding 8/10`、tab 50dp、图标 20dp 于 26dp 槽、选中态只有颜色 + 字重；四页底部 112dp 避让 |
| R1.6 弹层 M8/M9 | ✅ 完成 | 4 sheet（四角 24 + 无抓手）+ 7 AlertDialog（`shape = GlassShape.corner`）+ 1 drawer（`drawerShape`）；弹层窗口采样限制见 IK-23 |
| R1.7 组件层 M6/M7 | ✅ 完成 | 36 处卡片 `Surface` → `GlassSurface`（保留 `borderStroke` 语义描边）；ModuleBottomSurface 改四角 24 浮起玻璃条；EntryCard / 形式卡 / 统计卡 / 阅读段卡全部走玻璃 |
| R1.8 底部面板 M10 | ✅ 完成 | 4 处吸底条形状改 `GlassShape.corner`（四角 24） |
| R0.2–R0.6 | ⚠ 部分 | R0.4 结论已定（弹层窗口采样不了主窗口 backdrop → IK-23）；R0.2/R0.3/R0.5/R0.6 仍欠实测 |
| R2.1 一级页 | ✅ 完成 | 四页页边距 22dp + 底部 112dp 避让 + 卡片玻璃化 |
| R2.2–R2.6 | ⚠ 部分 | 模块页/练习屏/阅读链路随 R1.7 一并玻璃化（共用组件驱动），逐页目视走查未全部完成 |
| R3 收口 | ⚠ 部分 | 文档与决策已更新；发布前检查（APK 体积 / LICENSE / 三档兼容）未做 |

> 本轮实测基线（2026-11-08，`qa36` AVD / API 36）：单测 **265 项 0 失败**（原 264，+1 玻璃令牌用例）、仪器化 `TalkBackMergeTest` **4/4 通过**（此前长期待设备）；浅色与深色首屏、四标签切换均已实机走查。

---

## 4. 风险与待确认项

### 4.1 风险

| # | 风险 | 触发 | 影响 | 缓解 | 等级 |
| --- | --- | --- | --- | --- | --- |
| K1 | lens 在 24dp 圆角 / 小尺寸元素上不可见，或悬浮岛边缘 16dp 区域捕获不到 backdrop | 底栏岛 / 弹层 / 图标底板 | 玻璃退化成「只有模糊」→ 直接违反「不简化」 | 形状统一后原 `minCornerRadius=0` 风险已消除；R0.3 实测参数与最小尺寸阈值，必要时只调 `refractionAmount` 不改外观；禁止接受「只有模糊」 | 库文档 + 推导，待实测 |
| K2 | M3 弹层独立窗口无法读取根 backdrop | 4 sheet / 7 dialog | 弹层玻璃化做不到 | R0.4 Gate；必要时改应用内浮层，但形状/规格仍按 B | 待实测 |
| K3 | glass-on-glass 绘制环 → RenderThread SIGSEGV | 玻璃弹层内玻璃按钮 | 原生崩溃 | 一律 `exportedBackdrop`；加静态守卫禁止 `drawBackdrop` 后叠 `layerBackdrop` | 官方 FAQ 已确认 |
| K4 | 性能塌方 | 多玻璃面 + `LazyColumn` + 弹层叠加 | 掉帧；酷安 16.6.2 曾专门优化底栏性能 | R0.5 基线、限定同屏面数（§4.3 上限 8）、性能自动降档；**阈值已定，见 §4.3** | 官方无数据 + 酷安更新日志 |
| K5 | 降级分支残留（`blur` / `solid` / `SDK_INT` 材质分支）或 `minSdk` 未真正抬到 33 | R1.4 / M1 | 出现「结果不一样」的设备或代码，违反最高约束 | §0.4-1 + 静态守卫（禁 `blur` / `solid` 参数与材质分支）+ `build.gradle.kts` 断言 `minSdk == 33` | 设计约束 |
| K6 | 旧规范把实现拉回悬浮药丸 / 纸卡 | `android-theme-spec.md`、`ModuleGeometry`、`ThemeTokensTest` 未同步 | 混合方案 A | M3 / M14 与 D-050~ 一次性废止；§0.3 静态守卫 | 已确认冲突 |
| K7 | 深色回到「零阴影细线平铺」 | D-041 残留 | 违反双皮肤同形 | R1.5 双皮肤同一底栏实现；`navFloating` 删除 | 已确认冲突 |
| K8 | 无障碍语义被玻璃层重绘破坏 | selected / 焦点站 | TalkBack 回归 | `TalkBackMergeTest` 4 项；禁手工 `mergeDescendants`（D-030） | 项目既有实测 |
| K9 | 霞鹜臻楷在半透明上对比度不足 | 玻璃表面 + 光斑 | 可读性 | R0.6；必要时调 `onDrawSurface` alpha，但不得改字号层级 | 待实测 |
| K10 | 库版本漂移 / API 换代 | 依赖未钉死 | 编译失败或效果突变 | 钉精确版本；1.x 与 2.x 片段不混用；Apache-2.0 归属 | 官方 README/版本记录 |
| K11 | 测试基线劣化 | 主题与几何改动 | 回归 | 每批复跑全量单测；基线不许扩大失败集 | 项目基线 |

### 4.2 决策状态

| # | 项 | 结论 | 状态 |
| --- | --- | --- | --- |
| Q-B1 | 工具链 | **全部允许升级**：Compose BOM / Kotlin / AGP / Gradle / JDK 均可按需升到与 `backdrop` 兼容的最新稳定组合；R0.1 出版本矩阵、D-054 记录、D-015 修订 | 已确认（2026-11-08） |
| Q-B2 | 材质呈现 | **App 内无手动开关、用户可见风格只要液态玻璃**；产品只有 `glass` 一种材质、无任何降级（§0.6） | 已确认（2026-11-08） |
| Q-B3 | 性能阈值 | 由简按现代手机性能定，**数值见 §4.3**，不再等实测后另定 | 已确认（2026-11-08） |
| Q-B4 | `lens(refractionHeight / refractionAmount)` 实机校准值 | 以「与 B 原型 150° 边缘渐变 + 16px 模糊 + 1.6 饱和观感一致」为判定，数值实机调 | 待 R0.3 / R1.4 |
| Q-B5 | 低端机白名单 / 灰度 | 默认不做白名单；若 R0.5 显示不可接受，Gate 0 上报 | 待 R0.5 |
| Q-B6 | 新决策编号顺序 | 从 D-050 起按落地顺序顺延 | 待 R3.1 |
| Q-B7 | `minSdk` | **26 → 33**，由关子「结果必须跟 B 一样」推导确定；代价＝放弃 Android 8–12，已登记 D-051 | **已关闭（2026-11-08）** |
| Q-B8 | 底栏浮法：**覆盖 / 占位 / 收窄** | **覆盖**（关子确认时预览的默认形态）；三种仍保留在 Golden 控制条，改主意只需改 M5 一处 | **已关闭（2026-11-08）** |
| Q-B9 | 统一圆角保持 24 还是收到 16 | **24 保持**；胶囊化副作用已写进 §0.2.1 与 AC-01，作为已接受的取舍 | **已关闭（2026-11-08）** |

> Q-B2 不新增任何设置项与文案 → M13 字体子集重跑不触发。Q-B7 已关闭：`minSdk 33` + 全设备单一 `glass`，不再有待确认的覆盖范围分支；代价见 §0.6。**Q-B8（底栏浮法 = 覆盖）、Q-B9（圆角 = 24）已于 2026-11-08 随「确认方案无误」关闭，见 §0.7**。仍开放：Q-B4（lens 实机校准）、Q-B5（低端白名单）、Q-B6（决策编号）。

### 4.3 性能与兼容性阈值（Q-B3，按 2026 年现代手机性能定）

**测量基准**：以主流中端（骁龙 7 系 / 天玑 7 系同级，1080×2400，90–120Hz）为验收下限设备，旗舰为参考上限；60/120Hz 分别记录。不用 2020 年前低端机做门槛，但 **API 33/34/35 三档**兼容矩阵仍记录其表现。

| # | 指标 | 阈值 | 定这个数的理由 |
| --- | --- | --- | --- |
| T1 | 交互 / 滚动 P95 帧时间 | **≤ 16.7 ms**（60fps 底线）；120Hz 设备目标 ≤ 8.3 ms（目标，非硬门槛） | 现代中端 90–120Hz 屏，60fps 是「不卡顿」底线；不为追 120fps 牺牲全量玻璃 |
| T2 | 掉帧率（连续 30 分钟四场景） | >16.7 ms 的帧 **≤ 5%**；>33.3 ms 严重掉帧 **≤ 1%** | 允许 shader 首次编译与首帧捕获的少量抖动，不允许持续掉帧 |
| T3 | 同屏玻璃面数 | **≤ 8 个**（含底栏 / 卡片 / 弹层）；弹层叠加时总数 ≤ 10 | 每面 = 一次背景捕获 + 一条 RenderEffect 链，成本近线性；8 个内现代中端可稳 |
| T4 | 单帧 GPU 渲染耗时增量 | 相对无玻璃基线 **≤ 6 ms** | 16.7 ms 预算里给业务绘制留 10 ms |
| T5 | 冷启动首帧 | ≤ 现有基线 **+ 200 ms** | 光斑背景与库初始化不应明显拖慢启动 |
| T6 | APK 体积增量 | **≤ 2.0 MB** | 单个 CMP 库 + 少量资源；release 未开 R8，仍守 D-028 图标策略 |
| T7 | 运行时图形 / GPU 内存增量 | **≤ 30 MB** | 离屏捕获缓冲随玻璃面数增长 |
| T8 | 正文对比度 | **≥ 4.5:1**；标题 / 大字 ≥ 3:1（WCAG AA），取玻璃表面最不利点 | 楷体笔画细，半透明上必须留足余量 |
| T9 | RenderThread SIGSEGV | **0** | 官方 FAQ 已知崩溃，非零即不可发布 |
| T10 | 材质档位 | **产品只有 `glass` 一档**（API 33+ 全覆盖）；无 `SDK_INT` 材质分支、无性能降档 | 结果一致性优先（§0.6） |

> **超限时怎么处置**：T3 超上限 → 优化实现（合并 backdrop、复用 `exportedBackdrop`、减少离屏捕获），**不得减少玻璃面、不得退回实色或只做模糊**；T1/T2/T4 不达标 → 继续优化实现，**禁止降档改外观**，优化后仍不达标则 Gate 上报关子。阈值是验收线，不是放宽 B 的借口。
> 数值由简按现代手机性能定；R0.5 / R0.6 实测后如需微调，必须保留证据并经关子确认，不得静默改表。

### 4.4 过渡动效规格（M17，2026-11-08 追加）

**总原则**：动效只表达「谁进来了 / 谁被选中了 / 谁在加载」，不做装饰性弹跳。B 的交互语言是**光感反馈**（proto 里 `hover 浮起 3px`、`active scale(.98)`、`无弹跳曲线`），动效必须沿用同一套曲线与量级，否则玻璃的静态质感会被动画打散。

**令牌（`GlassMotion`，与 `GlassTokens` 并列，`ui/theme/Motion.kt`）**

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `durationInstant` | 100 ms | 按压反馈、颜色微变 |
| `durationFast` | 160 ms | 选中态、图标换色、判定结果淡入 |
| `durationBase` | 220 ms | Tab 内容切换、页面进出、Toast |
| `durationEmphasized` | 320 ms | 弹层进出、抽屉 |
| `easingStandard` | `CubicBezierEasing(0.2f, 0f, 0f, 1f)` | 绝大多数过渡（M3 standard） |
| `easingEnter` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` | 入场 |
| `easingExit` | `CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)` | 出场 |
| `springSheet` | `spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)` | **仅**弹层拖拽回弹 |

**逐场景规格**

| 场景 | 动效 | 时长 / 曲线 |
| --- | --- | --- |
| Tab 切换 | 内容 `alpha 0→1` + `translateY 8dp→0`；图标与标签颜色 crossfade；**岛本身不位移、不缩放、不改尺寸** | 220 / standard |
| 选中态（tab / 选项 / 胶囊） | 底色与光晕 `alpha` 过渡，**不加 scale、不画 ripple** | 160 / standard |
| 卡片、入口按压 | `scale 1→0.98`，抬起回 1；阴影不参与动画（避免每帧重建 RenderEffect 链） | 100 / standard |
| 弹层进 / 出 | scrim `alpha 0→1` 同期；sheet `translateY 24dp→0`（进）/ `0→24dp`（出）；圆角与尺寸不变 | 320 进 / 240 出 / enter·exit |
| 页面进出（一级 ↔ 二级） | 新页 `alpha + 上移 8dp` 入场；旧页只 `alpha 1→0`、不位移（位移会让 backdrop 捕获区跳变，玻璃出现闪边） | 220 进 / 160 出 |
| Toast | 入场 `alpha` + `上移 6dp`，停留 2 s，出场只 `alpha` | 160 / standard |
| 列表首批渲染 | 整体淡入一次；**不做逐项 stagger**（滚动中逐项动画会掉帧） | 180 / standard |
| 答题判定 | 颜色与「完成 · / 失败 ·」文字双通道同时淡入；禁止抖动、缩放、闪红 | 160 / standard |
| 加载骨架 | `alpha 0.55 ↔ 0.85` 呼吸循环 | 1200 / linear |

**硬约束**

1. **必须尊重系统动效设置**：所有时值走 `tween` / `animate*AsState`，Compose 在「动画时长缩放 = 0」（无障碍「移除动画」）时自动退化为瞬时切换；禁止手写 `withFrameNanos` 循环绕过。
2. **只动 alpha / translate / scale**：这三者走 RenderNode，不触发重新测量；**禁止对带 `drawBackdrop` 的元素做尺寸或圆角动画**（每帧都会重建 shader 链，必掉帧）。
3. **禁止共享元素转场**：玻璃的 backdrop 捕获在共享元素下会错位/闪边。
4. **禁止 spring 弹跳**，`springSheet` 仅用于弹层手势回弹。
5. **动效时长不叠加**：同一次交互里嵌套动画（如切 Tab 同时弹层关闭）以最长的那个为准，不做串行累加。
6. 动效期间同屏玻璃面数仍受 **T3（≤8）** 约束。

---

## 5. 验证方式

| 层 | 方式 | 命令 / 工具 | 频率 | 证据 |
| --- | --- | --- | --- | --- |
| 规格一致性 | `spec-b.json` ↔ `GlassTokens.kt` 自动比对 | 新建校验脚本（Python，托管 Python 3.13.12） | 每次提交 | 比对日志，零差异 |
| 静态守卫 | `Color(0x` 仅 Theme.kt；`RoundedCornerShape(<数字>.dp)` 0 处；`navFloating` / `HorizontalDivider` 底栏 / 旧色值零命中；`drawBackdrop` 后不得叠 `layerBackdrop`；**禁 `blur` / `solid` 材质参数与 `SDK_INT` 材质分支；`minSdk == 33` 断言** | Grep 脚本 + `web/tests/styles.test.js` 同款思路 | 每次提交 | 守卫输出 |
| 单测 | `ThemeTokensTest` → `GlassTokensTest`；全量 JVM 单测 | `export JAVA_HOME=...jdk-21.0.12.1+1 && ./gradlew :app:testDebugUnitTest` | 每批 | 测试报告 |
| 工具链复核（Q-B1） | 升级后：版本矩阵落库、D-031 的 `testInstrumentationRunner` 仍显式声明、仪器化测试发现数 > 0、M3 行为变化视觉走查 | `./gradlew :app:testDebugUnitTest` + `androidTest` 发现数核对 | R0.1 / Gate 3 | 版本矩阵 + 发现数记录 |
| Composable 渲染测试 | 新增：玻璃层存在性、双皮肤令牌、单一 `glass` 材质（无降级分支）、弹层形状 | `androidTestImplementation` 现有 `ui-test-junit4` | Gate 1 / Gate 3 | 测试报告 |
| 仪器化 | `TalkBackMergeTest` 4 项（合并语义 / selected / 嵌套按钮） | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...`（需连设备） | Gate 3 | 结果（此前无设备未跑） |
| 视觉走查 | 9 视图 × 双皮肤 × `glass` 档，对照 `option-b.html` | `adb shell cmd uimode night no\|yes` + `adb exec-out screencap`；人工逐项核对 §0.2 / §0.3 | 每批 Gate | 截图集 + 核对表 |
| 兼容矩阵 | **API 33 / 34 / 35**：冷启动 → 四标签 → 四类弹层 → 滚动列表，三档结果一致；另记录 26–32 系统拒绝安装 | 模拟器/真机三档 | Gate 0 / Gate 3 | 三档记录 + 覆盖范围说明 |
| 稳定性 | 弹层叠加 + 滚动连续 30 分钟，RenderThread SIGSEGV 计数 | logcat `Fatal signal 11` 过滤 | Gate 0 / Gate 3 | 崩溃日志 |
| 性能 | 单面 / 多面 / 滚动 / 弹层四场景帧率与渲染耗时，对比 R0.5 基线 | 项目既有性能采集手段 | Gate 0 / Gate 3 | 数据（阈值 Q-B3） |
| 字体 | 新增文案后双端重建 | `web/font/subset.py` + `web/tests/font.test.js` | M13 触发时 | 产物 + 测试 |
| 动效（M17） | 逐场景核对 §4.4：时长/曲线取自 `GlassMotion`（无散落 magic number）；系统「移除动画」下全部瞬时完成；无尺寸/圆角动画作用于玻璃元素 | 静态守卫（`tween(` 只允许出现在 `Motion.kt` 调用点）+ 录屏逐场景走查 | 每批 Gate | 守卫输出 + 录屏 |
| Web 基线 | 确认未受影响 | `node --test web/tests/*.test.js`（Windows 须显式列文件） | Gate 3 | 396/396 |

---

## 6. 验收标准

| # | 标准 | 判定 | 一票否决 |
| --- | --- | --- | --- |
| AC-01 | **与方案 B 完全一致**：§0.2 规格逐项核对 100% 通过；§0.3 A/C 禁止清单零命中；9 视图 × 双皮肤 × `glass` 档截图走查通过 | 核对表 + 截图集 | **是** |
| AC-02 | **产品只有 `glass` 一种材质**：无 `blur` / `solid` 实现、无 `SDK_INT` 材质分支、无性能降档；API 33/34/35 三档设备截图完全一致，形态不随设备 / 皮肤 / 运行状态漂移 | 三档逐项截图比对 + 代码走查 + 静态守卫 | **是** |
| AC-03 | 双皮肤同形：墨夜同为 24dp 四角悬浮岛 + 内高光，无 A 的胶囊/平铺两态、无细线平铺 | 双皮肤截图 + 静态守卫 | **是** |
| AC-04 | 任一组件因技术限制达不到 B → 已 Gate 上报，代码中无「只做模糊 / 退回实色 / 跳过」的简化实现 | Gate 记录 + 代码走查 | **是** |
| AC-05 | 测试基线：Android 单测 **264 项 0 失败**、Web **396/396**、`TalkBackMergeTest` 4 项通过 | 测试报告（基线数值来自项目记录，本次计划未重跑） | 是 |
| AC-06 | 静态守卫：`Color(0x` 仅 Theme.kt、`RoundedCornerShape(<数字>.dp)` 0 处、旧色值/`navFloating` 零命中、`spec-b.json` 比对零差异 | 守卫输出 | 是 |
| AC-07 | 兼容矩阵 **API 33 / 34 / 35** 三档：全部 `glass`、结果一致、无崩溃 / 透明像素 / 空白玻璃面；`minSdk == 33` 且 26–32 系统拒绝安装 | 三档记录 + 构建配置断言 | 是 |
| AC-08 | 稳定性：30 分钟弹层叠加 + 滚动，SIGSEGV 为 0；glass-on-glass 全走 `exportedBackdrop` | logcat + 代码走查 | 是 |
| AC-09 | 性能：四场景达 **§4.3 T1–T7 / T9**（P95 ≤16.7ms、严重掉帧 ≤1%、同屏玻璃面 ≤8、GPU 增量 ≤6ms、冷启动 +200ms、APK +2MB、图形内存 +30MB、SIGSEGV 0）；未达标**只准优化实现、禁止降档改外观**，优化后仍不达标则 Gate 上报；T3 超限不准减面 | 性能数据 + Gate 记录 | 否（需关子裁定） |
| AC-10 | 无障碍：TalkBack 焦点站与顺序不劣化；`selected` 语义、`onLongClickLabel` 保留；对比度达 **T8**（正文 ≥4.5:1 / 大字 ≥3:1） | 仪器化 + 走查 | 是 |
| AC-11 | 许可与产物：Apache-2.0 LICENSE / NOTICE 归属保留；APK 体积增量 ≤2.0 MB；签名 release 可构建 | 发布检查清单 | 是 |
| AC-12 | 文档一致：`android-theme-spec.md` 为 B 唯一规范、`decisions.md` D-050~ 已登记、旧 D-041/D-042/D-044 冲突条款标注废止、文档索引与 `current-state.md` 已更新 | 文档复核 | 是 |
| AC-13 | **Q-B1 落地**：工具链版本矩阵已记录、D-015 修订 + D-054 登记、升级后 D-031 的 `testInstrumentationRunner` 仍显式声明且仪器化发现数 > 0、全量基线不劣化 | 版本矩阵 + 测试报告 | 是 |
| AC-14 | **结果一致性落地**：App 内无任何材质选择入口、无新增设置项与文案（M13 不触发）、**`minSdk 33` 已落地**、无 `SDK_INT` 材质分支 / 无性能降档、三档设备结果一致（§0.6） | 代码走查 + 三档截图 + 文案 diff + 构建配置 | **是** |
| AC-15 | **动效一致（M17）**：§4.4 九个场景全部按表实现，时长/曲线取自 `GlassMotion` 无散落 magic number；系统「动画时长缩放 = 0」时全部瞬时切换；无共享元素转场、无逐项 stagger、无 spring 弹跳（弹层回弹除外）；玻璃元素上无尺寸/圆角动画 | 静态守卫 + 录屏逐场景走查 + 无障碍动效开关实测 | 否（需关子裁定） |

**AC-01 核对表必含项**（每批 Gate 复用）：**全局单一圆角 24dp**（卡片 / 按钮 / 输入框 / 弹层 / 图片 / 岛同值，`ui/` 下 `RoundedCornerShape(<数字>.dp)` 0 处）；页边距 22 / 卡内 18 / 间隙 16；字号 26/16/14/12/11/10/22/24 与行高 1.75；浅 `#14857F` 深 `#7FD3C7`；三处光斑可见；玻璃表面半透明 + 内高光 + 150° 边缘渐变；**悬浮岛底栏三边离屏 16dp + 四角 24dp + 宽 = 屏宽 − 32dp**；**底栏四枚矢量描边图标（1.75 描边 / 20dp / 无底板 / 颜色继承文字色）**；弹层**四角 24dp + 三边离屏 12dp** + 2px 高光条；光感 hover/选中/active 且无弹跳；状态条「完成 · / 失败 ·」。**功能性圆形（头像 / 状态点 / 开关滑块 / 环形进度）保持真圆，不计入圆角豁免**。

### 最终交付清单

`GlassTokens`（含单一 `radius = 24.dp`）与根 backdrop 层、**悬浮岛底栏**、**四角 24 + 离底 12 的浮起弹层**、11 共用组件 + 10 练习组件、4 一级页 + 20 二级页 + 5 Flow、`spec-b.json` 与校验脚本、`GlassTokensTest` + Composable 渲染测试、双皮肤 × `glass` 截图集、**API 33/34/35 三档**兼容与性能/稳定性报告、**`minSdk 33` 与 D-051 覆盖范围记录**、**工具链版本矩阵与 D-015 / D-054 修订**、新版 `android-theme-spec.md`、D-050~ 决策记录、发布检查清单。

---

## 附：证据与引用

- **方案 B 唯一基准**：`docs/design/liquid-glass/option-b.html`、`src/theme-b.css`、`src/engine.js`、`index.html`（§二 B 卡片 / §三 B 列 / §四共同基准）
- **原型验证记录**：三方案各 37 项断言共 111 项 0 失败；B 的悬浮岛与统一圆角另跑 34 项 0 失败（`--radius` 与五别名同解 24px、`.card/.btn/.tab/.tabbar` 计算圆角均 24px、无 999px 残留、覆盖浮法三边各 16px 且宽 = 屏宽 − 32、占位浮法不重叠、收窄浮法 280px 居中且在 320/390/496 三档屏宽下不溢出、弹层四角 24px 且离屏 12px、墨夜与三档材质下岛形不丢、A/C 未被污染）；B 的底栏矢量图标再跑 30 项 0 失败（4 枚内联 SVG 无文字残留、glyph 槽位 26×26 与图标 20×20 不变、四 tab 等宽 50dp、无底色块、选中/未选中颜色与同 tab 文字一致、墨夜 `#7FD3C7`、blur/solid 档与三种浮法下不丢、A/C 仍为文字字形、DPR 1/2/3 × 屏宽 320/390/600 下图标恒 20×20 无溢出）；computed style 实测：卡片 24px 圆角 / 标题 26px / 页边距 22px / 岛 24px 圆角 / `blur(16px) saturate(1.6)`
- **库一手文档**：`kyant.gitbook.io/backdrop` —— effects 顺序 `color filter ⇒ blur ⇒ lens`；`RenderEffect` 需 API 31、`RuntimeShader`/lens 需 API 33；`lens` 要求 `CornerBasedShape` 且 `height ∈ [0, minCornerRadius]`；glass-on-glass 必须 `exportedBackdrop`（否则 SIGSEGV）；库不含高层组件、Apache-2.0
- **项目现状**：`android/app/build.gradle.kts`（**原 minSdk 26 → 本计划改 33** / target 35 / Compose BOM 2025.08.01）、`ui/theme/Theme.kt`、`MainActivity.kt:154–183`、`docs/android-theme-spec.md` §1/§3/§4/§7、`docs/decisions.md` D-041～D-049
- **调研评估**：`docs/android-liquid-glass-assessment.md` §1.4 系统能力 / §2.4 全量清单 / §3 风险与不确定项
- **Q-B 确认记录（2026-11-08）**：Q-B1 工具链全部允许升级 → §0.5 / R0.1 / AC-13；Q-B2 无手动开关、可见风格只要液态玻璃 → §0.6 / AC-14；Q-B3 阈值按现代手机性能定 → §4.3 T1–T10 / AC-09 / AC-10；**Q-B7 已关闭 → `minSdk 33`**
- **最高约束记录（2026-11-08 关子重申）**：「最终的结果要做到跟方案 B 一样」→ 推导 `minSdk 33` + 产品单一 `glass` 材质 + 无降级，见 §0.6；代价（放弃 Android 8–12）已写明并登记 D-051
