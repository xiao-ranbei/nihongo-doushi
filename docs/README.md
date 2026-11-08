# 项目文档索引

本目录同时记录「言の葉」（ことのは）的产品目标、目标设计和当前代码状态。目标文档描述“准备建设什么”，当前状态文档描述“代码现在是什么”；不要把尚未实现的设计当成现有功能。

## 文档地图

| 文档 | 用途 | 状态 |
| --- | --- | --- |
| [product-overview.md](product-overview.md) | 产品愿景、范围、平台关系与设计原则 | 已确认方向 |
| [information-architecture.md](information-architecture.md) | 学习主页、模块导航、主要视图与用户流程 | Web 与 Android 主页/导航已实现 |
| [basic-design.md](basic-design.md) | Web 与 Android 的目标技术结构、数据边界和共享服务 | 目标设计；Android MVP 已实现，衍生能力待补 |
| [android-requirements.md](android-requirements.md) | Android MVP 范围、发布、离线、数据和待决技术项 | 需求已确认；MVP 已实现 |
| [module-guide.md](module-guide.md) | 新增日语学习模块的设计和接入清单 | 扩展约定 |
| [current-state.md](current-state.md) | 当前网页代码、功能、存储和测试基线 | 当前实现 |
| [decisions.md](decisions.md) | 已确认的产品／架构选择与延后决定 | 决策记录 |
| [known-issues.md](known-issues.md) | 遗留问题登记册：未完成实现、已知缺陷、技术债务与复核方法 | 持续更新（2026-10-17 建立，20 项） |
| [deployment-github-pages.md](deployment-github-pages.md) | GitHub Pages 首次启用、自动部署和本地数据说明 | 部署操作指南 |
| [navigation-prototypes.html](navigation-prototypes.html) | 可点击的网页导航方案原型 | 讨论用原型，不是正式页面 |
| [android-redesign-proposal.md](android-redesign-proposal.md) | 选择题答题界面、应用图标、视觉层次与交互逻辑的重设计方案 | 待确认；不含代码变更 |
| [android-answer-screen-spec.md](android-answer-screen-spec.md) | 已确认的答题界面结构与视觉布局说明（方案 A + 底部判定抽屉、图标方向 1） | 已实现 |
| [design/answer-choice-prototypes.html](design/answer-choice-prototypes.html) | 三版选择题答题界面可交互原型（对比用） | 讨论用原型，不是正式页面 |
| [design/answer-screen-A-drawer.html](design/answer-screen-A-drawer.html) | 确认版原型：方案 A + 底部判定抽屉（7 个状态/标注视图） | 讨论用原型，不是正式页面 |
| [android-two-tab-spec.md](android-two-tab-spec.md) | 底栏两标签（主页/我的）重设计：结构、层级、视觉、数据口径、选定清单与实现记录 | 已实现 |
| [android-particle-module-spec.md](android-particle-module-spec.md) | Android 助词学习模块：信息架构与页面流程、UI 布局与状态、学习闭环、教学策略与取舍 | 已实现（D-016～D-022，8b9d040/1ba8b4c；四标签后入口在「学习」页） |
| [design/particle-prototypes.html](design/particle-prototypes.html) | Android 助词模块四版可交互原型（专题地图 / 学习路径 / 直接开练 / 对开对比）与横向对比 | 已选定 A（2026-10-17） |
| [android-module-three-tier-refactor.md](android-module-three-tier-refactor.md) | 动词／形容词模块三层改造：模块首页 + 讲解页 + 练习屏，含内容划分、跳转、视觉规范与关键点 | 已实现（2026-10-17） |
| [android-four-tab-spec.md](android-four-tab-spec.md) | 底栏四标签框架（主页/学习/阅读/我的）：IA、各页规格、扩展点、存储、验收标准 | 已实现（2026-10-17） |
| [design/four-tab-design.md](design/four-tab-design.md) | 四标签四页界面设计说明：分区结构、令牌清单、图标选型、状态矩阵与动效 | 已实现 |
| [android-read-analyzer-spec.md](android-read-analyzer-spec.md) | Android 阅读模块首个联网能力「AI 文章解析」：联网边界（D-011 落地）、三次调用契约、存储三键、UI 与导航改造、五批实施、EARS 验收 | 已实现（批 1–3 落地：领域层、网络层、设置页、阅读 UI 与导航；批 4 收口文档与字体子集） |
| [android-read-analyzer-next-steps.html](android-read-analyzer-next-steps.html) | 「AI 文章解析」交付后待办清单：准备模型服务、Gradle 构建与单测、真机 13 条走查、字体管线复核、4 个提交 | 交付后待办（需在本机终端执行） |
| [android-theme-spec.md](android-theme-spec.md) | Android 双皮肤主题规范：为什么两套（浅色糖果卡／深色墨夜）、四个取色入口、配色与几何令牌表、平台层 windowBackground、与原型的关系、验收口径 | 已实现（2026-10-22） |
| [workflow-review-two-increments.md](workflow-review-two-increments.md) | 两次追加功能的工作流复盘：目标、步骤、模块依赖、异同与演进、注意事项与可复用模板 | 参考文档 |
| [design/home-mine-prototypes.html](design/home-mine-prototypes.html) | 主页/我的 三版可交互原型（框内底栏可切换） | 讨论用原型，不是正式页面 |
| [web-read-analyzer-plan.md](web-read-analyzer-plan.md) | Web 端文章分析分批实施计划：参考项目全能力逐项适配、四次调用契约、成本与隐私口径、B0～B9 批次与验收、闸门清单 | 已实现（B0–B9 收口，2026-11-06；Web 396/396） |
| [design/icon-concepts/](design/icon-concepts/) | 应用图标方向 1「あ・纸页」资产（已轮廓化，含 VectorDrawable）与生成/自检脚本 | 可接入 |
| [android-liquid-glass-assessment.md](android-liquid-glass-assessment.md) | 酷安「液体玻璃」与 AndroidLiquidGlass 关系澄清：视觉/模块边界/系统能力/性能兼容性，复用评估与全量 UI 翻新重构规划 | 调研评估（2026-11-07，不含实现与 UX 定案） |
| [design/liquid-glass/index.html](design/liquid-glass/index.html) | 安卓端 UI 翻新三方案可交互原型并列对照（纸感延续 / 全量液态玻璃 / 结构化线框），9 视图同一 IA 与操作路径，含特点/场景/优缺点/取舍 | 可预览原型（2026-11-07；已选定方案 B；2026-11-08 追加悬浮岛 + 统一圆角 24） |
| [android-liquid-glass-plan-b.md](android-liquid-glass-plan-b.md) | **方案 B 唯一基准**的全量 UI 翻新与底层重构计划：目标范围、模块与交付物、R0–R3 任务与依赖、风险、Q-B 决策、性能阈值 T1–T10、验证方式与 AC-01～AC-14 验收；含基准冻结、规格基线、A/C 禁止清单与旧决策废止 | **执行中**（2026-11-08：R0.1 + R1.1–R1.5 已落地并通过实机走查，进度表见 §3.7；Q-B1/B2/B3/B7 已确认，Q-B8 覆盖浮法 / Q-B9 圆角 24 已冻结） |

## 信息来源与更新规则

- [README.md](../README.md) 是仓库快速入口；[web/README.md](../web/README.md) 说明网页端运行方式、测试和资源维护。
- 产品范围变化时更新 `product-overview.md`；主页、导航或主要操作流程变化时更新 `information-architecture.md`。
- 目标架构、模块边界或数据策略变化时更新 `basic-design.md` 和 `decisions.md`。
- 新增或修改实际代码后更新 `current-state.md`，不要用它代替目标设计。
- 新增模块前按 `module-guide.md` 梳理模块；有未决选项时写入对应文档的“待确认”部分，不把推测写成已确认需求。

旧版 `project-summary.md` 中仍有效的实现边界、测试约定和视觉原则已并入 `current-state.md` 与 `module-guide.md`；过期的未来方向和旧测试基线不再作为规范。
