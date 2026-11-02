param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'; $root = $PSScriptRoot
if ($JavaHome -and (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    $java = Join-Path $JavaHome 'bin\java.exe'; $javac = Join-Path $JavaHome 'bin\javac.exe'
} else { $javac = (Get-Command javac -ErrorAction Stop).Source; $java = Join-Path (Split-Path $javac) 'java.exe' }
$out = Join-Path $root 'test-output'; $classes = Join-Path $out 'classes'; $lib = Join-Path $root 'tools\lib'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$core = @('Json','Config','Conversation','GoogleResponse','AudioClip','Base64','Speech') | ForEach-Object { Join-Path $root ('src\cepai\' + $_ + '.java') }
& $javac -encoding UTF-8 -d $classes @core (Join-Path $root 'tests\CoreTest.java') (Join-Path $root 'tests\VoiceCodecTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Core test compilation failed' }
& $java -cp $classes cepai.CoreTest
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
& $java -cp $classes cepai.VoiceCodecTest
if ($LASTEXITCODE -ne 0) { throw 'Voice codec tests failed' }
& node (Join-Path $root 'tests\make_qr_fixtures.js')
if ($LASTEXITCODE -ne 0) { throw 'QR fixture generation failed' }
& python (Join-Path $root 'tests\make_qr_images.py')
if ($LASTEXITCODE -ne 0) { throw 'QR image generation failed (Python + Pillow needed)' }
$emulator = Join-Path $lib 'microemulator-2.0.4.jar'
if (-not (Test-Path -LiteralPath $emulator)) { Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/microemu/microemulator/2.0.4/microemulator-2.0.4.jar' -OutFile $emulator }
if ((Get-FileHash -LiteralPath $emulator).Hash -ne 'DBD5F3EB8365D3E839D6A203149E0E3776FC1A0585E16AC1FC23F76C9FCAE1C6') { throw 'Emulator checksum mismatch' }
$classpath = $emulator + ';' + (Join-Path $lib 'microemu-jsr-135-2.0.4.jar') + ';' + (Join-Path $lib 'microemu-jsr-75-2.0.4.jar')
& $javac -encoding UTF-8 -cp "$classes;$classpath" -d $classes (Join-Path $root 'src\cepai\Microphone.java') (Join-Path $root 'src\cepai\AudioPlayback.java') (Join-Path $root 'tests\MediaLifecycleTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Media test compilation failed' }
& $java -cp "$classes;$classpath" cepai.MediaLifecycleTest
if ($LASTEXITCODE -ne 0) { throw 'Media lifecycle tests failed' }
& $javac -encoding UTF-8 -cp $classpath -d $classes (Join-Path $root 'tests\MockHttps.java') (Join-Path $root 'tests\EmulatorTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Emulator test compilation failed' }
& $java '-Djava.awt.headless=true' "-Duser.home=$out" -cp "$classes;$classpath" EmulatorTest (Join-Path $root 'dist\NokiAi.jad') (Join-Path $out 'screenshots') $out
if ($LASTEXITCODE -ne 0) { throw 'Packaged MIDlet tests failed' }
& python (Join-Path $root 'tests\verify_package.py')
if ($LASTEXITCODE -ne 0) { throw 'Package verification failed' }
& node --check (Join-Path $out 'qr-helper-syntax.js')
if ($LASTEXITCODE -ne 0) { throw 'QR helper syntax verification failed' }
