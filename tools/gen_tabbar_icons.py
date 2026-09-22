#!/usr/bin/env python3
"""
生成 / 重新着色 xsy-scm-miniapp 的 TabBar 图标。

背景
----
参考工程 xsy-app 自带的是蓝色（#1A9AFF）后台视觉图标，且只有
home / list / message / mine 四组，缺少「分类」与「购物车」。
本脚本不依赖 Pillow（离线环境装不上），自行解析 / 写出 PNG。

做两件事
--------
1. recolor：保留原图标 alpha（抗锯齿信息全在 alpha 里），把 RGB 整体
   换成目标品牌色。用于复用 home / list / mine 三个形状。
2. draw：用 SDF + 4x4 超采样从零绘制缺失的「分类（宫格）」与
   「购物车」图标，风格与既有实心圆角图标一致。

用法
----
    python tools/gen_tabbar_icons.py

输出目录：xsy-scm-miniapp/src/static/images/tabbar/
"""

import math
import os
import struct
import sys
import zlib

# ---------------------------------------------------------------- 配置

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

TABBAR_DIR = os.path.join(
    _ROOT, 'xsy-scm-miniapp', 'src', 'static', 'images', 'tabbar',
)

# 原始蓝色图标始终从参考工程读取，保证脚本可重复执行（幂等）。
# 不要改成从 TABBAR_DIR 读：那里第一次运行后就已被换成品牌绿。
SOURCE_DIR = os.path.join(
    _ROOT, 'project-reference-examples', 'xsy-scm', 'xsy-app',
    'src', 'static', 'images', 'tabbar',
)

# 与 src/styles/tokens.scss 保持一致
COLOR_NORMAL = (0x8F, 0x95, 0x9E)   # $color-text-tertiary
COLOR_SELECTED = (0x16, 0xA3, 0x4A)  # $color-primary

SIZE = 56          # 既有图标尺寸
SS = 4             # 超采样倍数


# ---------------------------------------------------------------- PNG 读写

def png_read(path):
    """返回 (width, height, bytearray RGBA)。仅支持 8bit colorType 2/6 非隔行。"""
    with open(path, 'rb') as fp:
        data = fp.read()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'不是 PNG: {path}')

    pos = 8
    idat = bytearray()
    width = height = depth = ctype = None
    while pos < len(data):
        (length,) = struct.unpack('>I', data[pos:pos + 4])
        ctype_name = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if ctype_name == b'IHDR':
            width, height, depth, ctype, _comp, _filt, interlace = struct.unpack('>IIBBBBB', body)
            if depth != 8 or ctype not in (2, 6) or interlace != 0:
                raise ValueError(f'不支持的 PNG 格式: depth={depth} ctype={ctype} interlace={interlace}')
        elif ctype_name == b'IDAT':
            idat += body
        elif ctype_name == b'IEND':
            break
        pos += 12 + length

    raw = zlib.decompress(bytes(idat))
    channels = 4 if ctype == 6 else 3
    stride = width * channels
    out = bytearray(width * height * 4)

    prev = bytearray(stride)
    cursor = 0
    for y in range(height):
        ftype = raw[cursor]
        cursor += 1
        line = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        _unfilter(line, prev, ftype, channels)
        for x in range(width):
            s = x * channels
            d = (y * width + x) * 4
            if channels == 4:
                out[d:d + 4] = line[s:s + 4]
            else:
                out[d:d + 3] = line[s:s + 3]
                out[d + 3] = 255
        prev = line
    return width, height, out


def _unfilter(line, prev, ftype, bpp):
    if ftype == 0:
        return
    n = len(line)
    if ftype == 1:      # Sub
        for i in range(bpp, n):
            line[i] = (line[i] + line[i - bpp]) & 0xFF
    elif ftype == 2:    # Up
        for i in range(n):
            line[i] = (line[i] + prev[i]) & 0xFF
    elif ftype == 3:    # Average
        for i in range(n):
            left = line[i - bpp] if i >= bpp else 0
            line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
    elif ftype == 4:    # Paeth
        for i in range(n):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
            line[i] = (line[i] + pr) & 0xFF
    else:
        raise ValueError(f'未知 PNG filter: {ftype}')


def _chunk(tag, body):
    return (struct.pack('>I', len(body)) + tag + body
            + struct.pack('>I', zlib.crc32(tag + body) & 0xFFFFFFFF))


def png_write(path, width, height, rgba):
    """写出 8bit RGBA PNG（filter 0）。"""
    stride = width * 4
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type None
        raw += rgba[y * stride:(y + 1) * stride]

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    # sRGB + rendering intent 0，保证与既有图标色彩空间一致
    srgb = struct.pack('>B', 0)
    png = (b'\x89PNG\r\n\x1a\n'
           + _chunk(b'IHDR', ihdr)
           + _chunk(b'sRGB', srgb)
           + _chunk(b'IDAT', zlib.compress(bytes(raw), 9))
           + _chunk(b'IEND', b''))
    with open(path, 'wb') as fp:
        fp.write(png)


# ---------------------------------------------------------------- 着色

def recolor(rgba, color):
    """保留 alpha，把 RGB 整体替换为 color。抗锯齿信息全在 alpha 通道。"""
    out = bytearray(len(rgba))
    r, g, b = color
    for i in range(0, len(rgba), 4):
        a = rgba[i + 3]
        if a:
            out[i] = r
            out[i + 1] = g
            out[i + 2] = b
            out[i + 3] = a
    return out


