"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const engine = require("../verb-engine.js");

const VERB_BY_ID = new Map(engine.VERBS.map(function (verb) {
  return [verb.id, verb];
}));

function form(verbId, voiceId, inflectionId) {
  return engine.conjugate(VERB_BY_ID.get(verbId), voiceId, inflectionId);
}

test("五段动词的五段词尾、否定与音便正确", function () {
  assert.equal(form("yomu", "base", "masu").written, "読みます");
  assert.equal(form("kau", "base", "negative").written, "買わない");
  assert.equal(form("oyogu", "base", "te").written, "泳いで");
  assert.equal(form("hanasu", "base", "past").written, "話した");
  assert.equal(form("matsu", "base", "te").written, "待って");
  assert.equal(form("shinu", "base", "past").written, "死んだ");
});

test("行く和一段动词使用正确的特例", function () {
  assert.equal(form("iku", "base", "te").written, "行って");
  assert.equal(form("iku", "base", "past").written, "行った");
  assert.equal(form("kaeru", "potential", "dictionary").written, "帰れる");
  assert.equal(form("taberu", "base", "negative").written, "食べない");
  assert.equal(form("taberu", "potential", "dictionary").written, "食べられる");
});

test("五段、一段与不规则动词支持所有派生语态", function () {
  assert.equal(form("yomu", "potential", "te").written, "読めて");
  assert.equal(form("yomu", "passive", "masu").written, "読まれます");
  assert.equal(form("yomu", "causative", "negative").written, "読ませない");
  assert.equal(form("yomu", "causativeShort", "past").written, "読ました");
  assert.equal(form("yomu", "causativePassive", "te").written, "読ませられて");
  assert.equal(form("yomu", "causativePassiveShort", "dictionary").written, "読まされる");
  assert.equal(form("hanasu", "causativePassiveShort", "dictionary").written, "話させられる");
  assert.equal(form("taberu", "causativeShort", "dictionary").written, "食べさす");
  assert.equal(form("benkyou-suru", "potential", "masu").written, "勉強できます");
  assert.equal(form("kuru", "causative", "dictionary").written, "来させる");
});

test("不规则动词的活用读音与辞书写法均可得到", function () {
  assert.equal(form("kuru", "base", "masu").written, "来ます");
  assert.equal(form("kuru", "base", "masu").kana, "きます");
  assert.equal(form("kuru", "base", "negative").kana, "こない");
  assert.equal(form("suru", "base", "zu").written, "せず");
  assert.equal(form("benkyou-suru", "base", "conditionalBa").written, "勉強すれば");
});

test("完整活用集合包含指定变形并可生成说明", function () {
  const formIds = new Set(engine.INFLECTIONS.map(function (item) { return item.id; }));
  const voiceIds = new Set(engine.VOICES.map(function (item) { return item.id; }));
  assert.ok(formIds.has("te"));
  assert.ok(formIds.has("tari"));
  assert.ok(voiceIds.has("causativePassiveShort"));
  assert.match(engine.explain(VERB_BY_ID.get("kau"), "base", "negative"), /う/);
  assert.match(engine.explain(VERB_BY_ID.get("iku"), "base", "te"), /行って/);
  assert.equal(
    engine.explain(VERB_BY_ID.get("suru"), "causativeShort", "masuNegative"),
    "先变「短使役形」：「する」变为较简短的「さす」。\n"
      + "再变「ます否定形」：把词尾换成对应的い段，再接「ません」。"
  );
});

test("所有语态与活用形式都提供常见表达大意", function () {
  assert.ok(engine.VOICES.every(function (voice) {
    return typeof voice.meaning === "string" && voice.meaning.trim().length > 0;
  }));
  assert.ok(engine.INFLECTIONS.every(function (form) {
    return typeof form.meaning === "string" && form.meaning.trim().length > 0;
  }));
});

test("所有内置动词都能生成全部变形组合", function () {
  for (const verb of engine.VERBS) {
    for (const voice of engine.VOICES) {
      for (const inflection of engine.INFLECTIONS) {
        const result = engine.conjugate(verb, voice.id, inflection.id);
        assert.ok(result.written.length, verb.id + " / " + voice.id + " / " + inflection.id);
        assert.ok(result.kana.length, verb.id + " / " + voice.id + " / " + inflection.id);
      }
    }
  }
});

test("罗马字输入可转换为平假名", function () {
  assert.equal(engine.toHiraganaFromRomaji("kakimasu"), "かきます");
  assert.equal(engine.toHiraganaFromRomaji("yomimashita"), "よみました");
  assert.equal(engine.toHiraganaFromRomaji("kanji"), "かんじ");
  assert.equal(engine.toHiraganaFromRomaji("matt e"), "まって");
});

test("答案归一化容忍中英标点、片假名与长音符号", function () {
  const normalize = engine.normalizeAnswer;
  assert.equal(normalize("かきます。"), "かきます");
  assert.equal(normalize("、かきます"), "かきます");
  assert.equal(normalize("かきます」"), "かきます");
  assert.equal(normalize("カキマス"), "かきます");
  assert.equal(normalize("ｶｷﾏｽ"), "かきます");
  assert.equal(normalize("kakimasu."), "かきます");
  assert.equal(normalize("kakimasu!"), "かきます");
  assert.equal(normalize("tōkyō"), "とうきょう");
  assert.equal(normalize("okāsan"), "おかあさん");
  assert.equal(normalize("きてー"), "きてー");
  assert.equal(normalize("ｔａｂｅｍａｓｕ"), "たべます");
  assert.equal(normalize("  ka ki masu  "), "かきます");
  assert.equal(normalize(""), "");
  assert.equal(normalize("。。。"), "");
});
