# 底部栏四标签框架 · 主页 / 学习 / 阅读 / 我的

> 日期：2026-10-17
> 状态：**已实现**（2026-10-17，见 §14 实现记录）
> 关联：[android-two-tab-spec.md](android-two-tab-spec.md)（前身：两标签重设计）、[module-guide.md](module-guide.md)、[decisions.md](decisions.md)（D-024～D-029）
> 原型：本期不另出多版原型——四标签结构与各页职责已由需求方直接选定；布局细节见 §5，设计说明见 `docs/design/four-tab-design.md`。

---

## 1. 这次改动的性质

底栏从**两个标签**（主页 / 我的）扩展为**四个标签**（主页 / 学习 / 阅读 / 我的）——信息架构（IA）调整，不只是加页面：

| 维度 | 改前（两标签） | 改后（四标签） |
| --- | --- | --- |
| 一级页面 | 2 个（主页、我的） | 4 个（主页、学习、阅读、我的） |
| 模块入口 | 主页四张大卡（动词/形容词/助词/错题本） | 迁至「学习」页；主页改为谚语 + 统计 + 知识点建议 |
| 新增能力 | — | 「阅读」工具框架（规划中位）、「我的」资料卡/书签/预留设置位 |
| 扩展方式 | 加模块只加主页入口行 | 加模块加学习页入口行；加工具加阅读页列表项；加设置加我的页列表项 |
| 代价 | — | 进模块多一次点击（与两标签版一致）；主页不再是入口Hub |

「学习」承接原主页的模块入口职责；「主页」转型为每日打开应用的第一屏（谚语 + 今日状态 + 下一步建议）；「阅读」「我的」本期只搭框架并预留扩展点。

**本期不做的**：任何阅读工具的真实能力、查词能力、模型/API 配置的真实读写、发音音色、词库导入导出、Web 端导航改造（Web 维持侧栏，D-003 不变）。

---

## 2. 信息架构（锁定）

```text
底栏四标签（一级）
├── 主页    谚语卡（随机 + 换一句） → 今日概览（周历条 + 本周小结） → 知识点建议（规则生成，可执行）
├── 学习    模块入口：动词练习 / 形容词练习 / 助词学习 / 错题本
│          └── 预留位：查词（规划中，不可点击）
├── 阅读    工具框架：AI 阅读 / 排版 / 撰写 / 语法单词解析（均规划中，不可点击）
└── 我的    资料卡（昵称可编辑） → 学习统计（原两标签版内容）
           → 书签收藏夹（空态框架） → 预留设置位：模型/API 设置、发音音色、词库导入导出（规划中）
           → 统计清零

二级页（从「学习」进入，进入后隐藏底栏，顶部返回 + 系统返回键回「学习」所在标签）
├── 动词练习 / 形容词练习 / 助词学习 / 错题本（现状不变，仅入口位置变更）
```

二级页回到一级页时落在**触发它的标签**（学习）。主页建议卡的动作可直达二级页（动词/形容词/助词/错题本）或切到「学习」标签。

---

## 3. 范围锁定（本期）

| # | 功能 | 验收标准摘要 | 优先级 |
| --- | --- | --- | --- |
| F-01 | 底栏四标签框架 | 4 标签可切换、选中态清晰、二级页逻辑不回归 | P0 |
| F-02 | 主页谚语卡 | 内置 24 条谚语随机展示，原文/读音/大意齐全，「换一句」不连续重复 | P0 |
| F-03 | 主页今日概览 | 周历条 + 本周小结行（数据源不变） | P0 |
| F-04 | 主页知识点建议 | 规则引擎生成，最多 3 条，可执行动作 | P0 |
| F-05 | 学习页模块入口 | 4 个入口卡（副信息口径与原主页一致），点击进二级页 | P0 |
| F-06 | 学习页查词预留位 | 可见、标注「规划中」、不可点击 | P1 |
| F-07 | 阅读页工具框架 | 4 个规划中工具位，可见、不可点击，带框架说明 | P1 |
| F-08 | 我的资料卡 | 昵称默认值 + 可编辑 + 长度/空白校验 + 重启保留 | P1 |
| F-09 | 我的书签收藏夹 | 空态框架 + 计数；存储骨架就绪（增删查纯函数可测） | P1 |
| F-10 | 我的预留设置位 | 3 项规划中设置位，可见、不可点击 | P1 |
| F-11 | 字体子集覆盖 | Android 独有文案（谚语日文 + 新中文）进入子集，字体契约测试通过 | P0 |

