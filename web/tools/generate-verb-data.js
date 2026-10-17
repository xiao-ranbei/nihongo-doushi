"use strict";

// 从 web/verb-engine.js 生成 Android 端 Kotlin 数据文件（D-019 原则：数据同源）。
// 用法：
//   node web/tools/generate-verb-data.js
// 输出：
//   android/app/src/main/java/com/nihongodoushi/app/engine/verb/VerbData.kt
//
// 生成后由 CrossSampleConsistencyTest 与 JS 样例逐条比对，防止两端漂移；
// 不手工维护 Kotlin 数据。改词库/形式/说明文案请改 JS 源并重跑本生成器。

const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");
const engine = require(path.join(ROOT, "verb-engine.js"));

const OUT = path.join(
  ROOT,
  "..",
  "android",
  "app",
  "src",
  "main",
  "java",
  "com",
  "nihongodoushi",
  "app",
  "engine",
  "verb",
  "VerbData.kt",
);

/** Kotlin 字符串字面量：转义反斜杠、双引号、$ 与换行。 */
function kt(value) {
  const text = String(value)
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/\$/g, "\\$")
    .replace(/\r?\n/g, "\\n")
    .replace(/\t/g, "\\t");
  return '"' + text + '"';
}

/** 字符串 -> 字符串 映射的 Kotlin mapOf 字面量，每项一行。 */
function ktStringMap(map, indent) {
  const pad = " ".repeat(indent);
  const lines = [pad + "mapOf("];
  Object.keys(map).forEach(function (key) {
    lines.push(pad + "    " + kt(key) + " to " + kt(map[key]) + ",");
  });
  lines.push(pad + ")");
  return lines;
}

/** List<T> 字面量：items 经 blockFn 转成已缩进的构造行。 */
function ktList(name, type, items, blockFn, indent) {
  const pad = " ".repeat(indent);
  const lines = [pad + "val " + name + ": List<" + type + "> = listOf("];
  items.forEach(function (item) {
    lines.push.apply(lines, blockFn(item, indent + 4));
  });
  lines.push(pad + ")");
  return lines;
}

const L = [];
function push(lines) {
  lines.forEach(function (line) {
    L.push(line);
  });
}

// ---------- 文件头 ----------

push([
  "package com.nihongodoushi.app.engine.verb",
  "",
  "/**",
  " * 动词引擎词库与形式常量（自动生成，勿手改）。",
  " *",
  " * 生成器：web/tools/generate-verb-data.js",
  " * 源：web/verb-engine.js（只读参考源）",
  " * 规模：" + engine.VERBS.length + " 个词条 / " + engine.VOICES.length + " 种语态 / " +
    engine.INFLECTIONS.length + " 种活用形式",
  " *",
  " * 数据忠实移植自 JS 源，逐字段一致：语态/活用形式的说明文案、词条字段、",
  " * 行映射与罗马字表均与 JS 版相同，日文与中文文案原样保留，不做改写。",
  " *",
  " * 本文件与 VerbEngine.kt 均为纯逻辑模块：",
  " * 禁止 import Android/androidx 类，禁止文件系统 / DOM / 随机数访问，",
  " * 所有函数确定性、可复现。",
  " */",
  "",
  "/** 语态元信息（对应 JS 的 VOICES 条目）。 */",
  "data class VoiceInfo(",
  "    val id: String,",
  "    val label: String,",
  "    val meaning: String,",
  ")",
  "",
  "/** 活用形式元信息（对应 JS 的 INFLECTIONS 条目）。 */",
  "data class InflectionInfo(",
  "    val id: String,",
  "    val label: String,",
  "    val level: String,",
  "    val meaning: String,",
  ")",
  "",
  "/** 动词词条（对应 JS 的 VERBS 条目）。 */",
  "data class VerbEntry(",
  "    val id: String,",
  "    val dictionary: String,",
  "    val reading: String,",
  "    val meaning: String,",
  "    val group: String,",
  "    val kind: String,",
  ")",
  "",
  "object VerbData {",
  "",
]);

// ---------- 词库与元信息 ----------

