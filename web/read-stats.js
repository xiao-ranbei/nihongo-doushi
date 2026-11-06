// 本地埋点（B8-f）：只记解析次数 / 缓存命中 / 耗时 / 错误分类。
// 硬边界（D-037）：不出网、不存原文、不存密钥、不含用户输入内容。
// 纯函数核心 + 薄持久化壳；schema 带版本号，坏数据一律回退空记录。
(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.ReadStats = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const STORAGE_KEY = "japanese-read-analyzer-stats-v1";
  const SCHEMA = 1;

  // 错误分类：统计维度归并，不记原始 message（可能带 URL / 用户数据）。
  const ERROR_GROUPS = {
    NETWORK: "network",
    TIMEOUT: "network",
    OFFLINE: "network",
    AUTH: "auth",
    ENDPOINT: "server",
    RATE_LIMIT: "server",
    SERVER: "server",
    MALFORMED: "parse",
    TRUNCATED: "parse",
    PARSE_FAILED: "parse",
    BAD_REQUEST: "parse",
    BUDGET: "budget",
    NOT_CONFIGURED: "config"
  };
  const ERROR_GROUP_NAMES = ["network", "auth", "server", "parse", "budget", "config", "other"];

  const emptyRecord = function () {
    const errors = {};
    ERROR_GROUP_NAMES.forEach(function (name) {
      errors[name] = 0;
    });
    return {
      v: SCHEMA,
      parseCount: 0,
      cacheHits: 0,
      totalMs: 0,
      errors: errors,
      updatedAt: ""
    };
  };

  const countOf = function (value) {
    return typeof value === "number" && isFinite(value) && value > 0 ? Math.floor(value) : 0;
  };

  // 防御性校验：缺字段 / 类型错 / 版本不认识 → 逐项回退，坏 errors 表整体重置。
  const normalize = function (raw) {
    const record = emptyRecord();
    if (!raw || typeof raw !== "object") return record;
    if (raw.v !== SCHEMA) return record;
    record.parseCount = countOf(raw.parseCount);
    record.cacheHits = countOf(raw.cacheHits);
    record.totalMs = countOf(raw.totalMs);
    if (raw.errors && typeof raw.errors === "object") {
      ERROR_GROUP_NAMES.forEach(function (name) {
        record.errors[name] = countOf(raw.errors[name]);
      });
    }
    if (typeof raw.updatedAt === "string") record.updatedAt = raw.updatedAt;
    return record;
  };

  const load = function (raw) {
    if (!raw) return emptyRecord();
    try {
      return normalize(JSON.parse(raw));
    } catch (error) {
      return emptyRecord();
    }
  };

  const groupOf = function (code) {
    return ERROR_GROUPS[code] || "other";
  };

  // 单条结果入账（纯函数，返回新记录）。取消不计错误；缓存命中不计耗时。
  const recordOutcome = function (record, outcome, durationMs, nowIso) {
    const next = normalize(record);
    if (!outcome || typeof outcome !== "object") return next;
    if (outcome.cancelled) return next;
    if (outcome.ok) {
      if (outcome.fromCache) {
        next.cacheHits += 1;
      } else {
        next.parseCount += 1;
        next.totalMs += countOf(durationMs);
      }
    } else if (outcome.local || outcome.error) {
      next.errors[groupOf(outcome.local || outcome.error)] += 1;
    }
    next.updatedAt = typeof nowIso === "string" ? nowIso : next.updatedAt;
    return next;
  };

  const persist = function (record, storage) {
    if (!storage || typeof storage.setItem !== "function") return false;
    try {
      storage.setItem(STORAGE_KEY, JSON.stringify(normalize(record)));
      return true;
    } catch (error) {
      return false;
    }
  };

  // 设置页统计区的展示行（只读汇总，不暴露原始码）。
  const summarize = function (record) {
    const r = normalize(record);
    const ops = r.parseCount + r.cacheHits;
    const avgMs = r.parseCount > 0 ? Math.round(r.totalMs / r.parseCount) : 0;
    return {
      parseCount: r.parseCount,
      cacheHits: r.cacheHits,
      avgMs: avgMs,
      totalMs: r.totalMs,
      errors: r.errors,
      updatedAt: r.updatedAt
    };
  };

  return {
    STORAGE_KEY: STORAGE_KEY,
    SCHEMA: SCHEMA,
    ERROR_GROUP_NAMES: ERROR_GROUP_NAMES,
    emptyRecord: emptyRecord,
    normalize: normalize,
    load: load,
    groupOf: groupOf,
    recordOutcome: recordOutcome,
    persist: persist,
    summarize: summarize
  };
});
