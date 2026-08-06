[CmdletBinding()]
param(
    [string]$InstancesRoot = (Join-Path $env:APPDATA 'PrismLauncher\instances'),
    [string]$PrismLauncher = (Join-Path $env:LOCALAPPDATA 'Programs\PrismLauncher\prismlauncher.exe'),
    [string]$Manifest = (Join-Path $PSScriptRoot '..\build\release\manifest.json'),
    [string]$Report = (Join-Path $PSScriptRoot '..\build\prism-smoke-report.json'),
    [int]$TimeoutSeconds = 240,
    [int]$PreJavaTimeoutSeconds = 60,
    [string[]]$OnlyLoader,
    [string[]]$OnlyMinecraft,
    [string]$StartAt,
    [string]$OfflineName = 'BetterLoreSmoke',
    [int]$ExpectedMaxMemoryMb = 2048,
    [switch]$Resume,
    [switch]$FailFast
)

$ErrorActionPreference = 'Stop'
$versionOrder = @(
    '1.20.5', '1.20.6', '1.21', '1.21.1', '1.21.2', '1.21.3',
    '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9',
    '1.21.10', '1.21.11', '26.1', '26.1.1', '26.1.2', '26.2'
)
$loaderNames = @{
    fabric = 'Fabric'
    neoforge = 'NeoForge'
    forge = 'Forge'
}
$loaderOrder = @{ fabric = 0; neoforge = 1; forge = 2 }
$fatalPatterns = @(
    'MixinApplyError',
    'Mixin apply failed',
    'InvalidMixinException',
    'NoSuchMethodError',
    'NoSuchFieldError',
    'AbstractMethodError',
    'UnsupportedClassVersionError',
    'Could not execute entrypoint',
    'Some of your mods are incompatible',
    'ModLoadingException',
    'LoadingFailedException',
    'Couldn''t load mod:(better_lore|better_lore_impl) pack metadata',
    '(?s:NoClassDefFoundError.{0,800}com[./]reign[./]betterlore)',
    '(?s:ClassNotFoundException.{0,800}com[./]reign[./]betterlore)',
    '(?im)^.*ERROR.*better_lore.*$',
    '(?im)^.*better_lore.*(?:failed|failure).*$'
)
$fatalRegex = [regex]::new(($fatalPatterns -join '|'), [Text.RegularExpressions.RegexOptions]::IgnoreCase)

if (-not (Test-Path -LiteralPath $PrismLauncher -PathType Leaf)) {
    throw "PrismLauncher executable is missing: $PrismLauncher"
}
if (-not (Test-Path -LiteralPath $Manifest -PathType Leaf)) {
    throw "Release manifest is missing: $Manifest"
}
if ($OfflineName -and $OfflineName -notmatch '^[A-Za-z0-9_]{3,16}$') {
    throw "OfflineName must be a valid Minecraft player name (3-16 letters, digits, or underscores): $OfflineName"
}
if ($ExpectedMaxMemoryMb -lt 256) {
    throw "ExpectedMaxMemoryMb must be at least 256, found $ExpectedMaxMemoryMb."
}
if ($PreJavaTimeoutSeconds -lt 10 -or $PreJavaTimeoutSeconds -gt $TimeoutSeconds) {
    throw "PreJavaTimeoutSeconds must be between 10 and TimeoutSeconds ($TimeoutSeconds)."
}

$manifestPath = [IO.Path]::GetFullPath($Manifest)
$manifestSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $manifestPath).Hash.ToLowerInvariant()
$manifestDocument = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifestDocument.schema_version -ne 2) {
    throw "Expected release manifest schema 2, found $($manifestDocument.schema_version)."
}
$artifactHashes = @{}
foreach ($artifactRecord in $manifestDocument.artifacts) {
    $artifactHashes[$artifactRecord.file] = $artifactRecord.sha256
}