# ---------------------------------------------------------------- 形状 SDF

def _rrect(cx, cy, hw, hh, r):
    def f(x, y):
        dx = abs(x - cx) - (hw - r)
        dy = abs(y - cy) - (hh - r)
        ox, oy = max(dx, 0.0), max(dy, 0.0)
        return math.hypot(ox, oy) + min(max(dx, dy), 0.0) - r
    return f


def _circle(cx, cy, r):
    def f(x, y):
        return math.hypot(x - cx, y - cy) - r
    return f


def _segment(x1, y1, x2, y2, width):
    half = width / 2.0
    dx, dy = x2 - x1, y2 - y1
    length_sq = dx * dx + dy * dy

    def f(x, y):
        if length_sq == 0:
            return math.hypot(x - x1, y - y1) - half
        t = ((x - x1) * dx + (y - y1) * dy) / length_sq
        t = max(0.0, min(1.0, t))
        return math.hypot(x - (x1 + t * dx), y - (y1 + t * dy)) - half
    return f


def _poly(points):
    def f(x, y):
        inside = False
        n = len(points)
        for i in range(n):
            x1, y1 = points[i]
            x2, y2 = points[(i + 1) % n]
            if (y1 > y) != (y2 > y):
                xin = (x2 - x1) * (y - y1) / (y2 - y1) + x1
                if x < xin:
                    inside = not inside
        return -1.0 if inside else 1.0
    return f


def _union(*shapes):
    def f(x, y):
        return min(s(x, y) for s in shapes)
    return f


def render(shapes, size=SIZE, ss=SS):
    """SDF 并集 -> 4x4 超采样覆盖率 -> RGBA（白色，alpha 为覆盖率）。"""
    rgba = bytearray(size * size * 4)
    step = 1.0 / ss
    offsets = [(i + 0.5) * step for i in range(ss)]
    total = ss * ss
    for py in range(size):
        for px in range(size):
            hits = 0
            for oy in offsets:
                for ox in offsets:
                    if min(s(px + ox, py + oy) for s in shapes) <= 0.0:
                        hits += 1
            if hits:
                d = (py * size + px) * 4
                rgba[d] = 255
                rgba[d + 1] = 255
                rgba[d + 2] = 255
                rgba[d + 3] = round(255 * hits / total)
    return rgba


def build_category():
    """分类：2x2 宫格（圆角方块），呼应既有实心圆角风格。"""
    m, s, gap = 6.0, 20.0, 4.0
    hw = s / 2.0
    r = 6.0
    centres = [(m + hw, m + hw), (m + s + gap + hw, m + hw),
               (m + hw, m + s + gap + hw), (m + s + gap + hw, m + s + gap + hw)]
    return [_rrect(cx, cy, hw, hw, r) for cx, cy in centres]


def build_cart():
    """购物车：把手 + 梯形篮体 + 两个轮子。"""
    basket = _poly([(15.0, 16.0), (53.0, 16.0), (46.0, 36.0), (22.0, 36.0)])
    handle = _union(
        _segment(4.0, 9.0, 13.0, 9.0, 5.0),
        _segment(13.0, 9.0, 18.0, 19.0, 5.0),
    )
    wheels = _union(_circle(26.0, 45.5, 4.5), _circle(42.0, 45.5, 4.5))
    return [basket, handle, wheels]


# ---------------------------------------------------------------- 主流程

def main():
    if not os.path.isdir(TABBAR_DIR):
        sys.exit(f'找不到输出目录: {TABBAR_DIR}')
    if not os.path.isdir(SOURCE_DIR):
        sys.exit(f'找不到参考图标目录: {SOURCE_DIR}')

    # 1) 复用既有形状，整体换成品牌色
    #    home -> 首页 | list -> 订单 | mine -> 我的
    reuse = {'home-icon': 'home', 'list-icon': 'order', 'mine-icon': 'mine'}
    produced = []

    for src_stem, dst_stem in reuse.items():
        for suffix, color in (('', COLOR_NORMAL), ('-h', COLOR_SELECTED)):
            src = os.path.join(SOURCE_DIR, f'{src_stem}{suffix}.png')
            dst = os.path.join(TABBAR_DIR, f'{dst_stem}{suffix}.png')
            w, h, rgba = png_read(src)
            png_write(dst, w, h, recolor(rgba, color))
            produced.append(os.path.basename(dst))

    # 2) 从零绘制缺失的两个图标
    for stem, builder in (('category', build_category), ('cart', build_cart)):
        shapes = builder()
        base = render(shapes)
        for suffix, color in (('', COLOR_NORMAL), ('-h', COLOR_SELECTED)):
            dst = os.path.join(TABBAR_DIR, f'{stem}{suffix}.png')
            png_write(dst, SIZE, SIZE, recolor(base, color))
            produced.append(os.path.basename(dst))

    # 3) 清理不再使用的旧图标
    #    蓝色时代的 *-icon*（已改为 home/mine/order 命名）与 message-*（无对应 Tab）
    stale = [
        'home-icon.png', 'home-icon-h.png',
        'list-icon.png', 'list-icon-h.png',
        'mine-icon.png', 'mine-icon-h.png',
        'message-icon.png', 'message-icon-h.png',
    ]
    for name in stale:
        path = os.path.join(TABBAR_DIR, name)
        if os.path.exists(path):
            os.remove(path)
            produced.append(f'(removed) {name}')

    print('生成完成：')
    for name in sorted(produced):
        print('  ', name)


if __name__ == '__main__':
    main()
