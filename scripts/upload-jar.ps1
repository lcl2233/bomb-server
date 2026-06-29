param(
    [string]$HostName = "114.55.177.200",
    [string]$RemoteUser = "root",
    [string]$JarPath = "target\bomb-server-1.0.0.jar",
    [string]$RemotePath = "/opt/bomb-server/bomb-server.jar"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $JarPath)) {
    throw "Jar not found: $JarPath. Please build it first."
}

$target = "${RemoteUser}@${HostName}:$RemotePath"
Write-Host "Uploading $JarPath -> $target"

scp -o StrictHostKeyChecking=accept-new $JarPath $target

if ($LASTEXITCODE -ne 0) {
    throw "Upload failed with exit code $LASTEXITCODE"
}

Write-Host "Upload completed."
