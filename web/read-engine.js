// 文章分析纯逻辑引擎（B1）。
// 边界：禁 DOM、禁网络、禁浏览器存储接口、禁随机；不依赖任何外部模块。
// 本文件不进 web/font/collect-chars.js 的白名单：这里的中文只出现在注释与错误码里，
// 不参与界面渲染，因此字符串与注释都不会影响字体子集契约。
// 界面上要显示的中文一律由 app.js 依据错误码 / 结构化差异数据生成。

(function (root) {
  "use strict";

  const MAX_UNIT_LENGTH = 500;
  const MAX_CACHE_ENTRIES = 500;
  const MAX_ARTICLES = 50;

  // 13 个 pos 标签 = 11 项品詞 + 記号 / 改行两个技术标签。
  const POS_LABELS = [
    "名詞", "代名詞", "動詞", "形容詞", "形容動詞", "副詞", "連体詞",
    "接続詞", "感動詞", "助詞", "助動詞", "記号", "改行"
  ];

  // 9 组配色 + none（記号 / 改行 / 无法识别时无底色）。
  const POS_GROUPS = {
    "名詞": "n", "代名詞": "n",
    "動詞": "v",
    "形容詞": "adj",
    "形容動詞": "adjv",
    "副詞": "adv",
    "連体詞": "misc", "接続詞": "misc", "感動詞": "misc",
    "助詞": "p",
    "助動詞": "aux",
    "記号": "none", "改行": "none"
  };

  const POS_ALIASES = {
    "名词": "名詞", "代名词": "代名詞", "代词": "代名詞", "动词": "動詞",
    "形容词": "形容詞", "形容动词": "形容動詞", "形状詞": "形容動詞",
    "副词": "副詞", "连体词": "連体詞", "接续词": "接続詞",
    "感动词": "感動詞", "助词": "助詞", "助动词": "助動詞",
    "符号": "記号", "标点": "記号", "改行": "改行",
    "noun": "名詞", "pronoun": "代名詞", "verb": "動詞", "adjective": "形容詞",
    "adverb": "副詞", "particle": "助詞", "auxiliary": "助動詞",
    "symbol": "記号", "punctuation": "記号", "newline": "改行"
  };

  const ERROR_CODES = {
    BASE_URL_REQUIRED: "BASE_URL_REQUIRED",
    BASE_URL_SCHEME: "BASE_URL_SCHEME",
    MODEL_REQUIRED: "MODEL_REQUIRED",
    API_KEY_REQUIRED: "API_KEY_REQUIRED",
    TEMPERATURE_RANGE: "TEMPERATURE_RANGE",
    NO_JSON: "NO_JSON",
    BAD_JSON: "BAD_JSON",
    NO_TOKENS: "NO_TOKENS",
    EMPTY_TOKENS: "EMPTY_TOKENS",
    EMPTY_DETAIL: "EMPTY_DETAIL",
    BAD_GRAMMAR: "BAD_GRAMMAR",
    BAD_SPAN: "BAD_SPAN"
  };

  const WORD_DETAIL_FIELDS = [
    "meaning", "dictionaryForm", "usage", "conjugation", "example", "exampleMeaning"
  ];

  // ── 同步 SHA-256 ────────────────────────────────────────────────────────
  // 不用 crypto.subtle：它异步且依赖 secure context，file:// 下可用性不稳，
  // 会把所有存储 API 改成 Promise。这里的实现与 Android MessageDigest 结果一致。

  const SHA256_K = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
  ];

  function rotr(x, n) {
    return ((x >>> n) | (x << (32 - n))) >>> 0;
  }

  function utf8Bytes(str) {
    const out = [];
    for (let i = 0; i < str.length; i++) {
      const code = str.charCodeAt(i);
      if (code < 0x80) {
        out.push(code);
      } else if (code < 0x800) {
        out.push(0xc0 | (code >> 6), 0x80 | (code & 63));
      } else if (code >= 0xd800 && code <= 0xdbff && i + 1 < str.length) {
        const next = str.charCodeAt(i + 1);
        if (next >= 0xdc00 && next <= 0xdfff) {
          const cp = 0x10000 + ((code - 0xd800) << 10) + (next - 0xdc00);
          out.push(
            0xf0 | (cp >> 18),
            0x80 | ((cp >> 12) & 63),
            0x80 | ((cp >> 6) & 63),
            0x80 | (cp & 63)
          );
          i++;
        } else {
          out.push(0xef, 0xbf, 0xbd);
        }
      } else if (code < 0x10000) {
        out.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 63), 0x80 | (code & 63));
      } else {
        out.push(0xef, 0xbf, 0xbd);
      }
    }
    return out;
  }

  function sha256Hex(str) {
    const bytes = utf8Bytes(String(str == null ? "" : str));
    const bitLen = bytes.length * 8;
    const padded = bytes.concat([0x80]);
    while (padded.length % 64 !== 56) {
      padded.push(0);
    }
    const hi = Math.floor(bitLen / 4294967296);
    const lo = bitLen >>> 0;
    padded.push(
      (hi >>> 24) & 255, (hi >>> 16) & 255, (hi >>> 8) & 255, hi & 255,
      (lo >>> 24) & 255, (lo >>> 16) & 255, (lo >>> 8) & 255, lo & 255
    );

    const h = [
      0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
      0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
    ];

    for (let offset = 0; offset < padded.length; offset += 64) {
      const w = new Array(64);
      for (let i = 0; i < 16; i++) {
        const p = offset + i * 4;
        w[i] = ((padded[p] << 24) | (padded[p + 1] << 16) | (padded[p + 2] << 8) | padded[p + 3]) >>> 0;
      }
      for (let i = 16; i < 64; i++) {
        const x = w[i - 15];
        const y = w[i - 2];
        const s0 = rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3);
        const s1 = rotr(y, 17) ^ rotr(y, 19) ^ (y >>> 10);
        w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
      }
      let a = h[0], b = h[1], c = h[2], d = h[3];
      let e = h[4], f = h[5], g = h[6], hh = h[7];
      for (let i = 0; i < 64; i++) {
        const s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
        const ch = (e & f) ^ ((~e) & g);
        const t1 = (hh + s1 + ch + SHA256_K[i] + w[i]) >>> 0;
        const s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
        const maj = (a & b) ^ (a & c) ^ (b & c);
        const t2 = (s0 + maj) >>> 0;
        hh = g; g = f; f = e; e = (d + t1) >>> 0;
        d = c; c = b; b = a; a = (t1 + t2) >>> 0;
      }
      h[0] = (h[0] + a) >>> 0;
      h[1] = (h[1] + b) >>> 0;
      h[2] = (h[2] + c) >>> 0;
      h[3] = (h[3] + d) >>> 0;
      h[4] = (h[4] + e) >>> 0;
      h[5] = (h[5] + f) >>> 0;
      h[6] = (h[6] + g) >>> 0;
      h[7] = (h[7] + hh) >>> 0;
    }
    return h.map(function (value) {
      return ("00000000" + (value >>> 0).toString(16)).slice(-8);
    }).join("");
  }

  function cacheKey(text, model) {
    return sha256Hex(String(text == null ? "" : text) + "\u0000" + String(model == null ? "" : model));
  }

  // ── 配置与端点 ──────────────────────────────────────────────────────────

  function normalizeEndpoint(baseUrl) {
    const trimmed = String(baseUrl == null ? "" : baseUrl).trim();
    if (!trimmed) return "";
    const withoutSlash = trimmed.replace(/\/+$/, "");
    if (!withoutSlash) return trimmed;
    if (/\/chat\/completions$/i.test(withoutSlash)) return withoutSlash;
    const schemeEnd = withoutSlash.indexOf("://");
    const pathStart = schemeEnd >= 0 ? schemeEnd + 3 : 0;
    const slash = withoutSlash.indexOf("/", pathStart);
    const path = slash >= 0 ? withoutSlash.slice(slash).replace(/^\/+|\/+$/g, "") : "";
    return withoutSlash + (path ? "/chat/completions" : "/v1/chat/completions");
  }

  // 只返回错误码，中文文案由 app.js 映射（避免新增 JS 里出现界面文案）。
  function validateConfig(cfg) {
    const value = cfg || {};
    const errors = [];
    const baseUrl = String(value.baseUrl == null ? "" : value.baseUrl).trim();
    if (!baseUrl) {
      errors.push(ERROR_CODES.BASE_URL_REQUIRED);
    } else if (!/^https?:\/\//i.test(baseUrl)) {
      errors.push(ERROR_CODES.BASE_URL_SCHEME);
    }
    if (!String(value.model == null ? "" : value.model).trim()) {
      errors.push(ERROR_CODES.MODEL_REQUIRED);
    }
    if (!String(value.apiKey == null ? "" : value.apiKey).trim()) {
      errors.push(ERROR_CODES.API_KEY_REQUIRED);
    }
    const temperature = value.temperature;
    if (typeof temperature !== "number" || isNaN(temperature) || temperature < 0 || temperature > 1) {
      errors.push(ERROR_CODES.TEMPERATURE_RANGE);
    }
    return errors;
  }

  // ── 文本切分 ────────────────────────────────────────────────────────────

  function normalizeNewlines(text) {
    return String(text == null ? "" : text).replace(/\r\n?/g, "\n");
  }

  const HARD_CUT_CHARS = /[、，,　\s]/;

  function hardCutText(text) {
    const pieces = [];
    let rest = text;
    while (rest.length > MAX_UNIT_LENGTH) {
      let cut = -1;
      for (let i = MAX_UNIT_LENGTH; i > 0; i--) {
        if (i <= rest.length && HARD_CUT_CHARS.test(rest.charAt(i - 1))) { cut = i; break; }
      }
      if (cut < 0) cut = MAX_UNIT_LENGTH;
      pieces.push(rest.slice(0, cut));
      rest = rest.slice(cut);
    }
    if (rest.length) pieces.push(rest);
    return pieces;
  }

  // 整行空白为段界；段内保留原有换行。返回 start/end 为原文半开区间。
  function splitParagraphs(text) {
    const source = normalizeNewlines(text);
    const lines = source.split("\n");
    const result = [];
    let buffer = [];
    let start = -1;
    let cursor = 0;
    let paragraphIndex = 0;

    function flush() {
      if (!buffer.length) return;
      const joined = buffer.join("\n");
      const pieces = hardCutText(joined);
      let offset = start;
      for (let i = 0; i < pieces.length; i++) {
        result.push({
          text: pieces[i],
          start: offset,
          end: offset + pieces[i].length,
          paragraphIndex: paragraphIndex,
          hardCut: pieces.length > 1
        });
        offset += pieces[i].length;
      }
      paragraphIndex++;
      buffer = [];
      start = -1;
    }

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (line.trim() === "") {
        flush();
      } else {
        if (start < 0) start = cursor;
        buffer.push(line);
      }
      cursor += line.length + 1;
    }
    flush();
    return result;
  }

  const BRACKET_PAIRS = {
    "「": "」", "『": "』", "（": "）", "(": ")", "【": "】", "〔": "〕",
    "〈": "〉", "《": "》", "[": "]", "{": "}", "“": "”", "‘": "’"
  };
  const SENTENCE_END = /[。！？!?]/;
  const CLOSING_CHARS = /[。！？!?»」』）)】〕〉》”’]/;

  // 句级切分（供标题、句型定位使用）。引号 / 括号内的句末标点不断句。
  function split(text) {
    const source = normalizeNewlines(text);
    const out = [];
    const closers = [];
    let start = 0;
    for (let i = 0; i < source.length; i++) {
      const ch = source.charAt(i);
      if (closers.length && ch === closers[closers.length - 1]) {
        closers.pop();
        continue;
      }
      if (BRACKET_PAIRS[ch]) {
        closers.push(BRACKET_PAIRS[ch]);
        continue;
      }
      if (closers.length) continue;
      if (SENTENCE_END.test(ch)) {
        let end = i + 1;
        while (end < source.length && CLOSING_CHARS.test(source.charAt(end))) end++;
        out.push({ text: source.slice(start, end), start: start, end: end });
        start = end;
        i = end - 1;
      }
    }
    if (start < source.length) {
      out.push({ text: source.slice(start), start: start, end: source.length });
    }
    return out;
  }

  // 批文本与实际发送文本必须是同一个拼法（否则会悄悄多一个字符）。
  function mergeText(paragraphs) {
    return (paragraphs || []).map(function (item) {
      return typeof item === "string" ? item : (item && item.text) || "";
    }).join("\n");
  }

  // 按段落边界聚合，超过上限才分批；单段自身超限时独占一批。
  function chunkParagraphs(paragraphs, maxChars) {
    const limit = typeof maxChars === "number" && maxChars > 0 ? maxChars : MAX_UNIT_LENGTH;
    const batches = [];
    let current = [];

    function flush() {
      if (!current.length) return;
      batches.push({
        text: mergeText(current),
        paragraphs: current.slice(),
        indices: current.map(function (item) { return item.paragraphIndex; })
      });
      current = [];
    }

    for (const item of (paragraphs || [])) {
      const text = typeof item === "string" ? item : (item && item.text) || "";
      const unit = typeof item === "string" ? { text: text, paragraphIndex: batches.length } : item;
      if (text.length > limit) {
        flush();
        const pieces = hardCutText(text);
        for (let i = 0; i < pieces.length; i++) {
          batches.push({
            text: pieces[i],
            paragraphs: [{ text: pieces[i], paragraphIndex: unit.paragraphIndex, hardCut: true }],
            indices: [unit.paragraphIndex]
          });
        }
        continue;
      }
      if (current.length && mergeText(current.concat([unit])).length > limit) flush();
      current.push(unit);
    }
    flush();
    return batches;
  }

  // ── 响应解析 ────────────────────────────────────────────────────────────

  function stripFence(content) {
    const raw = String(content == null ? "" : content).trim();
    const fenced = raw.match(/```(?:json)?\s*([\s\S]*?)```/);
    return fenced ? fenced[1].trim() : raw;
  }

  function extractObject(text) {
    const first = text.indexOf("{");
    const last = text.lastIndexOf("}");
    return first >= 0 && last > first ? text.slice(first, last + 1) : "";
  }

  function normalizeForCompare(text) {
    return normalizeNewlines(text).replace(/[\u200b\u200c\ufeff]/g, "");
  }

  // 最长公共子序列求差异，文本上限 500 字符，500×500 的表格可接受。
  function diffChars(expected, actual) {
    const n = expected.length;
    const m = actual.length;
    const table = [];
    for (let i = 0; i <= n; i++) table.push(new Array(m + 1).fill(0));
    for (let i = n - 1; i >= 0; i--) {
      for (let j = m - 1; j >= 0; j--) {
        table[i][j] = expected.charAt(i) === actual.charAt(j)
          ? table[i + 1][j + 1] + 1
          : Math.max(table[i + 1][j], table[i][j + 1]);
      }
    }
    const missing = [];
    const extra = [];
    let i = 0, j = 0;
    while (i < n && j < m) {
      if (expected.charAt(i) === actual.charAt(j)) { i++; j++; }
      else if (table[i + 1][j] >= table[i][j + 1]) { missing.push(expected.charAt(i)); i++; }
      else { extra.push(actual.charAt(j)); j++; }
    }
    while (i < n) { missing.push(expected.charAt(i)); i++; }
    while (j < m) { extra.push(actual.charAt(j)); j++; }
    return { missing: missing, extra: extra };
  }

  function checkAlignment(tokens, source) {
    const expected = normalizeForCompare(source == null ? "" : source);
    const actual = normalizeForCompare(tokens.map(function (token) { return token.word; }).join(""));
    if (expected === actual) {
      return { aligned: true, missing: [], extra: [], sourceLength: expected.length, tokenLength: actual.length };
    }
    const diff = diffChars(expected, actual);
    return {
      aligned: false,
      missing: diff.missing,
      extra: diff.extra,
      sourceLength: expected.length,
      tokenLength: actual.length
    };
  }

  function parseFailure(code) {
    return { ok: false, tokens: [], alignment: null, failure: code };
  }

  // 逐词解析。重建不一致不视为失败：返回 aligned=false，由 UI 隐藏逐词、只留译文。
  function parseAnalysis(content, source) {
    const text = stripFence(content);
    let parsed = null;
    try {
      parsed = JSON.parse(text);
    } catch (error) {
      parsed = null;
    }
    if (parsed === null) {
      const objectText = extractObject(text);
      if (!objectText) return parseFailure(ERROR_CODES.NO_JSON);
      try {
        parsed = JSON.parse(objectText);
      } catch (error) {
        return parseFailure(ERROR_CODES.BAD_JSON);
      }
    }
    const rawTokens = Array.isArray(parsed)
      ? parsed
      : (parsed && Array.isArray(parsed.tokens) ? parsed.tokens : null);
    if (!rawTokens) return parseFailure(ERROR_CODES.NO_TOKENS);
    const tokens = [];
    for (const item of rawTokens) {
      if (!item || typeof item !== "object" || typeof item.word !== "string") continue;
      tokens.push({
        word: item.word,
        pos: typeof item.pos === "string" ? item.pos : "",
        furigana: typeof item.furigana === "string" ? item.furigana : ""
      });
    }
    if (!tokens.length) return parseFailure(ERROR_CODES.EMPTY_TOKENS);
    return { ok: true, tokens: tokens, alignment: checkAlignment(tokens, source), failure: null };
  }

  // ── B7 · 长文与流式 ────────────────────────────────────────────────────

  // 并发块数自推（D-036 禁搬参考项目常量）：每段 = analyze + translate 两路请求，
  // 并发 2 段峰值 4 路，低于多数自填端点的限速阈值；再高只提心率不提速率。
  const LONG_TEXT_CONCURRENCY = 2;
  // 划选分批上限与单段上限同尺度：段界切分，单请求不超 500 字符，避免输出截断。
  const SELECTION_BATCH_CHARS = MAX_UNIT_LENGTH;

  // 连续完成前缀长度：doneFlags 从 0 起连续 true 的个数。
  // 顺序合并的进度口径 —— 后面的块先完成也不推进前缀，界面始终按原文顺序落位。
  function contiguousPrefixLength(doneFlags) {
    const flags = doneFlags || [];
    let i = 0;
    while (i < flags.length && flags[i]) i++;
    return i;
  }

  // 宽松兜底：从可能被截断的 JSON 文本里恢复「完整」token 对象。
  // 只收无嵌套花括号的完整对象，半截对象天然落选 —— 宁可少显示也不教错（D-035）。
  function recoverTokenObjects(text) {
    const tokens = [];
    const pattern = /\{[^{}]*\}/g;
    let match;
    while ((match = pattern.exec(String(text == null ? "" : text))) !== null) {
      let obj = null;
      try {
        obj = JSON.parse(match[0]);
      } catch (error) {
        obj = null;
      }
      if (obj && typeof obj.word === "string") {
        tokens.push({
          word: obj.word,
          pos: typeof obj.pos === "string" ? obj.pos : "",
          furigana: typeof obj.furigana === "string" ? obj.furigana : ""
        });
      }
    }
    return tokens;
  }

  // 增量 JSON 扫描：已接收内容（可能半截）里数出完整 token 个数，只做进度显示，
  // 不是结果；收尾仍走 parseAnalysisWithFallback 严格校验。
  function scanIncrementalTokens(content) {
    const text = stripFence(content);
    return { count: recoverTokenObjects(text).length, chars: text.length };
  }

  // 收尾严格校验 + 宽松兜底：严格失败且能救回完整 token 才降级；
  // 宽松结果同样过原文一致性校验，不一致按未对齐降级（只显示译文）。
  function parseAnalysisWithFallback(content, source) {
    const strict = parseAnalysis(content, source);
    if (strict.ok) {
      return {
        ok: true,
        tokens: strict.tokens,
        alignment: strict.alignment,
        failure: null,
        loose: false
      };
    }
    const tokens = recoverTokenObjects(stripFence(content));
    if (!tokens.length) return strict;
    return {
      ok: true,
      tokens: tokens,
      alignment: checkAlignment(tokens, source),
      failure: null,
      loose: true
    };
  }

  // 划选区间按段界分批：texts 是选中段落的原文（已按序），超上限才并批。
  // 批文本沿用 mergeText 的段间 "\n" 拼法，批间换行不进任何批 —— 由 mergeChunkResults
  // 在批界补「改行」token，保证拼接结果与选区原文逐字一致。
  function splitSelectionBatches(texts, maxChars) {
    const items = (texts || []).map(function (value, i) {
      return { text: String(value == null ? "" : value), paragraphIndex: i };
    });
    return chunkParagraphs(items, maxChars || SELECTION_BATCH_CHARS).map(function (batch) {
      return { text: batch.text, indices: batch.indices };
    });
  }

  // 合并多批分析结果（划选区间）：token 按批序拼接，批界若跨段落补一个改行 token
  // （段间换行不在任何批文本里，但属于选区原文），再对合并全文重做一次原文一致性校验。
  // 任一批失败 → 整体失败并指出批次号，不产生合并缓存（不写半截缓存）。
  function mergeChunkResults(batchResults, sourceText) {
    const batches = batchResults || [];
    const tokens = [];
    const translations = [];
    for (let i = 0; i < batches.length; i++) {
      const item = batches[i];
      if (!item || !item.ok) {
        return {
          ok: false,
          tokens: [],
          translation: "",
          alignment: null,
          failedBatch: i
        };
      }
      if (i > 0) {
        const prevIndices = batches[i - 1].indices || [];
        const curIndices = item.indices || [];
        const sameParagraph = prevIndices.length && curIndices.length &&
          prevIndices[prevIndices.length - 1] === curIndices[0];
        if (!sameParagraph) {
          // 段间换行是选区原文的一部分，模型在单批里看不到，合并时补回。
          tokens.push({ word: "\n", pos: "改行", furigana: "" });
        }
      }
      (item.tokens || []).forEach(function (token) { tokens.push(token); });
      translations.push(String(item.translation == null ? "" : item.translation));
    }
    if (!tokens.length) {
      return { ok: false, tokens: [], translation: "", alignment: null, failedBatch: -1 };
    }
    return {
      ok: true,
      tokens: tokens,
      translation: translations.join("\n"),
      alignment: checkAlignment(tokens, sourceText),
      failedBatch: -1
    };
  }

  // 半截 JSON 的宽松兜底：按字段名出现顺序恢复字符串值。
  function looseObject(text) {
    const found = {};
    const pattern = /"([A-Za-z]+)"\s*:\s*"((?:[^"\\]|\\.)*)"/g;
    let match;
    while ((match = pattern.exec(text)) !== null) {
      try {
        found[match[1]] = JSON.parse('"' + match[2] + '"');
      } catch (error) {
        found[match[1]] = match[2];
      }
    }
    return Object.keys(found).length ? found : null;
  }

  function parseWordDetail(content) {
    const text = stripFence(content);
    let parsed = null;
    try {
      const objectText = extractObject(text);
      if (objectText) parsed = JSON.parse(objectText);
    } catch (error) {
      parsed = null;
    }
    if (!parsed || typeof parsed !== "object") parsed = looseObject(text);
    if (!parsed) return { ok: false, detail: null, failure: ERROR_CODES.BAD_JSON };
    const detail = {};
    for (const field of WORD_DETAIL_FIELDS) {
      detail[field] = typeof parsed[field] === "string" ? parsed[field] : "";
    }
    if (!detail.meaning && !detail.usage) {
      return { ok: false, detail: null, failure: ERROR_CODES.EMPTY_DETAIL };
    }
    return { ok: true, detail: detail, failure: null };
  }

  function validSpan(span, sourceLength) {
    return Array.isArray(span) && span.length === 2 &&
      typeof span[0] === "number" && typeof span[1] === "number" &&
      span[0] >= 0 && span[1] >= span[0] &&
      (typeof sourceLength !== "number" || span[1] <= sourceLength);
  }

  // Q2=2 的结构化语法结果。span 必须是原文字符半开区间，越界即拒绝。
  function parseGrammar(content, sourceLength) {
    const text = stripFence(content);
    let parsed;
    try {
      const objectText = extractObject(text);
      if (!objectText) return { ok: false, grammar: null, failure: ERROR_CODES.BAD_GRAMMAR };
      parsed = JSON.parse(objectText);
    } catch (error) {
      return { ok: false, grammar: null, failure: ERROR_CODES.BAD_GRAMMAR };
    }
    if (!parsed || !Array.isArray(parsed.patterns)) {
      return { ok: false, grammar: null, failure: ERROR_CODES.BAD_GRAMMAR };
    }
    for (const pattern of parsed.patterns) {
      if (!pattern || typeof pattern.label !== "string") {
        return { ok: false, grammar: null, failure: ERROR_CODES.BAD_GRAMMAR };
      }
      if (pattern.span && !validSpan(pattern.span, sourceLength)) {
        return { ok: false, grammar: null, failure: ERROR_CODES.BAD_SPAN };
      }
    }
    const relations = parsed.structure && Array.isArray(parsed.structure.relations)
      ? parsed.structure.relations : [];
    for (const relation of relations) {
      if (relation.from !== undefined && !validSpan([relation.from, relation.to], sourceLength)) {
        return { ok: false, grammar: null, failure: ERROR_CODES.BAD_SPAN };
      }
    }
    const refs = Array.isArray(parsed.engineRefs) ? parsed.engineRefs : [];
    // 模型只给 type / surface / span；词库 id 由 B6 在本地反查后补齐。
    const engineRefs = refs.filter(function (ref) {
      return ref && (ref.type === "verb" || ref.type === "adjective" || ref.type === "particle") &&
        (typeof ref.surface === "string" && ref.surface.length > 0 ||
          typeof ref.id === "string" && ref.id.length > 0) &&
        (!ref.span || validSpan(ref.span, sourceLength));
    });
    return {
      ok: true,
      grammar: {
        patterns: parsed.patterns.map(function (pattern) {
          return {
            label: pattern.label,
            reading: typeof pattern.reading === "string" ? pattern.reading : "",
            meaning: typeof pattern.meaning === "string" ? pattern.meaning : "",
            span: pattern.span || null,
            note: typeof pattern.note === "string" ? pattern.note : "",
            level: typeof pattern.level === "string" ? pattern.level : ""
          };
        }),
        structure: {
          clauses: parsed.structure && Array.isArray(parsed.structure.clauses) ? parsed.structure.clauses : [],
          relations: relations
        },
        style: {
          politeness: parsed.style && typeof parsed.style.politeness === "string" ? parsed.style.politeness : "",
          register: parsed.style && typeof parsed.style.register === "string" ? parsed.style.register : ""
        },
        engineRefs: engineRefs
      },
      failure: null
    };
  }

  // ── 逐词排版（禁则分组） ─────────────────────────────────────────────────

  const NO_LINE_START = /^[。、，．！？：；」』）)】〕〉》”’,.!?:;]/;
  const NO_LINE_END = /[「『（(【〔〈《“‘\[{]$/;

  function groupTokens(tokens) {
    const groups = [];
    let current = [];
    function flush() {
      if (current.length) groups.push(current);
      current = [];
    }
    for (const token of (tokens || [])) {
      if (!token) continue;
      if (token.pos === "改行") {
        flush();
        groups.push([token]);
        continue;
      }
      const previous = current.length ? current[current.length - 1] : null;
      if (previous && (NO_LINE_START.test(token.word) || token.pos === "記号" || NO_LINE_END.test(previous.word))) {
        current.push(token);
        continue;
      }
      flush();
      current = [token];
    }
    flush();
    return groups;
  }

  // ── 罗马音（本地生成，模型不输出） ────────────────────────────────────────

  const ROMAJI_MAP = {
    "あ": "a", "い": "i", "う": "u", "え": "e", "お": "o",
    "か": "ka", "き": "ki", "く": "ku", "け": "ke", "こ": "ko",
    "さ": "sa", "し": "shi", "す": "su", "せ": "se", "そ": "so",
    "た": "ta", "ち": "chi", "つ": "tsu", "て": "te", "と": "to",
    "な": "na", "に": "ni", "ぬ": "nu", "ね": "ne", "の": "no",
    "は": "ha", "ひ": "hi", "ふ": "fu", "へ": "he", "ほ": "ho",
    "ま": "ma", "み": "mi", "む": "mu", "め": "me", "も": "mo",
    "や": "ya", "ゆ": "yu", "よ": "yo",
    "ら": "ra", "り": "ri", "る": "ru", "れ": "re", "ろ": "ro",
    "わ": "wa", "ゐ": "wi", "ゑ": "we", "を": "o", "ん": "n",
    "が": "ga", "ぎ": "gi", "ぐ": "gu", "げ": "ge", "ご": "go",
    "ざ": "za", "じ": "ji", "ず": "zu", "ぜ": "ze", "ぞ": "zo",
    "だ": "da", "ぢ": "ji", "づ": "zu", "で": "de", "ど": "do",
    "ば": "ba", "び": "bi", "ぶ": "bu", "べ": "be", "ぼ": "bo",
    "ぱ": "pa", "ぴ": "pi", "ぷ": "pu", "ぺ": "pe", "ぽ": "po",
    "ゔ": "vu",
    "きゃ": "kya", "きゅ": "kyu", "きょ": "kyo",
    "ぎゃ": "gya", "ぎゅ": "gyu", "ぎょ": "gyo",
    "しゃ": "sha", "しゅ": "shu", "しょ": "sho",
    "じゃ": "ja", "じゅ": "ju", "じょ": "jo",
    "ちゃ": "cha", "ちゅ": "chu", "ちょ": "cho",
    "ぢゃ": "ja", "ぢゅ": "ju", "ぢょ": "jo",
    "にゃ": "nya", "にゅ": "nyu", "にょ": "nyo",
    "ひゃ": "hya", "ひゅ": "hyu", "ひょ": "hyo",
    "びゃ": "bya", "びゅ": "byu", "びょ": "byo",
    "ぴゃ": "pya", "ぴゅ": "pyu", "ぴょ": "pyo",
    "みゃ": "mya", "みゅ": "myu", "みょ": "myo",
    "りゃ": "rya", "りゅ": "ryu", "りょ": "ryo",
    "いぇ": "ye", "うぃ": "wi", "うぇ": "we", "うぉ": "wo",
    "しぇ": "she", "じぇ": "je", "ちぇ": "che", "てぃ": "ti", "てゅ": "tyu",
    "とぅ": "tu", "つぁ": "tsa", "つぃ": "tsi", "つぇ": "tse", "つぉ": "tso",
    "ふぁ": "fa", "ふぃ": "fi", "ふぇ": "fe", "ふぉ": "fo",
    "ゔぁ": "va", "ゔぃ": "vi", "ゔぇ": "ve", "ゔぉ": "vo",
    "くぁ": "kwa", "くぃ": "kwi", "くぇ": "kwe", "くぉ": "kwo",
    "ぐゎ": "gwa"
  };

  const PARTICLE_SPECIAL = { "は": "wa", "へ": "e", "を": "o" };
  const PUNCTUATION_ONLY = /^[\s。、，．,.!?？！:：;；「」『』（）()【】〈〉《》…・･〜～\-—―]+$/;

  function toHiragana(text) {
    const normalized = String(text || "").normalize("NFKC");
    let out = "";
    for (let i = 0; i < normalized.length; i++) {
      const code = normalized.charCodeAt(i);
      out += code >= 0x30a1 && code <= 0x30f6
        ? String.fromCharCode(code - 0x60)
        : normalized.charAt(i);
    }
    return out;
  }

  function initialOf(kana, index) {
    const two = kana.slice(index, index + 2);
    if (ROMAJI_MAP[two]) return ROMAJI_MAP[two];
    const one = kana.charAt(index);
    return ROMAJI_MAP[one] || "";
  }

  function lastVowel(text) {
    for (let i = text.length - 1; i >= 0; i--) {
      if (/[aiueo]/.test(text.charAt(i))) return text.charAt(i);
    }
    return "";
  }

  function kanaToRomaji(kanaText) {
    const kana = toHiragana(kanaText);
    let out = "";
    for (let i = 0; i < kana.length;) {
      const ch = kana.charAt(i);
      if (ch === "っ") {
        const next = initialOf(kana, i + 1);
        out += next.charAt(0) === "c" ? "t" : (next ? next.charAt(0) : "t");
        i++;
        continue;
      }
      if (ch === "ん") {
        const next = initialOf(kana, i + 1);
        out += next && /^[aiueoy]/.test(next) ? "n'" : "n";
        i++;
        continue;
      }
      if (ch === "ー") {
        out += lastVowel(out) || "-";
        i++;
        continue;
      }
      const two = kana.slice(i, i + 2);
      if (ROMAJI_MAP[two]) {
        out += ROMAJI_MAP[two];
        i += 2;
        continue;
      }
      if (ROMAJI_MAP[ch]) {
        out += ROMAJI_MAP[ch];
        i++;
        continue;
      }
      return ""; // 遇到汉字等未知字符：不猜测，整体不用假罗马音冒充
    }
    return out;
  }

  function normalizePos(pos) {
    const value = String(pos == null ? "" : pos).trim().replace(/\s+/g, "");
    if (!value) return { label: "", group: "none" };
    if (POS_LABELS.indexOf(value) >= 0) return { label: value, group: POS_GROUPS[value] };
    const alias = POS_ALIASES[value] || POS_ALIASES[value.toLowerCase()];
    if (alias) return { label: alias, group: POS_GROUPS[alias] };
    return { label: "", group: "none" };
  }

  function toRomaji(word, furigana, pos) {
    const surface = String(word == null ? "" : word);
    const normalized = normalizePos(pos);
    if (!surface || normalized.group === "none" || PUNCTUATION_ONLY.test(surface)) return "";
    if (normalized.label === "助詞" && PARTICLE_SPECIAL[surface]) {
      return PARTICLE_SPECIAL[surface];
    }
    const reading = String(furigana == null ? "" : furigana).trim() || surface;
    return kanaToRomaji(reading);
  }

  // ── B5 · 词详情上下文裁剪 ────────────────────────────────────────────────
  // 全文 ≤1000 字直接发整篇；超过则收敛到目标词所在段落，段落仍超取前后各 2 句。
  // 目的是省 token，同时不让模型失去判断该词本句用法所需的语境。

  const CONTEXT_LIMIT = 1000;
  const NEIGHBOR_SENTENCES = 2;

  function selectWordDetailContext(fullText, word, options) {
    const source = normalizeNewlines(fullText == null ? "" : fullText);
    const opts = options || {};
    const limit = typeof opts.limit === "number" && opts.limit > 0 ? opts.limit : CONTEXT_LIMIT;
    const neighbors = typeof opts.neighbors === "number" ? opts.neighbors : NEIGHBOR_SENTENCES;
    if (Array.from(source).length <= limit) return source;

    const needle = String(word == null ? "" : word);
    const paragraphs = splitParagraphs(source);
    let target = -1;
    for (let i = 0; i < paragraphs.length; i++) {
      if (needle && paragraphs[i].text.indexOf(needle) >= 0) { target = i; break; }
    }
    if (target < 0) return source.slice(0, limit);

    const paragraph = paragraphs[target].text;
    if (Array.from(paragraph).length <= limit) return paragraph;

    const sentences = split(paragraph);
    let sentenceIndex = -1;
    for (let i = 0; i < sentences.length; i++) {
      if (needle && sentences[i].text.indexOf(needle) >= 0) { sentenceIndex = i; break; }
    }
    if (sentenceIndex < 0) return paragraph.slice(0, limit);
    const from = Math.max(0, sentenceIndex - neighbors);
    const to = Math.min(sentences.length, sentenceIndex + neighbors + 1);
    return sentences.slice(from, to).map(function (item) { return item.text; }).join("");
  }

  // B5 · 词详情 LRU：只缓存已完成结果，插入顺序淘汰，同一 key 命中后提升新鲜度。
  function createLru(max) {
    const capacity = typeof max === "number" && max > 0 ? max : 80;
    const store = new Map();
    return {
      get: function (key) {
        if (!store.has(key)) return null;
        const value = store.get(key);
        store.delete(key);
        store.set(key, value);
        return value;
      },
      put: function (key, value) {
        if (store.has(key)) store.delete(key);
        store.set(key, value);
        while (store.size > capacity) {
          store.delete(store.keys().next().value);
        }
      },
      clear: function () { store.clear(); },
      size: function () { return store.size; }
    };
  }

  // ── 学习闭环反查（B6 接线用，这里只做与具体引擎无关的纯匹配） ──────────────
  // catalogs: [{ type, id, dictionary, reading, forms: { formId: [surface...] } }]

  // B6 · 把三个既有引擎的词库展开成可由文章词面反查的目录。
  // 只读取引擎导出的纯数据，不改动任何引擎；返回的是全新数组，可安全缓存。
  function buildEngineCatalogs(verbEngine, adjEngine, particleEngine) {
    const catalogs = [];
    if (verbEngine && verbEngine.VERBS) {
      verbEngine.VERBS.forEach(function (verb) {
        const forms = {};
        (verbEngine.VOICES || []).forEach(function (voice) {
          (verbEngine.INFLECTIONS || []).forEach(function (inflection) {
            let result;
            try {
              result = verbEngine.conjugate(verb, voice.id, inflection.id);
            } catch (error) {
              return;
            }
            forms[voice.id + "::" + inflection.id] = [result.written, result.kana];
          });
        });
        catalogs.push({
          type: "verb", id: verb.id, dictionary: verb.dictionary,
          reading: verb.reading, forms: forms
        });
      });
    }
    if (adjEngine && adjEngine.ADJECTIVES) {
      adjEngine.ADJECTIVES.forEach(function (adjective) {
        const forms = {};
        (adjEngine.FORMS || []).forEach(function (form) {
          let result;
          try {
            result = adjEngine.conjugate(adjective, form.id);
          } catch (error) {
            return;
          }
          forms[form.id] = adjEngine.answerVariants
            ? adjEngine.answerVariants(result)
            : [result.written, result.kana];
        });
        catalogs.push({
          type: "adjective", id: adjective.id, dictionary: adjective.word,
          reading: adjective.kana, forms: forms
        });
      });
    }
    if (particleEngine && particleEngine.LESSONS) {
      particleEngine.LESSONS.forEach(function (lesson) {
        (lesson.particles || []).forEach(function (particle) {
          catalogs.push({
            type: "particle", id: lesson.id, dictionary: particle,
            reading: particle, forms: {}
          });
        });
      });
    }
    return catalogs;
  }

  function resolveEngineRef(token, catalogs) {
    if (!token || !Array.isArray(catalogs)) return null;
    const label = normalizePos(token.pos).label;
    const allowed = { "動詞": "verb", "形容詞": "adjective", "形容動詞": "adjective", "助詞": "particle" };
    const expectedType = allowed[label];
    if (!expectedType) return null;
    const surface = String(token.word || "");
    const reading = String(token.furigana || "");
    for (const entry of catalogs) {
      if (!entry || entry.type !== expectedType) continue;
      if (entry.dictionary === surface || (reading && entry.reading === reading && entry.dictionary === surface)) {
        return { type: entry.type, id: entry.id, formId: null, surface: surface };
      }
      const forms = entry.forms || {};
      for (const formId of Object.keys(forms)) {
        const variants = forms[formId] || [];
        if (variants.indexOf(surface) >= 0) {
          return { type: entry.type, id: entry.id, formId: formId, surface: surface };
        }
      }
    }
    return null;
  }

  // ── 容量裁剪 ────────────────────────────────────────────────────────────

  function trimByTime(entries, max, field) {
    if (max <= 0) return [];
    if (!Array.isArray(entries) || entries.length <= max) return (entries || []).slice();
    return entries
      .map(function (item, index) { return { item: item, index: index }; })
      .sort(function (a, b) {
        const av = String(a.item && a.item[field] || "");
        const bv = String(b.item && b.item[field] || "");
        if (av === bv) return a.index - b.index;
        return av > bv ? -1 : 1;
      })
      .slice(0, max)
      .map(function (row) { return row.item; });
  }

  function trimCache(entries, max) {
    return trimByTime(entries, typeof max === "number" ? max : MAX_CACHE_ENTRIES, "createdAt");
  }

  function trimArticles(items, max) {
    return trimByTime(items, typeof max === "number" ? max : MAX_ARTICLES, "updatedAt");
  }

  const api = {
    MAX_UNIT_LENGTH: MAX_UNIT_LENGTH,
    MAX_CACHE_ENTRIES: MAX_CACHE_ENTRIES,
    MAX_ARTICLES: MAX_ARTICLES,
    POS_LABELS: POS_LABELS,
    POS_GROUPS: POS_GROUPS,
    ERROR_CODES: ERROR_CODES,
    WORD_DETAIL_FIELDS: WORD_DETAIL_FIELDS,
    CONTEXT_LIMIT: CONTEXT_LIMIT,
    NEIGHBOR_SENTENCES: NEIGHBOR_SENTENCES,
    selectWordDetailContext: selectWordDetailContext,
    createLru: createLru,
    sha256Hex: sha256Hex,
    cacheKey: cacheKey,
    normalizeEndpoint: normalizeEndpoint,
    validateConfig: validateConfig,
    splitParagraphs: splitParagraphs,
    split: split,
    hardCutText: hardCutText,
    chunkParagraphs: chunkParagraphs,
    mergeText: mergeText,
    stripFence: stripFence,
    diffChars: diffChars,
    checkAlignment: checkAlignment,
    parseAnalysis: parseAnalysis,
    parseAnalysisWithFallback: parseAnalysisWithFallback,
    recoverTokenObjects: recoverTokenObjects,
    scanIncrementalTokens: scanIncrementalTokens,
    contiguousPrefixLength: contiguousPrefixLength,
    splitSelectionBatches: splitSelectionBatches,
    mergeChunkResults: mergeChunkResults,
    LONG_TEXT_CONCURRENCY: LONG_TEXT_CONCURRENCY,
    SELECTION_BATCH_CHARS: SELECTION_BATCH_CHARS,
    parseWordDetail: parseWordDetail,
    parseGrammar: parseGrammar,
    groupTokens: groupTokens,
    kanaToRomaji: kanaToRomaji,
    toHiragana: toHiragana,
    toRomaji: toRomaji,
    normalizePos: normalizePos,
    buildEngineCatalogs: buildEngineCatalogs,
    resolveEngineRef: resolveEngineRef,
    trimCache: trimCache,
    trimArticles: trimArticles
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = api;
  }
  root.ReadEngine = api;
})(globalThis);
