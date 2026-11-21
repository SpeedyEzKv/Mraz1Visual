$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$projectRoot = Split-Path $PSScriptRoot -Parent
$artifactDirectory = Join-Path $projectRoot 'mod/build/libs/all'
$expected = @{
    '1.21.11' = @('9.0.8+1.21.11-fabric', '17.0.1')
    '26.1.2' = @('9.0.8+26.1.2-fabric', '18.0.2')
    '26.2' = @('9.0.8+26.2-fabric', '20.0.3')
    '26.3' = @('9.0.8+26.3-fabric', '21.0.0')
}
function Read-ZipText($archive, [string]$entryName) {
    $entry = $archive.GetEntry($entryName)
    if (!$entry) { throw "Missing entry: $entryName" }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $reader.ReadToEnd() } finally { $reader.Dispose() }
}
function Get-EntryHash($entry) {
    if (!$entry) { throw 'Missing font entry' }
    $stream = $entry.Open()
    $hash = [Security.Cryptography.SHA256]::Create()
    try { [Convert]::ToHexString($hash.ComputeHash($stream)) } finally { $stream.Dispose(); $hash.Dispose() }
}
$sums = @()
foreach ($version in @('1.21.11', '26.1.2', '26.2', '26.3')) {
    $file = Join-Path $artifactDirectory "mraz1visual-0.1.0+mc$version.jar"
    $archive = [IO.Compression.ZipFile]::OpenRead($file)
    try {
        $names = @($archive.Entries.FullName)
        $forbidden = @($names | Where-Object { $_ -match '^assets/auravisuals/(fonts?/|icons/|gifs/|lang/|updates/|textures/(mainmenu|gui)/)' -or $_ -match '(AuraTitleScreen|AuraStyle|AuraMedia|PreviewScene)\$?[^/]*\.class$|/aura_(native\.json)|/aura-(text|bold|head)\.ttf$' })
        if ($forbidden.Count) { throw "Aura shell remains in ${version}: $($forbidden -join ', ')" }
        if (@($names | Where-Object { $_ -match '^assets/mraz1visual/cosmetics/aura/[^/]+\.bbmodel$' }).Count -ne 78) { throw "Cosmetic catalog mismatch: $version" }
        if (@($names | Where-Object { $_ -match '^assets/mraz1visual/cosmetics/aura-items/assets/minecraft/models/item/.+\.json$' }).Count -ne 119) { throw "Item catalog mismatch: $version" }
        foreach ($font in @('text','bold','head','brand','symbols')) {
            $resource = "assets/mraz1visual/font/$font.ttf"
            $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $projectRoot "mod/src/main/resources/$resource")).Hash
            if ((Get-EntryHash $archive.GetEntry($resource)) -ne $sourceHash) { throw "Changed native font: $font" }
        }
        foreach ($entry in @('META-INF/AURAVISUAL-NOTICE.txt','assets/mraz1visual/font/integration_native.json','assets/mraz1visual/font/world_native.json','dev/mraz1visual/mixin/IntegrationListMixin.class','dev/mraz1visual/models/AuraPlacement.class','dev/mraz1visual/input/CursorGuard.class','dev/mraz1visual/render/post/WorldMotionBlur.class')) {
            if (!$archive.GetEntry($entry)) { throw "Missing integration entry: $entry" }
        }
        foreach ($module in @('MeteorFly','Wave','SpeechBubbles','FrameEsp','ArmorNotifier','Cubes','WorldLines','EffectRemind','ArmorDurabilityTag','PotionHighlight','SeeInvisible','NoFluids','AuraParticles','EclipseCrown','ShardWings','PetReactions','GhostDash','SurfaceEffects','SkinTransition','MusicCosmetics','PhotoMode')) {
            if (!$archive.GetEntry("dev/mraz1visual/module/$module.class")) { throw "Missing visual module: $module" }
        }
        $native=$archive.GetEntry('assets/mraz1visual/native/windows/mraz1visual-media.exe')
        if (!$native -or $native.Length -lt 100000) { throw "Missing native music helper: $version" }
        $nativeSource=Join-Path $projectRoot 'mod/src/main/resources/assets/mraz1visual/native/windows/mraz1visual-media.exe'
        if ((Get-EntryHash $native) -ne (Get-FileHash -LiteralPath $nativeSource -Algorithm SHA256).Hash) { throw "Stale native music helper: $version" }
        if (!$archive.GetEntry('META-INF/licenses/native-media/dependencies.json')) { throw "Missing native dependency notices: $version" }
        if (!$archive.GetEntry('dev/mraz1visual/ui/PhotoScreen.class')) { throw "Missing photo screen: $version" }
        $jars = @($archive.Entries | Where-Object { $_.FullName -match '^META-INF/jars/.+\.jar$' })
        if ($jars.Count -ne 5) { throw "Wrong nested dependencies: $version" }
        foreach ($dependency in @(@('in-game-account-switcher-', $expected[$version][0]), @('modmenu-', $expected[$version][1]))) {
            $entry = $jars | Where-Object { $_.Name.StartsWith($dependency[0]) } | Select-Object -First 1
            if (!$entry) { throw "Missing dependency: $($dependency[0])" }
            $memory = [IO.MemoryStream]::new(); $stream = $entry.Open()
            try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
            $memory.Position = 0
            $nested = [IO.Compression.ZipArchive]::new($memory, [IO.Compression.ZipArchiveMode]::Read)
            try {
                $metadata = (Read-ZipText $nested 'fabric.mod.json') | ConvertFrom-Json
                if ($metadata.version -ne $dependency[1]) { throw "Wrong dependency version: $($metadata.id) $($metadata.version)" }
            } finally { $nested.Dispose(); $memory.Dispose() }
        }
        $digest = (Get-FileHash -Algorithm SHA256 -LiteralPath $file).Hash.ToLowerInvariant()
        $sums += "$digest  $([IO.Path]::GetFileName($file))"
        Write-Output "PASS $version : 78 cosmetics, 119 items, 21 visual modules, native music helper, photo screen, original fonts, real IAS/Mod Menu, no Aura shell"
    } finally { $archive.Dispose() }
}
[IO.File]::WriteAllLines((Join-Path $artifactDirectory 'SHA256SUMS.txt'), $sums)
