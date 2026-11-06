"use strict";

// 样式契约守卫：把「令牌必须先定义」「组件内禁止硬编码色值」两条项目红线
// 变成可执行断言。此前令牌块被误插到 :root 之外导致 59 个变量失效、整站
// 配色退化，本测试即为此类事故的静态护栏（无需浏览器）。
// B8 起深浅色双主题：@media 内的 :root 覆盖块同样是令牌定义区，不计组件层；
// 组件层硬编码色拦截同时覆盖 hex 与 rgb/rgba/hsl/hsla（rgba 此前漏检过）。

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

// 所有 :root 令牌块（含 @media 深浅色覆盖块）；令牌块内无嵌套花括号。
function allRootBlocks() {
  return Array.from(css.matchAll(/:root\s*\{([^}]*)\}/g), function (m) {
    return m[1];
  });
}

// 把所有 :root 令牌块的内容挖空但保留换行，组件层扫描用（行号不漂移）。
function cssWithoutRootBlocks() {
  return css.replace(/:root\s*\{[^}]*\}/g, function (block) {
    return block.replace(/[^\n]/g, " ");
  });
}

test("所有 var(--x) 引用都在 :root 中定义", function () {
  const defined = new Set();
  allRootBlocks().forEach(function (root) {
    for (const m of root.matchAll(/(--[a-z0-9-]+)\s*:/g)) {
      defined.add(m[1]);
    }
  });
  const used = new Set(Array.from(css.matchAll(/var\((--[a-z0-9-]+)\)/g), function (m) {
    return m[1];
  }));
  const undefinedRefs = Array.from(used).filter(function (name) {
    return !defined.has(name);
  });
  assert.deepEqual(undefinedRefs, [], "存在未定义的变量引用: " + undefinedRefs.join(", "));
  assert.ok(used.size > 0, "应至少有一个变量引用");
});

test("组件样式内不出现硬编码色值（含 #fff/#000）", function () {
  const body = cssWithoutRootBlocks();
  const offenders = [];
  body.split("\n").forEach(function (line, index) {
    const trimmed = line.trim();
    if (trimmed.startsWith("/*") || trimmed.startsWith("*")) {
      return;
    }
    for (const match of line.matchAll(/#[0-9a-fA-F]{3,8}\b/g)) {
      offenders.push("第 " + (index + 1) + " 行附近: " + match[0]);
    }
    for (const match of line.matchAll(/\b(rgb|rgba|hsl|hsla)\([^)]*\)/g)) {
      offenders.push("第 " + (index + 1) + " 行附近: " + match[0]);
    }
  });
  assert.deepEqual(offenders, [], "组件内出现硬编码色值:\n" + offenders.join("\n"));
});

test("深浅色双主题：@media 覆盖块存在且换核心令牌", function () {
  const dark = /@media\s*\(prefers-color-scheme:\s*dark\)\s*\{[\s\S]*?:root\s*\{([^}]*)\}/.exec(css);
  assert.ok(dark, "应有 @media (prefers-color-scheme: dark) 令牌覆盖块");
  ["--paper", "--surface", "--ink", "--line", "--green", "--shadow", "--pos-n"].forEach(function (name) {
    assert.match(dark[1], new RegExp(name + "\\s*:"), "深色覆盖块缺少 " + name);
  });
  assert.match(dark[1], /color-scheme:\s*dark/, "深色覆盖块应声明 color-scheme: dark");
  assert.match(extractRootBlock(), /color-scheme:\s*light/);
});

test("题干、规则与形容词词尾统一使用关键词高亮样式", function () {
  assert.match(css, /\.keyword-highlight,\s*\.adj-tail-i,\s*\.adj-tail-na\s*\{/);
  assert.match(css, /\.keyword-highlight,\s*\.adj-tail-i,\s*\.adj-tail-na\s*\{[^}]*border-radius:\s*999px;/);
  assert.doesNotMatch(css, /\.question-instruction strong,\s*\.rule-emphasis\s*\{/);
});

test("根视口固定且由主内容内部滚动，窄屏顶部栏固定", function () {
  assert.match(css, /html,\s*body\s*\{\s*height:\s*100vh;\s*height:\s*100dvh;\s*overflow:\s*hidden;\s*overflow:\s*clip;/);
  assert.match(css, /\.app-shell\s*\{[^}]*width:\s*100%;[^}]*min-height:\s*100vh;/);
  assert.match(css, /\.app-shell\s*\{[^}]*position:\s*fixed;[^}]*inset:\s*0;[^}]*height:\s*100vh;[^}]*height:\s*100dvh;[^}]*min-height:\s*0;[^}]*overflow:\s*hidden;\s*overflow:\s*clip;/);
  assert.match(css, /\.app-sidebar\s*\{\s*position:\s*relative;\s*top:\s*auto;\s*height:\s*100%;[\s\S]*?overflow-y:\s*auto;/);
  assert.match(css, /\.app-main\s*\{\s*height:\s*100vh;\s*height:\s*100dvh;[\s\S]*?overflow-y:\s*auto;/);
  assert.match(css, /\.app-main\s*\{[^}]*overflow-y:\s*auto;[^}]*padding-bottom:\s*calc\(16px \+ env\(safe-area-inset-bottom,\s*0px\)\);/);
  assert.match(
    css,
    /@media \(max-width: 840px\)[\s\S]*?\.mobile-topbar\s*\{\s*position:\s*sticky;\s*top:\s*0;/
  );
});

