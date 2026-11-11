"use strict";

// 语法整理引擎：纯内容数据，测试守护的是「结构完整」与「教学口径不跑偏」。
// 内容一旦被改错（例如把名词说成会活用），这里比视觉验收更早拦住。

const assert = require("node:assert/strict");
const test = require("node:test");
const grammar = require("../grammar-engine.js");

test("三主题按「词内变形 → 功能转换 → 句子打包」排列", function () {
  assert.deepEqual(grammar.TOPIC_IDS, [
    "inflection", "noun-adverb", "embedded-question"
  ]);
  assert.deepEqual(grammar.TOPIC_IDS, grammar.TOPICS.map(function (topic) {
    return topic.id;
  }));
});

test("内容自检无问题，每个主题四块结构齐全", function () {
  assert.deepEqual(grammar.validateTopics(), []);
  grammar.TOPICS.forEach(function (topic) {
    assert.ok(topic.lead.length > 20, topic.id + " 结论过短");
    assert.ok(topic.summary.length > 4, topic.id + " 缺总纲");
    assert.ok(topic.table.columns.length >= 3, topic.id + " 对照表列数不足");
    assert.ok(topic.table.rows.length >= 3, topic.id + " 对照表行数不足");
    assert.ok(topic.examples.length >= 3, topic.id + " 例句不足");
    assert.ok(topic.pitfalls.length >= 3, topic.id + " 易错点不足");
    assert.ok(topic.practice.length >= 2, topic.id + " 练一练入口不足");
    topic.examples.forEach(function (example) {
      assert.ok(example.jp && example.kana && example.zh && example.note);
    });
    topic.pitfalls.forEach(function (pitfall) {
      assert.ok(pitfall.wrong && pitfall.right && pitfall.note);
    });
  });
});

test("对照表每行列数与表头一致", function () {
  grammar.TOPICS.forEach(function (topic) {
    const width = topic.table.columns.length;
    topic.table.rows.forEach(function (row, index) {
      assert.equal(
        row.length, width,
        topic.id + " 第 " + (index + 1) + " 行列数不符"
      );
      row.forEach(function (cell) {
        assert.ok(cell && String(cell).length > 0, topic.id + " 有空单元格");
      });
    });
  });
});

test("教学口径：体言不活用、用言才变形、疑问短句是名词化", function () {
  const overview = grammar.getTopic("inflection");
  const adverb = grammar.getTopic("noun-adverb");
  const question = grammar.getTopic("embedded-question");

  assert.match(overview.lead, /只有用言/);
  assert.match(overview.lead, /名词.*不变形/);
  assert.match(adverb.lead, /不活用/);
  assert.match(adverb.summary, /助词和后缀/);
  assert.match(question.lead, /名词性成分/);
  assert.match(question.summary, /名词/);

  // 「静かに／速く」必须被明确点破不是名词变形，否则这个主题等于没讲。
  const pitfallText = adverb.pitfalls.map(function (item) {
    return item.wrong + item.right + item.note;
  }).join("");
  assert.match(pitfallText, /な形容词/);
  assert.match(pitfallText, /い形容词/);
  assert.match(pitfallText, /不是名词/);
});

test("表格里区分「有活用」与「无活用」两类，别一刀切", function () {
  const rows = grammar.getTopic("inflection").table.rows;
  const flags = rows.map(function (row) { return row[1]; });
  assert.ok(flags.indexOf("有") !== -1, "应列出会活用的词性");
  assert.ok(flags.indexOf("无") !== -1, "应列出不活用的词性");
  assert.match(rows.map(function (row) { return row[0]; }).join(""), /助动词/);
});

test("练一练落点全部指向既有模块，不造新题型", function () {
  grammar.TOPICS.forEach(function (topic) {
    topic.practice.forEach(function (link) {
      assert.ok(grammar.PRACTICE_TARGETS.indexOf(link.target) !== -1,
        topic.id + " 的落点无效：" + link.target);
      assert.ok(link.label, topic.id + " 的入口缺文案");
    });
  });
});

test("getTopic 拒绝未知主题，返回的是同一份数据", function () {
  assert.strictEqual(grammar.getTopic("noun-adverb"), grammar.TOPICS[1]);
  assert.throws(function () {
    grammar.getTopic("nope");
  }, RangeError);
});

test("UMD 同时挂到浏览器全局与 Node 导出", function () {
  assert.strictEqual(globalThis.JapaneseGrammarEngine, grammar);
});
