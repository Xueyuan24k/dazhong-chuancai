# 给 APK 写入 dex、对齐、签名（Windows / PowerShell 7）
#
# 用法：
#   pwsh tools/sign.ps1                          # 首次会自动生成 keystore
#   pwsh tools/sign.ps1 -KsPass 你的口令 -KeyPass 你的口令 -Alias mykey
#
# 说明：
#   仓库里不放任何 keystore / 口令。首次运行会在 tools/ 下生成 keystore.jks，
#   之后请自己保管好——换签名会导致已安装用户无法覆盖升级。
#
# 产物：dist/大众川菜.apk

param(
    [string]$Jdk = $env:JAVA_HOME,
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "" }),
    [string]$Ks = "",
    [string]$Alias = "dzc",
    [string]$KsPass = $(if ($env:DZC_KS_PASS) { $env:DZC_KS_PASS } else { "changeit" }),
    [string]$KeyPass = "",
    [string]$OutApk = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Build = Join-Path $Root "app\build"
if (-not $Ks) { $Ks = Join-Path $PSScriptRoot "keystore.jks" }
if (-not $KeyPass) { $KeyPass = $KsPass }
if (-not $OutApk) { $OutApk = Join-Path $Root "dist\大众川菜.apk" }

function Fail($msg) { Write-Host "× $msg" -ForegroundColor Red; exit 1 }

if (-not $Jdk) { Fail "未设 JAVA_HOME，且没有传 -Jdk" }
if (-not $Sdk) { Fail "未设 ANDROID_HOME / ANDROID_SDK_ROOT，且没有传 -Sdk" }
if (-not (Test-Path (Join-Path $Build "base.apk"))) { Fail "缺少 app\build\base.apk，先运行 pwsh tools/build.ps1" }
if (-not (Test-Path (Join-Path $Build "classes.dex"))) { Fail "缺少 classes.dex，先运行 pwsh tools/build.ps1" }

$BT = Join-Path $Sdk "build-tools\34.0.0"
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$BT;$env:PATH"

Write-Host "== 写入 classes.dex =="
$py = @"
import zipfile, os
B = r'$Build'
src = os.path.join(B, 'base.apk')
dst = os.path.join(B, 'withdex.apk')
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for it in zin.infolist():
        zout.writestr(it, zin.read(it.filename))
    zout.write(os.path.join(B, 'classes.dex'), 'classes.dex')
print('apk entries:', len(zipfile.ZipFile(dst).namelist()))
"@
python -c $py
if ($LASTEXITCODE -ne 0) { Fail "写入 dex 失败（需要 python3）" }

Write-Host "== 对齐 =="
& "$BT\zipalign.exe" -f -p 4 (Join-Path $Build "withdex.apk") (Join-Path $Build "aligned.apk")

if (-not (Test-Path $Ks)) {
    Write-Host "== 生成 keystore：$Ks =="
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore $Ks -alias $Alias -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass $KsPass -keypass $KeyPass `
        -dname "CN=Doubao, OU=Cook, O=Doubao, L=Lianyungang, C=CN"
}

Write-Host "== 签名 =="
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutApk) | Out-Null
& "$BT\apksigner.bat" sign --ks $Ks --ks-pass "pass:$KsPass" --key-pass "pass:$KeyPass" `
    --out $OutApk (Join-Path $Build "aligned.apk")
& "$BT\apksigner.bat" verify --print-certs $OutApk | Select-Object -First 3
Write-Host ("APK: " + [math]::Round((Get-Item $OutApk).Length / 1MB, 1) + " MB -> $OutApk")