$targets = @($manifestDocument.targets | Where-Object {
    (-not $OnlyLoader -or $OnlyLoader -contains $_.loader) -and
    (-not $OnlyMinecraft -or $OnlyMinecraft -contains $_.minecraft)
})
$targets = @($targets | Sort-Object `
    @{ Expression = { [array]::IndexOf($versionOrder, $_.minecraft) } }, `
    @{ Expression = { $loaderOrder[$_.loader] } })
if ($StartAt) {
    $startIndex = [array]::FindIndex(
        [object[]]$targets,
        [Predicate[object]] { param($target) "$($target.minecraft)_$($loaderNames[$target.loader])_Testing" -eq $StartAt }
    )
    if ($startIndex -lt 0) {
        throw "StartAt instance is not in the selected target set: $StartAt"
    }
    $targets = @($targets[$startIndex..($targets.Count - 1)])
}
if ($targets.Count -eq 0) {
    throw 'No Prism smoke targets were selected.'
}

$selectionText = ($targets | ForEach-Object {
    "$($_.loader)|$($_.minecraft)|$($_.file)|$([string]$_.nested_candidate)"
}) -join "`n"
$selectionHasher = [Security.Cryptography.SHA256]::Create()
try {
    $selectionBytes = [Text.Encoding]::UTF8.GetBytes($selectionText)
    $selectionSha256 = ([BitConverter]::ToString($selectionHasher.ComputeHash($selectionBytes))).Replace('-', '').ToLowerInvariant()
} finally {
    $selectionHasher.Dispose()
}

$reportPath = [IO.Path]::GetFullPath($Report)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($reportPath)) | Out-Null
$launchMode = if ($OfflineName) { "offline:$OfflineName" } else { 'online' }

function Get-JavaProcesses {
    return @(Get-Process java, javaw -ErrorAction SilentlyContinue)
}

function Get-ProcessCommandLine([int]$Id) {
    try {
        $record = Get-CimInstance -ClassName Win32_Process -Filter "ProcessId = $Id" -ErrorAction Stop
        return [string]$record.CommandLine
    } catch {
        return ''
    }
}

