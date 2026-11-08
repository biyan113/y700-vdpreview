#!/usr/bin/env python3
"""画出虚拟屏的启动图标和通知小图标。不依赖第三方库。"""
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def write_png(path, w, h, pixels):
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)
        raw.extend(pixels[y * stride:(y + 1) * stride])

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


def blend(pixels, w, h, x, y, color):
    if x < 0 or y < 0 or x >= w or y >= h:
        return
    i = (y * w + x) * 4
    sr, sg, sb, sa = color
    if sa <= 0:
        pixels[i:i + 4] = b"\x00\x00\x00\x00"
        return
    if sa >= 255:
        pixels[i:i + 4] = bytes((sr, sg, sb, 255))
        return
    dr, dg, db, da = pixels[i:i + 4]
    a = sa / 255
    ia = 1 - a
    pixels[i] = int(sr * a + dr * ia)
    pixels[i + 1] = int(sg * a + dg * ia)
    pixels[i + 2] = int(sb * a + db * ia)
    pixels[i + 3] = int(min(255, sa + da * ia))


def fill_round(pixels, w, h, rect, radius, color):
    left, top, right, bottom = rect
    for y in range(top, bottom):
        for x in range(left, right):
            dx = 0
            dy = 0
            if x < left + radius and y < top + radius:
                dx, dy = left + radius - x, top + radius - y
            elif x >= right - radius and y < top + radius:
                dx, dy = x - (right - radius - 1), top + radius - y
            elif x < left + radius and y >= bottom - radius:
                dx, dy = left + radius - x, y - (bottom - radius - 1)
            elif x >= right - radius and y >= bottom - radius:
                dx, dy = x - (right - radius - 1), y - (bottom - radius - 1)
            if dx or dy:
                dist = (dx * dx + dy * dy) ** 0.5
                if dist > radius + 0.6:
                    continue
                if dist > radius - 0.6:
                    fade = color[3] * (radius + 0.6 - dist)
                    blend(pixels, w, h, x, y, (color[0], color[1], color[2], max(0, min(255, int(fade)))))
                    continue
            blend(pixels, w, h, x, y, color)


def icon(size):
    pixels = bytearray(size * size * 4)
    fill_round(pixels, size, size, (0, 0, size, size), int(size * 0.22), (20, 34, 28, 255))
    m = int(size * 0.18)
    fill_round(pixels, size, size, (m, m, size - m, size - m), int(size * 0.12), (243, 239, 231, 255))
    n = int(size * 0.27)
    fill_round(pixels, size, size, (n, n, size - n, size - n), int(size * 0.08), (22, 48, 40, 255))
    bar_top = size - int(size * 0.40)
    bar_h = max(2, size // 28)
    fill_round(
        pixels, size, size,
        (n + int(size * 0.08), bar_top, size - n - int(size * 0.08), bar_top + bar_h),
        bar_h // 2,
        (231, 161, 90, 255),
    )
    return pixels


def stat(size):
    pixels = bytearray(size * size * 4)
    m = int(size * 0.08)
    fill_round(pixels, size, size, (m, m, size - m, size - m), int(size * 0.16), (255, 255, 255, 255))
    n = int(size * 0.24)
    fill_round(pixels, size, size, (n, n, size - n, size - n), int(size * 0.08), (0, 0, 0, 0))
    # 内屏被挖空后补一条白线，通知栏里仍能看出是一块屏。
    bar_top = size - int(size * 0.38)
    bar_h = max(2, size // 14)
    fill_round(
        pixels, size, size,
        (n + int(size * 0.08), bar_top, size - n - int(size * 0.08), bar_top + bar_h),
        bar_h // 2,
        (255, 255, 255, 255),
    )
    return pixels


def main():
    write_png(ROOT / "res/mipmap-xxxhdpi/ic_launcher.png", 192, 192, icon(192))
    write_png(ROOT / "res/drawable/ic_stat.png", 96, 96, stat(96))


if __name__ == "__main__":
    main()
