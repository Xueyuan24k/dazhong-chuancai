# 大众川菜 · 离线选菜 App

把一九七九年四川人民出版社《大众川菜》里的菜，做成一个**完全离线**的手机选菜工具：278 道菜，每道有味料克重、做法、特点、附注，详情页滑一下就能看，随机点一道就能定今天吃什么。

- 单文件 HTML（图片、菜谱、术语全部内联），装进 WebView 即成一个 App
- 不联网、无广告、无统计、无账号；APK 里没有 `INTERNET` 权限
- 体积 15.6 MB，278 张菜品图全内嵌
- 目标版本 V2.1（`versionCode 21`）

---

## 功能

| 模块 | 说明 |
| --- | --- |
| 开场 | 用 1979 年初版红印章封面做「书页自右向左翻开」的动画，翻过即进主界面，可点击跳过 |
| 菜谱 | 按「肉食类 106 / 家禽水产类 102 / 蔬菜类 70」分类翻查，列表显示前四味用料 |
| 详情 | 横向三面板：配料（两列，红色克重）→ 做法（圆形编号步骤）→ 补充（特点 + 术语提示 + 附注 + 来源）。左右滑页，`←/→/Esc` 可退出 |
| 搜索 | 菜名、用料、分类、**做法原文**、术语一起比对；按接近程度排序；结果分「我的菜谱」与「菜谱」两块，命中字标红 |
| 随机选菜 | 老虎机式滚动 22 帧后定格，进详情页；返回键回主界面 |
| 我的菜谱 | 自己记的菜（可拍照、写备注），按分类归组；改动后静默覆盖写 `我的菜谱_自动备份.json`；可导出 A4 PDF（照片 + 菜名 + 分类 + 备注） |
| 致谢 | 左上角入口，说明本书编者、出版社，以及本软件的整理过程 |

## 数据规模

- 菜谱 **278** 道，用料全部折算为标准克重（`0` 条“几钱几分”残留）
- 术语 **41** 条（取自书末《方言、名词、术语解释》），**204** 道菜的正文命中术语，详情页自动挂提示
- 每道菜保留 `特点` 与 `附注` 原文；三版文字有出入时以修订版为准

## 目录结构

```
app/                   Android 壳（原生 WebView）
  AndroidManifest.xml  包名 com.dazhong.chuancai，minSdk 21，无 INTERNET 权限
  src/com/dazhong/chuancai/MainActivity.java
                       返回键、退出、另存文本/PDF、系统文件选择器
  res/mipmap-*/        应用图标（红章居中裁切）
web/app_template.html  单文件 App 模板（__COVER__ / __DATA__ / __TERMS__ 占位符）
data/recipes_all.json  278 道菜谱（结构化）
data/glossary.json     41 条术语
assets/cover.jpg       开场封面
assets/icon-512.png    图标源图
tools/build_app.py     数据 + 模板 → dist/index.html
tools/pack_images.py   images/*.jpg → data/food_images.json（压图 + base64）
tools/build.ps1        aapt2 / javac / d8 出未签名 APK
tools/sign.ps1         写 dex、zipalign、apksigner 出成品
docs/数据结构.md       字段说明
```

## 构建

需要：**Python 3.8+**、**JDK 17**、**Android SDK**（`platforms/android-34` 与 `build-tools/34.0.0`）。

```powershell
# 0) 配图（可选）。仓库不带 278 张图，把同名 jpg 放进 images/ 后：
python tools/pack_images.py            # → data/food_images.json

# 1) 生成单文件页面
python tools/build_app.py              # → dist/index.html

# 2) 出 APK（JAVA_HOME / ANDROID_HOME 先设好）
pwsh tools/build.ps1                   # → app/build/base.apk
pwsh tools/sign.ps1                    # → dist/大众川菜.apk
```

也可以只做网页版：`dist/index.html` 直接用浏览器打开即可，不需要服务器、不需要联网。
图省事不跑第 0 步也能构建，只是详情页没有配图。

签名脚本首次运行会在 `tools/keystore.jks` 生成密钥（默认口令 `changeit`，**请自行替换并妥善保管**）；仓库不附带任何密钥或口令。

## 发布到 GitHub

```bash
git init -b main
git add .
git commit -m "大众川菜 · 离线选菜 App V2.1"
git remote add origin https://github.com/<你的账号>/dazhong-chuancai.git
git push -u origin main
```

`.gitignore` 已排除 APK、构建产物、keystore 与 `images/*.jpg`。成品 APK 建议放 Releases，而不是直接提交进历史。


## 搜索是怎么排的

`web/app_template.html` 里的 `scoreDish()` / `scoreMine()`：

1. 菜名完全相同 `100` → 菜名开头 `92` → 菜名包含 `84` 起（越靠前越高）
2. 菜名字符按顺序命中（如「回锅」→「回锅肉」之外的松散匹配）`55`
3. 用料命中 `58`、分类命中 `34`、做法原文命中 `30`、术语命中 `24`
4. 都不中时按字符重合比例兜底，低于阈值直接丢弃
5. 结果按分数倒序；「我的菜谱」与「菜谱」分两块展示，命中片段标红

## 数据从哪来

原书是扫描件。三份 PDF 共 1028 页，先渲染成图再逐页 OCR，按坐标聚行重建版式，再按「用料—做法—特点—附注」切段，书名以书前目录为准做跨版本对齐，最后统一换算：`一斤 = 500 克`、`一两 = 50 克`、`一钱 = 5 克`、`一分 = 0.5 克`（1959 年后的市制十两秤，与今天一致）。

菜品配图为 AI 生成的示意图（白瓷盘、老木桌、自然侧光、45° 俯视），非原书照片。

## 致谢与声明

本书初版于一九七九年，由**刘建成、胡廉泉、舒孝钧、杨镜吾**等先生编写，**四川人民出版社**印行；其后**四川科学技术出版社**又出修订版。软件内全部菜谱文字——菜名、用料、做法、特点、附注——都出自这几位编者与这两家出版社的这部书，一字一句皆有来处。谨致诚挚谢意。

本软件只为方便做菜时翻查，**请勿用作商业用途收费**。菜谱文字与封面版权归原作者及出版社；代码部分见 [LICENSE](LICENSE)，书籍内容与图片见 [NOTICE.md](NOTICE.md)。

---

一菜一格，百菜百味。
