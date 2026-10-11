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

  const VOICES = engine.VOICES;
  const INFLECTIONS = engine.INFLECTIONS;
  const VERBS = engine.VERBS;
  const VERB_BY_ID = new Map(VERBS.map(function (verb) { return [verb.id, verb]; }));
  const VOICE_BY_ID = new Map(VOICES.map(function (voice) { return [voice.id, voice]; }));
  const INFLECTION_BY_ID = new Map(INFLECTIONS.map(function (form) { return [form.id, form]; }));
  const STORAGE_KEY = "japanese-verb-practice-mistakes-v1";
  const GROUP_LABELS = {
    godan: "五段动词",
    ichidan: "一段动词",
    irregular: "不规则动词"
  };

  const elements = {
    tabs: Array.from(document.querySelectorAll("[data-view]")),
    views: {
      practice: document.getElementById("view-practice"),
      reference: document.getElementById("view-reference"),
      mistakes: document.getElementById("view-mistakes")
    },
    mistakeCount: document.getElementById("mistake-count"),
    groupFilter: document.getElementById("group-filter"),
    voiceFilter: document.getElementById("voice-filter"),
    inflectionFilter: document.getElementById("inflection-filter"),
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
    referenceTableBody: document.getElementById("reference-table-body"),
    reviewMistakes: document.getElementById("review-mistakes"),
    clearMistakes: document.getElementById("clear-mistakes"),
    mistakesList: document.getElementById("mistakes-list"),
    mistakesEmpty: document.getElementById("mistakes-empty"),
    toast: document.getElementById("toast")
  };

  let storageAvailable = true;
  let toastTimeout = null;
  let currentQuestion = null;
  let lastPairKey = "";
  let reviewingMistakes = false;
  let mistakes = loadMistakes();

  function showToast(message, isError) {
    elements.toast.textContent = message;
    elements.toast.classList.toggle("is-error", Boolean(isError));
    elements.toast.classList.add("is-visible");
    window.clearTimeout(toastTimeout);
    toastTimeout = window.setTimeout(function () {
      elements.toast.classList.remove("is-visible");
    }, 3000);
  }

  function isObject(value) {
    return value !== null && typeof value === "object" && !Array.isArray(value);
  }

  function mistakeKey(verbId, voiceId, inflectionId) {
    return [verbId, voiceId, inflectionId].join("::");
  }

  function loadMistakes() {
    let raw;
    try {
      raw = window.localStorage.getItem(STORAGE_KEY);
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器不允许读取本地错题；本次练习仍可继续。", true);
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

    const voiceIds = new Set(VOICES.map(function (voice) { return voice.id; }));
    const inflectionIds = new Set(INFLECTIONS.map(function (form) { return form.id; }));
    if (!Array.isArray(stored)) {
      showToast("本地错题数据格式无效，本次先从空列表开始。", true);
      return [];
    }

    return stored.filter(function (item) {
      return isObject(item)
        && VERB_BY_ID.has(item.verbId)
        && voiceIds.has(item.voiceId)
        && inflectionIds.has(item.inflectionId);
    }).map(function (item) {
      return {
        key: mistakeKey(item.verbId, item.voiceId, item.inflectionId),
        verbId: item.verbId,
        voiceId: item.voiceId,
        inflectionId: item.inflectionId,
        count: Math.max(1, Number.isFinite(Number(item.count)) ? Math.floor(Number(item.count)) : 1),
        wrongAnswer: typeof item.wrongAnswer === "string" ? item.wrongAnswer.slice(0, 120) : "",
        updatedAt: typeof item.updatedAt === "string" ? item.updatedAt : ""
      };
    });
  }

  function saveMistakes() {
    if (!storageAvailable) {
      return;
    }
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(mistakes));
    } catch (error) {
      storageAvailable = false;
      showToast("浏览器无法保存错题；关闭页面后，这次记录可能不会保留。", true);
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

  function buildTargets() {
    const voices = elements.voiceFilter.value === "all"
      ? VOICES
      : VOICES.filter(function (voice) { return voice.id === elements.voiceFilter.value; });
    const inflections = elements.inflectionFilter.value === "all"
      ? INFLECTIONS
      : INFLECTIONS.filter(function (form) { return form.id === elements.inflectionFilter.value; });
    const targets = [];

    voices.forEach(function (voice) {
      inflections.forEach(function (form) {
        targets.push({ voiceId: voice.id, inflectionId: form.id });
      });
    });
    return targets;
  }

  function questionFromMistake(mistake) {
    const verb = VERB_BY_ID.get(mistake.verbId);
    if (!verb) {
      return null;
    }
    return {
      verb: verb,
      voiceId: mistake.voiceId,
      inflectionId: mistake.inflectionId,
      result: engine.conjugate(verb, mistake.voiceId, mistake.inflectionId),
      key: mistake.key
    };
  }

  function chooseRandomQuestion() {
    const groupId = elements.groupFilter.value;
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
          key: getPairKey(verb, target.voiceId, target.inflectionId)
        });
      });
    });

    if (!pairs.length) {
      showToast("当前筛选范围没有可练习的题目。", true);
      return null;
    }

    const alternatives = pairs.filter(function (pair) { return pair.key !== lastPairKey; });
    const pair = randomItem(alternatives.length ? alternatives : pairs);
    lastPairKey = pair.key;
    return {
      verb: pair.verb,
      voiceId: pair.voiceId,
      inflectionId: pair.inflectionId,
      result: engine.conjugate(pair.verb, pair.voiceId, pair.inflectionId),
      key: pair.key
    };
  }

  function startQuestion(question) {
    if (!question) {
      return;
    }
    currentQuestion = question;
    elements.questionIndex.textContent = reviewingMistakes ? "错题重练" : "随机练习";
    elements.targetPill.textContent = getTargetLabel(question.voiceId, question.inflectionId);
    elements.questionGroup.textContent = GROUP_LABELS[question.verb.group];
    elements.questionMeaning.textContent = question.verb.meaning;
    elements.questionWord.textContent = question.verb.dictionary;
    elements.questionReading.textContent = question.verb.reading;

    if (question.voiceId === "base") {
      elements.questionInstruction.innerHTML = "请把 <strong>" + getInflectionLabel(question.inflectionId) + "</strong> 写出来。";
    } else {
      elements.questionInstruction.innerHTML = "请将动词变为 <strong>" + getVoiceLabel(question.voiceId)
        + "</strong>，再活用成 <strong>" + getInflectionLabel(question.inflectionId) + "</strong>。";
    }

    elements.answerInput.value = "";
    elements.answerInput.disabled = false;
    elements.submitAnswer.disabled = false;
    elements.feedbackPanel.hidden = true;
    elements.feedbackPanel.classList.remove("is-wrong");
    elements.nextQuestion.hidden = true;
    elements.ruleHint.hidden = true;
    elements.hintButton.textContent = "显示规律提示";
    elements.hintButton.setAttribute("aria-expanded", "false");
    appendRuleText(
      elements.ruleHint,
      engine.explain(question.verb, question.voiceId, question.inflectionId)
    );
  }

  function newRandomQuestion() {
    reviewingMistakes = false;
    startQuestion(chooseRandomQuestion());
  }

  function normalizeText(value) {
    return String(value).normalize("NFKC").replace(/[\s\u3000]/g, "");
  }

  function normalizeAnswer(value) {
    const normalized = normalizeText(value);
    return /^[a-z']+$/i.test(normalized)
      ? engine.toHiraganaFromRomaji(normalized)
      : normalized;
  }

  function appendRuleText(target, text) {
    target.replaceChildren();
    const parts = String(text).split(/(「[^」]*」|[あいうえお]段)/);
    parts.forEach(function (part, index) {
      if (!part) {
        return;
      }
      if (index % 2 === 1) {
        const mark = document.createElement("mark");
        mark.className = "rule-highlight";
        mark.textContent = part;
        target.append(mark);
      } else {
        target.append(document.createTextNode(part));
      }
    });
  }

  function recordMistake(question, answer) {
    const existing = mistakes.find(function (item) { return item.key === question.key; });
    if (existing) {
      existing.count += 1;
      existing.wrongAnswer = answer || "（未作答）";
      existing.updatedAt = new Date().toISOString();
    } else {
      mistakes.push({
        key: question.key,
        verbId: question.verb.id,
        voiceId: question.voiceId,
        inflectionId: question.inflectionId,
        count: 1,
        wrongAnswer: answer || "（未作答）",
        updatedAt: new Date().toISOString()
      });
    }
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
    if (!currentQuestion) {
      return;
    }

    const rawAnswer = elements.answerInput.value.trim();
    if (!rawAnswer) {
      elements.answerInput.reportValidity();
      return;
    }

    const normalizedAnswer = normalizeAnswer(rawAnswer);
    const expected = new Set([
      normalizeAnswer(currentQuestion.result.written),
      normalizeAnswer(currentQuestion.result.kana)
    ]);
    const correct = expected.has(normalizedAnswer);

    elements.answerInput.disabled = true;
    elements.submitAnswer.disabled = true;
    elements.feedbackPanel.hidden = false;
    elements.feedbackPanel.classList.toggle("is-wrong", !correct);
    elements.feedbackMark.textContent = correct ? "✓" : "!";
    elements.feedbackTitle.textContent = correct ? "答对了" : "再记一下这个变化";
    elements.feedbackUserAnswer.textContent = correct
      ? "你的答案：" + (normalizedAnswer === engine.toHiraganaFromRomaji(normalizedAnswer) ? normalizedAnswer : rawAnswer)
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
    appendRuleText(
      elements.feedbackRule,
      engine.explain(
        currentQuestion.verb,
        currentQuestion.voiceId,
        currentQuestion.inflectionId
      )
    );
    elements.nextQuestion.hidden = false;

    if (correct) {
      if (reviewingMistakes) {
        removeMistake(currentQuestion.key);
      }
      showToast("正确。留意上面的变化规则。", false);
    } else {
      recordMistake(currentQuestion, rawAnswer);
      showToast("已加入错题，稍后可以重练。", false);
    }

    updateMistakeCount();
    elements.nextQuestion.focus();
  }

  function updateMistakeCount() {
    elements.mistakeCount.textContent = String(mistakes.length);
  }

  function showView(viewId) {
    Object.keys(elements.views).forEach(function (id) {
      elements.views[id].hidden = id !== viewId;
    });
    elements.tabs.forEach(function (tab) {
      const active = tab.dataset.view === viewId;
      tab.classList.toggle("is-active", active);
      if (active) {
        tab.setAttribute("aria-current", "page");
      } else {
        tab.removeAttribute("aria-current");
      }
    });

    if (viewId === "mistakes") {
      renderMistakes();
    }
    if (viewId === "reference") {
      renderReferenceTable();
    }
  }

  function renderMistakes() {
    elements.mistakesList.replaceChildren();
    elements.mistakesEmpty.hidden = mistakes.length > 0;
    elements.reviewMistakes.disabled = mistakes.length === 0;
    elements.clearMistakes.disabled = mistakes.length === 0;
    updateMistakeCount();

    mistakes.slice().sort(function (a, b) {
      return b.updatedAt.localeCompare(a.updatedAt);
    }).forEach(function (mistake) {
      const question = questionFromMistake(mistake);
      if (!question) {
        return;
      }
      const card = document.createElement("article");
      card.className = "mistake-card";
      const description = document.createElement("div");
      const heading = document.createElement("h3");
      heading.textContent = question.verb.dictionary;
      const reading = document.createElement("span");
      reading.textContent = question.verb.reading + " · " + getTargetLabel(question.voiceId, question.inflectionId);
      heading.append(reading);
      const meaning = document.createElement("p");
      meaning.textContent = question.verb.meaning + " · " + GROUP_LABELS[question.verb.group]
        + " · 错 " + mistake.count + " 次";
      const correctAnswer = document.createElement("p");
      correctAnswer.className = "mistake-answer";
      correctAnswer.textContent = "正确答案：" + question.result.written
        + (mistake.wrongAnswer ? " · 上次作答：" + mistake.wrongAnswer : "");
      description.append(heading, meaning, correctAnswer);

      const actions = document.createElement("div");
      actions.className = "mistake-card-actions";
      const practiceButton = document.createElement("button");
      practiceButton.type = "button";
      practiceButton.textContent = "练这题";
      practiceButton.addEventListener("click", function () {
        reviewingMistakes = true;
        showView("practice");
        startQuestion(questionFromMistake(mistake));
      });
      const removeButton = document.createElement("button");
      removeButton.type = "button";
      removeButton.className = "remove-mistake";
      removeButton.textContent = "移除";
      removeButton.addEventListener("click", function () {
        removeMistake(mistake.key);
        renderMistakes();
      });
      actions.append(practiceButton, removeButton);
      card.append(description, actions);
      elements.mistakesList.append(card);
    });
  }

  function clearMistakes() {
    if (!mistakes.length) {
      return;
    }
    if (!window.confirm("确定清空所有错题吗？")) {
      return;
    }
    mistakes = [];
    saveMistakes();
    reviewingMistakes = false;
    renderMistakes();
    showToast("错题已清空。", false);
  }

  function startMistakeReview() {
    if (!mistakes.length) {
      showToast("目前没有错题可以重练。", false);
      return;
    }
    reviewingMistakes = true;
    showView("practice");
    const mistake = randomItem(mistakes);
    startQuestion(questionFromMistake(mistake));
  }

  function continueAfterAnswer() {
    if (reviewingMistakes) {
      if (!mistakes.length) {
        reviewingMistakes = false;
        showView("mistakes");
        showToast("错题已全部答对。", false);
        return;
      }
      const alternatives = mistakes.filter(function (item) {
        return !currentQuestion || item.key !== currentQuestion.key;
      });
      const nextMistake = randomItem(alternatives.length ? alternatives : mistakes);
      startQuestion(questionFromMistake(nextMistake));
      elements.answerInput.focus();
      return;
    }
    newRandomQuestion();
    elements.answerInput.focus();
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
      return;
    }

    elements.referenceWord.textContent = verb.dictionary + "（" + verb.reading + "）";
    elements.referenceMeaning.textContent = verb.meaning + " · " + getVoiceLabel(voiceId);
    elements.referenceVoiceMeaning.textContent = "语态大意：" + VOICE_BY_ID.get(voiceId).meaning;
    elements.referenceGroup.textContent = GROUP_LABELS[verb.group];

    INFLECTIONS.forEach(function (form) {
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
      appendRuleText(ruleCell, engine.explain(verb, voiceId, form.id));
      row.append(formCell, answerCell, meaningCell, ruleCell);
      elements.referenceTableBody.append(row);
    });
  }

  elements.tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      showView(tab.dataset.view);
    });
  });
  document.querySelectorAll("[data-open-view]").forEach(function (button) {
    button.addEventListener("click", function () {
      showView(button.dataset.openView);
    });
  });

  elements.answerForm.addEventListener("submit", submitAnswer);
  elements.newQuestion.addEventListener("click", newRandomQuestion);
  elements.nextQuestion.addEventListener("click", continueAfterAnswer);
  [elements.groupFilter, elements.voiceFilter, elements.inflectionFilter].forEach(function (select) {
    select.addEventListener("change", newRandomQuestion);
  });
  elements.hintButton.addEventListener("click", function () {
    const expanded = elements.hintButton.getAttribute("aria-expanded") === "true";
    elements.hintButton.setAttribute("aria-expanded", String(!expanded));
    elements.ruleHint.hidden = expanded;
    elements.hintButton.textContent = expanded ? "显示规律提示" : "收起规律提示";
  });

  elements.referenceSearch.addEventListener("input", function () {
    populateReferenceControls(elements.referenceSearch.value);
  });
  elements.referenceVerb.addEventListener("change", renderReferenceTable);
  elements.referenceVoice.addEventListener("change", renderReferenceTable);
  elements.reviewMistakes.addEventListener("click", startMistakeReview);
  elements.clearMistakes.addEventListener("click", clearMistakes);

  populatePracticeFilters();
  populateReferenceVoices();
  populateReferenceControls("");
  updateMistakeCount();
  renderMistakes();
  newRandomQuestion();
})();
