"""从项目自带 LXGW 楷体提取「あ」字形轮廓，生成图标资产。

为什么不用 <text> 渲染：SVG/系统字体渲染在不同设备上字形会漂移，
应用图标必须用固定轮廓路径。本源文件（web/font/LXGWZhenKaiGB-Regular.ttf）
就是应用内页正在用的字体，因此图标字形与内页视觉天然一致。

产物（写入 docs/design/icon-concepts/）：
  icon-a-ai-paper.svg          预览版（纸底 + 细纹 + あ + 书签）
  icon-a-background.svg        自适应图标背景层（纸底 + 细纹）
  icon-a-foreground.svg        自适应图标前景层（あ + 书签，透明底）
  icon-a-monochrome.svg        单色主题层（Android 13+ themed icon）
  android/*.xml                可直接接入工程的 VectorDrawable

用法：python docs/design/icon-concepts/tools/build-icon-assets.py
"""

from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.misc.transform import Transform
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[4]
SRC = ROOT / "web" / "font" / "LXGWZhenKaiGB-Regular.ttf"
OUT = ROOT / "docs" / "design" / "icon-concepts"
ANDROID_OUT = OUT / "android"

CANVAS = 108.0
GREEN = "#326151"
PAPER = "#FFFEFA"
FAINT = "#EDF3EB"

# 字形目标高度与视觉中心（略偏左上，右下留给书签）
GLYPH_HEIGHT = 46.0
GLYPH_CX = 47.5
GLYPH_CY = 50.0

# 书签角标（右下），与字形保持间隙
BOOKMARK = "M70 56h10v20l-5 -5.5 -5 5.5z"
BOOKMARK_BOX = (70.0, 56.0, 80.0, 76.0)

# 两层安全边界：
#   66dp = 官方安全区（1.5 倍裁切下不被切）
#   60dp = 本项目自留的视觉余量（在 72dp 可见窗口内约占 83%，不显小）
SAFE_MIN = 21.0
SAFE_MAX = 87.0
VISIBLE_MIN = 24.0
VISIBLE_MAX = 84.0

# 纸页细纹（背景层）
RULES = [
    (20.0, 34.0, 88.0, 34.0),
    (20.0, 52.0, 88.0, 52.0),
    (20.0, 70.0, 88.0, 70.0),
]


def glyph_outline(char: str):
    """返回 (path_d, bbox)，坐标已烘焙到 108×108 画布（含 y 翻转与缩放）。"""
    font = TTFont(str(SRC))
    upem = font["head"].unitsPerEm
    cmap = font.getBestCmap()
    glyph_name = cmap[ord(char)]
    glyph_set = font.getGlyphSet()

    bounds_pen = BoundsPen(glyph_set)
    glyph_set[glyph_name].draw(bounds_pen)
    x_min, y_min, x_max, y_max = bounds_pen.bounds

    scale = GLYPH_HEIGHT / (y_max - y_min)
    center_x = (x_min + x_max) / 2.0
    center_y = (y_min + y_max) / 2.0
    transform = Transform(
        scale, 0, 0, -scale,
        GLYPH_CX - center_x * scale,
        GLYPH_CY + center_y * scale,
    )

    pen = SVGPathPen(glyph_set)
    glyph_set[glyph_name].draw(TransformPen(pen, transform))
    path_d = pen.getCommands()

    box = (
        GLYPH_CX - (x_max - x_min) * scale / 2.0,
        GLYPH_CY - GLYPH_HEIGHT / 2.0,
        GLYPH_CX + (x_max - x_min) * scale / 2.0,
        GLYPH_CY + GLYPH_HEIGHT / 2.0,
    )
    font.close()
    return path_d, box


def rules_svg(stroke: str, width: float = 2.0) -> str:
    return "\n".join(
        f'  <path d="M{x1:g} {y1:g}H{x2:g}" stroke="{stroke}" stroke-width="{width:g}" stroke-linecap="round"/>'
        for x1, y1, x2, _ in RULES
    )


def svg_document(body: str, label: str) -> str:
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108" '
        f'role="img" aria-label="{label}">\n{body}\n</svg>\n'
    )


