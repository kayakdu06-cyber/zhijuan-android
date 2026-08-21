[CmdletBinding()]
param(
    [string]$MirrorRoot = 'D:\deepseekuser\work\zhijuan1-tests',
    [string[]]$GradleTasks = @(':core:test'),
    [switch]$SkipSync
)

# 织卷1 JVM 测试运行器（ASCII 镜像工作区）
#
# 背景：项目根目录按用户命名裁决固定为 D:\deepseekuser\projects\织卷1（中文路径）。
# Gradle 9.4.1 在 Windows 上运行测试 worker 时，会把 classpath argsfile 用 UTF-8 写出、
# 却按系统原生编码(GBK)读取，导致中文路径被读成乱码，测试类报 ClassNotFoundException
# （Gradle issue #30391/#30304）。主构建/assembleDebug 不受影响，JVM 测试受影响。
#
# 本脚本把当前项目快照复制到纯 ASCII 的镜像目录运行测试，镜像不包含缓存和 build，
# 避免配置缓存携带中文路径。测试证据与副本 HEAD 一一对应，回传方式：
#   1. 测试报告保留在镜像目录（本脚本输出路径）；
#
# 用法（在仓库根目录执行）：
#   .\scripts\run-jvm-tests-ascii-mirror.ps1
#   .\scripts\run-jvm-tests-ascii-mirror.ps1 -GradleTasks ':core:test',':data:testDebugUnitTest',':app:testDebugUnitTest'
#   .\scripts\run-jvm-tests-ascii-mirror.ps1 -SkipSync   # 复用已有镜像（手动确认同步）

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$expectedRoot = 'D:\deepseekuser\projects\织卷1'
$actualRoot = (Resolve-Path -LiteralPath $projectRoot).Path
if ($actualRoot -cne $expectedRoot) {
    throw "This runner must be invoked from '$expectedRoot', got '$actualRoot'."
}
$allowedMirrorParent = 'D:\deepseekuser\work'
$mirrorFullPath = [IO.Path]::GetFullPath($MirrorRoot)
$allowedPrefix = [IO.Path]::GetFullPath($allowedMirrorParent).TrimEnd('\') + '\'
if (-not $mirrorFullPath.StartsWith($allowedPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "MirrorRoot must be a child of '$allowedMirrorParent', got '$mirrorFullPath'."
}

if (-not $SkipSync) {
    if (Test-Path -LiteralPath $MirrorRoot) {
        $mirrorItem = Get-Item -Force -LiteralPath $MirrorRoot
        if ($mirrorItem.LinkType) { throw "MirrorRoot must not be a link: $MirrorRoot" }
        Remove-Item -LiteralPath $MirrorRoot -Recurse -Force
    }
    New-Item -ItemType Directory -Path $MirrorRoot -Force | Out-Null
    # 只复制项目文件；缓存和 build 不进镜像，防止配置缓存携带中文路径。
    robocopy $projectRoot $MirrorRoot /E /NFL /NDL /NJH /NP `
        /XD .gradle .kotlin build `
        /XF local.properties *.jks *.keystore *.p12 *.pem *.key | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "Mirror copy failed (robocopy exit $LASTEXITCODE)." }
}

$env:GRADLE_USER_HOME = 'D:\deepseekuser\.cache\gradle'
$env:ANDROID_USER_HOME = 'D:\deepseekuser\.cache\android-user'
$env:ANDROID_AVD_HOME = 'D:\deepseekuser\.cache\android-user\avd'
$env:ANDROID_EMULATOR_HOME = 'D:\deepseekuser\.cache\android-user'
$env:TEMP = 'D:\deepseekuser\.cache\temp'
$env:TMP = 'D:\deepseekuser\.cache\temp'

Push-Location $MirrorRoot
try {
    & '.\gradlew.bat' --no-daemon --offline --console=plain @GradleTasks
    if ($LASTEXITCODE -ne 0) {
        throw "Mirror test run failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Output 'ASCII_MIRROR_TESTS_OK'
Write-Output "MIRROR_ROOT=$MirrorRoot"
Write-Output "TASKS=$($GradleTasks -join ',')"
