# Android 双主题规范（糖果卡 / 墨夜）

本文件是 Android 端配色与几何的**唯一规范来源**。实现在 `android/app/src/main/java/com/kotonoha/app/ui/theme/Theme.kt`；本文与实现不一致时以代码为准并回填本文。

## 1. 为什么是两套主题

2026-10-22 用户从五版界面风格原型（`docs/design/` 之外的临时页面，见 §6）中选定两套：浅色「糖果卡」与深色「墨夜」，并明确「正好对应系统的深色系统与浅色系统」。因此主题选择**完全跟随系统 `isSystemInDarkTheme()`，App 内不提供手动开关**（D-041）。

两套不是「同一套色改个亮度」，而是两种材质语言：糖果卡靠大圆角 + 柔投影 + 悬浮胶囊底栏，墨夜靠小圆角 + 零阴影 + 细线平铺底栏。因此圆角与阴影也必须令牌化，否则深色下会看到「墨夜配色 + 糖果卡几何」的混搭。

## 2. 四个取色入口（页面只许用这四个）

| 入口 | 用途 | 位置 |
| --- | --- | --- |
| `MaterialTheme.colorScheme` | 背景 / 卡片 / 主色 / 强调 / 容器色阶 | Compose M3 |
| `paperColors()` | M3 覆盖不到的细粒度语义色（muted / textStrong / reading / meaning / line…） | `Theme.kt` |
| `moduleTone()` | 当前模块的一组四色（main / pale / deep / line） | `Theme.kt` |
| `posBackgroundColor(pos)` | 逐词区品詞底色（13 项品詞归 9 组） | `Theme.kt` |

另有 `geometry()` 提供圆角与阴影（§4）。**页面文件内禁止出现 `Color(0x…)`、`Color.White`、`Color.Black`**；唯一例外是 `Theme.kt` 自己。

### 2.1 深色下的模块色块

`ModuleTones.Home/Study/Read/Mine` 是 `object` 里的 `val`，**写死浅色值**，本身不认识主题。深色映射由两处完成：

- `ProvideModuleTone(tone) { … }`：把浅色色块换成墨夜同族色后放进 CompositionLocal；
- `themedTone(tone)`：给拿不到 CompositionLocal 的场景（例如底栏逐格自己 `toneOf(entry)`）显式解析。

> 踩坑记录：`BottomTabItem` 曾直接 `val tone = toneOf(entry)` 取 `ModuleTones` 常量，绕过了 `ProvideModuleTone`，导致深色下四个 tab 的选中胶囊仍是浅色 `#E9F7EF`。凡「按 tab / 按模块循环渲染」的地方都必须经 `themedTone()`。

## 3. 配色令牌

完整 40 项语义色见 `Theme.kt` 的 `Palette` 与 `CandyPalette` / `InkNightPalette`。下表列关键项：

| 语义 | 糖果卡（浅） | 墨夜（深） |
| --- | --- | --- |
| `paper`（页面底） | `#FAFAF8` | `#0F1114` |
| `surface`（卡片） | `#FFFFFF` | `#181C21` |
| `ink`（主文本） | `#212430` | `#E9E7E2` |
| `muted`（三级信息） | `#8A8D9C` | `#8B93A0` |
| `line`（描边） | `#EFF0F4` | `#262B33` |
| `lineControl`（控件描边） | `#E4E7EE` | `#333A44` |
| `green`（强调 / primary） | `#2E9463` 竹绿 | `#9FBBD3` 冷蓝 |
| `greenPale` | `#E9F7EF` | `#1D2831` |
| `greenDark` | `#1F6B47` | `#CFE0EE` |
| `orange`（阅读模块） | `#D98243` | `#C9927A` |
| `orangePale` | `#FFE6D1` | `#2E2119` |
| `slateBlue`（我的模块） | `#5E7392` | `#9FB3CC` |
| `slateBluePale` | `#E7EDF6` | `#202A38` |
| `red`（错误） | `#D2544C` | `#D98B80` |

深色 M3 容器色阶全部落在墨黑冷调（`surfaceContainerLowest #14181C` / `Low #1A1F24` / `Container #1C2226` / `High #212931` / `Highest #262E36` / `Bright #232A31` / `Dim #0B0D10`），**目的就是不留 M3 默认中性紫**——`darkColorScheme()` 不给的槽位会回落到 M3 基线色，深色第一眼会看成「紫色 Material 默认皮肤」。

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

## 4. 几何令牌

`@Composable fun geometry(): ModuleGeometry`（仅 Composable 上下文可调）：