function Test-JavaProcessBelongsToInstance([int]$Id, [string]$Instance) {
    $commandLine = (Get-ProcessCommandLine $Id).Replace('\', '/')
    $instancePath = [IO.Path]::GetFullPath($Instance).Replace('\', '/')
    return $commandLine.IndexOf($instancePath, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Test-JavaProcessMemory([int[]]$Ids, [int]$ExpectedMb) {
    $megabytePattern = "(?:^|\s)-Xmx$ExpectedMb[mM](?:\s|$)"
    $gigabytePattern = if ($ExpectedMb % 1024 -eq 0) {
        "(?:^|\s)-Xmx$($ExpectedMb / 1024)[gG](?:\s|$)"
    } else {
        $null
    }
    $liveIds = @($Ids | Where-Object {
        $null -ne (Get-Process -Id $_ -ErrorAction SilentlyContinue)
    })
    if ($liveIds.Count -eq 0) {
        return $false
    }
    foreach ($id in $liveIds) {
        $commandLine = Get-ProcessCommandLine $id
        if ($commandLine -notmatch $megabytePattern -and
            (-not $gigabytePattern -or $commandLine -notmatch $gigabytePattern)) {
            return $false
        }
    }
    return $true
}

function Get-PrismProcesses {
    $processName = [IO.Path]::GetFileNameWithoutExtension($PrismLauncher)
    return @(Get-Process -Name $processName -ErrorAction SilentlyContinue)
}

function Test-PrismProcessBelongsToLaunch([int]$Id, [string]$InstanceName) {
    $commandLine = Get-ProcessCommandLine $Id
    return $commandLine -match '(?i)(?:^|\s)--launch(?:\s+|=)' -and
        $commandLine.IndexOf($InstanceName, [StringComparison]::OrdinalIgnoreCase) -ge 0
}

function Stop-AutomationPrismProcesses([int[]]$BaselineIds, [string]$InstanceName) {
    $processes = @(Get-PrismProcesses | Where-Object {
        $BaselineIds -notcontains $_.Id -and
        (Test-PrismProcessBelongsToLaunch $_.Id $InstanceName)
    })
    $waiting = [Collections.Generic.List[object]]::new()
    foreach ($process in $processes) {
        try {
            $process.Refresh()
            if ($process.MainWindowHandle -ne 0 -and $process.CloseMainWindow()) {
                [void]$waiting.Add($process)
            } else {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {}
    }
    $deadline = (Get-Date).AddSeconds(10)
    $alive = @($waiting)
    while ($alive.Count -gt 0 -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
        $alive = @($alive | Where-Object {
            Get-Process -Id $_.Id -ErrorAction SilentlyContinue
        })
    }
    foreach ($process in $alive) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}

function Stop-TestProcesses([int[]]$Ids, [string]$Instance) {
    $processes = @(foreach ($id in $Ids) {
        if (Test-JavaProcessBelongsToInstance $id $Instance) {
            Get-Process -Id $id -ErrorAction SilentlyContinue
        }
    })
    $waiting = [Collections.Generic.List[object]]::new()
    foreach ($process in $processes) {
        try {
            $process.Refresh()
            if ($process.MainWindowHandle -ne 0 -and $process.CloseMainWindow()) {
                [void]$waiting.Add($process)
            } else {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {}
    }
    $deadline = (Get-Date).AddSeconds(10)
    $alive = @($waiting)
    while ($alive.Count -gt 0 -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
        $alive = @(foreach ($id in $Ids) {
            if (Test-JavaProcessBelongsToInstance $id $Instance) {
                Get-Process -Id $id -ErrorAction SilentlyContinue
            }
        })
    }
    foreach ($process in $alive) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}

function Get-LogSnapshot([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $null
    }
    $item = Get-Item -LiteralPath $Path
    return [pscustomobject]@{
        length = $item.Length
        write_ticks = $item.LastWriteTimeUtc.Ticks
    }
}

function Test-LogChanged([string]$Path, $Before, [DateTime]$Started) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $false
    }
    $item = Get-Item -LiteralPath $Path
    if ($item.LastWriteTimeUtc -lt $Started.AddSeconds(-2)) {
        return $false
    }
    if ($null -eq $Before) {
        return $true
    }
    return $item.Length -ne $Before.length -or $item.LastWriteTimeUtc.Ticks -ne $Before.write_ticks
}

function Write-SmokeReport($ResultList, [object[]]$SelectedTargets, [DateTime]$RunStarted) {
    $resultArray = @($ResultList)
    $passedResults = @($resultArray | Where-Object { $_.status -eq 'passed' })
    $failedResults = @($resultArray | Where-Object { $_.status -eq 'failed' })
    $selectedArtifacts = @($SelectedTargets | ForEach-Object file | Sort-Object -Unique)
    $passedArtifacts = @($passedResults | ForEach-Object artifact | Sort-Object -Unique)
    $complete = $resultArray.Count -eq $SelectedTargets.Count
    $reportDocument = [ordered]@{
        schema_version = 2
        manifest_sha256 = $manifestSha256
        selection_sha256 = $selectionSha256
        launch_mode = $launchMode
        max_memory_mb = $ExpectedMaxMemoryMb
        started_at = $RunStarted.ToString('o')
        updated_at = [DateTime]::UtcNow.ToString('o')
        finished_at = if ($complete) { [DateTime]::UtcNow.ToString('o') } else { $null }
        selected_target_count = $SelectedTargets.Count
        completed_target_count = $resultArray.Count
        passed = $passedResults.Count
        failed = $failedResults.Count
        selected_artifact_count = $selectedArtifacts.Count
        passed_artifact_count = $passedArtifacts.Count
        results = $resultArray
    }
    $temporaryReport = "$reportPath.tmp"
    $json = $reportDocument | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText($temporaryReport, $json, [Text.UTF8Encoding]::new($false))
    if (Test-Path -LiteralPath $reportPath -PathType Leaf) {
        $backupReport = "$reportPath.bak"
        [IO.File]::Delete($backupReport)
        [IO.File]::Replace($temporaryReport, $reportPath, $backupReport)
        [IO.File]::Delete($backupReport)
    } else {
        [IO.File]::Move($temporaryReport, $reportPath)
    }
    return $reportDocument
}

$priorPasses = @{}
if ($Resume -and (Test-Path -LiteralPath $reportPath -PathType Leaf)) {
    $priorReport = Get-Content -Raw -LiteralPath $reportPath | ConvertFrom-Json
    if ($priorReport.schema_version -ne 2) {
        throw "Cannot resume smoke report schema $($priorReport.schema_version); expected 2."
    }
    if ($priorReport.manifest_sha256 -ne $manifestSha256) {
        throw 'Cannot resume because the release manifest changed.'
    }
    if ($priorReport.selection_sha256 -ne $selectionSha256) {
        throw 'Cannot resume because the selected loader/version target set changed.'
    }
    if ($priorReport.launch_mode -ne $launchMode) {
        throw "Cannot resume because launch mode changed from $($priorReport.launch_mode) to $launchMode."
    }
    if ($priorReport.max_memory_mb -ne $ExpectedMaxMemoryMb) {
        throw "Cannot resume because the expected maximum memory changed from $($priorReport.max_memory_mb) to $ExpectedMaxMemoryMb MB."
    }
    foreach ($priorResult in $priorReport.results) {
        if ($priorResult.status -eq 'passed') {
            $priorPasses[$priorResult.instance] = $priorResult
        }
    }
}

$results = [Collections.Generic.List[object]]::new()
$runStarted = [DateTime]::UtcNow
foreach ($target in $targets) {
    $displayLoader = $loaderNames[$target.loader]
    if (-not $displayLoader) {
        throw "Unknown loader in manifest: $($target.loader)"
    }
    $instanceName = "$($target.minecraft)_${displayLoader}_Testing"
    $instance = Join-Path $InstancesRoot $instanceName
    $mods = Join-Path $instance 'minecraft\mods'
    $log = Join-Path $instance 'minecraft\logs\latest.log'
    $debugLog = Join-Path $instance 'minecraft\logs\debug.log'
    $artifactPath = Join-Path $mods $target.file
    $artifactSha256 = [string]$artifactHashes[$target.file]

    $priorPass = $priorPasses[$instanceName]
    if ($priorPass -and
        $priorPass.artifact -eq $target.file -and
        $priorPass.artifact_sha256 -eq $artifactSha256 -and
        [string]$priorPass.nested_candidate -eq [string]$target.nested_candidate -and
        (Test-Path -LiteralPath $artifactPath -PathType Leaf) -and
        (Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash.ToLowerInvariant() -eq $artifactSha256) {
        $resumedResult = [ordered]@{
            instance = $instanceName
            loader = $target.loader
            minecraft = $target.minecraft
            artifact = $target.file
            artifact_sha256 = $artifactSha256
            nested_candidate = $target.nested_candidate
            status = 'passed'
            duration_seconds = 0
            evidence = @($priorPass.evidence)
            failure = $null
            log = $log
            debug_log = if ($target.loader -eq 'fabric') { $null } else { $debugLog }
            launch_mode = $launchMode
            max_memory_mb = $ExpectedMaxMemoryMb
            completed_at = $priorPass.completed_at
            resumed = $true
        }
        $results.Add([pscustomobject]$resumedResult)
        Write-Host "[$($results.Count)/$($targets.Count)] Reusing verified pass for $instanceName" -ForegroundColor Cyan
        [void](Write-SmokeReport $results $targets $runStarted)
        continue
    }

    $started = [DateTime]::UtcNow
    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $status = 'failed'
    $reason = $null
    $evidence = [Collections.Generic.List[string]]::new()
    $observedIds = [Collections.Generic.HashSet[int]]::new()
    $baselineIds = @()
    $baselinePrismIds = @()
    $launchAttempted = $false
    $launchRetried = $false

    Write-Host "[$($results.Count + 1)/$($targets.Count)] Launching $instanceName with $($target.file)"
    try {
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            $reason = "mapped artifact is not installed: $artifactPath"
        } elseif ((Get-FileHash -Algorithm SHA256 -LiteralPath $artifactPath).Hash.ToLowerInvariant() -ne $artifactSha256) {
            $reason = 'installed artifact hash differs from the release manifest'
        } else {
            $baselineIds = @(Get-JavaProcesses | Select-Object -ExpandProperty Id)
            $baselinePrismIds = @(Get-PrismProcesses | Select-Object -ExpandProperty Id)
            $latestBefore = Get-LogSnapshot $log
            $debugBefore = Get-LogSnapshot $debugLog
            $launchArguments = @('--launch', $instanceName)
            if ($OfflineName) {
                $launchArguments += @('--offline', $OfflineName)
            }
            $launchAttempted = $true
            Start-Process -FilePath $PrismLauncher -ArgumentList $launchArguments | Out-Null

            $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
            $latestContent = ''
            $debugContent = ''
            do {
                Start-Sleep -Milliseconds 500
                foreach ($process in Get-JavaProcesses) {
                    if ($baselineIds -notcontains $process.Id -and
                        -not $observedIds.Contains($process.Id) -and
                        (Test-JavaProcessBelongsToInstance $process.Id $instance)) {
                        [void]$observedIds.Add($process.Id)
                    }
                }

                $latestFresh = Test-LogChanged $log $latestBefore $started
                $debugFresh = Test-LogChanged $debugLog $debugBefore $started
                if ($latestFresh) {
                    $latestContent = [string](Get-Content -Raw -LiteralPath $log)
                }
                if ($debugFresh) {
                    $debugContent = [string](Get-Content -Raw -LiteralPath $debugLog)
                }
                $combinedContent = "$latestContent`n$debugContent"
                $fatal = $fatalRegex.Match($combinedContent)
                if ($fatal.Success) {
                    $reason = "fatal log signature: $($fatal.Value -replace '\s+', ' ')"
                    break
                }

                $modReady = $false
                if ($target.loader -eq 'fabric') {
                    $candidateLabel = [IO.Path]::GetFileNameWithoutExtension($target.nested_candidate).Replace('better-lore-impl-', '')
                    $modReady = $latestFresh -and
                        $latestContent -match '(?m)^\s*-\s+better_lore\s+1\.1\.0\s*$' -and
                        $latestContent -match [regex]::Escape("better_lore_impl 1.1.0+mc.$candidateLabel")
                } else {
                    $fmlDiscoveryContent = "$latestContent`n$debugContent"
                    $modReady = ($latestFresh -or $debugFresh) -and
                        $fmlDiscoveryContent -match [regex]::Escape([string]$target.file) -and
                        ($fmlDiscoveryContent -match 'Found valid mod file[^\r\n]*\{better_lore\} mods' -or
                            $fmlDiscoveryContent -match '(?m)^\s*Better Lore 1\.1\.0 \(better_lore\)\s*$')
                }

                if ($observedIds.Count -gt 0 -and $modReady -and $latestFresh -and $latestContent -match 'Sound engine started') {
                    if (-not (Test-JavaProcessMemory @($observedIds) $ExpectedMaxMemoryMb)) {
                        $reason = "target Java process did not use the expected -Xmx$($ExpectedMaxMemoryMb)m limit"
                        break
                    }
                    Start-Sleep -Seconds 3
                    $latestContent = [string](Get-Content -Raw -LiteralPath $log)
                    if (Test-Path -LiteralPath $debugLog -PathType Leaf) {
                        $debugContent = [string](Get-Content -Raw -LiteralPath $debugLog)
                    }
                    $fatal = $fatalRegex.Match("$latestContent`n$debugContent")
                    if ($fatal.Success) {
                        $reason = "fatal log signature after readiness: $($fatal.Value -replace '\s+', ' ')"
                    } else {
                        $status = 'passed'
                        if ($launchRetried) {
                            [void]$evidence.Add('Prism launch forwarding was retried before the target JVM appeared')
                        }
                        if ($target.loader -eq 'fabric') {
                            [void]$evidence.Add('Fabric discovered the public better_lore container')
                            [void]$evidence.Add("Fabric selected $($target.nested_candidate)")
                        } else {
                            [void]$evidence.Add("$displayLoader found $($target.file) as valid better_lore mod 1.1.0")
                        }
                        [void]$evidence.Add('A Java process was correlated to the target instance')
                        [void]$evidence.Add("Java maximum heap is $ExpectedMaxMemoryMb MB")
                        [void]$evidence.Add('Sound engine started')
                    }
                    break
                }
                if ($latestFresh -and $latestContent -match '(?m)^.*Stopping!.*$') {
                    $reason = 'client stopped before the readiness marker'
                    break
                }
                if ($observedIds.Count -gt 0 -and $stopwatch.Elapsed.TotalSeconds -ge 8) {
                    $alive = @(foreach ($id in $observedIds) { Get-Process -Id $id -ErrorAction SilentlyContinue })
                    if ($alive.Count -eq 0) {
                        $reason = 'client process exited before readiness'
                        break
                    }
                }
                if ($observedIds.Count -eq 0) {
                    $memoryDialog = @(Get-Process -Name ([IO.Path]::GetFileNameWithoutExtension($PrismLauncher)) -ErrorAction SilentlyContinue |
                        Where-Object { $_.MainWindowTitle -match '^(Low free memory|High memory pressure)' } |
                        Select-Object -First 1)
                    if ($memoryDialog.Count -gt 0) {
                        $reason = 'Prism is waiting on a low-memory warning dialog; set LowMemWarning=false for automated test instances'
                        break
                    }
                    if (-not $launchRetried -and $stopwatch.Elapsed.TotalSeconds -ge 20) {
                        Start-Process -FilePath $PrismLauncher -ArgumentList $launchArguments | Out-Null
                        $launchRetried = $true
                    }
                    if ($stopwatch.Elapsed.TotalSeconds -ge $PreJavaTimeoutSeconds) {
                        $reason = "Prism did not start a target Java process within $PreJavaTimeoutSeconds seconds"
                        break
                    }
                }
            } while ((Get-Date) -lt $deadline)
            if ($status -ne 'passed' -and -not $reason) {
                $reason = "timed out after $TimeoutSeconds seconds"
            }
        }
    } catch {
        $reason = "smoke harness error: $($_.Exception.Message)"
    } finally {
        if ($launchAttempted) {
            foreach ($process in Get-JavaProcesses) {
                if ($baselineIds -notcontains $process.Id -and
                    (Test-JavaProcessBelongsToInstance $process.Id $instance)) {
                    [void]$observedIds.Add($process.Id)
                }
            }
        }
        Stop-TestProcesses @($observedIds) $instance
        Stop-AutomationPrismProcesses $baselinePrismIds $instanceName
        $stopwatch.Stop()
    }

    $result = [ordered]@{
        instance = $instanceName
        loader = $target.loader
        minecraft = $target.minecraft
        artifact = $target.file
        artifact_sha256 = $artifactSha256
        nested_candidate = $target.nested_candidate
        status = $status
        duration_seconds = [Math]::Round($stopwatch.Elapsed.TotalSeconds, 3)
        evidence = @($evidence)
        failure = $reason
        log = $log
        debug_log = if ($target.loader -eq 'fabric') { $null } else { $debugLog }
        launch_mode = $launchMode
        max_memory_mb = $ExpectedMaxMemoryMb
        completed_at = [DateTime]::UtcNow.ToString('o')
        resumed = $false
    }
    $results.Add([pscustomobject]$result)
    $reportDocument = Write-SmokeReport $results $targets $runStarted
    if ($status -eq 'passed') {
        Write-Host "  PASS in $($result.duration_seconds)s" -ForegroundColor Green
    } else {
        Write-Host "  FAIL: $reason" -ForegroundColor Red
        if ($FailFast) { break }
    }
}

$reportDocument = Write-SmokeReport $results $targets $runStarted
Write-Host "Smoke report: $reportPath"
if ($reportDocument.failed -gt 0 -or
    $reportDocument.completed_target_count -ne $targets.Count -or
    $reportDocument.passed_artifact_count -ne $reportDocument.selected_artifact_count) {
    exit 1
}
