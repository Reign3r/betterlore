param(
    [string]$InstancesRoot = "$env:APPDATA\PrismLauncher\instances"
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$releaseRoot = Join-Path $repo 'build\release'
$jeiRoot = Join-Path $repo 'build\prism-testing-deps\jei'
$marker = 'Better Lore 1.1.0 test instance'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$versions = @(
    '1.20.5', '1.20.6',
    '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5',
    '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
    '26.1', '26.1.1', '26.1.2', '26.2'
)

function Read-Properties([string]$Path) {
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $trimmed = $line.Trim()
        if (!$trimmed -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 1) {
            continue
        }
        $properties[$trimmed.Substring(0, $separator).Trim()] =
            $trimmed.Substring($separator + 1).Trim()
    }
    return $properties
}

function Find-CachedJar([string]$Group, [string]$Module, [string]$Version) {
    $base = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\$Group\$Module\$Version"
    if (!(Test-Path -LiteralPath $base)) {
        throw "Gradle cache entry missing: ${Group}:${Module}:${Version}"
    }
    $jar = Get-ChildItem -LiteralPath $base -Recurse -File -Filter '*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
        Sort-Object Length -Descending |
        Select-Object -First 1
    if (!$jar) {
        throw "Runtime jar missing: ${Group}:${Module}:${Version}"
    }
    return $jar.FullName
}

function Resolve-PlaceholderJar([hashtable]$Properties) {
    $coordinate = $Properties['deps.placeholder_api']
    $parts = $coordinate.Split(':')
    if ($parts.Count -eq 3) {
        return Find-CachedJar $parts[0] $parts[1] $parts[2]
    }
    return Find-CachedJar 'eu.pb4' 'placeholder-api' $coordinate
}

function Resolve-JeiVersion([pscustomobject]$Target) {
    $overrideKey = "deps.jei_$($Target.Slug)"
    if ($Target.Properties.ContainsKey($overrideKey)) {
        return $Target.Properties[$overrideKey]
    }
    return $Target.Properties['deps.jei']
}

$targets = @()
foreach ($minecraftVersion in $versions) {
    $properties = Read-Properties (Join-Path $repo "versions\$minecraftVersion\gradle.properties")
    $targets += [pscustomobject]@{
        Minecraft = $minecraftVersion
        Label = 'Fabric'
        Slug = 'fabric'
        LoaderUid = 'net.fabricmc.fabric-loader'
        LoaderVersion = $properties['deps.fabric_loader']
        Properties = $properties
    }
    $targets += [pscustomobject]@{
        Minecraft = $minecraftVersion
        Label = 'NeoForge'
        Slug = 'neoforge'
        LoaderUid = 'net.neoforged'
        LoaderVersion = $properties['deps.neoforge']
        Properties = $properties
    }
    if ($properties['deps.forge'] -ne 'UNSUPPORTED') {
        $targets += [pscustomobject]@{
            Minecraft = $minecraftVersion
            Label = 'Forge'
            Slug = 'forge'
            LoaderUid = 'net.minecraftforge'
            LoaderVersion = $properties['deps.forge'].Substring($minecraftVersion.Length + 1)
            Properties = $properties
        }
    }
}

if ($targets.Count -ne 52) {
    throw "Expected 52 Prism targets, found $($targets.Count)."
}

# Resolve every input before touching the Prism directory.
foreach ($target in $targets) {
    $betterLore = Join-Path $releaseRoot "better-lore-$($target.Slug)-$($target.Minecraft)-1.1.0.jar"
    if (!(Test-Path -LiteralPath $betterLore)) {
        throw "Missing Better Lore release jar: $betterLore"
    }
    if ($target.Slug -eq 'fabric') {
        [void](Find-CachedJar 'net.fabricmc.fabric-api' 'fabric-api' $target.Properties['deps.fabric_api'])
        [void](Resolve-PlaceholderJar $target.Properties)
    }
    $jeiVersion = Resolve-JeiVersion $target
    if ($jeiVersion -ne 'UNSUPPORTED') {
        $jei = Join-Path $jeiRoot "jei-$($target.Minecraft)-$($target.Slug)-$jeiVersion.jar"
        if (!(Test-Path -LiteralPath $jei)) {
            throw "Missing JEI runtime jar: $jei"
        }
    }
}

