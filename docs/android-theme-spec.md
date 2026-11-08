# Android 主题规范（方案 B 液态玻璃 · 双皮肤）

> 本文是 Android 端配色、材质、几何与动效的唯一规范。视觉基准是
> `docs/design/liquid-glass/option-b.html`（Golden v2），逐值对照由
> `GlassContractTest` 自动校验（D-050 / D-058）。

## 1. 两套皮肤，一套材质

皮肤只换颜色，**不换材质、不换几何**：

- 浅色「糖果卡」：纸底 `#E9F1F3`，青绿主色 `#14857F`；
- 深色「墨夜」：纸底 `#0A1218`，青绿主色 `#7FD3C7`；
- 两者的圆角、模糊、折射、投影、间距完全一致 —— 旧规范里「墨夜小圆角零阴影、糖果卡大圆角柔影」的双几何已废止（D-053）。

皮肤跟随系统深浅色自动切换，App 内不提供手动开关（D-041 保留条款）。

## 2. 取色取形入口（页面只许用这几个）

| 入口 | 拿到什么 | 典型用途 |
| --- | --- | --- |
| `MaterialTheme.colorScheme` | M3 语义色 | 按钮、输入框、图标着色 |
| `paperColors()` | `PaperColors`（40 项纸感语义色） | 正文/次要文字、统计数字、品詞底色 |
| `glassColors()` | `GlassColors`（屏幕底渐变、光斑、玻璃底/边/高光、主色、scrim、投影色） | 玻璃面、悬浮岛、弹层、Toast |
| `moduleTone()` | 当前模块的色块 | 页头图标底盒、区块标题 |
| `geometry()` | `ModuleGeometry`（三个别名同源 24dp + `navFloating`） | 兼容旧调用点；新代码直接用 `GlassTokens` |

**页面文件内禁止出现 `Color(0x…)`、`Color.White`、`Color.Black`**，唯一例外是 `Theme.kt` 自己。
**禁止写字面量圆角**：`RoundedCornerShape(12.dp)` 一律不行，形状只取 `GlassShape.corner`（`GlassContractTest` 静态守卫）。

## 3. 配色令牌（关键项）

完整表见 `Theme.kt` 的 `Palette` 与 `CandyPalette` / `InkNightPalette`。

| 语义 | 糖果卡（浅） | 墨夜（深） |
| --- | --- | --- |
| `paper`（页面底） | `#E9F1F3` | `#0A1218` |
| `surface`（不透明兜底卡） | `#F7FBFB` | `#14222C` |
| `glassSurface`（玻璃底） | `#8CFFFFFF`（55% 白） | `#8C182832` |
| `glassLine`（玻璃边） | `#D1FFFFFF`（82% 白） | `#4796C8D7` |
| `glassHighlight`（顶部高光） | `#B8FFFFFF` | `#3DFFFFFF` |
| `ink`（主文本） | `#143036` | `#E8F3F5` |
| `muted`（三级信息） | `#587175` | `#7E9AA3` |
| `textGreen`（绿色正文） | `#0C645F` | `#A6E4DA` |
| `brand`（强调 / primary） | `#14857F` | `#7FD3C7` |
| `brandOn`（主色上的文字） | `#FFFFFF` | `#0A1218` |
| `scrim`（弹层遮罩） | `#42103036` | `#80000000` |
| `screenTop` / `screenBottom` | `#EAF2F4` / `#DFEBEE` | `#0C1720` / `#081016` |
| `blobTeal` / `blobBlue` / `blobMint` | `#3D148C84` / `#38468CC8` / `#3378C8B4` | `#4D1E7882` / `#4D3C6EAA` / `#38288C82` |

`muted` 比 Golden 原始值 `#6D8A8E` 深一档：原值在纸底只有 3.23:1，够不上 WCAG AA 正文 4.5（D-057）。
`brand` 只用于 ≥14sp 粗体标题、区块标题与图标（4.22:1，按大字号 3.0 判定）；正文绿字一律用 `textGreen`。

### 3.1 品詞底色

浅色用实色，深色用带 alpha 的叠色（叠在 `surface` 上自己混）。逐词区不渲染品詞文字，色块是唯一的品詞线索，所以只给模型必须守的 13 项封闭集合；模型吐出集合外的值返回 `null`，保持无底色 —— 不把不认识的东西染成看起来有意义的颜色。

| 品詞 | 浅色 | 深色 |
| --- | --- | --- |
| 名詞 | `#DBF2E4` | `#299FBBD3` |
| 代名詞 | `#D9EEF0` | `#249FBBD3` |
| 動詞 | `#FFE6D1` | `#38CE6E58` |
| 形容詞 | `#DAE8FB` | `#33AE98E0` |
| 形容動詞 | `#EDE3FB` | `#33C9A0E0` |
| 副詞 | `#FBF3D6` | `#2ED9C978` |
| 連体詞 / 接続詞 / 感動詞 | `#EEF0F5` | `#29A0A8B0` |
| 助詞 | `#D7EDEC` | `#2978C4A8` |
| 助動詞 | `#E2E5F6` | `#2E8FA0C0` |