**明确不做（Out-of-Scope）**

| 不做的功能 | 原因 | 何时考虑 |
| --- | --- | --- |
| 阅读工具真实能力（AI 阅读/排版/撰写/解析） | 依赖模型服务，属后续模块（D-011 联网边界未定） | 模型方案确定后按 module-guide 走模块接入 |
| 查词功能 | 需要词库数据与查询交互，独立模块 | 词库方案确定后 |
| 模型/API 设置读写、发音音色、词库导入导出 | 依赖上述功能存在；本期只留可见规划位 | 随对应功能落地 |
| Web 端底部标签 | D-003 已锁定 Web 侧栏 + 抽屉；产品级 IA 在文档中描述即可 | 有明确需求再评估 |
| 书签真实产出（收藏动作入口） | 无语可收藏（阅读未上线）；只建存储骨架 + 空态 | 阅读模块上线时 |
| 账号/云同步 | D-009 纯本地 | 不做 |

---

## 4. 设计原则（沿用，不新造）

1. **纸感体系不变**：页面底 `--paper` → 纸白卡 + 1dp 描边 + 2dp 轻阴影 → 语义淡色，三级层次；颜色只走 `Theme.kt` 令牌，禁硬编码。
2. **一屏一个大数字锚点**：主页锚点是谚语卡与本周小结；我的页锚点仍是今日时长。
3. **需要行动的用暖色，纯展示用中性色**：建议卡中可执行项用 `tertiary`/`orange-pale` 系；规划中位用 `muted`。
4. **规划中位必须诚实**：可见、标注「规划中」、不可点击、不出现假交互（与 module-guide「不用不可操作的占位入口模拟已发布能力」的边界：仅作路线图沟通，不提供点击反馈）。落地条件写入 §8 扩展点。
5. **图标**：主页/我的沿用 Material Icons（`Icons.Filled.Home` / `Person`）；学习/阅读新增两枚 24dp VectorDrawable（学士帽 / 打开的书），理由见 D-028；全项目无 emoji。
6. **纯逻辑进引擎**：谚语数据与抽取、建议规则、资料/书签校验都是无 Android 依赖的纯函数，可单测。

---

## 5. 各标签页规格

### 5.1 主页（`ui/home/HomeScreen.kt` 重写）

自上而下四段：

| 段 | 区域 | 内容 | 数据来源 |
| --- | --- | --- | --- |
| ① | 页头 | 日期 + 主标题「今天继续」（沿用） | `LocalDate.now()` |
| ② | 谚语卡 | 原文（titleLarge 加粗）+ 读音（labelSmall muted）+ 大意（bodyMedium）；右上「换一句」按钮（Refresh 图标 + 文字） | `engine/proverb/ProverbData`（24 条）+ `ProverbPicker` |
| ③ | 今日概览 | 一周打卡条（七格，今天实心主色）+ 分隔线 + 「本周 N 题 · 连续 N 天 · 正确率 X%」 | `ActivityAggregation.weekStrip` / `AppState`（口径不变） |
| ④ | 知识点建议 | 「知识点建议」小节：最多 3 条建议行；可执行项右侧箭头 + 点击执行动作；不可执行项（鼓励语）无箭头 | `engine/suggest/SuggestionEngine` |

建议动作类型：打开动词/形容词/助词/错题本二级页、切换到「学习」标签。

**谚语卡交互**：进入主页随机一条；点「换一句」换一条，**不与当前条重复**（`ProverbPicker.nextIndex` 保证）；不持久化选中（每次进入可不同）。

**建议规则**（`SuggestionEngine.suggest(snapshot)`，按优先级取前 3）：

