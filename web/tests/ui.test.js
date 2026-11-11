"use strict";

// UI 冒烟测试：用最小 DOM 桩在 node 里原样加载 app.js，覆盖真实交互路径。
// 重点不是像素，而是「DOM 接线没断」——任何一个 getElementById 拿到不存在的
// id、任何一次状态迁移没刷新对应节点，都会在这里失败。

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const engine = require("../verb-engine.js");
const honorificEngine = require("../honorific-engine.js");
const adjEngine = require("../adjective-engine.js");
const particleEngine = require("../particle-engine.js");
const { createApp } = require("./helpers/dom-stub.js");

const MISTAKES_KEY = "japanese-verb-practice-mistakes-v1";
const STATS_KEY = "japanese-verb-practice-stats-v1";
const ADJ_STATS_KEY = "japanese-verb-practice-adj-stats-v1";
const PARTICLE_MISTAKES_KEY = "japanese-particle-practice-mistakes-v1";
const PARTICLE_MISTAKES_V2_KEY = "japanese-particle-practice-mistakes-v2";
const PARTICLE_STATS_KEY = "japanese-particle-practice-stats-v1";
const DAILY_KEY = "japanese-verb-practice-daily-v1";

// 从页面当前展示反推正确答案：动词辞书形 + 目标 pill（「语态 · 活用」）。
function currentAnswer(app) {
  const word = app.$("question-word").textContent;
  const verb = engine.VERBS.filter(function (item) {
    return item.dictionary === word;
  })[0];
  assert.ok(verb, "题目动词应存在于动词表：" + word);

  const parts = app.$("target-pill").textContent.split(" · ");
  const inflectionLabel = parts[parts.length - 1];
  const voiceLabel = parts.length > 1 ? parts[0] : "原形";
  const voice = engine.VOICES.filter(function (item) {
    return item.label === voiceLabel;
  })[0];
  const inflection = engine.INFLECTIONS.filter(function (item) {
    return item.label === inflectionLabel;
  })[0];
  assert.ok(voice, "目标 pill 的语态标签应可解析：" + app.$("target-pill").textContent);
  assert.ok(inflection, "目标 pill 的活用标签应可解析：" + app.$("target-pill").textContent);

  return engine.conjugate(verb, voice.id, inflection.id).written;
}

function currentQuestionKey(app) {
  const word = app.$("question-word").textContent;
  const verb = engine.VERBS.filter(function (item) {
    return item.dictionary === word;
  })[0];
  const parts = app.$("target-pill").textContent.split(" · ");
  const inflectionLabel = parts[parts.length - 1];
  const voiceLabel = parts.length > 1 ? parts[0] : "原形";
  const voice = engine.VOICES.filter(function (item) {
    return item.label === voiceLabel;
  })[0];
  const inflection = engine.INFLECTIONS.filter(function (item) {
    return item.label === inflectionLabel;
  })[0];

  assert.ok(verb, "题目动词应存在于动词表：" + word);
  assert.ok(voice, "目标 pill 的语态标签应可解析：" + app.$("target-pill").textContent);
  assert.ok(inflection, "目标 pill 的活用标签应可解析：" + app.$("target-pill").textContent);
  return [verb.id, voice.id, inflection.id].join("::");
}

function inflectionLabelOf(app) {
  const parts = app.$("target-pill").textContent.split(" · ");
  return parts[parts.length - 1];
}

function tab(app, viewId) {
  if (viewId === "home") {
    const homeLink = app.$("brand-home");
    app.click(homeLink);
    return homeLink;
  }
  if (viewId === "practice" || viewId === "reference" || viewId === "honorific") {
    const moduleTab = app.document.querySelectorAll("[data-view]").filter(function (node) {
      return node.dataset.view === "verb";
    })[0];
    assert.ok(moduleTab, "应存在动词模块导航");
    app.click(moduleTab);
    const subtab = app.document.querySelectorAll("[data-verb-subview]").filter(function (node) {
      return node.dataset.verbSubview === viewId;
    })[0];
    assert.ok(subtab, "应存在动词模块视图：" + viewId);
    app.click(subtab);
    return subtab;
  }
  const found = app.document.querySelectorAll("[data-view]").filter(function (node) {
    return node.dataset.view === viewId;
  })[0];
  assert.ok(found, "应存在 data-view=" + viewId + " 的标签页");
  app.click(found);
  return found;
}

test("网页 favicon 使用左上角的日字品牌标识", function () {
  const indexHtml = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
  const iconLink = /<link rel="icon" type="image\/svg\+xml" href="([^"]+)">/.exec(indexHtml);
  assert.ok(iconLink, "页面应声明 SVG favicon");
  assert.equal(iconLink[1], "./icon/favicon.svg");

  const iconSvg = fs.readFileSync(path.join(__dirname, "..", "icon", "favicon.svg"), "utf8");
  assert.match(iconSvg, /<text[\s\S]*?>日<\/text>/);
  assert.match(iconSvg, /fill="#326151"/);
});

test("初始化：注入字体、渲染首题并默认显示学习主页", function () {
  const app = createApp();
  const injected = app.document.head.querySelectorAll("style").filter(function (node) {
    return /@font-face/.test(node.textContent);
  });
  assert.equal(injected.length, 1, "应根据字体清单注入一条 @font-face 样式");

  assert.match(app.$("question-word").textContent, /\S/, "首题应渲染出动词");
  assert.equal(app.$("view-home").hidden, false);
  assert.equal(app.$("view-verb").hidden, true);
  assert.equal(app.$("view-practice").hidden, false, "动词模块默认子视图为练习");
  assert.equal(app.$("view-reference").hidden, true);
  assert.equal(app.$("view-adjective").hidden, true);
  assert.equal(app.$("view-particle").hidden, true);
  assert.equal(app.$("view-mistakes").hidden, true);
  assert.equal(app.document.querySelectorAll(".module-subnav").length, 3, "三个模块应共用页签导航");
  assert.equal(app.document.querySelectorAll(".module-subtab").length, 8, "动词三页签 + 形容词两页签 + 助词三页签");
  assert.equal(app.document.querySelectorAll(".subtabs").length, 0, "不应保留形容词专属页签样式");
  assert.equal(app.$("particle-view-practice").hidden, false);
  assert.equal(app.$("particle-view-learn").hidden, true);
  assert.equal(app.$("particle-controls-panel").classList.contains("filter-bar"), true);
  assert.equal(app.$("particle-question-card").classList.contains("practice-card"), true);
  assert.equal(
    app.$("particle-practice-stats").parentNode.classList.contains("practice-card-head"),
    true
  );
  assert.equal(app.$("particle-correct-count").textContent, "0");
  assert.equal(app.$("particle-incorrect-count").textContent, "0");
  assert.equal(app.$("particle-reset-stats").disabled, true);
  assert.equal(
    particleEngine.getQuestion(app.$("particle-question-card").getAttribute("data-question-id")).type,
    "fill",
    "助词模块应预备综合练习的第一道选助词题"
  );
  assert.equal(app.document.querySelectorAll(".particle-lesson-card").length, particleEngine.LESSONS.length);
  assert.equal(app.$("home-today-total").textContent, "0 题");
  assert.equal(app.$("home-active-module-count").textContent, "0");
  assert.equal(app.$("review-banner").hidden, true);
  assert.equal(app.$("feedback-panel").hidden, true);
  assert.equal(
    app.document.querySelector(".footer").parentNode,
    app.document.querySelector(".app-main"),
    "页脚应位于主内容滚动区域内"
  );
  assert.equal(app.$("question-index").textContent, "随机练习");
  assert.equal(app.$("question-group").textContent, "动词");
  assert.equal(app.$("question-reading").textContent, "假名");
  assert.equal(app.document.querySelector(".verb-prompt").getAttribute("aria-expanded"), "false");
  assert.equal(app.document.querySelector(".practice-card").classList.contains("is-answer-correct"), false);
  assert.equal(app.document.querySelector(".practice-card").classList.contains("is-answer-incorrect"), false);
});

test("导航：侧栏可折叠，窄屏抽屉可打开并关闭", function () {
  const app = createApp();
  const homeLinks = app.document.querySelectorAll("[data-home-link]");
  assert.equal(homeLinks.length, 2, "桌面侧栏和窄屏顶部品牌都应能返回主页");
  assert.equal(homeLinks[0].getAttribute("href"), "./index.html");
  assert.equal(homeLinks[1].getAttribute("href"), "./index.html");
  assert.equal(
    app.document.querySelectorAll("[data-view]").filter(function (node) {
      return node.dataset.view === "particle";
    }).length,
    1,
    "侧栏应仅展示一个助词模块入口"
  );
  assert.equal(
    app.document.querySelectorAll("[data-view]").some(function (node) {
      return node.dataset.view === "home";
    }),
    false,
    "侧栏不应重复显示主页按钮"
  );
  assert.ok(app.$("sidebar-collapse").querySelector("svg"), "侧栏控制应使用箭头图标");
  assert.equal(app.$("sidebar-collapse").querySelector(".nav-label"), null);
  assert.equal(app.$("sidebar-collapse").getAttribute("aria-label"), "折叠侧栏");

  app.$("app-main").scrollTop = 120;
  tab(app, "verb");
  assert.equal(app.$("app-main").scrollTop, 0, "切换模块应滚回内容顶部");
  app.click(app.$("sidebar-collapse"));
  assert.equal(app.$("app-shell").classList.contains("is-sidebar-collapsed"), true);
  assert.equal(app.$("sidebar-collapse").getAttribute("aria-pressed"), "true");
  assert.equal(app.$("sidebar-collapse").title, "展开侧栏");
  app.$("app-main").scrollTop = 120;
  const brandClick = app.click(app.$("brand-home"));
  assert.equal(brandClick.defaultPrevented, true, "主页链接应在应用内导航，避免重新加载");
  assert.equal(app.$("app-shell").classList.contains("is-sidebar-collapsed"), true);
  assert.equal(app.$("view-home").hidden, false);
  assert.equal(app.$("app-main").scrollTop, 0, "返回主页时应滚回内容顶部");
  app.click(app.$("sidebar-collapse"));
  assert.equal(app.$("app-shell").classList.contains("is-sidebar-collapsed"), false);
  assert.equal(app.$("sidebar-collapse").title, "折叠侧栏");

  app.click(app.$("mobile-nav-toggle"));
  assert.equal(app.$("mobile-nav-toggle").getAttribute("aria-expanded"), "true");
  assert.equal(app.$("sidebar-backdrop").hidden, false);
  tab(app, "verb");
  assert.equal(app.$("mobile-nav-toggle").getAttribute("aria-expanded"), "false");
  assert.equal(app.$("sidebar-backdrop").hidden, true);
  assert.equal(app.$("view-verb").hidden, false);
  app.click(homeLinks[1]);
  assert.equal(app.$("view-home").hidden, false, "窄屏顶部品牌应返回主页");
  assert.equal(homeLinks[1].getAttribute("aria-current"), "page");
});

test("助词综合练习默认交替两种题型并支持专项切换", function () {
  const app = createApp();
  tab(app, "particle");
  assert.equal(app.$("particle-view-practice").hidden, false);
  assert.equal(app.$("particle-view-learn").hidden, true);
  assert.equal(app.$("particle-level-select").tagName, "SELECT");
  assert.equal(app.$("particle-practice-mode-select").tagName, "SELECT");
  assert.equal(app.$("particle-view-title").textContent, "在句子中练习");
  const learnSubview = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "learn";
  })[0];
  app.click(learnSubview);
  assert.equal(app.$("particle-view-title").textContent, "先看语境，再选助词");
  assert.equal(app.$("particle-controls-panel").classList.contains("is-single-column"), true);
  assert.equal(app.$("particle-practice-modes").hidden, true);
  const practiceSubview = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "practice";
  })[0];
  app.click(practiceSubview);
  assert.equal(app.$("particle-view-title").textContent, "在句子中练习");
  assert.equal(app.$("particle-controls-panel").classList.contains("is-single-column"), false);

  const firstQuestion = particleEngine.getQuestion(
    app.$("particle-question-card").getAttribute("data-question-id")
  );
  assert.equal(firstQuestion.type, "fill", "综合练习从选助词题开始");
  const fillOption = app.$("particle-options").querySelectorAll("button").filter(function (button) {
    return firstQuestion.acceptedAnswers.includes(button.textContent);
  })[0];
  assert.ok(fillOption);
  app.click(fillOption);
  app.click(app.$("particle-check-answer"));
  app.click(app.$("particle-next-question"));

  const functionQuestion = particleEngine.getQuestion(
    app.$("particle-question-card").getAttribute("data-question-id")
  );
  const originalFunctionOptions = functionQuestion.options.slice();
  assert.equal(functionQuestion.type, "function", "第二题应判断目标助词的作用");
  assert.match(app.$("particle-question-progress").textContent, /综合练习/);
  assert.equal(app.$("particle-answer-slot").textContent, functionQuestion.targetParticle);
  assert.equal(app.$("particle-answer-slot").classList.contains("is-target-particle"), true);
  assert.equal(app.$("particle-question-translation").hidden, true, "作答前不显示句子译文");
  assert.match(app.$("particle-question-prompt").textContent, new RegExp(functionQuestion.targetParticle));
  assert.equal(app.$("particle-options").querySelectorAll("button").length, 4);

  const wrongOption = app.$("particle-options").querySelectorAll("button").filter(function (button) {
    return !functionQuestion.acceptedAnswers.includes(button.textContent);
  })[0];
  app.click(wrongOption);
  assert.equal(app.$("particle-answer-slot").textContent, functionQuestion.targetParticle, "选择解释时保留高亮助词");
  app.click(app.$("particle-check-answer"));
  assert.equal(app.$("particle-feedback").classList.contains("is-incorrect"), true);
  assert.equal(app.$("particle-question-translation").hidden, false, "判题后显示句子译文");
  assert.equal(app.$("particle-correct-count").textContent, "1");
  assert.equal(app.$("particle-incorrect-count").textContent, "1");
  assert.equal(app.$("particle-total-count").textContent, "2");
  assert.equal(app.$("particle-accuracy").textContent, "50%");

  const reviewTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "review";
  })[0];
  app.click(reviewTab);
  const interpretedSentence = functionQuestion.before
    + functionQuestion.targetParticle
    + functionQuestion.after;
  assert.ok(
    app.$("particle-review-list").textContent.includes(interpretedSentence),
    "作用判断错题应以完整句子进入复习列表"
  );
  assert.equal(
    app.$("particle-review-list").querySelector(".particle-review-target").textContent,
    functionQuestion.targetParticle,
    "作用判断错题应继续标亮目标助词"
  );
  const practiceTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "practice";
  })[0];
  app.click(practiceTab);

  const functionModeSelect = app.$("particle-practice-mode-select");
  functionModeSelect.value = "function";
  functionModeSelect.dispatch("change");
  const functionOnlyQuestion = particleEngine.getQuestion(
    app.$("particle-question-card").getAttribute("data-question-id")
  );
  assert.equal(functionOnlyQuestion.type, "function");
  assert.equal(functionModeSelect.value, "function");
  assert.equal(app.$("particle-practice-mode-hint").textContent, "集中判断助词在句中的作用");

  const fillModeSelect = app.$("particle-practice-mode-select");
  fillModeSelect.value = "fill";
  fillModeSelect.dispatch("change");
  const fillOnlyQuestion = particleEngine.getQuestion(
    app.$("particle-question-card").getAttribute("data-question-id")
  );
  assert.equal(fillOnlyQuestion.type, "fill");
  assert.deepEqual(functionQuestion.options, originalFunctionOptions, "随机化选项不能改写题库顺序");
});

test("助词语境练习可复习错题并计入每日练习", function () {
  const app = createApp();
  tab(app, "particle");
  assert.equal(app.$("view-particle").hidden, false);
  assert.equal(app.$("particle-view-practice").hidden, false);
  app.$("app-main").scrollTop = 120;
  const advancedLevelSelect = app.$("particle-level-select");

  const practiceTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "practice";
  })[0];
  app.click(practiceTab);
  assert.equal(app.$("app-main").scrollTop, 0, "切换助词子视图应回到内容顶部");
  const questionId = app.$("particle-question-card").getAttribute("data-question-id");
  const question = particleEngine.getQuestion(questionId);
  assert.ok(question, "当前助词题应能在题库中找到");
  assert.equal(app.$("particle-question-context").textContent, question.context);

  const wrongOption = app.$("particle-options").querySelectorAll("button").filter(function (button) {
    return !question.acceptedAnswers.includes(button.textContent);
  })[0];
  assert.ok(wrongOption, "题目应有可用于判错的选项");
  app.click(wrongOption);
  app.click(app.$("particle-check-answer"));
  assert.equal(app.$("particle-feedback").classList.contains("is-incorrect"), true);
  const loggedRecord = JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_V2_KEY))[0];
  assert.equal(loggedRecord.questionId, questionId, "答错应写入 v2 错题记录");
  assert.equal(loggedRecord.count, 1);
  assert.equal(loggedRecord.correctStreak, 0);
  assert.ok(Date.parse(loggedRecord.dueAt) > Date.now(), "新错题应排到一天后");
  assert.equal(JSON.parse(app.window.localStorage.getItem(DAILY_KEY)).particleAnswers, 1);

  const reviewTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "review";
  })[0];
  app.click(reviewTab);
  assert.equal(app.$("particle-review-count").textContent, "本地保存 1 道助词错题 · 0 道今天到期");
  assert.match(app.document.querySelector(".particle-review-meta").textContent, /错 1 次 · 下次复习/);
  app.click(app.$("particle-start-review"));
  assert.equal(app.$("particle-question-card").getAttribute("data-question-id"), questionId);
  assert.equal(app.$("particle-practice-modes").hidden, true, "错题复习不显示专项模式切换");

  const correctOption = app.$("particle-options").querySelectorAll("button").filter(function (button) {
    return question.acceptedAnswers.includes(button.textContent);
  })[0];
  assert.ok(correctOption, "错题复习应提供参考答案选项");
  app.click(correctOption);
  app.click(app.$("particle-check-answer"));
  assert.equal(app.$("particle-feedback").classList.contains("is-correct"), true);
  app.click(app.$("particle-next-question"));

  assert.equal(app.$("particle-view-review").hidden, false);
  assert.equal(
    app.$("particle-review-count").textContent,
    "本地保存 1 道助词错题 · 0 道今天到期",
    "答对一次只推进间隔，不立刻移出（D-022）"
  );
  assert.equal(app.$("particle-review-empty").hidden, true);
  const advancedRecord = JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_V2_KEY))[0];
  assert.equal(advancedRecord.correctStreak, 1);
  assert.equal(advancedRecord.intervalDays, 1);
  assert.ok(Date.parse(advancedRecord.dueAt) > Date.now(), "下一次复习应排到一天后");
  assert.equal(JSON.parse(app.window.localStorage.getItem(DAILY_KEY)).particleAnswers, 2);

  const learnTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "learn";
  })[0];
  app.click(learnTab);
  advancedLevelSelect.value = "advanced";
  advancedLevelSelect.dispatch("change");
  app.click(practiceTab);
  const advancedQuestion = particleEngine.getQuestion(app.$("particle-question-card").getAttribute("data-question-id"));
  assert.equal(advancedQuestion.level, "advanced");
});

