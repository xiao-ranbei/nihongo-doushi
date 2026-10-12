"use strict";

// UI 冒烟测试：用最小 DOM 桩在 node 里原样加载 app.js，覆盖真实交互路径。
// 重点不是像素，而是「DOM 接线没断」——任何一个 getElementById 拿到不存在的
// id、任何一次状态迁移没刷新对应节点，都会在这里失败。

const assert = require("node:assert/strict");
const test = require("node:test");

const engine = require("../verb-engine.js");
const { createApp } = require("./helpers/dom-stub.js");

const MISTAKES_KEY = "japanese-verb-practice-mistakes-v1";
const STATS_KEY = "japanese-verb-practice-stats-v1";

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

function inflectionLabelOf(app) {
  const parts = app.$("target-pill").textContent.split(" · ");
  return parts[parts.length - 1];
}

function tab(app, viewId) {
  const found = app.document.querySelectorAll("[data-view]").filter(function (node) {
    return node.dataset.view === viewId;
  })[0];
  assert.ok(found, "应存在 data-view=" + viewId + " 的标签页");
  app.click(found);
  return found;
}

test("初始化：注入字体、渲染首题、只显示练习视图", function () {
  const app = createApp();
  const injected = app.document.head.querySelectorAll("style").filter(function (node) {
    return /@font-face/.test(node.textContent);
  });
  assert.equal(injected.length, 1, "应根据字体清单注入一条 @font-face 样式");

  assert.match(app.$("question-word").textContent, /\S/, "首题应渲染出动词");
  assert.equal(app.$("view-practice").hidden, false);
  assert.equal(app.$("view-reference").hidden, true);
  assert.equal(app.$("view-mistakes").hidden, true);
  assert.equal(app.$("review-banner").hidden, true);
  assert.equal(app.$("feedback-panel").hidden, true);
  assert.equal(app.$("question-index").textContent, "随机练习");
});

test("答对：显示 ✓、统计 +1、不记入错题", function () {
  const app = createApp();
  app.answer(currentAnswer(app));

  assert.equal(app.$("feedback-panel").hidden, false);
  assert.equal(app.$("feedback-mark").textContent, "✓");
  assert.equal(app.$("feedback-title").textContent, "答对了");
  assert.equal(app.$("correct-answer-count").textContent, "1");
  assert.equal(app.$("incorrect-answer-count").textContent, "0");
  assert.equal(app.$("mistake-count").textContent, "0");
  assert.equal(app.$("answer-input").disabled, true, "作答后输入框应锁定");
  assert.equal(app.$("next-question").hidden, false);
});

test("答错：记入错题、渲染卡片、写入本地存储", function () {
  const app = createApp();
  app.answer("ぜったいにまちがい");

  assert.equal(app.$("feedback-mark").textContent, "!");
  assert.equal(app.$("feedback-panel").classList.contains("is-wrong"), true);
  assert.equal(app.$("incorrect-answer-count").textContent, "1");
  assert.equal(app.$("mistake-count").textContent, "1");

  tab(app, "mistakes");
  assert.equal(app.$("view-mistakes").hidden, false);
  assert.equal(app.$("mistakes-list").querySelectorAll(".mistake-card").length, 1);

  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY));
  assert.equal(stored.length, 1);
  assert.equal(stored[0].count, 1);
  assert.equal(stored[0].wrongAnswer, "ぜったいにまちがい");
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

test("速查表「练这题」切到练习并载入对应题目", function () {
  const app = createApp();
  tab(app, "reference");

  const rows = app.$("reference-table-body").childNodes;
  assert.equal(rows.length, engine.INFLECTIONS.length);
  const buttons = app.$("reference-table-body").querySelectorAll(".practice-link");
  assert.equal(buttons.length, engine.INFLECTIONS.length, "每行都应有一个「练这题」");

  const rowLabel = rows[3].childNodes[0].textContent;
  app.click(buttons[3]);

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
  assert.match(app.$("review-banner-count").textContent, /剩余 1 题/);
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

test("重练答对后移出错题，全部答完自动回错题页", function () {
  const app = createApp();
  app.answer("まちがいです");
  app.click(app.$("review-mistakes"));
  app.answer(currentAnswer(app));

  assert.equal(app.$("mistake-count").textContent, "0", "答对应从错题集移出");
  assert.match(app.$("review-banner-count").textContent, /剩余 0 题/);

  app.click(app.$("next-question"));
  assert.equal(app.$("view-mistakes").hidden, false, "错题清空后应回到错题页");
  assert.equal(app.$("review-banner").hidden, true, "离开重练后横幅应收起");
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
  const card = app.$("mistakes-list").querySelectorAll(".mistake-card")[0];
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