| # | 规则 | 条件 | 文案模板 | 动作 |
| --- | --- | --- | --- | --- |
| R1 | 到期错题 | `dueMistakes > 0` | 「有 N 道错题今天到期，复习一遍更牢」 | 打开错题本 |
| R2 | 今日未学 | `todayAnswers == 0` | 「今天还没练习，去学习页开始一组吧」 | 切换到学习标签 |
| R3 | 补最弱模块 | 某模块掌握度 ∈ [1, 59] 且为最低 | 「X 掌握度最低（N%），优先补这里」 | 打开对应模块 |
| R4 | 连续鼓励 | `currentStreak >= 3` | 「已连续学习 N 天，保持节奏」 | 无（鼓励） |
| R5 | 新用户引导 | `totalAnswers == 0` | 「从动词练习开始，先认识 N 个基础词条」 | 打开动词练习 |
| R6 | 兜底 | 以上都不命中 | 「今天已练 N 题，状态不错」 | 无 |

口径说明：`dueMistakes` = 动词/形容词到期错题 + 助词到期错题；`todayAnswers` = history 今日答题数；掌握度用现有 `verbModulePercent()/adjectiveModulePercent()` 与助词已掌握占比；`totalAnswers` = 两模块累计答对+答错（与现有正确率口径一致）。

### 5.2 学习（`ui/study/StudyScreen.kt` 新增）

| 段 | 区域 | 内容 |
| --- | --- | --- |
| ① | 页头 | 「学习」主标题 + 一行副标题（如「按模块练习，错题会按间隔复习」） |
| ② | 模块入口 | 四张大入口卡（复用原主页 EntryCard 样式，迁入 `ui/common/ModuleUi.kt`）：动词练习 / 形容词练习 / 助词学习 / 错题本；副信息口径与原主页完全一致（词条数、已练、掌握度、错题到期） |
| ③ | 预留区 | 「更多工具」小节标签 + 查词规划中行：可见、`muted`、「规划中」徽标、不可点击 |

### 5.3 阅读（`ui/read/ReadScreen.kt` 新增）

| 段 | 区域 | 内容 |
| --- | --- | --- |
| ① | 页头 | 「阅读」主标题 |
| ② | 框架说明卡 | 一段说明：阅读模块将提供 AI 辅助的日语文章阅读与写作工具；联网能力、模型配置就绪后逐个开放（指向「我的」预留设置位） |
| ③ | 工具列表 | 四个规划中工具位：AI 阅读 / 排版 / 撰写 / 语法单词解析；每项一句话描述 + 「规划中」徽标，不可点击 |

数据驱动：工具列表是 `ReadScreen.kt` 顶部的 `ReadTool` 数据清单（id/名称/描述/状态），新增工具 = 加一条数据 + 状态翻 `AVAILABLE` + 补路由（见 §8）。

### 5.4 我的（`ui/mine/MineScreen.kt` 扩展）

| 段 | 区域 | 内容 |
| --- | --- | --- |
| ① | 资料卡 | 圆形首字标识（昵称首字，缺省「日」）+ 昵称（缺省「日语学习者」）+ 「编辑」入口；副行注明「本地资料，仅存本机」；编辑对话框：输入框 + 保存/取消；校验：去空白、长度 1–12，非法拒绝并保持原值 |
| ② | 学习统计 | 原两标签版全部内容：时长/任务紧凑条、模块进度条（动词/形容词/错题掌握）、今日明细 |
| ③ | 书签收藏夹 | 小节 + 计数（0）+ 空态文案：「还没有收藏。阅读功能上线后，可收藏句子与单词」 |
| ④ | 预留设置位 | 「更多设置」小节：模型/API 设置、发音音色、词库导入导出；每项一句话描述 + 「规划中」徽标，不可点击 |
| ⑤ | 统计清零 | 现有按钮与确认弹窗（行为不变） |

---

## 6. 数据与存储（新增两个键）

遵守红线：新键带版本号 + 防御性校验 + 坏数据回退空态并置 `lastLoadInvalid`。

| 键 | 结构 | 读写时机 |
| --- | --- | --- |
| `japanese-verb-practice-profile-v1` | `{ "nickname": "日语学习者" }` | 启动加载；编辑保存 |
| `japanese-verb-practice-bookmarks-v1` | `{ "items": [{ "id", "type", "refId", "title", "note", "createdAt" }] }` | 启动加载；本期无写入入口（阅读上线后启用） |

