# -*- coding: utf-8 -*-
"""把共享骨架 + 三套主题 + 共享引擎内联成三个自包含、可直接打开的原型 HTML。

用法（在本目录）：python build.py
产物：option-a.html / option-b.html / option-c.html
三份产物共享同一份 engine.js 与 base.css，保证 IA、页面范围、操作路径、状态机完全对等。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"

PLANS = {
    "a": ("方案 A · 纸感延续", "theme-a.css"),
    "b": ("方案 B · 全量液态玻璃", "theme-b.css"),
    "c": ("方案 C · 结构化线框", "theme-c.css"),
}


def read(name):
    return (SRC / name).read_text(encoding="utf-8")


def icon_literal():
    """把 src/icons/tab-*.svg 压成一行，内联进 engine.js 的 ICONS 占位。

    图标必须内联而不是 <img src>：只有内联 SVG 才能继承 currentColor，
    深浅皮肤 / 三档材质 / 选中态才不用维护第二套资源。
    """
    parts = []
    for p in sorted((SRC / "icons").glob("tab-*.svg")):
        key = p.stem[len("tab-"):]
        svg = " ".join(p.read_text(encoding="utf-8").split())
        svg = svg.replace("'", "\\'")
        parts.append("%s:'%s'" % (key, svg))
    print("icons: %d (%s)" % (len(parts), ", ".join(
        p.stem for p in sorted((SRC / "icons").glob("tab-*.svg")))))
    return ",".join(parts)


def main():
    shell = read("shell.html")
    base = read("base.css")
    engine = read("engine.js").replace("/*ICONS*/", icon_literal())
    for plan, (title, css_name) in PLANS.items():
        css = base + "\n/* ---- " + title + " ---- */\n" + read(css_name)
        html = (shell.replace("{{TITLE}}", title)
                     .replace("{{PLAN}}", plan)
                     .replace("{{CSS}}", css)
                     .replace("{{JS}}", engine))
        out = ROOT / ("option-" + plan + ".html")
        out.write_text(html, encoding="utf-8")
        print("%s  %s bytes" % (out.name, out.stat().st_size))


if __name__ == "__main__":
    main()
