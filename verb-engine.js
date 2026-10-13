(function (root) {
  "use strict";

  const VOICES = [
    { id: "base", label: "原形", meaning: "普通形态，不附加可能、受身或使役等语态意义。" },
    { id: "potential", label: "可能形", meaning: "表示“能／可以做……”，常见为能力或许可。" },
    { id: "passive", label: "受身形", meaning: "表示“被……”，也可表达受到影响或感受；是否带负面语气看上下文。" },
    { id: "causative", label: "使役形", meaning: "表示“让／使某人做……”，也可表示允许对方做。" },
    { id: "causativeShort", label: "短使役形", meaning: "与使役形大意相同的短形，较多见于口语；使用场合会因动词和语境而异。" },
    { id: "causativePassive", label: "使役受身形", meaning: "表示“被迫做……”或“不得不做……”，强调受到他人驱使。" },
    { id: "causativePassiveShort", label: "短使役受身形", meaning: "与使役受身形大意相同的短形，较多见于口语；实际用法因动词和语境而异。" }
  ];

  const INFLECTIONS = [
    { id: "dictionary", label: "辞书形", level: "basic", meaning: "普通形的非过去表达，可表示习惯、事实或将来；具体时间由语境判断。" },
    { id: "masuStem", label: "连用形（ます词干）", level: "basic", meaning: "连用词干本身通常不单独表达时态或礼貌，常接「ます」及其他后续表达。" },
    { id: "masu", label: "ます形", level: "basic", meaning: "礼貌的非过去表达，可表示习惯、事实或将来。" },
    { id: "masuNegative", label: "ます否定形", level: "basic", meaning: "礼貌地表示不做、不会做或目前不是……。" },
    { id: "masuPast", label: "ます过去形", level: "basic", meaning: "礼貌地表示过去做了某事或过去的状态。" },
    { id: "masuNegativePast", label: "ます过去否定形", level: "basic", meaning: "礼貌地表示过去没有做某事或不处于某状态。" },
    { id: "negative", label: "ない形", level: "basic", meaning: "普通形的非过去否定，可表示不做、不会做或不是……。" },
    { id: "negativePast", label: "ない过去形", level: "basic", meaning: "普通形的过去否定，表示过去没有做某事或不处于某状态。" },
    { id: "past", label: "た形", level: "basic", meaning: "普通形的过去／完成表达；也可结合语境表示经历或结果状态。" },
    { id: "te", label: "て形", level: "basic", meaning: "用于连接动作或状态；也常用于请求「てください」、持续或结果「ている」等，具体含义取决于后续表达。" },
    { id: "conditionalBa", label: "条件形（ば）", level: "basic", meaning: "表示“如果……就……”，常用于一般条件、规律或假设。" },
    { id: "conditionalTara", label: "条件形（たら）", level: "basic", meaning: "表示“如果……／当……时”；也可表示前项完成后再发生后项。" },
    { id: "volitional", label: "意向形", level: "advanced", meaning: "表示提议“一起……吧”或说话者的意志“我来……／打算……”。" },
    { id: "imperative", label: "命令形", level: "advanced", meaning: "直接命令对方“做……！”，语气较强，使用时注意场合。" },
    { id: "sou", label: "そう形", level: "advanced", meaning: "根据外观或迹象表示“看起来要……／似乎……”（样态）；注意与传闻「そうだ」区分。" },
    { id: "tai", label: "たい形", level: "advanced", meaning: "表示（说话者）想做……；主语和具体用法受语境影响。" },
    { id: "zu", label: "ず形", level: "advanced", meaning: "表示“不……／未……”；偏书面，常见于「〜ずに」（不做……而……）。" },
    { id: "tari", label: "たり形", level: "advanced", meaning: "用来举例列举动作，如“做做……之类的事”，常和「する」搭配，不表示穷举。" }
  ];

  const VERBS = [
    { id: "iku", dictionary: "行く", reading: "いく", meaning: "去、前往", group: "godan", kind: "iku" },
    { id: "kaku", dictionary: "書く", reading: "かく", meaning: "写", group: "godan", kind: "godan" },
    { id: "kiku", dictionary: "聞く", reading: "きく", meaning: "听、询问", group: "godan", kind: "godan" },
    { id: "oyogu", dictionary: "泳ぐ", reading: "およぐ", meaning: "游泳", group: "godan", kind: "godan" },
    { id: "isogu", dictionary: "急ぐ", reading: "いそぐ", meaning: "赶快", group: "godan", kind: "godan" },
    { id: "hanasu", dictionary: "話す", reading: "はなす", meaning: "说话", group: "godan", kind: "godan" },
    { id: "dasu", dictionary: "出す", reading: "だす", meaning: "拿出、提交", group: "godan", kind: "godan" },
    { id: "matsu", dictionary: "待つ", reading: "まつ", meaning: "等待", group: "godan", kind: "godan" },
    { id: "motsu", dictionary: "持つ", reading: "もつ", meaning: "拿、持有", group: "godan", kind: "godan" },
    { id: "shinu", dictionary: "死ぬ", reading: "しぬ", meaning: "死亡", group: "godan", kind: "godan" },
    { id: "asobu", dictionary: "遊ぶ", reading: "あそぶ", meaning: "玩耍", group: "godan", kind: "godan" },
    { id: "yomu", dictionary: "読む", reading: "よむ", meaning: "阅读", group: "godan", kind: "godan" },
    { id: "nomu", dictionary: "飲む", reading: "のむ", meaning: "喝", group: "godan", kind: "godan" },
    { id: "kau", dictionary: "買う", reading: "かう", meaning: "买", group: "godan", kind: "godan" },
    { id: "au", dictionary: "会う", reading: "あう", meaning: "见面", group: "godan", kind: "godan" },
    { id: "utau", dictionary: "歌う", reading: "うたう", meaning: "唱歌", group: "godan", kind: "godan" },
    { id: "omou", dictionary: "思う", reading: "おもう", meaning: "想、认为", group: "godan", kind: "godan" },
    { id: "kaeru", dictionary: "帰る", reading: "かえる", meaning: "回去", group: "godan", kind: "godan" },
    { id: "hairu", dictionary: "入る", reading: "はいる", meaning: "进入", group: "godan", kind: "godan" },
    { id: "kiru", dictionary: "切る", reading: "きる", meaning: "切、剪", group: "godan", kind: "godan" },
    { id: "hashiru", dictionary: "走る", reading: "はしる", meaning: "跑", group: "godan", kind: "godan" },
    { id: "tsukuru", dictionary: "作る", reading: "つくる", meaning: "制作", group: "godan", kind: "godan" },
    { id: "toru", dictionary: "取る", reading: "とる", meaning: "拿、取得", group: "godan", kind: "godan" },
    { id: "tatsu", dictionary: "立つ", reading: "たつ", meaning: "站立", group: "godan", kind: "godan" },
    { id: "shiru", dictionary: "知る", reading: "しる", meaning: "知道", group: "godan", kind: "godan" },
    { id: "kau-tsukau", dictionary: "使う", reading: "つかう", meaning: "使用", group: "godan", kind: "godan" },
    { id: "taberu", dictionary: "食べる", reading: "たべる", meaning: "吃", group: "ichidan", kind: "ichidan" },
    { id: "miru", dictionary: "見る", reading: "みる", meaning: "看", group: "ichidan", kind: "ichidan" },
    { id: "okiru", dictionary: "起きる", reading: "おきる", meaning: "起床", group: "ichidan", kind: "ichidan" },
    { id: "neru", dictionary: "寝る", reading: "ねる", meaning: "睡觉", group: "ichidan", kind: "ichidan" },
    { id: "oshieru", dictionary: "教える", reading: "おしえる", meaning: "教、告诉", group: "ichidan", kind: "ichidan" },
    { id: "akeru", dictionary: "開ける", reading: "あける", meaning: "打开", group: "ichidan", kind: "ichidan" },
    { id: "shimeru", dictionary: "閉める", reading: "しめる", meaning: "关闭", group: "ichidan", kind: "ichidan" },
    { id: "deru", dictionary: "出る", reading: "でる", meaning: "出去、出现", group: "ichidan", kind: "ichidan" },
    { id: "wasureru", dictionary: "忘れる", reading: "わすれる", meaning: "忘记", group: "ichidan", kind: "ichidan" },
    { id: "kariru", dictionary: "借りる", reading: "かりる", meaning: "借入", group: "ichidan", kind: "ichidan" },
    { id: "oriru", dictionary: "降りる", reading: "おりる", meaning: "下车、下来", group: "ichidan", kind: "ichidan" },
    { id: "hajimeru", dictionary: "始める", reading: "はじめる", meaning: "开始", group: "ichidan", kind: "ichidan" },
    { id: "kangaeru", dictionary: "考える", reading: "かんがえる", meaning: "思考", group: "ichidan", kind: "ichidan" },
    { id: "suru", dictionary: "する", reading: "する", meaning: "做", group: "irregular", kind: "suru" },
    { id: "benkyou-suru", dictionary: "勉強する", reading: "べんきょうする", meaning: "学习", group: "irregular", kind: "suru" },
    { id: "undou-suru", dictionary: "運動する", reading: "うんどうする", meaning: "运动", group: "irregular", kind: "suru" },
    { id: "souji-suru", dictionary: "掃除する", reading: "そうじする", meaning: "打扫", group: "irregular", kind: "suru" },
    { id: "kuru", dictionary: "来る", reading: "くる", meaning: "来", group: "irregular", kind: "kuru" }
  ];

  const I_ROW = {
    "う": "い", "く": "き", "ぐ": "ぎ", "す": "し", "つ": "ち",
    "ぬ": "に", "ぶ": "び", "む": "み", "る": "り"
  };
  const A_ROW = {
    "う": "わ", "く": "か", "ぐ": "が", "す": "さ", "つ": "た",
    "ぬ": "な", "ぶ": "ば", "む": "ま", "る": "ら"
  };
  const E_ROW = {
    "う": "え", "く": "け", "ぐ": "げ", "す": "せ", "つ": "て",
    "ぬ": "ね", "ぶ": "べ", "む": "め", "る": "れ"
  };
  const O_ROW = {
    "う": "お", "く": "こ", "ぐ": "ご", "す": "そ", "つ": "と",
    "ぬ": "の", "ぶ": "ぼ", "む": "も", "る": "ろ"
  };
  const GODAN_TE = {
    "う": "って", "つ": "って", "る": "って",
    "む": "んで", "ぶ": "んで", "ぬ": "んで",
    "く": "いて", "ぐ": "いで", "す": "して"
  };
  const GODAN_PAST = {
    "う": "った", "つ": "った", "る": "った",
    "む": "んだ", "ぶ": "んだ", "ぬ": "んだ",
    "く": "いた", "ぐ": "いだ", "す": "した"
  };
  const VOICE_IDS = new Set(VOICES.map(function (voice) { return voice.id; }));
  const INFLECTION_IDS = new Set(INFLECTIONS.map(function (form) { return form.id; }));

  const ROMAJI = {
    a: "あ", i: "い", u: "う", e: "え", o: "お",
    ka: "か", ki: "き", ku: "く", ke: "け", ko: "こ",
    sa: "さ", shi: "し", si: "し", su: "す", se: "せ", so: "そ",
    ta: "た", chi: "ち", ti: "ち", tsu: "つ", tu: "つ", te: "て", to: "と",
    na: "な", ni: "に", nu: "ぬ", ne: "ね", no: "の",
    ha: "は", hi: "ひ", fu: "ふ", hu: "ふ", he: "へ", ho: "ほ",
    ma: "ま", mi: "み", mu: "む", me: "め", mo: "も",
    ya: "や", yu: "ゆ", yo: "よ",
    ra: "ら", ri: "り", ru: "る", re: "れ", ro: "ろ",
    wa: "わ", wo: "を", n: "ん",
    ga: "が", gi: "ぎ", gu: "ぐ", ge: "げ", go: "ご",
    za: "ざ", ji: "じ", zi: "じ", zu: "ず", ze: "ぜ", zo: "ぞ",
    da: "だ", di: "ぢ", du: "づ", de: "で", do: "ど",
    ba: "ば", bi: "び", bu: "ぶ", be: "べ", bo: "ぼ",
    pa: "ぱ", pi: "ぴ", pu: "ぷ", pe: "ぺ", po: "ぽ",
    kya: "きゃ", kyu: "きゅ", kyo: "きょ",
    sha: "しゃ", shu: "しゅ", sho: "しょ", sya: "しゃ", syu: "しゅ", syo: "しょ",
    cha: "ちゃ", chu: "ちゅ", cho: "ちょ", tya: "ちゃ", tyu: "ちゅ", tyo: "ちょ",
    nya: "にゃ", nyu: "にゅ", nyo: "にょ",
    hya: "ひゃ", hyu: "ひゅ", hyo: "ひょ",
    mya: "みゃ", myu: "みゅ", myo: "みょ",
    rya: "りゃ", ryu: "りゅ", ryo: "りょ",
    gya: "ぎゃ", gyu: "ぎゅ", gyo: "ぎょ",
    ja: "じゃ", ju: "じゅ", jo: "じょ", jya: "じゃ", jyu: "じゅ", jyo: "じょ",
    bya: "びゃ", byu: "びゅ", byo: "びょ",
    pya: "ぴゃ", pyu: "ぴゅ", pyo: "ぴょ"
  };

  function isGodan(kind) {
    return kind === "godan" || kind === "iku";
  }

  function deriveVoice(text, kind, voiceId, representation) {
    if (!VOICE_IDS.has(voiceId)) {
      throw new RangeError("Unknown verb form: " + voiceId);
    }
    if (voiceId === "base") {
      return { text: text, kind: kind };
    }

    if (kind === "suru") {
      const base = text.slice(0, -2);
      const forms = {
        potential: { suffix: "できる", kind: "ichidan" },
        passive: { suffix: "される", kind: "ichidan" },
        causative: { suffix: "させる", kind: "ichidan" },
        causativeShort: { suffix: "さす", kind: "godan" },
        causativePassive: { suffix: "させられる", kind: "ichidan" },
        causativePassiveShort: { suffix: "させられる", kind: "ichidan" }
      };
      const form = forms[voiceId];
      return { text: base + form.suffix, kind: form.kind };
    }

    if (kind === "kuru") {
      const writing = representation !== "kana";
      const forms = {
        potential: { kanji: "来られる", kana: "こられる", kind: "ichidan" },
        passive: { kanji: "来られる", kana: "こられる", kind: "ichidan" },
        causative: { kanji: "来させる", kana: "こさせる", kind: "ichidan" },
        causativeShort: { kanji: "来さす", kana: "こさす", kind: "godan" },
        causativePassive: { kanji: "来させられる", kana: "こさせられる", kind: "ichidan" },
        causativePassiveShort: { kanji: "来させられる", kana: "こさせられる", kind: "ichidan" }
      };
      const form = forms[voiceId];
      return { text: writing ? form.kanji : form.kana, kind: form.kind };
    }

    const ending = text.slice(-1);
    const stem = text.slice(0, -1);

    if (isGodan(kind)) {
      const aRow = A_ROW[ending];
      const eRow = E_ROW[ending];
      if (!aRow || !eRow) {
        throw new TypeError("Unsupported godan verb ending: " + ending);
      }
      const aStem = stem + aRow;
      const forms = {
        potential: { text: stem + eRow + "る", kind: "ichidan" },
        passive: { text: aStem + "れる", kind: "ichidan" },
        causative: { text: aStem + "せる", kind: "ichidan" },
        causativeShort: { text: aStem + "す", kind: "godan" },
        causativePassive: { text: aStem + "せられる", kind: "ichidan" },
        causativePassiveShort: {
          text: aStem + (ending === "す" ? "せられる" : "される"),
          kind: "ichidan"
        }
      };
      return forms[voiceId];
    }

    if (kind === "ichidan") {
      const forms = {
        potential: { text: stem + "られる", kind: "ichidan" },
        passive: { text: stem + "られる", kind: "ichidan" },
        causative: { text: stem + "させる", kind: "ichidan" },
        causativeShort: { text: stem + "さす", kind: "godan" },
        causativePassive: { text: stem + "させられる", kind: "ichidan" },
        causativePassiveShort: { text: stem + "させられる", kind: "ichidan" }
      };
      return forms[voiceId];
    }

    throw new TypeError("Unsupported verb group: " + kind);
  }

  function inflect(verb, formId, representation) {
    if (!INFLECTION_IDS.has(formId)) {
      throw new RangeError("Unknown inflection: " + formId);
    }
    const text = verb.text;
    const kind = verb.kind;

    if (kind === "suru") {
      const base = text.slice(0, -2);
      const forms = {
        dictionary: text,
        masuStem: base + "し",
        masu: base + "します",
        masuNegative: base + "しません",
        masuPast: base + "しました",
        masuNegativePast: base + "しませんでした",
        negative: base + "しない",
        negativePast: base + "しなかった",
        past: base + "した",
        te: base + "して",
        conditionalBa: base + "すれば",
        conditionalTara: base + "したら",
        volitional: base + "しよう",
        imperative: base + "しろ",
        sou: base + "しそう",
        tai: base + "したい",
        zu: base + "せず",
        tari: base + "したり"
      };
      return forms[formId];
    }

    if (kind === "kuru") {
      const writing = representation !== "kana";
      const base = writing ? "来" : "き";
      const negativeBase = writing ? "来" : "こ";
      const forms = {
        dictionary: writing ? "来る" : "くる",
        masuStem: base,
        masu: base + "ます",
        masuNegative: base + "ません",
        masuPast: base + "ました",
        masuNegativePast: base + "ませんでした",
        negative: negativeBase + "ない",
        negativePast: negativeBase + "なかった",
        past: base + "た",
        te: base + "て",
        conditionalBa: (writing ? "来れ" : "くれ") + "ば",
        conditionalTara: base + "たら",
        volitional: (writing ? "来よ" : "こよ") + "う",
        imperative: writing ? "来い" : "こい",
        sou: base + "そう",
        tai: base + "たい",
        zu: (writing ? "来" : "こ") + "ず",
        tari: base + "たり"
      };
      return forms[formId];
    }

    if (kind === "ichidan") {
      const stem = text.slice(0, -1);
      const forms = {
        dictionary: text,
        masuStem: stem,
        masu: stem + "ます",
        masuNegative: stem + "ません",
        masuPast: stem + "ました",
        masuNegativePast: stem + "ませんでした",
        negative: stem + "ない",
        negativePast: stem + "なかった",
        past: stem + "た",
        te: stem + "て",
        conditionalBa: stem + "れば",
        conditionalTara: stem + "たら",
        volitional: stem + "よう",
        imperative: stem + "ろ",
        sou: stem + "そう",
        tai: stem + "たい",
        zu: stem + "ず",
        tari: stem + "たり"
      };
      return forms[formId];
    }

    if (!isGodan(kind)) {
      throw new TypeError("Unsupported verb group: " + kind);
    }

    const ending = text.slice(-1);
    const stem = text.slice(0, -1);
    const iRow = I_ROW[ending];
    const aRow = A_ROW[ending];
    const eRow = E_ROW[ending];
    const oRow = O_ROW[ending];
    if (!iRow || !aRow || !eRow || !oRow) {
      throw new TypeError("Unsupported godan verb ending: " + ending);
    }

    const iStem = stem + iRow;
    const aStem = stem + aRow;
    const forms = {
      dictionary: text,
      masuStem: iStem,
      masu: iStem + "ます",
      masuNegative: iStem + "ません",
      masuPast: iStem + "ました",
      masuNegativePast: iStem + "ませんでした",
      negative: aStem + "ない",
      negativePast: aStem + "なかった",
      past: stem + (kind === "iku" ? "った" : GODAN_PAST[ending]),
      te: stem + (kind === "iku" ? "って" : GODAN_TE[ending]),
      conditionalBa: stem + eRow + "ば",
      conditionalTara: stem + (kind === "iku" ? "った" : GODAN_PAST[ending]) + "ら",
      volitional: stem + oRow + "う",
      imperative: stem + eRow,
      sou: iStem + "そう",
      tai: iStem + "たい",
      zu: aStem + "ず",
      tari: stem + (kind === "iku" ? "った" : GODAN_PAST[ending]) + "り"
    };
    return forms[formId];
  }

  function conjugate(verb, voiceId, inflectionId) {
    if (!verb || typeof verb.dictionary !== "string" || typeof verb.reading !== "string") {
      throw new TypeError("A verb with dictionary form and reading is required.");
    }
    const writtenVerb = deriveVoice(verb.dictionary, verb.kind, voiceId, "written");
    const kanaVerb = deriveVoice(verb.reading, verb.kind, voiceId, "kana");
    return {
      written: inflect(writtenVerb, inflectionId, "written"),
      kana: inflect(kanaVerb, inflectionId, "kana")
    };
  }

  function voiceRule(verb, voiceId) {
    const godan = isGodan(verb.kind);
    const rules = {
      base: "",
      potential: godan
        ? "五段动词词尾改为对应え段，再接「る」。"
        : verb.kind === "ichidan"
          ? "一段动词去掉「る」，再接「られる」。"
          : verb.kind === "suru"
            ? "「する」的可能形是「できる」。"
            : "「来る」的可能形是「来られる（こられる）」。",
      passive: godan
        ? "五段动词词尾改为对应あ段，再接「れる」。"
        : verb.kind === "ichidan"
          ? "一段动词去掉「る」，再接「られる」。"
          : verb.kind === "suru"
            ? "「する」变为「される」。"
            : "「来る」变为「来られる（こられる）」。",
      causative: godan
        ? "五段动词词尾改为对应あ段，再接「せる」。"
        : verb.kind === "ichidan"
          ? "一段动词去掉「る」，再接「させる」。"
          : verb.kind === "suru"
            ? "「する」变为「させる」。"
            : "「来る」变为「来させる（こさせる）」。",
      causativeShort: godan
        ? "五段动词词尾改为对应あ段，再接较简短的「す」。"
        : verb.kind === "ichidan"
          ? "一段动词去掉「る」，再接较简短的「さす」。"
          : verb.kind === "suru"
            ? "「する」变为较简短的「さす」。"
            : "「来る」变为较简短的「来さす（こさす）」。",
      causativePassive: godan
        ? "先形成五段动词使役形，再接「られる」。"
        : verb.kind === "ichidan"
          ? "一段动词去掉「る」，再接「させられる」。"
          : verb.kind === "suru"
            ? "「する」变为「させられる」。"
            : "「来る」变为「来させられる（こさせられる）」。",
      causativePassiveShort: godan
        ? verb.dictionary.endsWith("す")
          ? "「す」结尾的五段动词通常使用「させられる」。"
          : "五段动词词尾改为对应あ段，再接较简短的「される」。"
        : verb.kind === "ichidan"
          ? "一段动词去掉「る」，再接「させられる」。"
          : verb.kind === "suru"
            ? "「する」的短使役受身也常用「させられる」。"
            : "「来る」的短使役受身也常用「来させられる」。"
    };
    return rules[voiceId];
  }

  function explainVoice(verb, voiceId) {
    if (!verb || !VOICE_IDS.has(voiceId)) {
      throw new RangeError("A known verb and voice are required.");
    }
    return voiceId === "base"
      ? "不做语态变化，保留辞书形。"
      : voiceRule(verb, voiceId);
  }

  function inflectionRule(kind, inflectionId, verb, voiceId) {
    if (voiceId === "base" && verb.kind === "iku" && (inflectionId === "te" || inflectionId === "past")) {
      return "「行く」是特殊变化：て形为「行って」，た形为「行った」。";
    }
    if (kind === "suru") {
      const rules = {
        dictionary: "保留辞书形「する」。",
        masuStem: "「する」的连用形是「し」。",
        masu: "连用形「し」加「ます」。",
        masuNegative: "连用形「し」加「ません」。",
        masuPast: "连用形「し」加「ました」。",
        masuNegativePast: "连用形「し」加「ませんでした」。",
        negative: "「する」变为「しない」。",
        negativePast: "「する」变为「しなかった」。",
        past: "「する」变为「した」。",
        te: "「する」变为「して」。",
        conditionalBa: "「する」变为「すれば」。",
        conditionalTara: "た形「した」加「ら」。",
        volitional: "「する」变为「しよう」。",
        imperative: "「する」变为「しろ」。",
        sou: "连用形「し」加「そう」。",
        tai: "连用形「し」加「たい」。",
        zu: "「する」变为「せず」。",
        tari: "た形「した」加「り」。"
      };
      return rules[inflectionId];
    }
    if (kind === "kuru") {
      const rules = {
        dictionary: "保留辞书形「来る（くる）」。",
        masuStem: "「来る」的ます词干读作「き」。",
        masu: "「来る」的ます形读作「きます」。",
        masuNegative: "「来る」的ます否定形读作「きません」。",
        masuPast: "「来る」的ます过去形读作「きました」。",
        masuNegativePast: "「来る」的ます过去否定形读作「きませんでした」。",
        negative: "「来る」的ない形读作「こない」。",
        negativePast: "「来る」的ない过去形读作「こなかった」。",
        past: "「来る」的た形读作「きた」。",
        te: "「来る」的て形读作「きて」。",
        conditionalBa: "「来る」变为「くれば」。",
        conditionalTara: "た形「来た」加「ら」。",
        volitional: "「来る」变为「来よう（こよう）」。",
        imperative: "「来る」变为「来い（こい）」。",
        sou: "ます词干读作「き」，再接「そう」。",
        tai: "ます词干读作「き」，再接「たい」。",
        zu: "「来る」变为「来ず（こず）」。",
        tari: "た形「来た」加「り」。"
      };
      return rules[inflectionId];
    }

    if (kind === "ichidan") {
      const rules = {
        dictionary: "保持辞书形；这是其它活用的基础。",
        masuStem: "去掉词尾「る」，得到连用形。",
        masu: "去掉「る」后接「ます」。",
        masuNegative: "去掉「る」后接「ません」。",
        masuPast: "去掉「る」后接「ました」。",
        masuNegativePast: "去掉「る」后接「ませんでした」。",
        negative: "去掉「る」后接「ない」。",
        negativePast: "去掉「る」后接「なかった」。",
        past: "去掉「る」后接「た」。",
        te: "去掉「る」后接「て」。",
        conditionalBa: "去掉「る」后接「れば」。",
        conditionalTara: "先变成た形，再接「ら」。",
        volitional: "去掉「る」后接「よう」。",
        imperative: "去掉「る」后接「ろ」。",
        sou: "去掉「る」得到ます词干，再接「そう」。",
        tai: "去掉「る」得到ます词干，再接「たい」。",
        zu: "去掉「る」后接「ず」。",
        tari: "先变成た形，再接「り」。"
      };
      return rules[inflectionId];
    }

    const rules = {
      dictionary: "保持辞书形；这是其它活用的基础。",
      masuStem: "把词尾换成对应的い段，得到连用形。",
      masu: "把词尾换成对应的い段，再接「ます」。",
      masuNegative: "把词尾换成对应的い段，再接「ません」。",
      masuPast: "把词尾换成对应的い段，再接「ました」。",
      masuNegativePast: "把词尾换成对应的い段，再接「ませんでした」。",
      negative: "把词尾换成对应的あ段，再接「ない」；「う」要变成「わ」。",
      negativePast: "把词尾换成对应的あ段，再接「なかった」；「う」要变成「わ」。",
      past: "根据五段动词词尾发生音便，再接成た形。",
      te: "根据五段动词词尾发生音便，再接成て形。",
      conditionalBa: "把词尾换成对应的え段，再接「ば」。",
      conditionalTara: "先变成た形，再接「ら」。",
      volitional: "把词尾换成对应的お段，再接「う」。",
      imperative: "把词尾换成对应的え段。",
      sou: "把词尾换成对应的い段，再接「そう」。",
      tai: "把词尾换成对应的い段，再接「たい」。",
      zu: "把词尾换成对应的あ段，再接「ず」；「う」要变成「わ」。",
      tari: "先变成た形，再接「り」。"
    };
    return rules[inflectionId];
  }

  function explain(verb, voiceId, inflectionId) {
    if (!verb || !VOICE_IDS.has(voiceId) || !INFLECTION_IDS.has(inflectionId)) {
      throw new RangeError("A known verb form and inflection are required.");
    }
    const derived = deriveVoice(verb.dictionary, verb.kind, voiceId, "written");
    const baseRule = inflectionRule(derived.kind, inflectionId, verb, voiceId);
    const voice = VOICES.find(function (item) { return item.id === voiceId; });
    const inflection = INFLECTIONS.find(function (form) { return form.id === inflectionId; });
    if (voiceId === "base") {
      return "变「" + inflection.label + "」：" + baseRule;
    }
    return "先变「" + voice.label + "」：" + explainVoice(verb, voiceId)
      + "\n再变「" + inflection.label + "」：" + baseRule;
  }

  function toHiraganaFromRomaji(input) {
    const source = String(input).normalize("NFKC").toLowerCase().replace(/\s/g, "");
    let result = "";
    let index = 0;

    while (index < source.length) {
      const char = source[index];
      if (/\s/.test(char) || char === "'") {
        index += 1;
        continue;
      }

      if (char === "n") {
        const next = source[index + 1];
        const afterNext = source[index + 2];
        if (next === "'") {
          result += "ん";
          index += 2;
          continue;
        }
        if (next === "n") {
          result += "ん";
          if (!afterNext || /[bcdfghjklmpqrstvwxyz]/.test(afterNext)) {
            index += 2;
          } else {
            index += 1;
          }
          continue;
        }
        if (!next || (/[bcdfghjklmpqrstvwxyz]/.test(next) && next !== "y")) {
          result += "ん";
          index += 1;
          continue;
        }
      }

      if (/[bcdfghjklmpqrstvwxyz]/.test(char)
        && char !== "n"
        && source[index + 1] === char) {
        result += "っ";
        index += 1;
        continue;
      }

      const three = source.slice(index, index + 3);
      const two = source.slice(index, index + 2);
      if (Object.prototype.hasOwnProperty.call(ROMAJI, three)) {
        result += ROMAJI[three];
        index += 3;
      } else if (Object.prototype.hasOwnProperty.call(ROMAJI, two)) {
        result += ROMAJI[two];
        index += 2;
      } else if (Object.prototype.hasOwnProperty.call(ROMAJI, char)) {
        result += ROMAJI[char];
        index += 1;
      } else {
        return source;
      }
    }
    return result;
  }

  // 答案归一化管线：NFKC + 去空白 -> 去首尾中英标点 -> 片假名转平假名
  // -> 长音符号展开 -> 纯罗马字输入转假名。各步对空串与异常输入均安全。
  const LEADING_PUNCTUATION = /^[\u0021-\u002f\u003a-\u0040\u005b-\u0060\u007b-\u007e\u3001\u3002\u00b7\u2014\u2026\u300c-\u300f\u3010\u3011\uff01\uff0c\uff0e\uff1a\uff1b\uff1f]+/;
  const TRAILING_PUNCTUATION = /[\u0021-\u002f\u003a-\u0040\u005b-\u0060\u007b-\u007e\u3001\u3002\u00b7\u2014\u2026\u300c-\u300f\u3010\u3011\uff01\uff0c\uff0e\uff1a\uff1b\uff1f]+$/;
  const MACRON_EXPANSIONS = {
    "\u0101": "aa", "\u0113": "ee", "\u012b": "ii",
    "\u014d": "ou", "\u016b": "uu"
  };

  function normalizeText(value) {
    return String(value).normalize("NFKC").replace(/[\s\u3000]/g, "");
  }

  function katakanaToHiragana(text) {
    return text.replace(/[\u30a1-\u30f6]/g, function (char) {
      return String.fromCharCode(char.charCodeAt(0) - 0x60);
    });
  }

  function expandMacrons(text) {
    return text.replace(/[\u0101\u0113\u012b\u014d\u016b]/g, function (char) {
      return MACRON_EXPANSIONS[char];
    });
  }

  function normalizeAnswer(value) {
    const stripped = normalizeText(value)
      .replace(LEADING_PUNCTUATION, "")
      .replace(TRAILING_PUNCTUATION, "");
    const normalized = expandMacrons(katakanaToHiragana(stripped));
    return /^[a-z']+$/i.test(normalized)
      ? toHiraganaFromRomaji(normalized)
      : normalized;
  }

  const api = {
    VOICES: VOICES,
    INFLECTIONS: INFLECTIONS,
    VERBS: VERBS,
    conjugate: conjugate,
    explainVoice: explainVoice,
    explain: explain,
    toHiraganaFromRomaji: toHiraganaFromRomaji,
    normalizeText: normalizeText,
    normalizeAnswer: normalizeAnswer
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  root.JapaneseVerbEngine = api;
})(globalThis);
