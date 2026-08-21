param(
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$expectedRoot = 'D:\deepseekuser\projects\织卷1'
$projectRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
if ($projectRoot -cne $expectedRoot) { throw "WRONG_ROOT:$projectRoot" }

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\deepseekuser\tools\jdk-17' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = 'D:\deepseekuser\tools\android-sdk' }
if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
$env:ANDROID_USER_HOME = 'D:\deepseekuser\.cache\android-user'
$env:GRADLE_USER_HOME = 'D:\deepseekuser\.cache\gradle'
$env:TEMP = 'D:\deepseekuser\.cache\temp'
$env:TMP = 'D:\deepseekuser\.cache\temp'

foreach ($directory in @(
    $env:ANDROID_USER_HOME,
    $env:GRADLE_USER_HOME,
    $env:TEMP,
    'D:\deepseekuser\.cache\gradle-tmp',
    'D:\deepseekuser\.cache\kotlin-tmp'
)) {
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
}

$gradleArguments = @(
    '--no-daemon',
    '--console=plain',
    '--no-configuration-cache',
    ':app:assembleDebug',
    ':app:assembleRelease'
)

if ($Offline) {
    $gradleArguments = @('--offline') + $gradleArguments
}

Push-Location $projectRoot
try {
    $integrityOutput = @(& (Join-Path $PSScriptRoot 'verify-project-integrity.ps1'))
    if ($integrityOutput -notcontains 'PROJECT_INTEGRITY_CHECK_OK') {
        throw 'Project integrity verification did not return its success marker.'
    }

    $testOutput = @(& (Join-Path $PSScriptRoot 'run-jvm-tests-ascii-mirror.ps1') `
        -GradleTasks ':core:test', ':data:testDebugUnitTest', ':app:testDebugUnitTest')
    if ($testOutput -notcontains 'ASCII_MIRROR_TESTS_OK') {
        throw 'ASCII mirror JVM tests did not return their success marker.'
    }

    & '.\gradlew.bat' @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle verification failed with exit code $LASTEXITCODE."
    }

    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'test-security-scan.ps1')
    if ($LASTEXITCODE -ne 0) {
        throw "Security scan regression tests failed with exit code $LASTEXITCODE."
    }

    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'security-scan.ps1') -ProjectRoot $projectRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Security scan failed with exit code $LASTEXITCODE."
    }

    & (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'verify-backup-exclusions.ps1') -ProjectRoot $projectRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Backup exclusion verification failed with exit code $LASTEXITCODE."
    }

    Write-Output 'ZHIJUAN_FULL_LOCAL_VERIFICATION_OK'
    $integrityOutput
    $testOutput
}
finally {
    Pop-Location
}
