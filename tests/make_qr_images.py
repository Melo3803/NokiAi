import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

output = Path(__file__).resolve().parent.parent / 'test-output'
data = json.loads((output / 'qr-matrices.json').read_text())
for index, fixture in enumerate(data):
    matrix = fixture['modules']
    scale, quiet = 8, 4
    size = (len(matrix) + 2 * quiet) * scale
    image = Image.new('RGB', (size, size), 'white')
    draw = ImageDraw.Draw(image)
    for y, row in enumerate(matrix):
        for x, dark in enumerate(row):
            if dark:
                draw.rectangle(((x + quiet) * scale, (y + quiet) * scale,
                                (x + quiet + 1) * scale - 1, (y + quiet + 1) * scale - 1), fill='black')
    image.save(output / f'qr-{index}.png')
    if index == 0:
        image.rotate(90, expand=True).save(output / 'qr-rotated.png')
        image.filter(ImageFilter.GaussianBlur(0.6)).save(output / 'qr-camera.jpg', quality=80)
Image.new('RGB', (320, 240), 'white').save(output / 'blank.png')
print('Created PNG, rotated and JPEG QR fixtures; all keys are synthetic.')