| 字段 | 糖果卡 | 墨夜 | 语义 |
| --- | --- | --- | --- |
| `cardRadius` | 20.dp | 13.dp | 一级卡片 / 入口卡 / 底部面板顶角 |
| `smallRadius` | 12.dp | 9.dp | 行、小色块、图标盒、胶囊内的子块 |
| `cardShadow` | 3.dp | 0.dp | 卡片 `shadowElevation` |
| `pillRadius` | 999.dp | 999.dp | 胶囊 |
| `navFloating` | true | false | 底栏是否悬浮药丸 |

`cardShadow = 0` 时墨夜靠 1dp 描边分隔层次，因此**只靠阴影分层、没有 BorderStroke 的卡片必须补一条 `BorderStroke(1.dp, paperColors().line)`**，否则深色下卡片边界消失。

`geometry()` 只能在 `@Composable` 函数体内调用：它不是常量，不能放在顶层 `val`、普通 `fun` 或默认参数里。默认参数需要令牌时用 `param: Dp? = null` + 函数体内 `param ?: geometry().xxx`。

### 4.1 底栏两态

`AppBottomBar` 按 `geometry().navFloating` 分两态，共用同一段 `tabs` 行，只换外壳：

- **悬浮（浅色糖果卡）**：外层 `Box(fillMaxWidth + navigationBarsPadding + padding(horizontal = 7.dp, vertical = 8.dp))`，内层 `Surface(shape = RoundedCornerShape(pillRadius), shadowElevation = cardShadow)`，无上边线。`navigationBarsPadding` 提到外层是为了让药丸不贴在导航栏上。
- **平铺（深色墨夜）**：`Surface` → `Column { HorizontalDivider(1.dp, paperColors().line); tabs(navigationBarsPadding()) }`，无圆角、无阴影。

两态各自只调一次 `navigationBarsPadding()`，绝不叠加。

### 4.2 不为几何令牌化留死角的三个约定

1. `ModuleGlyphBox(icon, modifier, size, corner: Dp? = null, …)`：默认参数不能调 `@Composable`，所以类名 `corner` 用 `null` 占位，体内 `val c = corner ?: geometry().smallRadius`。**不传就吃主题圆角，传了就尊重调用方**——现有 5 个调用点仍显式传 `999.dp`／`10.dp`（行内小圆标），深色下比 `smallRadius` 大 1dp，无视觉影响。
2. `ParticlePracticeScreen` 的 `functionSentence(…, highlight: Color, highlightBg: Color)`：颜色由调用方从 `paperColors()` 传入，纯函数本身不认识主题。这是把最后两处硬编码色归零的形态。
3. `PracticeUi` 的选项描边与裁切必须同源（`cornerPx` 与 `shape` 都取 `g.smallRadius`），否则描边会比圆角多出几个像素。

## 5. 平台层

- `android/app/src/main/res/values/themes.xml`：`Theme.Kotonoha` 继承 `android:Theme.Material.Light.NoActionBar`，`windowBackground = #FAFAF8`；
- `android/app/src/main/res/values-night/themes.xml`：继承 `android:Theme.Material.NoActionBar`，`windowBackground = #0F1114`。

这两行 `windowBackground` 只为**冷启动第一帧**：留空会先闪一帧系统默认底，再切到 Compose 画的主题色。

## 6. 与原型页面的关系

五版风格原型（和紙墨 / 墨夜 / 糖果卡 / 鉛筆 / 文庫）是讨论用产物，不在仓库内，不是正式页面。选定的两版以本文为准；原型的无衬线字体**未采用** —— App 继续用霞鹜臻楷子集（`web/font/subset-chars.txt` + `web/font/subset.py` 管线），换字体等于退掉整条字体子集管线。

## 7. 验收

- `ui/theme/ThemeTokensTest.kt`（纯 JVM）钉住两套皮肤的 `primary`、`paper`、`cardRadius`、`smallRadius`、`cardShadow`、`pillRadius`、`navFloating`，色值或圆角逐字改动会立刻红灯；
- 真机验收：`adb shell cmd uimode night no|yes` 各截一轮，主页 / 学习 / 阅读 / 我的 / 阅读器 / 抽屉 / 词详情弹层七处；
- 底栏选中胶囊像素取样：深色必须是 `#1D2831`（学习）/ `#202A38`（我的），出现 `#E9F7EF` / `#E7EDF6` 即说明漏了 `themedTone()`。取点要避开文字与图标，例如浅色学习页取 `(300,2130)` 得 `#E9F7EF`、深色我的页取 `(820,2130)` 得 `#202A38`。
- 全工程 `RoundedCornerShape(<数字>.dp)` 必须为 0 处（`grep` 兜底），`shadowElevation` 只允许出现 `g.cardShadow`；`ui/` 下 `Color(0x` 只允许出现在 `Theme.kt`。