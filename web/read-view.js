// 文章阅读模块的视图渲染（B2 静态外壳 + B4 逐词/译文/状态渲染）。
// 只做 DOM 渲染与本地切段，不碰网络、不碰存储；所有界面文案由 app.js 通过 labels 注入，
// 因此本文件不必加入 web/font/collect-chars.js 白名单（中文只出现在注释里，不渲染）。
// doc 由调用方传入，使同一份代码能在浏览器与零依赖 DOM 桩下运行。

(function (root) {
  "use strict";

  const MIN_CHARS = 10;
  const POS_ORDER = ["n", "v", "adj", "adjv", "adv", "misc", "p", "aux"];
  // 单段朗读小喇叭（B8-c）：内联 SVG，红线禁 emoji 图标；无变换定位。
  const SPEAKER_SVG = '<svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true" focusable="false">' +
    '<path fill="currentColor" d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>';

  function countChars(text) {
    return Array.from(String(text == null ? "" : text).replace(/\s/g, "")).length;
  }

  function createView(doc, refs, engine, labels, hooks) {
    const text = labels || {};
    const actions = hooks || {};
    const display = { furigana: true, romaji: false, translation: true };
    let paragraphs = [];
    let cards = [];
    let states = [];
    let previousView = "input";
    let ttsEnabled = true;

    function updateCount() {
      const count = countChars(refs.textarea.value);
      refs.charCount.textContent = text.charCount ? text.charCount(count) : String(count);
      refs.startButton.disabled = count < MIN_CHARS;
      return count;
    }

    function setState(kind, message) {
      const statesMap = {
        error: refs.errorState,
        offline: refs.offlineState,
        loading: refs.loadingState
      };
      Object.keys(statesMap).forEach(function (key) {
        if (statesMap[key]) {
          statesMap[key].hidden = key !== kind;
        }
      });
      if (kind === "error" && refs.errorState) {
        refs.errorState.textContent = message || text.defaultError || "";
      }
    }

    function buildCard(paragraph, index) {
      const card = doc.createElement("article");
      card.className = "read-paragraph-card";
      card.dataset.paragraphIndex = String(index);
      card.dataset.state = "idle";

      const body = doc.createElement("p");
      body.className = "read-paragraph-text";
      body.textContent = paragraph.text;
      card.append(body);

      const translation = doc.createElement("div");
      translation.className = "read-paragraph-translation";
      translation.hidden = true;
      card.append(translation);

      const tokens = doc.createElement("div");
      tokens.className = "read-paragraph-tokens";
      tokens.hidden = true;
      card.append(tokens);

      const status = doc.createElement("p");
      status.className = "read-paragraph-status";
      status.textContent = (text.states && text.states.idle) || "";
      card.append(status);

      const grammarButton = doc.createElement("button");
      grammarButton.className = "text-button read-grammar-button";
      grammarButton.type = "button";
      grammarButton.textContent = text.grammarButton || "";
      grammarButton.hidden = true;
      grammarButton.addEventListener("click", function (event) {
        event.stopPropagation();
        if (actions.onGrammarClick) actions.onGrammarClick(index);
      });
      card.append(grammarButton);

      // B8-c 单段朗读：小喇叭固定卡片右上角，点读该段、再点停止（不触发解析）。
      const speakButton = doc.createElement("button");
      speakButton.className = "read-speak-button";
      speakButton.type = "button";
      speakButton.hidden = !ttsEnabled;
      speakButton.setAttribute("aria-label", (text.speakLabel) || "朗读该段");
      speakButton.innerHTML = SPEAKER_SVG;
      speakButton.addEventListener("click", function (event) {
        event.stopPropagation();
        if (actions.onSpeakClick) actions.onSpeakClick(index);
      });
      card.append(speakButton);

      if (paragraph.hardCut) {
        const cut = doc.createElement("span");
        cut.className = "read-paragraph-cut";
        cut.textContent = text.hardCut || "";
        card.append(cut);
      }
      if (actions.onParagraphClick) {
        card.addEventListener("click", function () {
          actions.onParagraphClick(index);
        });
      }
      return card;
    }

    function renderParagraphs(list) {
      paragraphs = list || [];
      cards = [];
      states = paragraphs.map(function () {
        return { status: "idle", data: null, message: "" };
      });
      refs.paragraphList.replaceChildren();
      paragraphs.forEach(function (paragraph, index) {
        const card = buildCard(paragraph, index);
        cards.push(card);
        refs.paragraphList.append(card);
      });
      refs.paragraphEmpty.hidden = paragraphs.length > 0;
      refs.paragraphCount.textContent = text.paragraphCount
        ? text.paragraphCount(paragraphs.length)
        : "";
      renderLegend([]);
    }

    function renderLegend(groups) {
      if (!refs.legend) return;
      refs.legend.replaceChildren();
      POS_ORDER.forEach(function (group) {
        if (groups.indexOf(group) < 0) return;
        const item = doc.createElement("span");
        item.className = "read-legend-item";
        const swatch = doc.createElement("span");
        swatch.className = "read-legend-swatch";
        swatch.dataset.posGroup = group;
        const label = doc.createElement("span");
        label.textContent = (text.posLabels && text.posLabels[group]) || group;
        item.append(swatch, label);
        refs.legend.append(item);
      });
      refs.legend.hidden = groups.length === 0;
    }

    function applyCardState(index) {
      const card = cards[index];
      const state = states[index];
      if (!card || !state) return;
      const parsed = state.status === "parsed";
      const data = state.data;
      const aligned = Boolean(parsed && data && data.alignment && data.alignment.aligned && data.tokens.length);
      const body = card.querySelectorAll(".read-paragraph-text")[0];
      const translation = card.querySelectorAll(".read-paragraph-translation")[0];
      const tokens = card.querySelectorAll(".read-paragraph-tokens")[0];
      const status = card.querySelectorAll(".read-paragraph-status")[0];
      body.hidden = aligned;
      translation.hidden = !(parsed && display.translation);
      tokens.hidden = !aligned;
      status.textContent = state.message || "";
      status.hidden = !state.message;
      const grammarButton = card.querySelectorAll(".read-grammar-button")[0];
      if (grammarButton) grammarButton.hidden = !parsed;
      card.dataset.state = state.status;
    }

    function setParagraphState(index, status, payload) {
      if (!states[index]) return;
      const options = payload || {};
      states[index].status = status;
      states[index].message = options.message !== undefined
        ? options.message
        : ((text.states && text.states[status]) || "");
      if (options.data) states[index].data = options.data;
      applyCardState(index);
    }

    // token 渲染抽公共：卡片与选区弹层共用同一套 ruby / 罗马音 / 词性底色。
    // paragraphIndex 用于点词回调定位上下文段落；返回出现过的 pos 分组供画图例。
    function renderTokenGroups(container, tokens, paragraphIndex) {
      container.replaceChildren();
      const present = [];
      let cursor = 0;
      engine.groupTokens(tokens).forEach(function (group) {
        const groupEl = doc.createElement("span");
        const isParagraphBreak = group.length === 1 && group[0].pos === "改行";
        groupEl.className = "read-token-group" + (isParagraphBreak ? " is-paragraph-break" : "");
        group.forEach(function (token) {
          const pos = engine.normalizePos(token.pos);
          if (present.indexOf(pos.group) < 0) present.push(pos.group);
          const unit = doc.createElement("span");
          unit.className = "read-token" + (token.pos === "改行" ? " is-break" : "");
          unit.dataset.posGroup = pos.group;
          const start = cursor;
          cursor += token.word.length;
          unit.dataset.start = String(start);
          unit.dataset.end = String(cursor);
          unit.addEventListener("click", function (event) {
            event.stopPropagation();
            if (actions.onTokenClick) actions.onTokenClick(paragraphIndex, token, start);
          });
          const ruby = doc.createElement("ruby");
          ruby.append(doc.createTextNode(token.word));
          if (display.furigana && token.furigana && pos.group !== "none") {
            const rt = doc.createElement("rt");
            rt.textContent = token.furigana;
            ruby.append(rt);
          }
          unit.append(ruby);
          if (display.romaji && pos.group !== "none") {
            const romaji = doc.createElement("span");
            romaji.className = "read-token-romaji";
            romaji.textContent = engine.toRomaji(token.word, token.furigana, token.pos);
            unit.append(romaji);
          }
          groupEl.append(unit);
        });
        container.append(groupEl);
      });
      return present;
    }

    function renderAnalysis(index, data) {
      const card = cards[index];
      if (!card || !states[index]) return;
      const tokensEl = card.querySelectorAll(".read-paragraph-tokens")[0];
      const translationEl = card.querySelectorAll(".read-paragraph-translation")[0];
      translationEl.textContent = data.translation || "";
      const present = renderTokenGroups(tokensEl, data.tokens, index);
      renderLegend(present.filter(function (group) { return group !== "none"; }));
      states[index].data = data;
      states[index].status = "parsed";
      states[index].message = data.alignment && data.alignment.aligned
        ? (data.translation ? "" : ((text.states && text.states.translationFailed) || ""))
        : ((text.states && text.states.unaligned) || "");
      applyCardState(index);
    }

    function setDisplay(next) {
      Object.keys(next || {}).forEach(function (key) {
        if (Object.prototype.hasOwnProperty.call(display, key)) display[key] = Boolean(next[key]);
      });
      states.forEach(function (state, index) {
        if (state.status === "parsed") renderAnalysis(index, state.data);
      });
      return Object.assign({}, display);
    }

    // B8-c：TTS 不可用时整体隐藏喇叭入口；朗读中卡片高亮发声态。
    function setTtsEnabled(enabled) {
      ttsEnabled = Boolean(enabled);
      cards.forEach(function (card) {
        const button = card.querySelectorAll(".read-speak-button")[0];
        if (button) button.hidden = !ttsEnabled;
      });
    }

    function setSpeakState(index, speaking) {
      cards.forEach(function (card, i) {
        const button = card.querySelectorAll(".read-speak-button")[0];
        if (!button) return;
        const active = Boolean(speaking) && i === index;
        button.dataset.speaking = active ? "true" : "false";
        button.setAttribute("aria-label", active
          ? ((text.stopLabel) || "停止朗读")
          : ((text.speakLabel) || "朗读该段"));
      });
    }

    function renderSheetRows(rows) {
      refs.sheetBody.replaceChildren();
      (rows || []).forEach(function (row) {
        const line = doc.createElement("div");
        line.className = "read-sheet-row";
        const label = doc.createElement("dt");
        label.textContent = row.label || "";
        const value = doc.createElement("dd");
        const values = Array.isArray(row.value) ? row.value : [row.value || ""];
        values.forEach(function (item) {
          const paragraph = doc.createElement("p");
          paragraph.textContent = item;
          value.append(paragraph);
        });
        line.append(label, value);
        refs.sheetBody.append(line);
      });
    }

    function showSheet(title, rows, buttons) {
      refs.sheetTitle.textContent = title || "";
      renderSheetRows(rows);
      refs.sheetActions.replaceChildren();
      (buttons || []).forEach(function (button) {
        const control = doc.createElement("button");
        control.type = "button";
        control.className = "outline-button read-sheet-action";
        control.textContent = button.label || "";
        control.disabled = Boolean(button.disabled);
        control.addEventListener("click", function () {
          if (actions.onSheetAction) actions.onSheetAction(button.kind);
        });
        refs.sheetActions.append(control);
      });
      refs.sheet.hidden = false;
    }

    function hideSheet() {
      refs.sheet.hidden = true;
      refs.sheetBody.replaceChildren();
      refs.sheetActions.replaceChildren();
    }

    // B7 · 划选区间合并结果进弹层（列表不动）。
    // 与卡片同规矩：未对齐只留译文（D-035 宁可少显示也不教错）。
    function showSheetAnalysis(title, data, tokenIndex) {
      refs.sheetTitle.textContent = title || "";
      refs.sheetBody.replaceChildren();
      const aligned = Boolean(data && data.alignment && data.alignment.aligned && data.tokens && data.tokens.length);
      if (aligned) {
        const tokensEl = doc.createElement("div");
        tokensEl.className = "read-paragraph-tokens";
        renderTokenGroups(tokensEl, data.tokens, tokenIndex);
        refs.sheetBody.append(tokensEl);
      }
      const translation = doc.createElement("div");
      translation.className = "read-paragraph-translation";
      translation.textContent = (data && data.translation) || "";
      refs.sheetBody.append(translation);
      refs.sheetActions.replaceChildren();
      refs.sheet.hidden = false;
    }

    // B7 · 长文运行状态条：进度文案 + 可选取消按钮。
    function showRunStatus(message, cancellable) {
      if (!refs.runStatus) return;
      if (refs.runStatusText) refs.runStatusText.textContent = message || "";
      if (refs.runCancel) refs.runCancel.hidden = !cancellable;
      refs.runStatus.hidden = false;
    }

    function hideRunStatus() {
      if (refs.runStatus) refs.runStatus.hidden = true;
    }

    // B7 · 划选动作按钮：按选区右下角坐标浮现。
    // 先显示再量宽，用 left 直接居中（不用 transform，避免命中检测与视觉盒不一致）。
    function showSelectionAction(x, y) {
      if (!refs.selectionAction) return;
      refs.selectionAction.hidden = false;
      refs.selectionAction.style.left = "0px";
      refs.selectionAction.style.top = "0px";
      const width = refs.selectionAction.offsetWidth || 0;
      refs.selectionAction.style.left = Math.round(x - width / 2) + "px";
      refs.selectionAction.style.top = Math.round(y + 8) + "px";
    }

    function hideSelectionAction() {
      if (refs.selectionAction) refs.selectionAction.hidden = true;
    }

    function highlightGrammar(index, spans) {
      const card = cards[index];
      if (!card) return;
      card.querySelectorAll(".read-token").forEach(function (unit) {
        const start = Number(unit.dataset.start);
        const end = Number(unit.dataset.end);
        const hit = (spans || []).some(function (span) {
          return Array.isArray(span) && span.length === 2 && start < span[1] && end > span[0];
        });
        unit.classList.toggle("is-grammar-hit", hit);
      });
    }

    function showReader() {
      refs.inputView.hidden = true;
      refs.readerView.hidden = false;
      refs.settingsView.hidden = true;
      refs.backButton.hidden = false;
    }

    function showInput() {
      refs.inputView.hidden = false;
      refs.readerView.hidden = true;
      refs.settingsView.hidden = true;
      refs.backButton.hidden = true;
      setState(null);
    }

    function showSettings() {
      previousView = refs.readerView.hidden ? "input" : "reader";
      refs.inputView.hidden = true;
      refs.readerView.hidden = true;
      refs.settingsView.hidden = false;
      refs.backButton.hidden = true;
      setState(null);
    }

    function backFromSettings() {
      if (previousView === "reader") showReader();
      else showInput();
    }

    function startReading() {
      if (countChars(refs.textarea.value) < MIN_CHARS) {
        return false;
      }
      renderParagraphs(engine.splitParagraphs(refs.textarea.value));
      showReader();
      return true;
    }

    return {
      updateCount: updateCount,
      setState: setState,
      renderParagraphs: renderParagraphs,
      renderAnalysis: renderAnalysis,
      setParagraphState: setParagraphState,
      setDisplay: setDisplay,
      getDisplay: function () { return Object.assign({}, display); },
      setTtsEnabled: setTtsEnabled,
      setSpeakState: setSpeakState,
      showSheet: showSheet,
      hideSheet: hideSheet,
      showSheetAnalysis: showSheetAnalysis,
      showRunStatus: showRunStatus,
      hideRunStatus: hideRunStatus,
      showSelectionAction: showSelectionAction,
      hideSelectionAction: hideSelectionAction,
      highlightGrammar: highlightGrammar,
      showReader: showReader,
      showInput: showInput,
      showSettings: showSettings,
      backFromSettings: backFromSettings,
      startReading: startReading,
      getParagraphs: function () { return paragraphs.slice(); }
    };
  }

  const api = {
    MIN_CHARS: MIN_CHARS,
    POS_ORDER: POS_ORDER,
    countChars: countChars,
    createView: createView
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  root.ReadView = api;
})(globalThis);
