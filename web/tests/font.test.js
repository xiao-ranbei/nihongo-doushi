"use strict";

// 字体契约守卫
//
// 子集字体只包含 subset-chars.txt 里列出的字形。一旦 UI 新增文案却忘记重跑
// font/subset.py，新字符就会回退到系统字体：字形不统一，行高也不同（实测
// 「难度」二字使筛选栏行高由 12.5px 变成 16.67px，整页下移 5px）。
// 这条测试把「改文案 → 重跑子集」变成必须履行的步骤。

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");
const test = require("node:test");

const ROOT = path.join(__dirname, "..");
const CHARS_FILE = path.join(ROOT, "font", "subset-chars.txt");
const FONTS_JS = path.join(ROOT, "font", "fonts.js");

test("字体子集字符表与当前源码一致", function () {
  assert.ok(fs.existsSync(CHARS_FILE), "缺少 font/subset-chars.txt，请先运行 font/subset.py");

  const collected = execFileSync(process.execPath, [path.join(ROOT, "font", "collect-chars.js")], {
    encoding: "utf8"
  });
  const subsetChars = new Set(fs.readFileSync(CHARS_FILE, "utf8"));

  const missing = Array.from(new Set(collected)).filter(function (char) {
    return !subsetChars.has(char);
  });
  assert.deepEqual(
    missing,
    [],
    "以下字符不在字体子集中，请重跑 font/subset.py：" + missing.join("")
  );
});

test("fonts.js 指向的字体文件确实存在", function () {
  const source = fs.readFileSync(FONTS_JS, "utf8");
  const files = Array.from(source.matchAll(/file:\s*"([^"]+)"/g), function (match) {
    return match[1];
  });
  assert.ok(files.length > 0, "font/fonts.js 应至少声明一个字体文件");
  files.forEach(function (file) {
    const target = path.join(ROOT, "font", file);
    assert.ok(fs.existsSync(target), "字体清单指向了不存在的文件：" + file);
    assert.ok(
      fs.statSync(target).size < 2 * 1024 * 1024,
      "字体文件超过 2MB，可能误提交了全量字体：" + file
    );
  });
});
