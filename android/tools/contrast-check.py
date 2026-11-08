"""方案 B 双皮肤对比度自检（R0.6）。

为什么单独写脚本而不是塞进 Kotlin 单测：对比度是「产品口径」而不是「代码契约」，
需要把玻璃合成后的实际背景算出来（玻璃底是半透明的，文字压在 paper+glass 的混色上，
不是 paper 本身），这类计算放在脚本里更容易读、也方便直接贴进文档。

用法（项目根）：python android/tools/contrast-check.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
THEME = ROOT / "android/app/src/main/java/com/kotonoha/app/ui/theme/Theme.kt"

# 参与检测的前景 / 背景对：(前景字段, 背景字段, 说明)
# 背景写 "paper+glass" 表示 paper 上叠一层 glassSurface（玻璃卡片的实际观感）
PAIRS = [
    ("ink", "paper", "正文 on 屏幕底"),
    ("ink", "paper+glass", "正文 on 玻璃卡片"),
    ("muted", "paper", "次要文字 on 屏幕底"),
    ("muted", "paper+glass", "次要文字 on 玻璃卡片"),
    ("textStrong", "paper+glass", "强调文字 on 玻璃卡片"),
    ("brand", "paper", "主色标题 on 屏幕底"),
    ("brand", "paper+glass", "主色标题 on 玻璃卡片"),
    ("textGreen", "paper+glass", "绿色正文 on 玻璃卡片"),
    ("brandOn", "brand", "主色按钮文字"),
    ("statCorrectText", "paper+glass", "答对文字 on 玻璃卡片"),
    ("statIncorrectText", "paper+glass", "答错文字 on 玻璃卡片"),
    ("textRed", "paper+glass", "错误提示 on 玻璃卡片"),
]

# WCAG 2.1 阈值：正文 4.5、大字号 3.0、非文本 UI 3.0
MIN_BODY = 4.5
MIN_LARGE = 3.0


def parse_palettes(text):
    """抓出 CandyPalette / InkNightPalette 两个构造块里的 `字段 = Color(0xAARRGGBB)`。"""
    palettes = {}
    for name in ("CandyPalette", "InkNightPalette"):
        m = re.search(name + r"\s*=\s*Palette\(", text)
        if not m:
            raise SystemExit(f"找不到 {name}")
        start = m.end()
        depth = 1
        i = start
        while depth:
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
            i += 1
        block = text[start:i]
        values = dict(re.findall(r"(\w+) = Color\(0x([0-9A-Fa-f]{8})\)", block))
        palettes[name] = {k: v for k, v in values.items()}
    return palettes


def to_rgb(argb):
    v = int(argb, 16)
    return (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, (v >> 24) & 0xFF


def composite(fg, bg):
    """把带 alpha 的 fg 叠到不透明 bg 上，返回不透明结果。"""
    fr, fg_, fb, fa = fg
    br, bg_, bb, _ = bg
    a = fa / 255
    return (
        round(fr * a + br * (1 - a)),
        round(fg_ * a + bg_ * (1 - a)),
        round(fb * a + bb * (1 - a)),
        255,
    )


def luminance(rgb):
    def channel(c):
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b, _ = rgb
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a, b):
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def resolve(palette, key):
    if key == "paper+glass":
        return composite(to_rgb(palette["glassSurface"]), to_rgb(palette["paper"]))
    return to_rgb(palette[key])


def hexof(rgb):
    return "#%02X%02X%02X" % rgb[:3]


def main():
    text = THEME.read_text(encoding="utf-8")
    palettes = parse_palettes(text)
    failures = []
    print(f"来源：{THEME.relative_to(ROOT)}\n")
    for name, palette in palettes.items():
        skin = "浅色 糖果卡" if name == "CandyPalette" else "深色 墨夜"
        print(f"== {skin} ({name}) ==")
        print(f"{'前景':<18}{'背景':<14}{'前景色':<10}{'背景色':<10}{'对比度':>7}  判定")
        for fg_key, bg_key, label in PAIRS:
            fg = resolve(palette, fg_key)
            bg = resolve(palette, bg_key)
            ratio = contrast(fg, bg)
            # 正文按 4.5 要求；主色按钮文字、大字号统计数字按 3.0
            # 主色（brand）只用于 ≥14sp 粗体标题、区块标题与图标，按大字号 3.0 口径；
            # 正文绿色一律走 textGreen（--brand-ink，深一档），它按 4.5 判
            large = fg_key in ("brandOn", "statCorrectText", "statIncorrectText", "brand")
            threshold = MIN_LARGE if large else MIN_BODY
            ok = ratio >= threshold
            if not ok:
                failures.append((skin, label, ratio, threshold))
            print(
                f"{label:<18}{bg_key:<14}{hexof(fg):<10}{hexof(bg):<10}{ratio:>7.2f}  "
                f"{'PASS' if ok else 'FAIL'} (≥{threshold})"
            )
        print()

    if failures:
        print("未达标：")
        for skin, label, ratio, threshold in failures:
            print(f"  - {skin} / {label}: {ratio:.2f} < {threshold}")
        return 1
    print("全部达标。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
