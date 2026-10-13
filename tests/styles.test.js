"use strict";

// 样式契约守卫：把「令牌必须先定义」「组件内禁止硬编码色值」两条项目红线
// 变成可执行断言。此前令牌块被误插到 :root 之外导致 59 个变量失效、整站
// 配色退化，本测试即为此类事故的静态护栏（无需浏览器）。

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

const CSS_PATH = path.join(__dirname, "..", "styles.css");
const css = fs.readFileSync(CSS_PATH, "utf8");

function extractRootBlock() {
  const match = /:root\s*\{([\s\S]*?)\}/.exec(css);
  assert.ok(match, "styles.css 必须包含 :root 块");
  return match[1];
}

function rootBlockEnd() {
  return css.indexOf("}", css.indexOf(":root"));
}

test("所有 var(--x) 引用都在 :root 中定义", function () {
  const root = extractRootBlock();
  const defined = new Set(Array.from(root.matchAll(/(--[a-z0-9-]+)\s*:/g), function (m) {
    return m[1];
  }));
  const used = new Set(Array.from(css.matchAll(/var\((--[a-z0-9-]+)\)/g), function (m) {
    return m[1];
  }));
  const undefinedRefs = Array.from(used).filter(function (name) {
    return !defined.has(name);
  });
  assert.deepEqual(undefinedRefs, [], "存在未定义的变量引用: " + undefinedRefs.join(", "));
  assert.ok(used.size > 0, "应至少有一个变量引用");
});

test("组件样式内不出现硬编码色值（#fff/#000 例外）", function () {
  const body = css.slice(rootBlockEnd() + 1);
  const offenders = [];
  body.split("\n").forEach(function (line, index) {
    const trimmed = line.trim();
    if (trimmed.startsWith("/*") || trimmed.startsWith("*")) {
      return;
    }
    for (const match of line.matchAll(/#[0-9a-fA-F]{3,8}\b/g)) {
      const value = match[0].toLowerCase();
      if (value !== "#fff" && value !== "#000") {
        offenders.push("第 " + (index + 1) + " 行附近: " + value);
      }
    }
  });
  assert.deepEqual(offenders, [], "组件内出现硬编码色值:\n" + offenders.join("\n"));
});

test("答题结果扫过整张统计卡片且不阻挡操作", function () {
  const sweepLayer = /\.practice-stats::after\s*\{([^}]*)\}/.exec(css);
  assert.ok(sweepLayer, "统计卡片应有独立扫光层");
  assert.match(sweepLayer[1], /inset:\s*0;/, "扫光层应覆盖整个统计卡片");
  assert.match(sweepLayer[1], /pointer-events:\s*none;/, "扫光层不应拦截卡片内交互");
  assert.match(css, /\.practice-stats\.is-answer-correct::after/);
  assert.match(css, /\.practice-stats\.is-answer-incorrect::after/);
  assert.match(css, /@keyframes\s+practice-stats-sweep/);
  assert.match(
    css,
    /@media \(prefers-reduced-motion: reduce\)[\s\S]*?\.practice-stats\.is-answer-correct::after[\s\S]*?animation:\s*none;/
  );
});

test(":root 令牌按语义分组并有注释", function () {
  const root = extractRootBlock();
  assert.match(root, /\/\* .+ \*\//, ":root 中应有分组注释");
  const customProps = Array.from(root.matchAll(/^\s*(--[a-z0-9-]+):/gm), function (m) {
    return m[1];
  });
  assert.ok(customProps.length >= 12, "基础调色板令牌不应少于 12 个");
});
