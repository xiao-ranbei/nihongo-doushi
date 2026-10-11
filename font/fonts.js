(function (root) {
  "use strict";

  // 字体清单：与本文件一起存放在 font/ 目录中，随仓库一同管理。
  // 新增或替换字体文件时，只需更新这里的列表，页面会自动按本文件
  // 所在目录动态生成加载路径，无需修改 CSS 或其他代码。
  const script = root.document && root.document.currentScript;
  const base = script && script.src
    ? new URL(".", script.src)
    : new URL(".", root.document.baseURI);

  const FONTS = [
    {
      family: "LXGW ZhenKai GB",
      file: "LXGWZhenKaiGB-Regular.ttf",
      format: "truetype",
      weight: "400",
      style: "normal",
      display: "swap"
    }
  ];

  root.JapaneseFontManifest = FONTS.map(function (font) {
    return Object.assign({}, font, {
      src: new URL(font.file, base).href
    });
  });
})(globalThis);
