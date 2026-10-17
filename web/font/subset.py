"""生成 LXGW 楷体的字符子集（woff2 + Android TTF），替换 font/ 下的全量字体。

背景：全量字体约 17MB，而页面实际用到的字形只有数百个（词库数据 +
全部变形结果 + UI 文本，由 collect-chars.js 实测收集）。子集化后首屏
不再等待大文件读取，视觉不变。

Android 端复用同一份字符表，产出 `res/font/lxgw_zhenkai_subset.ttf`；
两个产物必须同源，否则同一句文案在两端会用到不同的回退字体。

用法（需要 fonttools 与 brotli，仅开发时需要，运行时零依赖）：
    pip install fonttools brotli
    python font/subset.py

新增动词、助词或新页面文本后，重新跑一次本脚本即可；woff2 路径由
font/fonts.js 清单声明，本脚本会同步更新其中的文件名与格式。
"""

from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "font" / "LXGWZhenKaiGB-Regular.ttf"
DST = ROOT / "font" / "LXGWZhenKaiGB-Regular.subset.woff2"
ANDROID_DST = ROOT.parent / "android" / "app" / "src" / "main" / "res" / "font" / "lxgw_zhenkai_subset.ttf"
CHARS_FILE = ROOT / "font" / "subset-chars.txt"
FONTS_JS = ROOT / "font" / "fonts.js"

NODE_CANDIDATES = [
    r"C:\Users\xiao_\.workbuddy\binaries\node\versions\22.22.2-6\node.exe",
    "node",
]


def find_node() -> str:
    for candidate in NODE_CANDIDATES:
        try:
            subprocess.run([candidate, "--version"], capture_output=True, check=True)
            return candidate
        except Exception:
            continue
    sys.exit("找不到 node，无法收集字符集。")


def collect_chars(node: str) -> str:
    script = ROOT / "font" / "collect-chars.js"
    result = subprocess.run(
        [node, str(script)], capture_output=True, text=True, check=True
    )
    chars = set(result.stdout)
    # ASCII 可打印字符与常见中文标点，防御未来文本微调
    chars.update(chr(code) for code in range(0x20, 0x7F))
    chars.update("、。！？「」『』（）【】《》〈〉·…—～ー")
    return "".join(sorted(chars))


def build_subset() -> None:
    try:
        from fontTools.subset import main as subset_main
    except ImportError:
        sys.exit("需要 fonttools：pip install fonttools brotli")

    subset_main(
        [
            str(SRC),
            f"--text-file={CHARS_FILE}",
            f"--output-file={DST}",
            "--flavor=woff2",
            "--layout-features=*",
            "--no-hinting",
            "--drop-tables+=DSIG",
        ]
    )


def build_android_subset() -> None:
    """Android 端 TTF 子集：字符表与 Web 完全一致，只换输出格式与路径。"""
    from fontTools.subset import main as subset_main

    ANDROID_DST.parent.mkdir(parents=True, exist_ok=True)
    subset_main(
        [
            str(SRC),
            f"--text-file={CHARS_FILE}",
            f"--output-file={ANDROID_DST}",
            "--layout-features=*",
            "--no-hinting",
            "--drop-tables+=DSIG",
        ]
    )


def verify_android_subset() -> None:
    from fontTools.ttLib import TTFont

    wanted = set(CHARS_FILE.read_text(encoding="utf-8"))
    original_cmap = TTFont(SRC).getBestCmap()
    cmap = TTFont(ANDROID_DST).getBestCmap()
    missing = sorted(c for c in wanted if ord(c) in original_cmap and ord(c) not in cmap)
    if missing:
        sys.exit(f"Android 子集缺少字形: {''.join(missing)}")
    print(
        f"Android 子集: {ANDROID_DST.stat().st_size / 1024:.0f}KB，"
        f"覆盖 {sum(1 for c in wanted if ord(c) in cmap)}/{len(wanted)} 个字符"
    )


def verify() -> None:
    from fontTools.ttLib import TTFont

    wanted = set(CHARS_FILE.read_text(encoding="utf-8"))
    original = TTFont(SRC)
    subset = TTFont(DST)
    original_cmap = original.getBestCmap()
    cmap = subset.getBestCmap()

    # 原字体本身没有的字形（如 ↻）由浏览器回退渲染，不纳入子集校验
    not_in_original = sorted(c for c in wanted if ord(c) not in original_cmap)
    if not_in_original:
        print(f"原字体未覆盖（浏览器回退渲染，不影响子集）: {''.join(not_in_original)}")
    missing = sorted(
        c for c in wanted if ord(c) in original_cmap and ord(c) not in cmap
    )
    if missing:
        sys.exit(f"子集缺少字形: {''.join(missing)}")

    # 抽样比对原字体与子集的字形轮廓和步进宽度，确认视觉一致
    sample = [
        c
        for c in sorted(wanted)
        if ord(c) in cmap and ord(c) in original_cmap and not c.isspace()
    ][:24]
    for char in sample:
        glyph_name = cmap[ord(char)]
        assert glyph_name in subset.getGlyphOrder(), char
        a = original.getGlyphSet()[original_cmap[ord(char)]]
        b = subset.getGlyphSet()[glyph_name]
        if hasattr(a, "coordinates") and hasattr(b, "coordinates"):
            assert list(a.coordinates) == list(b.coordinates), char
        aw = original["hmtx"][original_cmap[ord(char)]][0]
        bw = subset["hmtx"][glyph_name][0]
        assert aw == bw, (char, aw, bw)
    assert subset["head"].unitsPerEm == original["head"].unitsPerEm

    print(f"字符数: {len(wanted)}（其中 CJK {sum(1 for c in wanted if ord(c) > 0x2E80)}）")
    print(f"抽样一致: {len(sample)} 个字形轮廓与步进宽度与原字体相同")
    print(f"体积: {SRC.stat().st_size / 1048576:.1f}MB -> {DST.stat().st_size / 1024:.0f}KB")


def update_manifest() -> None:
    text = FONTS_JS.read_text(encoding="utf-8")
    text = text.replace(
        'file: "LXGWZhenKaiGB-Regular.ttf",', 'file: "LXGWZhenKaiGB-Regular.subset.woff2",'
    )
    text = text.replace('format: "truetype",', 'format: "woff2",')
    FONTS_JS.write_text(text, encoding="utf-8")
    print("已更新 font/fonts.js 指向子集文件")


def main() -> None:
    if not SRC.exists():
        sys.exit(f"找不到全量字体: {SRC}")
    node = find_node()
    chars = collect_chars(node)
    CHARS_FILE.write_text(chars, encoding="utf-8")
    print(f"收集到 {len(chars)} 个字符 -> {CHARS_FILE.name}")
    build_subset()
    verify()
    build_android_subset()
    verify_android_subset()
    update_manifest()


if __name__ == "__main__":
    main()