test("练习统计位于各自随机练习卡片的标题区", function () {
  const app = createApp();
  const verbStats = app.document.querySelector(".practice-stats");
  const adjectiveStats = app.$("adj-practice-stats");
  const particleStats = app.$("particle-practice-stats");

  assert.equal(verbStats.parentNode.classList.contains("practice-card-head"), true);
  assert.equal(adjectiveStats.parentNode.classList.contains("practice-card-head"), true);
  assert.equal(particleStats.parentNode.classList.contains("practice-card-head"), true);
  assert.equal(particleStats.classList.contains("practice-stats-inline"), true);
  assert.equal(app.document.querySelectorAll(".practice-stats-title").length, 0);
});

test("动词卡片点击或键盘可切换提示，检查后显示且下一题重置", function () {
  const app = createApp();
  const prompt = app.document.querySelector(".verb-prompt");
  const word = app.$("question-word").textContent;
  const verb = engine.VERBS.filter(function (item) {
    return item.dictionary === word;
  })[0];
  const groupLabels = {
    godan: "五段动词",
    ichidan: "一段动词",
    irregular: "不规则动词"
  };

  app.click(prompt);
  assert.equal(app.$("question-group").textContent, groupLabels[verb.group]);
  assert.equal(app.$("question-reading").textContent, verb.reading);
  assert.equal(prompt.getAttribute("aria-expanded"), "true");

  app.click(prompt);
  assert.equal(app.$("question-group").textContent, "动词");
  assert.equal(app.$("question-reading").textContent, "假名");
  assert.equal(prompt.getAttribute("aria-expanded"), "false");

  prompt.dispatch("keydown", { key: "Enter" });
  assert.equal(prompt.getAttribute("aria-expanded"), "true");
  prompt.dispatch("keydown", { key: " " });
  assert.equal(prompt.getAttribute("aria-expanded"), "false");

  app.answer(currentAnswer(app));
  assert.equal(prompt.getAttribute("aria-expanded"), "true");
  assert.equal(app.$("question-group").textContent, groupLabels[verb.group]);
  assert.equal(app.$("question-reading").textContent, verb.reading);

  app.click(app.$("next-question"));
  assert.equal(app.$("question-group").textContent, "动词");
  assert.equal(app.$("question-reading").textContent, "假名");
  assert.equal(prompt.getAttribute("aria-expanded"), "false");
  assert.equal(app.document.querySelector(".practice-card").classList.contains("is-answer-correct"), false);
  assert.equal(app.document.querySelector(".practice-card").classList.contains("is-answer-incorrect"), false);
});

test("答对：显示 ✓、统计 +1、不记入错题", function () {
  const app = createApp();
  app.answer(currentAnswer(app));
  const practiceCard = app.document.querySelector(".practice-card");

  assert.equal(app.$("feedback-panel").hidden, false);
  assert.equal(app.$("feedback-mark").textContent, "✓");
  assert.equal(app.$("feedback-title").textContent, "答对了");
  assert.equal(app.$("correct-answer-count").textContent, "1");
  assert.equal(app.$("incorrect-answer-count").textContent, "0");
  assert.equal(app.$("mistake-count").textContent, "0");
  assert.equal(app.$("answer-input").disabled, true, "作答后输入框应锁定");
  assert.equal(app.$("next-question").hidden, false);
  assert.equal(app.$("next-question").focused, true, "答题后应将键盘焦点交给下一题");
  assert.equal(app.document.querySelector(".verb-prompt").getAttribute("aria-expanded"), "true");
  assert.equal(practiceCard.classList.contains("is-answer-correct"), true);
  assert.equal(practiceCard.classList.contains("is-answer-incorrect"), false);
});

test("答错：记入错题、渲染卡片、写入本地存储", function () {
  const app = createApp();
  app.answer("ぜったいにまちがい");
  const practiceCard = app.document.querySelector(".practice-card");

  assert.equal(app.$("feedback-mark").textContent, "!");
  assert.equal(app.$("feedback-panel").classList.contains("is-wrong"), true);
  assert.equal(app.$("incorrect-answer-count").textContent, "1");
  assert.equal(app.$("mistake-count").textContent, "1");
  assert.equal(app.document.querySelector(".verb-prompt").getAttribute("aria-expanded"), "true");
  assert.equal(practiceCard.classList.contains("is-answer-correct"), false);
  assert.equal(practiceCard.classList.contains("is-answer-incorrect"), true);

  tab(app, "mistakes");
  assert.equal(app.$("view-mistakes").hidden, false);
  assert.equal(app.$("verb-mistakes-list").querySelectorAll(".mistake-card").length, 1);

  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY));
  assert.equal(stored.length, 1);
  assert.equal(stored[0].count, 1);
  assert.equal(stored[0].wrongAnswer, "ぜったいにまちがい");
});

test("罗马字无法识别时明示按原样比对（IK-10）", function () {
  const app = createApp();
  // "hello" 含罗马字表之外的字母 l，归一化整体回退原串
  app.answer("hello");

  assert.equal(app.$("feedback-mark").textContent, "!");
  assert.match(app.$("feedback-user-answer").textContent, /罗马字未能识别，按原样比对/);
  assert.match(app.$("toast").textContent, /无法识别该罗马字拼写/);
  assert.equal(app.$("toast").classList.contains("is-error"), true);
  // 仍按既有语义记入错题
  assert.equal(app.$("mistake-count").textContent, "1");
});

test("答错后隔四道题重现，答题序号标为间隔复习", function () {
  const app = createApp();
  const firstQuestionKey = currentQuestionKey(app);
  app.answer("ぜったいにまちがい");

  for (let index = 0; index < 4; index += 1) {
    app.click(app.$("next-question"));
    assert.notEqual(currentQuestionKey(app), firstQuestionKey, "错题不应立刻重现");
    app.answer(currentAnswer(app));
  }

  app.click(app.$("next-question"));
  assert.equal(currentQuestionKey(app), firstQuestionKey);
  assert.equal(app.$("question-index").textContent, "间隔复习");
});

test("到期复习按正确次数推进间隔", function () {
  const verb = engine.VERBS[0];
  const key = [verb.id, "base", "te"].join("::");
  const stored = JSON.stringify([{
    verbId: verb.id,
    voiceId: "base",
    inflectionId: "te",
    count: 1,
    wrongAnswer: "まちがい",
    correctStreak: 1,
    intervalDays: 1,
    dueAt: new Date(Date.now() - 60000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  }]);
  const app = createApp({ storage: { [MISTAKES_KEY]: stored } });

  assert.equal(currentQuestionKey(app), key, "到期卡片应优先于随机新题");
  assert.equal(app.$("question-index").textContent, "间隔复习");
  app.answer(currentAnswer(app));

  const updated = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY))[0];
  assert.equal(updated.correctStreak, 2);
  assert.equal(updated.intervalDays, 3);
  const remainingMs = Date.parse(updated.dueAt) - Date.now();
  assert.ok(remainingMs <= 3 * 86400000 && remainingMs > 3 * 86400000 - 5000);
});

test("完成六次连续正确复习后从错题集移出", function () {
  const verb = engine.VERBS[0];
  const stored = JSON.stringify([{
    verbId: verb.id,
    voiceId: "base",
    inflectionId: "te",
    count: 1,
    wrongAnswer: "まちがい",
    correctStreak: 5,
    intervalDays: 30,
    dueAt: new Date(Date.now() - 60000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  }]);
  const app = createApp({ storage: { [MISTAKES_KEY]: stored } });

  app.answer(currentAnswer(app));
  assert.equal(app.$("mistake-count").textContent, "0");
  assert.deepEqual(JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY)), []);
});

test("空答案不进入判定", function () {
  const app = createApp();
  app.answer("");
  assert.equal(app.$("feedback-panel").hidden, true);
  assert.equal(app.$("total-answer-count").textContent, "0");
});

test("输入法组字期间的回车不判定", function () {
  const app = createApp();
  app.answer(currentAnswer(app), { isComposing: true });
  assert.equal(app.$("feedback-panel").hidden, true, "组字中不应出反馈");
  assert.equal(app.$("total-answer-count").textContent, "0", "组字中不应计分");
});

test("答案带句点同样判对", function () {
  const app = createApp();
  app.answer(currentAnswer(app) + "。");
  assert.equal(app.$("feedback-mark").textContent, "✓");
});

test("难度筛选：基础／进阶各自只出对应活用", function () {
  const app = createApp();
  const basicLabels = new Set(engine.INFLECTIONS.filter(function (form) {
    return form.level === "basic";
  }).map(function (form) {
    return form.label;
  }));
  const advancedLabels = new Set(engine.INFLECTIONS.filter(function (form) {
    return form.level === "advanced";
  }).map(function (form) {
    return form.label;
  }));

  app.$("level-filter").value = "basic";
  app.$("level-filter").dispatch("change");
  for (let index = 0; index < 12; index += 1) {
    assert.ok(basicLabels.has(inflectionLabelOf(app)), "基础难度不应出现进阶活用");
    assert.ok(!advancedLabels.has(inflectionLabelOf(app)) || basicLabels.has(inflectionLabelOf(app)));
    app.click(app.$("new-question"));
  }

  app.$("level-filter").value = "advanced";
  app.$("level-filter").dispatch("change");
  for (let index = 0; index < 12; index += 1) {
    assert.ok(advancedLabels.has(inflectionLabelOf(app)), "进阶难度只应出现进阶活用");
    app.click(app.$("new-question"));
  }
});

test("指定具体活用形式时难度筛选禁用且以具体形式为准", function () {
  const app = createApp();
  app.$("inflection-filter").value = "imperative";
  app.$("inflection-filter").dispatch("change");

  assert.equal(app.$("level-filter").disabled, true, "已指定具体活用时应禁用难度筛选");
  for (let index = 0; index < 8; index += 1) {
    assert.equal(inflectionLabelOf(app), "命令形");
    app.click(app.$("new-question"));
  }

  app.$("inflection-filter").value = "all";
  app.$("inflection-filter").dispatch("change");
  assert.equal(app.$("level-filter").disabled, false);
});

function formRowsOf(app) {
  return app.$("reference-table-body").childNodes.filter(function (row) {
    return !row.classList.contains("reference-section-row");
  });
}

function classTab(app, classId) {
  const found = app.document.querySelectorAll("[data-reference-class]").filter(function (node) {
    return node.dataset.referenceClass === classId;
  })[0];
  assert.ok(found, "应存在类别切换按钮：" + classId);
  return found;
}

function classFormRowsOf(app) {
  return app.$("reference-class-table-body").childNodes.filter(function (row) {
    return !row.classList.contains("reference-section-row");
  });
}

test("速查页可按动词类别看规则，与查单词互斥显示", function () {
  const app = createApp();
  tab(app, "reference");

  // 初始是「查一个词」：单词控件与活用表都在，类别面板隐藏。
  assert.equal(app.$("reference-controls").hidden, false);
  assert.equal(app.$("reference-class-panel").hidden, true);
  assert.equal(classTab(app, "all").getAttribute("aria-pressed"), "true");

  app.click(classTab(app, "godan"));

  assert.equal(app.$("reference-class-panel").hidden, false, "类别面板应显示");
  assert.equal(app.$("reference-controls").hidden, true, "类别模式下应隐藏单词控件");
  assert.equal(app.$("reference-summary").hidden, true);
  assert.equal(app.$("reference-word-table").hidden, true);
  assert.equal(app.$("verb-morphology-guide").hidden, true);
  assert.equal(app.$("reference-voice-guide").hidden, true);
  assert.equal(classTab(app, "godan").getAttribute("aria-pressed"), "true");
  assert.equal(classTab(app, "all").getAttribute("aria-pressed"), "false");

  assert.equal(app.$("reference-class-title").textContent, "五段动词");
  assert.equal(app.$("reference-class-pill").textContent, "一类");
  assert.match(app.$("reference-class-summary").textContent, /「あ・い・う・え・お」/);
  assert.match(app.$("reference-lead").textContent, /这一类怎么变/);
  assert.equal(app.$("reference-class-detect").childNodes.length, 2, "五段应给出两条判定方法");
  assert.equal(app.$("reference-class-exceptions").childNodes.length, 2, "五段应列出两条例外");
  assert.match(app.$("reference-class-detect").textContent, /不以「る」结尾/);
  assert.match(app.$("reference-class-exceptions").textContent, /行って/);
});

test("五段类别给出九个词尾行的て形与た形", function () {
  const app = createApp();
  tab(app, "reference");
  app.click(classTab(app, "godan"));

  assert.equal(app.$("reference-class-rows").hidden, false, "五段应显示九行音便表");
  const rows = app.$("reference-class-rows-body").childNodes;
  assert.equal(rows.length, engine.GODAN_ROWS.length);
  assert.equal(rows.length, 9, "五段应有九个词尾行");

  engine.GODAN_ROWS.forEach(function (rowSpec, index) {
    const verb = engine.VERBS.filter(function (item) {
      return item.id === rowSpec.verbId;
    })[0];
    const row = rows[index];
    assert.equal(row.childNodes[0].textContent, rowSpec.label, "第一列是行名");
    assert.equal(row.childNodes[1].textContent, "「" + rowSpec.ending + "」", "第二列是词尾");
    assert.equal(
      row.childNodes[3].textContent,
      engine.conjugate(verb, "base", "te").written,
      rowSpec.label + " 的て形应等于代表词的结果"
    );
    assert.equal(
      row.childNodes[4].textContent,
      engine.conjugate(verb, "base", "past").written,
      rowSpec.label + " 的た形应等于代表词的结果"
    );
    assert.equal(row.childNodes[5].textContent, rowSpec.onbin, "第六列是音便说明");
  });

  // 抽查两行：イ音便与促音便是不同的结果，表里不能混成一套。
  assert.equal(rows[0].childNodes[3].textContent, "書いて");
  assert.equal(rows[3].childNodes[3].textContent, "待って");
  assert.notEqual(rows[0].childNodes[3].textContent, rows[3].childNodes[3].textContent);
});

test("非五段的类别不显示九行音便表，规则用该类的说法", function () {
  const app = createApp();
  tab(app, "reference");

  app.click(classTab(app, "ichidan"));
  assert.equal(app.$("reference-class-rows").hidden, true, "一段没有九行音便表");
  assert.equal(app.$("reference-class-title").textContent, "一段动词");
  assert.equal(app.$("reference-class-pill").textContent, "二类");
  assert.equal(classFormRowsOf(app).length, engine.INFLECTIONS.length, "所有活用形都要列出");
  assert.match(app.$("reference-class-table-body").textContent, /去掉「る」/);

  app.click(classTab(app, "suru"));
  assert.equal(app.$("reference-class-title").textContent, "サ变动词");
  assert.match(app.$("reference-class-table-body").textContent, /「する」变为「した」/);
  assert.equal(app.$("reference-class-rows").hidden, true);

  app.click(classTab(app, "kuru"));
  assert.equal(app.$("reference-class-title").textContent, "カ变动词");
  assert.match(app.$("reference-class-table-body").textContent, /きます/);
  assert.equal(app.$("reference-class-rows").hidden, true);
});

test("类别里的词点一下就切回单词速查并选中该词", function () {
  const app = createApp();
  tab(app, "reference");
  app.click(classTab(app, "ichidan"));

  const buttons = app.$("reference-class-word-list").querySelectorAll("button");
  assert.ok(buttons.length > 0, "类别里应有可点的词");
  const target = buttons[0];
  const label = target.textContent;

  app.click(target);

  assert.equal(app.$("reference-class-panel").hidden, true, "应切回单词模式");
  assert.equal(app.$("reference-controls").hidden, false);
  assert.equal(app.$("reference-word-table").hidden, false);
  assert.equal(app.$("verb-morphology-guide").hidden, false);
  assert.equal(app.$("reference-voice-guide").hidden, false);
  assert.equal(classTab(app, "all").getAttribute("aria-pressed"), "true");
  assert.match(app.$("reference-lead").textContent, /六种活用形/);

  const verbId = app.$("reference-verb").value;
  const verb = engine.VERBS.filter(function (item) {
    return item.id === verbId;
  })[0];
  assert.equal(verb.dictionary, label, "下拉应选中刚点的词");
  assert.match(app.$("reference-word").textContent, new RegExp(label));
  assert.ok(formRowsOf(app).length > 0, "单词活用表应重新渲染");
});

test("类别表的「练这题」用代表词开练", function () {
  const app = createApp();
  tab(app, "reference");
  app.click(classTab(app, "ichidan"));

  const targetRow = classFormRowsOf(app)[2];
  const rowLabel = targetRow.childNodes[0].textContent;
  const expected = targetRow.childNodes[1].querySelector("strong").textContent;
  app.click(targetRow.querySelectorAll(".practice-link")[0]);

  assert.equal(app.$("view-practice").hidden, false);
  assert.equal(app.$("view-reference").hidden, true);
  assert.equal(app.$("target-pill").textContent, rowLabel);
  assert.equal(currentAnswer(app), expected, "题目答案应等于表里的代表词结果");
});

test("速查表按基础／进阶分组展示活用形式", function () {
  const app = createApp();
  tab(app, "reference");

  const sections = app.$("reference-table-body").querySelectorAll(".reference-section-row");
  assert.equal(sections.length, 2, "应有 基础活用/进阶活用 两个分组行");
  assert.match(sections[0].textContent, /基础活用/);
  assert.match(sections[0].textContent, /12 项/);
  assert.match(sections[1].textContent, /进阶活用/);
  assert.match(sections[1].textContent, /6 项/);

  const rows = formRowsOf(app);
  assert.equal(rows.length, engine.INFLECTIONS.length, "活用行总数不变");

  const basicLabels = new Set(engine.INFLECTIONS.filter(function (form) {
    return form.level === "basic";
  }).map(function (form) {
    return form.label;
  }));
  const advancedLabels = new Set(engine.INFLECTIONS.filter(function (form) {
    return form.level === "advanced";
  }).map(function (form) {
    return form.label;
  }));
  assert.equal(basicLabels.size, 12);
  assert.equal(advancedLabels.size, 6);

  rows.slice(0, 12).forEach(function (row) {
    assert.ok(basicLabels.has(row.childNodes[0].textContent), "分组头之后应先是基础活用");
  });
  rows.slice(12).forEach(function (row) {
    assert.ok(advancedLabels.has(row.childNodes[0].textContent), "其后才是进阶活用");
  });
});

