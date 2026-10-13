"use strict";

// UI 冒烟测试：用最小 DOM 桩在 node 里原样加载 app.js，覆盖真实交互路径。
// 重点不是像素，而是「DOM 接线没断」——任何一个 getElementById 拿到不存在的
// id、任何一次状态迁移没刷新对应节点，都会在这里失败。

const assert = require("node:assert/strict");
const test = require("node:test");

const engine = require("../verb-engine.js");
const adjEngine = require("../adjective-engine.js");
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
  assert.equal(app.$("question-group").textContent, "动词");
  assert.equal(app.$("question-reading").textContent, "假名");
  assert.equal(app.document.querySelector(".verb-prompt").getAttribute("aria-expanded"), "false");
  assert.equal(app.document.querySelector(".practice-stats").classList.contains("is-answer-correct"), false);
  assert.equal(app.document.querySelector(".practice-stats").classList.contains("is-answer-incorrect"), false);
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
  assert.equal(app.document.querySelector(".practice-stats").classList.contains("is-answer-correct"), false);
  assert.equal(app.document.querySelector(".practice-stats").classList.contains("is-answer-incorrect"), false);
});

test("答对：显示 ✓、统计 +1、不记入错题", function () {
  const app = createApp();
  app.answer(currentAnswer(app));
  const statsCard = app.document.querySelector(".practice-stats");

  assert.equal(app.$("feedback-panel").hidden, false);
  assert.equal(app.$("feedback-mark").textContent, "✓");
  assert.equal(app.$("feedback-title").textContent, "答对了");
  assert.equal(app.$("correct-answer-count").textContent, "1");
  assert.equal(app.$("incorrect-answer-count").textContent, "0");
  assert.equal(app.$("mistake-count").textContent, "0");
  assert.equal(app.$("answer-input").disabled, true, "作答后输入框应锁定");
  assert.equal(app.$("next-question").hidden, false);
  assert.equal(app.document.querySelector(".verb-prompt").getAttribute("aria-expanded"), "true");
  assert.equal(statsCard.classList.contains("is-answer-correct"), true);
  assert.equal(statsCard.classList.contains("is-answer-incorrect"), false);
});

test("答错：记入错题、渲染卡片、写入本地存储", function () {
  const app = createApp();
  app.answer("ぜったいにまちがい");
  const statsCard = app.document.querySelector(".practice-stats");

  assert.equal(app.$("feedback-mark").textContent, "!");
  assert.equal(app.$("feedback-panel").classList.contains("is-wrong"), true);
  assert.equal(app.$("incorrect-answer-count").textContent, "1");
  assert.equal(app.$("mistake-count").textContent, "1");
  assert.equal(app.document.querySelector(".verb-prompt").getAttribute("aria-expanded"), "true");
  assert.equal(statsCard.classList.contains("is-answer-correct"), false);
  assert.equal(statsCard.classList.contains("is-answer-incorrect"), true);

  tab(app, "mistakes");
  assert.equal(app.$("view-mistakes").hidden, false);
  assert.equal(app.$("mistakes-list").querySelectorAll(".mistake-card").length, 1);

  const stored = JSON.parse(app.window.localStorage.getItem(MISTAKES_KEY));
  assert.equal(stored.length, 1);
  assert.equal(stored[0].count, 1);
  assert.equal(stored[0].wrongAnswer, "ぜったいにまちがい");
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

test("速查页展示当前动词的全部变形类型规则与示例", function () {
  const app = createApp();
  tab(app, "reference");

  const list = app.$("reference-voice-guide-list");
  let items = list.querySelectorAll("[data-voice-id]");
  assert.equal(items.length, engine.VOICES.length);

  const potentialItem = items.filter(function (item) {
    return item.dataset.voiceId === "potential";
  })[0];
  assert.match(potentialItem.textContent, /行く → 行ける/);
  assert.match(potentialItem.textContent, /五段动词词尾改为对应え段/);

  const voiceSelect = app.$("reference-voice");
  voiceSelect.value = "causative";
  voiceSelect.dispatch("change");
  items = list.querySelectorAll("[data-voice-id]");
  const selectedItem = items.filter(function (item) {
    return item.dataset.voiceId === "causative";
  })[0];
  assert.equal(selectedItem.classList.contains("is-selected"), true);

  const verbSelect = app.$("reference-verb");
  verbSelect.value = "taberu";
  verbSelect.dispatch("change");
  assert.match(app.$("reference-voice-guide-caption").textContent, /一段动词/);
  items = list.querySelectorAll("[data-voice-id]");
  const ichidanPotentialItem = items.filter(function (item) {
    return item.dataset.voiceId === "potential";
  })[0];
  assert.match(ichidanPotentialItem.textContent, /食べる → 食べられる/);
  assert.match(ichidanPotentialItem.textContent, /一段动词去掉「る」/);
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
  assert.equal(app.$("view-practice").hidden, true);
  assert.match(app.$("intro-title").textContent, /看见形容词，想起接续。/);
  assert.match(app.$("intro-eyebrow").textContent, /先分い・な/);

  tab(app, "practice");
  assert.match(app.$("intro-title").textContent, /看见动词，想起变化。/, "切回应恢复动词标语");
});

test("形容词：答对计入独立统计且不进错题本", function () {
  const app = createApp();
  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = currentAdjectiveAnswer(app);
  app.document.getElementById("adj-answer-form").dispatch("submit");

  const statsCard = app.$("adj-practice-stats");
  assert.equal(app.$("adj-feedback-panel").hidden, false);
  assert.equal(app.$("adj-feedback-mark").textContent, "✓");
  assert.equal(app.$("adj-correct-count").textContent, "1");
  assert.equal(app.$("adj-incorrect-count").textContent, "0");
  assert.equal(app.$("mistake-count").textContent, "0", "形容词答错答对都不应写入动词错题本");
  assert.equal(app.$("adj-answer-input").disabled, true, "作答后输入框应锁定");
  assert.equal(app.$("adj-next-question").hidden, false);
  assert.equal(statsCard.classList.contains("is-answer-correct"), true);
  assert.equal(statsCard.classList.contains("is-answer-incorrect"), false);
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
  const card = app.$("mistakes-list").querySelectorAll(".mistake-card")[0];
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
  // 每张错题卡的按钮顺序：[0] 练这题、[1] 移除（dom-stub 不支持后代选择器，用标签选择）
  app.click(app.$("mistakes-list").querySelectorAll("button")[0]);

  assert.equal(app.$("view-adjective").hidden, false);
  assert.equal(app.$("adj-review-banner").hidden, false, "单题重练应进入重练模式");
  assert.equal(app.$("adj-question-word").textContent, "静か");
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

const DAILY_KEY = "japanese-verb-practice-daily-v1";

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

  tab(app, "adjective");
  app.document.getElementById("adj-answer-input").value = currentAdjectiveAnswer(app);
  app.document.getElementById("adj-answer-form").dispatch("submit");
  const stored = JSON.parse(app.window.localStorage.getItem(DAILY_KEY));
  assert.equal(stored.streak, 1, "同一天只记一次");
  assert.equal(stored.lastDate, localDateKey(0));
});

test("打卡：存档损坏降级为从零开始", function () {
  const app = createApp({ storage: { [DAILY_KEY]: "{ 不是合法 JSON" } });
  assert.match(app.$("toast").textContent, /打卡记录无法解析/);
  app.answer(currentAnswer(app));
  assert.match(app.$("streak-note-text").textContent, /连续练习 1 天 · 最佳 1 天/);
});
