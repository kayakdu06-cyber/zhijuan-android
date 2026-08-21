# 织卷1 方案A 工具链环境（2026-08-16）
# 用法（在当前 PowerShell 进程中生效）：
#   . .\scripts\env-zhijuan1-toolchain.ps1
# 之后的所有 gradlew / adb / emulator / sdkmanager 调用都只使用 D:\deepseekuser 下的自治工具链。
# 本脚本只设置环境变量，不写 D:\gptuser，不修改任何文件。

$ErrorActionPreference = 'Stop'

$env:JAVA_HOME          = 'D:\deepseekuser\tools\jdk-17'
$env:ANDROID_HOME       = 'D:\deepseekuser\tools\android-sdk'
$env:ANDROID_SDK_ROOT   = 'D:\deepseekuser\tools\android-sdk'
$env:ANDROID_USER_HOME  = 'D:\deepseekuser\.cache\android-user'
$env:ANDROID_EMULATOR_HOME = 'D:\deepseekuser\.cache\android-user'
$env:ANDROID_AVD_HOME   = 'D:\deepseekuser\.cache\android-user\avd'
$env:GRADLE_USER_HOME   = 'D:\deepseekuser\.cache\gradle'

# PATH 只前置，不清空系统其他路径
$jdkBin = Join-Path $env:JAVA_HOME 'bin'
$sdkPlatformTools = Join-Path $env:ANDROID_HOME 'platform-tools'
$sdkCmdlineTools = Join-Path $env:ANDROID_HOME 'cmdline-tools\latest\bin'
foreach ($p in @($jdkBin, $sdkPlatformTools, $sdkCmdlineTools)) {
    if (Test-Path -LiteralPath $p) {
        if (($env:PATH -split ';') -notcontains $p) {
            $env:PATH = "$p;$env:PATH"
        }
    }
}

Write-Host ("ZHIJUAN1-TOOLCHAIN: JAVA_HOME={0} ANDROID_HOME={1} GRADLE_USER_HOME={2} ANDROID_USER_HOME={3} ANDROID_AVD_HOME={4}" -f `
    $env:JAVA_HOME, $env:ANDROID_HOME, $env:GRADLE_USER_HOME, $env:ANDROID_USER_HOME, $env:ANDROID_AVD_HOME)
