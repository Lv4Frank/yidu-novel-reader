param(
    [string]$KeystorePath = "",
    [string]$Alias = "yidu",
    [string]$Dname = "CN=YiDu Novel, OU=App, O=YiDu, L=Shanghai, ST=Shanghai, C=CN"
)

# 生成用于签名 APK 的 keystore。密码只在本机输入，不会写入任何文件。
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $KeystorePath) {
    $KeystorePath = Join-Path $root 'keystore\yidu-release.jks'
}
$KeystorePath = [System.IO.Path]::GetFullPath($KeystorePath)

$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\keytool.exe'))) {
    $base = Join-Path $root '.tools\jdk'
    if (Test-Path $base) {
        foreach ($dir in (Get-ChildItem $base -Directory | Sort-Object Name -Descending)) {
            if (Test-Path (Join-Path $dir.FullName 'bin\keytool.exe')) { $jdk = $dir.FullName; break }
        }
    }
}
if (-not $jdk) { throw '未找到 JDK 17（keytool）。请设置 JAVA_HOME 或把 JDK 放在 .tools\jdk\ 下。' }
$keytool = Join-Path $jdk 'bin\keytool.exe'

if (Test-Path $KeystorePath) {
    throw "签名文件已存在：$KeystorePath。为避免覆盖现有签名密钥，本脚本不会覆盖；如确需重建，请先手动备份并删除。"
}
New-Item -ItemType Directory -Force -Path (Split-Path $KeystorePath) | Out-Null

$password = Read-Host '设置 keystore 密码（请自行妥善备份，不要提交到 Git）'
if ([string]::IsNullOrWhiteSpace($password)) { throw '密码不能为空' }
$confirm = Read-Host '再次输入密码'
if ($password -ne $confirm) { throw '两次输入的密码不一致' }

& $keytool -genkeypair -keystore $KeystorePath -alias $Alias -keyalg RSA -keysize 2048 `
    -validity 10000 -storepass $password -keypass $password -dname $Dname
if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }

Write-Host "已生成签名文件：$KeystorePath"
Write-Host '请把密码保存到密码管理器；构建时设置环境变量 YIDU_KS_PASS 再运行 build.ps1。'
Write-Host 'keystore/ 已被 .gitignore 忽略，请勿提交到 GitHub。'
