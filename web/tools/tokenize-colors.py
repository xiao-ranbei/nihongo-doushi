"""把 styles.css 中 :root 以外的硬编码色值替换为语义令牌。

策略：保值归并——按用途命名令牌，同用途取出现次数最多的值为代表，
组内色差 <=2（由 color-audit.py 聚类保证）；#fff/#fffefa 等直接复用
已有令牌或按 P0-3 例外保留。替换后组件内不再出现裸色值。

用法: python tools/tokenize-colors.py
前置: 先运行 python tools/color-audit.py 确认聚类未漂移。
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSS = ROOT / "styles.css"

# 令牌名 -> 代表色值（按用途分组；值为该用途在存量代码中的众数）
TOKENS = [
    ("文字 · 灰阶（弱 → 强）", [
        ("--text-faint-soft", "#9aa39b"),   # 页脚/空态说明/最弱点缀文字
        ("--text-faint", "#939c93"),        # 品牌副题/词义/错题角标行
        ("--text-faint-alt", "#909990"),    # 统计汇总文字
        ("--text-faint-deep", "#8a948b"),   # 答案读音/正确答案标签
        ("--text-muted-soft", "#879188"),   # 区块说明/受动静/错题描述
        ("--text-strong", "#657268"),       # 表格正文/统计数字
        ("--text-placeholder", "#b2bab2"),  # 输入框占位符
    ]),
    ("文字 · 绿色系", [
        ("--text-green-soft", "#65796a"),   # 语态大意/规则说明
        ("--text-green-mid", "#53685a"),    # 绿色正文/表格首列/错题答案
        ("--text-green", "#657c69"),        # 类别徽章文字
        ("--text-green-deep", "#3e5b49"),   # 正确答案/错题标题/速查结果
    ]),
    ("文字 · 语义色", [
        ("--stat-correct-text", "#54705a"),
        ("--stat-incorrect-text", "#98645b"),
        ("--text-red", "#9c5b50"),
        ("--orange-text", "#9c704f"),
        ("--orange-text-deep", "#895936"),
        ("--toast-text", "#45654f"),
    ]),
    ("文字 · 组件专用", [
        ("--eyebrow", "#839184"),
        ("--intro-copy", "#828d84"),
        ("--reading", "#839087"),
        ("--meaning-text", "#627967"),
        ("--form-label", "#57685c"),
        ("--input-text", "#4a5c50"),
        ("--button-text", "#4b6554"),
        ("--text-button", "#758178"),
        ("--user-answer", "#7f8b81"),
        ("--hint-text", "#718075"),
        ("--empty-icon", "#94a492"),
        ("--tab-count-text", "#58725e"),
    ]),
    ("线条", [
        ("--line-soft", "#e4e8df"),         # 常规描边（徽章/输入/空态）
        ("--line-control", "#dfe5dc"),      # 按钮与输入描边
        ("--line-faint", "#eef0eb"),        # 最弱分隔线/卡片内边线
        ("--line-prompt", "#e8ece4"),       # 题面区描边
        ("--line-accent", "#c6d4c7"),       # 悬停强调线
        ("--line-accent-hover", "#b9cbbc"), # 悬停强调线（深）
        ("--line-hover-green", "#cbd9ca"),  # 绿色悬停线/提示描边
        ("--line-green", "#d8e6d7"),        # 反馈面板描边
        ("--line-red", "#ecd6d0"),          # 错误态描边
        ("--line-red-strong", "#e7c7c0"),   # 错误 toast 描边
        ("--line-orange", "#d7b6a1"),       # 提示态描边
        ("--hint-border", "#9ab39a"),       # 提示左侧竖线
    ]),
    ("浅底", [
        ("--bg-pale-green", "#f4f8f2"),     # 提示/反馈/悬停浅绿底
        ("--bg-pale-cool", "#f8faf5"),     # 题面/表头浅底
        ("--bg-pale-strong", "#e9eee7"),    # 徽章/计数底
        ("--bg-green-hover", "#e2ece0"),    # 学习卡悬停底
        ("--bg-green-strong", "#dcebdd"),   # 对错标记底
        ("--bg-neutral", "#eef0eb"),        # 统计槽底
        ("--bg-orange-pale", "#fff8f1"),    # 提示浅底
        ("--bg-red-pale", "#fbf4f1"),       # 错误反馈浅底
        ("--bg-red-strong", "#f2dfda"),     # 错误标记底
    ]),
    ("渐变与状态点", [
        ("--stat-correct-from", "#92ad91"),
        ("--stat-correct-to", "#66876d"),
        ("--stat-incorrect-from", "#e2b0a5"),
        ("--stat-incorrect-to", "#c97f74"),
        ("--track-empty-from", "#edf1eb"),
        ("--track-empty-to", "#f3eeea"),
        ("--dot-correct", "#78977c"),
        ("--dot-incorrect", "#d99b91"),
        ("--dot-online", "#87a37f"),
    ]),
]

# 存量裸色值 -> 目标写法（令牌或保留项）
REPLACEMENTS = {
    "#939c93": "var(--text-faint)",
    "#929b92": "var(--text-faint)",
    "#9aa39b": "var(--text-faint-soft)",
    "#9aa29a": "var(--text-faint-soft)",
    "#9aa399": "var(--text-faint-soft)",
    "#909990": "var(--text-faint-alt)",
    "#909a91": "var(--text-faint-alt)",
    "#8a948b": "var(--text-faint-deep)",
    "#89958a": "var(--text-faint-deep)",
    "#8b968d": "var(--text-faint-deep)",
    "#94a492": "var(--empty-icon)",
    "#7c877e": "var(--muted)",
    "#7b867e": "var(--muted)",
    "#879188": "var(--text-muted-soft)",
    "#869188": "var(--text-muted-soft)",
    "#839184": "var(--eyebrow)",
    "#839087": "var(--reading)",
    "#828d84": "var(--intro-copy)",
    "#818c83": "var(--intro-copy)",
    "#758178": "var(--text-button)",
    "#7f8b81": "var(--user-answer)",
    "#718075": "var(--hint-text)",
    "#647268": "var(--text-strong)",
    "#647269": "var(--text-strong)",
    "#657268": "var(--text-strong)",
    "#627967": "var(--meaning-text)",
    "#65796a": "var(--text-green-soft)",
    "#53675a": "var(--text-green-mid)",
    "#53685a": "var(--text-green-mid)",
    "#536d59": "var(--text-green-mid)",
    "#657c69": "var(--text-green)",
    "#57685c": "var(--form-label)",
    "#58725e": "var(--tab-count-text)",
    "#56705d": "var(--tab-count-text)",
    "#54705a": "var(--stat-correct-text)",
    "#4a5c50": "var(--input-text)",
    "#4b6554": "var(--button-text)",
    "#365e49": "var(--text-green-deep)",
    "#3a5c49": "var(--text-green-deep)",
    "#3e5b49": "var(--text-green-deep)",
    "#45654f": "var(--toast-text)",
    "#b2bab2": "var(--text-placeholder)",
    "#98645b": "var(--stat-incorrect-text)",
    "#9c5b50": "var(--text-red)",
    "#895936": "var(--orange-text-deep)",
    "#9c704f": "var(--orange-text)",
    "#fffefa": "var(--surface)",
    "#e4e8df": "var(--line-soft)",
    "#e2e6de": "var(--line-soft)",
    "#e2e7df": "var(--line-soft)",
    "#dfe5dc": "var(--line-control)",
    "#eef0eb": "var(--line-faint)",
    "#edf0e9": "var(--line-faint)",
    "#eef0e9": "var(--line-faint)",
    "#e8ece4": "var(--line-prompt)",
    "#c6d4c7": "var(--line-accent)",
    "#b9cbbc": "var(--line-accent-hover)",
    "#cbd9ca": "var(--line-hover-green)",
    "#d8e6d7": "var(--line-green)",
    "#ecd6d0": "var(--line-red)",
    "#e7c7c0": "var(--line-red-strong)",
    "#d7b6a1": "var(--line-orange)",
    "#9ab39a": "var(--hint-border)",
    "#f4f8f2": "var(--bg-pale-green)",
    "#f2f7f0": "var(--bg-pale-green)",
    "#f5f8f2": "var(--bg-pale-green)",
    "#f8faf5": "var(--bg-pale-cool)",
    "#f8f9f5": "var(--bg-pale-cool)",
    "#e9eee7": "var(--bg-pale-strong)",
    "#eaf0e8": "var(--bg-pale-strong)",
    "#e2ece0": "var(--bg-green-hover)",
    "#dcebdd": "var(--bg-green-strong)",
    "#fff8f1": "var(--bg-orange-pale)",
    "#fbf4f1": "var(--bg-red-pale)",
    "#f2dfda": "var(--bg-red-strong)",
    "#92ad91": "var(--stat-correct-from)",
    "#66876d": "var(--stat-correct-to)",
    "#e2b0a5": "var(--stat-incorrect-from)",
    "#c97f74": "var(--stat-incorrect-to)",
    "#edf1eb": "var(--track-empty-from)",
    "#f3eeea": "var(--track-empty-to)",
    "#78977c": "var(--dot-correct)",
    "#d99b91": "var(--dot-incorrect)",
    "#87a37f": "var(--dot-online)",
}

# P0-3 允许的例外：#fff / #000 保留原样
KEEP = {"#fff", "#000"}


def build_token_block() -> str:
    lines = ["  /* —— 语义令牌（由 tools/tokenize-colors.py 归并生成）—— */"]
    for group, items in TOKENS:
        lines.append(f"  /* {group} */")
        for name, value in items:
            lines.append(f"  {name}: {value};")
    lines.append("")
    return "\n".join(lines)


def check_vars_defined(text: str) -> None:
    """护栏：任何 var(--x) 引用都必须在 :root 内有定义，否则属性会整体失效。"""
    root = re.search(r":root\s*\{(.*?)\}", text, re.S)
    if not root:
        sys.exit("找不到 :root 块")
    defined = set(re.findall(r"(--[a-z0-9-]+)\s*:", root.group(1)))
    used = set(re.findall(r"var\((--[a-z0-9-]+)\)", text))
    undefined = sorted(used - defined)
    if undefined:
        sys.exit(f"存在未定义的变量引用: {undefined}")
    print(f"变量校验通过：定义 {len(defined)} 个，引用 {len(used)} 个，无未定义引用")


def main() -> None:
    text = CSS.read_text(encoding="utf-8")
    lines = text.split("\n")

    # 定位 :root 块结束（第一个 "}"
    root_end = next(i for i, l in enumerate(lines) if l.strip() == "}")
    body = "\n".join(lines[root_end + 1 :])

    # .mistake-answer 的 !important 改为提升选择器特异性，避免令牌化后仍依赖 !important
    body = body.replace(
        ".mistake-answer {\n  color: #536d59 !important;\n}",
        ".mistake-card p.mistake-answer {\n  color: var(--text-green-mid);\n}",
    )
    if ".mistake-answer {\n  color: #536d59 !important;\n}" in body:
        sys.exit("mistake-answer 规则未按预期匹配，请检查 styles.css")

    # 替换（长值优先，避免 #fff 抢在 #fffefa 前）
    count = 0
    for hex_value in sorted(REPLACEMENTS, key=len, reverse=True):
        pattern = re.compile(re.escape(hex_value) + r"(?![0-9a-fA-F])")
        body, n = pattern.subn(REPLACEMENTS[hex_value], body)
        count += n

    # 残留检查：只允许 :root 定义、#fff/#000 例外与注释
    leftovers = []
    for index, line in enumerate(body.split("\n")):
        stripped = line.strip()
        if stripped.startswith("/*") or stripped.startswith("*"):
            continue
        for match in re.finditer(r"#[0-9a-fA-F]{3,8}\b", line):
            if match.group(0).lower() not in KEEP:
                leftovers.append((index, match.group(0)))
    if leftovers:
        sys.exit(f"未覆盖的硬编码色值: {leftovers}")

    # 插入令牌块到 :root 内部（闭合括号之前），否则令牌定义落在规则之外而失效
    new_root = "\n".join(lines[:root_end]) + "\n" + build_token_block() + "}"
    result = new_root + "\n" + body
    check_vars_defined(result)
    CSS.write_text(result, encoding="utf-8")
    print(f"替换 {count} 处；新增令牌 {sum(len(g[1]) for g in TOKENS)} 个")
    print("残留裸色值（P0-3 例外 #fff）:", sorted(KEEP))


if __name__ == "__main__":
    main()
