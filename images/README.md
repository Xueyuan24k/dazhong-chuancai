# 菜品图放这里

仓库不附带 278 张菜品成品图（AI 生成，压缩后约 20 MB）。

想构建带图的完整版，请把图片按**菜名**命名放到本目录，例如：

```
images/回锅肉.jpg
images/麻婆豆腐.jpg
```

然后运行：

```powershell
python tools/pack_images.py     # 生成 data/food_images.json
python tools/build_app.py       # 生成带图的 dist/index.html
```

脚本会统一压到长边 820px、JPEG 质量 70。三处文件名与菜名不一致的别名已在 `tools/pack_images.py` 的 `ALIAS` 里处理（`麻辣豆腐→麻婆豆腐`、`豆瓣鱼→豆瓣鲜鱼`）。

不带图也能构建，只是详情页没有配图。
