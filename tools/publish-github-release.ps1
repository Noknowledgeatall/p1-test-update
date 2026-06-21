param(
    [Parameter(Mandatory = $true)]
    [string]$Tag
)

$ErrorActionPreference = 'Stop'

$repo = 'Noknowledgeatall/p1-test-update'
$root = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
$token = if ($env:GITHUB_TOKEN) { $env:GITHUB_TOKEN } elseif ($env:GH_TOKEN) { $env:GH_TOKEN } else { '' }

if (-not $token) {
    throw 'Zet eerst GITHUB_TOKEN of GH_TOKEN met rechten op de repo.'
}
if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "APK niet gevonden: $apkPath"
}

$headers = @{
    Authorization = "Bearer $token"
    Accept = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
}

$releaseBody = @{
    tag_name = $Tag
    name = $Tag
    body = "Energy Optimizer testbuild $Tag"
    draft = $false
    prerelease = $true
} | ConvertTo-Json

try {
    $release = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$repo/releases" `
        -Method Post `
        -Headers $headers `
        -ContentType 'application/json' `
        -Body $releaseBody
} catch {
    $existing = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$repo/releases/tags/$Tag" `
        -Method Get `
        -Headers $headers
    $release = $existing
}

$assetName = "energy-optimizer-$Tag.apk"
$uploadUri = $release.upload_url -replace '\{\?name,label\}', "?name=$assetName"

Invoke-RestMethod `
    -Uri $uploadUri `
    -Method Post `
    -Headers $headers `
    -ContentType 'application/vnd.android.package-archive' `
    -InFile $apkPath | Out-Null

"Geupload naar https://github.com/$repo/releases/tag/$Tag"
