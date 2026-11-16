param(
    [string]$Find = "",
    [switch]$Tap,
    [switch]$Dump,
    [string]$InputText = "",
    [switch]$Back,
    [string]$Adb = $env:ADB,
    [string]$Device = $env:ADB_DEVICE,
    [string]$UiPath = ""
)

$ErrorActionPreference = 'Continue'
if (-not $Adb) {
    $found = Get-Command adb -ErrorAction SilentlyContinue
    if ($found) { $Adb = $found.Source }
}
if (-not $Adb) {
    Write-Output "未找到 adb：请设置环境变量 ADB，或把 adb 加入 PATH。"
    exit 1
}
if (-not $UiPath) { $UiPath = Join-Path $env:TEMP 'yidu_ui.xml' }
$deviceArgs = @()
if ($Device) { $deviceArgs = @('-s', $Device) }

if ($Back) {
    & $Adb @deviceArgs shell input keyevent 4 | Out-Null
    Write-Output "BACK"
    exit 0
}
if ($InputText -ne "") {
    $escaped = $InputText.Replace(' ', '%s')
    & $Adb @deviceArgs shell input text $escaped | Out-Null
    Write-Output "TYPED: $InputText"
    exit 0
}

& $Adb @deviceArgs shell uiautomator dump /sdcard/window.xml *> $null
& $Adb @deviceArgs pull /sdcard/window.xml $UiPath *> $null
if (-not (Test-Path $UiPath)) {
    Write-Output "UI dump failed：请确认手机已连接，并设置 ADB_DEVICE（多设备时）。"
    exit 1
}
[xml]$ui = Get-Content $UiPath -Encoding UTF8 -Raw

$nodes = $ui.SelectNodes('//node')
$matches = @()
foreach ($node in $nodes) {
    if ($node.text -and $node.text -like "*$Find*") { $matches += $node }
}
if ($matches.Count -eq 0) {
    Write-Output "NOT FOUND: $Find"
    if ($Dump) {
        foreach ($node in $nodes) {
            $hasText = $node.text -and $node.text.Trim() -ne ""
            $isField = $node.class -like "*EditText*" -or $node.class -like "*Button*"
            if ($hasText -or $isField) {
                Write-Output ("{0} | {1} | {2} | clickable={3}" -f $node.text, $node.bounds, $node.class, $node.clickable)
            }
        }
    }
    exit 2
}
$best = $matches[0]
foreach ($m in $matches) {
    if ($m.clickable -eq 'true') { $best = $m; break }
}
$bounds = [string]$best.bounds
$rx = [regex]'\[(\d+),(\d+)\]\[(\d+),(\d+)\]'
$mb = $rx.Match($bounds)
if ($mb.Success) {
    $x1 = [int]$mb.Groups[1].Value; $y1 = [int]$mb.Groups[2].Value
    $x2 = [int]$mb.Groups[3].Value; $y2 = [int]$mb.Groups[4].Value
    $cx = [int](($x1 + $x2) / 2); $cy = [int](($y1 + $y2) / 2)
    Write-Output ("FOUND: {0} | {1} | center=({2},{3}) clickable={4}" -f $best.text, $bounds, $cx, $cy, $best.clickable)
    if ($Tap) {
        & $Adb @deviceArgs shell input tap $cx $cy | Out-Null
        Write-Output "TAPPED ($cx,$cy)"
    }
}
if ($Dump) {
    foreach ($node in $nodes) {
        $hasText = $node.text -and $node.text.Trim() -ne ""
        $isField = $node.class -like "*EditText*" -or $node.class -like "*Button*"
        if ($hasText -or $isField) {
            Write-Output ("{0} | {1} | {2} | clickable={3}" -f $node.text, $node.bounds, $node.class, $node.clickable)
        }
    }
}