test("速查页以五列表格展示各变形类型的语义、结果与规则", function () {
  const app = createApp();
  tab(app, "reference");

  const table = app.$("reference-voice-guide-table");
  assert.equal(table.querySelector("thead").querySelectorAll("th").length, 5);
  const indexHtml = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
  const tableMarkup = /<table class="reference-table reference-voice-table"[\s\S]*?<\/table>/.exec(indexHtml);
  assert.ok(tableMarkup, "变形类型表格应存在于速查页");
  const headers = Array.from(tableMarkup[0].matchAll(/<th\b[^>]*>([^<]*)<\/th>/g), function (match) {
    return match[1].trim();
  });
  assert.deepEqual(headers, ["活用形式", "变形结果", "常见表达大意", "变化提示", "练一练"]);

  const body = app.$("reference-voice-guide-table-body");
  let items = body.querySelectorAll("[data-voice-id]");
  assert.equal(items.length, engine.VOICES.length);

  const potentialRow = items.filter(function (item) {
    return item.dataset.voiceId === "potential";
  })[0];
  assert.equal(potentialRow.childNodes.length, 5);
  assert.match(potentialRow.textContent, /行く → 行ける/);
  assert.match(potentialRow.textContent, /能力、条件或许可/);
  assert.match(potentialRow.textContent, /五段动词词尾改为对应え段/);

  const passiveRow = items.filter(function (item) {
    return item.dataset.voiceId === "passive";
  })[0];
  assert.match(passiveRow.textContent, /主语承受他人的动作/);
  assert.match(passiveRow.textContent, /雨に降られた/);

  const causativeRow = items.filter(function (item) {
    return item.dataset.voiceId === "causative";
  })[0];
  assert.match(causativeRow.textContent, /先生が学生に本を読ませる/);
  assert.match(causativeRow.textContent, /强制还是许可由语境判断/);

  const voiceSelect = app.$("reference-voice");
  voiceSelect.value = "causative";
  voiceSelect.dispatch("change");
  items = body.querySelectorAll("[data-voice-id]");
  const selectedRow = items.filter(function (item) {
    return item.dataset.voiceId === "causative";
  })[0];
  assert.equal(selectedRow.classList.contains("is-selected"), true);
  assert.equal(selectedRow.getAttribute("aria-current"), "true");

  const verbSelect = app.$("reference-verb");
  verbSelect.value = "taberu";
  verbSelect.dispatch("change");
  assert.match(app.$("reference-voice-guide-caption").textContent, /一段动词/);
  items = body.querySelectorAll("[data-voice-id]");
  const ichidanPotentialRow = items.filter(function (item) {
    return item.dataset.voiceId === "potential";
  })[0];
  assert.match(ichidanPotentialRow.textContent, /食べる → 食べられる/);
  assert.match(ichidanPotentialRow.textContent, /一段动词去掉「る」/);
});

test("变形类型速查表「练这题」按所点语态开始辞书形练习", function () {
  const app = createApp();
  tab(app, "reference");

  const voiceRow = app.$("reference-voice-guide-table-body")
    .querySelectorAll("[data-voice-id]")
    .filter(function (row) {
      return row.dataset.voiceId === "causative";
    })[0];
  const verb = engine.VERBS.filter(function (item) {
    return item.id === app.$("reference-verb").value;
  })[0];
  app.click(voiceRow.querySelector(".practice-link"));

  assert.equal(app.$("view-practice").hidden, false);
  assert.equal(app.$("view-reference").hidden, true);
  assert.equal(app.$("target-pill").textContent, "使役形 · 辞书形");
  assert.equal(
    currentAnswer(app),
    engine.conjugate(verb, "causative", "dictionary").written
  );
  assert.equal(app.$("answer-input").focused, true);
});

test("速查表「练这题」切到练习并载入对应题目", function () {
  const app = createApp();
  tab(app, "reference");

  const buttons = app.$("reference-table-body").querySelectorAll(".practice-link");
  assert.equal(buttons.length, engine.INFLECTIONS.length, "每个活用行都应有一个「练这题」");

  const targetRow = formRowsOf(app)[4];
  const rowLabel = targetRow.childNodes[0].textContent;
  app.click(targetRow.querySelectorAll(".practice-link")[0]);

  assert.equal(app.$("view-practice").hidden, false);
  assert.equal(app.$("view-reference").hidden, true);
  assert.equal(
    app.$("target-pill").textContent.indexOf(rowLabel) >= 0,
    true,
    "练习页目标应与所点行一致"
  );
  assert.equal(app.$("answer-input").focused, true, "应直接聚焦到输入框");
  assert.equal(app.$("question-index").textContent, "随机练习", "速查开练不进入错题重练模式");
});

test("错题重练：横幅常驻显示剩余量，退出后回到随机练习", function () {
  const app = createApp();
  app.answer("まちがいです");
  app.click(app.$("review-mistakes"));

  assert.equal(app.$("review-banner").hidden, false);
  assert.match(app.$("review-banner-count").textContent, /本轮待练 1 题/);
  assert.equal(app.$("question-index").textContent, "错题重练");

  app.click(app.$("exit-review"));
  assert.equal(app.$("review-banner").hidden, true);
  assert.equal(app.$("question-index").textContent, "随机练习");
  assert.match(app.$("toast").textContent, /已退出错题重练/);
});

test("换一题退出重练时会明确提示，不再静默离开", function () {
  const app = createApp();
  app.answer("まちがいです");
  app.click(app.$("review-mistakes"));
  app.click(app.$("new-question"));

  assert.equal(app.$("review-banner").hidden, true);
  assert.match(app.$("toast").textContent, /已退出错题重练/);
});

test("错题重练答对后推进间隔，单题本轮不会立刻重复", function () {
  const app = createApp();
  app.answer("まちがいです");
  app.click(app.$("review-mistakes"));
  app.answer(currentAnswer(app));

  assert.equal(app.$("mistake-count").textContent, "1", "卡片应保留到完成间隔复习");
  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY))[0];
  assert.equal(stored.correctStreak, 1);
  assert.equal(stored.intervalDays, 1);

  app.click(app.$("next-question"));
  assert.equal(app.$("view-mistakes").hidden, false, "错题清空后应回到错题页");
  assert.equal(app.$("review-banner").hidden, true, "离开重练后横幅应收起");
  assert.match(app.$("toast").textContent, /本轮错题已复习/);
});

test("存档损坏时降级为空错题集并提示", function () {
  const app = createApp({ storage: {} });
  assert.equal(app.$("mistake-count").textContent, "0");

  const broken = createApp({ storage: { [MISTAKES_KEY]: "{ 不是合法 JSON" } });
  assert.equal(broken.$("mistake-count").textContent, "0");
  assert.match(broken.$("toast").textContent, /无法解析/);
});

test("存档中的非法词条被丢弃，合法词条保留次数", function () {
  const seed = JSON.stringify([
    {
      verbId: engine.VERBS[0].id,
      voiceId: "base",
      inflectionId: "te",
      count: 3,
      wrongAnswer: "でた",
      updatedAt: "2026-01-01T00:00:00.000Z"
    },
    { verbId: "不存在的动词", voiceId: "base", inflectionId: "te", count: 1 },
    { verbId: engine.VERBS[0].id, voiceId: "不存在的语态", inflectionId: "te", count: 1 },
    "完全不是对象"
  ]);
  const app = createApp({ storage: { [MISTAKES_KEY]: seed } });

  assert.equal(app.$("mistake-count").textContent, "1", "只应保留一条合法错题");
  tab(app, "mistakes");
  const card = app.$("verb-mistakes-list").querySelectorAll(".mistake-card")[0];
  assert.match(card.textContent, /错 3 次/);
});

test("统计存档非法时从零开始，且不影响错题集", function () {
  const seed = JSON.stringify({ correct: -1, incorrect: "多" });
  const app = createApp({ storage: { [STATS_KEY]: seed } });

  assert.equal(app.$("total-answer-count").textContent, "0");
  assert.match(app.$("toast").textContent, /格式无效/);
  app.answer("まちがいです");
  assert.equal(app.$("mistake-count").textContent, "1", "统计异常不应连带清掉错题");
});

// —— 形容词模块 ——

function currentAdjectiveAnswer(app) {
  const word = app.$("adj-question-word").textContent;
  const adjective = adjEngine.ADJECTIVES.filter(function (item) {
    return item.word === word;
  })[0];
  assert.ok(adjective, "题目形容词应存在于词库：" + word);

  const formLabel = app.$("adj-target-pill").textContent;
  const form = adjEngine.FORMS.filter(function (item) {
    return item.label === formLabel;
  })[0];
  assert.ok(form, "目标 pill 的形式标签应可解析：" + formLabel);

  return adjEngine.conjugate(adjective, form.id).written;
}

test("形容词：初始化出题且视图默认隐藏", function () {
  const app = createApp();
  assert.equal(app.$("view-adjective").hidden, true, "形容词视图默认隐藏");
  assert.match(app.$("adj-question-word").textContent, /\S/, "首道形容词题应已渲染");
  assert.ok(
    ["い形容词", "な形容词"].includes(app.$("adj-question-kind").textContent),
    "题面应标出形容词分类"
  );
  assert.match(app.$("adj-question-reading").textContent, /\S/, "题面应展示假名读音");
});

test("形容词：页签切换视图与标语文案", function () {
  const app = createApp();
  tab(app, "adjective");

  assert.equal(app.$("view-adjective").hidden, false);
  assert.equal(app.$("view-verb").hidden, true);
  assert.match(app.$("intro-title").textContent, /看见形容词，想起接续。/);
  assert.match(app.$("intro-eyebrow").textContent, /先分い・な/);

  tab(app, "practice");
  assert.equal(app.$("view-verb").hidden, false);
  assert.equal(app.$("view-adjective").hidden, true);
  assert.match(app.$("intro-title").textContent, /看见动词，想起变化。/, "切回应恢复动词标语");
});

test("形容词：答对计入独立统计且不进错题本", function () {
  const app = createApp();
  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = currentAdjectiveAnswer(app);
  app.document.getElementById("adj-answer-form").dispatch("submit");

  const practiceCard = app.$("adj-practice-card");
  assert.equal(app.$("adj-feedback-panel").hidden, false);
  assert.equal(app.$("adj-feedback-mark").textContent, "✓");
  assert.equal(app.$("adj-correct-count").textContent, "1");
  assert.equal(app.$("adj-incorrect-count").textContent, "0");
  assert.equal(app.$("mistake-count").textContent, "0", "形容词答错答对都不应写入动词错题本");
  assert.equal(app.$("adj-answer-input").disabled, true, "作答后输入框应锁定");
  assert.equal(app.$("adj-next-question").hidden, false);
  assert.equal(app.$("adj-next-question").focused, true, "答题后应将键盘焦点交给下一题");
  assert.equal(practiceCard.classList.contains("is-answer-correct"), true);
  assert.equal(practiceCard.classList.contains("is-answer-incorrect"), false);
  assert.equal(app.$("correct-answer-count").textContent, "0", "动词统计不应被形容词作答污染");
});

test("形容词：礼貌变体与罗马字输入同样判对", function () {
  const app = createApp();
  tab(app, "adjective");
  // 收窄到 な形容词 × 过去否定：7 题一轮内必抽到 静か，测试确定性
  app.$("adj-group-filter").value = "na";
  app.$("adj-group-filter").dispatch("change");
  app.$("adj-inflection-filter").value = "pastNegative";
  app.$("adj-inflection-filter").dispatch("change");

  let answered = false;
  for (let attempt = 0; attempt < 10 && !answered; attempt += 1) {
    if (app.$("adj-question-word").textContent === "静か") {
      app.document.getElementById("adj-answer-input").value = "しずかじゃありませんでした";
      app.document.getElementById("adj-answer-form").dispatch("submit");
      assert.equal(app.$("adj-feedback-mark").textContent, "✓", "礼貌变体应判对");
      answered = true;
    } else {
      app.click(app.$("adj-new-question"));
    }
  }
  assert.ok(answered, "过去否定×な筛选下应抽到 静か");
});

test("形容词：类别筛选只出对应词性", function () {
  const app = createApp();
  tab(app, "adjective");
  app.$("adj-group-filter").value = "na";
  app.$("adj-group-filter").dispatch("change");

  for (let index = 0; index < 10; index += 1) {
    const word = app.$("adj-question-word").textContent;
    const adjective = adjEngine.ADJECTIVES.filter(function (item) {
      return item.word === word;
    })[0];
    assert.ok(adjective, "题目形容词应存在：" + word);
    assert.equal(adjective.kind, "na", "な类别筛选不应出い形容词");
    app.click(app.$("adj-new-question"));
  }
});

test("形容词：统计清零不影响动词统计", function () {
  const app = createApp();
  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = currentAdjectiveAnswer(app);
  app.document.getElementById("adj-answer-form").dispatch("submit");
  assert.equal(app.$("adj-total-count").textContent, "1");

  app.click(app.$("adj-reset-stats"));
  assert.equal(app.$("adj-correct-count").textContent, "0");
  assert.equal(app.$("adj-reset-stats").disabled, true, "清零后按钮应禁用");

  const stored = JSON.parse(app.window.localStorage.getItem("japanese-verb-practice-adj-stats-v1"));
  assert.deepEqual(stored, { correct: 0, incorrect: 0 });
});

const ADJ_MISTAKES_KEY = MISTAKES_KEY; // 两模块共用一本错题集

