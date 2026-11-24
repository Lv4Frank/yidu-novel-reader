param(
    [switch]$SkipSign,
    [string]$OutName = "YiDuNovel-1.2.1.apk",
    [string]$JdkPath = "",
    [string]$SdkPath = "",
    [string]$KeystorePath = "",
    [string]$KeyAlias = "yidu"
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Resolve-Jdk {
    if ($JdkPath -and (Test-Path (Join-Path $JdkPath 'bin\javac.exe'))) { return (Resolve-Path $JdkPath).Path }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { return $env:JAVA_HOME }
    $base = Join-Path $root '.tools\jdk'
    if (Test-Path $base) {
        foreach ($dir in (Get-ChildItem $base -Directory | Sort-Object Name -Descending)) {
            if (Test-Path (Join-Path $dir.FullName 'bin\javac.exe')) { return $dir.FullName }
        }
    }
    throw '未找到 JDK 17。请安装 JDK 17 后设置 JAVA_HOME，或使用 -JdkPath 指定；也可以把 JDK 放在 .tools\jdk\ 下（该目录不会提交到仓库）。'
}

function Resolve-Sdk {
    if ($SdkPath -and (Test-Path (Join-Path $SdkPath 'platforms'))) { return (Resolve-Path $SdkPath).Path }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path (Join-Path $env:ANDROID_SDK_ROOT 'platforms'))) { return $env:ANDROID_SDK_ROOT }
    if ($env:ANDROID_HOME -and (Test-Path (Join-Path $env:ANDROID_HOME 'platforms'))) { return $env:ANDROID_HOME }
    $local = Join-Path $root '.tools\sdk'
    if (Test-Path (Join-Path $local 'platforms')) { return $local }
    throw '未找到 Android SDK。请安装 Android SDK Platform 35 + Build Tools 35，并设置 ANDROID_HOME / ANDROID_SDK_ROOT，或使用 -SdkPath 指定。'
}

$jdk = Resolve-Jdk
$sdk = Resolve-Sdk
$buildToolsRoot = Join-Path $sdk 'build-tools'
if (-not (Test-Path $buildToolsRoot)) { throw "Android SDK 中缺少 build-tools：$buildToolsRoot" }
$bt = Get-ChildItem $buildToolsRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
if ($bt -eq $null) { throw 'build-tools 目录为空' }
$bt = $bt.FullName

$platformRoot = Join-Path $sdk 'platforms'
$platform = Get-ChildItem $platformRoot -Directory -Filter 'android-*' |
    Sort-Object { [int]($_.Name -replace 'android-', '') } -Descending | Select-Object -First 1
if ($platform -eq $null) { throw '未找到 Android platform（需要 android-35 或更新版本）' }
$androidJar = Join-Path $platform.FullName 'android.jar'

$build = Join-Path $root 'build'
$app = Join-Path $root 'app'
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$javac = Join-Path $jdk 'bin\javac.exe'
$jar = Join-Path $jdk 'bin\jar.exe'

foreach ($tool in @($androidJar, (Join-Path $bt 'aapt2.exe'), (Join-Path $bt 'd8.bat'),
        (Join-Path $bt 'zipalign.exe'), (Join-Path $bt 'apksigner.bat'), $javac, $jar)) {
    if (-not (Test-Path $tool)) { throw "Missing build tool: $tool" }
}

if (Test-Path $build) { Remove-Item -Recurse -Force $build }
New-Item -ItemType Directory -Force -Path $build, "$build\gen", "$build\classes", "$build\dex", "$build\res" | Out-Null

Write-Host '==> aapt2 compile resources'
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $app 'res') -o "$build\res\resources.zip"
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '==> aapt2 link'
$linkArgs = @(
    '-o', "$build\base.apk",
    '-I', $androidJar,
    '--manifest', (Join-Path $app 'AndroidManifest.xml'),
    '-R', "$build\res\resources.zip",
    '--java', "$build\gen",
    '--auto-add-overlay'
)
$assets = Join-Path $app 'assets'
if (Test-Path $assets) { $linkArgs += @('-A', $assets) }
& (Join-Path $bt 'aapt2.exe') link @linkArgs
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '==> javac'
$sources = @()
$sources += (Get-ChildItem (Join-Path $app 'java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$sources += (Get-ChildItem "$build\gen" -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& $javac --release 11 -encoding UTF-8 -classpath $androidJar -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '==> d8'
$classFiles = @(Get-ChildItem "$build\classes" -Recurse -Filter '*.class' | ForEach-Object { $_.FullName })
& (Join-Path $bt 'd8.bat') --release --min-api 26 --lib $androidJar --output "$build\dex" $classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '==> package classes.dex'
& $jar --update --file "$build\base.apk" -C "$build\dex" classes.dex
if ($LASTEXITCODE -ne 0) { throw 'jar update failed' }

Write-Host '==> zipalign'
& (Join-Path $bt 'zipalign.exe') -f -p 4 "$build\base.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

if ($SkipSign) {
    Write-Host "Unsigned APK: $build\aligned.apk"
    exit 0
}

$keystore = if ($KeystorePath) { (Resolve-Path $KeystorePath).Path } else { Join-Path $root 'keystore\yidu-release.jks' }
$ksPass = $env:YIDU_KS_PASS
if (-not (Test-Path $keystore)) {
    Write-Warning "未找到签名文件：$keystore"
    Write-Warning '已生成未签名 APK。请运行 tools\create-keystore.ps1 创建自己的签名密钥，或用 -KeystorePath 指定已有 keystore。'
    Write-Host "Unsigned APK: $build\aligned.apk"
    exit 0
}
if ([string]::IsNullOrEmpty($ksPass)) {
    Write-Warning '未设置环境变量 YIDU_KS_PASS。为保护密钥，不为空密码签名。'
    Write-Warning '请先设置：$env:YIDU_KS_PASS = "你的密钥库密码"，然后重新运行。'
    Write-Host "Unsigned APK: $build\aligned.apk"
    exit 0
}

Write-Host '==> sign'
$outApk = Join-Path $root $OutName
& (Join-Path $bt 'apksigner.bat') sign --ks $keystore --ks-key-alias $KeyAlias `
    --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
    --out $outApk "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw 'apksigner failed' }

Write-Host '==> verify'
& (Join-Path $bt 'apksigner.bat') verify --print-certs $outApk
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

$size = [math]::Round((Get-Item $outApk).Length / 1MB, 2)
Write-Host "APK ready: $outApk ($size MB)"