- `UserProfile` / `Bookmark` / `BookmarkList` 定义在 `storage/Models.kt`；纯函数（`ProfileRules.normalizeNickname`、`BookmarkRules` 校验、`BookmarkList` 增删去重）同文件或相邻文件，**不依赖 Android**，配单测。
- 昵称归一化：trim；空串回落缺省；超长截断还是拒绝——规格定为**拒绝保存**（UI 层提示），存储层仍做长度裁剪防御。
- 书签 `id` 稳定唯一（建议 `type:refId` 或 UUID）；`type` 限定 `sentence` / `word`（为阅读预留）。
- `AppState` 增 `profile` / `bookmarks` 状态与 `updateNickname`；书签增删查 API 就绪但本期无 UI 调用方（测试消费）。

---

## 7. 测试策略（基线只升不降）

| 层 | 新增测试 | 对象 |
| --- | --- | --- |
| 引擎 | `ProverbPickerTest` | 种子随机确定性、不连续重复、索引边界、数据完整性（24 条、字段非空、id 唯一） |
| 引擎 | `SuggestionEngineTest` | 六条规则各自命中、优先级顺序、最多 3 条、新用户/空数据兜底 |
| 存储 | `ProfileBookmarkTest` | 昵称归一化（空白/超长/缺省）、书签校验与增删去重纯函数 |
| 静态 | `styles.test.js` 之外无新增；`font.test.js` 因收集器扩展自动覆盖 Android 文案 | 见 §9 |

既有基线（2026-10-17）：Android 70/70；Web 111/111。改动后复跑，不得低于基线。

---

## 8. 扩展点（本规格的核心交付）

| 扩展场景 | 操作步骤 | 落点 |
| --- | --- | --- |
| 新增底栏标签（第 5 个） | `AppTab` 加一项 → `AppRoot` 的 `when` 加分支 → 新建 Screen 文件 | `MainActivity.kt` |
| 新增学习模块 | `StudyDestination` 加一项 → `StudyScreen` 加入口卡 → `SubPage` 加常量 → `MainActivity` 加映射；随后按 [module-guide.md](module-guide.md) 清单补引擎/存储/测试 | `ui/study/` + `MainActivity.kt` |
| 查词落地 | 学习页预留行转正：`StudyDestination.DICTIONARY` + 新 Flow（按 module-guide 走模块接入清单） | `ui/study/` |
| 阅读工具落地 | `ReadTool` 清单状态翻 `AVAILABLE` + `AppRoot` 加路由 + 按 module-guide 接入（含联网边界说明，D-011） | `ui/read/` + `MainActivity.kt` |
| 新增预留设置位 | 我的页预留列表加一条数据 | `ui/mine/MineScreen.kt` |
| 新增本地存储 | `StorageKeys` + `Models` + `LocalStore` 读写 + `AppState` 状态，四件套齐全 | `storage/` |
| 谚语扩充 | `ProverbData` 加条目 → **重跑字体子集**（见 §9） | `engine/proverb/` |
| 建议规则扩充 | `SuggestionEngine` 加规则 + 补单测（优先级表同步更新本文档） | `engine/suggest/` |
| 两端字体一致性 | 任何客户端新增可见文案后重跑 `web/font/subset.py`（已扩展为同时收集 Android 字面量） | `web/font/` |

---

## 9. 字体子集管线扩展（D-029）

**问题**：`collect-chars.js` 只扫 `web/` 源码，Android 独有文案（本轮 24 条谚语日文 + 新中文 UI 词）不会进入字符表，Android TTF 缺字 → 回退字体 → 行高位移（已知坑：实测缺字使整页下移 5px）。

**方案**：`collect-chars.js` 增加对 `android/app/src/main/java/**/*.kt` 的**双引号字符串字面量**收集（正则提取，覆盖渲染文本、排除大部分注释散文）；`subset.py` 行为不变（单一字符表同时产 woff2 与 Android TTF）。副作用：Web woff2 会纳入少量 Android 独有字符（体积增加约 10–20KB），换取两端不再各有缺字漏网。

**纪律**：改任何一端文案后都要重跑 `python web/font/subset.py`；`tests/font.test.js` 现在同时守护两端文案覆盖。

---

## 10. 关子选定（2026-10-17）