function adjectiveMistakeSeed(overrides) {
  return Object.assign({
    module: "adjective",
    adjectiveId: "shizuka",
    formId: "pastNegative",
    count: 1,
    wrongAnswer: "しずかくない",
    correctStreak: 0,
    intervalDays: 1,
    dueAt: new Date(Date.now() - 60000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  }, overrides || {});
}

test("形容词：答错入共享错题本并渲染形容词卡片", function () {
  const app = createApp();
  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = "ぜったいにまちがい";
  app.document.getElementById("adj-answer-form").dispatch("submit");

  assert.equal(app.$("mistake-count").textContent, "1", "形容词答错应计入共享错题角标");
  assert.match(app.$("toast").textContent, /已加入错题/);

  const stored = JSON.parse(app.window.localStorage.getItem(ADJ_MISTAKES_KEY));
  assert.equal(stored.length, 1);
  assert.equal(stored[0].module, "adjective");
  assert.equal(stored[0].adjectiveId, adjEngine.ADJECTIVES.filter(function (a) {
    return a.word === app.$("adj-question-word").textContent;
  })[0].id);
  assert.equal(stored[0].wrongAnswer, "ぜったいにまちがい");

  tab(app, "mistakes");
  const card = app.$("adjective-mistakes-list").querySelectorAll(".mistake-card")[0];
  assert.match(card.textContent, /い形容词|な形容词/, "形容词卡片应标注词性分类");
  assert.match(card.textContent, /错 1 次/);
});

test("形容词：到期错题优先插入随机练习，答对推进间隔", function () {
  const seed = JSON.stringify([adjectiveMistakeSeed({ correctStreak: 1 })]);
  const app = createApp({ storage: { [ADJ_MISTAKES_KEY]: seed } });
  tab(app, "adjective");

  assert.equal(app.$("adj-question-word").textContent, "静か", "到期形容词错题应优先出题");
  assert.equal(app.$("adj-question-index").textContent, "间隔复习");

  app.document.getElementById("adj-answer-input").value = "静かではなかった";
  app.document.getElementById("adj-answer-form").dispatch("submit");
  const stored = JSON.parse(app.window.localStorage.getItem(ADJ_MISTAKES_KEY))[0];
  assert.equal(stored.correctStreak, 2, "答对应推进复习进度");
  assert.equal(stored.intervalDays, 3);
});

test("形容词：完成六次正确复习后从错题集移出", function () {
  const seed = JSON.stringify([adjectiveMistakeSeed({ correctStreak: 5, intervalDays: 30 })]);
  const app = createApp({ storage: { [ADJ_MISTAKES_KEY]: seed } });
  tab(app, "adjective");
  assert.equal(app.$("adj-question-word").textContent, "静か");

  app.document.getElementById("adj-answer-input").value = "静かではなかった";
  app.document.getElementById("adj-answer-form").dispatch("submit");
  assert.equal(app.$("mistake-count").textContent, "0", "毕业错题应移出");
  assert.deepEqual(JSON.parse(app.window.localStorage.getItem(ADJ_MISTAKES_KEY)), []);
});

test("形容词：重练模式横幅常驻，换一题退出有提示", function () {
  const app = createApp();
  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = "まちがいです";
  app.document.getElementById("adj-answer-form").dispatch("submit");
  app.click(app.$("adj-review-mistakes"));

  assert.equal(app.$("adj-review-banner").hidden, false);
  assert.match(app.$("adj-review-banner-count").textContent, /本轮待练 1 题/);
  assert.equal(app.$("adj-question-index").textContent, "错题重练");

  app.click(app.$("adj-new-question"));
  assert.equal(app.$("adj-review-banner").hidden, true);
  assert.match(app.$("toast").textContent, /已退出错题重练/);
});

test("错题页：重练按钮按模块分流，verb 重练不含形容词错题", function () {
  const seed = JSON.stringify([adjectiveMistakeSeed()]);
  const app = createApp({ storage: { [ADJ_MISTAKES_KEY]: seed } });
  tab(app, "mistakes");

  assert.equal(app.$("adj-review-mistakes").disabled, false, "有形容词错题时应可重练");
  assert.equal(app.$("review-mistakes").disabled, true, "仅形容词错题时动词重练应禁用");

  app.click(app.$("adj-review-mistakes"));
  assert.equal(app.$("view-adjective").hidden, false);
  assert.equal(app.$("adj-question-index").textContent, "错题重练");
  assert.equal(app.$("adj-question-word").textContent, "静か");
});

test("形容词：错题卡「练这题」切到形容词视图重练", function () {
  const seed = JSON.stringify([adjectiveMistakeSeed()]);
  const app = createApp({ storage: { [ADJ_MISTAKES_KEY]: seed } });
  tab(app, "mistakes");
  // 形容词错题卡在独立分组容器里；按钮顺序：[0] 练这题、[1] 移除
  app.click(app.$("adjective-mistakes-list").querySelectorAll("button")[0]);

  assert.equal(app.$("view-adjective").hidden, false);
  assert.equal(app.$("adj-review-banner").hidden, false, "单题重练应进入重练模式");
  assert.equal(app.$("adj-question-word").textContent, "静か");
});

test("动词速查页动态展示六种活用形并连接现有练习", function () {
  const app = createApp();
  tab(app, "reference");

  const indexHtml = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
  const tableMarkup = /<table[^>]*id="verb-morphology-guide-table"[\s\S]*?<\/table>/.exec(indexHtml);
  assert.ok(tableMarkup, "动词速查页应有六种活用形表");
  const headers = Array.from(tableMarkup[0].matchAll(/<th\b[^>]*>([^<]*)<\/th>/g), function (match) {
    return match[1].trim();
  });
  assert.deepEqual(headers, ["活用形", "后面接什么／句中作用", "当前动词例子", "练一练"]);

  const verbSelect = app.$("reference-verb");
  verbSelect.value = "kaku";
  verbSelect.dispatch("change");
  const rows = app.$("verb-morphology-guide-table-body")
    .querySelectorAll("[data-morphology-form]");
  assert.equal(rows.length, 6);
  const rowFor = function (formId) {
    return rows.filter(function (row) {
      return row.dataset.morphologyForm === formId;
    })[0];
  };
  assert.match(rowFor("mizen").textContent, /書かない/);
  assert.match(rowFor("mizen").textContent, /書こう/);
  assert.match(rowFor("renyou").textContent, /書きます/);
  assert.match(rowFor("renyou").textContent, /書いて/);
  assert.match(rowFor("shushi").textContent, /書く。/);
  assert.match(rowFor("rentai").textContent, /書く人/);
  assert.match(rowFor("katei").textContent, /書けば/);
  assert.match(rowFor("meirei").textContent, /書け/);

  const verb = engine.VERBS.filter(function (item) {
    return item.id === "kaku";
  })[0];
  app.click(rowFor("katei").querySelector(".practice-link"));
  assert.equal(app.$("view-practice").hidden, false);
  assert.equal(app.$("question-index").textContent, "六形专项 · 仮定形");
  assert.equal(app.$("target-pill").textContent, "仮定形（接ば）");
  app.$("answer-input").value = engine.conjugate(verb, "base", "conditionalBa").written;
  app.$("answer-form").dispatch("submit");
  assert.equal(app.$("feedback-mark").textContent, "✓");
});

test("动词六形作为活用形式筛选项并持续出题", function () {
  const app = createApp();
  tab(app, "verb");
  const filter = app.$("inflection-filter");
  const options = filter.querySelectorAll("option").map(function (option) {
    return option.value;
  });
  ["mizen", "renyou", "shushi", "rentai", "katei", "meirei"].forEach(function (id) {
    assert.ok(options.includes("morphology:" + id), id + " 应在现有活用形式筛选中");
  });
  assert.equal(app.document.getElementById("six-form-practice"), null, "不应保留独立六形按钮");

  filter.value = "morphology:katei";
  filter.dispatch("change");
  assert.equal(app.$("question-index").textContent, "六形专项 · 仮定形");
  assert.equal(app.$("target-pill").textContent, "仮定形（接ば）");
  assert.equal(app.$("level-filter").disabled, true, "指定六形时难度筛选应停用");
  assert.equal(app.$("voice-filter").disabled, false, "语态筛选仍可与六形组合");
  app.click(app.$("new-question"));
  assert.equal(app.$("question-index").textContent, "六形专项 · 仮定形");

  filter.value = "all";
  filter.dispatch("change");
  assert.equal(app.$("level-filter").disabled, false);
  assert.equal(app.$("question-index").textContent, "随机练习");
});

test("动词六形导览每一行都能启动对应的语境书写题", function () {
  const cases = [
    { formId: "mizen", label: "未然形", inflectionId: "negative", target: "未然形（接ない）" },
    { formId: "renyou", label: "連用形", inflectionId: "masuStem", target: "連用形（ます词干）" },
    { formId: "shushi", label: "終止形", inflectionId: "dictionary", target: "終止形（句尾）" },
    { formId: "rentai", label: "連体形", inflectionId: "dictionary", target: "連体形（名词前）" },
    { formId: "katei", label: "仮定形", inflectionId: "conditionalBa", target: "仮定形（接ば）" },
    { formId: "meirei", label: "命令形", inflectionId: "imperative", target: "命令形" }
  ];

  cases.forEach(function (item) {
    const app = createApp();
    tab(app, "reference");
    app.$("reference-verb").value = "kaku";
    app.$("reference-verb").dispatch("change");
    const row = app.$("verb-morphology-guide-table-body")
      .querySelectorAll("[data-morphology-form]")
      .filter(function (candidate) {
        return candidate.dataset.morphologyForm === item.formId;
      })[0];
    app.click(row.querySelector(".practice-link"));

    assert.equal(app.$("target-pill").textContent, item.target);
    assert.equal(app.$("question-index").textContent, "六形专项 · " + item.label);
    app.$("answer-input").value = engine.conjugate(
      engine.VERBS.filter(function (verb) { return verb.id === "kaku"; })[0],
      "base",
      item.inflectionId
    ).written;
    app.$("answer-form").dispatch("submit");
    assert.equal(app.$("feedback-mark").textContent, "✓", item.formId);
  });
});

test("题干与变化提示使用统一关键词高亮类", function () {
  const app = createApp();
  assert.match(app.$("question-instruction").innerHTML, /class="keyword-highlight"/);

  app.$("group-filter").value = "godan";
  app.$("voice-filter").value = "base";
  app.$("inflection-filter").value = "conditionalBa";
  app.$("inflection-filter").dispatch("change");
  const emphasis = app.$("rule-hint").querySelector(".rule-emphasis");
  assert.ok(emphasis, "变化提示中的段位关键词应被强调");
  assert.equal(emphasis.classList.contains("keyword-highlight"), true);
});

test("形容词速查对照六形，条件形可练且命令形明确标为无独立形式", function () {
  const app = createApp();
  tab(app, "adjective");
  app.click(app.document.querySelectorAll("[data-adj-subview]").filter(function (subtab) {
    return subtab.dataset.adjSubview === "reference";
  })[0]);

  const rows = app.$("adjective-morphology-guide-table-body")
    .querySelectorAll("[data-morphology-form]");
  assert.equal(rows.length, 6);
  assert.ok(rows.every(function (row) {
    return row.childNodes.length === 5;
  }));
  assert.ok(rows.some(function (row) {
    return row.dataset.morphologyForm === "meirei";
  }));

  const indexHtml = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
  assert.match(indexHtml, /高かろう/);
  assert.match(indexHtml, /高ければ/);
  assert.match(indexHtml, /静かなら（ば）/);
  assert.match(indexHtml, /形容词本身没有独立命令形/);

  const conditionalButton = app.$("adjective-morphology-guide-table-body")
    .querySelectorAll("[data-adj-morphology-practice]")
    .filter(function (button) {
      return button.dataset.adjMorphologyPractice === "katei";
    })[0];
  app.click(conditionalButton);
  assert.equal(app.$("adj-question-index").textContent, "六形专项 · 仮定形");
  assert.equal(app.$("adj-target-pill").textContent, "仮定形（接ば）");

  const adjective = adjEngine.ADJECTIVES.filter(function (item) {
    return item.word === app.$("adj-question-word").textContent;
  })[0];
  app.$("adj-answer-input").value = adjEngine.conjugate(adjective, "conditional").written;
  app.$("adj-answer-form").dispatch("submit");
  assert.equal(app.$("adj-feedback-mark").textContent, "✓");

  const imperativeButton = app.$("adjective-morphology-guide-table-body")
    .querySelectorAll("[data-adj-morphology-practice]")
    .filter(function (button) {
      return button.dataset.adjMorphologyPractice === "meirei";
    })[0];
  app.click(imperativeButton);
  assert.equal(app.$("adj-target-pill").textContent, "命令表达（无独立形）");
  assert.match(app.$("adj-question-instruction").innerHTML, /没有独立命令形/);
  const imperativeAdjective = adjEngine.ADJECTIVES.filter(function (item) {
    return item.word === app.$("adj-question-word").textContent;
  })[0];
  app.$("adj-answer-input").value = adjEngine.conjugate(imperativeAdjective, "imperativeRequest").written;
  app.$("adj-answer-form").dispatch("submit");
  assert.equal(app.$("adj-feedback-mark").textContent, "✓");
});

test("形容词六形也在现有活用形式筛选中并可写出对应形式", function () {
  const app = createApp();
  tab(app, "adjective");
  const filter = app.$("adj-inflection-filter");
  const options = filter
    .querySelectorAll("option")
    .map(function (option) { return option.value; });
  ["mizen", "renyou", "shushi", "rentai", "katei", "meirei"].forEach(function (id) {
    assert.ok(options.includes("morphology:" + id), id + " 应在形容词活用形式筛选中");
  });
  assert.equal(app.document.getElementById("adj-six-form-practice"), null, "不应保留独立六形按钮");

  filter.value = "morphology:meirei";
  filter.dispatch("change");
  assert.equal(app.$("adj-question-index").textContent, "六形专项 · 命令形（形容词无独立形）");
  assert.equal(app.$("adj-target-pill").textContent, "命令表达（无独立形）");
  assert.equal(app.$("adj-level-filter").disabled, true);
  const adjective = adjEngine.ADJECTIVES.filter(function (item) {
    return item.word === app.$("adj-question-word").textContent;
  })[0];
  app.$("adj-answer-input").value = adjEngine.conjugate(adjective, "imperativeRequest").written;
  app.$("adj-answer-form").dispatch("submit");
  assert.equal(app.$("adj-feedback-mark").textContent, "✓");

  filter.value = "all";
  filter.dispatch("change");
  assert.equal(app.$("adj-level-filter").disabled, false);
  assert.equal(app.$("adj-question-index").textContent, "随机练习");
});

test("形容词：速查总表「练这题」载入对应形式", function () {
  const app = createApp();
  tab(app, "adjective");

  const button = app.document.querySelectorAll("[data-adj-practice]").filter(function (item) {
    return item.dataset.adjPractice === "pastNegative";
  })[0];
  assert.ok(button, "过去否定行应有练这题按钮");
  app.click(button);

  const word = app.$("adj-question-word").textContent;
  assert.ok(["高い", "静か"].includes(word), "应使用该行主例词出题：" + word);
  assert.equal(app.$("adj-target-pill").textContent, "过去否定");

  const adjective = adjEngine.ADJECTIVES.filter(function (item) { return item.word === word; })[0];
  const expected = adjEngine.conjugate(adjective, "pastNegative").written;
  app.document.getElementById("adj-answer-input").value = expected;
  app.document.getElementById("adj-answer-form").dispatch("submit");
  assert.equal(app.$("adj-feedback-mark").textContent, "✓", "总表开练的题目应可判对");
});

test("错题存档：形容词记录校验与 verb 兼容迁移", function () {
  const seed = JSON.stringify([
    adjectiveMistakeSeed(),
    { module: "adjective", adjectiveId: "不存在的词", formId: "past", count: 1 },
    { module: "adjective", adjectiveId: "takai", formId: "不存在的形式", count: 1 }
  ]);
  const app = createApp({ storage: { [ADJ_MISTAKES_KEY]: seed } });
  assert.equal(app.$("mistake-count").textContent, "1", "非法形容词错题应被丢弃");
});

// —— 查看答案通道（IK-16） ——

test("动词练习：查看答案揭示正解并按未答对记账", function () {
  const app = createApp();
  tab(app, "practice");
  const expected = currentAnswer(app);
  const keyBefore = currentQuestionKey(app);

  app.click(app.$("reveal-answer"));

  assert.equal(app.$("feedback-panel").hidden, false, "查看答案后应展开反馈面板");
  assert.equal(app.$("feedback-title").textContent, "已查看答案");
  assert.equal(app.$("feedback-correct-answer").textContent, expected, "应揭示正解");
  assert.equal(app.$("feedback-user-answer").textContent, "你的答案：（查看了答案，未作答）");
  assert.equal(app.$("feedback-panel").classList.contains("is-wrong"), true, "查看答案不算答对");
  assert.equal(app.$("submit-answer").disabled, true, "查看答案后禁止再提交");
  assert.equal(app.$("reveal-answer").disabled, true, "查看答案不可重复触发");
  assert.equal(app.$("answer-input").disabled, true, "查看答案后锁定输入");
  assert.equal(app.$("next-question").hidden, false, "应提供下一题入口");

  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY));
  assert.equal(stored.length, 1, "查看答案应记入错题");
  assert.equal(stored[0].key, keyBefore, "错题应为当前题");
  assert.equal(stored[0].wrongAnswer, "（查看答案）", "错题答案应标注查看答案");

  const stats = JSON.parse(app.window.localStorage.getItem(STATS_KEY));
  assert.equal(stats.correct, 0, "查看答案不得计答对");
  assert.equal(stats.incorrect, 1, "查看答案应计答错");
  assert.equal(app.$("incorrect-answer-count").textContent, "1");
  assert.equal(app.$("answer-accuracy").textContent, "0%");

  // 下一题后按钮复位，仍可正常作答闭环
  app.click(app.$("next-question"));
  assert.equal(app.$("feedback-panel").hidden, true, "下一题应重置反馈面板");
  assert.equal(app.$("reveal-answer").disabled, false, "新一题应恢复查看答案");
  app.$("answer-input").value = currentAnswer(app);
  app.$("answer-form").dispatch("submit");
  assert.equal(app.$("feedback-mark").textContent, "✓", "查看答案不影响后续正常判题");
});

test("形容词练习：查看答案揭示正解并按未答对记账", function () {
  const app = createApp();
  tab(app, "adjective");
  const word = app.$("adj-question-word").textContent;
  const adjective = adjEngine.ADJECTIVES.filter(function (item) {
    return item.word === word;
  })[0];
  assert.ok(adjective, "题目形容词应存在于词表：" + word);

  app.click(app.$("adj-reveal-answer"));

  assert.equal(app.$("adj-feedback-panel").hidden, false);
  assert.equal(app.$("adj-feedback-title").textContent, "已查看答案");
  assert.equal(app.$("adj-feedback-user-answer").textContent, "你的答案：（查看了答案，未作答）");
  assert.equal(app.$("adj-submit-answer").disabled, true);
  assert.equal(app.$("adj-reveal-answer").disabled, true);

  const stored = JSON.parse(app.window.localStorage.getItem(ADJ_MISTAKES_KEY));
  assert.equal(stored.length, 1, "查看答案应记入错题");
  assert.equal(stored[0].module, "adjective");
  assert.equal(stored[0].adjectiveId, adjective.id);
  assert.equal(stored[0].wrongAnswer, "（查看答案）");

  const adjStats = JSON.parse(app.window.localStorage.getItem(ADJ_STATS_KEY));
  assert.equal(adjStats.correct, 0);
  assert.equal(adjStats.incorrect, 1, "查看答案应计答错");
});

// —— 无障碍：导航播报与焦点管理（IK-18） ——

test("视图切换播报模块名并把焦点移到主标题", function () {
  const app = createApp();
  tab(app, "adjective");
  assert.equal(app.$("view-announcer").textContent, "已进入形容词模块");
  assert.equal(
    app.document.activeElement,
    app.$("intro-title"),
    "视图切换后焦点应落在主标题（h1 tabindex=-1）"
  );

  tab(app, "mistakes");
  assert.equal(app.$("view-announcer").textContent, "已进入错题本");
  assert.equal(app.document.activeElement, app.$("intro-title"));
});

test("有子视图的模块播报更具体的模块与子视图", function () {
  const app = createApp();
  tab(app, "verb");
  assert.equal(app.$("view-announcer").textContent, "动词 · 练习");

  const referenceSubtab = app.document.querySelectorAll("[data-verb-subview]").filter(function (subtab) {
    return subtab.dataset.verbSubview === "reference";
  })[0];
  app.click(referenceSubtab);
  assert.equal(app.$("view-announcer").textContent, "动词 · 速查");

  tab(app, "particle");
  assert.equal(app.$("view-announcer").textContent, "助词 · 在句子中练习");
  const learnSubtab = app.document.querySelectorAll("[data-particle-subview]").filter(function (subtab) {
    return subtab.dataset.particleSubview === "learn";
  })[0];
  app.click(learnSubtab);
  assert.equal(app.$("view-announcer").textContent, "助词 · 先看语境，再选助词");
});

test("清空错题的结果经 toast live region 播报", function () {
  const seed = JSON.stringify([
    dueMistakeSeed({ verbId: engine.VERBS[0].id, voiceId: "base", inflectionId: "te", count: 1 }),
    adjectiveMistakeSeed()
  ]);
  const app = createApp({ storage: { [MISTAKES_KEY]: seed } });
  tab(app, "mistakes");
  assert.equal(app.$("mistake-count").textContent, "2");

  // dom-stub 的 window.confirm 桩恒返回 true，走完整清空路径
  app.click(app.$("clear-mistakes"));
  assert.match(app.$("toast").textContent, /错题已清空/, "清空结果应经 role=status 的 toast 播报");
  assert.equal(app.$("mistake-count").textContent, "0");
});

// —— 错题加权与每日打卡 ——

function localDateKey(offsetDays) {
  const date = new Date();
  date.setDate(date.getDate() + (offsetDays || 0));
  const month = "0" + (date.getMonth() + 1);
  const day = "0" + date.getDate();
  return date.getFullYear() + "-" + month.slice(-2) + "-" + day.slice(-2);
}

function dueMistakeSeed(overrides) {
  return Object.assign({
    count: 1,
    wrongAnswer: "まちがい",
    correctStreak: 0,
    intervalDays: 1,
    dueAt: new Date(Date.now() - 60000).toISOString(),
    updatedAt: new Date(Date.now() - 86400000).toISOString()
  }, overrides || {});
}

test("错题加权：verb 到期题按错次加权出现", function () {
  // 到期池至少 3 条：两条时会因「排除当前题」变成完美交替，加权差异被掩盖
  const verbA = engine.VERBS[0];
  const verbB = engine.VERBS[1];
  const verbC = engine.VERBS[2];
  const seed = JSON.stringify([
    dueMistakeSeed({ verbId: verbA.id, voiceId: "base", inflectionId: "te", count: 1 }),
    dueMistakeSeed({ verbId: verbB.id, voiceId: "base", inflectionId: "te", count: 1 }),
    dueMistakeSeed({ verbId: verbC.id, voiceId: "base", inflectionId: "te", count: 3 })
  ]);
  const app = createApp({ storage: { [MISTAKES_KEY]: seed } });

  const counts = {};
  for (let index = 0; index < 400; index += 1) {
    app.click(app.$("new-question"));
    const key = currentQuestionKey(app);
    counts[key] = (counts[key] || 0) + 1;
  }
  const keyC = [verbC.id, "base", "te"].join("::");
  const keyA = [verbA.id, "base", "te"].join("::");
  assert.ok((counts[keyC] || 0) > (counts[keyA] || 0),
    "错 3 次的到期题应比错 1 次的更常被抽出：" + JSON.stringify(counts));
  assert.ok((counts[keyA] || 0) > 0, "低频错题仍应有机会出现");
});

test("错题加权：形容词到期题按错次加权出现", function () {
  const seed = JSON.stringify([
    adjectiveMistakeSeed({ adjectiveId: "shizuka", count: 1 }),
    adjectiveMistakeSeed({ adjectiveId: "genki", count: 1 }),
    adjectiveMistakeSeed({ adjectiveId: "benri", count: 3 })
  ]);
  const app = createApp({ storage: { [ADJ_MISTAKES_KEY]: seed } });
  tab(app, "adjective");

  const counts = {};
  for (let index = 0; index < 400; index += 1) {
    app.click(app.$("adj-new-question"));
    const word = app.$("adj-question-word").textContent;
    counts[word] = (counts[word] || 0) + 1;
  }
  assert.ok((counts["便利"] || 0) > (counts["静か"] || 0),
    "错 3 次的形容词应更常被抽出：" + JSON.stringify(counts));
  assert.ok((counts["静か"] || 0) > 0, "低频错题仍应有机会出现");
});

test("主页：昨日活动不冒充今日答题量，连续天数保留到当天", function () {
  const app = createApp({ storage: {
    [DAILY_KEY]: JSON.stringify({
      lastDate: localDateKey(-1),
      streak: 4,
      best: 6,
      verbAnswers: 5,
      adjectiveAnswers: 2
    })
  } });

  assert.equal(app.$("home-today-total").textContent, "0 题");
  assert.equal(app.$("home-today-breakdown").textContent, "动词 0 题 · 形容词 0 题 · 助词 0 题 · 敬语 0 题");
  assert.equal(app.$("home-active-module-count").textContent, "0");
  assert.equal(app.$("home-streak-count").textContent, "4");
});

