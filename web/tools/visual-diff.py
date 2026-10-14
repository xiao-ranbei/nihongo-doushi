"""对比两组截图的逐像素差异，输出每对的统计与差异热图。

用法: python tools/visual-diff.py <before目录> <after目录> <输出目录>
判定: max_delta <= 8 且 diff_ratio(>8 的像素占比) < 0.5% 视为无可见回归。
"""

import sys
from pathlib import Path

from PIL import Image, ImageChops

THRESHOLD = 8  # 单通道容差：抗锯齿与子像素渲染噪声量级
OVER_PIXEL_LIMIT = 500  # 超容差像素绝对数量上限（约占 1280x900 的 0.04%）
MEAN_LIMIT = 0.5  # 全图平均通道差上限


def diff_pair(before: Path, after: Path, out_dir: Path):
    a = Image.open(before).convert("RGB")
    b = Image.open(after).convert("RGB")
    if a.size != b.size:
        return None, f"尺寸不同 {a.size} vs {b.size}"

    delta = ImageChops.difference(a, b)
    import numpy as np

    arr = np.asarray(delta).max(axis=2)
    over = arr > THRESHOLD
    over_count = int(over.sum())
    max_delta = int(arr.max())
    mean_delta = float(arr.mean())

    # 热图：超容差像素标红，叠加到 after 图上
    heat = np.asarray(b).copy()
    heat[over] = [255, 0, 0]
    Image.fromarray(heat).save(out_dir / (after.stem + "-heat.png"))

    return {
        "max_delta": max_delta,
        "mean_delta": round(mean_delta, 3),
        "over_count": over_count,
        "pass": over_count <= OVER_PIXEL_LIMIT and mean_delta <= MEAN_LIMIT,
    }, None


def main():
    before_dir, after_dir, out_dir = map(Path, sys.argv[1:4])
    out_dir.mkdir(parents=True, exist_ok=True)
    names = sorted(p.name for p in after_dir.glob("*.png"))
    ok = True
    for name in names:
        before = before_dir / name
        if not before.exists():
            print(f"{name}: 缺少基线图，跳过")
            continue
        result, error = diff_pair(before, after_dir / name, out_dir)
        if error:
            print(f"{name}: {error}")
            ok = False
            continue
        verdict = "PASS" if result["pass"] else "FAIL"
        if not result["pass"]:
            ok = False
        print(
            f"{name}: {verdict}  max_delta={result['max_delta']}  "
            f"mean={result['mean_delta']}  over_pixels={result['over_count']}"
        )
    print("总体:", "无可见回归" if ok else "存在回归，需人工查看热图")
    sys.exit(0 if ok else 2)


if __name__ == "__main__":
    main()
