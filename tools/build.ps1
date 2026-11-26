# 构建 APK（Windows / PowerShell 7）
#
# 前置：
#   1) JDK 17           —— 设 JAVA_HOME，或用 -Jdk 指定
#   2) Android SDK      —— 设 ANDROID_HOME / ANDROID_SDK_ROOT，或用 -Sdk 指定
#      需要 platforms/android-34/android.jar 与 build-tools/34.0.0
#   3) 先跑 python tools/build_app.py 生成 dist/index.html（本脚本会检查）
#
# 用法：
#   pwsh tools/build.ps1
#   pwsh tools/build.ps1 -Jdk "C:\jdk-17" -Sdk "C:\Android\Sdk"
#
# 产物：app/build/base.apk（未签名，供 tools/sign.ps1 使用）

param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "" })
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$App = Join-Path $Root "app"
$Html = Join-Path $Root "dist\index.html"

function Fail($msg) { Write-Host "× $msg" -ForegroundColor Red; exit 1 }

if (-not $Jdk) { Fail "未设 JAVA_HOME，且没有传 -Jdk" }
if (-not $Sdk) { Fail "未设 ANDROID_HOME / ANDROID_SDK_ROOT，且没有传 -Sdk" }
if (-not (Test-Path (Join-Path $Jdk "bin\javac.exe"))) { Fail "JDK 路径不对：$Jdk" }
if (-not (Test-Path $Html)) { Fail "缺少 dist\index.html，先运行：python tools/build_app.py" }

$BT = Join-Path $Sdk "build-tools\34.0.0"
$AJAR = Join-Path $Sdk "platforms\android-34\android.jar"
if (-not (Test-Path (Join-Path $BT "aapt2.exe"))) { Fail "找不到 build-tools/34.0.0：$BT" }
if (-not (Test-Path $AJAR)) { Fail "找不到 platforms/android-34/android.jar" }

$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$BT;$env:PATH"

$Assets = Join-Path $App "assets"
$Build = Join-Path $App "build"
New-Item -ItemType Directory -Force -Path $Assets, $Build | Out-Null
Copy-Item -LiteralPath $Html -Destination (Join-Path $Assets "index.html") -Force

Write-Host "== 编译资源 =="
& "$BT\aapt2.exe" compile --dir (Join-Path $App "res") -o (Join-Path $Build "res.zip")

Write-Host "== 链接资源 =="
& "$BT\aapt2.exe" link -o (Join-Path $Build "base.apk") -I $AJAR `
    --manifest (Join-Path $App "AndroidManifest.xml") -A $Assets `
    --min-sdk-version 21 --target-sdk-version 34 (Join-Path $Build "res.zip")

Write-Host "== 编译 Java =="
Remove-Item (Join-Path $Build "classes") -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path (Join-Path $Build "classes") | Out-Null
$srcs = Get-ChildItem (Join-Path $App "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& "$Jdk\bin\javac.exe" --release 8 -encoding UTF-8 -classpath $AJAR -d (Join-Path $Build "classes") $srcs
Write-Host ("class files: " + (Get-ChildItem (Join-Path $Build "classes") -Recurse -Filter *.class).Count)

Write-Host "== 转 dex =="
Remove-Item (Join-Path $Build "classes.dex") -Force -ErrorAction SilentlyContinue
$cls = Get-ChildItem (Join-Path $Build "classes") -Recurse -Filter *.class | ForEach-Object { $_.FullName }
& "$BT\d8.bat" --lib $AJAR --min-api 21 --output $Build $cls
Write-Host ("dex: " + (Test-Path (Join-Path $Build "classes.dex")))
Write-Host "完成：$Build\base.apk（下一步 pwsh tools/sign.ps1）"