| # | 决策点 | 选定 | 说明 |
| --- | --- | --- | --- |
| Q-A | 实施范围 | **仅 Android 改底栏** | Web 维持侧栏 + 抽屉（D-003 不变）；产品级 IA 在 information-architecture.md 描述 |
| Q-B | 谚语数据 | **内置 24 条** | 原文 + 读音 + 大意；引擎化管理、可单测；后续可扩充 |
| Q-C | 个人信息 | **最小资料卡：昵称可编辑** | 新增 `profile-v1`；不做账号（D-009） |
| Q-D | 预留位呈现 | **可见的「规划中」信息入口** | 不可点击、不模拟可用功能；与 module-guide 占位禁令的边界写进 §4.4 |

---

## 11. 验收标准（EARS）

| 编号 | 功能 | 验收标准 | 优先级 |
| --- | --- | --- | --- |
| AC-01 | 底栏 | While 应用在一级页，系统**必须**展示 4 个标签（主页/学习/阅读/我的），点选后切换且选中态可辨 | P0 |
| AC-02 | 导航 | When 用户从学习页进入二级页，系统**必须**隐藏底栏；返回后落在学习标签 | P0 |
| AC-03 | 谚语 | While 停留在主页，系统**必须**展示一条谚语（原文/读音/大意）；When 点「换一句」，**必须**换到与当前不同的另一条 | P0 |
| AC-04 | 统计 | While 主页可见，系统**必须**展示周历条与本周小结，数值口径与改前一致 | P0 |
| AC-05 | 建议 | While 主页可见，系统**必须**展示知识点建议（≤3 条）；When 点击可执行项，**必须**执行对应跳转 | P0 |
| AC-06 | 学习入口 | When 点击学习页模块入口卡，系统**必须**进入对应二级页且行为与改前一致 | P0 |
| AC-07 | 预留位 | While 学习/阅读/我的页可见，规划中项**必须**可见且标注「规划中」，且点击无跳转、无假交互 | P1 |
| AC-08 | 昵称 | When 用户编辑昵称并保存合法值，系统**必须**持久化；重启后**必须**保留；非法输入（空白/超长）**必须**被拒绝且原值不变 | P1 |
| AC-09 | 书签 | While 书签收藏夹可见，系统**必须**展示空态与计数 0 | P1 |
| AC-10 | 存储 | If 存储文件损坏，系统**必须**回退缺省值并置 invalid 标记，不崩溃 | P0 |
| AC-11 | 测试 | 系统**必须**通过 `./gradlew :app:testDebugUnitTest` 且条数 ≥ 70 + 新增用例 | P0 |
| AC-12 | 构建 | 系统**必须**通过 `./gradlew :app:assembleDebug` | P0 |
| AC-13 | 字体 | 新增文案字符**必须**全部在 Android TTF 子集内（重跑 subset.py 后 `font.test.js` 通过） | P0 |
| AC-14 | 回归 | 动词/形容词/助词/错题本既有行为**必须**无回归（含错题排期、掌握度口径） | P0 |

---

## 12. 内嵌已知坑（从项目记忆拉取）

| 坑 | 根因 | 修法 |
| --- | --- | --- |
| 缺字整页下移 5px | 子集漏字回退系统字体 | 改文案重跑 subset.py；本轮已把 Android 字面量纳入收集（D-029） |
| 安装后界面「没生效」 | `install -r` 不杀旧进程 | `am force-stop <包名>` 再启动 |
| uiautomator dump 读到旧文件 |  dump 落盘缓存 | 关键判定用截图；或先 `rm -f /sdcard/ui.xml` |
| adb 静默失败 | MSYS 路径转换 | 本地路径用 `E:/...`，设备侧路径才需 `MSYS_NO_PATHCONV=1` |
| Kotlin init 顺序 | 初始化写在被读属性之前 | 先确认声明顺序 |
| 存储键无版本号/无校验 | 坏数据整键报废 | 新键带 `-v1` + 字段校验 + 回退空态 |
| 硬编码颜色/emoji 图标 | 视觉体系漂移 | 颜色只走 `Theme.kt`；图标 Material Icons + 两枚新增 VectorDrawable |

---

## 13. 端到端验证步骤

