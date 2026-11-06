// 图片压缩（B8-d）：canvas 长边 1600 / JPEG 质量 0.7 → dataURL。
// win 全注入（document / Image / FileReader / URL 都取自 win），测试可整体换掉本模块。
// 降级：环境缺 canvas 能力时回退 FileReader 原样 dataURL（体积大但流程不断）。
(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.ReadOcr = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  const MAX_EDGE = 1600;
  const JPEG_QUALITY = 0.7;

  function readAsDataUrl(win, file) {
    return new Promise(function (resolve, reject) {
      if (!win.FileReader) {
        reject(new Error("NO_FILE_READER"));
        return;
      }
      const reader = new win.FileReader();
      reader.onload = function () {
        resolve(String(reader.result || ""));
      };
      reader.onerror = function () {
        reject(new Error("READ_FAILED"));
      };
      reader.readAsDataURL(file);
    });
  }

  function loadImage(win, src) {
    return new Promise(function (resolve, reject) {
      if (!win.Image) {
        reject(new Error("NO_IMAGE"));
        return;
      }
      const image = new win.Image();
      image.onload = function () {
        resolve(image);
      };
      image.onerror = function () {
        reject(new Error("IMAGE_DECODE_FAILED"));
      };
      image.src = src;
    });
  }

  // 长边压到 MAX_EDGE 以内；已经是小图则按原尺寸画一遍统一转 JPEG。
  function drawCompressed(win, image) {
    const doc = win.document;
    const canvas = doc && typeof doc.createElement === "function" ? doc.createElement("canvas") : null;
    if (!canvas || typeof canvas.getContext !== "function") {
      throw new Error("NO_CANVAS");
    }
    const width = Number(image.naturalWidth || image.width) || 0;
    const height = Number(image.naturalHeight || image.height) || 0;
    if (!width || !height) {
      throw new Error("EMPTY_IMAGE");
    }
    const scale = Math.min(1, MAX_EDGE / Math.max(width, height));
    canvas.width = Math.max(1, Math.round(width * scale));
    canvas.height = Math.max(1, Math.round(height * scale));
    canvas.getContext("2d").drawImage(image, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL("image/jpeg", JPEG_QUALITY);
  }

  const compressImage = function (win, file) {
    return readAsDataUrl(win, file).then(function (rawDataUrl) {
      return loadImage(win, rawDataUrl).then(function (image) {
        try {
          return drawCompressed(win, image);
        } catch (error) {
          // 无 canvas 等硬能力缺口：降级给原始 dataURL，流程不断（体积大可接受）。
          return rawDataUrl;
        }
      }, function () {
        // 解码失败（如非图片文件）：同样降级给原始数据，由服务端报错或转写为空。
        return rawDataUrl;
      });
    });
  };

  return {
    MAX_EDGE: MAX_EDGE,
    JPEG_QUALITY: JPEG_QUALITY,
    readAsDataUrl: readAsDataUrl,
    loadImage: loadImage,
    compressImage: compressImage
  };
});
