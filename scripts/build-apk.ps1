param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$toolCandidates = @(
  $env:HA_LISTENER_ANDROID_TOOLS,
  'H:\files\android-build-tools',
  (Join-Path $env:USERPROFILE 'Downloads\CGI_Codex_Handoff\android-build-tools')
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'android-sdk')) }
if (-not $toolCandidates) {
  throw 'Android build tools not found. Set HA_LISTENER_ANDROID_TOOLS to a folder containing jdk17, gradle and android-sdk.'
}
$tools = (Resolve-Path @($toolCandidates)[0]).Path
$env:JAVA_HOME = (Get-ChildItem (Join-Path $tools 'jdk17') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $tools 'android-sdk'
$gradle = (Get-ChildItem (Join-Path $tools 'gradle') -Recurse -Filter gradle.bat | Select-Object -First 1).FullName
$buildTools = (Get-ChildItem (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name | Select-Object -Last 1).FullName
$version = (Select-String -Path (Join-Path $root 'app\build.gradle') -Pattern "versionName '([^']+)'").Matches[0].Groups[1].Value
$localBuild = Join-Path $env:LOCALAPPDATA 'ha-listener-lab-build'
$localProject = Join-Path $localBuild 'project'
$keystore = Join-Path $localBuild 'listener-lab.keystore'

New-Item -ItemType Directory -Path $localProject -Force | Out-Null
robocopy $root $localProject /MIR /XD .git .gradle build releases claude /XF local.properties *.apk /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw 'Could not copy the Android project to the local build folder.' }
Set-Content -Path (Join-Path $localProject 'local.properties') -Value ("sdk.dir=" + $env:ANDROID_HOME.Replace('\', '\\')) -Encoding ascii

Push-Location $localProject
try {
  & $gradle --no-daemon testReleaseUnitTest assembleRelease
  if ($LASTEXITCODE) { throw 'Gradle tests or build failed.' }
} finally {
  Pop-Location
}

if (-not (Test-Path $keystore)) {
  New-Item -ItemType Directory -Path $localBuild -Force | Out-Null
  & (Join-Path $env:JAVA_HOME 'bin\keytool.exe') -genkeypair -keystore $keystore -storepass android -keypass android -alias listenerlab -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Listener Lab, OU=Local Test Build, O=Private, C=US'
  if ($LASTEXITCODE) { throw 'Could not create the local signing key.' }
}

$unsigned = Join-Path $localProject 'app\build\outputs\apk\release\app-release-unsigned.apk'
$aligned = Join-Path $localBuild 'listener-lab-aligned.apk'
$outputDir = Join-Path $root 'releases'
$output = Join-Path $outputDir "Listener_Lab_v$version.apk"
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
& (Join-Path $buildTools 'zipalign.exe') -f -p 4 $unsigned $aligned
if ($LASTEXITCODE) { throw 'zipalign failed.' }
& (Join-Path $buildTools 'apksigner.bat') sign --ks $keystore --ks-pass pass:android --key-pass pass:android --ks-key-alias listenerlab --out $output $aligned
if ($LASTEXITCODE) { throw 'APK signing failed.' }
& (Join-Path $buildTools 'apksigner.bat') verify --print-certs $output | Select-Object -First 2
Write-Host "Built $output"