test("主页：汇总今日答题并提供独立模块入口", function () {
  const app = createApp();
  assert.equal(
    app.document.querySelectorAll("[data-open-view]").filter(function (node) {
      return node.dataset.openView === "particle";
    }).length,
    1,
    "主页应仅展示一个助词模块入口"
  );
  const particleCard = app.document.querySelectorAll("[data-open-view]").filter(function (node) {
    return node.dataset.openView === "particle";
  })[0];
  const particleLearnTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "learn";
  })[0];
  app.click(particleLearnTab);
  app.$("app-main").scrollTop = 180;
  app.click(particleCard);
  assert.equal(app.$("view-particle").hidden, false, "主页助词卡片应打开助词模块");
  assert.equal(app.$("particle-view-practice").hidden, false, "主页入口应直接开始练习");
  assert.equal(app.$("app-main").scrollTop, 0, "主页模块入口应从内容顶部打开");
  tab(app, "verb");
  app.answer(currentAnswer(app));

  let stored = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(stored.verbAnswers, 1);
  assert.equal(stored.adjectiveAnswers, 0);
  assert.equal(app.$("home-today-total").textContent, "1 题");
  assert.equal(app.$("home-verb-status").textContent, "今日已练");

  tab(app, "home");
  assert.equal(app.$("view-home").hidden, false);
  assert.equal(app.$("view-verb").hidden, true);
  tab(app, "adjective");
  app.$("adj-answer-input").value = currentAdjectiveAnswer(app);
  app.$("adj-answer-form").dispatch("submit");

  stored = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(stored.verbAnswers, 1);
  assert.equal(stored.adjectiveAnswers, 1);
  assert.equal(app.$("home-today-total").textContent, "2 题");
  assert.equal(app.$("home-today-breakdown").textContent, "动词 1 题 · 形容词 1 题 · 助词 0 题 · 敬语 0 题");
  assert.equal(app.$("home-active-module-count").textContent, "2");
  assert.equal(app.$("home-adjective-status").textContent, "今日已练");
});

test("助词答题统计从独立存档恢复并单独清零", function () {
  const app = createApp({ storage: {
    [PARTICLE_STATS_KEY]: JSON.stringify({ correct: 4, incorrect: 2 }),
    [PARTICLE_MISTAKES_KEY]: JSON.stringify(["function-weather-ga"])
  } });

  assert.equal(app.$("particle-correct-count").textContent, "4");
  assert.equal(app.$("particle-incorrect-count").textContent, "2");
  assert.equal(app.$("particle-total-count").textContent, "6");
  assert.equal(app.$("particle-accuracy").textContent, "67%");
  app.click(app.$("particle-reset-stats"));

  assert.deepEqual(
    JSON.parse(app.window.localStorage.getItem(PARTICLE_STATS_KEY)),
    { correct: 0, incorrect: 0 }
  );
  assert.deepEqual(
    JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_KEY)),
    ["function-weather-ga"]
  );
});

test("打卡：首次作答记连续 1 天并写入存档", function () {
  const app = createApp();
  assert.equal(app.$("streak-note").hidden, true, "无记录时徽章应隐藏");
  app.answer(currentAnswer(app));

  assert.equal(app.$("streak-note").hidden, false);
  assert.match(app.$("streak-note-text").textContent, /连续练习 1 天 · 最佳 1 天/);
  const stored = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(stored.streak, 1);
  assert.equal(stored.best, 1);
  assert.equal(stored.lastDate, localDateKey(0));
  assert.equal(stored.verbAnswers, 1);
  assert.equal(stored.adjectiveAnswers, 0);
});

test("打卡：昨天练过今天续上，中断则重新计数但保留最佳", function () {
  const continued = createApp({ storage: {
    [DAILY_KEY]: JSON.stringify({ lastDate: localDateKey(-1), streak: 4, best: 4 })
  } });
  continued.answer(currentAnswer(continued));
  assert.match(continued.$("streak-note-text").textContent, /连续练习 5 天 · 最佳 5 天/);

  const broken = createApp({ storage: {
    [DAILY_KEY]: JSON.stringify({ lastDate: localDateKey(-2), streak: 4, best: 6 })
  } });
  broken.answer(currentAnswer(broken));
  assert.match(broken.$("streak-note-text").textContent, /连续练习 1 天 · 最佳 6 天/);
});

test("打卡：同一天多次作答不重复计数，形容词作答同样打卡", function () {
  const app = createApp();
  app.answer(currentAnswer(app));
  app.answer(currentAnswer(app));
  const afterVerb = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(afterVerb.streak, 1);
  assert.equal(afterVerb.verbAnswers, 2);

  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = currentAdjectiveAnswer(app);
  app.document.getElementById("adj-answer-form").dispatch("submit");
  const stored = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(stored.streak, 1, "同一天只记一次");
  assert.equal(stored.lastDate, localDateKey(0));
  assert.equal(stored.verbAnswers, 2);
  assert.equal(stored.adjectiveAnswers, 1);
});

test("打卡：存档损坏降级为从零开始", function () {
  const app = createApp({ storage: { [DAILY_KEY]: "{ 不是合法 JSON" } });
  assert.match(app.$("toast").textContent, /打卡记录无法解析/);
  app.answer(currentAnswer(app));
  assert.match(app.$("streak-note-text").textContent, /连续练习 1 天 · 最佳 1 天/);
});

// —— 错题导出 / 导入 ——

test("导出：下载文件含全部错题且剥离会话内调度字段", function () {
  const seed = JSON.stringify([
    dueMistakeSeed({ verbId: engine.VERBS[0].id, voiceId: "base", inflectionId: "te", count: 2 }),
    adjectiveMistakeSeed()
  ]);
  const app = createApp({ storage: { [MISTAKES_KEY]: seed } });
  tab(app, "mistakes");

  assert.equal(app.$("export-mistakes").disabled, false);
  app.click(app.$("export-mistakes"));

  const download = app.window.__mistakeDownloads[0];
  assert.ok(download, "应触发一次文件下载");
  const exported = JSON.parse(download.blob._text);
  assert.equal(exported.length, 2);
  assert.ok(exported.some(function (item) { return item.module === "adjective"; }));
  exported.forEach(function (item) {
    assert.equal(item.sessionDueAtAnswerCount, undefined, "会话内调度字段不应导出");
  });
  assert.match(app.$("toast").textContent, /已导出 2 道错题/);
});

test("导入：新增错题入库并持久化", function () {
  const app = createApp();
  tab(app, "mistakes");
  const incoming = JSON.stringify([
    dueMistakeSeed({ verbId: engine.VERBS[0].id, voiceId: "base", inflectionId: "te", count: 1 }),
    adjectiveMistakeSeed()
  ]);
  const input = app.$("import-mistakes-file");
  input.files = [{ _content: incoming, name: "mistakes.json" }];
  input.dispatch("change");

  assert.equal(app.$("mistake-count").textContent, "2");
  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY));
  assert.equal(stored.length, 2);
  assert.match(app.$("toast").textContent, /导入 2 道新错题/);
  assert.equal(input.value, "", "导入完成后应清空 file input");
});

test("导入：同 key 合并，复习进度取更先进记录且次数取较大值", function () {
  const verb = engine.VERBS[0];
  const localSeed = JSON.stringify([
    dueMistakeSeed({ verbId: verb.id, voiceId: "base", inflectionId: "te", count: 1, correctStreak: 0, intervalDays: 1 })
  ]);
  const app = createApp({ storage: { [MISTAKES_KEY]: localSeed } });
  tab(app, "mistakes");

  const incoming = JSON.stringify([
    dueMistakeSeed({
      verbId: verb.id, voiceId: "base", inflectionId: "te",
      count: 3, correctStreak: 2, intervalDays: 7,
      dueAt: new Date(Date.now() + 7 * 86400000).toISOString(),
      wrongAnswer: "かきませんでした",
      updatedAt: new Date().toISOString()
    })
  ]);
  const input = app.$("import-mistakes-file");
  input.files = [{ _content: incoming, name: "mistakes.json" }];
  input.dispatch("change");

  assert.equal(app.$("mistake-count").textContent, "1", "同 key 不应产生重复卡片");
  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY))[0];
  assert.equal(stored.count, 3, "错误次数应取较大值");
  assert.equal(stored.correctStreak, 2, "复习进度应取更先进记录");
  assert.equal(stored.intervalDays, 7);
  assert.match(app.$("toast").textContent, /均已存在，复习进度已取更先进的记录/);
});

test("导入：非法记录被丢弃，全部非法时提示", function () {
  const app = createApp();
  tab(app, "mistakes");
  const input = app.$("import-mistakes-file");
  input.files = [{ _content: JSON.stringify([
    { verbId: "不存在的动词", voiceId: "base", inflectionId: "te", count: 1 }
  ]), name: "bad.json" }];
  input.dispatch("change");
  assert.match(app.$("toast").textContent, /没有合法的错题记录/);
  assert.equal(app.$("mistake-count").textContent, "0");

  const empty = createApp();
  tab(empty, "mistakes");
  const emptyInput = empty.$("import-mistakes-file");
  emptyInput.files = [{ _content: JSON.stringify([]), name: "empty.json" }];
  emptyInput.dispatch("change");
  assert.match(empty.$("toast").textContent, /没有合法的错题记录/);
});

test("导入：解析失败与格式无效均有提示且不动错题集", function () {
  const app = createApp();
  tab(app, "mistakes");
  const input = app.$("import-mistakes-file");

  input.files = [{ _content: "{ 不是合法 JSON", name: "broken.json" }];
  input.dispatch("change");
  assert.match(app.$("toast").textContent, /导入文件无法解析/);

  input.files = [{ _content: JSON.stringify({ correct: 1 }), name: "object.json" }];
  input.dispatch("change");
  assert.match(app.$("toast").textContent, /导入文件格式无效/);
  assert.equal(app.$("mistake-count").textContent, "0");
});

test("导出再导入：往返等价（剥离会话字段后可完整恢复）", function () {
  const seed = JSON.stringify([
    dueMistakeSeed({ verbId: engine.VERBS[0].id, voiceId: "base", inflectionId: "te", count: 2, correctStreak: 1, intervalDays: 3 }),
    adjectiveMistakeSeed({ correctStreak: 2, intervalDays: 7 })
  ]);
  const first = createApp({ storage: { [MISTAKES_KEY]: seed } });
  tab(first, "mistakes");
  first.click(first.$("export-mistakes"));
  const exportedText = first.window.__mistakeDownloads[0].blob._text;

  const second = createApp();
  tab(second, "mistakes");
  const input = second.$("import-mistakes-file");
  input.files = [{ _content: exportedText, name: "mistakes.json" }];
  input.dispatch("change");

  assert.equal(second.$("mistake-count").textContent, "2");
  const restored = JSON.parse(second.window.localStorage.getItem(MISTAKES_KEY));
  assert.equal(restored.length, 2);
  restored.forEach(function (item) {
    const original = JSON.parse(exportedText).filter(function (candidate) {
      return candidate.key === item.key;
    })[0];
    assert.equal(item.correctStreak, original.correctStreak, "复习进度应完整恢复");
    assert.equal(item.intervalDays, original.intervalDays);
    assert.equal(item.dueAt, original.dueAt);
  });
});

// —— 形容词模块分区：练习/速查子视图 + 错题分组 ——

function adjectiveSubviewOf(app) {
  return app.$("adj-subview-practice").hidden ? "reference" : "practice";
}

test("形容词：练习与速查为两个独立子视图，默认练习", function () {
  const app = createApp();
  tab(app, "adjective");

  assert.equal(adjectiveSubviewOf(app), "practice", "默认应为练习子视图");
  assert.equal(app.$("adj-subview-reference").hidden, true);

  app.click(app.document.querySelectorAll("[data-adj-subview]").filter(function (tab) {
    return tab.dataset.adjSubview === "reference";
  })[0]);
  assert.equal(adjectiveSubviewOf(app), "reference", "点速查应切到速查子视图");
  assert.equal(app.$("adj-subview-practice").hidden, true, "练习子视图应隐藏");

  // 侧栏「查形容词速查资料」入口
  const back = createApp();
  tab(back, "adjective");
  back.click(back.$("adj-guide-link"));
  assert.equal(adjectiveSubviewOf(back), "reference", "侧栏入口应切到速查子视图");
});

test("形容词：速查总表「练这题」从速查子视图切回练习", function () {
  const app = createApp();
  tab(app, "adjective");
  app.click(app.document.querySelectorAll("[data-adj-subview]").filter(function (tab) {
    return tab.dataset.adjSubview === "reference";
  })[0]);
  assert.equal(adjectiveSubviewOf(app), "reference");

  const button = app.document.querySelectorAll("[data-adj-practice]").filter(function (item) {
    return item.dataset.adjPractice === "past";
  })[0];
  app.click(button);
  assert.equal(adjectiveSubviewOf(app), "practice", "开练应切回练习子视图");
  assert.equal(app.$("adj-target-pill").textContent, "过去肯定");
});

test("错题页：动词与形容词错题分组建卡，不混排", function () {
  const seed = JSON.stringify([
    dueMistakeSeed({ verbId: engine.VERBS[0].id, voiceId: "base", inflectionId: "te", count: 1 }),
    adjectiveMistakeSeed(),
    adjectiveMistakeSeed({ adjectiveId: "benri", formId: "present" })
  ]);
  const app = createApp({ storage: { [MISTAKES_KEY]: seed } });
  tab(app, "mistakes");

  const verbCards = app.$("verb-mistakes-list").querySelectorAll(".mistake-card");
  const adjectiveCards = app.$("adjective-mistakes-list").querySelectorAll(".mistake-card");
  assert.equal(verbCards.length, 1, "动词组应只有动词错题卡");
  assert.equal(adjectiveCards.length, 2, "形容词组应只有形容词错题卡");
  assert.equal(app.$("verb-mistakes-group").hidden, false);
  assert.equal(app.$("adjective-mistakes-group").hidden, false);
  assert.match(app.$("verb-mistakes-count").textContent, /共 1 道/);
  assert.match(app.$("adjective-mistakes-count").textContent, /共 2 道/);
});

test("错题页：只有单模块错题时另一分组整体隐藏", function () {
  const seed = JSON.stringify([adjectiveMistakeSeed()]);
  const app = createApp({ storage: { [MISTAKES_KEY]: seed } });
  tab(app, "mistakes");

  assert.equal(app.$("verb-mistakes-group").hidden, true, "无动词错题时动词组应隐藏");
  assert.equal(app.$("adjective-mistakes-group").hidden, false);
  assert.equal(app.$("mistakes-empty").hidden, true, "仍有错题时不显示空态");
});

test("助词错答给出所选助词的专属说明，学习地图补最小对立提示", function () {
  const app = createApp();
  tab(app, "particle");
  const question = particleEngine.getQuestion(
    app.$("particle-question-card").getAttribute("data-question-id")
  );
  assert.equal(question.type, "fill", "综合练习首题应为选助词题");
  const noteKeys = Object.keys(question.optionNotes || {});
  assert.ok(noteKeys.length > 0, "首题应带逐项说明");

  const distractor = app.$("particle-options").querySelectorAll("button").filter(function (button) {
    return button.textContent === noteKeys[0];
  })[0];
  assert.ok(distractor, "选项里应能找到带说明的干扰项");
  app.click(distractor);
  app.click(app.$("particle-check-answer"));

  const note = app.$("particle-feedback-note");
  assert.equal(note.hidden, false, "答错时应显示逐项说明");
  assert.match(note.textContent, new RegExp("你选的「" + noteKeys[0] + "」"));
  assert.match(note.textContent, new RegExp(question.optionNotes[noteKeys[0]].slice(0, 6)));

  const learnSubview = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "learn";
  })[0];
  app.click(learnSubview);
  const expected = particleEngine.LESSONS.reduce(function (total, lesson) {
    return total + lesson.examples.filter(function (example) {
      return Boolean(example.contrastNote);
    }).length;
  }, 0);
  const blocks = app.document.querySelectorAll(".particle-example-contrast");
  assert.equal(blocks.length, expected, "每条带对比说明的例句都应渲染对比提示");
  assert.match(blocks[0].textContent, /换个助词会怎样/);
});

test("v1 纯 ID 数组迁移成带排期的 v2 记录", function () {
  const app = createApp({
    storage: { [PARTICLE_MISTAKES_KEY]: JSON.stringify(["function-weather-ga", "wa-topic"]) }
  });
  tab(app, "particle");

  const migrated = JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_V2_KEY));
  assert.equal(migrated.length, 2, "旧错题应全部迁移到 v2");
  assert.deepEqual(
    migrated.map(function (record) { return record.questionId; }),
    ["function-weather-ga", "wa-topic"]
  );
  migrated.forEach(function (record) {
    assert.equal(record.count, 1);
    assert.equal(record.correctStreak, 0);
    assert.equal(record.intervalDays, 1);
    assert.ok(Date.parse(record.dueAt) <= Date.now() + 1000, "迁移的错题应立即可复习");
  });
  assert.equal(
    app.$("particle-review-count").textContent,
    "本地保存 2 道助词错题 · 2 道今天到期"
  );
  assert.equal(JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_KEY)).length, 2, "v1 原样保留作为备份");
});

test("复习连续答对达到毕业次数后移出助词错题本", function () {
  const app = createApp({
    storage: {
      [PARTICLE_MISTAKES_V2_KEY]: JSON.stringify([
        {
          questionId: "function-weather-ga",
          count: 3,
          wrongAnswer: "连接前后分句表示转折",
          updatedAt: new Date().toISOString(),
          correctStreak: 5,
          intervalDays: 30,
          dueAt: new Date(Date.now() - 60 * 1000).toISOString(),
          sessionDueAtAnswerCount: null
        }
      ])
    }
  });
  tab(app, "particle");
  const reviewTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "review";
  })[0];
  app.click(reviewTab);
  app.click(app.$("particle-start-review"));

  const question = particleEngine.getQuestion("function-weather-ga");
  const correctOption = app.$("particle-options").querySelectorAll("button").filter(function (button) {
    return question.acceptedAnswers.includes(button.textContent);
  })[0];
  app.click(correctOption);
  app.click(app.$("particle-check-answer"));

  assert.deepEqual(
    JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_V2_KEY)),
    [],
    "连对 6 次后应移出错题本"
  );
});

// IK-20 跨端互通：Android 写出的 mistakes-v2（storage/Models.kt ParticleMistakeRecord）
// 必须能被 Web 原样读取、保留排期语义，且不被重写。
test("Web 原样读取 Android 写出的助词错题 v2 记录", function () {
  const dueAt = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString();
  const androidRecord = {
    questionId: "wa-topic",
    count: 2,
    wrongAnswer: "は",
    updatedAt: new Date().toISOString(),
    correctStreak: 3,
    intervalDays: 7,
    dueAt: dueAt,
    sessionDueAtAnswerCount: null
  };
  const app = createApp({
    storage: { [PARTICLE_MISTAKES_V2_KEY]: JSON.stringify([androidRecord]) }
  });
  tab(app, "particle");

  // 排期语义：未到期不计入「今天到期」
  assert.equal(
    app.$("particle-review-count").textContent,
    "本地保存 1 道助词错题 · 0 道今天到期"
  );

  // v2 文件不被重写：Android 的记录原样保留（字段与顺序都不动）
  assert.deepEqual(
    JSON.parse(app.window.localStorage.getItem(PARTICLE_MISTAKES_V2_KEY)),
    [androidRecord],
    "Web 读取 Android v2 后不应重写文件"
  );

  // 复习列表按 Android 字段渲染
  const reviewTab = app.document.querySelectorAll("[data-particle-subview]").filter(function (node) {
    return node.dataset.particleSubview === "review";
  })[0];
  app.click(reviewTab);
  const item = app.$("particle-review-list").querySelector(".particle-review-item");
  assert.ok(item, "应渲染 Android 写入的错题");
  assert.match(item.textContent, /错 2 次/);
  assert.match(item.textContent, /已连对 3\/6/);
  const expectedDate = new Date(dueAt).toLocaleDateString("zh-CN", { month: "numeric", day: "numeric" });
  assert.match(item.textContent, new RegExp("下次复习：" + expectedDate));

  // 复习模式取全部错题（含未到期）：Android 侧同一口径
  assert.equal(app.$("particle-start-review").disabled, false);
  app.click(app.$("particle-start-review"));
  assert.equal(
    app.$("particle-question-card").getAttribute("data-question-id"),
    "wa-topic",
    "复习队列应包含 Android 写入的错题"
  );
});

