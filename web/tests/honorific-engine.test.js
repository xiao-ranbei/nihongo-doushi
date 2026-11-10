"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const engine = require("../honorific-engine.js");
const verbEngine = require("../verb-engine.js");

const VERB_BY_ID = new Map(verbEngine.VERBS.map(function (verb) {
  return [verb.id, verb];
}));

function verb(id) {
  const found = VERB_BY_ID.get(id);
  assert.ok(found, "词库缺少动词：" + id);
  return found;
}

function formsOf(id, levelId) {
  return engine.getHonorificForms(verb(id), levelId);
}

test("三层敬语定义完整且各有构成规则与说明", function () {
  assert.deepEqual(
    engine.LEVELS.map(function (level) { return level.id; }),
    ["sonkeigo", "kenjougo", "teineigo"]
  );
  engine.LEVELS.forEach(function (level) {
    assert.ok(level.label.length, level.id);
    assert.ok(level.pattern.length, level.id);
    assert.ok(level.meaning.length, level.id);
    assert.ok(level.question.length, level.id);
    assert.ok(engine.explainLevel(level.id).length > 10, level.id);
  });
  assert.throws(function () { engine.explainLevel("unknown"); }, RangeError);
});

test("规则形按动词分组派生：五段、一段、サ変", function () {
  assert.equal(formsOf("kaku", "sonkeigo").primary.masu, "お書きになります");
  assert.equal(formsOf("kaku", "kenjougo").primary.masu, "お書きします");
  assert.equal(formsOf("kaku", "teineigo").primary.masu, "書きます");

  assert.equal(formsOf("taberu", "teineigo").primary.masu, "食べます");
  assert.equal(formsOf("motsu", "sonkeigo").primary.masu, "お持ちになります");
  assert.equal(formsOf("kasu", "kenjougo").primary.masu, "お貸しします");

  assert.equal(formsOf("setsumei-suru", "sonkeigo").primary.masu, "ご説明になります");
  assert.equal(formsOf("setsumei-suru", "kenjougo").primary.masu, "ご説明します");
  assert.equal(formsOf("setsumei-suru", "teineigo").primary.masu, "説明します");

  // 謙譲語的「いたす」版作为可接受答案的一档
  const kakuKenjougo = formsOf("kaku", "kenjougo");
  assert.equal(kakuKenjougo.alternatives.length, 1);
  assert.equal(kakuKenjougo.alternatives[0].masu, "お書きいたします");
});

test("特殊敬语动词表覆盖 15 个词且优先于规则形", function () {
  const cases = [
    ["iku", "sonkeigo", "いらっしゃいます"],
    ["iku", "kenjougo", "参ります"],
    ["kuru", "sonkeigo", "いらっしゃいます"],
    ["kuru", "kenjougo", "参ります"],
    ["iru", "sonkeigo", "いらっしゃいます"],
    ["iru", "kenjougo", "おります"],
    ["taberu", "sonkeigo", "召し上がります"],
    ["taberu", "kenjougo", "いただきます"],
    ["nomu", "sonkeigo", "召し上がります"],
    ["miru", "sonkeigo", "ご覧になります"],
    ["miru", "kenjougo", "拝見します"],
    ["iu", "sonkeigo", "おっしゃいます"],
    ["iu", "kenjougo", "申します"],
    ["suru", "sonkeigo", "なさいます"],
    ["suru", "kenjougo", "いたします"],
    ["shiru", "sonkeigo", "ご存じです"],
    ["morau", "kenjougo", "いただきます"],
    ["ageru", "kenjougo", "差し上げます"],
    ["kureru", "sonkeigo", "くださいます"],
    ["kiku", "kenjougo", "伺います"],
    ["au", "kenjougo", "お目にかかります"],
    ["shinu", "sonkeigo", "お亡くなりになります"]
  ];

  cases.forEach(function (item) {
    const forms = formsOf(item[0], item[1]);
    assert.equal(forms.available, true, item[0] + " / " + item[1]);
    assert.equal(forms.isSpecial, true, item[0] + " / " + item[1]);
    assert.equal(forms.primary.masu, item[2], item[0] + " / " + item[1]);
    assert.equal(forms.ruleForm, null, "特殊形不应生成规则形：" + item[0]);
  });

  assert.equal(Object.keys(engine.SPECIAL_HONORIFIC).length, 15);
});

test("没有对应说法的层级返回不可用并说明原因", function () {
  const ageru = formsOf("ageru", "sonkeigo");
  assert.equal(ageru.available, false);
  assert.ok(ageru.reason.length > 10);

  const kureru = formsOf("kureru", "kenjougo");
  assert.equal(kureru.available, false);
  assert.ok(kureru.reason.length > 10);

  const shinu = formsOf("shinu", "kenjougo");
  assert.equal(shinu.available, false);
});

test("无意志动词只出丁寧語", function () {
  const limited = ["furu", "saku", "wakaru", "mitsukaru", "hajimaru", "owaru", "aku", "shimaru"];
  assert.deepEqual(Object.keys(engine.LIMITED_VERBS).sort(), limited.slice().sort());

  limited.forEach(function (id) {
    ["sonkeigo", "kenjougo"].forEach(function (levelId) {
      const forms = formsOf(id, levelId);
      assert.equal(forms.available, false, id + " / " + levelId);
      assert.equal(forms.primary, null);
      assert.ok(forms.reason.length > 8, id + " / " + levelId);
    });
    assert.equal(formsOf(id, "teineigo").available, true, id + " / teineigo");
  });

  // 着く 的「お着きになる」是成立说法，不应被限制
  assert.equal(engine.isLimited("tsuku"), false);
  assert.equal(formsOf("tsuku", "sonkeigo").available, true);
});