def main() -> None:
    path_d, glyph_box = glyph_outline("あ")

    print(f"字形 bbox: x {glyph_box[0]:.1f}–{glyph_box[2]:.1f}, y {glyph_box[1]:.1f}–{glyph_box[3]:.1f}")
    print(f"书签 bbox: x {BOOKMARK_BOX[0]:.1f}–{BOOKMARK_BOX[2]:.1f}, y {BOOKMARK_BOX[1]:.1f}–{BOOKMARK_BOX[3]:.1f}")

    for label, box in (("字形", glyph_box), ("书签", BOOKMARK_BOX)):
        if not (box[0] >= SAFE_MIN and box[2] <= SAFE_MAX and box[1] >= SAFE_MIN and box[3] <= SAFE_MAX):
            raise SystemExit(f"{label}超出 66dp 官方安全区（x/y 允许范围 {SAFE_MIN:g}–{SAFE_MAX:g}）：{box}")
        if not (box[0] >= VISIBLE_MIN and box[2] <= VISIBLE_MAX and box[1] >= VISIBLE_MIN and box[3] <= VISIBLE_MAX):
            raise SystemExit(
                f"{label}超出 60dp 自留余量区（x/y 允许范围 {VISIBLE_MIN:g}–{VISIBLE_MAX:g}）：{box}"
            )

    overlaps = (
        glyph_box[0] < BOOKMARK_BOX[2] and BOOKMARK_BOX[0] < glyph_box[2]
        and glyph_box[1] < BOOKMARK_BOX[3] and BOOKMARK_BOX[1] < glyph_box[3]
    )
    if overlaps:
        raise SystemExit("字形与书签矩形相交，需调整版面")
    gap_x = BOOKMARK_BOX[0] - glyph_box[2]
    print(f"安全区检查通过：66dp 官方区（x/y {SAFE_MIN:g}–{SAFE_MAX:g}）与 60dp 余量区（x/y {VISIBLE_MIN:g}–{VISIBLE_MAX:g}）")
    print(f"字形与书签水平间隙 {gap_x:.1f}px")
    print(f"路径长度: {len(path_d)} 字符；子路径数: {path_d.count('M')}")

    OUT.mkdir(parents=True, exist_ok=True)
    ANDROID_OUT.mkdir(parents=True, exist_ok=True)

    # 1) 预览版：纸底 + 细纹 + 字形 + 书签
    preview = (
        f'  <rect width="108" height="108" fill="{PAPER}"/>\n'
        f"{rules_svg(FAINT)}\n"
        f'  <path d="{path_d}" fill="{GREEN}"/>\n'
        f'  <path d="{BOOKMARK}" fill="{GREEN}"/>'
    )
    (OUT / "icon-a-ai-paper.svg").write_text(svg_document(preview, "图标方案 A あ与纸页"), encoding="utf-8")

    # 2) 背景层：纸底 + 细纹
    background = f'  <rect width="108" height="108" fill="{PAPER}"/>\n{rules_svg(FAINT)}'
    (OUT / "icon-a-background.svg").write_text(svg_document(background, "图标背景层"), encoding="utf-8")

    # 3) 前景层：字形 + 书签（透明底）
    foreground = f'  <path d="{path_d}" fill="{GREEN}"/>\n  <path d="{BOOKMARK}" fill="{GREEN}"/>'
    (OUT / "icon-a-foreground.svg").write_text(svg_document(foreground, "图标前景层"), encoding="utf-8")

    # 4) 单色主题层：系统着色，因此用纯黑
    monochrome = f'  <path d="{path_d}" fill="#000000"/>\n  <path d="{BOOKMARK}" fill="#000000"/>'
    (OUT / "icon-a-monochrome.svg").write_text(svg_document(monochrome, "图标单色主题层"), encoding="utf-8")

    # 5) Android VectorDrawable（可直接接入 res/drawable）
    def vector(body: str, name: str) -> None:
        (ANDROID_OUT / name).write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n'
            f"{body}\n"
            "</vector>\n",
            encoding="utf-8",
        )

    vector(
        f'    <path\n        android:fillColor="{GREEN}"\n        android:fillType="nonZero"\n'
        f'        android:pathData="{path_d}" />\n'
        f'    <path\n        android:fillColor="{GREEN}"\n'
        f'        android:pathData="{BOOKMARK}" />',
        "ic_launcher_foreground.xml",
    )
    rules_xml = "\n".join(
        f'    <path\n        android:strokeColor="{FAINT}"\n        android:strokeWidth="2"\n'
        f'        android:strokeLineCap="round"\n        android:pathData="M{x1:g} {y1:g}H{x2:g}" />'
        for x1, y1, x2, _ in RULES
    )
    vector(
        f'    <path\n        android:fillColor="{PAPER}"\n        android:pathData="M0 0h108v108H0z" />\n{rules_xml}',
        "ic_launcher_background.xml",
    )
    vector(
        f'    <path\n        android:fillColor="#000000"\n        android:fillType="nonZero"\n'
        f'        android:pathData="{path_d}" />\n'
        f'    <path\n        android:fillColor="#000000"\n'
        f'        android:pathData="{BOOKMARK}" />',
        "ic_launcher_monochrome.xml",
    )

    print("已生成：")
    for path in sorted(OUT.glob("icon-a-*.svg")):
        print(f"  {path.relative_to(ROOT).as_posix()}  ({path.stat().st_size} B)")
    for path in sorted(ANDROID_OUT.glob("*.xml")):
        print(f"  {path.relative_to(ROOT).as_posix()}  ({path.stat().st_size} B)")


if __name__ == "__main__":
    main()
