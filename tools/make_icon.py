from pathlib import Path
from PIL import Image, ImageDraw

image = Image.new('RGBA', (32, 32), (16, 25, 35, 255))
draw = ImageDraw.Draw(image)
draw.rounded_rectangle((3, 4, 28, 24), radius=5, fill='#70deb4')
draw.polygon([(7, 22), (7, 29), (14, 23)], fill='#70deb4')
draw.polygon([(16, 7), (18, 12), (23, 14), (18, 16), (16, 21), (14, 16), (9, 14), (14, 12)], fill='#101923')
image.save(Path(__file__).resolve().parent.parent / 'resources' / 'icon.png')
