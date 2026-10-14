"""分析 styles.css 中 :root 以外的硬编码色值，按色差聚类，输出映射草案。

用法: python tools/color-audit.py
输出: tools/color-clusters.json —— 每个聚类含代表色、成员、出现次数与行号，
      供人工审阅后写入 tools/color-token-map.json 执行替换。
"""

import json
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSS = ROOT / "styles.css"
OUT = ROOT / "tools" / "color-clusters.json"

HEX_RE = re.compile(r"#[0-9a-fA-F]{3,8}\b")


def parse_hex(value: str):
    value = value[1:]
    if len(value) == 3:
        value = "".join(c * 2 for c in value)
    if len(value) == 4:  # #rgba，本文件未使用，防御
        return None
    if len(value) != 6:
        return None
    return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))


def channel_delta(a, b):
    return max(abs(x - y) for x, y in zip(a, b))


def main() -> None:
    lines = CSS.read_text(encoding="utf-8").splitlines()
    root_end = 0
    for index, line in enumerate(lines):
        if line.strip() == "}":
            root_end = index
            break

    occurrences = []  # (line_no, hex, rgb)
    for index, line in enumerate(lines):
        if index <= root_end:
            continue  # :root 令牌定义区本身就是色值的家
        if line.strip().startswith("/*") or line.strip().startswith("*"):
            continue
        for match in HEX_RE.finditer(line):
            rgb = parse_hex(match.group(0))
            if rgb is None:
                continue
            occurrences.append((index + 1, match.group(0).lower(), rgb))

    # 按 (亮度相近, 色相差不大) 贪心聚类：亮度差 <=2 且逐通道差 <=2 视为同一家族
    families = []  # [ [rgb, ...] ]
    assign = {}
    for _, hex_value, rgb in occurrences:
        placed = False
        for family in families:
            representative = family[0]
            lum_a = sum(representative) / 3
            lum_b = sum(rgb) / 3
            if abs(lum_a - lum_b) <= 3 and channel_delta(representative, rgb) <= 2:
                family.append(rgb)
                assign[hex_value] = families.index(family)
                placed = True
                break
        if not placed:
            families.append([rgb])
            assign[hex_value] = len(families) - 1

    clusters = []
    for family_index, family in enumerate(families):
        members = sorted({h for h, f in assign.items() if f == family_index})
        count = sum(1 for _, h, _ in occurrences if assign[h] == family_index)
        lines_used = sorted({ln for ln, h, _ in occurrences if assign[h] == family_index})
        clusters.append(
            {
                "cluster": family_index,
                "suggest_token": None,  # 人工填写
                "representative": "#%02x%02x%02x" % family[0],
                "members": members,
                "occurrences": count,
                "lines": lines_used,
            }
        )

    clusters.sort(key=lambda c: -c["occurrences"])
    OUT.parent.mkdir(exist_ok=True)
    OUT.write_text(json.dumps(clusters, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"硬编码出现 {len(occurrences)} 次，distinct {len(assign)} 个，聚类后 {len(clusters)} 组")
    for c in clusters:
        print(f"  #{c['cluster']:>2} {c['representative']}  x{c['occurrences']:>2}  {c['members']}")


if __name__ == "__main__":
    main()