## 4. 材质令牌（`ui/theme/GlassTokens.kt`）

| 令牌 | 值 | 说明 |
| --- | --- | --- |
| `radius` | 24dp | **全局唯一圆角**：卡片 / 按钮 / 输入框 / 弹层 / 图片 / 岛，无例外 |
| `blur` | 16dp | 对应 Golden `blur(16px)` |
| `refractionHeight` / `refractionAmount` | 18dp / 24dp | `lens` 折射带宽与偏移量（R0.3 校准） |
| `cardElevation` / `islandElevation` / `sheetElevation` | 4 / 10 / 24dp | 三档投影 |
| `pagePadding` / `cardPadding` / `gap` | 22 / 18 / 16dp | 页面与卡片留白 |
| `islandInset` / `islandContentInset` | 16dp / 112dp | 悬浮岛离屏距；页面底部避让高度 |
| `islandPaddingH/V`、`tabHeight`、`tabGlyphSlot`、`tabIcon` | 10 / 8 / 50 / 26 / 20dp | 岛内几何 |
| `blobWidths/Heights/CenterX/CenterY` | 190×230@16%/14%、230×270@88%/40%、210×210@28%/88% | 屏幕底三团光斑，逐值对齐 Golden |

## 5. 玻璃的三条硬规矩

1. **玻璃面只能 `GlassSurface` / `Modifier.glassPanel`**。用「半透明 `colorScheme.surface` + 普通 `Surface`」冒充玻璃会在卡片内部出现边界生硬的白色矩形（D-056）。
2. **层级不能混**：`Layer.Background`（只录屏幕底）供内容流卡片采样；`Layer.Content`（屏幕底 + 页面内容）供悬浮岛 / 吸底条采样。悬浮类玻璃面必须是 `GlassLayer` 的兄弟节点，放进录制层里会形成绘制环、启动即 native 崩溃（D-052）。
3. **弹层走包装件**：`GlassBottomSheet` / `GlassAlertDialog`。它们把容器底设透明、用 `ScreenTextureBackdrop` 补采样源；`ModalNavigationDrawer` 在主窗口内可直接采 `Layer.Background`，但仍要 `glassPanel` + 透明容器（D-058）。

## 6. 动效令牌（`ui/theme/Motion.kt`）

`instant 100` / `fast 160` / `base 220` / `emphasized 320` / `exit 240`（ms）。
三条曲线：`standard`（M3）、`enter`（起步快收尾缓）、`exitEasing`（起步缓收尾快）。
位移：`pageShift 8dp`、`tabShift 8dp`、`toastShift 6dp`。

硬约束：尊重系统「动画时长缩放 = 0」（Compose 自动退化，不写分支）；只动 alpha / translate / scale；
禁共享元素转场、禁 spring 弹跳（`sheetSpring` 只给弹层回弹）；时长不叠加。

已落地场景：Tab 内容淡入 + 8dp 上移、底栏选中换色 160ms + 图标缩放 220ms、页面进出、Toast 进出场、
判定结果 160ms 淡入 + 6dp 上移。骨架呼吸在本项目无对应界面，不适用。

## 7. 平台层

- `values/themes.xml`：`Theme.Kotonoha` 继承 `android:Theme.Material.Light.NoActionBar`，`windowBackground = #E9F1F3`；
- `values-night/themes.xml`：继承 `android:Theme.Material.NoActionBar`，`windowBackground = #0A1218`；
- `values-v31` / `values-night-v31`：额外给 `windowSplashScreenBackground` 同值 —— Android 12+ 的冷启动由系统
  SplashScreen 接管，`windowBackground` 在那几帧里不生效，缺这条会先白闪一帧；
- `MainActivity`：`enableEdgeToEdge` 的两块系统栏都传透明 scrim。AndroidX 默认在 API 29–34 给状态栏叠
  `0xE6FFFFFF`，在纸底上就是一条灰带（API 35+ 系统强制 edge-to-edge 才看不出来）。

## 8. 验收

- `ui/theme/ThemeTokensTest.kt`（纯 JVM，4 用例）：两套皮肤的 `primary` / `paper` / 单值圆角 / 玻璃 alpha 区间 / 渐变两端不等 / 三光斑不同色；
- `ui/theme/GlassContractTest.kt`（纯 JVM，8 用例）：Golden CSS 逐值比对（圆角、玻璃底、玻璃边、渐变两端、三团光斑尺寸位置颜色）+ 四条静态守卫（禁字面量圆角、禁不透明 surface 冒充玻璃、弹层必须走包装件、抽屉必须挂 `glassPanel`）；
- `android/tools/contrast-check.py`：11 组前景/背景 × 双皮肤按 WCAG AA 判定，全绿才算过；
- 真机/模拟器走查：`adb shell cmd uimode night no|yes` 各截一轮，主页 / 学习 / 阅读 / 我的 / 模块首页 / 练习屏 / 速查 / 抽屉 / 对话框九处；
- 兜底 grep：`RoundedCornerShape\([0-9]+(\.[0-9]+)?\.dp\)` 在 `ui/` 下 0 处；`Color(0x` 只允许出现在 `Theme.kt`。
