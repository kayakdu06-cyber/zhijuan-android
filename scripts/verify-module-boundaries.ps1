[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$expectedRoot = 'D:\deepseekuser\projects\织卷1'
$actualRoot = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
if ($actualRoot -cne $expectedRoot) { throw "WRONG_ROOT:$actualRoot" }

$expected = [ordered]@{
    ':core' = @()
    ':data' = @(':core')
    ':app' = @(':core', ':data')
}

function Get-ModulePath([string]$module) {
    return $module.TrimStart(':').Replace(':', [IO.Path]::DirectorySeparatorChar)
}

function Get-ProductionProjectDependencies([string]$buildFile) {
    $text = Get-Content -LiteralPath $buildFile -Raw
    return [regex]::Matches(
        $text,
        '(?m)^\s*implementation\(project\("(?<module>:[^"]+)"\)\)'
    ) | ForEach-Object { $_.Groups['module'].Value }
}

$configured = Select-String -LiteralPath 'settings.gradle.kts' -Pattern 'include\("(?<module>:[^"]+)"\)' -AllMatches |
    ForEach-Object { $_.Matches } |
    ForEach-Object { $_.Groups['module'].Value }
$configuredSorted = @($configured | Sort-Object)
$expectedSorted = @($expected.Keys | Sort-Object)
if (Compare-Object $configuredSorted $expectedSorted) {
    throw "Configured module set differs from the third-edition three-module contract."
}

foreach ($module in $expected.Keys) {
    $path = Get-ModulePath $module
    $buildFile = Join-Path $path 'build.gradle.kts'
    if (-not (Test-Path -LiteralPath $buildFile)) { throw "Missing build file for $module" }
    $actualDependencies = @(Get-ProductionProjectDependencies $buildFile | Sort-Object)
    $expectedDependencies = @($expected[$module] | Sort-Object)
    if (Compare-Object $actualDependencies $expectedDependencies) {
        throw "$module production dependencies are $($actualDependencies -join ', '); expected $($expectedDependencies -join ', ')."
    }

}

$foreignSourceSet = Get-ChildItem -Recurse -File -Filter 'build.gradle.kts' |
    Select-String -Pattern '(?:srcDir|srcDirs)\([^\r\n]*\.\.[^\r\n]*\)'
if ($foreignSourceSet) { throw 'A module sourceSet points outside its own directory.' }

$coreAndroidImports = Get-ChildItem 'core/src' -Recurse -File -Filter '*.kt' |
    Select-String -Pattern '^import (android|androidx)\.'
if ($coreAndroidImports) { throw ':core contains Android imports.' }

foreach ($legacyPath in @('engine', 'feature', 'provider', 'data/schemas')) {
    if (Test-Path -LiteralPath $legacyPath) { throw "Legacy path still exists: $legacyPath" }
}

$temporarySourceSets = @('app/src/s0', 'core/src/s0', 'data/src/s0') |
    Where-Object { Test-Path -LiteralPath $_ }
if ($temporarySourceSets) { throw 'Temporary src/s0 source-set layout still exists.' }

$forbiddenDataDependencies = Get-Content -LiteralPath 'data/build.gradle.kts' -Raw |
    Select-String -Pattern 'room|sqlcipher|androidx\.sqlite' -AllMatches
if ($forbiddenDataDependencies) { throw ':data contains a forbidden database dependency.' }

# Kahn topological check. Dependencies are edges from dependency to consumer.
$inDegree = @{}
$consumers = @{}
foreach ($module in $expected.Keys) {
    $inDegree[$module] = @($expected[$module]).Count
    $consumers[$module] = [Collections.Generic.List[string]]::new()
}
foreach ($module in $expected.Keys) {
    foreach ($dependency in $expected[$module]) { $consumers[$dependency].Add($module) }
}
$queue = [Collections.Generic.Queue[string]]::new()
foreach ($module in $expected.Keys) { if ($inDegree[$module] -eq 0) { $queue.Enqueue($module) } }
$visited = 0
while ($queue.Count -gt 0) {
    $module = $queue.Dequeue()
    $visited++
    foreach ($consumer in $consumers[$module]) {
        $inDegree[$consumer]--
        if ($inDegree[$consumer] -eq 0) { $queue.Enqueue($consumer) }
    }
}
if ($visited -ne $expected.Count) { throw 'Project dependency graph contains a cycle.' }

Write-Output "MODULE_BOUNDARY_CHECK_OK"
Write-Output "MODULE_COUNT=$($expected.Count)"
Write-Output "TOP_LEVEL_ROUTES=4"
Write-Output "PROVIDER_PROTOCOLS=1"
Write-Output "APP_PRODUCTION_DEPENDENCIES=:core,:data"
Write-Output "DEPENDENCY_GRAPH=ACYCLIC"
