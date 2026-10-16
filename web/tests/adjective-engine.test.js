"use strict";

// 形容词引擎测试：全部变形结果的 written/kana 断言 + 特例（いい、きれい）
// + explain 文本 + 错误输入防护。判定集合（alts）也在覆盖范围内。

const assert = require("node:assert/strict");
const test = require("node:test");

const adjEngine = require("../adjective-engine.js");
const verbEngine = require("../verb-engine.js");

const FORM_IDS = adjEngine.FORMS.map(function (form) { return form.id; });

function byWord(word) {
  const found = adjEngine.ADJECTIVES.filter(function (item) {
    return item.word === word;
  })[0];
  assert.ok(found, "形容词应存在于词库：" + word);
  return found;
}

function assertShape(result) {
  assert.ok(result && typeof result.written === "string" && result.written.length > 0);
  assert.ok(typeof result.kana === "string" && result.kana.length > 0);
  assert.ok(Array.isArray(result.alts));
  result.alts.forEach(function (alt) {
    assert.ok(alt.written.length > 0 && alt.kana.length > 0);
  });
}

test("全词库 × 全形式冒烟：written/kana 恒非空，alts 结构合法", function () {
  adjEngine.ADJECTIVES.forEach(function (adjective) {
    FORM_IDS.forEach(function (formId) {
      assertShape(adjEngine.conjugate(adjective, formId));
    });
  });
});

test("い形容词「高い」的常用形式与六形专项形式", function () {
  const takai = byWord("高い");
  const expected = {
    attributive: "高い",
    present: "高い",
    presentNegative: "高くない",
    past: "高かった",
    pastNegative: "高くなかった",
    te: "高くて",
    conditional: "高ければ",
    adverbial: "高く",
    mizen: "高かろう",
    imperativeRequest: "高くしてください"
  };
  FORM_IDS.forEach(function (formId) {
    const result = adjEngine.conjugate(takai, formId);
    assert.equal(result.written, expected[formId]);
    assert.equal(result.kana, expected[formId].replace("高", "たか"));
    assert.deepEqual(result.alts, []);
  });
});

test("な形容词「静か」的常用形式与六形专项形式", function () {
  const shizuka = byWord("静か");
  const main = {
    attributive: "静かな",
    present: "静かだ",
    presentNegative: "静かじゃない",
    past: "静かだった",
    pastNegative: "静かではなかった",
    te: "静かで",
    conditional: "静かなら",
    adverbial: "静かに",
    mizen: "静かだろう",
    imperativeRequest: "静かにしてください"
  };
  FORM_IDS.forEach(function (formId) {
    const result = adjEngine.conjugate(shizuka, formId);
    assert.equal(result.written, main[formId]);
    assert.equal(result.kana, main[formId].replace(/静か/g, "しずか"));
  });

  const present = adjEngine.conjugate(shizuka, "present");
  assert.deepEqual(present.alts.map(function (alt) { return alt.written; }), ["静かです"]);

  const negative = adjEngine.conjugate(shizuka, "presentNegative");
  assert.ok(negative.alts.some(function (alt) { return alt.written === "静かではない"; }));
  assert.ok(negative.alts.some(function (alt) { return alt.written === "静かじゃありません"; }));
  assert.ok(negative.alts.some(function (alt) { return alt.written === "静かではありません"; }));

  const past = adjEngine.conjugate(shizuka, "past");
  assert.deepEqual(past.alts.map(function (alt) { return alt.written; }), ["静かでした"]);

  const pastNegative = adjEngine.conjugate(shizuka, "pastNegative");
  assert.ok(pastNegative.alts.some(function (alt) { return alt.written === "静かじゃなかった"; }));
  assert.ok(pastNegative.alts.some(function (alt) { return alt.written === "静かではありませんでした"; }));

  const conditional = adjEngine.conjugate(shizuka, "conditional");
  assert.deepEqual(conditional.alts.map(function (alt) { return alt.written; }), ["静かならば"]);
});

