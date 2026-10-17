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

// 页面与脚本中的可见文本（含 toast / aria-label / CSS content）
for (const file of ["index.html", "app.js", "adjective-engine.js", "particle-engine.js", "styles.css"]) {
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
