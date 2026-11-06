"use strict";

// read-stats 单测（B8-f）：纯函数核心——入账口径、坏数据防御、汇总行。
// 红线断言：任何路径都不产生原文 / 密钥字段（不出网埋点的可执行证明）。

const assert = require("node:assert/strict");
const test = require("node:test");

const ReadStats = require("../read-stats.js");

test("空记录骨架：版本、三计数、七错误组齐全", function () {
  const record = ReadStats.emptyRecord();
  assert.equal(record.v, ReadStats.SCHEMA);
  assert.equal(record.parseCount, 0);
  assert.equal(record.cacheHits, 0);
  assert.equal(record.totalMs, 0);
  assert.deepEqual(Object.keys(record.errors).sort(), [
    "auth", "budget", "config", "network", "other", "parse", "server"
  ]);
});

test("normalize：坏数据逐项回退，非法 errors 表整体重置", function () {
  const bad = {
    v: ReadStats.SCHEMA,
    parseCount: -3,
    cacheHits: "x",
    totalMs: 2.7,
    errors: { network: "boom", parse: 4, unknown: 9 },
    updatedAt: 123
  };
  const record = ReadStats.normalize(bad);
  assert.equal(record.parseCount, 0, "负数不入账");
  assert.equal(record.cacheHits, 0, "非数字不入账");
  assert.equal(record.totalMs, 2, "取整");
  assert.equal(record.errors.parse, 4, "合法键保留");
  assert.equal(record.errors.network, 0, "非法计数回退 0");
  assert.equal(record.errors.other, 0, "未知键忽略");
  assert.equal(record.updatedAt, "", "非字符串时间戳回退");
});

test("normalize：版本不认识整体回退空记录", function () {
  const record = ReadStats.normalize({ v: 99, parseCount: 5 });
  assert.equal(record.parseCount, 0);
  assert.equal(record.v, ReadStats.SCHEMA);
});

test("load：坏 JSON 回退空记录", function () {
  assert.equal(ReadStats.load("{oops").parseCount, 0);
  assert.equal(ReadStats.load("").parseCount, 0);
  assert.equal(ReadStats.load(null).parseCount, 0);
});

test("recordOutcome：网络解析成功计次数与耗时", function () {
  const record = ReadStats.recordOutcome(ReadStats.emptyRecord(), {
    ok: true,
    fromCache: false
  }, 120, "2026-11-06T00:00:00.000Z");
  assert.equal(record.parseCount, 1);
  assert.equal(record.totalMs, 120);
  assert.equal(record.cacheHits, 0);
  assert.equal(record.updatedAt, "2026-11-06T00:00:00.000Z");
});

test("recordOutcome：缓存命中计命中不计耗时", function () {
  const record = ReadStats.recordOutcome(ReadStats.emptyRecord(), {
    ok: true,
    fromCache: true
  }, 5, "");
  assert.equal(record.cacheHits, 1);
  assert.equal(record.parseCount, 0);
  assert.equal(record.totalMs, 0);
});

test("recordOutcome：错误按分组入账，取消不计", function () {
  let record = ReadStats.emptyRecord();
  record = ReadStats.recordOutcome(record, { ok: false, error: "TIMEOUT" }, 3, "");
  record = ReadStats.recordOutcome(record, { ok: false, error: "PARSE_FAILED" }, 3, "");
  record = ReadStats.recordOutcome(record, { ok: false, local: "BUDGET" }, 0, "");
  record = ReadStats.recordOutcome(record, { ok: false, error: "WEIRD_CODE" }, 3, "");
  record = ReadStats.recordOutcome(record, {
    ok: false,
    cancelled: true,
    error: "NETWORK"
  }, 3, "");
  assert.equal(record.errors.network, 1, "TIMEOUT 归 network");
  assert.equal(record.errors.parse, 1, "PARSE_FAILED 归 parse");
  assert.equal(record.errors.budget, 1, "BUDGET 归 budget");
  assert.equal(record.errors.other, 1, "未知码归 other");
  assert.equal(record.parseCount + record.cacheHits, 0, "失败与取消都不计解析次数");
});

test("groupOf：错误码到分类映射稳定", function () {
  assert.equal(ReadStats.groupOf("AUTH"), "auth");
  assert.equal(ReadStats.groupOf("SERVER"), "server");
  assert.equal(ReadStats.groupOf("NOT_CONFIGURED"), "config");
  assert.equal(ReadStats.groupOf("OFFLINE"), "network");
  assert.equal(ReadStats.groupOf(null), "other");
});

test("summarize：平均耗时按网络解析次数摊", function () {
  let record = ReadStats.emptyRecord();
  record = ReadStats.recordOutcome(record, { ok: true, fromCache: false }, 100, "");
  record = ReadStats.recordOutcome(record, { ok: true, fromCache: false }, 250, "");
  record = ReadStats.recordOutcome(record, { ok: true, fromCache: true }, 0, "");
  const view = ReadStats.summarize(record);
  assert.equal(view.parseCount, 2);
  assert.equal(view.cacheHits, 1);
  assert.equal(view.avgMs, 175);
});

test("persist：写入存储并可读回，写失败降级返回 false", function () {
  const box = { data: null };
  const fakeStorage = {
    setItem: function (key, value) {
      box.data = { key: key, value: value };
    }
  };
  const record = ReadStats.recordOutcome(ReadStats.emptyRecord(), {
    ok: true,
    fromCache: false
  }, 40, "t1");
  assert.equal(ReadStats.persist(record, fakeStorage), true);
  assert.equal(box.data.key, ReadStats.STORAGE_KEY);
  assert.equal(ReadStats.load(box.data.value).parseCount, 1);
  assert.equal(ReadStats.persist(record, null), false);
  assert.equal(ReadStats.persist(record, {
    setItem: function () {
      throw new Error("quota");
    }
  }), false);
});

test("红线：记录结构不携带原文、密钥或用户输入字段", function () {
  const record = ReadStats.recordOutcome(ReadStats.emptyRecord(), {
    ok: true,
    fromCache: false,
    data: { tokens: [{ word: "秘密の原文" }] },
    error: "AUTH"
  }, 10, "");
  const json = JSON.stringify(record);
  assert.ok(!json.includes("秘密"), "入账结果不得携带输入内容");
  assert.ok(!/api[-_]?key|token|secret/i.test(json), "不得出现密钥类字段名");
  assert.deepEqual(Object.keys(record).sort(), [
    "cacheHits", "errors", "parseCount", "totalMs", "updatedAt", "v"
  ]);
});
