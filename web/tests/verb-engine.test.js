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

test("词库结构完整：id 与词形唯一、分组取值合法、五段词尾全覆盖", function () {
  const ids = new Set();
  const words = new Set();
  const ALLOWED_GROUPS = new Set(["godan", "ichidan", "irregular"]);
  const ALLOWED_KINDS = new Set(["godan", "iku", "ichidan", "suru", "kuru"]);
  const endings = new Set();

  for (const verb of engine.VERBS) {
    assert.ok(!ids.has(verb.id), "动词 id 重复：" + verb.id);
    ids.add(verb.id);
    const word = verb.dictionary + "|" + verb.reading;
    assert.ok(!words.has(word), "动词词形重复：" + word);
    words.add(word);
    assert.ok(ALLOWED_GROUPS.has(verb.group), verb.id + " 的分组非法：" + verb.group);
    assert.ok(ALLOWED_KINDS.has(verb.kind), verb.id + " 的 kind 非法：" + verb.kind);
    if (verb.kind === "godan" || verb.kind === "iku") {
      endings.add(verb.dictionary.slice(-1));
    }
  }

  // 「ぬ」结尾的五段动词在现代日语中只有「死ぬ」，其余词尾应各有多个样本，
  // 保证音便练习不会只覆盖少数词尾。
  ["う", "く", "ぐ", "す", "つ", "ぬ", "ぶ", "む", "る"].forEach(function (ending) {
    assert.ok(endings.has(ending), "词库缺少以「" + ending + "」结尾的五段动词");
  });
});

test("扩容动词的音便与语态抽样正确", function () {
  const cases = [
    ["arau", "te", "洗って"], ["arau", "past", "洗った"],
    ["nugu", "te", "脱いで"], ["nugu", "past", "脱いだ"],
    ["migaku", "te", "磨いて"], ["migaku", "past", "磨いた"],
    ["kasu", "te", "貸して"], ["kasu", "past", "貸した"],
    ["katsu", "te", "勝って"], ["katsu", "past", "勝った"],
    ["hakobu", "te", "運んで"], ["hakobu", "past", "運んだ"],
    ["sumu", "te", "住んで"], ["sumu", "past", "住んだ"],
    ["noboru", "te", "登って"], ["noboru", "past", "登った"],
    ["mitsukaru", "negative", "見つからない"],
    ["katadzukeru", "masu", "片付けます"],
    ["umareru", "te", "生まれて"],
    ["sentaku-suru", "masu", "洗濯します"],
    ["sentaku-suru", "negative", "洗濯しない"],
    ["shutchou-suru", "past", "出張した"]
  ];

  cases.forEach(function (item) {
    const verb = VERB_BY_ID.get(item[0]);
    assert.ok(verb, "找不到动词：" + item[0]);
    assert.equal(
      engine.conjugate(verb, "base", item[1]).written,
      item[2],
      item[0] + " / " + item[1]
    );
  });

  const voiceCases = [
    ["katadzukeru", "potential", "片付けられる"],
    ["sentaku-suru", "potential", "洗濯できる"],
    ["kasu", "passive", "貸される"],
    ["nugu", "causative", "脱がせる"]
  ];
  voiceCases.forEach(function (item) {
    const verb = VERB_BY_ID.get(item[0]);
    assert.equal(
      engine.conjugate(verb, item[1], "dictionary").written,
      item[2],
      item[0] + " / " + item[1]
    );
  });
});