test("特例「いい」全部换成よく系列", function () {
  const ii = byWord("いい");
  const expected = {
    attributive: "いい",
    present: "いい",
    presentNegative: "よくない",
    past: "よかった",
    pastNegative: "よくなかった",
    te: "よくて",
    conditional: "よければ",
    adverbial: "よく",
    mizen: "よかろう",
    imperativeRequest: "よくしてください"
  };
  FORM_IDS.forEach(function (formId) {
    const result = adjEngine.conjugate(ii, formId);
    assert.equal(result.written, expected[formId], formId);
    assert.equal(result.kana, expected[formId]);
  });
});

test("「きれい」是な形容词：否定不接くない", function () {
  const kirei = byWord("きれい");
  assert.equal(kirei.kind, "na");
  const negative = adjEngine.conjugate(kirei, "presentNegative");
  assert.equal(negative.written, "きれいじゃない");
  assert.equal(negative.written.includes("くない"), false);
});

test("变形结果与罗马字归一化互通（复用 verb-engine 管线）", function () {
  const shizuka = byWord("静か");
  const negative = adjEngine.conjugate(shizuka, "presentNegative");
  assert.equal(
    verbEngine.normalizeAnswer("shizukajyanai"),
    verbEngine.normalizeAnswer(negative.kana)
  );
  assert.equal(
    verbEngine.normalizeAnswer("takanakatta"),
    verbEngine.normalizeAnswer("たかなかった")
  );
});

test("explain 文本包含目标形式与变化提示", function () {
  const takai = byWord("高い");
  assert.match(adjEngine.explain(takai, "pastNegative"), /变「过去否定」/);
  assert.match(adjEngine.explain(takai, "pastNegative"), /「くなかった」/);

  const ii = byWord("いい");
  assert.match(adjEngine.explain(ii, "past"), /不参与变形/);
  assert.match(adjEngine.explain(ii, "past"), /「よかった」/);

  const shizuka = byWord("静か");
  assert.match(adjEngine.explain(shizuka, "attributive"), /「な」/);
  assert.match(adjEngine.explain(shizuka, "adverbial"), /「に」/);
  assert.match(adjEngine.explain(shizuka, "conditional"), /仮定形/);
  assert.match(adjEngine.explain(shizuka, "mizen"), /未然形/);
  assert.match(adjEngine.explain(shizuka, "imperativeRequest"), /没有独立命令形/);

  assert.match(adjEngine.explain(ii, "conditional"), /「よければ」/);
  assert.match(adjEngine.explain(ii, "mizen"), /「よかろう」/);
});

test("未知形式与非法输入按类型报错", function () {
  const takai = byWord("高い");
  assert.throws(function () { adjEngine.conjugate(takai, "not-a-form"); }, RangeError);
  assert.throws(function () { adjEngine.conjugate(null, "past"); }, TypeError);
  assert.throws(function () {
    adjEngine.conjugate({ word: "静か", kana: "しずか", kind: "i" }, "past");
  }, TypeError);
  assert.throws(function () { adjEngine.explain(takai, "not-a-form"); }, RangeError);
});

test("判定集合覆盖罗马字输入的所有礼貌变体", function () {
  const shizuka = byWord("静か");
  const pastNegative = adjEngine.conjugate(shizuka, "pastNegative");
  const expected = new Set(adjEngine.answerVariants(pastNegative).map(function (text) {
    return verbEngine.normalizeAnswer(text);
  }));
  assert.ok(expected.has(verbEngine.normalizeAnswer("shizukadewanakatta")));
  assert.ok(expected.has(verbEngine.normalizeAnswer("しずかじゃなかった")));
  assert.ok(expected.has(verbEngine.normalizeAnswer("shizukajyanakatta")));

  const past = adjEngine.conjugate(shizuka, "past");
  const pastExpected = new Set(adjEngine.answerVariants(past).map(function (text) {
    return verbEngine.normalizeAnswer(text);
  }));
  assert.ok(pastExpected.has(verbEngine.normalizeAnswer("shizukadesita")));
  assert.ok(pastExpected.has(verbEngine.normalizeAnswer("静かだ")) === false, "词干裸用不应判对");
});

test("answerVariants 校验非法输入", function () {
  assert.throws(function () { adjEngine.answerVariants(null); }, TypeError);
});
