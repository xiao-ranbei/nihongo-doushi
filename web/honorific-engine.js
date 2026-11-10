// 敬语引擎：三层敬语（尊敬語／謙譲語／丁寧語）的规则派生、特殊动词表、
// 场景判定与二重敬语检测。
//
// 边界与 verb-engine 一致：纯逻辑，禁 DOM、禁浏览器存储、禁网络、零随机。
// 依赖 verb-engine 的 conjugate / normalizeAnswer，不重复实现活用规则。
//
// 数据形态约定：每个敬语形式带四套写法 —— 辞书形与ます形的「汉字写法 / 假名写法」，
// 因为判题要同时接受「召し上がります」与「めしあがります」，两者无法互相推导。

(function (root) {
  "use strict";

  const verbEngine = root.JapaneseVerbEngine
    || (typeof require === "function" ? require("./verb-engine.js") : null);
  if (!verbEngine) {
    throw new Error("honorific-engine 需要先加载 verb-engine.js");
  }

  // ---------- 三层敬语 ----------

  const LEVELS = [
    {
      id: "sonkeigo",
      label: "尊敬語",
      topic: "抬高对方的动作",
      question: "这个动作是对方或上位的人做的吗？",
      pattern: "お + ます词干 + になる ／ ご + 漢語 + になる",
      meaning: "把对方或上位者的动作抬高来表示敬意；动作主体不能是说话者自己。"
    },
    {
      id: "kenjougo",
      label: "謙譲語",
      topic: "压低自己的动作",
      question: "这个动作是自己或己方做的吗？",
      pattern: "お + ます词干 + する（更礼貌：いたす） ／ ご + 漢語 + する",
      meaning: "把自己或己方的动作说低，从而抬高对方；动作主体是说话者一侧。"
    },
    {
      id: "teineigo",
      label: "丁寧語",
      topic: "把话说得体面",
      question: "没有需要抬高或压低的具体动作吗？",
      pattern: "ます形（です・ます・ございます）",
      meaning: "不改变动作归属，只让语气礼貌；对任何人都可以用。"
    }
  ];

  const LEVEL_IDS = new Set(LEVELS.map(function (level) { return level.id; }));

  // ---------- 特殊敬语动词 ----------
  // 这些词的敬语形不能由规则推导，必须整词记忆；规则形对它们不成立或明显生硬，
  // 因此不参与判题（ruleAvailable 恒 false）。

  function form(written, kana, masu, masuKana) {
    return { written: written, kana: kana, masu: masu, masuKana: masuKana };
  }

  const SPECIAL_HONORIFIC = {
    iku: {
      sonkeigo: [
        form("いらっしゃる", "いらっしゃる", "いらっしゃいます", "いらっしゃいます"),
        form("おいでになる", "おいでになる", "おいでになります", "おいでになります")
      ],
      kenjougo: [
        form("参る", "まいる", "参ります", "まいります"),
        form("伺う", "うかがう", "伺います", "うかがいます")
      ]
    },
    kuru: {
      sonkeigo: [
        form("いらっしゃる", "いらっしゃる", "いらっしゃいます", "いらっしゃいます"),
        form("お見えになる", "おみえになる", "お見えになります", "おみえになります")
      ],
      kenjougo: [form("参る", "まいる", "参ります", "まいります")]
    },
    iru: {
      sonkeigo: [
        form("いらっしゃる", "いらっしゃる", "いらっしゃいます", "いらっしゃいます"),
        form("おいでになる", "おいでになる", "おいでになります", "おいでになります")
      ],
      kenjougo: [form("おる", "おる", "おります", "おります")]
    },
    taberu: {
      sonkeigo: [form("召し上がる", "めしあがる", "召し上がります", "めしあがります")],
      kenjougo: [form("いただく", "いただく", "いただきます", "いただきます")]
    },
    nomu: {
      sonkeigo: [form("召し上がる", "めしあがる", "召し上がります", "めしあがります")],
      kenjougo: [form("いただく", "いただく", "いただきます", "いただきます")]
    },
    miru: {
      sonkeigo: [form("ご覧になる", "ごらんになる", "ご覧になります", "ごらんになります")],
      kenjougo: [form("拝見する", "はいけんする", "拝見します", "はいけんします")]
    },
    iu: {
      sonkeigo: [form("おっしゃる", "おっしゃる", "おっしゃいます", "おっしゃいます")],
      kenjougo: [
        form("申す", "もうす", "申します", "もうします"),
        form("申し上げる", "もうしあげる", "申し上げます", "もうしあげます")
      ]
    },
    suru: {
      sonkeigo: [form("なさる", "なさる", "なさいます", "なさいます")],
      kenjougo: [form("いたす", "いたす", "いたします", "いたします")]
    },
    shiru: {
      sonkeigo: [form("ご存じです", "ごぞんじです", "ご存じです", "ごぞんじです")],
      kenjougo: [
        form("存じる", "ぞんじる", "存じております", "ぞんじております")
      ]
    },
    morau: {
      sonkeigo: [],
      kenjougo: [
        form("いただく", "いただく", "いただきます", "いただきます"),
        form("頂戴する", "ちょうだいする", "頂戴します", "ちょうだいします")
      ]
    },
    ageru: {
      sonkeigo: [],
      kenjougo: [form("差し上げる", "さしあげる", "差し上げます", "さしあげます")]
    },
    kureru: {
      sonkeigo: [form("くださる", "くださる", "くださいます", "くださいます")],
      kenjougo: []
    },
    kiku: {
      sonkeigo: [],
      kenjougo: [
        form("伺う", "うかがう", "伺います", "うかがいます"),
        form("拝聴する", "はいちょうする", "拝聴します", "はいちょうします")
      ]
    },
    au: {
      sonkeigo: [],
      kenjougo: [form("お目にかかる", "おめにかかる", "お目にかかります", "おめにかかります")]
    },
    shinu: {
      sonkeigo: [form("お亡くなりになる", "おなくなりになる", "お亡くなりになります", "おなくなりになります")],
      kenjougo: []
    }
  };

  // ---------- 不宜接敬语的动词 ----------
  // 敬语只对可控动作成立。这些词的动作主体不是「人有意为之」，
  // 强行套「お〜になる」会得到不自然的句子，因此只出丁寧語并说明原因。

  const LIMITED_VERBS = {
    furu: "天气现象，没有可控的行为主体；对上级改说「降っておいでです」等。",
    saku: "花木的状态变化，不接「お〜になる」；可用「咲いておいでです」。",
    wakaru: "表示状态而非可控动作；对上级多用「ご理解いただく」。",
    mitsukaru: "主体是物品，不能抬高动作；直接用「見つかりました」。",
    hajimaru: "主体是事件或活动，不用尊敬語；改说「開始される」。",
    owaru: "主体是事件或活动，不用尊敬語；改说「終了する」。",
    aku: "主体是门、窗、店等物件，没有行为主体，不用敬语。",
    shimaru: "主体是门、窗、店等物件，没有行为主体，不用敬语。"
  };

  // ---------- 六个使用场景 ----------

  const SCENES = [
    { id: "work", label: "职场·上司", who: "部下 → 上司", note: "对上司的动作抬高，自己的动作压低。" },
    { id: "client", label: "职场·客户", who: "社内 → 社外", note: "对外部的人，本公司同事也算「己方」，用謙譲語。" },
    { id: "shop", label: "店·服务", who: "店员 ↔ 顾客", note: "店员对顾客全用尊敬語；顾客对店员用丁寧語即可。" },
    { id: "phone", label: "电话·邮件", who: "对上级或客户", note: "开头寒暄用丁寧語，自述用謙譲語。" },
    { id: "visit", label: "拜访·接待", who: "访客 ↔ 主人", note: "访客自述用謙譲語，主人对访客用尊敬語。" },
    { id: "daily", label: "日常礼貌", who: "邻居·陌生人", note: "丁寧語为主，少量尊敬語。" }
  ];

  // ---------- 场景例句（手写，不联网、不调用模型） ----------

  const EXAMPLES = [
    { id: "w1", sceneId: "work", levelId: "sonkeigo", verbId: "miru",
      jp: "部長、こちらの資料をご覧になりましたか。", kana: "ぶちょう、こちらのしりょうをごらんになりましたか。",
      zh: "部长，您看过这份资料了吗？", who: "部下 → 上司" },
    { id: "w2", sceneId: "work", levelId: "kenjougo", verbId: "setsumei-suru",
      jp: "明日の資料は私がご説明いたします。", kana: "あしたのしりょうはわたしがごせつめいいたします。",
      zh: "明天的资料由我来说明。", who: "部下 → 上司" },
    { id: "w3", sceneId: "work", levelId: "kenjougo", verbId: "iru",
      jp: "課長はただいま席を外しております。", kana: "かちょうはただいませきをはずしております。",
      zh: "科长现在不在座位上。", who: "部下 → 客户" },
    { id: "w4", sceneId: "work", levelId: "sonkeigo", verbId: "modoru",
      jp: "部長は何時ごろお戻りになりますか。", kana: "ぶちょうはなんじごろおもどりになりますか。",
      zh: "部长大概几点回来？", who: "部下 → 上司" },

    { id: "c1", sceneId: "client", levelId: "teineigo",
      jp: "いつもお世話になっております。", kana: "いつもおせわになっております。",
      zh: "承蒙您关照。", who: "社内 → 社外" },
    { id: "c2", sceneId: "client", levelId: "kenjougo", verbId: "iku",
      jp: "弊社の田中が明日そちらに伺います。", kana: "へいしゃのたなかがあしたそちらにうかがいます。",
      zh: "本公司田中明天去贵处拜访。", who: "社内 → 社外" },
    { id: "c3", sceneId: "client", levelId: "kenjougo", verbId: "okuru",
      jp: "資料は明日お送りいたします。", kana: "しりょうはあしたおおくりいたします。",
      zh: "资料明天寄出。", who: "社内 → 社外" },
    { id: "c4", sceneId: "client", levelId: "sonkeigo",
      jp: "ご不明な点がございましたら、お申し付けください。", kana: "ごふめいなてんがございましたら、おもうしつけください。",
      zh: "如有不明之处，请告知。", who: "社内 → 社外" },

    { id: "s1", sceneId: "shop", levelId: "sonkeigo", verbId: "taberu",
      jp: "こちらでお召し上がりになりますか。", kana: "こちらでおめしあがりになりますか。",
      zh: "您要在这儿用餐吗？", who: "店员 → 顾客" },
    { id: "s2", sceneId: "shop", levelId: "sonkeigo", verbId: "matsu",
      jp: "少々お待ちください。", kana: "しょうしょうおまちください。",
      zh: "请稍等。", who: "店员 → 顾客" },
    { id: "s3", sceneId: "shop", levelId: "kenjougo", verbId: "motsu",
      jp: "お荷物は私がお持ちいたします。", kana: "おにもつはわたしがおもちいたします。",
      zh: "行李我来拿。", who: "店员 → 顾客" },
    { id: "s4", sceneId: "shop", levelId: "sonkeigo",
      jp: "こちらがおすすめのお料理でございます。", kana: "こちらがおすすめのおりょうりでございます。",
      zh: "这是推荐的菜品。", who: "店员 → 顾客" },

    { id: "p1", sceneId: "phone", levelId: "teineigo",
      jp: "株式会社言の葉の田中でございます。", kana: "かぶしきがいしゃことのはのたなかでございます。",
      zh: "我是言の叶公司的田中。", who: "对外 → 对方" },
    { id: "p2", sceneId: "phone", levelId: "kenjougo", verbId: "iru",
      jp: "田中はただいま外出しております。", kana: "たなかはただいまがいしゅつしております。",
      zh: "田中现在外出了。", who: "对内 → 对外" },
    { id: "p3", sceneId: "phone", levelId: "kenjougo", verbId: "denwa-suru",
      jp: "折り返しお電話いたします。", kana: "おりかえしおでんわいたします。",
      zh: "我稍后回电话。", who: "对外 → 对方" },
    { id: "p4", sceneId: "phone", levelId: "sonkeigo", verbId: "kakunin-suru",
      jp: "メールをご確認いただけますか。", kana: "めーるをごかくにんいただけますか。",
      zh: "能请您确认一下邮件吗？", who: "对外 → 对方" },

    { id: "v1", sceneId: "visit", levelId: "kenjougo",
      jp: "本日はお忙しいところ、お時間をいただきありがとうございます。", kana: "ほんじつはおいそがしいところ、おじかんをいただきありがとうございます。",
      zh: "感谢您百忙之中抽时间。", who: "访客 → 主人" },
    { id: "v2", sceneId: "visit", levelId: "sonkeigo",
      jp: "どうぞお上がりください。", kana: "どうぞおあがりください。",
      zh: "请进。", who: "主人 → 访客" },
    { id: "v3", sceneId: "visit", levelId: "kenjougo", verbId: "iku",
      jp: "また改めて伺います。", kana: "またあらためてうかがいます。",
      zh: "我改日再来拜访。", who: "访客 → 主人" },
    { id: "v4", sceneId: "visit", levelId: "sonkeigo", verbId: "matsu",
      jp: "先生はこちらでお待ちになっております。", kana: "せんせいはこちらでおまちになっております。",
      zh: "老师在那边等着。", who: "接待者 → 访客" },

    { id: "d1", sceneId: "daily", levelId: "teineigo", verbId: "iku",
      jp: "すみません、駅までどうやって行きますか。", kana: "すみません、えきまでどうやっていきますか。",
      zh: "请问，怎么去车站？", who: "陌生人 → 陌生人" },
    { id: "d2", sceneId: "daily", levelId: "teineigo",
      jp: "お先に失礼します。", kana: "おさきにしつれいします。",
      zh: "我先走了。", who: "同事 → 同事" },
    { id: "d3", sceneId: "daily", levelId: "sonkeigo", verbId: "kau-tsukau",
      jp: "よろしければ、こちらをお使いください。", kana: "よろしければ、こちらをおつかいください。",
      zh: "如果不介意，请用这个。", who: "邻居 → 邻居" },
    { id: "d4", sceneId: "daily", levelId: "teineigo",
      jp: "お忙しいところ、すみません。", kana: "おいそがしいところ、すみません。",
      zh: "不好意思，打扰了。", who: "日常 → 对方" }
  ];

  // ---------- 二重敬语 ----------

  // severity：error = 明确错误；warning = 说法冗余、商务场合常被指摘。
  const DOUBLE_PATTERNS = [
    { pattern: /おっしゃられ/, phrase: "おっしゃられる", suggestion: "おっしゃる",
      reason: "「おっしゃる」本身已是尊敬語，不能再接「られる」。", severity: "error" },
    { pattern: /ご覧になられ/, phrase: "ご覧になられる", suggestion: "ご覧になる",
      reason: "「ご覧になる」已是尊敬語，叠加「られる」构成二重敬语。", severity: "error" },
    { pattern: /お召し上がりになられ|召し上がられ/, phrase: "お召し上がりになられる", suggestion: "召し上がる",
      reason: "「召し上がる」已是尊敬語，不能再叠加。", severity: "error" },
    { pattern: /お亡くなりになられ/, phrase: "お亡くなりになられる", suggestion: "お亡くなりになる",
      reason: "「お亡くなりになる」已是尊敬語。", severity: "error" },
    { pattern: /なさられ/, phrase: "なさられる", suggestion: "なさる",
      reason: "「なさる」是「する」的尊敬語，不再接「られる」。", severity: "error" },
    { pattern: /くださられ/, phrase: "くださられる", suggestion: "くださる",
      reason: "「くださる」已是尊敬語。", severity: "error" },
    { pattern: /拝見させていただ/, phrase: "拝見させていただきます", suggestion: "拝見します",
      reason: "「拝見する」已含謙譲，再叠「させていただく」是双重谦让。", severity: "warning" },
    { pattern: /お伺いいたし|お伺いし/, phrase: "お伺いいたします", suggestion: "伺います／お伺いします",
      reason: "「伺う」已含謙譲，「お」与「いたす」二者择一即可。", severity: "warning" },
    { pattern: /申し上げられ/, phrase: "申し上げられる", suggestion: "申し上げる",
      reason: "「申し上げる」是謙譲語，不能再用「られる」抬高。", severity: "error" },
    { pattern: /ご存じでいらっしゃ/, phrase: "ご存じでいらっしゃいます", suggestion: "ご存じです",
      reason: "「ご存じ」已含敬意，「いらっしゃる」属于叠加。", severity: "warning" }
  ];

  // ---------- 规则派生 ----------

  function levelById(levelId) {
    return LEVELS.filter(function (level) { return level.id === levelId; })[0] || null;
  }

  function isLimited(verbId) {
    return Object.prototype.hasOwnProperty.call(LIMITED_VERBS, verbId);
  }

  // 返回 null 表示「该动词没有这一层的特殊形，应走规则派生」；
  // 返回空数组表示「该动词这一层没有固定对应说法」。
  function specialForms(verbId, levelId) {
    const entry = SPECIAL_HONORIFIC[verbId];
    if (!entry || !Object.prototype.hasOwnProperty.call(entry, levelId)) {
      return null;
    }
    const forms = entry[levelId];
    return Array.isArray(forms) ? forms : [];
  }

  // 规则形：和語 → お + ます词干 + になる／する；サ変 → ご + 漢語 + になる／する。
  // 特殊敬语动词与「来る」不生成规则形（お来になる 等不成立）。
  function buildRuleForm(verb, levelId) {
    if (levelId === "teineigo") {
      const masu = verbEngine.conjugate(verb, "base", "masu");
      return {
        written: masu.written, kana: masu.kana,
        masu: masu.written, masuKana: masu.kana,
        polite: null
      };
    }

    if (SPECIAL_HONORIFIC[verb.id] || verb.kind === "kuru" || verb.kind === "iku") {
      return null;
    }

    const honorificSuffix = levelId === "sonkeigo" ? "になる" : "する";
    const masuSuffix = levelId === "sonkeigo" ? "になります" : "します";

    if (verb.kind === "suru") {
      const base = verb.dictionary.slice(0, -2);
      const baseKana = verb.reading.slice(0, -2);
      return {
        written: "ご" + base + honorificSuffix,
        kana: "ご" + baseKana + honorificSuffix,
        masu: "ご" + base + masuSuffix,
        masuKana: "ご" + baseKana + masuSuffix,
        polite: levelId === "kenjougo"
          ? {
            written: "ご" + base + "いたします",
            kana: "ご" + baseKana + "いたします",
            masu: "ご" + base + "いたします",
            masuKana: "ご" + baseKana + "いたします"
          }
          : null
      };
    }

    const stem = verbEngine.conjugate(verb, "base", "masuStem");
    return {
      written: "お" + stem.written + honorificSuffix,
      kana: "お" + stem.kana + honorificSuffix,
      masu: "お" + stem.written + masuSuffix,
      masuKana: "お" + stem.kana + masuSuffix,
      polite: levelId === "kenjougo"
        ? {
          written: "お" + stem.written + "いたします",
          kana: "お" + stem.kana + "いたします",
          masu: "お" + stem.written + "いたします",
          masuKana: "お" + stem.kana + "いたします"
        }
        : null
    };
  }

  // 返回一个动词在某层敬语下的全部可用形式。
  // available=false 表示该词不适合用这一层（如自然现象用尊敬語），reason 说明原因。
  function getHonorificForms(verb, levelId) {
    if (!verb || !LEVEL_IDS.has(levelId)) {
      throw new RangeError("A known verb and honorific level are required.");
    }

    const level = levelById(levelId);
    const limitedReason = LIMITED_VERBS[verb.id];
    if (limitedReason && levelId !== "teineigo") {
      return {
        levelId: levelId,
        level: level,
        available: false,
        isSpecial: false,
        primary: null,
        alternatives: [],
        ruleForm: null,
        reason: limitedReason
      };
    }

    const special = specialForms(verb.id, levelId);
    const ruleForm = special && special.length ? null : buildRuleForm(verb, levelId);

    if (special && special.length) {
      return {
        levelId: levelId,
        level: level,
        available: true,
        isSpecial: true,
        primary: special[0],
        alternatives: special.slice(1),
        ruleForm: null,
        reason: ""
      };
    }

    if (special && !special.length) {
      return {
        levelId: levelId,
        level: level,
        available: false,
        isSpecial: true,
        primary: null,
        alternatives: [],
        ruleForm: null,
        reason: "「" + verb.dictionary + "」在" + level.label + "里没有固定的单一对应说法，"
          + "实际表达要按语境换用别的句式。"
      };
    }

    if (!ruleForm) {
      return {
        levelId: levelId,
        level: level,
        available: false,
        isSpecial: false,
        primary: null,
        alternatives: [],
        ruleForm: null,
        reason: "「" + verb.dictionary + "」的活用不能套用「" + level.pattern + "」，需整词记忆。"
      };
    }

    return {
      levelId: levelId,
      level: level,
      available: true,
      isSpecial: false,
      primary: {
        written: ruleForm.written, kana: ruleForm.kana,
        masu: ruleForm.masu, masuKana: ruleForm.masuKana
      },
      alternatives: ruleForm.polite ? [ruleForm.polite] : [],
      ruleForm: ruleForm,
      reason: ""
    };
  }

  function collectAnswers(forms) {
    const seen = new Set();
    const answers = [];
    const all = [forms.primary].concat(forms.alternatives);
    all.forEach(function (item) {
      [item.masu, item.masuKana].forEach(function (text) {
        const normalized = verbEngine.normalizeAnswer(text);
        if (text && !seen.has(normalized)) {
          seen.add(normalized);
          answers.push(text);
        }
      });
    });
    return answers;
  }

  // 练习题：题干是动词 + 敬语层，答案是ます形（敬语几乎只在礼貌体里使用）。
  // 特殊动词的辞书形同时列出，供反馈面板展示「原形是什么」。
  function buildQuestion(verb, levelId) {
    const forms = getHonorificForms(verb, levelId);
    if (!forms.available) {
      return null;
    }
    const level = forms.level;
    const answer = forms.primary.masu;
    const explanation = forms.isSpecial
      ? "「" + verb.dictionary + "」的" + level.label + "是特殊说法「" + forms.primary.written
        + "」，不按「" + level.pattern + "」变化，需要整词记忆。"
      : "「" + verb.dictionary + "」按规则变化：" + level.pattern + "，得到「" + forms.primary.written + "」。";

    return {
      key: "hon::" + verb.id + "::" + levelId,
      verbId: verb.id,
      levelId: levelId,
      levelLabel: level.label,
      verbDictionary: verb.dictionary,
      verbReading: verb.reading,
      verbMeaning: verb.meaning,
      prompt: "把「" + verb.dictionary + "」变成对对方说的" + level.label + "（ます形）",
      answers: collectAnswers(forms),
      display: answer,
      displayKana: forms.primary.masuKana,
      alternatives: forms.alternatives.map(function (item) { return item.masu; }),
      isSpecial: forms.isSpecial,
      explanation: explanation,
      levelMeaning: level.meaning
    };
  }

  function checkAnswer(question, input) {
    if (!question) {
      throw new TypeError("A question is required.");
    }
    const normalized = verbEngine.normalizeAnswer(input);
    const correct = question.answers.some(function (answer) {
      return verbEngine.normalizeAnswer(answer) === normalized && normalized.length > 0;
    });
    return { correct: correct, normalized: normalized };
  }

  // ---------- 场景判定 ----------

  const SUBJECTS = [
    { id: "self", label: "我／己方" },
    { id: "other", label: "对方（听话人）" },
    { id: "third", label: "第三方" }
  ];

  // 三问判定：主体 → 敬语层。inGroup 表示听话人是公司外部的人，
  // 此时「本公司的人」即使职位更高也算己方，这是敬语最容易错的一条。
  function judgeLevel(input) {
    const subject = input && input.subject;
    const inGroup = !!(input && input.inGroup);

    if (subject === "self") {
      return {
        levelId: "kenjougo",
        subject: subject,
        reason: "动作由自己或己方做，用謙譲語把自己的动作说低，从而抬高对方。"
      };
    }
    if (subject === "other") {
      return {
        levelId: "sonkeigo",
        subject: subject,
        reason: "动作由听话人做，用尊敬語抬高对方的动作。"
      };
    }
    if (subject === "third") {
      if (inGroup) {
        return {
          levelId: "kenjougo",
          subject: subject,
          reason: "听话人是公司外部的人，本公司的人属于「己方」，"
            + "即使对方是上司也用謙譲語（如「部長が申しておりました」）。"
        };
      }
      return {
        levelId: "sonkeigo",
        subject: subject,
        reason: "动作由第三方做，对听话人表示敬意时用尊敬語。"
      };
    }
    return {
      levelId: "teineigo",
      subject: subject,
      reason: "没有需要抬高或压低的具体动作，用丁寧語把话说得体面即可。"
    };
  }

  // ---------- 二重敬语检测 ----------

  function checkDoubleHonorific(text) {
    const source = String(text || "");
    const issues = [];
    DOUBLE_PATTERNS.forEach(function (entry) {
      if (entry.pattern.test(source)) {
        issues.push({
          phrase: entry.phrase,
          suggestion: entry.suggestion,
          reason: entry.reason,
          severity: entry.severity
        });
      }
    });
    return { ok: issues.length === 0, issues: issues };
  }

  // ---------- 查询辅助 ----------

  function getExamplesByScene(sceneId) {
    if (!sceneId || sceneId === "all") {
      return EXAMPLES.slice();
    }
    return EXAMPLES.filter(function (example) { return example.sceneId === sceneId; });
  }

  function explainLevel(levelId) {
    const level = levelById(levelId);
    if (!level) {
      throw new RangeError("Unknown honorific level: " + levelId);
    }
    return level.label + "：" + level.meaning + "\n构成：" + level.pattern;
  }

  const api = {
    LEVELS: LEVELS,
    SCENES: SCENES,
    SUBJECTS: SUBJECTS,
    SPECIAL_HONORIFIC: SPECIAL_HONORIFIC,
    LIMITED_VERBS: LIMITED_VERBS,
    EXAMPLES: EXAMPLES,
    DOUBLE_PATTERNS: DOUBLE_PATTERNS,
    levelById: levelById,
    isLimited: isLimited,
    getHonorificForms: getHonorificForms,
    buildQuestion: buildQuestion,
    checkAnswer: checkAnswer,
    judgeLevel: judgeLevel,
    checkDoubleHonorific: checkDoubleHonorific,
    getExamplesByScene: getExamplesByScene,
    explainLevel: explainLevel
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  root.JapaneseHonorificEngine = api;
})(globalThis);