test("Web 丢弃 Android 旧版本遗留的无效题目 ID", function () {
  const app = createApp({
    storage: {
      [PARTICLE_MISTAKES_V2_KEY]: JSON.stringify([
        {
          questionId: "旧版本遗留的不存在题目",
          count: 4,
          wrongAnswer: "が",
          updatedAt: new Date().toISOString(),
          correctStreak: 1,
          intervalDays: 3,
          dueAt: new Date().toISOString(),
          sessionDueAtAnswerCount: null
        }
      ])
    }
  });
  tab(app, "particle");
  assert.equal(
    app.$("particle-review-count").textContent,
    "还没有需要复习的助词错题",
    "无效题目 ID 应被静默丢弃"
  );
  assert.equal(app.$("particle-start-review").disabled, true);
});

// ── 文章阅读（B2 静态外壳 + 本地切段） ────────────────────────────────────

function readTab(app) {
  return app.document.querySelectorAll("[data-view]").filter(function (node) {
    return node.dataset.view === "read";
  });
}

function readCard(app) {
  return app.document.querySelectorAll("[data-open-view]").filter(function (node) {
    return node.dataset.openView === "read";
  });
}

test("阅读引擎脚本在 app.js 之前按 defer 顺序加载", function () {
  const html = fs.readFileSync(path.join(__dirname, "..", "index.html"), "utf8");
  const appScript = html.indexOf('src="app.js"');
  ["read-engine.js", "read-prompts.js", "read-view.js"].forEach(function (name) {
    const at = html.indexOf('src="' + name + '"');
    assert.ok(at > 0, "应加载 " + name);
    assert.ok(at < appScript, name + " 必须在 app.js 之前");
  });
});

test("初始化：文章阅读视图隐藏，侧栏与主页各有一个入口", function () {
  const app = createApp();
  assert.equal(app.$("view-read").hidden, true);
  assert.equal(readTab(app).length, 1, "侧栏应有唯一的文章阅读入口");
  assert.equal(readCard(app).length, 1, "主页应有唯一的文章阅读卡片");
  assert.equal(app.$("read-config-notice").hidden, false, "未配置提示应常驻可见");
  assert.equal(app.$("read-start-button").disabled, true);
  assert.equal(app.$("read-view-input").hidden, false);
  assert.equal(app.$("read-view-reader").hidden, true);
});

test("初始化：阅读模块的错误、离线与加载三态默认都不显示", function () {
  const app = createApp();
  assert.equal(app.$("read-state-error").hidden, true);
  assert.equal(app.$("read-state-offline").hidden, true);
  assert.equal(app.$("read-state-loading").hidden, true);
});

test("文章阅读：字数不足 10 字时不能开始阅读", function () {
  const app = createApp();
  const textarea = app.$("read-input-textarea");
  textarea.value = "短い";
  textarea.dispatch("input");
  assert.equal(app.$("read-char-count").textContent, "2 字");
  assert.equal(app.$("read-start-button").disabled, true);
  app.click(app.$("read-start-button"));
  assert.equal(app.$("read-view-reader").hidden, true, "门槛不足时不应切到阅读器");
});

test("文章阅读：粘贴文章后本地切段并保留逐词与译文容器", function () {
  const app = createApp();
  const textarea = app.$("read-input-textarea");
  textarea.value = "今日はいい天気です。\n散歩に行きました。\n\n明日は雨だそうだ。";
  textarea.dispatch("input");
  assert.equal(app.$("read-start-button").disabled, false);
  app.click(app.$("read-start-button"));
  assert.equal(app.$("read-view-input").hidden, true);
  assert.equal(app.$("read-view-reader").hidden, false);
  assert.equal(app.$("read-back-input").hidden, false);

  const cards = app.document.querySelectorAll(".read-paragraph-card");
  assert.equal(cards.length, 2, "空行应把文章切成两段");
  assert.equal(app.$("read-paragraph-count").textContent, "共 2 段");
  assert.equal(app.$("read-paragraph-empty").hidden, true);
  assert.equal(
    cards[0].querySelectorAll(".read-paragraph-text")[0].textContent,
    "今日はいい天気です。\n散歩に行きました。"
  );
  assert.equal(cards[0].querySelectorAll(".read-paragraph-translation")[0].hidden, true);
  assert.equal(cards[0].querySelectorAll(".read-paragraph-tokens")[0].hidden, true);
  assert.equal(cards[0].querySelectorAll(".read-paragraph-status")[0].textContent,
    "点击这一段解析 · 本次约 2 次请求");
});

test("文章阅读：换一篇文章回到输入页", function () {
  const app = createApp();
  const textarea = app.$("read-input-textarea");
  textarea.value = "今日はいい天気です。散歩に行きました。";
  textarea.dispatch("input");
  app.click(app.$("read-start-button"));
  assert.equal(app.$("read-view-reader").hidden, false);
  app.click(app.$("read-back-input"));
  assert.equal(app.$("read-view-input").hidden, false);
  assert.equal(app.$("read-view-reader").hidden, true);
  assert.equal(app.$("read-back-input").hidden, true);
});

test("文章阅读：侧栏导航切换、播报与引导文案", function () {
  const app = createApp();
  tab(app, "read");
  assert.equal(app.$("view-read").hidden, false);
  assert.equal(app.$("view-home").hidden, true);
  assert.equal(app.$("view-announcer").textContent, "已进入文章阅读");
  assert.equal(app.$("intro-copy").textContent,
    "贴入日语文章，先在本地切成段落读原文；配置模型后可逐段查看读音、词性与译文。");
});

test("文章阅读：主页卡片可直接进入阅读视图", function () {
  const app = createApp();
  app.click(readCard(app)[0]);
  assert.equal(app.$("view-read").hidden, false);
  assert.equal(app.$("view-particle").hidden, true);
});

// ── 文章阅读 · 模型 / API 设置（B3，全程 Fake transport） ──────────────────

const READ_CONFIG_KEY = "japanese-read-analyzer-config-v1";

function openReadSettings(app) {
  tab(app, "read");
  app.click(app.$("read-open-settings"));
}

function fillReadForm(app, baseUrl, model, key) {
  app.$("read-config-base-url").value = baseUrl;
  app.$("read-config-model").value = model;
  app.$("read-config-key").value = key;
  app.$("read-config-base-url").dispatch("input");
}

function flushPromises() {
  return new Promise(function (resolve) { setTimeout(resolve, 0); });
}

test("文章阅读：三个子视图互斥，设置页默认隐藏", function () {
  const app = createApp();
  assert.equal(app.$("read-view-input").hidden, false);
  assert.equal(app.$("read-view-reader").hidden, true);
  assert.equal(app.$("read-view-settings").hidden, true);
  openReadSettings(app);
  assert.equal(app.$("read-view-input").hidden, true);
  assert.equal(app.$("read-view-settings").hidden, false);
  app.click(app.$("read-settings-back"));
  assert.equal(app.$("read-view-input").hidden, false);
  assert.equal(app.$("read-view-settings").hidden, true);
});

test("文章阅读：设置页实时显示规范化后的实际请求地址", function () {
  const app = createApp();
  openReadSettings(app);
  assert.equal(app.$("read-config-resolved-url").textContent, "—");
  fillReadForm(app, "https://api.example.com", "m", "k");
  assert.equal(app.$("read-config-resolved-url").textContent,
    "https://api.example.com/v1/chat/completions");
  app.$("read-config-base-url").value = "https://open.example/api/paas/v4";
  app.$("read-config-base-url").dispatch("input");
  assert.equal(app.$("read-config-resolved-url").textContent,
    "https://open.example/api/paas/v4/chat/completions");
});

test("文章阅读：空表单保存被拦下且不写本地存储", function () {
  const app = createApp();
  openReadSettings(app);
  app.click(app.$("read-config-save"));
  assert.equal(app.$("read-config-errors").hidden, false);
  // 温度输入框有默认值 0.2，因此只报地址 / 模型 / 密钥三项。
  assert.equal(app.$("read-config-errors").querySelectorAll("li").length, 3);
  assert.equal(app.window.localStorage.getItem(READ_CONFIG_KEY), null);
});

test("文章阅读：保存配置后密钥只以末四位掩码回显", function () {
  const app = createApp();
  openReadSettings(app);
  fillReadForm(app, "https://api.example.com", "test-model", "sk-abcd1234");
  app.click(app.$("read-config-save"));
  assert.equal(app.$("read-config-errors").hidden, true);
  assert.equal(app.$("read-config-test-result").textContent, "已保存。");

  const stored = JSON.parse(app.window.localStorage.getItem(READ_CONFIG_KEY));
  assert.equal(stored.baseUrl, "https://api.example.com");
  assert.equal(stored.model, "test-model");
  assert.equal(stored.apiKey, "sk-abcd1234");
  assert.equal(stored.temperature, 0.2);

  app.click(app.$("read-settings-back"));
  openReadSettings(app);
  assert.equal(app.$("read-config-key").value, "", "密钥明文不得回填到输入框");
  assert.equal(app.$("read-config-key-hint").hidden, false);
  assert.equal(app.$("read-config-key-hint").textContent, "已保存密钥：••••••1234");
});

test("文章阅读：修改密钥可留空沿用已保存值", function () {
  const app = createApp();
  openReadSettings(app);
  fillReadForm(app, "https://api.example.com", "m", "sk-first1234");
  app.click(app.$("read-config-save"));
  app.$("read-config-model").value = "another-model";
  app.$("read-config-key").value = "";
  app.click(app.$("read-config-save"));
  const stored = JSON.parse(app.window.localStorage.getItem(READ_CONFIG_KEY));
  assert.equal(stored.model, "another-model");
  assert.equal(stored.apiKey, "sk-first1234");
});

test("文章阅读：坏配置 JSON 回退为空配置且不崩溃", function () {
  const bad = {};
  bad[READ_CONFIG_KEY] = "{not-json";
  const app = createApp({ storage: bad });
  openReadSettings(app);
  assert.equal(app.$("read-config-base-url").value, "");
  assert.equal(app.$("read-config-model").value, "");
  assert.equal(app.$("read-config-resolved-url").textContent, "—");
});

test("文章阅读：已保存配置可跨会话读回", function () {
  const stored = {};
  stored[READ_CONFIG_KEY] = JSON.stringify({
    baseUrl: "https://saved.example/v1", model: "saved-model",
    apiKey: "sk-saved9876", temperature: 0.5
  });
  const app = createApp({ storage: stored });
  openReadSettings(app);
  assert.equal(app.$("read-config-base-url").value, "https://saved.example/v1");
  assert.equal(app.$("read-config-model").value, "saved-model");
  assert.equal(app.$("read-config-temperature").value, "0.5");
  assert.equal(app.$("read-config-key-hint").textContent, "已保存密钥：••••••9876");
});

test("文章阅读：测试连接走 Fake transport 并给出中文结果", function () {
  const app = createApp();
  openReadSettings(app);
  fillReadForm(app, "https://api.example.com", "m", "k");
  app.click(app.$("read-config-test"));
  return flushPromises().then(function () {
    assert.equal(app.$("read-config-test-result").hidden, false);
    assert.equal(app.$("read-config-test-result").textContent, "连接成功，模型返回可解析。");
  });
});

// ── 文章阅读 · 核心解析闭环（B4，全程 Fake fetch） ─────────────────────────

const READ_CACHE_KEY_B4 = "japanese-read-analyzer-cache-v1";
const READ_BUDGET_KEY_B4 = "japanese-read-analyzer-budget-v1";

function configuredApp(storage, model) {
  const values = Object.assign({}, storage || {});
  values[READ_CONFIG_KEY] = JSON.stringify({
    baseUrl: "https://api.example.com",
    model: model || "m1",
    apiKey: "sk-test",
    temperature: 0.2
  });
  return createApp({ storage: values });
}

function charTokens(text) {
  const out = [];
  Array.from(text).forEach(function (ch) {
    let pos = "名詞";
    let furigana = "";
    if (ch === "は") pos = "助詞";
    else if (ch === "です" || ch === "た") pos = "助動詞";
    else if (ch === "\n") pos = "改行";
    else if (/[。、，．！？]/.test(ch)) pos = "記号";
    else if (/[\u4e00-\u9faf]/.test(ch)) furigana = "かんじ";
    out.push({ word: ch, pos: pos, furigana: furigana });
  });
  return out;
}

const DETAIL_JSON = JSON.stringify({
  meaning: "天气", dictionaryForm: "天気", usage: "与「いい」搭配表示天气好",
  conjugation: "", example: "いい天気ですね。", exampleMeaning: "天气真好。"
});
const GRAMMAR_JSON = JSON.stringify({
  patterns: [{ label: "〜ね", span: [0, 3], meaning: "确认语气", note: "句尾", level: "" }],
  structure: { clauses: [{ span: [0, 3], role: "主节", head: "ですね" }], relations: [] },
  style: { politeness: "敬体", register: "口语" },
  engineRefs: [{ type: "particle", surface: "は", span: [2, 3] }]
});

function installReadFetch(app, options) {
  const opts = options || {};
  const calls = [];
  app.window.fetch = function (url, init) {
    const body = JSON.parse(init.body);
    const system = body.messages[0].content;
    let kind = "translate";
    if (system.indexOf("词法标注器") >= 0) kind = "analyze";
    else if (system.indexOf("转写器") >= 0) kind = "ocr";
    else if (system.indexOf("日语学习助手") >= 0) kind = "assistant";
    else if (system.indexOf("双语词典编辑") >= 0) kind = "detail";
    else if (system.indexOf("日语教育语法分析器") >= 0) kind = "grammar";
    calls.push({ url: url, body: body, kind: kind });
    let ok = true;
    let status = 200;
    let content;
    if (kind === "detail") {
      content = opts.detailContent !== undefined ? opts.detailContent : DETAIL_JSON;
      if (opts.detailStatus) { ok = false; status = opts.detailStatus; content = "{}"; }
    } else if (kind === "grammar") {
      content = opts.grammarContent !== undefined ? opts.grammarContent : GRAMMAR_JSON;
      if (opts.grammarStatus) { ok = false; status = opts.grammarStatus; content = "{}"; }
    } else if (kind === "ocr") {
      content = opts.ocrContent !== undefined ? opts.ocrContent : "今日はいい天気です。\n\n散歩に行きました。";
      if (opts.ocrStatus) { ok = false; status = opts.ocrStatus; content = "{}"; }
    } else if (kind === "assistant") {
      content = opts.assistantContent !== undefined ? opts.assistantContent : "这是助手的回复。";
      if (opts.assistantStatus) { ok = false; status = opts.assistantStatus; content = "{}"; }
    } else if (body.response_format) {
      const match = /原文（JSON 字符串）：("(?:[^"\\]|\\.)*")/.exec(body.messages[1].content);
      const text = match ? JSON.parse(match[1]) : "";
      content = opts.analyzeContent !== undefined
        ? opts.analyzeContent
        : JSON.stringify({ tokens: charTokens(text) });
      if (opts.analyzeStatus) { ok = false; status = opts.analyzeStatus; content = "{}"; }
    } else {
      content = opts.translationContent !== undefined ? opts.translationContent : "这是译文。";
      if (opts.translateStatus) { ok = false; status = opts.translateStatus; content = "{}"; }
    }
    return Promise.resolve({
      ok: ok,
      status: status,
      text: function () {
        return Promise.resolve(JSON.stringify({
          choices: [{ message: { content: content }, finish_reason: opts.finishReason || "stop" }]
        }));
      }
    });
  };
  return calls;
}

function openReaderWith(app, text) {
  tab(app, "read");
  app.$("read-input-textarea").value = text || "今日はいい天気ですね。";
  app.$("read-input-textarea").dispatch("input");
  app.click(app.$("read-start-button"));
}

function firstCard(app) {
  const cards = app.document.querySelectorAll(".read-paragraph-card");
  assert.equal(cards.length, 1);
  return cards[0];
}

function nodeIn(card, className) {
  return card.querySelectorAll("." + className)[0];
}

test("文章阅读：点段解析后逐词、注音、图例与译文同卡呈现", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.equal(card.dataset.state, "parsed");
    assert.equal(calls.length, 2, "analyze 与 translate 各一次");
    assert.equal(nodeIn(card, "read-paragraph-text").hidden, true, "对齐后隐藏重复原句");
    assert.equal(nodeIn(card, "read-paragraph-tokens").hidden, false);
    assert.equal(nodeIn(card, "read-paragraph-translation").textContent, "这是译文。");
    assert.ok(card.querySelectorAll(".read-token").length >= 4);
    assert.ok(card.querySelectorAll("rt").length > 0, "汉字应有 ruby 注音");
    const tokenUnits = card.querySelectorAll(".read-token");
    assert.ok(tokenUnits.length > 0);
    assert.ok(tokenUnits[0].dataset.posGroup, "逐词应带 pos 分组");
    assert.ok(app.document.querySelectorAll(".read-legend-item").length > 0);
  });
});

test("文章阅读：段内换行渲染为独立断行组", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app, "今日はいい天気ですね。\n散歩に行きました。");
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.equal(card.dataset.state, "parsed");
    const groups = card.querySelectorAll(".read-token-group");
    const breaks = groups.filter(function (group) {
      return group.classList.contains("is-paragraph-break");
    });
    assert.equal(breaks.length, 1);
  });
});

test("文章阅读：缓存命中零请求，且缓存按段落文本与模型落盘", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const stored = JSON.parse(app.window.localStorage.getItem(READ_CACHE_KEY_B4));
    assert.equal(stored.entries.length, 1);
    assert.equal(stored.entries[0].model, "m1");
    assert.equal(stored.entries[0].key,
      require("../read-engine.js").cacheKey("今日はいい天気ですね。", "m1"));
    app.click(firstCard(app));
    return flushPromises();
  }).then(function () {
    assert.equal(calls.length, 2, "第二次点击不应再发请求");
    assert.equal(firstCard(app).dataset.state, "parsed");
  });
});

test("文章阅读：换模型名后同一段重新请求", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(app.$("read-open-settings"));
    app.$("read-config-model").value = "m2";
    app.click(app.$("read-config-save"));
    app.click(app.$("read-settings-back"));
    app.click(firstCard(app));
    return flushPromises();
  }).then(function () {
    assert.equal(calls.length, 4, "换模型后缓存键改变，应重新请求");
    const stored = JSON.parse(app.window.localStorage.getItem(READ_CACHE_KEY_B4));
    assert.equal(stored.entries.length, 2);
  });
});

