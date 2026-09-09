# Dot-source this script in the terminal before running Maven or npm.
$toolRoot = Join-Path $PSScriptRoot '..\.local-tools'
$jdkDir = Get-ChildItem $toolRoot -Directory -Filter 'jdk-21*' | Select-Object -First 1
$nodeDir = Get-ChildItem $toolRoot -Directory -Filter 'node-v20*-win-x64' | Select-Object -First 1
$mavenDir = Get-ChildItem $toolRoot -Directory -Filter 'apache-maven-3.9*' | Select-Object -First 1
if (!$jdkDir -or !$nodeDir -or !$mavenDir) { throw 'Project-local development tools are missing.' }
$env:JAVA_HOME = $jdkDir.FullName
$env:PATH = "$($jdkDir.FullName)\bin;$($nodeDir.FullName);$($mavenDir.FullName)\bin;$env:PATH"
$env:CHROME_BIN = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
$env:MAVEN_OPTS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
$env:npm_config_cache = Join-Path $toolRoot 'npm-cache'
$certFile = Join-Path $toolRoot 'windows-roots.pem'
if (Test-Path $certFile) { $env:NODE_EXTRA_CA_CERTS = (Resolve-Path $certFile).Path }
$runDir = Join-Path $PSScriptRoot '..\.local-run'
New-Item -ItemType Directory -Force $runDir | Out-Null
$env:TEMP = (Resolve-Path $runDir).Path
$env:TMP = $env:TEMP
