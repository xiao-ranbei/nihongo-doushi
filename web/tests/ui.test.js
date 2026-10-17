"use strict";

// UI 冒烟测试：用最小 DOM 桩在 node 里原样加载 app.js，覆盖真实交互路径。
// 重点不是像素，而是「DOM 接线没断」——任何一个 getElementById 拿到不存在的
// id、任何一次状态迁移没刷新对应节点，都会在这里失败。

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const engine = require("../verb-engine.js");
const adjEngine = require("../adjective-engine.js");
const particleEngine = require("../particle-engine.js");
const { createApp } = require("./helpers/dom-stub.js");

const MISTAKES_KEY = "japanese-verb-practice-mistakes-v1";
const STATS_KEY = "japanese-verb-practice-stats-v1";
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
  if (viewId === "practice" || viewId === "reference") {
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
  assert.equal(app.document.querySelectorAll(".module-subtab").length, 7);
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
  assert.equal(app.$("home-today-breakdown").textContent, "动词 0 题 · 形容词 0 题 · 助词 0 题");
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
  assert.equal(app.$("home-today-breakdown").textContent, "动词 1 题 · 形容词 1 题 · 助词 0 题");
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
