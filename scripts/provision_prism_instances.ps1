[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$InstancesRoot,

    [string]$GradleCacheRoot = (Join-Path $env:USERPROFILE '.gradle\caches'),

    [switch]$BetterLoreOnly,

    [switch]$ConfigureTestRuntime
)

$ErrorActionPreference = 'Stop'
$python = Get-Command python -ErrorAction Stop
$script = Join-Path $PSScriptRoot 'provision_prism_instances.py'

$arguments = @(
    $script,
    '--instances-root', $InstancesRoot,
    '--gradle-cache-root', $GradleCacheRoot
)
if ($BetterLoreOnly) {
    $arguments += '--better-lore-only'
}
if ($ConfigureTestRuntime) {
    $arguments += '--configure-test-runtime'
}

& $python.Source @arguments

if ($LASTEXITCODE -ne 0) {
    throw "Prism provisioning failed with exit code $LASTEXITCODE."
}
