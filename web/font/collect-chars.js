// 收集两端实际渲染到的全部字符，供字体子集化使用。
// 由 font/subset.py 调用；也可单独运行查看字符集：
//   node font/collect-chars.js
//
// 覆盖范围（D-029，2026-10-17 扩展）：
// 1. Web：引擎数据（动词/形容词全部变形结果）+ index.html/app.js/styles.css 等页面文本
// 2. Android：app/src/main/java 下所有 Kotlin 文件的「双引号字符串字面量」——
//    Android 的 UI 文案与引擎数据（含谚语）都在字面量里。只取字面量可以排除
//    注释散文，避免字符表被注释撑大。新增任何一端文案后都要重跑 font/subset.py，
//    否则缺字回退系统字体（web 端实测缺字会使整页下移 5px）。
"use strict";

const fs = require("fs");
const path = require("path");
const engine = require(path.join(__dirname, "..", "verb-engine.js"));
const honorificEngine = require(path.join(__dirname, "..", "honorific-engine.js"));
const adjEngine = require(path.join(__dirname, "..", "adjective-engine.js"));

const chars = new Set();

// 引擎数据：动词、读音、释义、语态与活用标签及大意
for (const verb of engine.VERBS) {
  chars.add(verb.dictionary);
  chars.add(verb.reading);
  chars.add(verb.meaning);
}
for (const item of engine.VOICES.concat(engine.INFLECTIONS)) {
  chars.add(item.label);
  chars.add(item.meaning);
}

// 形容词引擎数据：词、读音、释义、形式标签与接续提示
for (const adjective of adjEngine.ADJECTIVES) {
  chars.add(adjective.word);
  chars.add(adjective.kana);
  chars.add(adjective.meaning);
}
for (const form of adjEngine.FORMS) {
  chars.add(form.label);
  chars.add(form.meaning);
}

// 引擎数据派生出的全部变形结果（written 与 kana 两套表示）
for (const verb of engine.VERBS) {
  for (const voice of engine.VOICES) {
    for (const inflection of engine.INFLECTIONS) {
      const result = engine.conjugate(verb, voice.id, inflection.id);
      for (const char of result.written + result.kana) {
        chars.add(char);
      }
    }
  }
}

// 形容词引擎派生出的全部变形结果（含礼貌变体 alts）
for (const adjective of adjEngine.ADJECTIVES) {
  for (const form of adjEngine.FORMS) {
    const result = adjEngine.conjugate(adjective, form.id);
    const texts = [result.written, result.kana]
      .concat((result.alts || []).map(function (alt) { return alt.written + alt.kana; }))
      .join("");
    for (const char of texts) {
      chars.add(char);
    }
  }
}

// 敬语引擎数据：三层定义、场景、特殊敬语动词表、场景例句与二重敬语提示。
// honorific-engine.js 不进下面「页面与脚本」白名单——那里有大量中文注释，
// 只按结构化数据收集，字符表不会被注释散文撑大。
for (const level of honorificEngine.LEVELS) {
  chars.add(level.label + level.topic + level.question + level.pattern + level.meaning);
}
for (const scene of honorificEngine.SCENES) {
  chars.add(scene.label + scene.who + scene.note);
}
for (const subject of honorificEngine.SUBJECTS) {
  chars.add(subject.label);
}
for (const verbId of Object.keys(honorificEngine.SPECIAL_HONORIFIC)) {
  const entry = honorificEngine.SPECIAL_HONORIFIC[verbId];
  for (const levelId of Object.keys(entry)) {
    for (const form of entry[levelId]) {
      chars.add(form.written + form.kana + form.masu + form.masuKana);
    }
  }
}
for (const verbId of Object.keys(honorificEngine.LIMITED_VERBS)) {
  chars.add(honorificEngine.LIMITED_VERBS[verbId]);
}
for (const example of honorificEngine.EXAMPLES) {
  chars.add(example.jp + example.kana + example.zh + example.who);
}
for (const entry of honorificEngine.DOUBLE_PATTERNS) {
  chars.add(entry.phrase + entry.suggestion + entry.reason);
}
// 敬语派生结果：不可用层的说明文字同样是界面文案
for (const verb of engine.VERBS) {
  for (const level of honorificEngine.LEVELS) {
    const forms = honorificEngine.getHonorificForms(verb, level.id);
    if (!forms.available) {
      chars.add(forms.reason);
      continue;
    }
    const all = [forms.primary].concat(forms.alternatives);
    for (const form of all) {
      chars.add(form.written + form.kana + form.masu + form.masuKana);
    }
  }
}

// 页面与脚本中的可见文本（含 toast / aria-label / CSS content）
for (const file of [
  "index.html", "app.js", "adjective-engine.js", "particle-engine.js", "styles.css",
  "read-engine.js", "read-prompts.js", "read-view.js", "read-client.js", "read-stats.js", "read-tts.js",
  "read-ocr.js"
]) {
  const text = fs.readFileSync(path.join(__dirname, "..", file), "utf8");
  for (const char of text) {
    chars.add(char);
  }
}

// Android 客户端：Kotlin 源码中的双引号字符串字面量（D-029）。
// 字面量正则同时容忍 \" 转义与 ${...} 模板（模板里的 ASCII 字符无害）。
// 本仓库 src/main 无三引号原始字符串（已实测），无需处理 """
const androidJavaRoot = path.join(__dirname, "..", "..", "android", "app", "src", "main", "java");

function collectAndroidLiterals(dir) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (error) {
    return; // android 目录不存在时（纯 Web 检出）静默跳过
  }
  for (const entry of entries) {
    const target = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      collectAndroidLiterals(target);
    } else if (entry.name.endsWith(".kt")) {
      const text = fs.readFileSync(target, "utf8");
      for (const match of text.matchAll(/"((?:[^"\\]|\\.)*)"/g)) {
        for (const char of match[1]) {
          chars.add(char);
        }
      }
    }
  }
}

collectAndroidLiterals(androidJavaRoot);

process.stdout.write(Array.from(chars).join(""));
