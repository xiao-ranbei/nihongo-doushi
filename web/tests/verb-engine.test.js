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

test("得る（える）按一段动词活用", function () {
  assert.equal(VERB_BY_ID.get("eru").reading, "える");
  assert.equal(form("eru", "base", "masu").written, "得ます");
  assert.equal(form("eru", "base", "masu").kana, "えます");
  assert.equal(form("eru", "base", "negative").written, "得ない");
  assert.equal(form("eru", "base", "past").written, "得た");
  assert.equal(form("eru", "potential", "dictionary").written, "得られる");
});

test("补充的常用动词按各自类别正确活用", function () {
  [
    ["iu", "言います"],
    ["aruku", "歩きます"],
    ["yasumu", "休みます"],
    ["hataraku", "働きます"],
    ["noru", "乗ります"],
    ["furu", "降ります"],
    ["uru", "売ります"],
    ["kaesu", "返します"],
    ["kesu", "消します"],
    ["erabu", "選びます"],
    ["yobu", "呼びます"],
    ["okuru", "送ります"],
    ["sagasu", "探します"],
    ["wakaru", "分かります"],
    ["modoru", "戻ります"],
    ["morau", "もらいます"],
    ["warau", "笑います"],
    ["naku", "泣きます"],
    ["hajimaru", "始まります"],
    ["owaru", "終わります"],
    ["iru", "います"],
    ["kiru-wear", "着ます"],
    ["ireru", "入れます"],
    ["miseru", "見せます"],
    ["kimeru", "決めます"],
    ["shiraberu", "調べます"],
    ["oboeru", "覚えます"],
    ["dekakeru", "出かけます"],
    ["tsuzukeru", "続けます"],
    ["atsumeru", "集めます"],
    ["denwa-suru", "電話します"],
    ["ryouri-suru", "料理します"],
    ["ryokou-suru", "旅行します"],
    ["renshu-suru", "練習します"]
  ].forEach(function (item) {
    assert.equal(form(item[0], "base", "masu").written, item[1], item[0]);
  });

  assert.equal(form("noru", "base", "negative").written, "乗らない");
  assert.equal(form("iru", "base", "negative").written, "いない");
  assert.equal(form("kiru-wear", "base", "negative").written, "着ない");
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
      + "再变「ます否定形」：把词尾换成对应的い段，再接「ません」。\n"
      + "六形对应：連用形接礼貌否定「ません」。"
  );
});

test("语态说明按动词类别返回对应变化规则", function () {
  assert.equal(
    engine.explainVoice(VERB_BY_ID.get("kaku"), "potential"),
    "五段动词词尾改为对应え段，再接「る」。"
  );
  assert.equal(
    engine.explainVoice(VERB_BY_ID.get("taberu"), "potential"),
    "一段动词去掉「る」，再接「られる」。"
  );
  assert.equal(
    engine.explainVoice(VERB_BY_ID.get("suru"), "potential"),
    "「する」的可能形是「できる」。"
  );
  assert.equal(
    engine.explainVoice(VERB_BY_ID.get("iku"), "base"),
    "不做语态变化，保留辞书形。"
  );
  assert.throws(function () {
    engine.explainVoice(VERB_BY_ID.get("iku"), "unknown");
  }, RangeError);
});

test("所有语态与活用形式都提供常见表达大意", function () {
  assert.ok(engine.VOICES.every(function (voice) {
    return typeof voice.meaning === "string" && voice.meaning.trim().length > 0;
  }));
  assert.ok(engine.INFLECTIONS.every(function (form) {
    return typeof form.meaning === "string" && form.meaning.trim().length > 0;
  }));
});

test("现有动词形式提示标出六种传统活用形的对应关系", function () {
  const kaku = engine.VERBS.filter(function (verb) {
    return verb.id === "kaku";
  })[0];
  assert.match(engine.explain(kaku, "base", "negative"), /未然形接「ない」/);
  assert.match(engine.explain(kaku, "base", "masu"), /連用形接「ます」/);
  assert.match(engine.explain(kaku, "base", "dictionary"), /終止形.*連体形/);
  assert.match(engine.explain(kaku, "base", "conditionalBa"), /仮定形接「ば」/);
  assert.match(engine.explain(kaku, "base", "conditionalTara"), /不是仮定形接「ば」/);
  assert.match(engine.explain(kaku, "base", "imperative"), /命令形/);
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

test("归一化明细标识罗马字识别失败", function () {
  const detailed = engine.normalizeAnswerDetailed;
  // 可识别的纯罗马字：正常转换，不标记失败
  assert.deepEqual(detailed("kakimasu"), { answer: "かきます", romajiFailed: false });
  assert.deepEqual(detailed("sinbun"), { answer: "しんぶん", romajiFailed: false });
  // 非罗马字输入（汉字/假名/标点）：不经过罗马字管线
  assert.deepEqual(detailed("かきます。"), { answer: "かきます", romajiFailed: false });
  assert.deepEqual(detailed(""), { answer: "", romajiFailed: false });
  // 表外序列（含无法映射的字符）：整体回退原串并标记失败
  assert.deepEqual(detailed("hello"), { answer: "hello", romajiFailed: true });
  assert.deepEqual(detailed("xyz"), { answer: "xyz", romajiFailed: true });
  // 部分可映射仍算失败：结果与原串不同但包含回退段时不出现——
  // toHiraganaFromRomaji 一旦遇表外字符即整体返回原串，故 converted === normalized 即失败
  assert.equal(detailed("kakimasu").answer, engine.normalizeAnswer("kakimasu"));
});
