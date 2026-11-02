from pathlib import Path
import re
import struct
import zipfile

root = Path(__file__).resolve().parent.parent
jar = root / 'dist' / 'NokiAi.jar'
jad = (root / 'dist' / 'NokiAi.jad').read_text('ascii')
size = int(re.search(r'^MIDlet-Jar-Size: (\d+)', jad, re.M).group(1))
assert jar.stat().st_size == size
assert re.search(r'^MIDlet-Name: noki Ai\r?$', jad, re.M)
assert re.search(r'^MIDlet-1: noki Ai, /icon.png, cepai.CepAiMIDlet\r?$', jad, re.M)
assert re.search(r'^MIDlet-Version: 1\.2\.1\r?$', jad, re.M)
assert re.search(r'^MIDlet-Jar-URL: NokiAi.jar\r?$', jad, re.M)
with zipfile.ZipFile(jar) as archive:
    manifest = archive.read('META-INF/MANIFEST.MF').decode('ascii')
    assert 'MIDlet-Name: noki Ai\r\n' in manifest
    assert 'MIDlet-1: noki Ai, /icon.png, cepai.CepAiMIDlet\r\n' in manifest
    classes = [name for name in archive.namelist() if name.endswith('.class')]
    assert classes and all(name.startswith(('cepai/', 'com/google/zxing/')) for name in classes)
    stack_maps = 0
    for name in classes:
        data = archive.read(name)
        minor, major = struct.unpack('>HH', data[4:8])
        assert (major, minor) == (45, 3), (name, major, minor)
        assert b'java/lang/StringBuilder' not in data, name
        assert b'java/net/' not in data, name
        stack_maps += b'StackMap' in data
    assert stack_maps > 0
    assert 'licenses/Apache-2.0.txt' in archive.namelist()
    assert archive.read('LICENSE').startswith(b'MIT License')
html = (root / 'dist' / 'key-qr.html').read_text('utf-8')
assert '/* QR_LIBRARY */' not in html
assert "connect-src 'none'" in html
assert '<script src=' not in html
assert '<title>noki Ai' in html
script = '\n'.join(re.findall(r'<script>(.*?)</script>', html, re.S))
(root / 'test-output' / 'qr-helper-syntax.js').write_text(script, encoding='utf-8')
print(f'PASS: {len(classes)} CLDC class files; JAD size, StackMap, licenses and offline helper packaging verified.')
