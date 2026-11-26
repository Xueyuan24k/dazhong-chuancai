#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 data/ 与 web/ 合成一个自包含的单文件 HTML（图片、封面、数据全部内联）。

用法:
    python tools/build_app.py            # 输出 dist/index.html
    可选环境变量:
    DZC_IMAGES_JSON  指定 food_images.json 路径（默认 data/food_images.json）

输入:
    data/recipes_all.json   278 道菜谱
    data/glossary.json      术语库
    data/food_images.json   {菜名: base64}，由 tools/pack_images.py 生成（缺失则不配图）
    assets/cover.jpg        开场翻页的封面图
    web/app_template.html   单文件 App 模板（含 __COVER__ / __DATA__ / __TERMS__ 占位符）
"""

import base64
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")
WEB = os.path.join(ROOT, "web")
ASSETS = os.path.join(ROOT, "assets")
DIST = os.path.join(ROOT, "dist")

BAD = re.compile(r"[钱兩两斤分]")


def load():
    recipes = json.load(open(os.path.join(DATA, "recipes_all.json"), encoding="utf-8"))
    gloss = json.load(open(os.path.join(DATA, "glossary.json"), encoding="utf-8"))
    return recipes, gloss


# OCR 残留的常见错字与粘连
ING_FIX = [("甜軒酱", "甜面酱"), ("軒酱", "面酱"), ("酸糟汁", "醪糟汁"), ("焗", ""),
           ("姜，葱各", "姜、葱"), ("葱，姜各", "姜、葱"), ("姜、蒜各", "姜、蒜")]


def cn_count(a):
    """计件用量里的中文数字转阿拉伯数字（不改动克重）"""
    m = re.match(r"^([一二两三四五六七八九十半]+|[0-9.]+)(余?)([\u4e00-\u9fa5]{1,2})$", a)
    if not m:
        return a
    num, extra, unit = m.group(1), m.group(2), m.group(3)
    if re.fullmatch(r"[0-9.]+", num) and not extra:
        return a
    cn = {"一": 1, "二": 2, "两": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7,
          "八": 8, "九": 9, "十": 10, "半": 0.5}
    v = cn.get(num, None)
    if v is None:
        try:
            v = float(num)
        except Exception:
            return a
    s = f"{v:g}"
    return f"{s}{extra}{unit}"


def clean_ing(ings):
    out, seen = [], set()
    for i in ings:
        n = i["n"]
        for a, b in ING_FIX:
            n = n.replace(a, b)
        note = i.get("note") or ""
        if n.endswith("各"):
            n = n.rstrip("各").strip("、，,")
            note = (note + "各半" if note else "各半")
        n = re.sub(r"[0-9]+[尾条]?约?$", "", n)
        n = re.sub(r"^[、，,：:\s]+|[、，,：:\s]+$", "", n)
        a = cn_count(i["a"].strip())
        if not n or n in seen:
            continue
        seen.add(n)
        out.append({"n": n, "a": a, "note": note})
    return out


def clean_text(s):
    s = (s or "").strip()
    return re.sub(r"^[：:·、，。；;\s]+", "", s)


def attach_terms(recipes, gloss):
    for r in recipes:
        blob = r["name"] + "|" + "|".join(i["n"] for i in r["ing"]) + "|" + \
               "|".join(r["steps"]) + "|" + (r["feat"] or "") + "|" + (r["note"] or "")
        hits, seen = [], set()
        for t in gloss:
            for k in t["k"]:
                if k and k in blob:
                    if t["t"] not in seen:
                        seen.add(t["t"])
                        hits.append({"t": t["t"], "d": t["d"]})
                    break
        r["terms"] = hits[:6]
    return recipes


def check(recipes):
    bad = []
    for r in recipes:
        for i in r["ing"]:
            if BAD.search(i["a"]):
                bad.append((r["name"], i["n"], i["a"]))
    print("非克重用量条数:", len(bad))
    for b in bad[:12]:
        print("  ", b)


ALIAS = {"麻辣豆腐": "麻婆豆腐", "豆瓣鱼": "豆瓣鲜鱼"}


def attach_img(recipes):
    path = os.environ.get("DZC_IMAGES_JSON", os.path.join(DATA, "food_images.json"))
    imgs = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    n = 0
    for r in recipes:
        key = ALIAS.get(r["name"], r["name"])
        b64 = imgs.get(key)
        if b64:
            r["img"] = "data:image/jpeg;base64," + b64
            n += 1
    print("带图菜数:", n, "（缺图不影响构建，只是详情页无配图）" if n < len(recipes) else "")
    return recipes


def build():
    recipes, gloss = load()
    for r in recipes:
        r["ing"] = clean_ing(r["ing"])
        r["steps"] = [clean_text(s) for s in r["steps"] if clean_text(s)]
        r["feat"] = clean_text(r["feat"])
        r["note"] = clean_text(r["note"])
    recipes = [r for r in recipes if len(r["ing"]) >= 2 and r["steps"]]
    recipes = attach_terms(recipes, gloss)
    recipes = attach_img(recipes)
    check(recipes)

    cover = base64.b64encode(open(os.path.join(ASSETS, "cover.jpg"), "rb").read()).decode()
    tpl = open(os.path.join(WEB, "app_template.html"), encoding="utf-8").read()
    data = json.dumps(recipes, ensure_ascii=False, separators=(",", ":"))
    terms = json.dumps(gloss, ensure_ascii=False, separators=(",", ":"))
    html = tpl.replace("__COVER__", "data:image/jpeg;base64," + cover) \
               .replace("__DATA__", data).replace("__TERMS__", terms)

    os.makedirs(DIST, exist_ok=True)
    out = os.path.join(DIST, "index.html")
    open(out, "w", encoding="utf-8").write(html)
    print("写出:", out, round(os.path.getsize(out) / 1024), "KB", "菜数", len(recipes),
          "带术语菜数", sum(1 for r in recipes if r["terms"]))


if __name__ == "__main__":
    build()
