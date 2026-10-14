// 收集页面实际渲染到的全部字符，供字体子集化使用。
// 由 font/subset.py 调用；也可单独运行查看字符集：
//   node font/collect-chars.js
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
for (const file of ["index.html", "app.js", "adjective-engine.js", "styles.css"]) {
  const text = fs.readFileSync(path.join(__dirname, "..", file), "utf8");
  for (const char of text) {
    chars.add(char);
  }
}

process.stdout.write(Array.from(chars).join(""));