```bash
# 1. 单元测试（在 android/ 下；Windows 需先导出 JDK）
export JAVA_HOME=/c/Users/xiao_/tools/jdk-extract/jdk-21.0.12.1+1
export PATH="/c/Users/xiao_/tools/jdk-extract/jdk-21.0.12.1+1/bin:$PATH"
./gradlew :app:testDebugUnitTest     # 断言：全绿且条数 ≥ 70 + 新增

# 2. 编译
./gradlew :app:assembleDebug         # 断言：成功

# 3. 字体子集（在 web/ 下；需要 fonttools + brotli）
python font/subset.py                # 断言：Android 子集覆盖全部新文案

# 4. Web 字体契约测试（在 web/ 下）
node --test tests/font.test.js       # 断言：通过

# 5. 模拟器实测（若设备在线）
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.nihongodoushi.app
# 人工/截图断言：四标签切换；谚语与换一句；建议点击跳转；学习页四入口；
# 阅读/我的规划中位不可点；昵称编辑重启保留；
adb shell run-as com.nihongodoushi.app cat files/japanese-verb-practice-profile-v1.json
```

---

## 14. 实现记录（2026-10-17 落地）

### 14.1 实现概览

| 层 | 落点 | 说明 |
| --- | --- | --- |
| 引擎 | `engine/proverb/`（ProverbData 24 条 + ProverbPicker）、`engine/suggest/`（SuggestionEngine 六规则） | 纯 Kotlin 无 Android 依赖；建议 id 与文案进引擎，图标/色调映射留在 UI |
| 存储 | `StorageKeys`（profile-v1 / bookmarks-v1）、`Models`（UserProfile/NicknameCheck/Bookmark/BookmarkList）、`LocalStore`（防御性读写）、`AppState`（profile/bookmarks 状态 + updateNickname） | 书签增删纯函数就绪，本期无 UI 写入入口 |
| UI | `MainActivity`（AppTab 4 项 + 图标单点解析）、`ui/study/StudyScreen`、`ui/read/ReadScreen`（ReadTool 数据清单）、`ui/home/` 重写（ProverbCard/SuggestionSection 拆分）、`ui/mine/` 扩展（MineProfileCard/MineStats 拆分）、`ui/common/ModuleUi`（EntryCard 迁入 + PlannedBadge/PlannedRow） | 单文件 ≤300 行通过拆分达成；既有 bottomInset/BackHandler/错题来源逻辑未动 |
| 图标 | `res/drawable/ic_tab_study.xml`（学士帽）、`ic_tab_read.xml`（打开的书） | 24dp 单色填充，tint 基底 #FF000000 |
| 字体 | `web/font/collect-chars.js` 扩展收集 Android 字面量（D-029）+ `subset.py` 重跑：1218 字符，woff2 361KB，Android TTF 655KB | 两端同源；此前 Android 独有的 ざぢぱぴぷぺ 等缺字一并修复 |

### 14.2 验证结果（模拟器 qa36 实测 + 独立复跑）

| 项 | 结果 |
| --- | --- |
| 单元测试 | **101/101**（基线 70 + ProverbPicker 8 + SuggestionEngine 9 + ProfileBookmark 14），QA 独立复跑确认 |
| 构建 | `:app:assembleDebug` BUILD SUCCESSFUL |
| Web 基线 | 113/113（含扩展后的 font.test.js 字符表契约） |
| emoji 扫描 | 新增/修改 .kt 与 .xml 零命中 |
| AC-01～AC-14 | 全部 PASS（P0 归零）；截图 15 张见 `docs/design/four-tab-ui/` |
| 落盘核对 | 昵称保存后 `files/japanese-verb-practice-profile-v1.json` 内容与 UI 一致 |
| 防游戏化检查 | 测试含独立断言非抽样、无对实现的镜像断言、词库改动有护栏测试 |

### 14.3 已知限制与说明

- **模拟器无法输入中文昵称**（`adb input text` 仅 ASCII）：AC-08 的持久化验证用 ASCII 昵称实测，中文昵称的归一化/校验由 ProfileBookmarkTest 单测覆盖（14 条）。
- 损坏 profile 存档在真机验证后已清理，设备存储恢复干净状态。
- 建议 R3 的助词掌握度副文案为「进入助词学习」（与练习入口命名一致）。

---

## 15. 变更记录

| 日期 | 变更 | 原因 |
| --- | --- | --- |
| 2026-10-17 | 初版：四标签 IA、各页规格、扩展点、存储、验收标准；关子选定 Q-A～Q-D | 关子提出底栏四标签重设计 |
| 2026-10-17 | 实现并实测（101/101 单测、模拟器 15 截图、AC 全过），状态翻「已实现」 | 选定后落地 |
