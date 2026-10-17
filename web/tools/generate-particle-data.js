"use strict";

// 从 web/particle-engine.js 生成 Android 端 Kotlin 数据文件（D-019 原则：数据同源）。
// 用法：
//   node web/tools/generate-particle-data.js
// 输出：
//   android/app/src/main/java/com/nihongodoushi/app/engine/particle/ParticleData.kt
//
// 生成后由 ParticleCrossSampleTest 与 JS 样例比对，防止两端漂移；不手工维护 Kotlin 数据。

const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");
const engine = require(path.join(ROOT, "particle-engine.js"));

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
  "particle",
  "ParticleData.kt",
);

// 跨端样例单独成文件：既有 cross-samples.json 的 verb/adjective 基线与当前 JS 存在历史差异
// （Web 形容词 10 形式 / Android 7 形式），混入同一文件会让基线重生成时连带改变既有断言。
const SAMPLES_OUT = path.join(
  ROOT,
  "..",
  "android",
  "app",
  "src",
  "test",
  "resources",
  "particle-samples.json",
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

function ktNullable(value) {
  return value === undefined || value === null || value === "" ? "null" : kt(value);
}

function indent(lines, level) {
  const pad = "    ".repeat(level);
  return lines.map(function (line) {
    return line === "" ? "" : pad + line;
  });
}

function exampleBlock(example) {
  const lines = [
    "ParticleExample(",
    "    context = " + kt(example.context) + ",",
    "    sentence = " + kt(example.sentence) + ",",
    "    reading = " + kt(example.reading) + ",",
    "    translation = " + kt(example.translation) + ",",
    "    note = " + kt(example.note) + ",",
  ];
  if (example.contrastGroup) {
    lines.push("    contrastGroup = " + kt(example.contrastGroup) + ",");
  }
  if (example.contrastNote) {
    lines.push("    contrastNote = " + kt(example.contrastNote) + ",");
  }
  lines.push("),");
  return lines;
}

function lessonBlock(lesson) {
  const lines = [
    "ParticleLesson(",
    "    id = " + kt(lesson.id) + ",",
    "    title = " + kt(lesson.title) + ",",
    "    particles = listOf(" + lesson.particles.map(kt).join(", ") + "),",
    "    beginnerSummary = " + kt(lesson.beginnerSummary) + ",",
    "    advancedSummary = " + kt(lesson.advancedSummary) + ",",
    "    examples = listOf(",
  ];
  lesson.examples.forEach(function (example) {
    lines.push.apply(lines, indent(exampleBlock(example), 2));
  });
  lines.push("    ),");
  lines.push("),");
  return lines;
}

function questionBlock(question) {
  const lines = [
    "ParticleQuestion(",
    "    id = " + kt(question.id) + ",",
    "    type = " + kt(question.type) + ",",
    "    lessonId = " + kt(question.lessonId) + ",",
    "    level = " + kt(question.level) + ",",
    "    context = " + kt(question.context) + ",",
    "    before = " + ktNullable(question.before) + ",",
    "    after = " + ktNullable(question.after) + ",",
    "    readingBefore = " + ktNullable(question.readingBefore) + ",",
    "    readingAfter = " + ktNullable(question.readingAfter) + ",",
    "    translation = " + kt(question.translation) + ",",
    "    targetParticle = " + ktNullable(question.targetParticle) + ",",
    "    targetReading = " + ktNullable(question.targetReading) + ",",
    "    options = listOf(" + question.options.map(kt).join(", ") + "),",
    "    acceptedAnswers = listOf(" + question.acceptedAnswers.map(kt).join(", ") + "),",
    "    beginnerExplanation = " + kt(question.beginnerExplanation) + ",",
    "    advancedExplanation = " + kt(question.advancedExplanation) + ",",
  ];
  if (question.optionNotes && Object.keys(question.optionNotes).length) {
    lines.push("    optionNotes = mapOf(");
    Object.keys(question.optionNotes).forEach(function (key) {
      lines.push("        " + kt(key) + " to " + kt(question.optionNotes[key]) + ",");
    });
    lines.push("    ),");
  }
  lines.push("),");
  return lines;
}

const header = [
  "package com.nihongodoushi.app.engine.particle",
  "",
  "/**",
  " * 助词模块数据（自动生成，勿手改）。",
  " *",
  " * 生成器：web/tools/generate-particle-data.js",
  " * 源：web/particle-engine.js（只读参考源）",
  " * 规模：" + engine.LESSONS.length + " 个专题 / " + engine.QUESTIONS.length + " 道题",
  " *",
  " * 纯数据模块：无 Android/androidx 依赖。改内容请改 JS 源并重跑生成器。",
  " */",
  "",
  "/** 学习层级。仅决定解析深度，不做难度分层（D-018）。 */",
  "object ParticleLevels {",
  "    const val BEGINNER = \"beginner\"",
  "    const val ADVANCED = \"advanced\"",
  "}",
  "",
  "/** 题型。 */",
  "object ParticleQuestionTypes {",
  "    const val FILL = \"fill\"",
  "    const val FUNCTION = \"function\"",
  "}",
  "",
  "/** 专题例句（对应 JS 的 examples 条目）。 */",
  "data class ParticleExample(",
  "    val context: String,",
  "    val sentence: String,",
  "    val reading: String,",
  "    val translation: String,",
  "    val note: String,",
  "    /** D-019 补充字段：最小对立对的分组标识；未补时为 null */",
  "    val contrastGroup: String? = null,",
  "    /** D-019 补充字段：换掉助词后意思如何变化的一句话说明 */",
  "    val contrastNote: String? = null,",
  ")",
  "",
  "/** 助词专题（对应 JS 的 LESSONS 条目）。 */",
  "data class ParticleLesson(",
  "    val id: String,",
  "    val title: String,",
  "    val particles: List<String>,",
  "    val beginnerSummary: String,",
  "    val advancedSummary: String,",
  "    val examples: List<ParticleExample>,",
  ")",
  "",
  "/** 助词题目（对应 JS 的 QUESTIONS 条目）。 */",
  "data class ParticleQuestion(",
  "    val id: String,",
  "    val type: String,",
  "    val lessonId: String,",
  "    /** beginner / advanced，仅决定解析深度 */",
  "    val level: String,",
  "    val context: String,",
  "    val before: String?,",
  "    val after: String?,",
  "    val readingBefore: String?,",
  "    val readingAfter: String?,",
  "    val translation: String,",
  "    /** 判作用题的目标助词；选助词题为 null */",
  "    val targetParticle: String?,",
  "    val targetReading: String?,",
  "    val options: List<String>,",
  "    /** 可以被判为正确的选项文本（含一题多答案的情形） */",
  "    val acceptedAnswers: List<String>,",
  "    val beginnerExplanation: String,",
  "    val advancedExplanation: String,",
  "    /** D-019 补充字段：选项文本 → 该选项为何不适用的说明；未补时为空 */",
  "    val optionNotes: Map<String, String> = emptyMap(),",
  ")",
  "",
  "object ParticleData {",
  "    val LESSONS: List<ParticleLesson> = listOf(",
];

const lessonLines = [];
engine.LESSONS.forEach(function (lesson) {
  lessonLines.push.apply(lessonLines, indent(lessonBlock(lesson), 2));
});

const middle = [
  "    )",
  "",
  "    val QUESTIONS: List<ParticleQuestion> = listOf(",
];

const questionLines = [];
engine.QUESTIONS.forEach(function (question) {
  questionLines.push.apply(questionLines, indent(questionBlock(question), 2));
});

const footer = [
  "    )",
  "",
  "    val LESSON_BY_ID: Map<String, ParticleLesson> = LESSONS.associateBy { it.id }",
  "    val QUESTION_BY_ID: Map<String, ParticleQuestion> = QUESTIONS.associateBy { it.id }",
  "",
  "    const val TOTAL_QUESTIONS: Int = " + engine.QUESTIONS.length,
  "}",
  "",
];

const content = header
  .concat(lessonLines)
  .concat(middle)
  .concat(questionLines)
  .concat(footer)
  .join("\n");

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, content, "utf8");

