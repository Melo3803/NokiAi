param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
if ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    $java = Join-Path $JavaHome 'bin\java.exe'; $jar = Join-Path $JavaHome 'bin\jar.exe'
} else {
    $javac = (Get-Command javac -ErrorAction Stop).Source
    $java = Join-Path (Split-Path $javac) 'java.exe'; $jar = Join-Path (Split-Path $javac) 'jar.exe'
}
$lib = Join-Path $root 'tools\lib'
New-Item -ItemType Directory -Force -Path $lib | Out-Null
$deps = @(
    @('cldcapi11-2.0.4.jar', 'org/microemu/cldcapi11/2.0.4/cldcapi11-2.0.4.jar', 'F86F41BFB4097E4BD9CDFECA528960F6977CBDA0F5785342ADEF9909A6A3BAEF'),
    @('midpapi20-2.0.4.jar', 'org/microemu/midpapi20/2.0.4/midpapi20-2.0.4.jar', 'B7F6ED5E0344C7000643ECC114E6EDFF3587E8E77FACE546AF0E12FFD6DBE826'),
    @('ecj-4.6.1.jar', 'org/eclipse/jdt/core/compiler/ecj/4.6.1/ecj-4.6.1.jar', '9CDDDA75F4A1B4469E73F44E7B61A3E897D0F657DF4797F9106FFE88C4EEADE0'),
    @('proguard-base-6.2.2.jar', 'net/sf/proguard/proguard-base/6.2.2/proguard-base-6.2.2.jar', '7587A3A415D6AEA1946F597F4BABE710A245F085BAC4DC5E7CDA653ACFC39B03'),
    @('zxing-core-1.7-sources.jar', 'com/google/zxing/core/1.7/core-1.7-sources.jar', '922F2C8A57490000A0497C1D7434C828D04F0F3FD79663CCB99B46260C350F1F'),
    @('microemu-jsr-135-2.0.4.jar', 'org/microemu/microemu-jsr-135/2.0.4/microemu-jsr-135-2.0.4.jar', '9DE8726D16D559DA98BD36B30E83C992CED946200E80D93A9F4C61F71CEDA6AF'),
    @('microemu-jsr-75-2.0.4.jar', 'org/microemu/microemu-jsr-75/2.0.4/microemu-jsr-75-2.0.4.jar', 'D4372CF344EE03F0261AE58BD3D2BECB4AED98272BCA7C6BB98749EFD11B4601')
)
foreach ($dep in $deps) {
    $target = Join-Path $lib $dep[0]
    if (-not (Test-Path -LiteralPath $target)) {
        Invoke-WebRequest -Uri ('https://repo.maven.apache.org/maven2/' + $dep[1]) -OutFile $target
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $dep[2]) { throw ('Checksum mismatch: ' + $dep[0]) }
}
$build = Join-Path $root ('build\' + [guid]::NewGuid().ToString('N'))
$classes = Join-Path $build 'classes'; $dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $classes,$dist | Out-Null
$apiJars = @('cldcapi11-2.0.4.jar','midpapi20-2.0.4.jar','microemu-jsr-135-2.0.4.jar','microemu-jsr-75-2.0.4.jar')
$api = ($apiJars | ForEach-Object { Join-Path $lib $_ }) -join ';'
$qrSources = Join-Path $build 'zxing-source'
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $lib 'zxing-core-1.7-sources.jar'), $qrSources)
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src\cepai') -Filter '*.java' | ForEach-Object { $_.FullName })
$sources += Join-Path $qrSources 'com\google\zxing\qrcode\QRCodeReader.java'
& $java -jar (Join-Path $lib 'ecj-4.6.1.jar') -source 1.3 -target 1.1 -encoding UTF-8 -bootclasspath $api -classpath $classes -sourcepath $qrSources -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Java ME compilation failed' }
$manifest = @"
Manifest-Version: 1.0
MIDlet-Name: noki Ai
MIDlet-Version: 1.2.0
MIDlet-Vendor: Buza
MIDlet-1: noki Ai, /icon.png, cepai.CepAiMIDlet
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MIDlet-Description: Nokia E63 icin dogrudan Gemini sohbeti
MIDlet-Permissions: javax.microedition.io.Connector.https
MIDlet-Permissions-Opt: javax.microedition.media.control.VideoControl.getSnapshot, javax.microedition.io.Connector.file.read, javax.microedition.media.control.RecordControl

"@
$manifestPath = Join-Path $build 'MANIFEST.MF'
[IO.File]::WriteAllText($manifestPath, ($manifest -replace "`r?`n", "`r`n"), [Text.Encoding]::ASCII)
$raw = Join-Path $build 'unverified.jar'
& $jar "-J-Djava.io.tmpdir=$build" cfm $raw $manifestPath -C $classes . -C (Join-Path $root 'resources') . -C $root LICENSE
if ($LASTEXITCODE -ne 0) { throw 'JAR creation failed' }
$output = Join-Path $dist 'NokiAi.jar'
$arguments = @('-injars',$raw)
foreach ($apiJar in $apiJars) { $arguments += @('-libraryjars', ((Join-Path $lib $apiJar) + '(javax/**,java/**)')) }
$arguments += @('-outjars',$output,'-keep','class cepai.** { *; }','-dontoptimize','-dontobfuscate','-microedition','-target','1.1')
& $java -jar (Join-Path $lib 'proguard-base-6.2.2.jar') @arguments
if ($LASTEXITCODE -ne 0) { throw 'CLDC shrink/preverification failed' }
$size = (Get-Item -LiteralPath $output).Length
$jad = $manifest + "MIDlet-Jar-URL: NokiAi.jar`nMIDlet-Jar-Size: $size`n"
[IO.File]::WriteAllText((Join-Path $dist 'NokiAi.jad'), ($jad -replace "`r?`n", "`r`n"), [Text.Encoding]::ASCII)
$qrLibrary = Join-Path $root 'tools\qrcode.js'
if ((Get-FileHash -LiteralPath $qrLibrary).Hash -ne '18AE399F81182BC9DE916E9C77B195DF20CC58D6F2D55A62B085A299F1BF1780') { throw 'QR generator checksum mismatch' }
$html = [IO.File]::ReadAllText((Join-Path $root 'tools\anahtar-qr.template.html'))
$html = $html.Replace('/* QR_LIBRARY */', [IO.File]::ReadAllText($qrLibrary))
[IO.File]::WriteAllText((Join-Path $dist 'anahtar-qr.html'), $html, (New-Object Text.UTF8Encoding $false))
Copy-Item -LiteralPath (Join-Path $root 'README_TR.md') -Destination (Join-Path $dist 'KURULUM.md')
$releaseFiles = @('NokiAi.jar','NokiAi.jad','anahtar-qr.html','KURULUM.md') | ForEach-Object { Join-Path $dist $_ }
Compress-Archive -LiteralPath $releaseFiles -DestinationPath (Join-Path $dist 'NokiAi-E63.zip') -Force
Write-Output "Ready: $output ($size bytes), CLDC 1.1 / MIDP 2.0, direct HTTPS"
