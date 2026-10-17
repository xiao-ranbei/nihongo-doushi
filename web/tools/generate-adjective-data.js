"use strict";

// 从 web/adjective-engine.js 生成 Android 端 Kotlin 数据文件（D-019 原则：数据同源）。
// 用法：
//   node web/tools/generate-adjective-data.js
// 输出：
//   android/app/src/main/java/com/nihongodoushi/app/engine/adjective/AdjectiveData.kt
//
// 生成后由 CrossSampleConsistencyTest 与 JS 样例逐条比对，防止两端漂移；
// 不手工维护 Kotlin 数据。改词库/形式/规则文案请改 JS 源并重跑本生成器。

const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");
const engine = require(path.join(ROOT, "adjective-engine.js"));

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
  "adjective",
  "AdjectiveData.kt",
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
  "package com.nihongodoushi.app.engine.adjective",
  "",
  "/**",
  " * 形容词引擎词库与形式常量（自动生成，勿手改）。",
  " *",
  " * 生成器：web/tools/generate-adjective-data.js",
  " * 源：web/adjective-engine.js（只读参考源）",
  " * 规模：" + engine.ADJECTIVES.length + " 个词条 / " + engine.FORMS.length + " 种形式",
  " *",
  " * 数据忠实移植自 JS 源，逐字段一致：",
  " * 词条、形式元信息、规则说明文案均与 JS 版相同，日文与中文文案原样保留。",
  " *",
  " * 答案归一化复用 VerbEngine.normalizeAnswer，与 JS 版复用 verb-engine 的做法一致。",
  " *",
  " * 纯逻辑模块：无 Android/androidx 依赖，无文件系统 / DOM / 随机数访问，全部确定性。",
  " */",
  "",
  "/** 形容词词条（对应 JS 的 ADJECTIVES 条目）。 */",
  "data class AdjectiveEntry(",
  "    val id: String,",
  "    val word: String,",
  "    val kana: String,",
  "    val meaning: String,",
  "    val kind: String,",
  "    val special: Boolean = false,",
  ")",
  "",
  "/** 形式元信息（对应 JS 的 FORMS 条目）。 */",
  "data class FormInfo(",
  "    val id: String,",
  "    val label: String,",
  "    val level: String,",
  "    val meaning: String,",
  "    /** 仅形态练习用：该形式在日常书面中不独立出现（未然形接推量う / 命令请求表达）。 */",
  "    val morphologyPracticeOnly: Boolean = false,",
  ")",
  "",
  "object AdjectiveData {",
  "",
]);

// ---------- 词库与形式 ----------

push(ktList("ADJECTIVES", "AdjectiveEntry", engine.ADJECTIVES, function (item, indent) {
  const pad = " ".repeat(indent);
  const special = item.special ? ", special = true" : "";
  return [pad + "AdjectiveEntry(" +
    kt(item.id) + ", " + kt(item.word) + ", " + kt(item.kana) + ", " +
    kt(item.meaning) + ", " + kt(item.kind) + special + "),"];
}, 4));

push([""]);

push(ktList("FORMS", "FormInfo", engine.FORMS, function (form, indent) {
  const pad = " ".repeat(indent);
  const practiceOnly = form.morphologyPracticeOnly ? ", morphologyPracticeOnly = true" : "";
  return [pad + "FormInfo(" +
    kt(form.id) + ", " + kt(form.label) + ", " + kt(form.level) + ", " + kt(form.meaning) +
    practiceOnly + "),"];
}, 4));

push([""]);

push([
  "    val ADJECTIVE_BY_ID: Map<String, AdjectiveEntry> = ADJECTIVES.associateBy { it.id }",
  "    val FORM_BY_ID: Map<String, FormInfo> = FORMS.associateBy { it.id }",
  "",
]);

// ---------- 变形映射 ----------

push(["    // い形容词：去掉词尾「い」，再接词尾。修饰名词与现在肯定保持原形。"]);
push(["    internal val I_SUFFIXES: Map<String, String> ="]);
push(ktStringMap(engine.I_SUFFIXES, 8));
push([""]);

push(["    // 「いい」是唯一不按普通规则变形的い形容词：全部换成「よく」系列。"]);
push(["    internal val II_RESULTS: Map<String, String> ="]);
push(ktStringMap(engine.II_RESULTS, 8));
push([""]);

push(["    // い形容词规则说明（对应 JS 的 I_RULE_TEXT）。"]);
push(["    internal val I_RULE_TEXT: Map<String, String> ="]);
push(ktStringMap(engine.I_RULE_TEXT, 8));
push([""]);

push(["    // な形容词规则说明（对应 JS 的 NA_RULE_TEXT）。"]);
push(["    internal val NA_RULE_TEXT: Map<String, String> ="]);
push(ktStringMap(engine.NA_RULE_TEXT, 8));
push([""]);

push(["}"]);

const content = L.join("\n") + "\n";

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, content, "utf8");

process.stdout.write(
  "wrote " +
    path.relative(process.cwd(), OUT).replace(/\\/g, "/") +
    " (" +
    engine.ADJECTIVES.length +
    " adjectives, " +
    engine.FORMS.length +
    " forms, " +
    content.length +
    " chars)\n",
);