// ---------- 跨端样例（供 ParticleCrossSampleTest 逐字段比对） ----------

function buildQuestionSamples() {
  return engine.QUESTIONS.map(function (question) {
    const accepted = new Set(question.acceptedAnswers);
    const correctIndexes = [];
    question.options.forEach(function (option, index) {
      if (accepted.has(option)) {
        correctIndexes.push(index);
      }
    });
    const uniqueAnswers = question.acceptedAnswers.filter(function (value, index, all) {
      return all.indexOf(value) === index;
    });
    return {
      id: question.id,
      type: question.type,
      lessonId: question.lessonId,
      level: question.level,
      context: question.context,
      before: question.before || null,
      after: question.after || null,
      readingBefore: question.readingBefore || null,
      readingAfter: question.readingAfter || null,
      translation: question.translation,
      targetParticle: question.targetParticle || null,
      targetReading: question.targetReading || null,
      options: question.options,
      acceptedAnswers: question.acceptedAnswers,
      correctIndexes: correctIndexes,
      answerLabel: uniqueAnswers.join("／"),
      beginnerExplanation: question.beginnerExplanation,
      advancedExplanation: question.advancedExplanation,
      optionNotes: question.optionNotes || {},
    };
  });
}

function buildLessonSamples() {
  return engine.LESSONS.map(function (lesson) {
    return {
      id: lesson.id,
      title: lesson.title,
      particles: lesson.particles,
      beginnerSummary: lesson.beginnerSummary,
      advancedSummary: lesson.advancedSummary,
      examples: lesson.examples.map(function (example) {
        return {
          sentence: example.sentence,
          contrastGroup: example.contrastGroup || null,
          contrastNote: example.contrastNote || null,
        };
      }),
    };
  });
}

const samplePayload = {
  generatedAt: new Date().toISOString(),
  lessons: buildLessonSamples(),
  questions: buildQuestionSamples(),
};

fs.mkdirSync(path.dirname(SAMPLES_OUT), { recursive: true });
fs.writeFileSync(SAMPLES_OUT, JSON.stringify(samplePayload), "utf8");

process.stdout.write(
  "wrote " +
    path.relative(process.cwd(), OUT).replace(/\\/g, "/") +
    " (" +
    engine.LESSONS.length +
    " lessons, " +
    engine.QUESTIONS.length +
    " questions, " +
    content.length +
    " chars)\n" +
    "wrote " +
    path.relative(process.cwd(), SAMPLES_OUT).replace(/\\/g, "/") +
    " (" +
    samplePayload.questions.length +
    " question samples, " +
    samplePayload.lessons.length +
    " lesson samples)\n",
);

