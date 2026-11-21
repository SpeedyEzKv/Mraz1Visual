$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
& node (Join-Path $PSScriptRoot 'package-local-launcher.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Local bundle generation failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundle = Join-Path $projectRoot 'launcher/portable'
$manifest = Get-Content -Raw -LiteralPath (Join-Path $bundle 'manifest.local.json') | ConvertFrom-Json
# Explicit allowlist: never archive portable/data (worlds, accounts, tokens, settings).
$files = @('Mraz1Visual.exe', 'manifest.local.json', 'portable.flag', 'ЧИТАТЬ.txt', 'ИНТЕГРАЦИЯ.md', 'CREDITS.md', 'UI_FIXES.md', 'ECLIPSE_FEATURES.md', 'VISUAL_AUDIT_20261118.md')
$files += @($manifest.versions | ForEach-Object { 'mods/' + $_.file })
$archivePath = Join-Path $projectRoot 'launcher/Mraz1Visual-AuraVisual-local.zip'
$temporaryPath = Join-Path $projectRoot 'launcher/Mraz1Visual-AuraVisual-local.zip.tmp'
$archive = [IO.Compression.ZipFile]::Open($temporaryPath, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, (Join-Path $bundle $file), $file, [IO.Compression.CompressionLevel]::Fastest) | Out-Null
    }
} finally { $archive.Dispose() }
$check = [IO.Compression.ZipFile]::OpenRead($temporaryPath)
try {
    if ($check.Entries.Count -ne $files.Count) { throw 'Unexpected archive entries' }
    foreach ($entry in $check.Entries) { if ($entry.FullName -notin $files) { throw "Unexpected private file: $($entry.FullName)" } }
    foreach ($version in $manifest.versions) {
        $entry = $check.GetEntry('mods/' + $version.file)
        $stream = $entry.Open(); $hasher = [Security.Cryptography.SHA256]::Create()
        try { $digest = [Convert]::ToHexString($hasher.ComputeHash($stream)).ToLowerInvariant() }
        finally { $stream.Dispose(); $hasher.Dispose() }
        if ($digest -ne $version.sha256) { throw "Archive checksum mismatch: $($version.file)" }
    }
} finally { $check.Dispose() }
Move-Item -LiteralPath $temporaryPath -Destination $archivePath -Force
Write-Output "PASS: $($files.Count) shipped files, four verified JARs, no user data. $archivePath"
