(function () {
  "use strict";

  function injectFontFaces() {
    const fonts = Array.isArray(window.JapaneseFontManifest)
      ? window.JapaneseFontManifest
      : [];
    if (!fonts.length) {
      return;
    }
    const style = document.createElement("style");
    style.id = "dynamic-font-faces";
    style.textContent = fonts.map(function (font) {
      return '@font-face {'
        + ' font-family: "' + font.family + '";'
        + ' src: url("' + font.src + '") format("' + (font.format || "truetype") + '");'
        + ' font-weight: ' + (font.weight || "normal") + ';'
        + ' font-style: ' + (font.style || "normal") + ';'
        + ' font-display: ' + (font.display || "swap") + ';'
        + ' }';
    }).join("\n");
    document.head.append(style);
  }

  injectFontFaces();

  const engine = window.JapaneseVerbEngine;
  if (!engine) {
    throw new Error("The verb conjugation engine did not load.");
  }

  const adjEngine = window.JapaneseAdjectiveEngine;
  if (!adjEngine) {
    throw new Error("The adjective engine did not load.");
  }

  const particleEngine = window.JapaneseParticleEngine;
  if (!particleEngine) {
    throw new Error("The Japanese particle learning engine did not load.");
  }

  const VOICES = engine.VOICES;
  const INFLECTIONS = engine.INFLECTIONS;
  const VERBS = engine.VERBS;
  const VERB_BY_ID = new Map(VERBS.map(function (verb) { return [verb.id, verb]; }));
  const VOICE_BY_ID = new Map(VOICES.map(function (voice) { return [voice.id, voice]; }));
  const INFLECTION_BY_ID = new Map(INFLECTIONS.map(function (form) { return [form.id, form]; }));
  const LEVELS = [];
  const STORAGE_KEY = "japanese-verb-practice-mistakes-v1";
  const STATS_STORAGE_KEY = "japanese-verb-practice-stats-v1";
  const REVIEW_INTERVALS_DAYS = [1, 3, 7, 14, 30];
  const MASTERY_CORRECT_STREAK = REVIEW_INTERVALS_DAYS.length + 1;
  const SESSION_RETRY_AFTER_ANSWERS = 4;
  // 错题加权：到期题与重练队列按错次加权抽取，高频错题更早重现。
  // 权重按错次封顶，避免反复答错的题彻底挤占其他错题的复习机会。
  const MAX_MISTAKE_WEIGHT = 3;
  const GROUP_LABELS = {
    godan: "五段动词",
    ichidan: "一段动词",
    irregular: "不规则动词"
  };
  const LEVEL_LABELS = {
    basic: "基础活用",
    advanced: "进阶活用"
  };
  // 速查表分组行需要跨全部列；列数以 index.html 表头为准（活用形式/变形结果/
  // 常见表达大意/变化提示/练一练），改表头时同步这里。
  const REFERENCE_COLUMN_COUNT = 5;
  const MORPHOLOGY_FILTER_PREFIX = "morphology:";
  const VERB_MORPHOLOGY_FORMS = [
    {
      id: "mizen",
      label: "未然形",
      usage: "接「ない」「れる／せる」等；意向形也接「う／よう」。五段动词会依接续出现「あ段／お段」两路；未然形不等于完整的ない形。",
      examples: [
        { inflectionId: "negative", label: "接「ない」" },
        { inflectionId: "volitional", label: "接「う／よう」" }
      ],
      targetLabel: "未然形（接ない）",
      instruction: "请写出接「ない」时使用的<strong class=\"keyword-highlight\">未然形</strong>，并组成完整的ない形。",
      practiceInflectionId: "negative",
      practiceLabel: "练ない形"
    },
    {
      id: "renyou",
      label: "連用形",
      usage: "连接「ます」「たい」等；接「て／た」时常先发生音便。词干通常不单独作完整句子。",
      examples: [
        { inflectionId: "masuStem", label: "ます词干" },
        { inflectionId: "masu", label: "接「ます」" },
        { inflectionId: "te", label: "音便后接「て」" },
        { inflectionId: "past", label: "音便后接「た」" }
      ],
      targetLabel: "連用形（ます词干）",
      instruction: "请写出接「ます」的<strong class=\"keyword-highlight\">連用形</strong>，只写ます词干。",
      practiceInflectionId: "masuStem",
      practiceLabel: "练ます词干"
    },
    {
      id: "shushi",
      label: "終止形",
      usage: "用于句尾作陈述；现代动词的辞书形常作終止形。",
      examples: [{ inflectionId: "dictionary", label: "句尾陈述", suffix: "。" }],
      targetLabel: "終止形（句尾）",
      instruction: "请写出句尾作陈述时使用的<strong class=\"keyword-highlight\">終止形</strong>（辞书形）。",
      practiceInflectionId: "dictionary",
      practiceLabel: "练辞书形"
    },
    {
      id: "rentai",
      label: "連体形",
      usage: "放在名词前修饰名词；现代动词通常与終止形同形，要看它后面是否接名词。",
      examples: [{ inflectionId: "dictionary", label: "修饰名词", suffix: "人", readingSuffix: "ひと" }],
      targetLabel: "連体形（名词前）",
      instruction: "请写出修饰名词「人」时使用的<strong class=\"keyword-highlight\">連体形</strong>（只写动词）。",
      practiceInflectionId: "dictionary",
      practiceLabel: "练同形辞书形"
    },
    {
      id: "katei",
      label: "仮定形",
      usage: "表示假设条件，常见结构是仮定形接「ば」；「たら」是「た形＋ら」，不是这一形。",
      examples: [{ inflectionId: "conditionalBa", label: "接「ば」" }],
      targetLabel: "仮定形（接ば）",
      instruction: "请写出接「ば」的<strong class=\"keyword-highlight\">仮定形</strong>，并组成完整条件表达。",
      practiceInflectionId: "conditionalBa",
      practiceLabel: "练ば条件形"
    },
    {
      id: "meirei",
      label: "命令形",
      usage: "直接要求对方做某事，语气较强；请求时常用更委婉的其他表达。",
      examples: [{ inflectionId: "imperative", label: "直接命令" }],
      targetLabel: "命令形",
      instruction: "请写出直接命令对方时使用的<strong class=\"keyword-highlight\">命令形</strong>。",
      practiceInflectionId: "imperative",
      practiceLabel: "练命令形"
    }
  ];
  // 难度档位从 INFLECTIONS.level 派生，新增档位时只需补上面的中文标签。
  INFLECTIONS.forEach(function (form) {
    const existing = LEVELS.some(function (level) { return level.id === form.level; });
    if (!existing && LEVEL_LABELS[form.level]) {
      LEVELS.push({ id: form.level, label: LEVEL_LABELS[form.level] });
    }
  });
  const RULE_FORM_LABELS = new Set(VOICES.concat(INFLECTIONS).map(function (form) {
    return form.label;
  }).concat(adjEngine.FORMS.map(function (form) { return form.label; })));

  // —— 形容词模块 ——
  const ADJECTIVES = adjEngine.ADJECTIVES;
  const ADJ_FORMS = adjEngine.FORMS;
  const ADJ_GROUP_LABELS = { i: "い形容词", na: "な形容词" };
  const ADJ_BY_ID = new Map(ADJECTIVES.map(function (adjective) { return [adjective.id, adjective]; }));
  const ADJECTIVE_MORPHOLOGY_FORMS = [
    {
      id: "mizen",
      label: "未然形",
      formId: "mizen",
      targetLabel: "未然形（接推量う）",
      instruction: "请用<strong class=\"keyword-highlight\">未然形</strong>接推量「う」，写出完整表达。"
    },
    {
      id: "renyou",
      label: "連用形",
      formId: "te",
      targetLabel: "連用形（连接后句）",
      instruction: "请把形容词连接到下一分句，写出<strong class=\"keyword-highlight\">連用形</strong>的连接形式。"
    },
    {
      id: "shushi",
      label: "終止形",
      formId: "present",
      targetLabel: "終止形（句尾）",
      instruction: "请让形容词作句尾谓语，写出<strong class=\"keyword-highlight\">終止形</strong>。"
    },
    {
      id: "rentai",
      label: "連体形",
      formId: "attributive",
      targetLabel: "連体形（名词前）",
      instruction: "请写出放在名词前修饰名词时的<strong class=\"keyword-highlight\">連体形</strong>。"
    },
    {
      id: "katei",
      label: "仮定形",
      formId: "conditional",
      targetLabel: "仮定形（接ば）",
      instruction: "请接「ば」写出<strong class=\"keyword-highlight\">仮定形</strong>条件表达。"
    },
    {
      id: "meirei",
      label: "命令形（形容词无独立形）",
      formId: "imperativeRequest",
      targetLabel: "命令表达（无独立形）",
      instruction: "形容词本身没有独立命令形；请用「<strong class=\"keyword-highlight\">～く／にしてください</strong>」改写成礼貌请求。"
    }
  ];
  const ADJ_STATS_STORAGE_KEY = "japanese-verb-practice-adj-stats-v1";
  const PARTICLE_STATS_STORAGE_KEY = "japanese-particle-practice-stats-v1";
  const DAILY_STORAGE_KEY = "japanese-verb-practice-daily-v1";
  const PARTICLE_MISTAKES_STORAGE_KEY = "japanese-particle-practice-mistakes-v1";
  // D-022：助词错题升级为带复习字段的记录（v2）。v1 的纯 ID 数组只作为迁移来源读取。
  const PARTICLE_MISTAKES_V2_STORAGE_KEY = "japanese-particle-practice-mistakes-v2";
  // 形容词错题与动词错题共用一本错题集：记录带 module 字段区分，
  // 旧存档没有 module 字段（即动词错题），缺失即视为 verb 的兼容迁移。
  const ADJ_MISTAKE_KEY_PREFIX = "adj::";

  function isAdjectiveMistake(mistake) {
    return mistake.module === "adjective";
  }
  // 与动词模块共用 LEVEL_LABELS；档位同样从 FORMS.level 派生。
  const ADJ_LEVELS = [];
  ADJ_FORMS.forEach(function (form) {
    const existing = ADJ_LEVELS.some(function (level) { return level.id === form.level; });
    if (!existing && LEVEL_LABELS[form.level]) {
      ADJ_LEVELS.push({ id: form.level, label: LEVEL_LABELS[form.level] });
    }
  });
  // 标语区文案随主视图切换。
  const INTRO_COPY = {
    home: {
      eyebrow: "每天学一点，积累成习惯",
      main: "把日语学习，",
      accent: "拆成今天的一小步。",
      copy: "从学习主页进入动词、形容词与助词模块，按自己的节奏练习。"
    },
    verb: {
      eyebrow: "一题一变，练到熟悉",
      main: "看见动词，",
      accent: "想起变化。",
      copy: "主动写出答案，再用变化规律记住它。支持五段、一段和不规则动词。"
    },
    adjective: {
      eyebrow: "先分い・な，再记变形",
      main: "看见形容词，",
      accent: "想起接续。",
      copy: "和动词同一套练法：主动写出答案，再用变化规律记住它。支持い形容词与な形容词的常用变形与三种接续。"
    },
    particle: {
      eyebrow: "例句练习，读懂助词",
      main: "在句子里反复练习，",
      accent: "逐渐掌握助词用法。",
      copy: "综合练习交替训练选助词和判断助词作用；学习地图随时可查。"
    },
    mistakes: {
      eyebrow: "REVIEW",
      main: "复习错题，",
      accent: "巩固每一次练习。",
      copy: "动词与形容词错题集中保存；助词错题在助词模块内复习。"
    }
  };

  const PARTICLE_SUBVIEW_COPY = {
    learn: {
      eyebrow: "PARTICLE MAP",
      title: "先看语境，再选助词",
      copy: "从常见助词对比开始；入门侧重核心意义，进阶补充焦点、语气和细微区别。"
    },
    practice: {
      eyebrow: "PARTICLE PRACTICE",
      title: "在句子中练习",
      copy: "综合练习会交替出现“选助词”和“判断助词作用”两种题型。"
    },
    review: {
      eyebrow: "REVIEW",
      title: "助词错题复习",
      copy: "错题按具体题目保存；答对后从本模块复习队列移除。"
    }
  };

  const elements = {
    tabs: Array.from(document.querySelectorAll("[data-view]")),
    verbSubtabs: Array.from(document.querySelectorAll("[data-verb-subview]")),
    adjSubtabs: Array.from(document.querySelectorAll("[data-adj-subview]")),
    views: {
      home: document.getElementById("view-home"),
      verb: document.getElementById("view-verb"),
      adjective: document.getElementById("view-adjective"),
      particle: document.getElementById("view-particle"),
      mistakes: document.getElementById("view-mistakes")
    },
    verbSubviewNav: document.getElementById("verb-subnav"),
    verbSubviewPractice: document.getElementById("view-practice"),
    verbSubviewReference: document.getElementById("view-reference"),
    particleSubviewNav: document.getElementById("particle-subnav"),
    particleViewEyebrow: document.getElementById("particle-view-eyebrow"),
    particleViewTitle: document.getElementById("particle-view-title"),
    particleViewDescription: document.getElementById("particle-view-description"),
    particleSubtabs: Array.from(document.querySelectorAll("[data-particle-subview]")),
    particleSubviewLearn: document.getElementById("particle-view-learn"),
    particleSubviewPractice: document.getElementById("particle-view-practice"),
    particleSubviewReview: document.getElementById("particle-view-review"),
    particleControlsPanel: document.getElementById("particle-controls-panel"),
    particleLevelControls: document.getElementById("particle-level-controls"),
    particleLevelSelect: document.getElementById("particle-level-select"),
    particlePracticeModes: document.getElementById("particle-practice-modes"),
    particlePracticeModeSelect: document.getElementById("particle-practice-mode-select"),
    particlePracticeModeHint: document.getElementById("particle-practice-mode-hint"),
    particleLessonGrid: document.getElementById("particle-lesson-grid"),
    particleQuestionProgress: document.getElementById("particle-question-progress"),
    particleQuestionTitle: document.getElementById("particle-question-title"),
    particleQuestionTarget: document.getElementById("particle-question-target"),
    particleQuestionCard: document.getElementById("particle-question-card"),
    particleQuestionContext: document.getElementById("particle-question-context"),
    particleQuestionPrompt: document.getElementById("particle-question-prompt"),
    particleSentenceBefore: document.getElementById("particle-sentence-before"),
    particleAnswerSlot: document.getElementById("particle-answer-slot"),
    particleSentenceAfter: document.getElementById("particle-sentence-after"),
    particleQuestionReading: document.getElementById("particle-question-reading"),
    particleQuestionTranslation: document.getElementById("particle-question-translation"),
    particleOptions: document.getElementById("particle-options"),
    particleCheckAnswer: document.getElementById("particle-check-answer"),
    particleNextQuestion: document.getElementById("particle-next-question"),
    particleFeedback: document.getElementById("particle-feedback"),
    particleFeedbackTitle: document.getElementById("particle-feedback-title"),
    particleFeedbackAnswer: document.getElementById("particle-feedback-answer"),
    particleFeedbackExplanation: document.getElementById("particle-feedback-explanation"),
    particleFeedbackNote: document.getElementById("particle-feedback-note"),
    particleCorrectCount: document.getElementById("particle-correct-count"),
    particleIncorrectCount: document.getElementById("particle-incorrect-count"),
    particleTotalCount: document.getElementById("particle-total-count"),
    particleAccuracy: document.getElementById("particle-accuracy"),
    particleAnswerDistribution: document.getElementById("particle-answer-distribution"),
    particleCorrectSegment: document.getElementById("particle-correct-segment"),
    particleIncorrectSegment: document.getElementById("particle-incorrect-segment"),
    particleResetStats: document.getElementById("particle-reset-stats"),
    particleGuideLink: document.getElementById("particle-guide-link"),
    particleStartReview: document.getElementById("particle-start-review"),
    particleReviewCount: document.getElementById("particle-review-count"),
    particleReviewList: document.getElementById("particle-review-list"),
    particleReviewEmpty: document.getElementById("particle-review-empty"),
    particleMistakeCount: document.getElementById("particle-mistake-count"),
    appShell: document.getElementById("app-shell"),
    appMain: document.getElementById("app-main"),
    homeLinks: Array.from(document.querySelectorAll("[data-home-link]")),
    sidebarCollapse: document.getElementById("sidebar-collapse"),
    mobileNavToggle: document.getElementById("mobile-nav-toggle"),
    sidebarBackdrop: document.getElementById("sidebar-backdrop"),
    introEyebrow: document.getElementById("intro-eyebrow"),
    introTitle: document.getElementById("intro-title"),
    introCopy: document.getElementById("intro-copy"),
    streakNote: document.getElementById("streak-note"),
    streakNoteText: document.getElementById("streak-note-text"),
    mistakeCount: document.getElementById("mistake-count"),
    homeMistakeCount: document.getElementById("home-mistake-count"),
    homeTodayTotal: document.getElementById("home-today-total"),
    homeTodayBreakdown: document.getElementById("home-today-breakdown"),
    homeStreakCount: document.getElementById("home-streak-count"),
    homeActiveModuleCount: document.getElementById("home-active-module-count"),
    homeVerbToday: document.getElementById("home-verb-today"),
    homeVerbStatus: document.getElementById("home-verb-status"),
    homeAdjectiveToday: document.getElementById("home-adjective-today"),
    homeAdjectiveStatus: document.getElementById("home-adjective-status"),
    homeParticleToday: document.getElementById("home-particle-today"),
    homeParticleStatus: document.getElementById("home-particle-status"),
    correctAnswerCount: document.getElementById("correct-answer-count"),
    incorrectAnswerCount: document.getElementById("incorrect-answer-count"),
    totalAnswerCount: document.getElementById("total-answer-count"),
    answerAccuracy: document.getElementById("answer-accuracy"),
    answerDistribution: document.getElementById("answer-distribution"),
    correctAnswerSegment: document.getElementById("correct-answer-segment"),
    incorrectAnswerSegment: document.getElementById("incorrect-answer-segment"),
    resetStats: document.getElementById("reset-stats"),
    groupFilter: document.getElementById("group-filter"),
    voiceFilter: document.getElementById("voice-filter"),
    inflectionFilter: document.getElementById("inflection-filter"),
    levelFilter: document.getElementById("level-filter"),
    reviewBanner: document.getElementById("review-banner"),
    reviewBannerCount: document.getElementById("review-banner-count"),
    exitReview: document.getElementById("exit-review"),
    practiceCard: document.querySelector(".practice-card"),
    verbPrompt: document.querySelector(".verb-prompt"),
    newQuestion: document.getElementById("new-question"),
    questionIndex: document.getElementById("question-index"),
    targetPill: document.getElementById("target-pill"),
    questionGroup: document.getElementById("question-group"),
    questionMeaning: document.getElementById("question-meaning"),
    questionWord: document.getElementById("question-word"),
    questionReading: document.getElementById("question-reading"),
    questionInstruction: document.getElementById("question-instruction"),
    answerForm: document.getElementById("answer-form"),
    answerInput: document.getElementById("answer-input"),
    submitAnswer: document.getElementById("submit-answer"),
    hintButton: document.getElementById("hint-button"),
    revealAnswer: document.getElementById("reveal-answer"),
    ruleHint: document.getElementById("rule-hint"),
    feedbackPanel: document.getElementById("feedback-panel"),
    feedbackMark: document.getElementById("feedback-mark"),
    feedbackTitle: document.getElementById("feedback-title"),
    feedbackUserAnswer: document.getElementById("feedback-user-answer"),
    feedbackCorrectAnswer: document.getElementById("feedback-correct-answer"),
    feedbackReading: document.getElementById("feedback-reading"),
    feedbackVoiceMeaning: document.getElementById("feedback-voice-meaning"),
    feedbackInflectionMeaning: document.getElementById("feedback-inflection-meaning"),
    feedbackRule: document.getElementById("feedback-rule"),
    nextQuestion: document.getElementById("next-question"),
    referenceSearch: document.getElementById("reference-search"),
    referenceVerb: document.getElementById("reference-verb"),
    referenceVoice: document.getElementById("reference-voice"),
    referenceWord: document.getElementById("reference-word"),
    referenceMeaning: document.getElementById("reference-meaning"),
    referenceVoiceMeaning: document.getElementById("reference-voice-meaning"),
    referenceGroup: document.getElementById("reference-group"),
    verbMorphologyGuide: document.getElementById("verb-morphology-guide"),
    verbMorphologyGuideCaption: document.getElementById("verb-morphology-guide-caption"),
    verbMorphologyGuideTableBody: document.getElementById("verb-morphology-guide-table-body"),
    referenceVoiceGuide: document.getElementById("reference-voice-guide"),
    referenceVoiceGuideCaption: document.getElementById("reference-voice-guide-caption"),
    referenceVoiceGuideTableBody: document.getElementById("reference-voice-guide-table-body"),
    referenceTableBody: document.getElementById("reference-table-body"),
    reviewMistakes: document.getElementById("review-mistakes"),
    clearMistakes: document.getElementById("clear-mistakes"),
    verbMistakesList: document.getElementById("verb-mistakes-list"),
    verbMistakesGroup: document.getElementById("verb-mistakes-group"),
    verbMistakesCount: document.getElementById("verb-mistakes-count"),
    adjectiveMistakesGroup: document.getElementById("adjective-mistakes-group"),
    adjectiveMistakesList: document.getElementById("adjective-mistakes-list"),
    adjectiveMistakesCount: document.getElementById("adjective-mistakes-count"),
    mistakesEmpty: document.getElementById("mistakes-empty"),
    adjSubviewPractice: document.getElementById("adj-subview-practice"),
    adjSubviewReference: document.getElementById("adj-subview-reference"),
    toast: document.getElementById("toast"),
    viewAnnouncer: document.getElementById("view-announcer"),
    // —— 形容词模块 ——
    adjGroupFilter: document.getElementById("adj-group-filter"),
    adjInflectionFilter: document.getElementById("adj-inflection-filter"),
    adjLevelFilter: document.getElementById("adj-level-filter"),
    adjNewQuestion: document.getElementById("adj-new-question"),
    adjReviewBanner: document.getElementById("adj-review-banner"),
    adjReviewBannerCount: document.getElementById("adj-review-banner-count"),
    adjExitReview: document.getElementById("adj-exit-review"),
    adjReviewMistakes: document.getElementById("adj-review-mistakes"),
    exportMistakes: document.getElementById("export-mistakes"),
    importMistakes: document.getElementById("import-mistakes"),
    importMistakesFile: document.getElementById("import-mistakes-file"),
    adjQuestionIndex: document.getElementById("adj-question-index"),
    adjTargetPill: document.getElementById("adj-target-pill"),
    adjQuestionKind: document.getElementById("adj-question-kind"),
    adjQuestionMeaning: document.getElementById("adj-question-meaning"),
    adjQuestionWord: document.getElementById("adj-question-word"),
    adjQuestionReading: document.getElementById("adj-question-reading"),
    adjQuestionInstruction: document.getElementById("adj-question-instruction"),
    adjAnswerForm: document.getElementById("adj-answer-form"),
    adjAnswerInput: document.getElementById("adj-answer-input"),
    adjSubmitAnswer: document.getElementById("adj-submit-answer"),
    adjHintButton: document.getElementById("adj-hint-button"),
    adjRevealAnswer: document.getElementById("adj-reveal-answer"),
    adjRuleHint: document.getElementById("adj-rule-hint"),
    adjFeedbackPanel: document.getElementById("adj-feedback-panel"),
    adjFeedbackMark: document.getElementById("adj-feedback-mark"),
    adjFeedbackTitle: document.getElementById("adj-feedback-title"),
    adjFeedbackUserAnswer: document.getElementById("adj-feedback-user-answer"),
    adjFeedbackCorrectAnswer: document.getElementById("adj-feedback-correct-answer"),
    adjFeedbackReading: document.getElementById("adj-feedback-reading"),
    adjFeedbackFormMeaning: document.getElementById("adj-feedback-form-meaning"),
    adjFeedbackRule: document.getElementById("adj-feedback-rule"),
    adjNextQuestion: document.getElementById("adj-next-question"),
    adjPracticeCard: document.getElementById("adj-practice-card"),
    adjResetStats: document.getElementById("adj-reset-stats"),
    adjCorrectCount: document.getElementById("adj-correct-count"),
    adjIncorrectCount: document.getElementById("adj-incorrect-count"),
    adjTotalCount: document.getElementById("adj-total-count"),
    adjAccuracy: document.getElementById("adj-accuracy"),
    adjAnswerDistribution: document.getElementById("adj-answer-distribution"),
    adjCorrectSegment: document.getElementById("adj-correct-segment"),
    adjIncorrectSegment: document.getElementById("adj-incorrect-segment"),
    adjGuideLink: document.getElementById("adj-guide-link")
  };

  const ANSWER_STATS_CONTROLS = {
    verb: {
      correct: elements.correctAnswerCount,
      incorrect: elements.incorrectAnswerCount,
      total: elements.totalAnswerCount,
      accuracy: elements.answerAccuracy,
      distribution: elements.answerDistribution,
      correctSegment: elements.correctAnswerSegment,
      incorrectSegment: elements.incorrectAnswerSegment,
      reset: elements.resetStats
    },
    adjective: {
      correct: elements.adjCorrectCount,
      incorrect: elements.adjIncorrectCount,
      total: elements.adjTotalCount,
      accuracy: elements.adjAccuracy,
      distribution: elements.adjAnswerDistribution,
      correctSegment: elements.adjCorrectSegment,
      incorrectSegment: elements.adjIncorrectSegment,
      reset: elements.adjResetStats
    },
    particle: {
      correct: elements.particleCorrectCount,
      incorrect: elements.particleIncorrectCount,
      total: elements.particleTotalCount,
      accuracy: elements.particleAccuracy,
      distribution: elements.particleAnswerDistribution,
      correctSegment: elements.particleCorrectSegment,
      incorrectSegment: elements.particleIncorrectSegment,
      reset: elements.particleResetStats
    }
  };

  let storageAvailable = true;
  let toastTimeout = null;
  let composing = false;
  let activeViewId = "home";
  let currentVerbSubview = "practice";
  let currentQuestion = null;
  let lastPairKey = "";
  let recentQuestionVerbIds = [];
  let questionDeck = [];
  let questionDeckFilterKey = "";
  let reviewingMistakes = false;
  let sessionAnswerCount = 0;
  let reviewedMistakeKeys = new Set();
  let mistakes = loadMistakes();
  let practiceStats = loadAnswerStats(STATS_STORAGE_KEY, "", "浏览器不允许访问本地存储；答题统计和错题仅保留在当前页面。");

  // —— 形容词模块状态 ——
  let adjCurrentQuestion = null;
  let adjLastPairKey = "";
  let adjRecentWordIds = [];
  let adjQuestionDeck = [];
  let adjQuestionDeckFilterKey = "";
  let adjPracticeStats = loadAnswerStats(ADJ_STATS_STORAGE_KEY, "形容词");
  let reviewingAdjectiveMistakes = false;
  let dailyRecord = loadDailyRecord();
  let particleMistakes = loadParticleMistakes();
  let particlePracticeStats = loadAnswerStats(
    PARTICLE_STATS_STORAGE_KEY,
    "助词",
    "浏览器不允许访问本地存储；助词答题统计仅保留在当前页面。"
  );
  let currentParticleSubview = "practice";
  let currentParticleLevel = "beginner";
  let currentParticlePracticeMode = "mixed";
  let currentParticleQuestion = null;
  let currentParticleQuestionId = "";
  let particleTargetLessonId = "";
  let particleQuestionQueue = [];
  let particleQuestionIndex = 0;
  let selectedParticleAnswer = "";
  let particleAnswered = false;
  let reviewingParticleMistakes = false;
  let particleReviewQueue = [];

  function showToast(message, isError) {
    elements.toast.textContent = message;
    elements.toast.classList.toggle("is-error", Boolean(isError));
    elements.toast.classList.add("is-visible");
    window.clearTimeout(toastTimeout);
    toastTimeout = window.setTimeout(function () {
      elements.toast.classList.remove("is-visible");
    }, 3000);
  }

  // IK-18：导航类状态变化的统一播报出口（视图/子视图切换）。
  // live region 重复写入相同文本不触发播报，先清空再写入。
  // 有子视图的模块（动词/助词）由子视图函数播报更具体的「模块 · 子视图」，
  // 其余模块在此播报模块名。
  const VIEW_ANNOUNCE_LABELS = {
    home: "学习主页",
    adjective: "形容词模块",
    mistakes: "错题本"
  };

  function announce(message) {
    elements.viewAnnouncer.textContent = "";
    elements.viewAnnouncer.textContent = message;
  }

  function renderRuleExplanation(element, explanation) {
    element.replaceChildren();
    explanation.split(/(「[^」]+」|[あいうえお]段|\n)/g).forEach(function (part) {
      if (!part) {
        return;
      }
      if (part === "\n") {
        element.append(document.createElement("br"));
        return;
      }

      const quotedTerm = /^「([^」]+)」$/.exec(part);
      const vowelRow = /^[あいうえお]段$/.test(part);
      if (vowelRow || (quotedTerm && !RULE_FORM_LABELS.has(quotedTerm[1]))) {
        const emphasis = document.createElement("span");
        emphasis.className = "keyword-highlight rule-emphasis";
        emphasis.textContent = part;
        element.append(emphasis);
        return;
      }
      element.append(document.createTextNode(part));
    });
  }

  function isObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function mistakeKey(verbId, voiceId, inflectionId) {
    return [verbId, voiceId, inflectionId].join("::");
  }

  // 错题记录校验与规范化：本地存档与导入文件共用同一套防御性过滤。
  // 只做结构校验与字段兜底，不弹 toast（提示语由调用方按场景决定）。
  function sanitizeMistakeList(stored) {
    const voiceIds = new Set(VOICES.map(function (voice) { return voice.id; }));
    const inflectionIds = new Set(INFLECTIONS.map(function (form) { return form.id; }));
    const adjFormIds = new Set(ADJ_FORMS.map(function (form) { return form.id; }));
    if (!Array.isArray(stored)) {
      return [];
    }

    const now = new Date().toISOString();
    return stored.filter(function (item) {
      if (!isObject(item)) {
        return false;
      }
      if (item.module === "adjective") {
        return ADJ_BY_ID.has(item.adjectiveId) && adjFormIds.has(item.formId);
      }
      return VERB_BY_ID.has(item.verbId)
        && voiceIds.has(item.voiceId)
        && inflectionIds.has(item.inflectionId);
    }).map(function (item) {
      const storedCorrectStreak = Number(item.correctStreak);
      const storedIntervalDays = Number(item.intervalDays);
      const storedDueAt = typeof item.dueAt === "string" ? Date.parse(item.dueAt) : NaN;
      const common = {
        count: Math.max(1, Number.isFinite(Number(item.count)) ? Math.floor(Number(item.count)) : 1),
        wrongAnswer: typeof item.wrongAnswer === "string" ? item.wrongAnswer.slice(0, 120) : "",
        updatedAt: typeof item.updatedAt === "string" ? item.updatedAt : "",
        correctStreak: Number.isFinite(storedCorrectStreak)
          ? Math.max(0, Math.min(MASTERY_CORRECT_STREAK - 1, Math.floor(storedCorrectStreak)))
          : 0,
        intervalDays: Number.isFinite(storedIntervalDays) ? Math.max(1, Math.floor(storedIntervalDays)) : 1,
        dueAt: Number.isFinite(storedDueAt) ? new Date(storedDueAt).toISOString() : now,
        sessionDueAtAnswerCount: null
      };
      if (item.module === "adjective") {
        return Object.assign({
          key: ADJ_MISTAKE_KEY_PREFIX + [item.adjectiveId, item.formId].join("::"),
          module: "adjective",
          adjectiveId: item.adjectiveId,
          formId: item.formId
        }, common);
      }
      return Object.assign({
        key: mistakeKey(item.verbId, item.voiceId, item.inflectionId),
        verbId: item.verbId,
        voiceId: item.voiceId,
        inflectionId: item.inflectionId
      }, common);
    });
  }

  function loadMistakes() {
    let raw;
    try {
      raw = window.localStorage.getItem(STORAGE_KEY);
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器不允许访问本地存储；错题、复习计划和答题统计仅保留在当前页面。", true);
      return [];
    }

    if (!raw) {
      return [];
    }

    let stored;
    try {
      stored = JSON.parse(raw);
    } catch (error) {
      showToast("本地错题记录无法解析，本次先从空列表开始。", true);
      return [];
    }

    if (!Array.isArray(stored)) {
      showToast("本地错题数据格式无效，本次先从空列表开始。", true);
      return [];
    }
    return sanitizeMistakeList(stored);
  }

  function saveMistakes() {
    if (!storageAvailable) {
      return;
    }
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(mistakes));
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器无法保存本地数据；错题、复习计划和答题统计仅保留在当前页面。", true);
    }
  }

  /** 新建一条助词错题记录（字段与动词/形容词错题对齐，见 D-022）。 */
  function createParticleMistakeRecord(questionId, wrongAnswer, now) {
    const stamp = (now || new Date()).toISOString();
    return {
      questionId: questionId,
      count: 1,
      wrongAnswer: wrongAnswer || "",
      updatedAt: stamp,
      correctStreak: 0,
      intervalDays: 1,
      dueAt: addDays(new Date(stamp), 1),
      sessionDueAtAnswerCount: sessionAnswerCount + SESSION_RETRY_AFTER_ANSWERS
    };
  }

  /** 逐字段校验并补默认值；题目已不在题库时返回 null（由调用方丢弃）。 */
  function normalizeParticleMistake(raw, now) {
    if (!raw || typeof raw !== "object") {
      return null;
    }
    const questionId = raw.questionId;
    if (typeof questionId !== "string" || !particleEngine.getQuestion(questionId)) {
      return null;
    }
    const countValue = Number(raw.count);
    const streakValue = Number(raw.correctStreak);
    const intervalValue = Number(raw.intervalDays);
    // 可空字段必须先挡 null/undefined：Number(null) === 0，会把「无会话内重试」
    // 误归一成「0 次答题后重试」，导致 Android 写入的未到期错题全部判为到期（IK-20 实测）
    const sessionRaw = raw.sessionDueAtAnswerCount;
    const sessionValue = sessionRaw === null || sessionRaw === undefined ? NaN : Number(sessionRaw);
    const dueTimestamp = typeof raw.dueAt === "string" ? Date.parse(raw.dueAt) : NaN;
    return {
      questionId: questionId,
      count: Number.isFinite(countValue) && countValue >= 1 ? Math.floor(countValue) : 1,
      wrongAnswer: typeof raw.wrongAnswer === "string" ? raw.wrongAnswer : "",
      updatedAt: typeof raw.updatedAt === "string" ? raw.updatedAt : now.toISOString(),
      correctStreak: Number.isFinite(streakValue) && streakValue >= 0
        ? Math.min(Math.floor(streakValue), MASTERY_CORRECT_STREAK)
        : 0,
      intervalDays: Number.isFinite(intervalValue) && intervalValue >= 1 ? Math.floor(intervalValue) : 1,
      dueAt: Number.isFinite(dueTimestamp) ? new Date(dueTimestamp).toISOString() : now.toISOString(),
      sessionDueAtAnswerCount: Number.isFinite(sessionValue) && sessionValue >= 0
        ? Math.floor(sessionValue)
        : null
    };
  }

  function dedupeParticleMistakes(records) {
    const seen = new Set();
    const valid = [];
    let discarded = false;
    records.forEach(function (record) {
      if (!record || seen.has(record.questionId)) {
        discarded = true;
        return;
      }
      seen.add(record.questionId);
      valid.push(record);
    });
    if (discarded) {
      showToast("部分助词错题记录无效或重复，已跳过。", true);
    }
    return valid;
  }

  function loadParticleMistakes() {
    const now = new Date();

    let rawV2;
    try {
      rawV2 = window.localStorage.getItem(PARTICLE_MISTAKES_V2_STORAGE_KEY);
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器不允许访问本地存储；助词错题仅保留在当前页面。", true);
      return [];
    }

    if (rawV2) {
      let stored;
      try {
        stored = JSON.parse(rawV2);
      } catch (error) {
        showToast("本地助词错题无法解析，本次先从空列表开始。", true);
        return [];
      }
      if (!Array.isArray(stored)) {
        showToast("本地助词错题格式无效，本次先从空列表开始。", true);
        return [];
      }
      return dedupeParticleMistakes(stored.map(function (raw) {
        return normalizeParticleMistake(raw, now);
      }));
    }

    // 迁移：v1 是纯题目 ID 数组，没有排期信息 → 视为「现在就到期」
    let rawV1;
    try {
      rawV1 = window.localStorage.getItem(PARTICLE_MISTAKES_STORAGE_KEY);
    } catch (error) {
      return [];
    }
    if (!rawV1) {
      return [];
    }
    let legacy;
    try {
      legacy = JSON.parse(rawV1);
    } catch (error) {
      showToast("本地助词错题无法解析，本次先从空列表开始。", true);
      return [];
    }
    if (!Array.isArray(legacy)) {
      showToast("本地助词错题格式无效，本次先从空列表开始。", true);
      return [];
    }
    const migrated = dedupeParticleMistakes(legacy.map(function (questionId) {
      return normalizeParticleMistake({ questionId: questionId }, now);
    }));
    if (migrated.length) {
      try {
        window.localStorage.setItem(PARTICLE_MISTAKES_V2_STORAGE_KEY, JSON.stringify(migrated));
        showToast("已把 " + migrated.length + " 道旧助词错题并入复习计划。", true);
      } catch (error) {
        // 迁移写入失败不阻塞使用：本轮仍按迁移结果工作
      }
    }
    return migrated;
  }

  function saveParticleMistakes() {
    if (!storageAvailable) {
      return false;
    }
    try {
      window.localStorage.setItem(PARTICLE_MISTAKES_V2_STORAGE_KEY, JSON.stringify(particleMistakes));
      return true;
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器无法保存助词错题；错题仅保留在当前页面。", true);
      return false;
    }
  }

  function createEmptyAnswerStats() {
    return { correct: 0, incorrect: 0 };
  }

  function loadAnswerStats(storageKey, moduleLabel, storageErrorMessage) {
    if (!storageAvailable) {
      return createEmptyAnswerStats();
    }

    let raw;
    try {
      raw = window.localStorage.getItem(storageKey);
    } catch (error) {
      storageAvailable = false;
      showToast(
        storageErrorMessage || "浏览器不允许访问本地存储；" + moduleLabel + "答题统计仅保留在当前页面。",
        true
      );
      return createEmptyAnswerStats();
    }

    if (!raw) {
      return createEmptyAnswerStats();
    }

    let stored;
    try {
      stored = JSON.parse(raw);
    } catch (error) {
      showToast("本地" + moduleLabel + "答题统计无法解析，本次从零开始。", true);
      return createEmptyAnswerStats();
    }

    if (!isObject(stored)
      || !Number.isSafeInteger(stored.correct)
      || stored.correct < 0
      || !Number.isSafeInteger(stored.incorrect)
      || stored.incorrect < 0) {
      showToast("本地" + moduleLabel + "答题统计格式无效，本次从零开始。", true);
      return createEmptyAnswerStats();
    }

    return { correct: stored.correct, incorrect: stored.incorrect };
  }

  function savePracticeStats() {
    return saveAnswerStats(
      practiceStats,
      STATS_STORAGE_KEY,
      "浏览器无法保存本地数据；答题统计和错题仅保留在当前页面。"
    );
  }

  function saveAnswerStats(stats, storageKey, errorMessage) {
    if (!storageAvailable) {
      return false;
    }
    try {
      window.localStorage.setItem(storageKey, JSON.stringify(stats));
      return true;
    } catch (error) {
      storageAvailable = false;
      showToast(errorMessage, true);
      return false;
    }
  }

  function renderPracticeStats() {
    renderAnswerStats(practiceStats, ANSWER_STATS_CONTROLS.verb);
  }

  function renderAnswerStats(stats, controls) {
    controls.correct.textContent = String(stats.correct);
    controls.incorrect.textContent = String(stats.incorrect);
    const total = stats.correct + stats.incorrect;
    const correctRatio = total === 0 ? 0 : stats.correct / total;
    const incorrectRatio = total === 0 ? 0 : stats.incorrect / total;
    const correctPercent = Math.round(correctRatio * 100);
    const incorrectPercent = 100 - correctPercent;
    controls.total.textContent = String(total);
    controls.accuracy.textContent = total === 0 ? "—" : correctPercent + "%";
    controls.correctSegment.style.width = correctRatio * 100 + "%";
    controls.incorrectSegment.style.width = incorrectRatio * 100 + "%";
    controls.distribution.classList.toggle("is-empty", total === 0);
    controls.distribution.setAttribute(
      "aria-label",
      total === 0
        ? "尚未作答"
        : "答题比例：答对 " + stats.correct + " 题，占 " + correctPercent
          + "%；答错 " + stats.incorrect + " 题，占 " + incorrectPercent + "%。"
    );
    controls.reset.disabled = stats.correct === 0 && stats.incorrect === 0;
  }

  function updatePracticeStats(correct) {
    updateAnswerStats(
      practiceStats,
      correct,
      ANSWER_STATS_CONTROLS.verb,
      savePracticeStats,
      elements.practiceCard
    );
  }

  function updateAnswerStats(stats, correct, controls, save, practiceCard) {
    const key = correct ? "correct" : "incorrect";
    stats[key] = Math.min(Number.MAX_SAFE_INTEGER, stats[key] + 1);
    renderAnswerStats(stats, controls);
    save();
    practiceCard.classList.remove("is-answer-correct");
    practiceCard.classList.remove("is-answer-incorrect");
    void practiceCard.offsetWidth;
    practiceCard.classList.add(
      correct ? "is-answer-correct" : "is-answer-incorrect"
    );
  }

  function resetPracticeStats() {
    practiceStats = createEmptyAnswerStats();
    renderPracticeStats();
    if (savePracticeStats()) {
      showToast("答题统计已清零，错题集保持不变。", false);
    }
  }

  function saveParticleStats() {
    return saveAnswerStats(
      particlePracticeStats,
      PARTICLE_STATS_STORAGE_KEY,
      "浏览器无法保存助词答题统计；统计仅保留在当前页面。"
    );
  }

  function renderParticleStats() {
    renderAnswerStats(particlePracticeStats, ANSWER_STATS_CONTROLS.particle);
  }

  function updateParticleStats(correct) {
    updateAnswerStats(
      particlePracticeStats,
      correct,
      ANSWER_STATS_CONTROLS.particle,
      saveParticleStats,
      elements.particleQuestionCard
    );
  }

  function resetParticleStats() {
    particlePracticeStats = createEmptyAnswerStats();
    renderParticleStats();
    if (saveParticleStats()) {
      showToast("助词答题统计已清零，错题集保持不变。", false);
    }
  }

  function addOption(select, value, label) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.append(option);
  }

  function populatePracticeFilters() {
    addOption(elements.voiceFilter, "all", "全部变形");
    addOption(elements.voiceFilter, "base", "基础形态");
    VOICES.filter(function (voice) { return voice.id !== "base"; })
      .forEach(function (voice) {
        addOption(elements.voiceFilter, voice.id, voice.label);
      });
    elements.voiceFilter.value = "base";

    addOption(elements.inflectionFilter, "all", "全部活用");
    INFLECTIONS.forEach(function (form) {
      addOption(elements.inflectionFilter, form.id, form.label);
    });
    VERB_MORPHOLOGY_FORMS.forEach(function (form) {
      addOption(
        elements.inflectionFilter,
        MORPHOLOGY_FILTER_PREFIX + form.id,
        "六形 · " + form.targetLabel
      );
    });

    addOption(elements.levelFilter, "all", "全部难度");
    LEVELS.forEach(function (level) {
      addOption(elements.levelFilter, level.id, level.label);
    });
    syncLevelFilterState();
  }

  // 已指定具体活用形式时，难度筛选必然被覆盖，直接禁用比静默失效更诚实。
  function syncLevelFilterState() {
    const overridden = elements.inflectionFilter.value !== "all";
    elements.levelFilter.disabled = overridden;
    if (overridden) {
      elements.levelFilter.title = "已指定具体活用形式，难度筛选暂不生效";
      elements.levelFilter.setAttribute("aria-disabled", "true");
    } else {
      elements.levelFilter.title = "按基础／进阶筛选活用形式";
      elements.levelFilter.removeAttribute("aria-disabled");
    }
  }

  function getSelectedVerbMorphologyForm() {
    const selectedValue = elements.inflectionFilter.value;
    if (selectedValue.indexOf(MORPHOLOGY_FILTER_PREFIX) !== 0) {
      return null;
    }
    const morphologyFormId = selectedValue.slice(MORPHOLOGY_FILTER_PREFIX.length);
    const morphologyForm = VERB_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === morphologyFormId;
    })[0];
    if (!morphologyForm) {
      throw new RangeError("Unknown verb morphology filter: " + morphologyFormId);
    }
    return morphologyForm;
  }

  function populateReferenceControls(query) {
    const normalizedQuery = normalizeText(query || "").toLocaleLowerCase();
    const previousId = elements.referenceVerb.value;
    const matches = VERBS.filter(function (verb) {
      const text = normalizeText(
        verb.dictionary + verb.reading + verb.meaning + GROUP_LABELS[verb.group]
      ).toLocaleLowerCase();
      return text.includes(normalizedQuery);
    });

    elements.referenceVerb.replaceChildren();
    matches.forEach(function (verb) {
      const option = document.createElement("option");
      option.value = verb.id;
      option.textContent = verb.dictionary + "（" + verb.reading + "） · " + verb.meaning;
      elements.referenceVerb.append(option);
    });

    if (!matches.length) {
      addOption(elements.referenceVerb, "", "没有匹配的动词");
      elements.referenceVerb.disabled = true;
    } else {
      elements.referenceVerb.disabled = false;
      elements.referenceVerb.value = matches.some(function (verb) { return verb.id === previousId; })
        ? previousId
        : matches[0].id;
    }
    renderReferenceTable();
  }

  function populateReferenceVoices() {
    VOICES.forEach(function (voice) {
      addOption(elements.referenceVoice, voice.id, voice.label);
    });
  }

  function getVoiceLabel(voiceId) {
    return VOICE_BY_ID.get(voiceId).label;
  }

  function getInflectionLabel(inflectionId) {
    return INFLECTION_BY_ID.get(inflectionId).label;
  }

  function getTargetLabel(voiceId, inflectionId) {
    const inflectionLabel = getInflectionLabel(inflectionId);
    return voiceId === "base"
      ? inflectionLabel
      : getVoiceLabel(voiceId) + " · " + inflectionLabel;
  }

  function getPairKey(verb, voiceId, inflectionId) {
    return mistakeKey(verb.id, voiceId, inflectionId);
  }

  function randomItem(items) {
    return items[Math.floor(Math.random() * items.length)];
  }

  // 按权重随机抽取：权重大的条目出现概率成比例放大（权重必须为正整数）。
  function weightedItem(items, weightOf) {
    let total = 0;
    items.forEach(function (item) {
      total += weightOf(item);
    });
    let roll = Math.random() * total;
    for (let index = 0; index < items.length; index += 1) {
      roll -= weightOf(items[index]);
      if (roll < 0) {
        return items[index];
      }
    }
    return items[items.length - 1];
  }

  function mistakeWeight(mistake) {
    return Math.max(1, Math.min(MAX_MISTAKE_WEIGHT, mistake.count));
  }

  function shuffleItems(items) {
    for (let index = items.length - 1; index > 0; index -= 1) {
      const randomIndex = Math.floor(Math.random() * (index + 1));
      [items[index], items[randomIndex]] = [items[randomIndex], items[index]];
    }
    return items;
  }

  function buildTargets() {
    const voices = elements.voiceFilter.value === "all"
      ? VOICES
      : VOICES.filter(function (voice) { return voice.id === elements.voiceFilter.value; });
    const morphologyForm = getSelectedVerbMorphologyForm();
    // 具体活用形式优先于难度：两者同时指定时以具体形式为准，不会筛出空题库。
    const inflections = morphologyForm
      ? INFLECTIONS.filter(function (form) {
        return form.id === morphologyForm.practiceInflectionId;
      })
      : elements.inflectionFilter.value !== "all"
      ? INFLECTIONS.filter(function (form) { return form.id === elements.inflectionFilter.value; })
      : elements.levelFilter.value === "all"
        ? INFLECTIONS
        : INFLECTIONS.filter(function (form) { return form.level === elements.levelFilter.value; });
    const targets = [];

    voices.forEach(function (voice) {
      inflections.forEach(function (form) {
        const target = { voiceId: voice.id, inflectionId: form.id };
        if (morphologyForm) {
          target.morphologyFormId = morphologyForm.id;
        }
        targets.push(target);
      });
    });
    return targets;
  }

  function isReviewDue(mistake, now) {
    const nowTime = now === undefined ? Date.now() : now;
    return (Number.isFinite(mistake.sessionDueAtAnswerCount)
      && mistake.sessionDueAtAnswerCount <= sessionAnswerCount)
      || Date.parse(mistake.dueAt) <= nowTime;
  }

  function chooseDueReviewQuestion() {
    const groupId = elements.groupFilter.value;
    const allowedTargets = new Set(buildTargets().map(function (target) {
      return [target.voiceId, target.inflectionId].join("::");
    }));
    const now = Date.now();
    const dueMistakes = mistakes.filter(function (mistake) {
      const verb = VERB_BY_ID.get(mistake.verbId);
      return verb
        && isReviewDue(mistake, now)
        && (!currentQuestion || currentQuestion.key !== mistake.key)
        && (groupId === "all" || verb.group === groupId)
        && allowedTargets.has([mistake.voiceId, mistake.inflectionId].join("::"));
    });
    if (!dueMistakes.length) {
      return null;
    }

    const question = questionFromMistake(weightedItem(dueMistakes, mistakeWeight));
    if (question) {
      question.isScheduledReview = true;
      const morphologyForm = getSelectedVerbMorphologyForm();
      if (morphologyForm) {
        question.morphologyFormId = morphologyForm.id;
      }
    }
    return question;
  }

  function buildQuestion(verb, voiceId, inflectionId) {
    if (!verb) {
      return null;
    }
    return {
      verb: verb,
      voiceId: voiceId,
      inflectionId: inflectionId,
      result: engine.conjugate(verb, voiceId, inflectionId),
      key: getPairKey(verb, voiceId, inflectionId)
    };
  }

  function questionFromMistake(mistake) {
    if (isAdjectiveMistake(mistake)) {
      return buildAdjectiveQuestionById(mistake.adjectiveId, mistake.formId);
    }
    return buildQuestion(VERB_BY_ID.get(mistake.verbId), mistake.voiceId, mistake.inflectionId);
  }

  function buildVerbMorphologyQuestion(verb, morphologyFormId) {
    const morphologyForm = VERB_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === morphologyFormId;
    })[0];
    if (!morphologyForm) {
      throw new RangeError("Unknown verb morphology form: " + morphologyFormId);
    }
    const question = buildQuestion(verb, "base", morphologyForm.practiceInflectionId);
    if (question) {
      question.morphologyFormId = morphologyForm.id;
    }
    return question;
  }

  function practiceVerbMorphology(verb, morphologyFormId) {
    const question = buildVerbMorphologyQuestion(verb, morphologyFormId);
    if (!question) {
      return;
    }
    elements.groupFilter.value = verb.group;
    elements.voiceFilter.value = "base";
    elements.inflectionFilter.value = MORPHOLOGY_FILTER_PREFIX + morphologyFormId;
    syncLevelFilterState();
    setReviewingMistakes(false);
    showView("verb");
    setVerbSubview("practice");
    startQuestion(question);
    elements.answerInput.focus();
  }

  // 从速查表直接开练：不进入错题重练模式，但保留明确的题目来源。
  function practiceFromReference(verb, voiceId, inflectionId) {
    const question = buildQuestion(verb, voiceId, inflectionId);
    if (!question) {
      return;
    }
    elements.groupFilter.value = verb.group;
    elements.voiceFilter.value = voiceId;
    elements.inflectionFilter.value = inflectionId;
    syncLevelFilterState();
    setReviewingMistakes(false);
    showView("verb");
    setVerbSubview("practice");
    startQuestion(question);
    elements.answerInput.focus();
  }

  function buildVerbMorphologyGuideRow(verb, form) {
    const row = document.createElement("tr");
    row.setAttribute("data-morphology-form", form.id);

    const labelCell = document.createElement("th");
    labelCell.scope = "row";
    labelCell.textContent = form.label;

    const usageCell = document.createElement("td");
    usageCell.textContent = form.usage;

    const exampleCell = document.createElement("td");
    exampleCell.className = "morphology-guide-examples";
    form.examples.forEach(function (example) {
      const result = engine.conjugate(verb, "base", example.inflectionId);
      const line = document.createElement("div");
      line.className = "morphology-guide-example-line";
      const label = document.createElement("small");
      label.textContent = example.label;
      const written = document.createElement("strong");
      written.textContent = result.written + (example.suffix || "");
      const reading = document.createElement("small");
      reading.className = "morphology-guide-reading";
      reading.textContent = result.kana + (example.readingSuffix || "");
      line.append(label, written, reading);
      exampleCell.append(line);
    });

    const practiceCell = document.createElement("td");
    practiceCell.className = "reference-practice morphology-guide-practice";
    const practiceButton = document.createElement("button");
    practiceButton.type = "button";
    practiceButton.className = "inline-button practice-link";
    practiceButton.textContent = form.practiceLabel;
    practiceButton.setAttribute(
      "aria-label",
      "练习 " + verb.dictionary + " 的" + form.label + "语境题"
    );
    practiceButton.addEventListener("click", function () {
      practiceVerbMorphology(verb, form.id);
    });
    practiceCell.append(practiceButton);

    row.append(labelCell, usageCell, exampleCell, practiceCell);
    return row;
  }

  function renderVerbMorphologyGuide(verb) {
    elements.verbMorphologyGuideCaption.textContent = "以当前动词「" + verb.dictionary + "」（"
      + verb.reading + "）的原形为例，不叠加上方选择的可能、受身或使役等变形。"
      + "每行按钮会按具体接续启动六形专项练习。";
    elements.verbMorphologyGuideTableBody.replaceChildren();
    VERB_MORPHOLOGY_FORMS.forEach(function (form) {
      elements.verbMorphologyGuideTableBody.append(
        buildVerbMorphologyGuideRow(verb, form)
      );
    });
  }

  function chooseRandomQuestion() {
    const groupId = elements.groupFilter.value;
    const filterKey = JSON.stringify([
      groupId,
      elements.voiceFilter.value,
      elements.inflectionFilter.value,
      elements.levelFilter.value
    ]);
    if (filterKey !== questionDeckFilterKey) {
      questionDeck = [];
      questionDeckFilterKey = filterKey;
    }

    const activeMistakeKeys = new Set(mistakes.map(function (mistake) { return mistake.key; }));
    questionDeck = questionDeck.filter(function (pair) { return !activeMistakeKeys.has(pair.key); });

    if (!questionDeck.length) {
      const verbs = VERBS.filter(function (verb) {
        return groupId === "all" || verb.group === groupId;
      });
      const targets = buildTargets();
      const pairs = [];

      verbs.forEach(function (verb) {
        targets.forEach(function (target) {
          pairs.push({
            verb: verb,
            voiceId: target.voiceId,
            inflectionId: target.inflectionId,
            morphologyFormId: target.morphologyFormId,
            key: getPairKey(verb, target.voiceId, target.inflectionId)
          });
        });
      });

      if (!pairs.length) {
        showToast("当前筛选范围没有可练习的题目。", true);
        return null;
      }
      const newPairs = pairs.filter(function (pair) { return !activeMistakeKeys.has(pair.key); });
      questionDeck = shuffleItems(newPairs.length ? newPairs : pairs);
    }

    const nextIndex = questionDeck.length - 1;
    if (nextIndex > 0) {
      const nextPair = questionDeck[nextIndex];
      const repeatsQuestion = nextPair.key === lastPairKey;
      const repeatsRecentVerb = recentQuestionVerbIds.includes(nextPair.verb.id);
      if (repeatsQuestion || repeatsRecentVerb) {
        let alternatives = [];
        for (let index = 0; index < nextIndex; index += 1) {
          const candidate = questionDeck[index];
          if (candidate.key !== lastPairKey && !recentQuestionVerbIds.includes(candidate.verb.id)) {
            alternatives.push(index);
          }
        }
        if (!alternatives.length && repeatsQuestion) {
          for (let index = 0; index < nextIndex; index += 1) {
            if (questionDeck[index].key !== lastPairKey) {
              alternatives.push(index);
            }
          }
        }
        if (alternatives.length) {
          const swapIndex = randomItem(alternatives);
          [questionDeck[nextIndex], questionDeck[swapIndex]] =
            [questionDeck[swapIndex], questionDeck[nextIndex]];
        }
      }
    }

    const pair = questionDeck.pop();
    lastPairKey = pair.key;
    return {
      verb: pair.verb,
      voiceId: pair.voiceId,
      inflectionId: pair.inflectionId,
      morphologyFormId: pair.morphologyFormId,
      result: engine.conjugate(pair.verb, pair.voiceId, pair.inflectionId),
      key: pair.key
    };
  }

  function setVerbPromptDetailsVisible(visible) {
    if (!currentQuestion) {
      return;
    }
    elements.questionGroup.textContent = visible
      ? GROUP_LABELS[currentQuestion.verb.group]
      : "动词";
    elements.questionReading.textContent = visible ? currentQuestion.verb.reading : "假名";
    elements.verbPrompt.setAttribute("aria-expanded", String(visible));
  }

  function toggleVerbPromptDetails() {
    const expanded = elements.verbPrompt.getAttribute("aria-expanded") === "true";
    setVerbPromptDetailsVisible(!expanded);
  }

  function startQuestion(question) {
    if (!question) {
      return;
    }
    currentQuestion = question;
    elements.practiceCard.classList.remove("is-answer-correct");
    elements.practiceCard.classList.remove("is-answer-incorrect");
    recentQuestionVerbIds = [question.verb.id]
      .concat(recentQuestionVerbIds.filter(function (verbId) { return verbId !== question.verb.id; }))
      .slice(0, 3);
    renderReviewBanner();
    const morphologyForm = VERB_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === question.morphologyFormId;
    })[0];
    elements.questionIndex.textContent = morphologyForm
      ? "六形专项 · " + morphologyForm.label
      : reviewingMistakes
        ? "错题重练"
        : question.isScheduledReview ? "间隔复习" : "随机练习";
    elements.targetPill.textContent = morphologyForm
      ? question.voiceId === "base"
        ? morphologyForm.targetLabel
        : getVoiceLabel(question.voiceId) + " · " + morphologyForm.targetLabel
      : getTargetLabel(question.voiceId, question.inflectionId);
    elements.questionMeaning.textContent = question.verb.meaning;
    elements.questionWord.textContent = question.verb.dictionary;
    setVerbPromptDetailsVisible(false);

    if (morphologyForm) {
      elements.questionInstruction.innerHTML = question.voiceId === "base"
        ? morphologyForm.instruction
        : "请先变为<strong class=\"keyword-highlight\">"
          + getVoiceLabel(question.voiceId) + "</strong>，再按接续写出目标形：<br>"
          + morphologyForm.instruction;
    } else if (question.voiceId === "base") {
      elements.questionInstruction.innerHTML = "请把 <strong class=\"keyword-highlight\">"
        + getInflectionLabel(question.inflectionId) + "</strong> 写出来。";
    } else {
      elements.questionInstruction.innerHTML = "请将动词变为 <strong class=\"keyword-highlight\">"
        + getVoiceLabel(question.voiceId) + "</strong>，再活用成 <strong class=\"keyword-highlight\">"
        + getInflectionLabel(question.inflectionId) + "</strong>。";
    }

    elements.answerInput.value = "";
    elements.answerInput.disabled = false;
    elements.submitAnswer.disabled = false;
    elements.revealAnswer.disabled = false;
    elements.feedbackPanel.hidden = true;
    elements.feedbackPanel.classList.remove("is-wrong");
    elements.nextQuestion.hidden = true;
    elements.ruleHint.hidden = true;
    elements.hintButton.textContent = "显示规律提示";
    elements.hintButton.setAttribute("aria-expanded", "false");
    renderRuleExplanation(
      elements.ruleHint,
      engine.explain(question.verb, question.voiceId, question.inflectionId)
    );

    elements.practiceCard.classList.remove("is-question-entering");
    void elements.practiceCard.offsetWidth;
    elements.practiceCard.classList.add("is-question-entering");
  }

  function newRandomQuestion() {
    const wasReviewing = reviewingMistakes;
    setReviewingMistakes(false);
    startQuestion(chooseDueReviewQuestion() || chooseRandomQuestion());
    if (wasReviewing) {
      showToast("已退出错题重练，回到练习。", false);
    }
  }

  function exitMistakeReview() {
    if (!reviewingMistakes) {
      return;
    }
    newRandomQuestion();
  }

  function normalizeText(value) {
    return engine.normalizeText(value);
  }

  function normalizeAnswer(value) {
    return engine.normalizeAnswer(value);
  }

  // 归一化明细：{ answer, romajiFailed }。romajiFailed = 纯罗马字输入但拼写无法识别
  // （IK-10：避免用户把「无法识别的输入」误当成「变形错误」）。
  function normalizeAnswerDetailed(value) {
    return engine.normalizeAnswerDetailed(value);
  }

  function addDays(date, days) {
    return new Date(date.getTime() + days * 24 * 60 * 60 * 1000).toISOString();
  }

  function formatReviewDate(dueAt) {
    const dueTimestamp = Date.parse(dueAt);
    if (!Number.isFinite(dueTimestamp) || dueTimestamp <= Date.now()) {
      return "现在";
    }
    return new Date(dueTimestamp).toLocaleDateString("zh-CN", { month: "numeric", day: "numeric" });
  }

  function recordMistake(question, answer) {
    const now = new Date();
    const updatedAt = now.toISOString();
    const existing = mistakes.find(function (item) { return item.key === question.key; });
    if (existing) {
      existing.count += 1;
      existing.wrongAnswer = answer || "（未作答）";
      existing.updatedAt = updatedAt;
    } else if (question.key.indexOf(ADJ_MISTAKE_KEY_PREFIX) === 0) {
      mistakes.push({
        key: question.key,
        module: "adjective",
        adjectiveId: question.adjective.id,
        formId: question.formId,
        count: 1,
        wrongAnswer: answer || "（未作答）",
        updatedAt: updatedAt,
        correctStreak: 0,
        intervalDays: 1,
        dueAt: addDays(now, 1),
        sessionDueAtAnswerCount: null
      });
    } else {
      mistakes.push({
        key: question.key,
        verbId: question.verb.id,
        voiceId: question.voiceId,
        inflectionId: question.inflectionId,
        count: 1,
        wrongAnswer: answer || "（未作答）",
        updatedAt: updatedAt,
        correctStreak: 0,
        intervalDays: 1,
        dueAt: addDays(now, 1),
        sessionDueAtAnswerCount: null
      });
    }

    const mistake = existing || mistakes[mistakes.length - 1];
    mistake.correctStreak = 0;
    mistake.intervalDays = 1;
    mistake.dueAt = addDays(now, 1);
    mistake.sessionDueAtAnswerCount = sessionAnswerCount + SESSION_RETRY_AFTER_ANSWERS;
    saveMistakes();
  }

  function recordSuccessfulReview(question) {
    const mistake = mistakes.find(function (item) { return item.key === question.key; });
    if (!mistake) {
      return;
    }

    const now = new Date();
    mistake.correctStreak += 1;
    mistake.updatedAt = now.toISOString();
    mistake.sessionDueAtAnswerCount = null;
    if (mistake.correctStreak >= MASTERY_CORRECT_STREAK) {
      removeMistake(mistake.key);
      return;
    }

    mistake.intervalDays = REVIEW_INTERVALS_DAYS[mistake.correctStreak - 1];
    mistake.dueAt = addDays(now, mistake.intervalDays);
    saveMistakes();
  }

  function removeMistake(key) {
    const before = mistakes.length;
    mistakes = mistakes.filter(function (item) { return item.key !== key; });
    if (mistakes.length !== before) {
      saveMistakes();
    }
  }

  function submitAnswer(event) {
    event.preventDefault();
    if (composing || (event && event.isComposing)) {
      // 输入法组字期间的回车只确认候选，不作为提交，避免半截答案入库
      return;
    }
    if (!currentQuestion) {
      return;
    }

    const rawAnswer = elements.answerInput.value.trim();
    if (!rawAnswer) {
      elements.answerInput.reportValidity();
      return;
    }

    setVerbPromptDetailsVisible(true);
    const normalized = normalizeAnswerDetailed(rawAnswer);
    const normalizedAnswer = normalized.answer;
    const expected = new Set([
      normalizeAnswer(currentQuestion.result.written),
      normalizeAnswer(currentQuestion.result.kana)
    ]);
    const correct = expected.has(normalizedAnswer);
    sessionAnswerCount += 1;

    elements.answerInput.disabled = true;
    elements.submitAnswer.disabled = true;
    elements.feedbackPanel.hidden = false;
    elements.feedbackPanel.classList.toggle("is-wrong", !correct);
    elements.feedbackMark.textContent = correct ? "✓" : "!";
    elements.feedbackTitle.textContent = correct ? "答对了" : "再记一下这个变化";
    const romajiInput = /^[a-z']+$/i.test(rawAnswer);
    elements.feedbackUserAnswer.textContent = correct && romajiInput
      ? "你的答案：" + rawAnswer + "（" + normalizedAnswer + "）"
      : "你的答案：" + rawAnswer;
    elements.feedbackCorrectAnswer.textContent = currentQuestion.result.written;
    elements.feedbackReading.textContent = currentQuestion.result.kana === currentQuestion.result.written
      ? ""
      : "读音：" + currentQuestion.result.kana;
    const voice = VOICE_BY_ID.get(currentQuestion.voiceId);
    const inflection = INFLECTION_BY_ID.get(currentQuestion.inflectionId);
    elements.feedbackVoiceMeaning.hidden = currentQuestion.voiceId === "base";
    elements.feedbackVoiceMeaning.textContent = "语态：" + voice.meaning;
    elements.feedbackInflectionMeaning.textContent = (currentQuestion.voiceId === "base" ? "" : "活用：")
      + inflection.meaning;
    renderRuleExplanation(
      elements.feedbackRule,
      engine.explain(currentQuestion.verb, currentQuestion.voiceId, currentQuestion.inflectionId)
    );
    elements.nextQuestion.hidden = false;

    if (correct) {
      recordSuccessfulReview(currentQuestion);
      showToast("正确。留意上面的变化规则。", false);
    } else {
      recordMistake(currentQuestion, rawAnswer);
      if (normalized.romajiFailed) {
        // 罗马字拼写无法识别（IK-10）：明示按原样比对，避免用户误以为是变形错误
        elements.feedbackUserAnswer.textContent =
          "你的答案：" + rawAnswer + "（罗马字未能识别，按原样比对）";
        showToast("无法识别该罗马字拼写，已按原样比对；可改用假名输入。", true);
      } else {
        showToast("已加入错题，稍后可以重练。", false);
      }
    }

    updateMistakeCount();
    updatePracticeStats(correct);
    updateDailyStreak("verb");
    elements.nextQuestion.focus();
  }

  // IK-16 查看答案通道：卡题时揭示正解。按「未答对」记账——计入错题
  // （wrongAnswer 记「（查看答案）」）、统计计错、不当答对处理；与 Android
  // 「未提示答对才计掌握度」口径一致（D-020），Web 暂无按词条掌握度故无需额外标记。
  function revealVerbAnswer() {
    if (!currentQuestion || elements.submitAnswer.disabled) {
      return; // 已判定或已查看答案，不可重复触发
    }
    setVerbPromptDetailsVisible(true);
    sessionAnswerCount += 1;

    elements.answerInput.disabled = true;
    elements.submitAnswer.disabled = true;
    elements.revealAnswer.disabled = true;
    elements.feedbackPanel.hidden = false;
    elements.feedbackPanel.classList.add("is-wrong");
    elements.feedbackMark.textContent = "!";
    elements.feedbackTitle.textContent = "已查看答案";
    elements.feedbackUserAnswer.textContent = "你的答案：（查看了答案，未作答）";
    elements.feedbackCorrectAnswer.textContent = currentQuestion.result.written;
    elements.feedbackReading.textContent = currentQuestion.result.kana === currentQuestion.result.written
      ? ""
      : "读音：" + currentQuestion.result.kana;
    const voice = VOICE_BY_ID.get(currentQuestion.voiceId);
    const inflection = INFLECTION_BY_ID.get(currentQuestion.inflectionId);
    elements.feedbackVoiceMeaning.hidden = currentQuestion.voiceId === "base";
    elements.feedbackVoiceMeaning.textContent = "语态：" + voice.meaning;
    elements.feedbackInflectionMeaning.textContent = (currentQuestion.voiceId === "base" ? "" : "活用：")
      + inflection.meaning;
    renderRuleExplanation(
      elements.feedbackRule,
      engine.explain(currentQuestion.verb, currentQuestion.voiceId, currentQuestion.inflectionId)
    );
    elements.nextQuestion.hidden = false;

    recordMistake(currentQuestion, "（查看答案）");
    updateMistakeCount();
    updatePracticeStats(false);
    updateDailyStreak("verb");
    showToast("已查看答案，本题记入错题，稍后重练。", false);
    elements.nextQuestion.focus();
  }

  function updateMistakeCount() {
    elements.mistakeCount.textContent = String(mistakes.length);
    elements.homeMistakeCount.textContent = String(mistakes.length);
    renderReviewBanner();
  }

  // 重练模式必须有常驻提示：否则「换一题」会静默退出，用户不知道自己已离开错题集。
  function renderReviewBanner() {
    elements.reviewBanner.hidden = !reviewingMistakes;
    if (reviewingMistakes) {
      const remaining = mistakes.filter(function (mistake) {
        return !reviewedMistakeKeys.has(mistake.key);
      }).length;
      elements.reviewBannerCount.textContent = "本轮待练 " + remaining
        + " 题，完成 " + MASTERY_CORRECT_STREAK + " 次正确复习后移出";
    }
  }

  function setReviewingMistakes(value) {
    reviewingMistakes = Boolean(value);
    renderReviewBanner();
  }

  function renderIntro(viewId) {
    const copy = INTRO_COPY[viewId] || INTRO_COPY.verb;
    elements.introEyebrow.textContent = copy.eyebrow;
    const accent = document.createElement("span");
    accent.textContent = copy.accent;
    elements.introTitle.replaceChildren(document.createTextNode(copy.main), accent);
    elements.introCopy.textContent = copy.copy;
  }

  function renderHomeDashboard() {
    const isToday = dailyRecord.lastDate === localDateString(new Date());
    const verbCount = isToday ? dailyRecord.verbAnswers : 0;
    const adjectiveCount = isToday ? dailyRecord.adjectiveAnswers : 0;
    const particleCount = isToday ? dailyRecord.particleAnswers : 0;
    const total = verbCount + adjectiveCount + particleCount;
    const activeModules = Number(verbCount > 0) + Number(adjectiveCount > 0) + Number(particleCount > 0);

    elements.homeTodayTotal.textContent = total + " 题";
    elements.homeTodayBreakdown.textContent = "动词 " + verbCount + " 题 · 形容词 "
      + adjectiveCount + " 题 · 助词 " + particleCount + " 题";
    elements.homeStreakCount.textContent = String(getCurrentStreak());
    elements.homeActiveModuleCount.textContent = String(activeModules);
    elements.homeVerbToday.textContent = verbCount + " 题";
    elements.homeVerbStatus.textContent = verbCount > 0 ? "今日已练" : "今日尚未练习";
    elements.homeAdjectiveToday.textContent = adjectiveCount + " 题";
    elements.homeAdjectiveStatus.textContent = adjectiveCount > 0 ? "今日已练" : "今日尚未练习";
    elements.homeParticleToday.textContent = particleCount + " 题";
    elements.homeParticleStatus.textContent = particleCount > 0 ? "今日已练" : "今日尚未练习";
  }

  function setMobileSidebarOpen(open, restoreFocus) {
    const isOpen = Boolean(open);
    elements.appShell.classList.toggle("is-sidebar-open", isOpen);
    elements.mobileNavToggle.setAttribute("aria-expanded", String(isOpen));
    elements.mobileNavToggle.setAttribute("aria-label", isOpen ? "关闭导航" : "打开导航");
    elements.sidebarBackdrop.hidden = !isOpen;
    if (isOpen && elements.tabs.length) {
      elements.tabs[0].focus();
    } else if (restoreFocus) {
      elements.mobileNavToggle.focus();
    }
  }

  function setVerbSubview(subview) {
    currentVerbSubview = subview === "reference" ? "reference" : "practice";
    const showPractice = currentVerbSubview === "practice";
    elements.verbSubviewPractice.hidden = !showPractice;
    elements.verbSubviewReference.hidden = showPractice;
    elements.verbSubtabs.forEach(function (subtab) {
      const active = subtab.dataset.verbSubview === currentVerbSubview;
      subtab.classList.toggle("is-active", active);
      subtab.setAttribute("aria-selected", String(active));
    });
    if (activeViewId === "verb" && !showPractice) {
      renderReferenceTable();
    }
    announce("动词 · " + (showPractice ? "练习" : "速查"));
  }

  // 形容词模块二级切换：练习（做题训练）与速查（规则查阅）分视，
  // 切换只改子视图可见性，不重置练习状态；跨模块入口一律显式指定子视图。
  function setAdjectiveSubview(subview) {
    const showPractice = subview !== "reference";
    elements.adjSubviewPractice.hidden = !showPractice;
    elements.adjSubviewReference.hidden = showPractice;
    elements.adjSubtabs.forEach(function (subtab) {
      const active = subtab.dataset.adjSubview === (showPractice ? "practice" : "reference");
      subtab.classList.toggle("is-active", active);
      subtab.setAttribute("aria-selected", String(active));
    });
    announce("形容词 · " + (showPractice ? "练习" : "速查"));
  }

  function setParticleSubview(subview) {
    if (subview !== "learn" && subview !== "practice" && subview !== "review") {
      throw new RangeError("Unknown particle subview: " + subview);
    }
    currentParticleSubview = subview;
    const headingKey = subview === "practice" && reviewingParticleMistakes ? "review" : subview;
    const heading = PARTICLE_SUBVIEW_COPY[headingKey];
    elements.particleViewEyebrow.textContent = heading.eyebrow;
    elements.particleViewTitle.textContent = heading.title;
    elements.particleViewDescription.textContent = heading.copy;
    elements.particleSubviewLearn.hidden = subview !== "learn";
    elements.particleSubviewPractice.hidden = subview !== "practice";
    elements.particleSubviewReview.hidden = subview !== "review";
    elements.particleLevelControls.hidden = subview === "review";
    elements.particleControlsPanel.hidden = subview === "review"
      || (subview === "practice" && reviewingParticleMistakes);
    elements.particleControlsPanel.classList.toggle("is-single-column", subview === "learn");
    elements.particlePracticeModes.hidden = subview !== "practice" || reviewingParticleMistakes;
    elements.particleSubtabs.forEach(function (subtab) {
      const active = subtab.dataset.particleSubview === subview;
      subtab.classList.toggle("is-active", active);
      subtab.setAttribute("aria-selected", String(active));
    });
    if (subview === "learn") {
      renderParticleLessons();
    } else if (subview === "review") {
      renderParticleMistakes();
    }
    announce("助词 · " + heading.title);
    elements.appMain.scrollTop = 0;
  }

  function setParticleLevel(level) {
    if (!particleEngine.LEVELS.some(function (item) { return item.id === level; })) {
      throw new RangeError("Unknown particle learning level: " + level);
    }
    currentParticleLevel = level;
    elements.particleLevelSelect.value = level;
    renderParticleLessons();
    if (currentParticleSubview === "practice" && !reviewingParticleMistakes) {
      startParticlePractice(particleTargetLessonId);
    }
  }

  function setParticlePracticeMode(mode) {
    if (mode !== "mixed" && !particleEngine.QUESTION_TYPES.some(function (type) {
      return type.id === mode;
    })) {
      throw new RangeError("Unknown particle practice mode: " + mode);
    }
    if (currentParticlePracticeMode === mode) {
      return;
    }
    currentParticlePracticeMode = mode;
    elements.particlePracticeModeSelect.value = mode;
    elements.particlePracticeModeHint.textContent = mode === "mixed"
      ? "两种题型交替出现"
      : mode === "fill" ? "集中练习选择合适的助词" : "集中判断助词在句中的作用";
    if (currentParticleSubview === "practice" && !reviewingParticleMistakes) {
      startParticlePractice(particleTargetLessonId);
    }
  }

  function createParticleTextElement(tagName, className, text) {
    const element = document.createElement(tagName);
    element.className = className;
    element.textContent = text;
    return element;
  }

  function renderParticleLessons() {
    elements.particleLessonGrid.replaceChildren();
    particleEngine.LESSONS.forEach(function (lesson) {
      const card = document.createElement("article");
      card.className = "particle-lesson-card";

      const heading = document.createElement("div");
      heading.className = "particle-lesson-heading";
      const title = createParticleTextElement("h3", "", lesson.title);
      const particles = createParticleTextElement("span", "particle-lesson-particles", lesson.particles.join("／"));
      heading.append(title, particles);

      const summary = createParticleTextElement(
        "p",
        "particle-lesson-summary",
        currentParticleLevel === "beginner" ? lesson.beginnerSummary : lesson.advancedSummary
      );
      const examples = document.createElement("div");
      examples.className = "particle-lesson-examples";
      lesson.examples.forEach(function (example) {
        const item = document.createElement("article");
        item.className = "particle-lesson-example";
        item.append(
          createParticleTextElement("span", "particle-example-context", example.context),
          createParticleTextElement("strong", "particle-example-japanese", example.sentence),
          createParticleTextElement("small", "particle-example-reading", example.reading),
          createParticleTextElement("span", "particle-example-translation", example.translation),
          createParticleTextElement("span", "particle-example-note", example.note)
        );
        if (example.contrastNote) {
          item.append(
            createParticleTextElement(
              "span",
              "particle-example-contrast",
              "换个助词会怎样：" + example.contrastNote
            )
          );
        }
        examples.append(item);
      });

      const action = document.createElement("button");
      action.className = "outline-button particle-lesson-start";
      action.type = "button";
      action.textContent = "练习这一组";
      action.addEventListener("click", function () {
        startParticlePractice(lesson.id);
      });
      card.append(heading, summary, examples, action);
      elements.particleLessonGrid.append(card);
    });
  }

  function renderParticleQuestion() {
    if (!currentParticleQuestion) {
      throw new RangeError("No active particle question.");
    }
    const question = currentParticleQuestion;
    const isFunctionQuestion = question.type === "function";
    elements.particleQuestionCard.setAttribute("data-question-id", question.id);
    elements.particleQuestionCard.setAttribute("data-question-type", question.type);
    const level = particleEngine.LEVELS.filter(function (item) {
      return item.id === question.level;
    })[0];
    if (!level) {
      throw new RangeError("Unknown particle question level: " + question.level);
    }

    const practiceLabel = currentParticlePracticeMode === "mixed"
      ? "综合练习"
      : particleEngine.QUESTION_TYPES.filter(function (type) {
        return type.id === currentParticlePracticeMode;
      })[0].label + "练习";
    elements.particleQuestionProgress.textContent = (reviewingParticleMistakes ? "错题复习" : practiceLabel)
      + " · 第 " + (particleQuestionIndex + 1) + " / " + particleQuestionQueue.length + " 题";
    elements.particleQuestionTitle.textContent = isFunctionQuestion ? "判断助词作用" : "选择合适的助词";
    const lesson = particleEngine.getLesson(question.lessonId);
    if (!lesson) {
      throw new RangeError("Unknown particle lesson: " + question.lessonId);
    }
    elements.particleQuestionTarget.textContent = lesson.particles.join("／");
    elements.particleQuestionContext.textContent = question.context;
    elements.particleQuestionPrompt.textContent = isFunctionQuestion
      ? "句中的「" + question.targetParticle + "」在这里起什么作用？"
      : "根据语境，选择空格处最合适的助词。";
    elements.particleSentenceBefore.textContent = question.before;
    elements.particleSentenceAfter.textContent = question.after;
    elements.particleAnswerSlot.textContent = isFunctionQuestion ? question.targetParticle : "＿";
    elements.particleAnswerSlot.classList.toggle("is-target-particle", isFunctionQuestion);
    elements.particleQuestionReading.textContent = question.readingBefore
      + (isFunctionQuestion ? question.targetReading : "＿")
      + question.readingAfter;
    elements.particleQuestionTranslation.textContent = question.translation;
    elements.particleQuestionTranslation.hidden = isFunctionQuestion;
    elements.particleFeedback.hidden = true;
    elements.particleFeedback.classList.remove("is-correct", "is-incorrect");
    elements.particleFeedbackNote.hidden = true;
    elements.particleFeedbackNote.textContent = "";
    elements.particleNextQuestion.hidden = true;
    elements.particleCheckAnswer.disabled = true;
    selectedParticleAnswer = "";
    particleAnswered = false;
    elements.particleOptions.replaceChildren();
    elements.particleOptions.classList.toggle("is-function-question", isFunctionQuestion);
    elements.particleOptions.setAttribute(
      "aria-label",
      isFunctionQuestion ? "选择助词在句中的作用" : "选择合适的助词"
    );
    elements.particleCheckAnswer.textContent = isFunctionQuestion ? "查看解析" : "检查判断";

    shuffleItems(question.options.slice()).forEach(function (option) {
      const button = document.createElement("button");
      button.className = "particle-option";
      button.type = "button";
      button.setAttribute("aria-pressed", "false");
      button.textContent = option;
      button.addEventListener("click", function () {
        if (particleAnswered) {
          return;
        }
        selectedParticleAnswer = option;
        if (!isFunctionQuestion) {
          elements.particleAnswerSlot.textContent = option;
        }
        elements.particleCheckAnswer.disabled = false;
        elements.particleOptions.querySelectorAll("button").forEach(function (candidate) {
          const selected = candidate.textContent === option;
          candidate.classList.toggle("is-selected", selected);
          candidate.setAttribute("aria-pressed", String(selected));
        });
      });
      elements.particleOptions.append(button);
    });
  }

  function renderParticleMistakes() {
    elements.particleReviewList.replaceChildren();
    const dueCount = countDueParticleMistakes();
    elements.particleMistakeCount.textContent = String(particleMistakes.length);
    elements.particleReviewCount.textContent = particleMistakes.length
      ? "本地保存 " + particleMistakes.length + " 道助词错题 · " + dueCount + " 道今天到期"
      : "还没有需要复习的助词错题";
    elements.particleReviewEmpty.hidden = particleMistakes.length > 0;
    elements.particleStartReview.disabled = particleMistakes.length === 0;
    elements.particleStartReview.textContent = particleMistakes.length
      ? "开始复习 " + particleMistakes.length + " 道"
      : "开始复习";

    particleMistakes.forEach(function (record) {
      const question = particleEngine.getQuestion(record.questionId);
      if (!question) {
        throw new RangeError("Unknown stored particle question: " + record.questionId);
      }
      const lesson = particleEngine.getLesson(question.lessonId);
      if (!lesson) {
        throw new RangeError("Unknown particle lesson: " + question.lessonId);
      }
      const questionTypeLabel = question.type === "function" ? "判作用" : "选助词";
      const item = document.createElement("article");
      item.className = "particle-review-item";
      const sentence = document.createElement("p");
      if (question.type === "function") {
        sentence.append(
          createParticleTextElement("span", "", question.before),
          createParticleTextElement("mark", "particle-review-target", question.targetParticle),
          createParticleTextElement("span", "", question.after)
        );
      } else {
        sentence.textContent = question.before + "＿" + question.after;
      }
      const streakLabel = record.correctStreak > 0
        ? " · 已连对 " + record.correctStreak + "/" + MASTERY_CORRECT_STREAK
        : "";
      item.append(
        createParticleTextElement("strong", "", lesson.title),
        createParticleTextElement("span", "particle-review-type", questionTypeLabel),
        createParticleTextElement("span", "", question.context),
        sentence,
        createParticleTextElement(
          "span",
          "particle-review-meta",
          "错 " + record.count + " 次" + streakLabel + " · 下次复习：" + formatReviewDate(record.dueAt)
        )
      );
      elements.particleReviewList.append(item);
    });
  }

  function findParticleMistake(questionId) {
    return particleMistakes.filter(function (record) {
      return record.questionId === questionId;
    })[0] || null;
  }

  /** 到期判定：会话内重试到期，或跨会话计划到期（与动词/形容词同一套语义）。 */
  function isParticleMistakeDue(record, now) {
    if (!record) {
      return false;
    }
    if (record.sessionDueAtAnswerCount !== null
      && record.sessionDueAtAnswerCount <= sessionAnswerCount) {
      return true;
    }
    const dueTimestamp = Date.parse(record.dueAt);
    return Number.isFinite(dueTimestamp) ? dueTimestamp <= (now || new Date()).getTime() : true;
  }

  function countDueParticleMistakes() {
    const now = new Date();
    return particleMistakes.filter(function (record) {
      return isParticleMistakeDue(record, now);
    }).length;
  }

  /** 答错：新建或加重记错次数，并重置复习进度（D-022）。 */
  function recordParticleMistake(questionId, wrongAnswer) {
    if (!particleEngine.getQuestion(questionId)) {
      throw new RangeError("Unknown particle question: " + questionId);
    }
    const now = new Date();
    const existing = findParticleMistake(questionId);
    if (!existing) {
      particleMistakes = particleMistakes.concat([
        createParticleMistakeRecord(questionId, wrongAnswer, now)
      ]);
    } else {
      particleMistakes = particleMistakes.map(function (record) {
        if (record.questionId !== questionId) {
          return record;
        }
        return Object.assign({}, record, {
          count: record.count + 1,
          wrongAnswer: wrongAnswer || record.wrongAnswer,
          updatedAt: now.toISOString(),
          correctStreak: 0,
          intervalDays: 1,
          dueAt: addDays(now, 1),
          sessionDueAtAnswerCount: sessionAnswerCount + SESSION_RETRY_AFTER_ANSWERS
        });
      });
    }
    saveParticleMistakes();
    renderParticleMistakes();
  }

  /** 复习答对：推进一档间隔；连续答对达到毕业次数则移出（D-022）。 */
  function recordParticleReviewSuccess(questionId) {
    const existing = findParticleMistake(questionId);
    if (!existing) {
      return;
    }
    const nextStreak = existing.correctStreak + 1;
    if (nextStreak >= MASTERY_CORRECT_STREAK) {
      removeParticleMistake(questionId);
      showToast("连续答对 " + MASTERY_CORRECT_STREAK + " 次，这道助词错题已移出。");
      return;
    }
    const now = new Date();
    const interval = REVIEW_INTERVALS_DAYS[nextStreak - 1];
    particleMistakes = particleMistakes.map(function (record) {
      if (record.questionId !== questionId) {
        return record;
      }
      return Object.assign({}, record, {
        correctStreak: nextStreak,
        intervalDays: interval,
        dueAt: addDays(now, interval),
        updatedAt: now.toISOString(),
        sessionDueAtAnswerCount: null
      });
    });
    saveParticleMistakes();
    renderParticleMistakes();
  }

  function removeParticleMistake(questionId) {
    const remaining = particleMistakes.filter(function (record) {
      return record.questionId !== questionId;
    });
    if (remaining.length !== particleMistakes.length) {
      particleMistakes = remaining;
      saveParticleMistakes();
      renderParticleMistakes();
    }
  }

  function startParticlePractice(lessonId) {
    const questionType = currentParticlePracticeMode === "mixed" ? undefined : currentParticlePracticeMode;
    const questions = particleEngine.getQuestions(currentParticleLevel, lessonId, questionType);
    if (!questions.length) {
      showToast("这一组暂时没有当前层级的对应题型。", true);
      setParticleSubview("learn");
      return;
    }
    particleTargetLessonId = lessonId || "";
    reviewingParticleMistakes = false;
    particleQuestionQueue = buildParticleQuestionQueue(questions);
    particleQuestionIndex = 0;
    currentParticleQuestion = particleEngine.getQuestion(particleQuestionQueue[0]);
    setParticleSubview("practice");
    renderParticleQuestion();
  }

  function buildParticleQuestionQueue(questions) {
    if (currentParticlePracticeMode !== "mixed") {
      return shuffleItems(questions.map(function (question) {
        return question.id;
      }));
    }

    const fillQuestions = shuffleItems(questions.filter(function (question) {
      return question.type === "fill";
    }).map(function (question) {
      return question.id;
    }));
    const functionQuestions = shuffleItems(questions.filter(function (question) {
      return question.type === "function";
    }).map(function (question) {
      return question.id;
    }));
    if (!fillQuestions.length || !functionQuestions.length) {
      throw new RangeError("Mixed particle practice requires both question types.");
    }

    const queue = [];
    const rounds = Math.max(fillQuestions.length, functionQuestions.length);
    for (let index = 0; index < rounds; index += 1) {
      queue.push(
        fillQuestions[index % fillQuestions.length],
        functionQuestions[index % functionQuestions.length]
      );
    }
    return queue;
  }

  function startParticleReview() {
    if (!particleMistakes.length) {
      setParticleSubview("review");
      return;
    }
    reviewingParticleMistakes = true;
    particleTargetLessonId = "";
    particleQuestionQueue = shuffleItems(particleMistakes.map(function (record) {
      return record.questionId;
    }));
    particleQuestionIndex = 0;
    currentParticleQuestion = particleEngine.getQuestion(particleQuestionQueue[0]);
    setParticleSubview("practice");
    renderParticleQuestion();
  }

  function checkParticleAnswer() {
    if (!currentParticleQuestion || particleAnswered || !selectedParticleAnswer) {
      return;
    }
    particleAnswered = true;
    const correct = particleEngine.checkAnswer(currentParticleQuestion, selectedParticleAnswer);
    const accepted = currentParticleQuestion.acceptedAnswers.join("／");
    elements.particleFeedback.hidden = false;
    elements.particleQuestionTranslation.hidden = false;
    elements.particleFeedback.classList.toggle("is-correct", correct);
    elements.particleFeedback.classList.toggle("is-incorrect", !correct);
    elements.particleFeedbackTitle.textContent = correct ? "判断正确" : "再看一眼语境";
    elements.particleFeedbackAnswer.textContent = currentParticleQuestion.type === "function"
      ? "这里的「" + currentParticleQuestion.targetParticle + "」：" + accepted
      : "可接受答案：" + accepted;
    elements.particleFeedbackExplanation.textContent = currentParticleQuestion[
      currentParticleQuestion.level === "advanced" ? "advancedExplanation" : "beginnerExplanation"
    ];
    // 逐项说明（D-019）：只在答错且该选项有说明时出现，避免给正确项也加一段否定语气
    const optionNote = !correct && currentParticleQuestion.optionNotes
      ? currentParticleQuestion.optionNotes[selectedParticleAnswer]
      : undefined;
    if (optionNote) {
      elements.particleFeedbackNote.textContent = "你选的「" + selectedParticleAnswer + "」：" + optionNote;
      elements.particleFeedbackNote.hidden = false;
    } else {
      elements.particleFeedbackNote.hidden = true;
      elements.particleFeedbackNote.textContent = "";
    }
    elements.particleCheckAnswer.disabled = true;
    elements.particleOptions.querySelectorAll("button").forEach(function (button) {
      button.disabled = true;
    });
    elements.particleNextQuestion.hidden = false;
    sessionAnswerCount += 1;
    if (!correct) {
      recordParticleMistake(currentParticleQuestion.id, selectedParticleAnswer);
    } else if (reviewingParticleMistakes) {
      recordParticleReviewSuccess(currentParticleQuestion.id);
    }
    updateParticleStats(correct);
    updateDailyStreak("particle");
  }

  function nextParticleQuestion() {
    if (!particleAnswered || !particleQuestionQueue.length) {
      return;
    }
    if (reviewingParticleMistakes) {
      particleQuestionQueue.splice(particleQuestionIndex, 1);
      if (!particleQuestionQueue.length) {
        reviewingParticleMistakes = false;
        currentParticleQuestion = null;
        setParticleSubview("review");
        showToast("本轮助词错题复习完成。", false);
        return;
      }
      particleQuestionIndex %= particleQuestionQueue.length;
    } else {
      particleQuestionIndex += 1;
      if (particleQuestionIndex >= particleQuestionQueue.length) {
        const questionType = currentParticlePracticeMode === "mixed" ? undefined : currentParticlePracticeMode;
        particleQuestionQueue = buildParticleQuestionQueue(
          particleEngine.getQuestions(currentParticleLevel, particleTargetLessonId, questionType)
        );
        particleQuestionIndex = 0;
      }
    }
    currentParticleQuestion = particleEngine.getQuestion(particleQuestionQueue[particleQuestionIndex]);
    renderParticleQuestion();
  }

  // options.focus === false 时只切视图不抢焦点（初始装载用，避免 h1 出现焦点环）。
  function showView(viewId, options) {
    if (!Object.prototype.hasOwnProperty.call(elements.views, viewId)) {
      throw new RangeError("Unknown view: " + viewId);
    }
    const moveFocus = !options || options.focus !== false;
    activeViewId = viewId;
    Object.keys(elements.views).forEach(function (id) {
      elements.views[id].hidden = id !== viewId;
    });
    elements.verbSubviewNav.hidden = viewId !== "verb";
    elements.particleSubviewNav.hidden = viewId !== "particle";
    setMobileSidebarOpen(false, false);
    elements.tabs.forEach(function (tab) {
      const active = tab.dataset.view === viewId;
      tab.classList.toggle("is-active", active);
      if (active) {
        tab.setAttribute("aria-current", "page");
      } else {
        tab.removeAttribute("aria-current");
      }
    });
    elements.homeLinks.forEach(function (link) {
      if (viewId === "home") {
        link.setAttribute("aria-current", "page");
      } else {
        link.removeAttribute("aria-current");
      }
    });
    renderIntro(viewId);
    // IK-18：视图切换后把焦点移到视图主标题（h1 已设 tabindex=-1），
    // 键盘与屏幕阅读器用户从新内容顶部开始，而非留在原标签上。
    if (moveFocus) {
      elements.introTitle.focus();
    }
    if (VIEW_ANNOUNCE_LABELS[viewId]) {
      announce("已进入" + VIEW_ANNOUNCE_LABELS[viewId]);
    }
    elements.appMain.scrollTop = 0;

    if (viewId === "verb") {
      setVerbSubview(currentVerbSubview);
    }
    if (viewId === "particle") {
      setParticleSubview(currentParticleSubview);
    }
    if (viewId === "home") {
      renderHomeDashboard();
    }
    if (viewId === "mistakes") {
      renderMistakes();
    }
  }

  function renderMistakes() {
    const verbMistakes = mistakes.filter(function (mistake) {
      return !isAdjectiveMistake(mistake);
    });
    const adjectiveMistakes = mistakes.filter(function (mistake) {
      return isAdjectiveMistake(mistake);
    });
    renderMistakeGroup(
      elements.verbMistakesList,
      elements.verbMistakesGroup,
      elements.verbMistakesCount,
      verbMistakes
    );
    renderMistakeGroup(
      elements.adjectiveMistakesList,
      elements.adjectiveMistakesGroup,
      elements.adjectiveMistakesCount,
      adjectiveMistakes
    );
    elements.mistakesEmpty.hidden = mistakes.length > 0;
    elements.reviewMistakes.disabled = verbMistakes.length === 0;
    elements.adjReviewMistakes.disabled = adjectiveMistakes.length === 0;
    elements.clearMistakes.disabled = mistakes.length === 0;
    elements.exportMistakes.disabled = mistakes.length === 0;
    updateMistakeCount();
  }

  function renderMistakeGroup(listElement, groupElement, countElement, groupMistakes) {
    groupElement.hidden = groupMistakes.length === 0;
    countElement.textContent = groupMistakes.length
      ? "共 " + groupMistakes.length + " 道"
      : "";
    listElement.replaceChildren();
    groupMistakes.slice().sort(function (a, b) {
      return b.updatedAt.localeCompare(a.updatedAt);
    }).forEach(function (mistake) {
      const card = buildMistakeCard(mistake);
      if (card) {
        listElement.append(card);
      }
    });
  }

  function buildMistakeCard(mistake) {
    const question = questionFromMistake(mistake);
    if (!question) {
      return null;
    }
    const isAdjective = isAdjectiveMistake(mistake);
    const card = document.createElement("article");
    card.className = "mistake-card";
    const description = document.createElement("div");
    const heading = document.createElement("h3");
    heading.textContent = isAdjective ? question.adjective.word : question.verb.dictionary;
    const reading = document.createElement("span");
    reading.textContent = isAdjective
      ? question.adjective.kana + " · " + getAdjectiveFormLabel(question.formId)
      : question.verb.reading + " · " + getTargetLabel(question.voiceId, question.inflectionId);
    heading.append(reading);
    const meaning = document.createElement("p");
    meaning.textContent = isAdjective
      ? question.adjective.meaning + " · " + ADJ_GROUP_LABELS[question.adjective.kind] + " · 错 " + mistake.count + " 次"
      : question.verb.meaning + " · " + GROUP_LABELS[question.verb.group] + " · 错 " + mistake.count + " 次";
    const progress = document.createElement("p");
    progress.className = "mistake-progress";
    const remainingAnswers = Number.isFinite(mistake.sessionDueAtAnswerCount)
      ? mistake.sessionDueAtAnswerCount - sessionAnswerCount
      : 0;
    progress.textContent = "连续答对 " + mistake.correctStreak + "/" + MASTERY_CORRECT_STREAK
      + " · " + (remainingAnswers > 0
        ? "本轮再答 " + remainingAnswers + " 题后重现"
        : "下次复习：" + formatReviewDate(mistake.dueAt));
    const correctAnswer = document.createElement("p");
    correctAnswer.className = "mistake-answer";
    correctAnswer.textContent = "正确答案：" + question.result.written
      + (mistake.wrongAnswer ? " · 上次作答：" + mistake.wrongAnswer : "");
    description.append(heading, meaning, progress, correctAnswer);

    const actions = document.createElement("div");
    actions.className = "mistake-card-actions";
    const practiceButton = document.createElement("button");
    practiceButton.type = "button";
    practiceButton.textContent = "练这题";
    practiceButton.addEventListener("click", function () {
      reviewedMistakeKeys = new Set();
      if (isAdjective) {
        setReviewingMistakes(false);
        setReviewingAdjectiveMistakes(true);
        showView("adjective");
        setAdjectiveSubview("practice");
        startAdjectiveQuestion(questionFromMistake(mistake));
        elements.adjAnswerInput.focus();
        return;
      }
      setReviewingAdjectiveMistakes(false);
      setReviewingMistakes(true);
      showView("verb");
      setVerbSubview("practice");
      startQuestion(questionFromMistake(mistake));
      elements.answerInput.focus();
    });
    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "remove-mistake";
    removeButton.textContent = "移除";
    removeButton.addEventListener("click", function () {
      removeMistake(mistake.key);
      updateMistakeCount();
      renderMistakes();
    });
    actions.append(practiceButton, removeButton);
    card.append(description, actions);
    return card;
  }

  function clearMistakes() {
    if (!mistakes.length) {
      return;
    }
    if (!window.confirm("确定清空所有错题吗？（含动词与形容词错题）")) {
      return;
    }
    mistakes = [];
    reviewedMistakeKeys = new Set();
    saveMistakes();
    setReviewingMistakes(false);
    setReviewingAdjectiveMistakes(false);
    updateMistakeCount();
    renderMistakes();
    showToast("错题已清空。", false);
  }

  function startMistakeReview() {
    // 只重练动词错题；形容词错题走形容词视图内的「重练形容词错题」。
    const verbMistakes = mistakes.filter(function (mistake) {
      return !isAdjectiveMistake(mistake);
    });
    if (!verbMistakes.length) {
      showToast("没有动词错题；形容词错题请在形容词练习里重练。", false);
      return;
    }
    reviewedMistakeKeys = new Set();
    setReviewingAdjectiveMistakes(false);
    setReviewingMistakes(true);
    showView("verb");
    setVerbSubview("practice");
    const mistake = weightedItem(verbMistakes, mistakeWeight);
    startQuestion(questionFromMistake(mistake));
    elements.answerInput.focus();
  }

  // ===================== 错题导出 / 导入 =====================

  function buildMistakesExport() {
    // sessionDueAtAnswerCount 属于当前会话的调度状态，不随文件跨设备。
    return JSON.stringify(mistakes.map(function (mistake) {
      const copy = Object.assign({}, mistake);
      delete copy.sessionDueAtAnswerCount;
      return copy;
    }), null, 2);
  }

  function exportMistakes() {
    if (!mistakes.length) {
      return;
    }
    const text = buildMistakesExport();
    try {
      const blob = new window.Blob([text], { type: "application/json" });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "japanese-mistakes-" + localDateString(new Date()) + ".json";
      document.body.append(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      showToast("已导出 " + mistakes.length + " 道错题。", false);
    } catch (error) {
      showToast("当前浏览器不支持文件下载。", true);
    }
  }

  function mergeMistakes(incoming) {
    let added = 0;
    let merged = 0;
    incoming.forEach(function (item) {
      const existing = mistakes.find(function (mistake) { return mistake.key === item.key; });
      if (!existing) {
        mistakes.push(item);
        added += 1;
        return;
      }
      merged += 1;
      // 错误次数取两边较大值；复习进度整体取更先进的记录（correctStreak 大者），
      // 上次作答与时间取较新的一方。会话内调度字段一律重置。
      existing.count = Math.max(existing.count, item.count);
      if (item.correctStreak > existing.correctStreak) {
        existing.correctStreak = item.correctStreak;
        existing.intervalDays = item.intervalDays;
        existing.dueAt = item.dueAt;
      }
      if (item.updatedAt > existing.updatedAt) {
        existing.wrongAnswer = item.wrongAnswer;
        existing.updatedAt = item.updatedAt;
      }
      existing.sessionDueAtAnswerCount = null;
    });
    return { added: added, merged: merged };
  }

  function importMistakeText(text) {
    let parsed;
    try {
      parsed = JSON.parse(String(text));
    } catch (error) {
      showToast("导入文件无法解析，请确认是本工具导出的 JSON。", true);
      return;
    }
    if (!Array.isArray(parsed)) {
      showToast("导入文件格式无效，应为由错题记录组成的数组。", true);
      return;
    }
    const incoming = sanitizeMistakeList(parsed);
    if (!incoming.length) {
      showToast("文件里没有合法的错题记录。", true);
      return;
    }
    const result = mergeMistakes(incoming);
    saveMistakes();
    updateMistakeCount();
    renderMistakes();
    if (result.added) {
      showToast("导入 " + result.added + " 道新错题"
        + (result.merged ? "，合并 " + result.merged + " 道已有错题。" : "。"), false);
    } else {
      showToast("导入的 " + result.merged + " 道错题均已存在，复习进度已取更先进的记录。", false);
    }
  }

  function handleImportFile(event) {
    const input = event.target;
    const file = input.files && input.files[0];
    if (!file) {
      return;
    }
    try {
      const reader = new window.FileReader();
      reader.onload = function (loadEvent) {
        importMistakeText(loadEvent.target.result);
        input.value = "";
      };
      reader.onerror = function () {
        showToast("读取导入文件失败。", true);
        input.value = "";
      };
      reader.readAsText(file);
    } catch (error) {
      showToast("读取导入文件失败。", true);
      input.value = "";
    }
  }

  function startAdjectiveMistakeReview() {
    const adjectiveMistakes = mistakes.filter(function (mistake) {
      return isAdjectiveMistake(mistake);
    });
    if (!adjectiveMistakes.length) {
      showToast("目前没有形容词错题。", false);
      return;
    }
    reviewedMistakeKeys = new Set();
    setReviewingMistakes(false);
    setReviewingAdjectiveMistakes(true);
    showView("adjective");
    setAdjectiveSubview("practice");
    const mistake = weightedItem(adjectiveMistakes, mistakeWeight);
    startAdjectiveQuestion(questionFromMistake(mistake));
    elements.adjAnswerInput.focus();
  }

  // 从形容词速查表直接开练：不进入重练模式，用该行形式 × 两个主例词随机一题。
  function practiceFromAdjectiveReference(formId) {
    const sampleWords = ["高い", "静か"];
    const sample = sampleWords[Math.random() < 0.5 ? 0 : 1];
    const adjective = ADJECTIVES.filter(function (item) { return item.word === sample; })[0];
    const question = buildAdjectiveQuestionById(adjective.id, formId);
    if (!question) {
      return;
    }
    setReviewingMistakes(false);
    setReviewingAdjectiveMistakes(false);
    setAdjectiveSubview("practice");
    startAdjectiveQuestion(question);
    elements.adjAnswerInput.focus();
  }

  function continueAfterAnswer() {
    if (reviewingMistakes) {
      if (currentQuestion) {
        reviewedMistakeKeys.add(currentQuestion.key);
      }
      const alternatives = mistakes.filter(function (item) {
        return !reviewedMistakeKeys.has(item.key);
      });
      if (alternatives.length) {
        startQuestion(questionFromMistake(randomItem(alternatives)));
        elements.answerInput.focus();
        return;
      }

      setReviewingMistakes(false);
      showView("mistakes");
      showToast(mistakes.length
        ? "本轮错题已复习，剩余题目会按间隔继续复习。"
        : "错题已全部完成间隔复习。", false);
      return;
    }
    newRandomQuestion();
    elements.answerInput.focus();
  }

  // ===================== 形容词模块 =====================
  // 与动词练习同构但相互独立：独立题池、独立统计（adj-stats-v1）。
  // 形容词错题暂不入错题本（recordMistake 与动词题库结构耦合），作为下一批接入。

  function saveAdjectiveStats() {
    return saveAnswerStats(
      adjPracticeStats,
      ADJ_STATS_STORAGE_KEY,
      "浏览器无法保存形容词答题统计；统计仅保留在当前页面。"
    );
  }

  function renderAdjectiveStats() {
    renderAnswerStats(adjPracticeStats, ANSWER_STATS_CONTROLS.adjective);
  }

  function updateAdjectiveStats(correct) {
    updateAnswerStats(
      adjPracticeStats,
      correct,
      ANSWER_STATS_CONTROLS.adjective,
      saveAdjectiveStats,
      elements.adjPracticeCard
    );
  }

  function resetAdjectiveStats() {
    adjPracticeStats = createEmptyAnswerStats();
    renderAdjectiveStats();
    if (saveAdjectiveStats()) {
      showToast("形容词答题统计已清零。", false);
    }
  }

  // ===================== 每日连续打卡 =====================
  // 「练习日」以本地日期为准（跨时区/深夜作答不串日）；任一模块作答都算打卡。

  function localDateString(date) {
    const month = "0" + (date.getMonth() + 1);
    const day = "0" + date.getDate();
    return date.getFullYear() + "-" + month.slice(-2) + "-" + day.slice(-2);
  }

  function previousDateString(date) {
    return localDateString(
      new Date(date.getFullYear(), date.getMonth(), date.getDate() - 1)
    );
  }

  function createEmptyDailyRecord() {
    return {
      lastDate: "",
      streak: 0,
      best: 0,
      verbAnswers: 0,
      adjectiveAnswers: 0,
      particleAnswers: 0
    };
  }

  function loadDailyRecord() {
    if (!storageAvailable) {
      return createEmptyDailyRecord();
    }
    let raw;
    try {
      raw = window.localStorage.getItem(DAILY_STORAGE_KEY);
    } catch (error) {
      return createEmptyDailyRecord();
    }
    if (!raw) {
      return createEmptyDailyRecord();
    }
    let stored;
    try {
      stored = JSON.parse(raw);
    } catch (error) {
      showToast("打卡记录无法解析，本次从零开始。", true);
      return createEmptyDailyRecord();
    }
    if (!isObject(stored)) {
      showToast("打卡记录格式无效，本次从零开始。", true);
      return createEmptyDailyRecord();
    }
    const verbAnswers = stored.verbAnswers === undefined ? 0 : stored.verbAnswers;
    const adjectiveAnswers = stored.adjectiveAnswers === undefined ? 0 : stored.adjectiveAnswers;
    const particleAnswers = stored.particleAnswers === undefined ? 0 : stored.particleAnswers;
    if (typeof stored.lastDate !== "string"
      || !Number.isSafeInteger(stored.streak)
      || stored.streak < 0
      || !Number.isSafeInteger(stored.best)
      || stored.best < 0
      || !Number.isSafeInteger(verbAnswers)
      || verbAnswers < 0
      || !Number.isSafeInteger(adjectiveAnswers)
      || adjectiveAnswers < 0
      || !Number.isSafeInteger(particleAnswers)
      || particleAnswers < 0) {
      showToast("打卡记录格式无效，本次从零开始。", true);
      return createEmptyDailyRecord();
    }
    return {
      lastDate: stored.lastDate,
      streak: stored.streak,
      best: stored.best,
      verbAnswers: verbAnswers,
      adjectiveAnswers: adjectiveAnswers,
      particleAnswers: particleAnswers
    };
  }

  function saveDailyRecord() {
    if (!storageAvailable) {
      return false;
    }
    try {
      window.localStorage.setItem(DAILY_STORAGE_KEY, JSON.stringify(dailyRecord));
      return true;
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器无法保存本地数据；打卡记录仅保留在当前页面。", true);
      return false;
    }
  }

  function getCurrentStreak() {
    const now = new Date();
    const todayKey = localDateString(now);
    return dailyRecord.lastDate === todayKey || dailyRecord.lastDate === previousDateString(now)
      ? dailyRecord.streak
      : 0;
  }

  function updateDailyStreak(moduleId) {
    const countKey = {
      verb: "verbAnswers",
      adjective: "adjectiveAnswers",
      particle: "particleAnswers"
    }[moduleId];
    if (!countKey) {
      throw new RangeError("Unknown daily activity module: " + moduleId);
    }

    const now = new Date();
    const todayKey = localDateString(now);
    if (dailyRecord.lastDate !== todayKey) {
      if (dailyRecord.lastDate === previousDateString(now)) {
        dailyRecord.streak += 1;
      } else {
        dailyRecord.streak = 1;
      }
      dailyRecord.lastDate = todayKey;
      dailyRecord.verbAnswers = 0;
      dailyRecord.adjectiveAnswers = 0;
      dailyRecord.particleAnswers = 0;
      dailyRecord.best = Math.max(dailyRecord.best, dailyRecord.streak);
    }
    dailyRecord[countKey] = Math.min(Number.MAX_SAFE_INTEGER, dailyRecord[countKey] + 1);
    renderStreakNote();
    renderHomeDashboard();
    saveDailyRecord();
  }

  function renderStreakNote() {
    const streak = getCurrentStreak();
    const hasRecord = streak > 0;
    elements.streakNote.hidden = !hasRecord;
    if (hasRecord) {
      elements.streakNoteText.textContent = "连续练习 " + streak
        + " 天 · 最佳 " + dailyRecord.best + " 天";
    }
  }

  function populateAdjectiveFilters() {
    addOption(elements.adjInflectionFilter, "all", "全部活用");
    ADJ_FORMS.filter(function (form) { return !form.morphologyPracticeOnly; })
      .forEach(function (form) {
      addOption(elements.adjInflectionFilter, form.id, form.label);
    });
    ADJECTIVE_MORPHOLOGY_FORMS.forEach(function (form) {
      addOption(
        elements.adjInflectionFilter,
        MORPHOLOGY_FILTER_PREFIX + form.id,
        "六形 · " + form.targetLabel
      );
    });
    addOption(elements.adjLevelFilter, "all", "全部难度");
    ADJ_LEVELS.forEach(function (level) {
      addOption(elements.adjLevelFilter, level.id, level.label);
    });
    syncAdjectiveLevelFilterState();
  }

  function syncAdjectiveLevelFilterState() {
    const overridden = elements.adjInflectionFilter.value !== "all";
    elements.adjLevelFilter.disabled = overridden;
    if (overridden) {
      elements.adjLevelFilter.title = "已指定具体活用形式，难度筛选暂不生效";
      elements.adjLevelFilter.setAttribute("aria-disabled", "true");
    } else {
      elements.adjLevelFilter.title = "按基础／进阶筛选活用形式";
      elements.adjLevelFilter.removeAttribute("aria-disabled");
    }
  }

  function getAdjectiveFormLabel(formId) {
    const form = ADJ_FORMS.find(function (item) { return item.id === formId; });
    return form ? form.label : formId;
  }

  function getSelectedAdjectiveMorphologyForm() {
    const selectedValue = elements.adjInflectionFilter.value;
    if (selectedValue.indexOf(MORPHOLOGY_FILTER_PREFIX) !== 0) {
      return null;
    }
    const morphologyFormId = selectedValue.slice(MORPHOLOGY_FILTER_PREFIX.length);
    const morphologyForm = ADJECTIVE_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === morphologyFormId;
    })[0];
    if (!morphologyForm) {
      throw new RangeError("Unknown adjective morphology filter: " + morphologyFormId);
    }
    return morphologyForm;
  }

  function buildAdjectiveQuestionById(adjectiveId, formId) {
    const adjective = ADJ_BY_ID.get(adjectiveId);
    const form = ADJ_FORMS.filter(function (item) { return item.id === formId; })[0];
    if (!adjective || !form) {
      return null;
    }
    const question = {
      adjective: adjective,
      formId: formId,
      result: adjEngine.conjugate(adjective, formId),
      key: ADJ_MISTAKE_KEY_PREFIX + [adjectiveId, formId].join("::")
    };
    if (form.morphologyPracticeOnly) {
      const morphologyForm = ADJECTIVE_MORPHOLOGY_FORMS.filter(function (item) {
        return item.formId === formId;
      })[0];
      if (!morphologyForm) {
        throw new RangeError("A morphology-only adjective form must have a practice prompt: " + formId);
      }
      question.morphologyFormId = morphologyForm.id;
    }
    return question;
  }

  function buildAdjectiveMorphologyQuestion(adjectiveId, morphologyFormId) {
    const morphologyForm = ADJECTIVE_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === morphologyFormId;
    })[0];
    if (!morphologyForm) {
      throw new RangeError("Unknown adjective morphology form: " + morphologyFormId);
    }
    const question = buildAdjectiveQuestionById(adjectiveId, morphologyForm.formId);
    if (question) {
      question.morphologyFormId = morphologyForm.id;
    }
    return question;
  }

  function chooseAdjectiveMorphologySample() {
    const groupId = elements.adjGroupFilter.value;
    const sampleIds = ["takai", "shizuka"];
    const candidates = ADJECTIVES.filter(function (adjective) {
      return sampleIds.includes(adjective.id)
        && (groupId === "all" || adjective.kind === groupId);
    });
    if (!candidates.length) {
      showToast("当前形容词类别没有六形专项例词。", true);
      return null;
    }
    return randomItem(candidates);
  }

  function practiceFromAdjectiveMorphology(morphologyFormId) {
    const morphologyForm = ADJECTIVE_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === morphologyFormId;
    })[0];
    if (!morphologyForm) {
      throw new RangeError("Unknown adjective morphology form: " + morphologyFormId);
    }
    const adjective = chooseAdjectiveMorphologySample();
    if (!adjective) {
      return;
    }
    elements.adjInflectionFilter.value = MORPHOLOGY_FILTER_PREFIX + morphologyForm.id;
    syncAdjectiveLevelFilterState();
    setReviewingAdjectiveMistakes(false);
    setAdjectiveSubview("practice");
    const question = buildAdjectiveMorphologyQuestion(adjective.id, morphologyForm.id);
    if (!question) {
      return;
    }
    startAdjectiveQuestion(question);
    elements.adjAnswerInput.focus();
  }

  // 形容词到期错题优先插入：与动词 chooseDueReviewQuestion 同构，尊重当前筛选。
  function chooseAdjectiveDueReview() {
    const groupId = elements.adjGroupFilter.value;
    const allowedFormIds = new Set(buildAdjectiveTargets().map(function (target) {
      return target.formId;
    }));
    const now = Date.now();
    const dueMistakes = mistakes.filter(function (mistake) {
      const adjective = isAdjectiveMistake(mistake) && ADJ_BY_ID.get(mistake.adjectiveId);
      return adjective
        && isReviewDue(mistake, now)
        && (!adjCurrentQuestion || adjCurrentQuestion.key !== mistake.key)
        && (groupId === "all" || adjective.kind === groupId)
        && allowedFormIds.has(mistake.formId);
    });
    if (!dueMistakes.length) {
      return null;
    }
    const question = questionFromMistake(weightedItem(dueMistakes, mistakeWeight));
    if (question) {
      question.isScheduledReview = true;
      const morphologyForm = getSelectedAdjectiveMorphologyForm();
      if (morphologyForm) {
        question.morphologyFormId = morphologyForm.id;
      }
    }
    return question;
  }

  function buildAdjectiveTargets() {
    const groupId = elements.adjGroupFilter.value;
    const adjectives = ADJECTIVES.filter(function (adjective) {
      return groupId === "all" || adjective.kind === groupId;
    });
    const morphologyForm = getSelectedAdjectiveMorphologyForm();
    let forms;
    if (morphologyForm) {
      forms = [{
        id: morphologyForm.formId,
        morphologyFormId: morphologyForm.id
      }];
    } else {
      const availableForms = ADJ_FORMS.filter(function (form) {
        return !form.morphologyPracticeOnly;
      });
      // 具体活用形式优先于难度：与动词筛选同一规则，不会筛出空题库。
      forms = elements.adjInflectionFilter.value !== "all"
        ? availableForms.filter(function (form) { return form.id === elements.adjInflectionFilter.value; })
        : elements.adjLevelFilter.value === "all"
          ? availableForms
          : availableForms.filter(function (form) { return form.level === elements.adjLevelFilter.value; });
    }
    const targets = [];
    adjectives.forEach(function (adjective) {
      forms.forEach(function (form) {
        targets.push({
          adjective: adjective,
          formId: form.id,
          morphologyFormId: form.morphologyFormId
        });
      });
    });
    return targets;
  }

  function chooseAdjectiveQuestion() {
    const morphologyForm = getSelectedAdjectiveMorphologyForm();
    const filterKey = JSON.stringify([
      elements.adjGroupFilter.value,
      elements.adjInflectionFilter.value,
      elements.adjLevelFilter.value
    ]);
    if (filterKey !== adjQuestionDeckFilterKey) {
      adjQuestionDeck = [];
      adjQuestionDeckFilterKey = filterKey;
    }

    if (!adjQuestionDeck.length) {
      const activeAdjectiveMistakeKeys = new Set(mistakes.filter(function (mistake) {
        return isAdjectiveMistake(mistake);
      }).map(function (mistake) { return mistake.key; }));
      // 题目 key 统一带 adj:: 前缀：与错题 key 同构，判定入库/复习推进才能对上。
      const pairs = buildAdjectiveTargets()
        .map(function (target) {
          return {
            adjective: target.adjective,
            formId: target.formId,
            morphologyFormId: target.morphologyFormId,
            key: ADJ_MISTAKE_KEY_PREFIX + [target.adjective.id, target.formId].join("::")
          };
        })
        .filter(function (pair) {
          return morphologyForm || !activeAdjectiveMistakeKeys.has(pair.key);
        });
      if (!pairs.length) {
        showToast("当前筛选范围没有可练习的形容词题目。", true);
        return null;
      }
      adjQuestionDeck = shuffleItems(pairs);
    }

    const nextIndex = adjQuestionDeck.length - 1;
    if (nextIndex > 0) {
      const nextPair = adjQuestionDeck[nextIndex];
      const repeatsQuestion = nextPair.key === adjLastPairKey;
      const repeatsRecentWord = adjRecentWordIds.includes(nextPair.adjective.id);
      if (repeatsQuestion || repeatsRecentWord) {
        let alternatives = [];
        for (let index = 0; index < nextIndex; index += 1) {
          const candidate = adjQuestionDeck[index];
          if (candidate.key !== adjLastPairKey && !adjRecentWordIds.includes(candidate.adjective.id)) {
            alternatives.push(index);
          }
        }
        if (!alternatives.length && repeatsQuestion) {
          for (let index = 0; index < nextIndex; index += 1) {
            if (adjQuestionDeck[index].key !== adjLastPairKey) {
              alternatives.push(index);
            }
          }
        }
        if (alternatives.length) {
          const swapIndex = randomItem(alternatives);
          [adjQuestionDeck[nextIndex], adjQuestionDeck[swapIndex]] =
            [adjQuestionDeck[swapIndex], adjQuestionDeck[nextIndex]];
        }
      }
    }

    const pair = adjQuestionDeck.pop();
    adjLastPairKey = pair.key;
    return {
      adjective: pair.adjective,
      formId: pair.formId,
      morphologyFormId: pair.morphologyFormId,
      result: adjEngine.conjugate(pair.adjective, pair.formId),
      key: pair.key
    };
  }

  function startAdjectiveQuestion(question) {
    if (!question) {
      return;
    }
    adjCurrentQuestion = question;
    elements.adjPracticeCard.classList.remove("is-answer-correct");
    elements.adjPracticeCard.classList.remove("is-answer-incorrect");
    adjRecentWordIds = [question.adjective.id]
      .concat(adjRecentWordIds.filter(function (id) { return id !== question.adjective.id; }))
      .slice(0, 3);
    renderAdjectiveReviewBanner();
    const morphologyForm = ADJECTIVE_MORPHOLOGY_FORMS.filter(function (form) {
      return form.id === question.morphologyFormId;
    })[0];
    elements.adjQuestionIndex.textContent = morphologyForm
      ? "六形专项 · " + morphologyForm.label
      : reviewingAdjectiveMistakes
        ? "错题重练"
        : question.isScheduledReview ? "间隔复习" : "随机练习";
    elements.adjTargetPill.textContent = morphologyForm
      ? morphologyForm.targetLabel
      : getAdjectiveFormLabel(question.formId);
    elements.adjQuestionKind.textContent = ADJ_GROUP_LABELS[question.adjective.kind];
    elements.adjQuestionMeaning.textContent = question.adjective.meaning;
    elements.adjQuestionWord.textContent = question.adjective.word;
    elements.adjQuestionReading.textContent = question.adjective.kana;
    if (morphologyForm && morphologyForm.id === "meirei") {
      const requestPattern = question.adjective.kind === "i"
        ? "～くしてください"
        : "～にしてください";
      elements.adjQuestionInstruction.innerHTML = "形容词本身没有独立命令形。请用礼貌请求「<strong class=\"keyword-highlight\">"
        + requestPattern + "</strong>」表达。";
    } else {
      elements.adjQuestionInstruction.innerHTML = morphologyForm
        ? morphologyForm.instruction
        : "请写出 <strong class=\"keyword-highlight\">" + question.adjective.word
          + "</strong> 的<strong class=\"keyword-highlight\">"
          + getAdjectiveFormLabel(question.formId) + "</strong>形式。";
    }

    elements.adjAnswerInput.value = "";
    elements.adjAnswerInput.disabled = false;
    elements.adjSubmitAnswer.disabled = false;
    elements.adjRevealAnswer.disabled = false;
    elements.adjFeedbackPanel.hidden = true;
    elements.adjFeedbackPanel.classList.remove("is-wrong");
    elements.adjNextQuestion.hidden = true;
    elements.adjRuleHint.hidden = true;
    elements.adjHintButton.textContent = "显示规律提示";
    elements.adjHintButton.setAttribute("aria-expanded", "false");
    renderRuleExplanation(
      elements.adjRuleHint,
      adjEngine.explain(question.adjective, question.formId)
    );

    const card = elements.adjPracticeCard;
    card.classList.remove("is-question-entering");
    void card.offsetWidth;
    card.classList.add("is-question-entering");
  }

  function newAdjectiveQuestion() {
    const wasReviewing = reviewingAdjectiveMistakes;
    setReviewingAdjectiveMistakes(false);
    // 到期形容词错题优先插入，与动词练习同一节奏。
    startAdjectiveQuestion(chooseAdjectiveDueReview() || chooseAdjectiveQuestion());
    if (wasReviewing) {
      showToast("已退出错题重练，回到练习。", false);
    }
  }

  // 形容词重练模式的常驻横幅：与动词侧 setReviewingMistakes 同一纪律。
  function renderAdjectiveReviewBanner() {
    elements.adjReviewBanner.hidden = !reviewingAdjectiveMistakes;
    if (reviewingAdjectiveMistakes) {
      const remaining = mistakes.filter(function (mistake) {
        return isAdjectiveMistake(mistake) && !reviewedMistakeKeys.has(mistake.key);
      }).length;
      elements.adjReviewBannerCount.textContent = "本轮待练 " + remaining
        + " 题，完成 " + MASTERY_CORRECT_STREAK + " 次正确复习后移出";
    }
  }

  function setReviewingAdjectiveMistakes(value) {
    reviewingAdjectiveMistakes = Boolean(value);
    renderAdjectiveReviewBanner();
  }

  function submitAdjectiveAnswer(event) {
    event.preventDefault();
    if (composing || (event && event.isComposing)) {
      return;
    }
    if (!adjCurrentQuestion) {
      return;
    }

    const rawAnswer = elements.adjAnswerInput.value.trim();
    if (!rawAnswer) {
      elements.adjAnswerInput.reportValidity();
      return;
    }

    const normalized = normalizeAnswerDetailed(rawAnswer);
    const normalizedAnswer = normalized.answer;
    const expected = new Set(adjEngine.answerVariants(adjCurrentQuestion.result).map(function (text) {
      return normalizeAnswer(text);
    }));
    const correct = expected.has(normalizedAnswer);
    sessionAnswerCount += 1;

    elements.adjAnswerInput.disabled = true;
    elements.adjSubmitAnswer.disabled = true;
    elements.adjFeedbackPanel.hidden = false;
    elements.adjFeedbackPanel.classList.toggle("is-wrong", !correct);
    elements.adjFeedbackMark.textContent = correct ? "✓" : "!";
    elements.adjFeedbackTitle.textContent = correct ? "答对了" : "再记一下这个接续";
    const romajiInput = /^[a-z']+$/i.test(rawAnswer);
    elements.adjFeedbackUserAnswer.textContent = correct && romajiInput
      ? "你的答案：" + rawAnswer + "（" + normalizedAnswer + "）"
      : "你的答案：" + rawAnswer;
    elements.adjFeedbackCorrectAnswer.textContent = adjCurrentQuestion.result.written;
    elements.adjFeedbackReading.textContent = adjCurrentQuestion.result.kana === adjCurrentQuestion.result.written
      ? ""
      : "读音：" + adjCurrentQuestion.result.kana;
    const form = ADJ_FORMS.find(function (item) { return item.id === adjCurrentQuestion.formId; });
    elements.adjFeedbackFormMeaning.textContent = form.meaning;
    renderRuleExplanation(
      elements.adjFeedbackRule,
      adjEngine.explain(adjCurrentQuestion.adjective, adjCurrentQuestion.formId)
    );
    elements.adjNextQuestion.hidden = false;

    if (correct) {
      recordSuccessfulReview(adjCurrentQuestion);
      showToast("正确。留意上面的接续规则。", false);
    } else {
      recordMistake(adjCurrentQuestion, rawAnswer);
      if (normalized.romajiFailed) {
        // 罗马字拼写无法识别（IK-10）：明示按原样比对，避免用户误以为是接续错误
        elements.adjFeedbackUserAnswer.textContent =
          "你的答案：" + rawAnswer + "（罗马字未能识别，按原样比对）";
        showToast("无法识别该罗马字拼写，已按原样比对；可改用假名输入。", true);
      } else {
        showToast("已加入错题，稍后可以重练。", false);
      }
    }

    updateMistakeCount();
    updateAdjectiveStats(correct);
    updateDailyStreak("adjective");
    elements.adjNextQuestion.focus();
  }

  // IK-16 查看答案通道（形容词）：记账口径同动词侧 revealVerbAnswer。
  function revealAdjectiveAnswer() {
    if (!adjCurrentQuestion || elements.adjSubmitAnswer.disabled) {
      return; // 已判定或已查看答案，不可重复触发
    }
    sessionAnswerCount += 1;

    elements.adjAnswerInput.disabled = true;
    elements.adjSubmitAnswer.disabled = true;
    elements.adjRevealAnswer.disabled = true;
    elements.adjFeedbackPanel.hidden = false;
    elements.adjFeedbackPanel.classList.add("is-wrong");
    elements.adjFeedbackMark.textContent = "!";
    elements.adjFeedbackTitle.textContent = "已查看答案";
    elements.adjFeedbackUserAnswer.textContent = "你的答案：（查看了答案，未作答）";
    elements.adjFeedbackCorrectAnswer.textContent = adjCurrentQuestion.result.written;
    elements.adjFeedbackReading.textContent = adjCurrentQuestion.result.kana === adjCurrentQuestion.result.written
      ? ""
      : "读音：" + adjCurrentQuestion.result.kana;
    const form = ADJ_FORMS.find(function (item) { return item.id === adjCurrentQuestion.formId; });
    elements.adjFeedbackFormMeaning.textContent = form.meaning;
    renderRuleExplanation(
      elements.adjFeedbackRule,
      adjEngine.explain(adjCurrentQuestion.adjective, adjCurrentQuestion.formId)
    );
    elements.adjNextQuestion.hidden = false;

    recordMistake(adjCurrentQuestion, "（查看答案）");
    updateMistakeCount();
    updateAdjectiveStats(false);
    updateDailyStreak("adjective");
    showToast("已查看答案，本题记入错题，稍后重练。", false);
    elements.adjNextQuestion.focus();
  }

  function continueAdjectiveAfterAnswer() {
    if (reviewingAdjectiveMistakes) {
      if (adjCurrentQuestion) {
        reviewedMistakeKeys.add(adjCurrentQuestion.key);
      }
      const alternatives = mistakes.filter(function (mistake) {
        return isAdjectiveMistake(mistake) && !reviewedMistakeKeys.has(mistake.key);
      });
      if (alternatives.length) {
        startAdjectiveQuestion(questionFromMistake(randomItem(alternatives)));
        elements.adjAnswerInput.focus();
        return;
      }

      setReviewingAdjectiveMistakes(false);
      showView("adjective");
      const remainingAdjectiveMistakes = mistakes.filter(function (mistake) {
        return isAdjectiveMistake(mistake);
      }).length;
      showToast(remainingAdjectiveMistakes
        ? "本轮错题已复习，剩余题目会按间隔继续复习。"
        : "错题已全部完成间隔复习。", false);
      return;
    }
    newAdjectiveQuestion();
    elements.adjAnswerInput.focus();
  }

  function buildReferenceRow(verb, voiceId, form) {
    const result = engine.conjugate(verb, voiceId, form.id);
    const row = document.createElement("tr");
    const formCell = document.createElement("td");
    formCell.textContent = form.label;

    const answerCell = document.createElement("td");
    const answer = document.createElement("div");
    answer.className = "reference-result";
    const written = document.createElement("strong");
    written.textContent = result.written;
    answer.append(written);
    if (result.kana !== result.written) {
      const reading = document.createElement("small");
      reading.textContent = result.kana;
      answer.append(reading);
    }
    answerCell.append(answer);

    const meaningCell = document.createElement("td");
    meaningCell.className = "reference-form-meaning";
    meaningCell.textContent = form.meaning;

    const ruleCell = document.createElement("td");
    renderRuleExplanation(ruleCell, engine.explain(verb, voiceId, form.id));

    const actionCell = document.createElement("td");
    actionCell.className = "reference-practice";
    const practiceButton = document.createElement("button");
    practiceButton.type = "button";
    practiceButton.className = "inline-button practice-link";
    practiceButton.textContent = "练这题";
    practiceButton.setAttribute(
      "aria-label",
      "练习 " + verb.dictionary + " 的" + getTargetLabel(voiceId, form.id)
    );
    practiceButton.addEventListener("click", function () {
      practiceFromReference(verb, voiceId, form.id);
    });
    actionCell.append(practiceButton);

    row.append(formCell, answerCell, meaningCell, ruleCell, actionCell);
    return row;
  }

  function buildReferenceVoiceRow(verb, voice, selectedVoiceId) {
    const result = engine.conjugate(verb, voice.id, "dictionary");
    const row = document.createElement("tr");
    row.setAttribute("data-voice-id", voice.id);
    if (voice.id === selectedVoiceId) {
      row.classList.add("is-selected");
      row.setAttribute("aria-current", "true");
    }

    const formCell = document.createElement("th");
    formCell.scope = "row";
    formCell.textContent = voice.label;

    const answerCell = document.createElement("td");
    const answer = document.createElement("div");
    answer.className = "reference-result";
    const written = document.createElement("strong");
    written.textContent = voice.id === "base"
      ? verb.dictionary + "（不变）"
      : verb.dictionary + " → " + result.written;
    answer.append(written);
    if (result.kana !== result.written) {
      const reading = document.createElement("small");
      reading.textContent = result.kana;
      answer.append(reading);
    }
    answerCell.append(answer);

    const meaningCell = document.createElement("td");
    meaningCell.className = "reference-form-meaning";
    meaningCell.textContent = voice.meaning;

    const ruleCell = document.createElement("td");
    renderRuleExplanation(ruleCell, engine.explainVoice(verb, voice.id));

    const actionCell = document.createElement("td");
    actionCell.className = "reference-practice";
    const practiceButton = document.createElement("button");
    practiceButton.type = "button";
    practiceButton.className = "inline-button practice-link";
    practiceButton.textContent = "练这题";
    practiceButton.setAttribute(
      "aria-label",
      "练习 " + verb.dictionary + " 的" + voice.label + "辞书形"
    );
    practiceButton.addEventListener("click", function () {
      practiceFromReference(verb, voice.id, "dictionary");
    });
    actionCell.append(practiceButton);

    row.append(formCell, answerCell, meaningCell, ruleCell, actionCell);
    return row;
  }

  function renderReferenceVoiceGuide(verb, selectedVoiceId) {
    elements.referenceVoiceGuideCaption.textContent = "以「" + verb.dictionary + "」（"
      + GROUP_LABELS[verb.group] + "）为例。表中对照各类变形后的辞书形；下方活用表再展示"
      + "当前所选变形的各种活用形式。";
    elements.referenceVoiceGuideTableBody.replaceChildren();

    VOICES.forEach(function (voice) {
      elements.referenceVoiceGuideTableBody.append(
        buildReferenceVoiceRow(verb, voice, selectedVoiceId)
      );
    });
  }

  // 与练习页共用同一套 LEVELS 分类：速查表按 基础活用/进阶活用 分组展示，
  // 组头为跨列表头，随后紧跟该组的活用形式行。
  function appendReferenceSection(levelId, forms) {
    const groupRow = document.createElement("tr");
    groupRow.className = "reference-section-row";
    const groupCell = document.createElement("th");
    groupCell.colSpan = REFERENCE_COLUMN_COUNT;
    groupCell.scope = "colgroup";
    const groupLabel = document.createElement("span");
    groupLabel.className = "reference-section-label";
    groupLabel.textContent = LEVEL_LABELS[levelId];
    const groupCount = document.createElement("span");
    groupCount.className = "reference-section-count";
    groupCount.textContent = forms.length + " 项";
    groupCell.append(groupLabel, groupCount);
    groupRow.append(groupCell);
    elements.referenceTableBody.append(groupRow);
  }

  function renderReferenceTable() {
    const verb = VERB_BY_ID.get(elements.referenceVerb.value);
    const voiceId = elements.referenceVoice.value || "base";
    elements.referenceTableBody.replaceChildren();

    if (!verb) {
      elements.referenceWord.textContent = "没有找到动词";
      elements.referenceMeaning.textContent = "请换一个搜索词。";
      elements.referenceVoiceMeaning.textContent = "";
      elements.referenceGroup.textContent = "";
      elements.verbMorphologyGuide.hidden = true;
      elements.verbMorphologyGuideTableBody.replaceChildren();
      elements.referenceVoiceGuide.hidden = true;
      elements.referenceVoiceGuideTableBody.replaceChildren();
      return;
    }

    elements.verbMorphologyGuide.hidden = false;
    elements.referenceVoiceGuide.hidden = false;
    elements.referenceWord.textContent = verb.dictionary + "（" + verb.reading + "）";
    elements.referenceMeaning.textContent = verb.meaning + " · " + getVoiceLabel(voiceId);
    elements.referenceVoiceMeaning.textContent = "语态大意：" + VOICE_BY_ID.get(voiceId).meaning;
    elements.referenceGroup.textContent = GROUP_LABELS[verb.group];
    renderVerbMorphologyGuide(verb);
    renderReferenceVoiceGuide(verb, voiceId);

    LEVELS.forEach(function (level) {
      const forms = INFLECTIONS.filter(function (form) {
        return form.level === level.id;
      });
      appendReferenceSection(level.id, forms);
      forms.forEach(function (form) {
        elements.referenceTableBody.append(buildReferenceRow(verb, voiceId, form));
      });
    });
  }

  elements.homeLinks.forEach(function (link) {
    link.addEventListener("click", function (event) {
      if ((event.button !== undefined && event.button !== 0)
        || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      event.preventDefault();
      showView("home");
    });
  });
  elements.tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      showView(tab.dataset.view);
    });
  });
  document.querySelectorAll("[data-open-view]").forEach(function (button) {
    button.addEventListener("click", function () {
      if (button.dataset.verbSubview) {
        setVerbSubview(button.dataset.verbSubview);
      }
      if (button.dataset.adjSubview) {
        setAdjectiveSubview(button.dataset.adjSubview);
      }
      if (button.dataset.particleSubview) {
        if (button.dataset.particleSubview === "practice") {
          startParticlePractice();
        } else {
          setParticleSubview(button.dataset.particleSubview);
        }
      }
      showView(button.dataset.openView);
    });
  });
  elements.verbSubtabs.forEach(function (subtab) {
    subtab.addEventListener("click", function () {
      setVerbSubview(subtab.dataset.verbSubview);
    });
  });
  elements.sidebarCollapse.addEventListener("click", function () {
    const collapsed = elements.appShell.classList.toggle("is-sidebar-collapsed");
    elements.sidebarCollapse.setAttribute("aria-pressed", String(collapsed));
    elements.sidebarCollapse.setAttribute("aria-label", collapsed ? "展开侧栏" : "折叠侧栏");
    elements.sidebarCollapse.title = collapsed ? "展开侧栏" : "折叠侧栏";
  });
  elements.mobileNavToggle.addEventListener("click", function () {
    const open = elements.mobileNavToggle.getAttribute("aria-expanded") !== "true";
    setMobileSidebarOpen(open, false);
  });
  elements.sidebarBackdrop.addEventListener("click", function () {
    setMobileSidebarOpen(false, true);
  });
  elements.appShell.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && elements.appShell.classList.contains("is-sidebar-open")) {
      setMobileSidebarOpen(false, true);
    }
  });

  elements.answerForm.addEventListener("submit", submitAnswer);
  elements.verbPrompt.addEventListener("click", toggleVerbPromptDetails);
  elements.verbPrompt.addEventListener("keydown", function (event) {
    if (event.key !== "Enter" && event.key !== " ") {
      return;
    }
    event.preventDefault();
    if (!event.repeat) {
      toggleVerbPromptDetails();
    }
  });
  elements.answerInput.addEventListener("compositionstart", function () {
    composing = true;
  });
  elements.answerInput.addEventListener("compositionend", function () {
    composing = false;
  });
  elements.newQuestion.addEventListener("click", newRandomQuestion);
  elements.nextQuestion.addEventListener("click", continueAfterAnswer);
  elements.resetStats.addEventListener("click", resetPracticeStats);
  elements.exitReview.addEventListener("click", exitMistakeReview);
  [elements.groupFilter, elements.voiceFilter, elements.inflectionFilter, elements.levelFilter]
    .forEach(function (select) {
      select.addEventListener("change", function () {
        syncLevelFilterState();
        newRandomQuestion();
      });
    });
  elements.hintButton.addEventListener("click", function () {
    const expanded = elements.hintButton.getAttribute("aria-expanded") === "true";
    elements.hintButton.setAttribute("aria-expanded", String(!expanded));
    elements.ruleHint.hidden = expanded;
    elements.hintButton.textContent = expanded ? "显示规律提示" : "收起规律提示";
  });
  elements.revealAnswer.addEventListener("click", revealVerbAnswer);

  elements.referenceSearch.addEventListener("input", function () {
    populateReferenceControls(elements.referenceSearch.value);
  });
  elements.referenceVerb.addEventListener("change", renderReferenceTable);
  elements.referenceVoice.addEventListener("change", renderReferenceTable);
  elements.reviewMistakes.addEventListener("click", startMistakeReview);
  elements.clearMistakes.addEventListener("click", clearMistakes);
  elements.exportMistakes.addEventListener("click", exportMistakes);
  elements.importMistakes.addEventListener("click", function () {
    elements.importMistakesFile.click();
  });
  elements.importMistakesFile.addEventListener("change", handleImportFile);

  // —— 形容词模块事件 ——
  elements.adjAnswerForm.addEventListener("submit", submitAdjectiveAnswer);
  elements.adjAnswerInput.addEventListener("compositionstart", function () {
    composing = true;
  });
  elements.adjAnswerInput.addEventListener("compositionend", function () {
    composing = false;
  });
  elements.adjNewQuestion.addEventListener("click", newAdjectiveQuestion);
  elements.adjNextQuestion.addEventListener("click", continueAdjectiveAfterAnswer);
  elements.adjResetStats.addEventListener("click", resetAdjectiveStats);
  elements.adjReviewMistakes.addEventListener("click", startAdjectiveMistakeReview);
  elements.adjExitReview.addEventListener("click", function () {
    if (!reviewingAdjectiveMistakes) {
      return;
    }
    newAdjectiveQuestion();
  });
  document.querySelectorAll("[data-adj-practice]").forEach(function (button) {
    button.addEventListener("click", function () {
      practiceFromAdjectiveReference(button.dataset.adjPractice);
    });
  });
  document.querySelectorAll("[data-adj-morphology-practice]").forEach(function (button) {
    button.addEventListener("click", function () {
      practiceFromAdjectiveMorphology(button.dataset.adjMorphologyPractice);
    });
  });
  [elements.adjGroupFilter, elements.adjInflectionFilter, elements.adjLevelFilter]
    .forEach(function (select) {
      select.addEventListener("change", function () {
        syncAdjectiveLevelFilterState();
        newAdjectiveQuestion();
      });
    });
  elements.adjHintButton.addEventListener("click", function () {
    const expanded = elements.adjHintButton.getAttribute("aria-expanded") === "true";
    elements.adjHintButton.setAttribute("aria-expanded", String(!expanded));
    elements.adjRuleHint.hidden = expanded;
    elements.adjHintButton.textContent = expanded ? "显示规律提示" : "收起规律提示";
  });
  elements.adjRevealAnswer.addEventListener("click", revealAdjectiveAnswer);
  elements.adjGuideLink.addEventListener("click", function () {
    setAdjectiveSubview("reference");
  });
  elements.adjSubtabs.forEach(function (subtab) {
    subtab.addEventListener("click", function () {
      setAdjectiveSubview(subtab.dataset.adjSubview);
    });
  });
  elements.particleGuideLink.addEventListener("click", function () {
    setParticleSubview("learn");
  });
  elements.particleSubtabs.forEach(function (subtab) {
    subtab.addEventListener("click", function () {
      if (subtab.dataset.particleSubview === "practice") {
        startParticlePractice();
      } else {
        setParticleSubview(subtab.dataset.particleSubview);
      }
    });
  });
  elements.particlePracticeModeSelect.addEventListener("change", function () {
    setParticlePracticeMode(elements.particlePracticeModeSelect.value);
  });
  elements.particleLevelSelect.addEventListener("change", function () {
    setParticleLevel(elements.particleLevelSelect.value);
  });
  elements.particleCheckAnswer.addEventListener("click", checkParticleAnswer);
  elements.particleNextQuestion.addEventListener("click", nextParticleQuestion);
  elements.particleStartReview.addEventListener("click", startParticleReview);
  elements.particleResetStats.addEventListener("click", resetParticleStats);

  populatePracticeFilters();
  populateReferenceVoices();
  populateReferenceControls("");
  populateAdjectiveFilters();
  updateMistakeCount();
  renderPracticeStats();
  renderAdjectiveStats();
  renderParticleStats();
  renderStreakNote();
  renderMistakes();
  setVerbSubview("practice");
  setAdjectiveSubview("practice");
  setParticleLevel("beginner");
  renderParticleMistakes();
  newRandomQuestion();
  newAdjectiveQuestion();
  showView("home", { focus: false }); // 初始装载不抢焦点
})();