New-Item -ItemType Directory -Path $InstancesRoot -Force | Out-Null
$rootFull = [System.IO.Path]::GetFullPath($InstancesRoot).TrimEnd('\') + '\'
$created = 0
$updated = 0
$withJei = 0
$withoutJei = 0

foreach ($target in $targets) {
    $name = "$($target.Minecraft)_$($target.Label)_Testing"
    $instanceDir = Join-Path $InstancesRoot $name
    $instanceFull = [System.IO.Path]::GetFullPath($instanceDir).TrimEnd('\') + '\'
    if (!$instanceFull.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing unsafe instance path: $instanceDir"
    }

    $notesPath = Join-Path $instanceDir 'notes.txt'
    $configPath = Join-Path $instanceDir 'instance.cfg'
    if (Test-Path -LiteralPath $instanceDir) {
        $recognized = (Test-Path -LiteralPath $notesPath) -and
            (Select-String -LiteralPath $notesPath -SimpleMatch $marker -Quiet)
        $partialFromThisScript = (Test-Path -LiteralPath $configPath) -and
            (Select-String -LiteralPath $configPath -SimpleMatch "name=$name" -Quiet)
        if (!$recognized -and !$partialFromThisScript) {
            throw "Refusing to overwrite unrecognized Prism instance: $instanceDir"
        }
        $updated++
    } else {
        New-Item -ItemType Directory -Path $instanceDir -Force | Out-Null
        $created++
    }

    $modsDir = Join-Path $instanceDir 'minecraft\mods'
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null

    $config = @"
[General]
AutomaticJava=true
ConfigVersion=1.3
InstanceType=OneSix
iconKey=default
name=$name
"@
    [System.IO.File]::WriteAllText($configPath, $config.Replace("`n", "`r`n"), $utf8)

    $pack = [ordered]@{
        components = @(
            [ordered]@{ important = $true; uid = 'net.minecraft'; version = $target.Minecraft },
            [ordered]@{ uid = $target.LoaderUid; version = $target.LoaderVersion }
        )
        formatVersion = 1
    } | ConvertTo-Json -Depth 6
    [System.IO.File]::WriteAllText(
        (Join-Path $instanceDir 'mmc-pack.json'),
        $pack + "`r`n",
        $utf8
    )

    # These profiles are generated and owned by this script. Replace only the
    # managed dependency families while retaining any unrelated test mods.
    Get-ChildItem -LiteralPath $modsDir -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(better-lore|fabric-api|placeholder-api|jei)-.*\.jar$' } |
        Remove-Item -Force

    $betterLore = Join-Path $releaseRoot "better-lore-$($target.Slug)-$($target.Minecraft)-1.1.0.jar"
    Copy-Item -LiteralPath $betterLore -Destination (Join-Path $modsDir (Split-Path $betterLore -Leaf)) -Force

    $notes = @(
        $marker
        "Minecraft: $($target.Minecraft)"
        "Loader: $($target.Label) $($target.LoaderVersion)"
        'Better Lore: 1.1.0'
    )

    if ($target.Slug -eq 'fabric') {
        $fabricApiVersion = $target.Properties['deps.fabric_api']
        $fabricApi = Find-CachedJar 'net.fabricmc.fabric-api' 'fabric-api' $fabricApiVersion
        Copy-Item -LiteralPath $fabricApi -Destination (Join-Path $modsDir "fabric-api-$fabricApiVersion.jar") -Force
        $placeholder = Resolve-PlaceholderJar $target.Properties
        Copy-Item -LiteralPath $placeholder -Destination (Join-Path $modsDir "placeholder-api-$($target.Minecraft).jar") -Force
        $notes += "Fabric API: $fabricApiVersion"
        $notes += "Placeholder API: $($target.Properties['deps.placeholder_api'])"
    }

    $jeiVersion = Resolve-JeiVersion $target
    if ($jeiVersion -ne 'UNSUPPORTED') {
        $jei = Join-Path $jeiRoot "jei-$($target.Minecraft)-$($target.Slug)-$jeiVersion.jar"
        Copy-Item -LiteralPath $jei -Destination (Join-Path $modsDir (Split-Path $jei -Leaf)) -Force
        $notes += "JEI: $jeiVersion"
        $withJei++
    } else {
        $notes += 'JEI: not installed (no compatible release is published for this Minecraft/loader target)'
        $withoutJei++
    }

    [System.IO.File]::WriteAllText($notesPath, ($notes -join "`r`n") + "`r`n", $utf8)
}

Write-Output "Provisioned=$($targets.Count) Created=$created Updated=$updated JEI=$withJei NoJEI=$withoutJei"
