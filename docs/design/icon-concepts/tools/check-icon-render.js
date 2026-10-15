/**
 * 图标渲染自检：把生成的四层 SVG 内联进页面，在真实 Chromium 里渲染并截图。
 * 用途：确认「あ」轮廓提取正确（不是乱码路径）、各遮罩与各尺寸下均可辨认。
 *
 * 用法：NODE_PATH=<node 工作区>/node_modules <node> docs/design/icon-concepts/tools/check-icon-render.js
 * 产物：C:/Users/xiao_/AppData/Local/Temp/icon-render-check.png
 */

"use strict";

const fs = require("node:fs");
const path = require("node:path");

const ROOT = path.resolve(__dirname, "..", "..", "..", "..");
const ICON_DIR = path.join(ROOT, "docs", "design", "icon-concepts");
const OUT_PNG = process.argv[2] || "C:/Users/xiao_/AppData/Local/Temp/icon-render-check.png";
const EXECUTABLE =
  process.env.LOCALAPPDATA +
  "/ms-playwright/chromium_headless_shell-1228/chrome-headless-shell-win64/chrome-headless-shell.exe";

function read(name) {
  const svg = fs.readFileSync(path.join(ICON_DIR, name), "utf8");
  return svg.replace(/<\?xml[^>]*\?>\s*/, "").replace(/width="108" height="108"/, 'width="100%" height="100%"');
}

const preview = read("icon-a-ai-paper.svg");
const foreground = read("icon-a-foreground.svg");
const background = read("icon-a-background.svg");
const monochrome = read("icon-a-monochrome.svg");

const maskCell = (inner, cls, label) => `
  <div class="cell">
    <div class="mask ${cls}">${inner}</div>
    <div class="cap">${label}</div>
  </div>`;

const sizeCell = (size, cls, label) => `
  <div class="cell">
    <div class="mask ${cls}" style="width:${size}px;height:${size}px">${preview}</div>
    <div class="cap">${label}</div>
  </div>`;

const html = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><style>
  body { margin:0; padding:28px 32px; background:#e9ebe4;
         font-family:"Microsoft YaHei",sans-serif; color:#26332e; }
  h2 { font-size:15px; margin:0 0 12px; font-weight:600; }
  .row { display:flex; gap:22px; align-items:flex-end; margin-bottom:30px; }
  .cell { text-align:center; }
  .mask { overflow:hidden; background:#fff; box-shadow:0 4px 14px rgba(43,55,47,.14); }
  .mask svg { display:block; }
  .mask.circle { border-radius:50%; }
  .mask.rounded { border-radius:26%; }
  .mask.squircle { border-radius:34%; }
  .mask.full { border-radius:8px; }
  .cap { font-size:11px; color:#657268; margin-top:7px; }
  .split { display:flex; }
  .split > * { width:50%; height:100%; overflow:hidden; }
</style></head><body>
  <h2>预览层 · 四种遮罩（各 108px）</h2>
  <div class="row">
    ${maskCell(preview, "circle", "圆形")}
    ${maskCell(preview, "rounded", "圆角方")}
    ${maskCell(preview, "squircle", "squircle")}
    ${maskCell(preview, "full", "方形（无遮罩）")}
  </div>

  <h2>实际尺寸档位（圆形遮罩）</h2>
  <div class="row" style="align-items:center">
    ${sizeCell(108, "circle", "108dp 商店")}
    ${sizeCell(72, "circle", "72dp 桌面大")}
    ${sizeCell(48, "circle", "48dp 桌面")}
    ${sizeCell(36, "circle", "36dp 通知")}
    ${sizeCell(24, "circle", "24dp 最小")}
  </div>

  <h2>自适应图标分层（前景 + 背景）与单色主题</h2>
  <div class="row">
    <div class="cell">
      <div class="mask circle" style="width:108px;height:108px;position:relative;overflow:hidden">
        <div style="position:absolute;width:108px;height:108px;top:-18px;left:-18px">
          <div style="position:absolute;inset:0">${background}</div>
          <div style="position:absolute;inset:0">${foreground}</div>
        </div>
      </div>
      <div class="cap">系统裁切实况（只显示中央 72dp 窗口）</div>
    </div>
    <div class="cell">
      <div class="mask circle" style="width:108px;height:108px;background:#26332e;color:#fff">${monochrome}</div>
      <div class="cap">单色层（系统深色着色）</div>
    </div>
    <div class="cell">
      <div class="mask circle" style="width:108px;height:108px;background:#fffefa;color:#326151">${monochrome}</div>
      <div class="cap">单色层（系统浅色着色）</div>
    </div>
  </div>
</body></html>`;

const { chromium } = require("playwright");

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const page = await browser.newPage({ viewport: { width: 900, height: 640 }, deviceScaleFactor: 2 });
  await page.setContent(html, { waitUntil: "load" });
  await page.screenshot({ path: OUT_PNG, fullPage: true });
  await browser.close();
  console.log("screenshot:", OUT_PNG);
})();