test("文章阅读：原文重建不一致时隐藏逐词、保底显示原句", function () {
  const app = configuredApp();
  installReadFetch(app, { analyzeContent: '{"tokens":[{"word":"今日","pos":"名詞"}]}' });
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.equal(card.dataset.state, "parsed");
    assert.equal(nodeIn(card, "read-paragraph-text").hidden, false);
    assert.equal(nodeIn(card, "read-paragraph-tokens").hidden, true);
    assert.match(nodeIn(card, "read-paragraph-status").textContent, /分词不可用/);
  });
});

test("文章阅读：译文失败不推翻逐词结果", function () {
  const app = configuredApp();
  installReadFetch(app, { translateStatus: 500 });
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.equal(card.dataset.state, "parsed");
    assert.equal(nodeIn(card, "read-paragraph-translation").textContent, "");
    assert.match(nodeIn(card, "read-paragraph-status").textContent, /译文暂不可用/);
  });
});

test("文章阅读：请求失败给中文提示且不写缓存", function () {
  const app = configuredApp();
  installReadFetch(app, { analyzeStatus: 401 });
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.equal(card.dataset.state, "error");
    assert.match(nodeIn(card, "read-paragraph-status").textContent, /密钥无效/);
    assert.equal(app.window.localStorage.getItem(READ_CACHE_KEY_B4), null);
  });
});

test("文章阅读：截断返回不写缓存", function () {
  const app = configuredApp();
  installReadFetch(app, { finishReason: "length" });
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    assert.equal(firstCard(app).dataset.state, "error");
    assert.equal(app.window.localStorage.getItem(READ_CACHE_KEY_B4), null);
  });
});

test("文章阅读：三个显示开关可切换注音、罗马音与译文", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.ok(card.querySelectorAll("rt").length > 0);
    app.$("read-toggle-furigana").checked = false;
    app.$("read-toggle-furigana").dispatch("change");
    assert.equal(card.querySelectorAll("rt").length, 0);
    app.$("read-toggle-romaji").checked = true;
    app.$("read-toggle-romaji").dispatch("change");
    assert.ok(card.querySelectorAll(".read-token-romaji").length > 0);
    app.$("read-toggle-translation").checked = false;
    app.$("read-toggle-translation").dispatch("change");
    assert.equal(nodeIn(card, "read-paragraph-translation").hidden, true);
  });
});

test("文章阅读：未配置时点段不发请求并提示去设置", function () {
  const app = createApp();
  const calls = installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    assert.equal(calls.length, 0);
    assert.equal(firstCard(app).dataset.state, "error");
    assert.match(nodeIn(firstCard(app), "read-paragraph-status").textContent, /先去设置页/);
  });
});

test("文章阅读：离线时只读缓存不发请求", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  app.window.navigator = { onLine: false };
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    assert.equal(calls.length, 0);
    assert.equal(firstCard(app).dataset.state, "offline");
    assert.match(nodeIn(firstCard(app), "read-paragraph-status").textContent, /离线/);
  });
});

test("文章阅读：达到每日预算上限后不再发请求", function () {
  const storage = {};
  const today = new Date();
  const localDate = today.getFullYear() + "-" +
    String(today.getMonth() + 1).padStart(2, "0") + "-" +
    String(today.getDate()).padStart(2, "0");
  storage[READ_BUDGET_KEY_B4] = JSON.stringify({
    date: localDate, dailyCount: 100, articleCount: 0
  });
  const app = configuredApp(storage);
  const calls = installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    assert.equal(calls.length, 0);
    assert.equal(firstCard(app).dataset.state, "budget");
    assert.match(nodeIn(firstCard(app), "read-paragraph-status").textContent, /上限/);
  });
});

// ── 文章阅读 · 词详情与语法抽屉（B5） ──────────────────────────────────────

function parsedFirstCard(app) {
  const appInstance = configuredApp();
  const calls = installReadFetch(appInstance);
  openReaderWith(appInstance);
  appInstance.click(firstCard(appInstance));
  return flushPromises().then(function () {
    return { app: appInstance, calls: calls, card: firstCard(appInstance) };
  });
}

test("文章阅读：点词打开底部抽屉并展示六字段词详情", function () {
  return parsedFirstCard().then(function (context) {
    const unit = context.card.querySelectorAll(".read-token")[0];
    context.app.click(unit);
    return flushPromises().then(function () {
      assert.equal(context.app.$("read-sheet").hidden, false);
      assert.match(context.app.$("read-sheet-title").textContent, /词详情/);
      assert.equal(context.app.$("read-sheet-body").querySelectorAll(".read-sheet-row").length, 6);
      assert.equal(context.calls.filter(function (c) { return c.kind === "detail"; }).length, 1);
    });
  });
});

test("文章阅读：同一词二次点击命中 LRU 不再请求", function () {
  return parsedFirstCard().then(function (context) {
    const unit = context.card.querySelectorAll(".read-token")[0];
    context.app.click(unit);
    return flushPromises().then(function () {
      context.app.click(unit);
      return flushPromises();
    }).then(function () {
      assert.equal(context.calls.filter(function (c) { return c.kind === "detail"; }).length, 1);
      assert.equal(context.app.$("read-sheet").hidden, false);
    });
  });
});

test("文章阅读：词详情失败在抽屉里给中文提示", function () {
  const app = configuredApp();
  installReadFetch(app, { detailStatus: 401 });
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(firstCard(app).querySelectorAll(".read-token")[0]);
    return flushPromises();
  }).then(function () {
    assert.equal(app.$("read-sheet").hidden, false);
    assert.match(app.$("read-sheet-body").textContent, /密钥无效/);
  });
});

test("文章阅读：点看语法展示句型结构文体并高亮原文", function () {
  return parsedFirstCard().then(function (context) {
    const button = context.card.querySelectorAll(".read-grammar-button")[0];
    assert.equal(button.hidden, false, "解析成功后才显示看语法按钮");
    context.app.click(button);
    return flushPromises().then(function () {
      assert.equal(context.app.$("read-sheet").hidden, false);
      assert.equal(context.app.$("read-sheet-title").textContent, "语法分析");
      assert.equal(context.app.$("read-sheet-body").querySelectorAll(".read-sheet-row").length, 4);
      assert.equal(context.calls.filter(function (c) { return c.kind === "grammar"; }).length, 1);
      const hits = context.card.querySelectorAll(".read-token").filter(function (unit) {
        return unit.classList.contains("is-grammar-hit");
      });
      assert.ok(hits.length > 0, "句型 span 应高亮对应逐词");
    });
  });
});

test("文章阅读：语法结果按段缓存，二次点击零请求", function () {
  return parsedFirstCard().then(function (context) {
    const button = context.card.querySelectorAll(".read-grammar-button")[0];
    context.app.click(button);
    return flushPromises().then(function () {
      context.app.click(button);
      return flushPromises();
    }).then(function () {
      assert.equal(context.calls.filter(function (c) { return c.kind === "grammar"; }).length, 1);
      const stored = JSON.parse(context.app.window.localStorage.getItem(READ_CACHE_KEY_B4));
      assert.ok(stored.entries[0].grammar, "语法结果应挂在同一条按段缓存里");
    });
  });
});

test("文章阅读：抽屉可由关闭按钮与遮罩收起", function () {
  return parsedFirstCard().then(function (context) {
    context.app.click(context.card.querySelectorAll(".read-token")[0]);
    return flushPromises().then(function () {
      context.app.click(context.app.$("read-sheet-close"));
      assert.equal(context.app.$("read-sheet").hidden, true);
      context.app.click(context.card.querySelectorAll(".read-grammar-button")[0]);
      return flushPromises();
    }).then(function () {
      assert.equal(context.app.$("read-sheet").hidden, false);
      context.app.click(context.app.$("read-sheet-backdrop"));
      assert.equal(context.app.$("read-sheet").hidden, true);
    });
  });
});

test("文章阅读：未配置时不暴露逐词与语法入口", function () {
  const app = createApp();
  const calls = installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const card = firstCard(app);
    assert.equal(calls.length, 0);
    assert.equal(card.dataset.state, "error");
    assert.equal(card.querySelectorAll(".read-token").length, 0);
    assert.equal(card.querySelectorAll(".read-grammar-button")[0].hidden, true);
    assert.match(nodeIn(card, "read-paragraph-status").textContent, /先去设置页/);
  });
});

// ── 文章阅读 · 接回既有练习体系（B6） ──────────────────────────────────────

const READ_BOOKMARKS_KEY_B6 = "japanese-verb-practice-bookmarks-v1";
const READ_ACTIVITY_KEY_B6 = "japanese-read-analyzer-activity-v1";

function sheetAction(app, label) {
  return app.document.querySelectorAll(".read-sheet-action").filter(function (node) {
    return node.textContent === label;
  })[0];
}

function particleToken(card) {
  return card.querySelectorAll(".read-token").filter(function (unit) {
    return unit.textContent === "は";
  })[0];
}

test("文章阅读：可反查的词在抽屉里提供练这题与收藏入口", function () {
  return parsedFirstCard().then(function (context) {
    const token = particleToken(context.card);
    assert.ok(token, "文章里应有助词「は」");
    context.app.click(token);
    return flushPromises().then(function () {
      assert.ok(sheetAction(context.app, "练这个词"));
      assert.ok(sheetAction(context.app, "收藏这个词"));
      assert.ok(sheetAction(context.app, "收藏这一段"));
    });
  });
});

test("文章阅读：普通名词不会暴露练这题入口", function () {
  return parsedFirstCard().then(function (context) {
    const noun = context.card.querySelectorAll(".read-token")[0];
    context.app.click(noun);
    return flushPromises().then(function () {
      assert.equal(sheetAction(context.app, "练这个词"), undefined);
      assert.ok(sheetAction(context.app, "收藏这个词"));
    });
  });
});

test("文章阅读：收藏词与段落复用既有书签键并可取消", function () {
  return parsedFirstCard().then(function (context) {
    context.app.click(particleToken(context.card));
    return flushPromises().then(function () {
      context.app.click(sheetAction(context.app, "收藏这个词"));
      context.app.click(sheetAction(context.app, "收藏这一段"));
      const stored = JSON.parse(context.app.window.localStorage.getItem(READ_BOOKMARKS_KEY_B6));
      assert.equal(stored.items.length, 2);
      assert.deepEqual(stored.items.map(function (item) { return item.type; }).sort(),
        ["sentence", "word"]);
      assert.ok(sheetAction(context.app, "已收藏 · 取消"), "按钮应变为已收藏状态");
      const activity = JSON.parse(context.app.window.localStorage.getItem(READ_ACTIVITY_KEY_B6));
      assert.equal(activity.words, 1);
    });
  });
});

test("文章阅读：点练这个词进入既有助词练习视图", function () {
  return parsedFirstCard().then(function (context) {
    context.app.click(particleToken(context.card));
    return flushPromises().then(function () {
      context.app.click(sheetAction(context.app, "练这个词"));
      assert.equal(context.app.$("view-particle").hidden, false);
      assert.equal(context.app.$("view-read").hidden, true);
      assert.equal(context.app.$("read-sheet").hidden, true, "切视图应自动收起抽屉");
      assert.equal(context.app.$("particle-view-practice").hidden, false);
    });
  });
});

test("文章阅读：语法抽屉提供练这组词并进入既有练习", function () {
  return parsedFirstCard().then(function (context) {
    context.app.click(context.card.querySelectorAll(".read-grammar-button")[0]);
    return flushPromises().then(function () {
      assert.ok(sheetAction(context.app, "练这组词"));
      context.app.click(sheetAction(context.app, "练这组词"));
      assert.equal(context.app.$("view-particle").hidden, false);
      assert.equal(context.app.$("read-sheet").hidden, true);
    });
  });
});

test("文章阅读：主页显示今日阅读段落摘要", function () {
  return parsedFirstCard().then(function (context) {
    context.app.click(context.app.$("brand-home"));
    assert.match(context.app.$("home-read-status").textContent, /今日已读 1 段/);
    assert.equal(context.app.$("home-read-count").textContent, "1 段");
  });
});

test("文章阅读：测试连接失败时按错误码给中文提示", function () {
  const app = createApp();
  app.window.fetch = function () {
    return Promise.resolve({
      ok: false,
      status: 401,
      text: function () { return Promise.resolve('{"error":{"message":"bad key"}}'); }
    });
  };
  openReadSettings(app);
  fillReadForm(app, "https://api.example.com", "m", "k");
  app.click(app.$("read-config-test"));
  return flushPromises().then(function () {
    assert.equal(app.$("read-config-test-result").hidden, false);
    assert.equal(app.$("read-config-test-result").textContent,
      "密钥无效或没有该模型的权限，先检查密钥。");
  });
});

// ── B7 · 长文与流式增强（UI 接线） ─────────────────────────────────────────

function mustSheetAction(app, label) {
  const buttons = Array.prototype.slice.call(app.document.querySelectorAll(".read-sheet-action"));
  const found = buttons.filter(function (button) { return button.textContent === label; })[0];
  assert.ok(found, "弹层应有「" + label + "」按钮");
  return found;
}

const B7_THREE_PARAGRAPHS = "今日はいい天気ですね。\n\n散歩に行きました。\n\n明日も晴れです。";

test("文章阅读 B7：一键全文先出费用确认弹层，不确认不发请求", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  openReaderWith(app, B7_THREE_PARAGRAPHS);
  assert.equal(app.document.querySelectorAll(".read-paragraph-card").length, 3);
  app.click(app.$("read-analyze-all"));
  assert.equal(app.$("read-sheet").hidden, false, "应弹出费用确认层");
  const body = app.$("read-sheet-body").textContent;
  assert.ok(body.indexOf("共 3 段") >= 0, "应写明总段数");
  assert.ok(body.indexOf("6 次请求") >= 0, "应写明请求数（每段 2 次）");
  assert.equal(calls.length, 0, "确认前不发请求");
  app.click(mustSheetAction(app, "取消"));
  assert.equal(app.$("read-sheet").hidden, true);
  assert.equal(calls.length, 0, "取消后仍不发请求");
});

test("文章阅读 B7：确认后全文按原文序解析且状态条收起", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  openReaderWith(app, B7_THREE_PARAGRAPHS);
  app.click(app.$("read-analyze-all"));
  app.click(mustSheetAction(app, "开始解析"));
  assert.equal(app.$("read-sheet").hidden, true, "确认后弹层关闭");
  return flushPromises().then(function () {
    const cards = app.document.querySelectorAll(".read-paragraph-card");
    assert.equal(calls.length, 6, "3 段 × 2 请求");
    Array.prototype.slice.call(cards).forEach(function (card, index) {
      assert.equal(card.dataset.state, "parsed", "第 " + (index + 1) + " 段应已落位");
    });
    assert.equal(app.$("read-run-status").hidden, true, "完成后状态条收起");
  });
});

test("文章阅读 B7：解析中状态条可取消，取消中止全部在途块并复位卡片", function () {
  const app = configuredApp();
  openReaderWith(app, B7_THREE_PARAGRAPHS);
  // 挂起的 fetch + 可中止的 AbortController：验证取消能中止在途块。
  let fetchCount = 0;
  app.window.AbortController = function () {
    this.signal = { _onabort: null };
    const self = this;
    this.abort = function () {
      if (self.signal._onabort) self.signal._onabort();
    };
  };
  app.window.fetch = function (url, init) {
    fetchCount++;
    return new Promise(function (resolve, reject) {
      if (init && init.signal) {
        init.signal._onabort = function () {
          const error = new Error("aborted");
          error.name = "AbortError";
          reject(error);
        };
      }
    });
  };
  app.click(app.$("read-analyze-all"));
  app.click(mustSheetAction(app, "开始解析"));
  return flushPromises().then(function () {
    assert.equal(app.$("read-run-status").hidden, false, "运行中显示状态条");
    assert.equal(app.$("read-run-cancel").hidden, false, "状态条带取消按钮");
    assert.equal(fetchCount, 4, "并发上限 2 段 = 在途 4 个请求");
    app.click(app.$("read-run-cancel"));
    return flushPromises();
  }).then(function () {
    assert.equal(app.$("read-run-status").hidden, true, "取消后状态条收起");
    const cards = app.document.querySelectorAll(".read-paragraph-card");
    Array.prototype.slice.call(cards).forEach(function (card) {
      assert.equal(card.dataset.state, "idle", "取消后卡片回到待解析态");
    });
  });
});

test("文章阅读 B7：划选区间浮现分析按钮，结果进弹层且列表不动", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  openReaderWith(app, "今日はいい天気ですね。\n\n散歩に行きました。");
  const cards = Array.prototype.slice.call(app.document.querySelectorAll(".read-paragraph-card"));
  assert.equal(cards.length, 2);
  app.window.getSelection = function () {
    return {
      rangeCount: 1,
      isCollapsed: false,
      anchorNode: cards[0],
      focusNode: cards[1],
      getRangeAt: function () {
        return { getClientRects: function () { return [{ right: 20, bottom: 30 }]; } };
      }
    };
  };
  app.$("read-paragraph-list").dispatch("mouseup");
  assert.equal(app.$("read-selection-action").hidden, false, "选区旁应浮现分析按钮");
  app.click(app.$("read-selection-action"));
  assert.equal(app.$("read-selection-action").hidden, true);
  return flushPromises().then(function () {
    assert.equal(app.$("read-sheet").hidden, false, "合并结果进弹层");
    assert.ok(app.$("read-sheet-body").querySelectorAll(".read-token").length > 0, "弹层应有逐词");
    assert.equal(calls.length, 2, "两段合批一次解析 + 一次翻译");
    // 列表不动：段落卡片保持待解析态
    assert.equal(cards[0].dataset.state, "idle");
    assert.equal(cards[1].dataset.state, "idle");
    assert.equal(app.$("read-run-status").hidden, true);
  });
});

test("文章阅读 B7：未选中文字时不浮现划选按钮", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app, "今日はいい天気ですね。");
  app.window.getSelection = function () {
    return { rangeCount: 0, isCollapsed: true };
  };
  app.$("read-paragraph-list").dispatch("mouseup");
  assert.equal(app.$("read-selection-action").hidden, true);
});

test("文章阅读 B7：选区按钮 mousedown 阻止默认以保住选区", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app, "今日はいい天気ですね。");
  const event = app.$("read-selection-action").dispatch("mousedown");
  assert.equal(event.defaultPrevented, true, "preventDefault 保住选区，避免 click 落空");
});

// ── B8-f · 本地埋点（设置页统计区） ─────────────────────────────────────

const READ_STATS_KEY_B8 = "japanese-read-analyzer-stats-v1";

test("文章阅读 B8：解析成功计入次数与耗时，设置页展示统计", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(app.$("read-open-settings"));
    assert.equal(app.$("read-stats-parse").textContent, "1 次");
    assert.equal(app.$("read-stats-cache").textContent, "0 次");
    assert.ok(/^\d+ 毫秒$/.test(app.$("read-stats-avg").textContent), "平均耗时应带单位");
    assert.equal(app.$("read-stats-errors").textContent, "无");
    const stored = JSON.parse(app.window.localStorage.getItem(READ_STATS_KEY_B8));
    assert.equal(stored.parseCount, 1, "落盘只记次数不记原文");
    assert.ok(JSON.stringify(stored).indexOf("今日") < 0, "统计不含原文");
  });
});

