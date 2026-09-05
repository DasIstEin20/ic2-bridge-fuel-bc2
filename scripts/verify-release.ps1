param([Parameter(Mandatory = $true)][string]$JarPath)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$resolvedJar = (Resolve-Path -LiteralPath $JarPath).Path
$archive = [IO.Compression.ZipFile]::OpenRead($resolvedJar)
try {
    $names = @($archive.Entries.FullName)
    $forbidden = @($names | Where-Object {
        $_ -match '(IntegrationTest|EnergyBudgetTest|BridgeTestSupport|PacketKeyRegistrationTest|UniversalCableEnergyBridgeTest|TransmitterNetworkTest)(\$[^/]*)?\.class$' -or
        $_ -match '^mekanism/common/util/test/' -or $_ -match '^META-INF/jarjar/' -or
        $_ -match '^data/ic2universalenergy(_integration)?/structures/bridge_test\.nbt$' -or
        $_ -match '^(ic2|buildcraft|forestry|com/refinedmods)/' -or
        $_ -match 'ForestryEnergyBridge'
    })
    if ($forbidden.Count) { throw "Release contains forbidden test/embedded integration files: $($forbidden -join ', ')" }
    $metadata = $archive.GetEntry('META-INF/mods.toml')
    if (!$metadata) { throw 'Missing mods.toml' }
    $reader = [IO.StreamReader]::new($metadata.Open())
    try { $toml = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ([regex]::Matches($toml, '(?m)^\s*\[\[mods\]\]').Count -ne 1 -or
        $toml -notmatch '(?s)\[\[mods\]\].*?modId\s*=\s*"ic2universalenergy"') {
        throw 'The release must contain exactly one mod: ic2universalenergy.'
    }
    $license = $archive.GetEntry('LICENSE')
    if (!$license) { throw 'Missing LICENSE' }
    $reader = [IO.StreamReader]::new($license.Open())
    try { $licenseText = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($licenseText -notmatch 'Permission is hereby granted' -or $licenseText -notmatch 'Mekanism') {
        throw 'Mekanism MIT attribution is missing.'
    }
    $classCount = 0
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName.EndsWith('.class')) {
            $stream = $entry.Open()
            try {
                $header = [byte[]]::new(8)
                $read = $stream.Read($header, 0, 8)
                if ($read -ne 8 -or ($header[6] * 256 + $header[7]) -ne 61) {
                    throw "Class is not Java 17 bytecode: $($entry.FullName)"
                }
                $classCount++
            } finally { $stream.Dispose() }
        }
    }
    Write-Output "Release audit passed: one mod, $classCount Java 17 classes, MIT attribution, no GameTests or embedded mods."
} finally {
    $archive.Dispose()
}
$releaseFile = Get-Item -LiteralPath $resolvedJar
Write-Output "$($releaseFile.Name): $($releaseFile.Length) bytes"
Write-Output "SHA256: $((Get-FileHash -LiteralPath $resolvedJar -Algorithm SHA256).Hash)"
