"""Create a GitHub-ready source folder and ZIP from an explicit allowlist."""
from pathlib import Path
import hashlib
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent.parent
EXPORTS = ROOT / 'exports'
STAGE = EXPORTS / 'noki-ai-github'
ARCHIVE = EXPORTS / 'noki-ai-github.zip'

FILES = [
    '.gitignore', '.gitattributes', 'LICENSE', 'README.md', 'README_TR.md',
    'CHANGELOG.md', 'CONTRIBUTING.md', 'THIRD_PARTY.md', 'requirements-dev.txt',
    'build.ps1', 'test.ps1', 'tools/qrcode.js', 'tools/anahtar-qr.template.html',
    'tools/make_icon.py', 'tools/package_project.py',
    'dist/NokiAi.jar', 'dist/NokiAi.jad', 'dist/NokiAi-E63.zip',
    'dist/anahtar-qr.html', 'dist/KURULUM.md',
    'docs/images/home.png', 'docs/images/voice.png',
    'docs/images/chat.png', 'docs/images/settings.png', 'docs/images/menu.png',
]
DIRECTORIES = ('src', 'resources', 'tests')


def selected_files():
    selected = {ROOT / name for name in FILES}
    for directory in DIRECTORIES:
        for path in (ROOT / directory).rglob('*'):
            if path.is_file() and '__pycache__' not in path.parts and path.suffix != '.pyc':
                selected.add(path)
    for path in selected:
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(ROOT):
            raise RuntimeError('Missing or unsafe package input: ' + str(path.relative_to(ROOT)))
        # Real Google keys are never needed in source or installation packages.
        if re.search(rb'AIza[A-Za-z0-9_-]{35}', path.read_bytes()):
            raise RuntimeError('Possible API key; packaging stopped: ' + str(path.relative_to(ROOT)))
    return sorted(selected)


def main():
    selected = selected_files()
    EXPORTS.mkdir(exist_ok=True)
    # Only replace this generated directory, after checking its absolute location.
    if EXPORTS.is_symlink() or EXPORTS.resolve().parent != ROOT.resolve():
        raise RuntimeError('Unsafe exports directory')
    if STAGE.is_symlink() or STAGE.resolve().parent != EXPORTS.resolve():
        raise RuntimeError('Unsafe staging directory')
    if STAGE.exists():
        shutil.rmtree(STAGE)
    STAGE.mkdir()
    sums = []
    for source in selected:
        relative = source.relative_to(ROOT)
        target = STAGE / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        sums.append(hashlib.sha256(target.read_bytes()).hexdigest() + '  ' + relative.as_posix())
    (STAGE / 'SHA256SUMS.txt').write_text('\n'.join(sums) + '\n', encoding='utf-8')
    with zipfile.ZipFile(ARCHIVE, 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(STAGE.rglob('*')):
            if path.is_file():
                archive.write(path, 'noki-ai/' + path.relative_to(STAGE).as_posix())
    with zipfile.ZipFile(ARCHIVE) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('ZIP integrity check failed')
        assert len(archive.namelist()) == len(selected) + 1
    print('Ready: ' + str(STAGE))
    print('ZIP: ' + str(ARCHIVE) + ' (' + str(ARCHIVE.stat().st_size) + ' bytes)')
    print(str(len(selected)) + ' selected files; API key scan and ZIP integrity check passed.')


if __name__ == '__main__':
    main()
