(function (root) {
  "use strict";

  // 形容词活用引擎：与 verb-engine 同一套约定 ——
  // 纯逻辑零 DOM、数据 + 变形管线 + 中文规则解释、双表示 { written, kana }、
  // UMD 双端（浏览器挂 window，node 走 module.exports 供测试 require）。
  // 答案归一化继续复用 verb-engine 的 normalizeAnswer，本文件不重复实现。

  const ADJECTIVES = [
    { id: "takai", word: "高い", kana: "たかい", meaning: "高的", kind: "i" },
    { id: "nagai", word: "長い", kana: "ながい", meaning: "长的", kind: "i" },
    { id: "samui", word: "寒い", kana: "さむい", meaning: "寒冷的", kind: "i" },
    { id: "ookii", word: "大きい", kana: "おおきい", meaning: "大的", kind: "i" },
    { id: "hayai", word: "早い", kana: "はやい", meaning: "早的", kind: "i" },
    { id: "oishii", word: "おいしい", kana: "おいしい", meaning: "好吃的", kind: "i" },
    { id: "isogashii", word: "忙しい", kana: "いそがしい", meaning: "忙碌的", kind: "i" },
    { id: "ii", word: "いい", kana: "いい", meaning: "好的", kind: "i", special: true },
    { id: "shizuka", word: "静か", kana: "しずか", meaning: "安静的", kind: "na" },
    { id: "benri", word: "便利", kana: "べんり", meaning: "方便的", kind: "na" },
    { id: "suki", word: "好き", kana: "すき", meaning: "喜欢的", kind: "na" },
    { id: "kirei", word: "きれい", kana: "きれい", meaning: "干净的、美丽的", kind: "na" },
    { id: "yuumei", word: "有名", kana: "ゆうめい", meaning: "有名的", kind: "na" },
    { id: "kantan", word: "簡単", kana: "かんたん", meaning: "简单的", kind: "na" },
    { id: "genki", word: "元気", kana: "げんき", meaning: "有精神的", kind: "na" }
  ];

  const FORMS = [
    { id: "attributive", label: "修饰名词", level: "basic", meaning: "直接放在名词前面使用。" },
    { id: "present", label: "现在肯定", level: "basic", meaning: "作谓语表示现在的性质或状态；更礼貌时可以接「です」。" },
    { id: "presentNegative", label: "现在否定", level: "basic", meaning: "表示「不是……」；两类形容词的否定接法不同。" },
    { id: "past", label: "过去肯定", level: "basic", meaning: "表示过去的性质或状态。" },
    { id: "pastNegative", label: "过去否定", level: "advanced", meaning: "表示过去「不是……」。" },
    { id: "te", label: "て形", level: "advanced", meaning: "用来并列多个性质，也可以表示原因。" },
    { id: "adverbial", label: "状语（修饰动词）", level: "advanced", meaning: "放在动词前面修饰动作；两类形容词的接法不同。" }
  ];

  const ADJECTIVE_BY_ID = new Map(ADJECTIVES.map(function (item) { return [item.id, item]; }));
  const FORM_BY_ID = new Map(FORMS.map(function (form) { return [form.id, form]; }));

  // い形容词：去掉词尾「い」，再接词尾。修饰名词与现在肯定保持原形。
  const I_SUFFIXES = {
    attributive: "",
    present: "",
    presentNegative: "くない",
    past: "かった",
    pastNegative: "くなかった",
    te: "くて",
    adverbial: "く"
  };

  // 「いい」是唯一不按普通规则变形的い形容词：全部换成「よく」系列。
  const II_RESULTS = {
    attributive: "いい",
    present: "いい",
    presentNegative: "よくない",
    past: "よかった",
    pastNegative: "よくなかった",
    te: "よくて",
    adverbial: "よく"
  };

  function pair(written, kana, alts) {
    return { written: written, kana: kana, alts: alts || [] };
  }

  function buildI(adjective, formId) {
    const suffix = I_SUFFIXES[formId];
    // 修饰名词与现在肯定保持原形（不加任何词尾）；其余形式去「い」再接后缀。
    if (!suffix) {
      return pair(adjective.word, adjective.kana);
    }
    return pair(
      adjective.word.slice(0, -1) + suffix,
      adjective.kana.slice(0, -1) + suffix
    );
  }

  function buildSpecialI(adjective, formId) {
    const result = II_RESULTS[formId];
    return pair(result, result);
  }

  function buildNa(adjective, formId) {
    const word = adjective.word;
    const kana = adjective.kana;
    const builders = {
      attributive: function () {
        return pair(word + "な", kana + "な");
      },
      present: function () {
        return pair(word + "だ", kana + "だ", [
          pair(word + "です", kana + "です")
        ]);
      },
      presentNegative: function () {
        return pair(word + "じゃない", kana + "じゃない", [
          pair(word + "ではない", kana + "ではない"),
          pair(word + "じゃありません", kana + "じゃありません"),
          pair(word + "ではありません", kana + "ではありません")
        ]);
      },
      past: function () {
        return pair(word + "だった", kana + "だった", [
          pair(word + "でした", kana + "でした")
        ]);
      },
      pastNegative: function () {
        return pair(word + "ではなかった", kana + "ではなかった", [
          pair(word + "じゃなかった", kana + "じゃなかった"),
          pair(word + "ではありませんでした", kana + "ではありませんでした"),
          pair(word + "じゃありませんでした", kana + "じゃありませんでした")
        ]);
      },
      te: function () {
        return pair(word + "で", kana + "で");
      },
      adverbial: function () {
        return pair(word + "に", kana + "に");
      }
    };
    return builders[formId]();
  }

  function conjugate(adjective, formId) {
    if (!adjective || typeof adjective.word !== "string" || typeof adjective.kana !== "string") {
      throw new TypeError("An adjective with word and kana is required.");
    }
    if (!FORM_BY_ID.has(formId)) {
      throw new RangeError("Unknown adjective form: " + formId);
    }
    if (adjective.kind === "i") {
      if (adjective.word.slice(-1) !== "い" || adjective.kana.slice(-1) !== "い") {
        throw new TypeError("い形容词必须以「い」结尾: " + adjective.word);
      }
      return adjective.special
        ? buildSpecialI(adjective, formId)
        : buildI(adjective, formId);
    }
    if (adjective.kind === "na") {
      return buildNa(adjective, formId);
    }
    throw new TypeError("Unsupported adjective kind: " + adjective.kind);
  }

  const I_RULE_TEXT = {
    attributive: "直接放在名词前，保持原形。",
    present: "保持原形直接作谓语；更礼貌时在后面接「です」。",
    presentNegative: "把词尾「い」换成「くない」。",
    past: "把词尾「い」换成「かった」。",
    pastNegative: "把词尾「い」换成「くなかった」。",
    te: "把词尾「い」换成「くて」。",
    adverbial: "把词尾「い」换成「く」，再放在动词前。"
  };

  const NA_RULE_TEXT = {
    attributive: "在词干后面接「な」，再放在名词前。",
    present: "在词干后面接「だ」；更礼貌时接「です」。",
    presentNegative: "在词干后面接「じゃない」，也可以接「ではない」。",
    past: "在词干后面接「だった」；更礼貌时接「でした」。",
    pastNegative: "在词干后面接「ではなかった」，也可以接「じゃなかった」。",
    te: "在词干后面接「で」，用来并列多个性质。",
    adverbial: "在词干后面接「に」，再放在动词前。"
  };

  // 判定用答案集合：主形态（written/kana）+ 礼貌变体（alts）+ 助词音变体。
  // 「では」的 は 是助词读 wa，罗马字 dewanakatta 忠实转写为「でわ」，
  // 与书写形态「では」不一致；这里把含「では」的串补一个「でわ」变体，
  // 让罗马字输入与假名书写都能判对。仅此一处拼写变体，不做通用替换。
  function answerVariants(result) {
    if (!result || typeof result.written !== "string" || typeof result.kana !== "string") {
      throw new TypeError("A conjugation result with written and kana is required.");
    }
    const variants = [result.written, result.kana];
    (result.alts || []).forEach(function (alt) {
      variants.push(alt.written, alt.kana);
    });
    const particleVariants = [];
    variants.forEach(function (text) {
      if (text.indexOf("では") !== -1) {
        particleVariants.push(text.split("では").join("でわ"));
      }
    });
    return variants.concat(particleVariants);
  }

  function explain(adjective, formId) {
    if (!adjective || !ADJECTIVE_BY_ID.has(adjective.id) || !FORM_BY_ID.has(formId)) {
      throw new RangeError("A known adjective and form are required.");
    }
    const label = FORM_BY_ID.get(formId).label;
    if (adjective.kind === "i" && adjective.special) {
      return "变「" + label + "」：「いい」不参与变形，先换成「よく」再接词尾，得到「"
        + II_RESULTS[formId] + "」。";
    }
    const rule = adjective.kind === "i" ? I_RULE_TEXT[formId] : NA_RULE_TEXT[formId];
    return "变「" + label + "」：" + rule;
  }

  const api = {
    ADJECTIVES: ADJECTIVES,
    FORMS: FORMS,
    conjugate: conjugate,
    answerVariants: answerVariants,
    explain: explain
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  root.JapaneseAdjectiveEngine = api;
})(globalThis);
