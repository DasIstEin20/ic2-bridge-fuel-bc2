param(
    [string]$ModsDirectory = 'R:\Codex-misc\Mods',
    [string]$GradleCache = 'R:\Codex-misc\ic2 mods\transporter-1.20.1\.gradle-user-home',
    [string]$JavaDirectory = '',
    [string]$BuildCraftVersion = '8.0.13+1.20.1+forge',
    [string]$ForestryVersion = '1.20.1-2.10.2',
    [switch]$BuildJar,
    [switch]$WithoutOptionalMods
)

$projectDirectory = Split-Path -Parent $PSScriptRoot
if ([IO.Path]::GetPathRoot($projectDirectory) -ne 'R:\' -or [IO.Path]::GetPathRoot($GradleCache) -ne 'R:\') {
    throw 'This project requires build outputs and Gradle caches on drive R:.'
}
if (!$JavaDirectory) {
    $JavaDirectory = Join-Path $GradleCache 'jdks\eclipse_adoptium-17-amd64-windows\jdk-17.0.20.1+1'
}
if (!(Test-Path -LiteralPath (Join-Path $JavaDirectory 'bin\java.exe'))) {
    throw 'Pass -JavaDirectory pointing to an existing Java 17 installation.'
}
if (!(Select-String -LiteralPath (Join-Path $JavaDirectory 'release') -Pattern '^JAVA_VERSION="17\.' -Quiet)) {
    throw 'The integration suite must run on Java 17.'
}

$env:JAVA_HOME = $JavaDirectory
$env:GRADLE_USER_HOME = $GradleCache
$env:TEMP = Join-Path $projectDirectory 'build\tmp'
$env:TMP = $env:TEMP
New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir="' + $env:TEMP + '"'
$testLog = Join-Path $env:TEMP $(if ($WithoutOptionalMods) { 'base-suite.log' } else { 'integration-suite.log' })
$gradleArguments = @('compileJava', 'runGameTestServer', '--offline', '--no-daemon')
if ($WithoutOptionalMods) {
    $gradleArguments += '-PbaseIntegrationTest'
} else {
    $gradleArguments += @("-PintegrationModsDir=$ModsDirectory", "-PintegrationBuildCraftVersion=$BuildCraftVersion",
        "-PintegrationForestryVersion=$ForestryVersion")
}
if ($BuildJar) {
    $gradleArguments += @('jar', 'reobfJar')
}
Push-Location $projectDirectory
try {
    & '.\gradlew.bat' @gradleArguments *> $testLog
    $gradleExit = $LASTEXITCODE
    Get-Content -LiteralPath $testLog -Tail 30
    if ($gradleExit -ne 0) {
        throw "Gradle failed with exit code $gradleExit. See $testLog"
    }
    #Forge can return zero after a mod-loading failure, before running any tests.
    $expectedTests = if ($WithoutOptionalMods) { 6 } else { 20 }
    if (!(Select-String -LiteralPath $testLog -Pattern "All $expectedTests required tests passed" -Quiet)) {
        throw "Expected $expectedTests successful GameTests; check namespace filtering and failures in $testLog"
    }
    Write-Output "Integration tests passed. Full log: $testLog"
    if ($BuildJar) {
        $versionLine = Get-Content -LiteralPath (Join-Path $projectDirectory 'gradle.properties') |
            Where-Object { $_ -match '^mod_version=' } | Select-Object -First 1
        $releaseVersion = ($versionLine -split '=', 2)[1].Trim()
        & (Join-Path $PSScriptRoot 'verify-release.ps1') -JarPath (
            Join-Path $projectDirectory "build\libs\IC2-Universal-Energy-Extension-1.20.1-$releaseVersion.jar")
    }
} finally {
    Pop-Location
}
