"use strict";

// read-ocr 单测（B8-d）：压缩管线的可注入假环境。
// 真实 canvas/Image 只在浏览器冒烟里走；这里钉降级与尺寸计算。

const assert = require("node:assert/strict");
const test = require("node:test");

const ReadOcr = require("../read-ocr.js");

function fakeWin(options) {
  const opts = options || {};
  const calls = { read: 0, draw: [], toDataUrl: 0 };
  const win = {
    FileReader: function () {
      const self = this;
      calls.read += 1;
      this.readAsDataURL = function () {
        self.result = opts.rawDataUrl || "data:image/png;base64,RAW";
        if (opts.readFail) {
          self.onerror && self.onerror();
        } else {
          self.onload && self.onload();
        }
      };
    },
    Image: function () {
      const self = this;
      Object.defineProperty(self, "src", {
        set: function () {
          if (opts.imageFail) {
            self.onerror && self.onerror();
            return;
          }
          self.naturalWidth = opts.width || 3200;
          self.naturalHeight = opts.height || 1600;
          self.onload && self.onload();
        }
      });
    },
    document: {
      createElement: function () {
        return {
          width: 0,
          height: 0,
          getContext: opts.noCanvas ? undefined : function () {
            return {
              drawImage: function (img, x, y, w, h) {
                calls.draw.push({ w: w, h: h });
              }
            };
          },
          toDataURL: function () {
            calls.toDataUrl += 1;
            return "data:image/jpeg;base64,COMPRESSED";
          }
        };
      }
    }
  };
  return { win: win, calls: calls };
}

test("compressImage：长边 1600 等比压缩并转 JPEG", function () {
  const fake = fakeWin({ width: 3200, height: 1600 });
  return ReadOcr.compressImage(fake.win, { name: "a.png" }).then(function (dataUrl) {
    assert.equal(dataUrl, "data:image/jpeg;base64,COMPRESSED");
    assert.equal(fake.calls.draw[0].w, 1600);
    assert.equal(fake.calls.draw[0].h, 800);
    assert.equal(fake.calls.toDataUrl, 1);
  });
});

test("compressImage：小图不放大（scale 上限 1）", function () {
  const fake = fakeWin({ width: 800, height: 600 });
  return ReadOcr.compressImage(fake.win, { name: "b.png" }).then(function (dataUrl) {
    assert.equal(dataUrl, "data:image/jpeg;base64,COMPRESSED");
    assert.equal(fake.calls.draw[0].w, 800);
    assert.equal(fake.calls.draw[0].h, 600);
  });
});

test("compressImage：无 canvas 与解码失败都降级原始 dataURL", function () {
  const noCanvas = fakeWin({ noCanvas: true });
  return ReadOcr.compressImage(noCanvas.win, { name: "c.png" }).then(function (dataUrl) {
    assert.equal(dataUrl, "data:image/png;base64,RAW", "无 canvas 回退原图");
    const broken = fakeWin({ imageFail: true });
    return ReadOcr.compressImage(broken.win, { name: "d.png" });
  }).then(function (dataUrl) {
    assert.equal(dataUrl, "data:image/png;base64,RAW", "解码失败回退原图");
  });
});

test("compressImage：读文件失败直接拒绝", function () {
  const fake = fakeWin({ readFail: true });
  return ReadOcr.compressImage(fake.win, { name: "e.png" }).then(function () {
    assert.fail("应当拒绝");
  }, function (error) {
    assert.equal(error.message, "READ_FAILED");
  });
});

test("常量口径：长边 1600 / JPEG 0.7（计划钉死值）", function () {
  assert.equal(ReadOcr.MAX_EDGE, 1600);
  assert.equal(ReadOcr.JPEG_QUALITY, 0.7);
});
