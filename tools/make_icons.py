"""从一张方形图片生成全套 Android 启动器图标。

    python tools/make_icons.py path/to/icon.png

会生成（覆盖）app/src/main/res 下的：
    mipmap-{mdpi..xxxhdpi}/ic_launcher.png            旧版方形图标
    mipmap-{mdpi..xxxhdpi}/ic_launcher_round.png      旧版圆形图标
    mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png 自适应图标前景层

自适应图标的背景色自动取原图四周边缘的平均色，这样启动器做视差位移时
露出的边缘色和图片本身无缝。前景铺满 108dp 画布，主体应落在中央 66%
安全区内（生成后建议用圆形遮罩预览确认没被裁到）。

依赖：pip install pillow
"""

from __future__ import annotations

import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1

    src = Image.open(sys.argv[1]).convert("RGB")
    w, h = src.size
    if w != h:
        side = min(w, h)
        src = src.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2))
        print(f"原图不是正方形，已居中裁成 {side}x{side}")

    px = []
    for x in range(0, src.size[0], 8):
        px.append(src.getpixel((x, 2)))
        px.append(src.getpixel((x, src.size[1] - 3)))
    for y in range(0, src.size[1], 8):
        px.append(src.getpixel((2, y)))
        px.append(src.getpixel((src.size[0] - 3, y)))
    avg = tuple(sum(c[i] for c in px) // len(px) for i in range(3))
    print("自适应图标背景色：#%02X%02X%02X" % avg)

    for d, size in LEGACY.items():
        out = os.path.join(RES, f"mipmap-{d}")
        os.makedirs(out, exist_ok=True)

        icon = src.resize((size, size), Image.LANCZOS)
        icon.save(os.path.join(out, "ic_launcher.png"), optimize=True)

        round_icon = icon.convert("RGBA")
        mask = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
        round_icon.putalpha(mask.resize((size, size), Image.LANCZOS))
        round_icon.save(os.path.join(out, "ic_launcher_round.png"), optimize=True)

    for d, size in ADAPTIVE.items():
        out = os.path.join(RES, f"mipmap-{d}")
        os.makedirs(out, exist_ok=True)
        src.resize((size, size), Image.LANCZOS).save(
            os.path.join(out, "ic_launcher_foreground.png"), optimize=True
        )

    print(f"已生成 {len(LEGACY) * 2 + len(ADAPTIVE)} 个 PNG 到 {RES}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