test("助词专题卡在窄屏下改为单列", function () {
  const baseRule = css.indexOf(".particle-lesson-grid {");
  const responsiveRule = /@media \(max-width: 840px\)\s*\{\s*\.particle-lesson-grid\s*\{\s*grid-template-columns:\s*minmax\(0,\s*1fr\);/.exec(css);
  assert.ok(baseRule >= 0, "助词专题应使用网格布局");
  assert.ok(responsiveRule, "应为窄屏定义助词专题单列布局");
  assert.ok(responsiveRule.index > baseRule, "窄屏规则必须覆盖后声明的双列基础布局");
});

test("助词作用判断选项适配长文本和窄屏", function () {
  assert.match(
    css,
    /\.particle-options\.is-function-question\s*\{\s*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\);/
  );
  assert.match(css, /\.particle-options\.is-function-question \.particle-option\s*\{[^}]*text-align:\s*left;/);
  assert.match(
    css,
    /\.particle-options\.is-function-question\s*\{\s*grid-template-columns:\s*minmax\(0,\s*1fr\);/
  );
});

test("三个模块共享页签、说明卡和练习控制面板", function () {
  assert.match(css, /\.view > \.module-subnav\s*\{[^}]*margin-bottom:\s*18px;/);
  assert.match(css, /\.module-subtab\.is-active\s*\{[^}]*background:\s*var\(--green-pale\);/);
  assert.match(
    css,
    /\.practice-card,\s*\.learning-card,\s*\.practice-stats,\s*\.section-heading,\s*\.reference-summary,\s*\.mistake-card\s*\{[^}]*border:\s*1px solid/
  );
  assert.match(css, /\.filter-bar\s*\{[^}]*padding:\s*13px;/);
  assert.match(css, /\.particle-controls-panel\s*\{[^}]*grid-template-columns:/);
  assert.doesNotMatch(css, /\.subtabs\s*\{/);
});

test("形容词说明卡不额外叠加页签上方留白", function () {
  assert.doesNotMatch(css, /#view-adjective\s*\{[^}]*gap:\s*22px;/);
  assert.doesNotMatch(css, /#view-adjective > \.adj-guide-heading\s*\{[^}]*margin-top:/);
  assert.match(css, /\.view > \.module-subnav\s*\{[^}]*margin-bottom:\s*18px;/);
});

test("桌面布局不通过缩放 body 改变动态视口尺寸", function () {
  assert.doesNotMatch(css, /body\s*\{\s*zoom\s*:/);
});

test("内嵌答题统计不单独显示悬停特效", function () {
  assert.doesNotMatch(css, /\.practice-stats:hover/);
});

test("桌面侧栏折叠后用浅色横线分隔复习区", function () {
  const separator = /@media \(min-width: 841px\)\s*\{\s*\.app-shell\.is-sidebar-collapsed \.sidebar-section-review\s*\{([^}]*)\}/.exec(css);
  assert.ok(separator, "桌面折叠侧栏应有复习区分隔线");
  assert.match(separator[1], /width:\s*24px;/);
  assert.match(separator[1], /height:\s*1px;/);
  assert.match(separator[1], /background:\s*var\(--line-faint\);/);
});

test("答题结果扫过整张练习卡且不阻挡操作", function () {
  const sweepLayer = /\.practice-card::after\s*\{([^}]*)\}/.exec(css);
  assert.ok(sweepLayer, "练习卡应有独立扫光层");
  assert.match(sweepLayer[1], /inset:\s*0;/, "扫光层应覆盖整张练习卡");
  assert.match(sweepLayer[1], /pointer-events:\s*none;/, "扫光层不应拦截卡片内交互");
  assert.match(css, /\.practice-card\.is-answer-correct::after/);
  assert.match(css, /\.practice-card\.is-answer-incorrect::after/);
  assert.match(css, /@keyframes\s+practice-card-sweep/);
  assert.equal((css.match(/animation:\s*practice-card-sweep 1200ms ease-out;/g) || []).length, 2);
  assert.match(css, /--stat-correct-sweep:\s*rgba\(102,\s*135,\s*109,\s*0\.42\)/);
  assert.match(css, /--stat-incorrect-sweep:\s*rgba\(201,\s*127,\s*116,\s*0\.42\)/);
  assert.match(
    css,
    /@media \(prefers-reduced-motion: reduce\)[\s\S]*?\.practice-card\.is-answer-correct::after,[\s\S]*?\.practice-card\.is-answer-incorrect::after\s*\{\s*animation-duration:\s*1200ms !important;/
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

test("文章阅读的 9 组 pos 底色令牌都在 :root 定义", function () {
  const root = extractRootBlock();
  ["n", "v", "adj", "adjv", "adv", "misc", "p", "aux", "none"].forEach(function (group) {
    assert.match(root, new RegExp("--pos-" + group + "\\s*:"), "缺少 --pos-" + group);
  });
});

test("逐词按 pos 分组取底色，正文与输入区固定行高", function () {
  assert.match(css, /\.read-token\[data-pos-group="n"\]\s*\{\s*background:\s*var\(--pos-n\);\s*\}/);
  assert.match(css, /\.read-token\[data-pos-group="aux"\]\s*\{\s*background:\s*var\(--pos-aux\);\s*\}/);
  assert.match(css, /\.read-paragraph-text\s*\{[^}]*line-height:\s*30px;/);
  assert.match(css, /\.read-textarea\s*\{[^}]*line-height:\s*30px;/);
  assert.match(css, /\.read-paragraph-tokens\s*\{[^}]*flex-wrap:\s*wrap;/);
});
