// 视觉回归：对项目目录截取 6 个关键 UI 状态，供改动前后对比。
//
// 用法:
//   node tools/visual-check.js --dir <项目目录> --out <输出目录>
//
// 说明:
// - 通过 addInitScript 把 Math.random 固定为 0.5，保证两次运行随机出题一致；
// - 等待 document.fonts.ready，避免 font-display:swap 抓到回退字体帧；
// - 反馈态通过读取题面并用本目录的 verb-engine 计算正确答案后真实提交得到。
//
// 依赖: playwright（仅开发时需要），浏览器用本机已安装的 chromium headless shell。

"use strict";

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const EXECUTABLE =
  "C:/Users/xiao_/AppData/Local/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-win64/chrome-headless-shell.exe";

function parseArgs(argv) {
  const args = {};
  for (let i = 2; i < argv.length; i += 2) {
    args[argv[i].replace(/^--/, "")] = argv[i + 1];
  }
  return args;
}

async function settle(page) {
  // 等页面结构、字体与题目文本全部就绪，避免抓到 mid-render 帧
  await page.waitForFunction(() => document.readyState === "complete");
  await page.waitForFunction(() => {
    const word = document.getElementById("question-word");
    return word && word.textContent.length > 0;
  });
  await page.evaluate(() => document.fonts.ready);
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))
  );
  await page.waitForTimeout(600);
}

async function readQuestion(page) {
  return page.evaluate(() => ({
    word: document.getElementById("question-word").textContent,
    target: document.getElementById("target-pill").textContent
  }));
}

async function shoot(page, outDir, name, options) {
  // 归零滚动：提交后 nextQuestion.focus() 会滚动到按钮，若不归零两次运行会错位。
  // keepScroll 为 true 时保留当前位置（用于拍摄页内锚点区域）。
  const keepScroll = Boolean(options && options.keepScroll);
  if (!keepScroll) {
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.waitForTimeout(150);
  }
  const file = path.join(outDir, name + ".png");
  await page.screenshot({ path: file, fullPage: false });
  console.log("  " + name);
}

async function main() {
  const args = parseArgs(process.argv);
  const dir = path.resolve(args.dir);
  const outDir = path.resolve(args.out);
  fs.mkdirSync(outDir, { recursive: true });

  const engine = require(path.join(dir, "verb-engine.js"));
  const adjectiveEngine = require(path.join(dir, "adjective-engine.js"));
  const verbByDictionary = new Map(engine.VERBS.map((v) => [v.dictionary, v]));
  const labelToVoice = new Map(engine.VOICES.map((v) => [v.label, v.id]));
  const labelToInflection = new Map(engine.INFLECTIONS.map((f) => [f.label, f.id]));
  const adjectiveByWord = new Map(adjectiveEngine.ADJECTIVES.map((a) => [a.word, a]));
  const labelToForm = new Map(adjectiveEngine.FORMS.map((f) => [f.label, f.id]));

  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
    const page = await context.newPage();
    await page.addInitScript(() => {
      Math.random = () => 0.5;
    });

    const url = "file:///" + dir.replace(/\\/g, "/") + "/index.html";
    await page.goto(url);
    await settle(page);
    await shoot(page, outDir, "01-practice-initial");

    // 反馈态（答对）：读题面 -> 计算正确答案 -> 真实提交
    const question = await readQuestion(page);
    const parts = question.target.split(" · ");
    const voiceId = parts.length > 1 ? labelToVoice.get(parts[0]) : "base";
    const inflectionId = labelToInflection.get(parts[parts.length - 1]);
    const verb = verbByDictionary.get(question.word);
    if (!verb || !inflectionId) {
      throw new Error("无法解析题面: " + JSON.stringify(question));
    }
    const expected = engine.conjugate(verb, voiceId, inflectionId).kana;
    await page.fill("#answer-input", expected);
    await page.click("#submit-answer");
    await page.waitForTimeout(450);
    await shoot(page, outDir, "02-practice-correct");

    // 反馈态（答错）-> 错题视图（含卡片）
    await page.reload();
    await settle(page);
    await page.fill("#answer-input", "まちがえた");
    await page.click("#submit-answer");
    await page.waitForTimeout(450);
    await shoot(page, outDir, "03-practice-wrong");
    await page.click('[data-view="mistakes"]');
    await page.waitForTimeout(350);
    await shoot(page, outDir, "04-mistakes-card");

    // 错题重练模式：横幅 + 退出入口
    await page.click("#review-mistakes");
    await page.waitForTimeout(450);
    await shoot(page, outDir, "07-mistake-review");

    // 速查视图
    await page.click('[data-view="reference"]');
    await page.waitForTimeout(350);
    await shoot(page, outDir, "05-reference");

    // 形容词练习视图（初始）
    await page.click('[data-view="adjective"]');
    await page.waitForTimeout(400);
    await shoot(page, outDir, "08-adjective-practice");

    // 形容词答对态：读题面 -> 计算正确答案 -> 真实提交
    const adjQuestion = await page.evaluate(() => ({
      word: document.getElementById("adj-question-word").textContent,
      target: document.getElementById("adj-target-pill").textContent
    }));
    const adjective = adjectiveByWord.get(adjQuestion.word);
    // labelToForm 映射为 label -> formId（与 verb 侧 labelToInflection 同构）
    const formId = labelToForm.get(adjQuestion.target);
    if (!adjective || !formId) {
      throw new Error("无法解析形容词题面: " + JSON.stringify({
        question: adjQuestion,
        hasAdjective: Boolean(adjective),
        formId: formId === undefined ? null : formId
      }));
    }
    const adjExpected = adjectiveEngine.conjugate(adjective, formId).kana;
    await page.fill("#adj-answer-input", adjExpected);
    await page.click("#adj-submit-answer");
    await page.waitForTimeout(450);
    await shoot(page, outDir, "09-adjective-correct");

    // 形容词速查资料区（滚动到总表；保留滚动位置）
    await page.evaluate(() => {
      document.getElementById("adjective-guide").scrollIntoView();
    });
    await page.waitForTimeout(300);
    await shoot(page, outDir, "10-adjective-guide", { keepScroll: true });

    // 移动端练习视图
    const mobile = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const mobilePage = await mobile.newPage();
    await mobilePage.addInitScript(() => {
      Math.random = () => 0.5;
    });
    await mobilePage.goto(url);
    await settle(mobilePage);
    await shoot(mobilePage, outDir, "06-mobile-practice");
    await mobile.close();

    console.log("完成 -> " + outDir);
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