push(ktList("VOICES", "VoiceInfo", engine.VOICES, function (voice, indent) {
  const pad = " ".repeat(indent);
  return [pad + "VoiceInfo(" + kt(voice.id) + ", " + kt(voice.label) + ", " + kt(voice.meaning) + "),"];
}, 4));

push([""]);

push(ktList("INFLECTIONS", "InflectionInfo", engine.INFLECTIONS, function (form, indent) {
  const pad = " ".repeat(indent);
  return [pad + "InflectionInfo(" +
    kt(form.id) + ", " + kt(form.label) + ", " + kt(form.level) + ", " + kt(form.meaning) + "),"];
}, 4));

push([""]);

push(ktList("VERBS", "VerbEntry", engine.VERBS, function (verb, indent) {
  const pad = " ".repeat(indent);
  return [pad + "VerbEntry(" +
    kt(verb.id) + ", " + kt(verb.dictionary) + ", " + kt(verb.reading) + ", " +
    kt(verb.meaning) + ", " + kt(verb.group) + ", " + kt(verb.kind) + "),"];
}, 4));

push([""]);

push([
  "    val VOICE_IDS: Set<String> = VOICES.map { it.id }.toSet()",
  "    val INFLECTION_IDS: Set<String> = INFLECTIONS.map { it.id }.toSet()",
  "",
  "    val VERB_BY_ID: Map<String, VerbEntry> = VERBS.associateBy { it.id }",
  "    val VOICE_BY_ID: Map<String, VoiceInfo> = VOICES.associateBy { it.id }",
  "    val INFLECTION_BY_ID: Map<String, InflectionInfo> = INFLECTIONS.associateBy { it.id }",
  "",
]);

// ---------- 行映射 ----------

const ROW_MAP_COMMENTS = {
  I_ROW: "五段动词词尾对应行（う段 -> い段），键值与 JS 版逐项一致。",
  A_ROW: "五段动词词尾对应行（う段 -> あ段），键值与 JS 版逐项一致。",
  E_ROW: "五段动词词尾对应行（う段 -> え段），键值与 JS 版逐项一致。",
  O_ROW: "五段动词词尾对应行（う段 -> お段），键值与 JS 版逐项一致。",
  GODAN_TE: "五段动词て形音便表，键值与 JS 版逐项一致。",
  GODAN_PAST: "五段动词た形音便表，键值与 JS 版逐项一致。",
};

[
  ["I_ROW", engine.I_ROW],
  ["A_ROW", engine.A_ROW],
  ["E_ROW", engine.E_ROW],
  ["O_ROW", engine.O_ROW],
  ["GODAN_TE", engine.GODAN_TE],
  ["GODAN_PAST", engine.GODAN_PAST],
].forEach(function (entry) {
  push(["    // " + ROW_MAP_COMMENTS[entry[0]]]);
  push(["    internal val " + entry[0] + ": Map<String, String> ="]);
  push(ktStringMap(entry[1], 8));
  push([""]);
});

// ---------- 罗马字表与六形提示 ----------

push(["    // 罗马字 -> 平假名 映射，键值与 JS 版 ROMAJI 表逐项一致（含 si/ti/tu/hu/zi/di/du 等变体拼写）。"]);
push(["    internal val ROMAJI: Map<String, String> ="]);
push(ktStringMap(engine.ROMAJI, 8));
push([""]);

push(["    // 六种活用形对应提示（对应 JS 的 TRADITIONAL_FORM_HINTS），用于 explain 的「六形对应」行。"]);
push(["    internal val TRADITIONAL_FORM_HINTS: Map<String, String> ="]);
push(ktStringMap(engine.TRADITIONAL_FORM_HINTS, 8));
push([""]);

push(["}"]);

const content = L.join("\n") + "\n";

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, content, "utf8");

process.stdout.write(
  "wrote " +
    path.relative(process.cwd(), OUT).replace(/\\/g, "/") +
    " (" +
    engine.VERBS.length +
    " verbs, " +
    engine.VOICES.length +
    " voices, " +
    engine.INFLECTIONS.length +
    " inflections, " +
    content.length +
    " chars)\n",
);