test("全部动词 × 三层都能给出明确结论且无异常", function () {
  let available = 0;
  let unavailable = 0;
  for (const item of verbEngine.VERBS) {
    for (const level of engine.LEVELS) {
      const forms = engine.getHonorificForms(item, level.id);
      if (forms.available) {
        available += 1;
        assert.ok(forms.primary.masu.length, item.id + " / " + level.id);
        assert.ok(forms.primary.masuKana.length, item.id + " / " + level.id);
        assert.ok(forms.primary.written.length, item.id + " / " + level.id);
      } else {
        unavailable += 1;
        assert.equal(forms.primary, null);
        assert.ok(forms.reason.length > 8, item.id + " / " + level.id + " 缺少不可用原因");
      }
    }
  }
  assert.ok(available > 250, "可用组合过少：" + available);
  assert.ok(unavailable > 0, "应存在不可用组合");
});

test("练习题包含判定答案、可接受变体与规则说明", function () {
  const question = engine.buildQuestion(verb("kaku"), "sonkeigo");
  assert.equal(question.key, "hon::kaku::sonkeigo");
  assert.equal(question.levelLabel, "尊敬語");
  assert.equal(question.display, "お書きになります");
  assert.ok(question.prompt.indexOf("書く") >= 0);
  assert.ok(question.explanation.indexOf("お + ます词干 + になる") >= 0);
  assert.ok(!question.isSpecial);

  const specialQuestion = engine.buildQuestion(verb("iku"), "kenjougo");
  assert.ok(specialQuestion.isSpecial);
  assert.ok(specialQuestion.explanation.indexOf("特殊说法") >= 0);
  assert.deepEqual(specialQuestion.answers, ["参ります", "まいります", "伺います", "うかがいます"]);

  assert.equal(engine.buildQuestion(verb("furu"), "sonkeigo"), null);
});

test("判题接受汉字、假名、罗马字与同义特殊形，拒绝只给辞书形", function () {
  const question = engine.buildQuestion(verb("kaku"), "sonkeigo");
  assert.equal(engine.checkAnswer(question, "お書きになります").correct, true);
  assert.equal(engine.checkAnswer(question, "おかきになります").correct, true);
  assert.equal(engine.checkAnswer(question, "okakininarimasu").correct, true);
  assert.equal(engine.checkAnswer(question, "お書きになる").correct, false);
  assert.equal(engine.checkAnswer(question, "書きます").correct, false);
  assert.equal(engine.checkAnswer(question, "").correct, false);

  const iku = engine.buildQuestion(verb("iku"), "sonkeigo");
  assert.equal(engine.checkAnswer(iku, "いらっしゃいます").correct, true);
  assert.equal(engine.checkAnswer(iku, "おいでになります").correct, true);
  assert.equal(engine.checkAnswer(iku, "お行きになります").correct, false);
});

test("二重敬语检测命中常见错误", function () {
  const cases = [
    ["おっしゃられました", "おっしゃる"],
    ["先生がご覧になられました", "ご覧になる"],
    ["お召し上がりになられますか", "召し上がる"],
    ["なさられましたね", "なさる"],
    ["申し上げられません", "申し上げる"]
  ];
  cases.forEach(function (item) {
    const result = engine.checkDoubleHonorific(item[0]);
    assert.equal(result.ok, false, item[0]);
    assert.ok(result.issues.length >= 1, item[0]);
    assert.equal(result.issues[0].suggestion, item[1], item[0]);
    assert.equal(result.issues[0].severity, "error", item[0]);
  });
});

test("二重敬语检测不误报正确说法", function () {
  const correct = [
    "おっしゃいました",
    "ご覧になりましたか",
    "召し上がってください",
    "なさいますか",
    "申し上げます",
    "お書きになります",
    "ご説明いたします"
  ];
  correct.forEach(function (text) {
    assert.equal(engine.checkDoubleHonorific(text).ok, true, text);
  });
  assert.equal(engine.checkDoubleHonorific("").ok, true);
});

test("场景判定按主体与人际关系给出敬语层", function () {
  assert.equal(engine.judgeLevel({ subject: "self" }).levelId, "kenjougo");
  assert.equal(engine.judgeLevel({ subject: "other" }).levelId, "sonkeigo");
  assert.equal(engine.judgeLevel({ subject: "third" }).levelId, "sonkeigo");
  assert.equal(engine.judgeLevel({ subject: "third", inGroup: true }).levelId, "kenjougo");
  assert.equal(engine.judgeLevel({}).levelId, "teineigo");

  const inGroup = engine.judgeLevel({ subject: "third", inGroup: true });
  assert.ok(inGroup.reason.indexOf("己方") >= 0);
});

test("六个场景各有四条例句且场次与层级合法", function () {
  assert.equal(engine.SCENES.length, 6);
  assert.equal(engine.EXAMPLES.length, 24);

  const levelIds = new Set(engine.LEVELS.map(function (level) { return level.id; }));
  const sceneIds = new Set(engine.SCENES.map(function (scene) { return scene.id; }));

  engine.SCENES.forEach(function (scene) {
    assert.equal(engine.getExamplesByScene(scene.id).length, 4, scene.id);
  });

  engine.EXAMPLES.forEach(function (example) {
    assert.ok(sceneIds.has(example.sceneId), example.id);
    assert.ok(levelIds.has(example.levelId), example.id);
    assert.ok(example.jp.length && example.kana.length && example.zh.length, example.id);
    assert.ok(example.who.length, example.id);
    if (example.verbId) {
      assert.ok(VERB_BY_ID.has(example.verbId), example.id + " 引用了不存在的动词：" + example.verbId);
    }
  });

  assert.equal(engine.getExamplesByScene("all").length, 24);
});