test("书面语与复合动词的活用抽样正确", function () {
  // 这批词按语体补入：行う 这类书面语动词与「申し込む／間に合う」这类复合动词
  // 是上一轮按词尾音便选词时整类漏掉的。抽样重点盯「汉字同形但读音不同」的坑：
  // 行う（おこなう）与 行く（いく）汉字相同，て形必须按五段规则走 おこなって。
  const cases = [
    ["okonau", "te", "行って"],
    ["okonau", "past", "行った"],
    ["okonau", "negative", "行わない"],
    ["fukumu", "te", "含んで"],
    ["shimesu", "masu", "示します"],
    ["motodzuku", "te", "基づいて"],
    ["motodzuku", "past", "基づいた"],
    ["kakawaru", "negative", "関わらない"],
    ["tomonau", "te", "伴って"],
    ["moushikomu", "masu", "申し込みます"],
    ["moushikomu", "te", "申し込んで"],
    ["maniau", "te", "間に合って"],
    ["ochitsuku", "te", "落ち着いて"],
    ["moukeru", "masu", "設けます"],
    ["shoujiru", "masu", "生じます"],
    ["shoujiru", "te", "生じて"],
    ["hikiukeru", "masu", "引き受けます"],
    ["sadameru", "past", "定めた"]
  ];

  cases.forEach(function (item) {
    const target = VERB_BY_ID.get(item[0]);
    assert.ok(target, "找不到动词：" + item[0]);
    assert.equal(
      engine.conjugate(target, "base", item[1]).written,
      item[2],
      item[0] + " / " + item[1]
    );
  });

  // 行う 与 行く 的假名必须分开：前者 おこなって，后者 いって
  const okonau = VERB_BY_ID.get("okonau");
  const iku = VERB_BY_ID.get("iku");
  assert.equal(engine.conjugate(okonau, "base", "te").kana, "おこなって");
  assert.equal(engine.conjugate(iku, "base", "te").kana, "いって");
  assert.equal(engine.conjugate(okonau, "base", "masu").kana, "おこないます");
  assert.equal(engine.conjugate(iku, "base", "masu").kana, "いきます");

  // サ変复合的漢語词干切分：提出 + する
  assert.equal(
    engine.conjugate(VERB_BY_ID.get("taiou-suru"), "potential", "dictionary").written,
    "対応できる"
  );
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

// —— 按类别看规则（五段/一段/サ变/カ变）——

test("类别规则按类给出各自的活用说法", function () {
  assert.equal(engine.explainClass("godan", "masu"), "把词尾换成对应的い段，再接「ます」。");
  assert.equal(engine.explainClass("ichidan", "masu"), "去掉「る」后接「ます」。");
  assert.equal(engine.explainClass("suru", "masu"), "连用形「し」加「ます」。");
  assert.match(engine.explainClass("kuru", "masu"), /きます/);

  assert.match(engine.explainClass("godan", "negative"), /あ段/);
  assert.match(engine.explainClass("ichidan", "negative"), /去掉「る」/);
  assert.match(engine.explainClass("godan", "te"), /音便/);
  assert.match(engine.explainClass("ichidan", "te"), /去掉「る」/);
});

test("类别规则不受行く特例污染", function () {
  // 行く 的て形是不规则，但五段这一类不该继承它。
  assert.equal(
    engine.explainClass("godan", "te").indexOf("行く"),
    -1,
    "类级规则不该出现个别动词"
  );
  assert.equal(
    engine.explainClass("godan", "past").indexOf("行く"),
    -1,
    "た形同理"
  );
  // 通用规则仍在：「う」结尾转あ段要变「わ」。
  assert.match(engine.explainClass("godan", "negative"), /「う」要变成「わ」/);
});

test("四类各自绑定一个属于该类的代表词", function () {
  assert.equal(engine.VERB_CLASSES.length, 4);
  engine.VERB_CLASSES.forEach(function (klass) {
    const representative = engine.getVerbById(klass.representativeId);
    assert.ok(representative, klass.id + " 应有代表词");
    assert.equal(
      engine.classIdOfVerb(representative),
      klass.id,
      klass.id + " 的代表词应属于该类"
    );
    assert.equal(typeof klass.label, "string");
    assert.equal(typeof klass.alias, "string");
    assert.ok(klass.detect.length > 0, klass.id + " 应给出判定方法");
    assert.ok(klass.exceptions.length > 0, klass.id + " 应列出例外");
  });
});

test("类别归类：行く并入五段、帰る是五段、変える是一段", function () {
  const classOf = function (verbId) {
    return engine.classIdOfVerb(VERB_BY_ID.get(verbId));
  };
  assert.equal(classOf("iku"), "godan");
  assert.equal(classOf("kaeru"), "godan");
  assert.equal(classOf("kaeru-change"), "ichidan");
  assert.equal(classOf("taberu"), "ichidan");
  assert.equal(classOf("suru"), "suru");
  assert.equal(classOf("benkyou-suru"), "suru");
  assert.equal(classOf("kuru"), "kuru");
  assert.equal(engine.classIdOfKind("iku"), "godan");
  assert.equal(engine.classIdOfKind("godan"), "godan");
  assert.equal(engine.classIdOfKind("ichidan"), "ichidan");
});

test("五段九行覆盖全部词尾，代表词词尾与所在行一致", function () {
  const endings = engine.GODAN_ROWS.map(function (row) {
    return row.ending;
  });
  assert.equal(endings.length, 9);
  assert.deepEqual(
    endings.slice().sort(),
    ["う", "く", "ぐ", "す", "つ", "ぬ", "ぶ", "む", "る"].sort()
  );

  engine.GODAN_ROWS.forEach(function (row) {
    const verb = engine.getVerbById(row.verbId);
    assert.ok(verb, row.label + " 应有代表词");
    assert.equal(
      verb.dictionary.slice(-1),
      row.ending,
      row.label + " 的代表词应以「" + row.ending + "」结尾"
    );
    assert.equal(engine.classIdOfVerb(verb), "godan", row.label + " 代表词应是五段");
    assert.match(row.onbin, /音便/);
  });
});

test("类别规则拒绝未知类别与未知活用形", function () {
  assert.throws(function () {
    engine.explainClass("nope", "masu");
  }, RangeError);
  assert.throws(function () {
    engine.explainClass("godan", "nope");
  }, RangeError);
});