test("文章阅读 B8：缓存命中入账为命中，不计解析次数", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(firstCard(app));
    return flushPromises();
  }).then(function () {
    app.click(app.$("read-open-settings"));
    assert.equal(app.$("read-stats-parse").textContent, "1 次");
    assert.equal(app.$("read-stats-cache").textContent, "1 次");
  });
});

test("文章阅读 B8：解析失败按错误分类入账", function () {
  const app = configuredApp();
  installReadFetch(app, { analyzeStatus: 500 });
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(app.$("read-open-settings"));
    assert.equal(app.$("read-stats-parse").textContent, "0 次");
    assert.ok(
      app.$("read-stats-errors").textContent.indexOf("服务 1") >= 0,
      "500 应归入服务类错误：" + app.$("read-stats-errors").textContent
    );
  });
});

test("文章阅读 B8：统计清零复位展示与存储", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app);
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(app.$("read-open-settings"));
    assert.equal(app.$("read-stats-parse").textContent, "1 次");
    app.click(app.$("read-stats-clear"));
    assert.equal(app.$("read-stats-parse").textContent, "0 次");
    assert.equal(app.$("read-stats-avg").textContent, "—");
    assert.equal(app.$("read-stats-errors").textContent, "无");
    const stored = JSON.parse(app.window.localStorage.getItem(READ_STATS_KEY_B8));
    assert.equal(stored.parseCount, 0);
    assert.equal(stored.cacheHits, 0);
  });
});

// ── B8-b · 显示弹层收纳 ─────────────────────────────────────────────────

test("文章阅读 B8：显示弹层收纳三开关、语速与图例，原显示栏撤出阅读流", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app);
  assert.equal(app.document.querySelectorAll("#read-view-reader .read-display-bar").length, 0,
    "阅读流里不应再有显示栏");
  assert.equal(app.$("read-display-sheet").hidden, true);
  app.click(app.$("read-open-display"));
  assert.equal(app.$("read-display-sheet").hidden, false, "点「显示」弹出设置层");
  assert.ok(app.$("read-toggle-furigana"), "弹层内保留注音开关（id 稳定）");
  assert.ok(app.$("read-tts-rate"), "弹层内含语速滑杆");
  app.click(app.$("read-display-close"));
  assert.equal(app.$("read-display-sheet").hidden, true);
});

test("文章阅读 B8：语速滑杆联动读数", function () {
  const app = configuredApp();
  openReaderWith(app);
  app.$("read-tts-rate").value = "1.5";
  app.$("read-tts-rate").dispatch("input");
  assert.equal(app.$("read-tts-rate-value").textContent, "1.5×");
});

// ── B8-c · 朗读（单段喇叭 + 全文播放条） ─────────────────────────────────

function installFakeTts(app) {
  const calls = { speak: [], cancel: 0, pause: 0, resume: 0 };
  function Utterance(text) { this.text = text; }
  app.window.SpeechSynthesisUtterance = Utterance;
  app.window.speechSynthesis = {
    speaking: false,
    speak: function (u) {
      calls.speak.push(u);
      this.speaking = true;
    },
    cancel: function () {
      calls.cancel += 1;
      this.speaking = false;
    },
    pause: function () { calls.pause += 1; },
    resume: function () { calls.resume += 1; }
  };
  return calls;
}

test("文章阅读 B8：无 speechSynthesis 时隐藏朗读入口", function () {
  const app = configuredApp();
  installReadFetch(app);
  openReaderWith(app);
  assert.equal(app.$("read-play-bar").hidden, true, "播放条应隐藏");
  const button = firstCard(app).querySelectorAll(".read-speak-button")[0];
  assert.equal(button.hidden, true, "喇叭按钮应隐藏");
});

test("文章阅读 B8：单段喇叭点读该段原文，再点停止且不触发解析", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  const tts = installFakeTts(app);
  openReaderWith(app, "今日はいい天気ですね。");
  const button = firstCard(app).querySelectorAll(".read-speak-button")[0];
  assert.equal(button.hidden, false);
  app.click(button);
  assert.equal(tts.speak.length, 1);
  assert.equal(tts.speak[0].text, "今日はいい天気ですね。");
  assert.equal(tts.speak[0].lang, "ja-JP");
  assert.equal(button.dataset.speaking, "true");
  app.click(button);
  assert.equal(tts.cancel, 1, "再点应停止");
  assert.equal(button.dataset.speaking, "false");
  assert.equal(calls.length, 0, "朗读不发解析请求");
});

test("文章阅读 B8：播放条顺序接续，读完自动收口", function () {
  const app = configuredApp();
  installReadFetch(app);
  const tts = installFakeTts(app);
  openReaderWith(app, B7_THREE_PARAGRAPHS);
  assert.equal(app.$("read-play-bar").hidden, false);
  app.click(app.$("read-play-toggle"));
  assert.equal(tts.speak.length, 1, "从第 1 段开始");
  assert.equal(app.$("read-play-toggle").textContent, "暂停");
  // 模拟第 1 段读完 → 自动接第 2 段。
  tts.speak[0].onend();
  assert.equal(tts.speak.length, 2);
  assert.equal(tts.speak[1].text, "散歩に行きました。");
  tts.speak[1].onend();
  assert.equal(tts.speak.length, 3);
  tts.speak[2].onend();
  assert.equal(app.$("read-play-toggle").textContent, "播放", "读完复位");
  assert.equal(tts.speak.length, 3, "共 3 段不再接续");
});

test("文章阅读 B8：暂停与继续走 synth 的 pause/resume", function () {
  const app = configuredApp();
  installReadFetch(app);
  const tts = installFakeTts(app);
  openReaderWith(app, B7_THREE_PARAGRAPHS);
  app.click(app.$("read-play-toggle"));
  app.click(app.$("read-play-toggle"));
  assert.equal(tts.pause, 1, "播放中再点应暂停");
  assert.equal(app.$("read-play-toggle").textContent, "继续");
  app.click(app.$("read-play-toggle"));
  assert.equal(tts.resume, 1, "继续应 resume");
  assert.equal(app.$("read-play-toggle").textContent, "暂停");
});

test("文章阅读 B8：下一段按钮切段朗读", function () {
  const app = configuredApp();
  installReadFetch(app);
  const tts = installFakeTts(app);
  openReaderWith(app, B7_THREE_PARAGRAPHS);
  app.click(app.$("read-play-toggle"));
  assert.equal(tts.speak[0].text, "今日はいい天気ですね。");
  app.click(app.$("read-play-next"));
  assert.equal(tts.speak.length, 2);
  assert.equal(tts.speak[1].text, "散歩に行きました。");
  assert.equal(app.$("read-play-status").textContent.indexOf("2 / 3") >= 0, true);
});

// ── B8-d · 图片 OCR（双入口 + 压缩 + 确认） ─────────────────────────────

function stubOcrCompress(app) {
  const state = { files: [] };
  app.window.ReadOcr = {
    compressImage: function (win, file) {
      state.files.push(file);
      return Promise.resolve("data:image/jpeg;base64,STUB");
    }
  };
  return state;
}

test("文章阅读 B8：未配置时识图入口隐藏，配置后显示", function () {
  const bare = createApp();
  assert.equal(bare.$("read-ocr-import").hidden, true, "未配置应隐藏识图入口");
  const app = configuredApp();
  assert.equal(app.$("read-ocr-import").hidden, false);
});

test("文章阅读 B8：识图导入走确认弹层，确认后填入输入框并切段", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  const stub = stubOcrCompress(app);
  tab(app, "read");
  const fileInput = app.$("read-ocr-file");
  fileInput.files = [{ name: "photo.jpg" }];
  fileInput.dispatch("change");
  return flushPromises().then(function () {
    assert.equal(stub.files.length, 1, "应先走压缩");
    assert.equal(calls.length, 1, "只发 1 次转写请求");
    assert.equal(calls[0].kind, "ocr");
    assert.equal(calls[0].body.messages[1].content[1].image_url.url, "data:image/jpeg;base64,STUB");
    assert.equal(app.$("read-sheet").hidden, false, "应弹出识别结果确认层");
    assert.ok(app.$("read-sheet-body").textContent.indexOf("今日はいい天気です。") >= 0);
    app.click(mustSheetAction(app, "导入并切段"));
    assert.equal(app.$("read-input-textarea").value, "今日はいい天気です。\n\n散歩に行きました。");
    assert.equal(app.$("read-view-reader").hidden, false, "确认后进入阅读器");
    assert.equal(app.document.querySelectorAll(".read-paragraph-card").length, 2);
  });
});

test("文章阅读 B8：阅读器拖拽图片与按钮共用同一确认流程", function () {
  const app = configuredApp();
  const calls = installReadFetch(app);
  const stub = stubOcrCompress(app);
  openReaderWith(app, "今日はいい天気ですね。");
  app.$("read-view-reader").dispatch("drop", {
    dataTransfer: { files: [{ name: "shot.png" }] }
  });
  return flushPromises().then(function () {
    assert.equal(stub.files.length, 1);
    assert.equal(calls[0].kind, "ocr");
    assert.equal(app.$("read-sheet").hidden, false);
    app.click(mustSheetAction(app, "取消"));
    assert.equal(app.$("read-sheet").hidden, true, "取消不导入");
    assert.equal(app.$("read-input-textarea").value, "今日はいい天気ですね。", "原文不动");
  });
});

test("文章阅读 B8：转写失败提示且不填输入框", function () {
  const app = configuredApp();
  installReadFetch(app, { ocrStatus: 500 });
  stubOcrCompress(app);
  tab(app, "read");
  const fileInput = app.$("read-ocr-file");
  fileInput.files = [{ name: "bad.jpg" }];
  fileInput.dispatch("change");
  return flushPromises().then(function () {
    assert.equal(app.$("read-sheet").hidden, true, "失败不弹确认层");
    assert.equal(app.$("read-input-textarea").value, "", "输入框保持为空");
  });
});

// ── B8-e · AI 助手（底部弹层，默认关） ───────────────────────────────────

function assistantApp(enabled) {
  return createApp({
    storage: {
      [READ_CONFIG_KEY]: JSON.stringify({
        baseUrl: "https://api.example.com",
        model: "m1",
        apiKey: "sk-test",
        temperature: 0.2,
        assistantEnabled: enabled === true
      })
    }
  });
}

test("文章阅读 B8：助手默认关，设置开关启用后头部显示入口", function () {
  const off = assistantApp(false);
  assert.equal(off.$("read-open-assistant").hidden, true, "默认关应隐藏入口");
  const on = assistantApp(true);
  assert.equal(on.$("read-open-assistant").hidden, false, "启用后显示入口");
});

test("文章阅读 B8：助手提问注入当前段落上下文并显示对话", function () {
  const app = assistantApp(true);
  const calls = installReadFetch(app);
  openReaderWith(app, "今日はいい天気ですね。");
  app.click(firstCard(app));
  return flushPromises().then(function () {
    app.click(app.$("read-open-assistant"));
    assert.equal(app.$("read-assistant-sheet").hidden, false);
    app.$("read-assistant-input").value = "这句话什么意思？";
    app.click(app.$("read-assistant-send"));
    return flushPromises();
  }).then(function () {
    const assistantCalls = calls.filter(function (c) { return c.kind === "assistant"; });
    assert.equal(assistantCalls.length, 1, "一次提问一次助手请求");
    const request = assistantCalls[0];
    const userContent = request.body.messages[1].content;
    assert.ok(userContent.indexOf("【当前段落】") >= 0, "首轮应注入段落上下文");
    assert.ok(userContent.indexOf("今日はいい天気ですね。") >= 0, "上下文含原文");
    assert.ok(userContent.indexOf("这句话什么意思？") >= 0, "含用户提问");
    const bubbles = app.$("read-assistant-log").querySelectorAll(".read-assistant-bubble");
    const texts = Array.prototype.map.call(bubbles, function (b) { return b.textContent; });
    assert.ok(texts.indexOf("这句话什么意思？") >= 0, "应显示用户气泡");
    assert.ok(texts.indexOf("这是助手的回复。") >= 0, "应回显助手回复");
    assert.equal(app.$("read-assistant-input").value, "", "发送后清空输入");
  });
});

test("文章阅读 B8：助手消息仅内存不落盘", function () {
  const app = assistantApp(true);
  installReadFetch(app);
  openReaderWith(app, "今日はいい天気ですね。");
  app.click(firstCard(app));
  return flushPromises().then(function () {
    const writes = [];
    const originalSet = app.window.localStorage.setItem;
    app.window.localStorage.setItem = function (key, value) {
      writes.push(String(key));
      return originalSet.call(this, key, value);
    };
    app.window.localStorage.setItem("probe-spy", "1");
    assert.equal(writes.length, 1, "写入记账桩在位");
    app.click(app.$("read-open-assistant"));
    app.$("read-assistant-input").value = "问题一";
    app.click(app.$("read-assistant-send"));
    return flushPromises().then(function () {
      assert.ok(!writes.some(function (k) { return /assistant|助手/i.test(k); }),
        "localStorage 不应有助手消息键：" + writes.join(","));
    });
  });
});

test("静态守卫：生产代码禁用 dom-stub 专属 API（dispatch），只走标准 dispatchEvent", function () {
  const productionFiles = ["app.js", "read-view.js", "read-client.js", "read-engine.js",
    "read-prompts.js", "read-stats.js", "read-tts.js", "read-ocr.js"];
  productionFiles.forEach(function (name) {
    const source = fs.readFileSync(path.join(__dirname, "..", name), "utf8");
    const hits = source.match(/\.dispatch\(/g) || [];
    assert.equal(hits.length, 0,
      name + " 含 dom-stub 专属 .dispatch() 调用（真浏览器不存在该 API，应改用 dispatchEvent）");
  });
});

// ---------------- 敬语子页 ----------------

// 从页面当前展示反推敬语答案：题干动词 + 目标 pill（敬语类别）。
function currentHonorificAnswer(app) {
  const word = app.$("honorific-question-word").textContent;
  const verb = engine.VERBS.filter(function (item) {
    return item.dictionary === word;
  })[0];
  assert.ok(verb, "敬语题目动词应存在于动词表：" + word);

  const label = app.$("honorific-level-pill").textContent;
  const level = honorificEngine.LEVELS.filter(function (item) {
    return item.label === label;
  })[0];
  assert.ok(level, "敬语类别标签应可解析：" + label);

  const question = honorificEngine.buildQuestion(verb, level.id);
  assert.ok(question, "该动词在此敬语类别下应可出题");
  return question.display;
}

test("敬语：侧栏入口打开动词模块的敬语子页并渲染三层对照", function () {
  const app = createApp();
  tab(app, "honorific");

  assert.equal(app.$("view-verb").hidden, false);
  assert.equal(app.$("view-honorific").hidden, false);
  assert.equal(app.$("view-practice").hidden, true);
  assert.equal(app.$("view-reference").hidden, true);

  app.$("honorific-verb").value = "kaku";
  app.$("honorific-verb").dispatch("change");
  const rows = app.$("honorific-table-body").childNodes;
  assert.equal(rows.length, 3, "三层敬语应各占一行");
  assert.match(rows[0].textContent, /尊敬語/);
  assert.match(rows[0].textContent, /お書きになります/);
  assert.match(rows[1].textContent, /お書きします/);
  assert.match(rows[2].textContent, /書きます/);

  // 切回练习子页后敬语视图隐藏
  tab(app, "practice");
  assert.equal(app.$("view-honorific").hidden, true);
  assert.equal(app.$("view-practice").hidden, false);
});

test("敬语：对照表标出特殊说法与不适用层", function () {
  const app = createApp();
  tab(app, "honorific");

  app.$("honorific-verb").value = "iku";
  app.$("honorific-verb").dispatch("change");
  const ikuRows = app.$("honorific-table-body").childNodes;
  assert.match(ikuRows[0].textContent, /いらっしゃいます/);
  assert.match(ikuRows[0].textContent, /特殊/);

  app.$("honorific-verb").value = "furu";
  app.$("honorific-verb").dispatch("change");
  const furuRows = app.$("honorific-table-body").childNodes;
  assert.match(furuRows[0].textContent, /不适用/);
  assert.match(furuRows[0].textContent, /自然现象|天气/);
  assert.match(furuRows[2].textContent, /降ります/);
});

test("敬语：答对显示反馈并计入每日统计，答错在下一题重现", function () {
  const app = createApp();
  tab(app, "honorific");

  app.$("honorific-answer-input").value = currentHonorificAnswer(app);
  app.$("honorific-answer-form").dispatch("submit");
  assert.equal(app.$("honorific-feedback").hidden, false);
  assert.match(app.$("honorific-feedback-title").textContent, /答对/);
  assert.ok(app.$("honorific-feedback-answer").textContent.length > 0);
  assert.ok(app.$("honorific-feedback-rule").textContent.length > 0);

  const stored = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(stored.honorificAnswers, 1);

  app.click(app.$("honorific-next"));
  assert.equal(app.$("honorific-feedback").hidden, true);

  const wrongWord = app.$("honorific-question-word").textContent;
  app.$("honorific-answer-input").value = "ちがう";
  app.$("honorific-answer-form").dispatch("submit");
  assert.match(app.$("honorific-feedback-title").textContent, /没答对/);
  assert.equal(JSON.parse(app.window.localStorage.getItem(DAILY_KEY)).honorificAnswers, 2);

  app.click(app.$("honorific-next"));
  assert.equal(app.$("honorific-question-word").textContent, wrongWord, "答错的题应在下一题重现");
});

test("敬语：二重敬语检查给出纠正，正确说法不误报", function () {
  const app = createApp();
  tab(app, "honorific");

  const input = app.$("honorific-check-input");
  input.value = "おっしゃられました";
  input.dispatch("input");
  assert.match(app.$("honorific-check-result").textContent, /おっしゃる/);
  assert.equal(app.$("honorific-check-result").classList.contains("is-warning"), true);

  input.value = "おっしゃいました";
  input.dispatch("input");
  assert.match(app.$("honorific-check-result").textContent, /没有检测到/);
  assert.equal(app.$("honorific-check-result").classList.contains("is-ok"), true);
});

test("敬语：诊断器按主体与内外关系给出敬语类别", function () {
  const app = createApp();
  tab(app, "honorific");

  const subject = app.$("honorific-subject");
  subject.value = "self";
  subject.dispatch("change");
  assert.match(app.$("honorific-diagnose-result").textContent, /謙譲語/);

  subject.value = "other";
  subject.dispatch("change");
  assert.match(app.$("honorific-diagnose-result").textContent, /尊敬語/);

  subject.value = "third";
  subject.dispatch("change");
  assert.match(app.$("honorific-diagnose-result").textContent, /尊敬語/);

  app.$("honorific-in-group").checked = true;
  app.$("honorific-in-group").dispatch("change");
  assert.match(app.$("honorific-diagnose-result").textContent, /謙譲語/);
  assert.match(app.$("honorific-diagnose-result").textContent, /己方/);
});

test("敬语：场景筛选切换例句列表", function () {
  const app = createApp();
  tab(app, "honorific");

  assert.equal(app.$("honorific-example-list").querySelectorAll(".honorific-example").length, 24);

  const buttons = app.$("honorific-scene-filter").querySelectorAll(".honorific-scene-button");
  assert.equal(buttons.length, 7, "全部 + 六个场景");
  app.click(buttons[1]);
  assert.equal(app.$("honorific-example-list").querySelectorAll(".honorific-example").length, 4);
});
