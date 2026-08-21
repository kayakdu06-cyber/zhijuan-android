[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$expectedRoot = 'D:\deepseekuser\projects\织卷1'
$projectRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path

function Assert-True {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Code
    )
    if (-not $Condition) { throw $Code }
}

function Assert-File {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    Assert-True -Condition (Test-Path -LiteralPath (Join-Path $projectRoot $RelativePath) -PathType Leaf) -Code "MISSING_FILE:$RelativePath"
}

function Assert-ExistingPath {
    param([Parameter(Mandatory = $true)][string]$RelativePath)
    Assert-True -Condition (Test-Path -LiteralPath (Join-Path $projectRoot $RelativePath)) -Code "MISSING_PATH:$RelativePath"
}

Assert-True -Condition ($projectRoot -ceq $expectedRoot) -Code "WRONG_ROOT:$projectRoot"
Push-Location $projectRoot
try {
    $requiredFiles = @(
        'AGENTS.md',
        '.gitignore',
        'README.md',
        'settings.gradle.kts',
        'build.gradle.kts',
        'gradle.properties',
        'gradle\libs.versions.toml',
        'gradle\wrapper\gradle-wrapper.jar',
        'gradle\wrapper\gradle-wrapper.properties',
        'gradlew',
        'gradlew.bat',
        'local.properties',
        'THIRD_PARTY_NOTICES.md',
        'app\build.gradle.kts',
        'core\build.gradle.kts',
        'data\build.gradle.kts',
        'app\src\main\AndroidManifest.xml',
        'data\src\main\AndroidManifest.xml',
        'app\src\main\kotlin\app\zhijuan\reader\MainActivity.kt',
        'app\src\main\kotlin\app\zhijuan\reader\S0App.kt',
        'core\src\main\kotlin\app\zhijuan\core\s0\S0Domain.kt',
        'core\src\main\kotlin\app\zhijuan\core\s0\S0GenerationCoordinator.kt',
        'core\src\main\kotlin\app\zhijuan\core\s0\S1ProviderContract.kt',
        'data\src\main\kotlin\app\zhijuan\data\s0\FileS0NovelRepository.kt',
        'data\src\main\kotlin\app\zhijuan\data\s0\provider\AndroidKeystoreS1SecretStore.kt',
        'data\src\main\kotlin\app\zhijuan\data\s0\provider\OpenAiCompatibleS1Provider.kt',
        'branding\selected\zhijuan-logo-draft.png',
        'app\src\main\res\drawable-nodpi\zhijuan_logo_draft.png',
        'docs\ai\PROJECT-STATE.json',
        'docs\ai\NODE-REPORT-INDEX.md',
        'docs\ai\NODE-REPORT-V3-S0.md',
        'docs\ai\NODE-REPORT-V3-S1.md',
        'docs\ai\NODE-REPORT-V3-SEPARATION.md',
        'docs\ai\NODE-REPORT-V3-S4.md',
        'docs\ai\NODE-REPORT-V3-S5.md',
        'docs\ai\TEST-EVIDENCE-S4-V3.md',
        'docs\ai\TEST-EVIDENCE-S5-V3.md',
        'docs\ai\HANDOFF-V3.md',
        'docs\ai\TEST-EVIDENCE-S0-V3.md',
        'docs\ai\TEST-EVIDENCE-S1-V3.md',
        'scripts\verify-module-boundaries.ps1',
        'scripts\security-scan.ps1',
        'scripts\verify-backup-exclusions.ps1'
    )
    foreach ($file in $requiredFiles) { Assert-File -RelativePath $file }

    Assert-True -Condition (Test-Path -LiteralPath '.git' -PathType Container) -Code 'GIT_METADATA_MISSING'
    foreach ($path in @('.gitnexus', 'engine', 'feature', 'provider', 'data\schemas')) {
        Assert-True -Condition (-not (Test-Path -LiteralPath $path)) -Code "FORBIDDEN_PATH:$path"
    }
    foreach ($path in @('app\src\s0', 'core\src\s0', 'data\src\s0')) {
        Assert-True -Condition (-not (Test-Path -LiteralPath $path)) -Code "TEMPORARY_SOURCE_SET:$path"
    }
    foreach ($path in @('app\src\main', 'app\src\test', 'app\src\androidTest', 'core\src\main', 'core\src\test', 'data\src\main', 'data\src\test', 'data\src\androidTest')) {
        Assert-True -Condition (Test-Path -LiteralPath $path -PathType Container) -Code "MISSING_SOURCE_SET:$path"
    }

    $settings = Get-Content -LiteralPath 'settings.gradle.kts' -Raw
    $modules = @([regex]::Matches($settings, 'include\("(?<module>:[^"]+)"\)') | ForEach-Object { $_.Groups['module'].Value } | Sort-Object)
    Assert-True -Condition (-not (Compare-Object $modules @(':app', ':core', ':data'))) -Code 'MODULE_SET_MISMATCH'

    $appSource = Get-Content -LiteralPath 'app\src\main\kotlin\app\zhijuan\reader\S0App.kt' -Raw
    $routeMatch = [regex]::Match($appSource, '(?s)(?:private|internal) enum class S0Route\s*\{(?<body>.*?)\}')
    Assert-True -Condition $routeMatch.Success -Code 'ROUTE_ENUM_MISSING'
    $routes = @($routeMatch.Groups['body'].Value -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    Assert-True -Condition ($routes.Count -eq 4) -Code "TOP_LEVEL_ROUTE_COUNT:$($routes.Count)"

    $providerDefinitions = @(Get-ChildItem -LiteralPath 'core\src\main' -Recurse -File -Filter '*.kt' |
        Select-String -Pattern '^interface S0TextGenerationProvider\s*\{' -CaseSensitive)
    Assert-True -Condition ($providerDefinitions.Count -eq 1) -Code "PROVIDER_PROTOCOL_COUNT:$($providerDefinitions.Count)"

    $dependencyText = (Get-Content -LiteralPath 'gradle\libs.versions.toml' -Raw) + "`n" + (Get-Content -LiteralPath 'data\build.gradle.kts' -Raw)
    Assert-True -Condition ($dependencyText -notmatch '(?i)androidx\.room|room-runtime|sqlcipher|work-runtime|vector(?:db|store)|\brag\b') -Code 'FORBIDDEN_DEPENDENCY'

    $localProperties = Get-Content -LiteralPath 'local.properties' -Raw
    Assert-True -Condition ($localProperties -match '(?m)^sdk\.dir=D\\:\\\\deepseekuser\\\\tools\\\\android-sdk\s*$') -Code 'SDK_PATH_OUTSIDE_DEEPSEEK_ROOT'

    $state = Get-Content -LiteralPath 'docs\ai\PROJECT-STATE.json' -Raw | ConvertFrom-Json
    Assert-True -Condition ($state.projectRoot -eq 'D:/deepseekuser/projects/织卷1') -Code 'STATE_PROJECT_ROOT_MISMATCH'
    Assert-True -Condition ($state.productName -eq '织卷') -Code 'STATE_PRODUCT_NAME_MISMATCH'
    Assert-True -Condition ($state.versionControl -eq 'GIT_PUBLIC') -Code 'STATE_VERSION_CONTROL_MISMATCH'
    Assert-True -Condition (@($state.modules).Count -eq 3) -Code 'STATE_MODULE_COUNT_MISMATCH'
    Assert-True -Condition ($state.topLevelRoutes -eq 4) -Code 'STATE_ROUTE_COUNT_MISMATCH'
    Assert-True -Condition ($state.providerProtocols -eq 1) -Code 'STATE_PROVIDER_PROTOCOL_COUNT_MISMATCH'
    foreach ($reportProperty in $state.reports.PSObject.Properties) {
        if ($reportProperty.Name -notmatch 'Apk$') {
            Assert-ExistingPath -RelativePath $reportProperty.Value
        }
    }
    foreach ($nodeId in $state.completedNodes) {
        $nodeReport = "docs\ai\NODE-REPORT-$nodeId.md"
        Assert-File -RelativePath $nodeReport
        $nodeReportText = Get-Content -LiteralPath $nodeReport -Raw
        Assert-True -Condition ($nodeReportText -match '(?m)^marker:\s*V[0-9]+-NODE-REPORT\s*$') -Code "NODE_REPORT_MARKER_MISSING:$nodeId"
        Assert-True -Condition ($nodeReportText -match "(?m)^node_id:\s*$([regex]::Escape($nodeId))\s*$") -Code "NODE_REPORT_ID_MISMATCH:$nodeId"
    }
    if ($state.currentStatus -eq 'COMPLETE') {
        Assert-True -Condition (@($state.completedNodes) -contains $state.currentNode) -Code 'CURRENT_NODE_NOT_COMPLETED'
        Assert-True -Condition (@($state.pendingGates).Count -eq 0) -Code 'FINAL_STATE_HAS_PENDING_GATES'
    } else {
        Assert-True -Condition (@($state.pendingGates) -contains $state.currentNode) -Code 'CURRENT_NODE_NOT_PENDING'
    }

    $brandHash = (Get-FileHash -Algorithm SHA256 -LiteralPath 'branding\selected\zhijuan-logo-draft.png').Hash
    $appLogoHash = (Get-FileHash -Algorithm SHA256 -LiteralPath 'app\src\main\res\drawable-nodpi\zhijuan_logo_draft.png').Hash
    Assert-True -Condition ($brandHash -eq $appLogoHash) -Code 'BRAND_ASSET_HASH_MISMATCH'
    Assert-True -Condition ($brandHash -eq $state.verified.brandAssetSha256) -Code 'STATE_BRAND_HASH_MISMATCH'
    if ($state.currentStatus -eq 'COMPLETE') {
        foreach ($artifact in @(
            @{ Path = 'app\build\outputs\apk\debug\app-debug.apk'; StateHash = $state.verified.debugApkSha256; Code = 'DEBUG_APK_HASH_MISMATCH' },
            @{ Path = 'app\build\outputs\apk\release\app-release.apk'; StateHash = $state.verified.releaseApkSha256; Code = 'RELEASE_APK_HASH_MISMATCH' }
        )) {
            if (Test-Path -LiteralPath $artifact.Path -PathType Leaf) {
                $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $artifact.Path).Hash
                Assert-True -Condition ($actualHash -eq $artifact.StateHash) -Code $artifact.Code
            }
        }
    }

    $boundaryOutput = @(& (Join-Path $PSScriptRoot 'verify-module-boundaries.ps1'))
    Assert-True -Condition ($boundaryOutput -contains 'MODULE_BOUNDARY_CHECK_OK') -Code 'MODULE_BOUNDARY_SCRIPT_FAILED'

    Write-Output 'PROJECT_INTEGRITY_CHECK_OK'
    Write-Output 'PROJECT_ROOT=D:\deepseekuser\projects\织卷1'
    Write-Output 'VERSION_CONTROL=GIT_PUBLIC'
    Write-Output 'MODULES=:app,:core,:data'
    Write-Output 'TOP_LEVEL_ROUTES=4'
    Write-Output 'PROVIDER_PROTOCOLS=1'
    Write-Output 'SOURCE_LAYOUT=STANDARD'
    Write-Output 'LEGACY_PROJECT_PATHS=ABSENT'
    Write-Output "BRAND_SHA256=$brandHash"
    Write-Output "CURRENT_NODE=$($state.currentNode)"
    Write-Output "CURRENT_STATUS=$($state.currentStatus)"
}
finally {
    Pop-Location
}
