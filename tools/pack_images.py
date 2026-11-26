#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 images/*.jpg 压成 data/food_images.json（{菜名: base64}），供 build_app.py 内联。

用法:
    python tools/pack_images.py                # 读 images/，写 data/food_images.json
    python tools/pack_images.py --side 820 --quality 70

说明:
    仓库不附带 278 张成品图（AI 生成，约 20 MB）。把同名 jpg 放进 images/
    （文件名 = 菜名，例如 images/回锅肉.jpg），再跑本脚本即可复现完整版。
"""

import argparse
import base64
import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMAGES = os.path.join(ROOT, "images")
OUT = os.path.join(ROOT, "data", "food_images.json")

# 菜名与图片文件名的少数别名
ALIAS = {"麻辣豆腐": "麻婆豆腐", "豆瓣鱼": "豆瓣鲜鱼"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--side", type=int, default=820, help="长边像素上限")
    ap.add_argument("--quality", type=int, default=70, help="JPEG 质量")
    args = ap.parse_args()

    from PIL import Image  # 仅打包图片时需要

    if not os.path.isdir(IMAGES):
        raise SystemExit("没有 images/ 目录，先把菜品图放进去（文件名用菜名，如 回锅肉.jpg）")

    recipes = json.load(open(os.path.join(ROOT, "data", "recipes_all.json"), encoding="utf-8"))
    names = [r["name"] for r in recipes]
    pool = {}
    for f in os.listdir(IMAGES):
        if f.lower().endswith((".jpg", ".jpeg", ".png")):
            pool[os.path.splitext(f)[0]] = os.path.join(IMAGES, f)

    out, miss = {}, []
    for name in names:
        key = ALIAS.get(name, name)
        path = pool.get(key) or pool.get(name)
        if not path:
            miss.append(name)
            continue
        im = Image.open(path).convert("RGB")
        w, h = im.size
        scale = args.side / max(w, h)
        if scale < 1:
            im = im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=args.quality, optimize=True, progressive=True)
        out[name] = base64.b64encode(buf.getvalue()).decode()

    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print("打包 %d 张 → %s（%.1f MB）" % (len(out), OUT, os.path.getsize(OUT) / 1048576))
    if miss:
        print("缺图 %d 张：%s" % (len(miss), "、".join(miss[:10]) + ("…" if len(miss) > 10 else "")))


if __name__ == "__main__":
    main()
