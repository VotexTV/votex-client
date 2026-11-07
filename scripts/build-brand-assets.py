"""Export Votex's code-native pixel logo as SVG, PNG and Windows ICO.

Requires Pillow. The JSON grid is the single source of truth; no game assets
or downloaded artwork are used. Run before rebuilding the desktop bundles.
"""
from pathlib import Path
import argparse
import json
from PIL import Image, ImageDraw

project = Path(__file__).resolve().parents[1]
spec = json.loads((project / 'packages/brand/pixel-cloud.json').read_text())
grid, palette = spec['grid'], spec['palette']
width, height = len(grid[0]), len(grid)
assert all(len(row) == width and set(row) <= {'.', '#'} for row in grid)

def color(y):
    return palette[min(len(palette)-1, max(0, (y-1)//3))]

rects = []
for y, row in enumerate(grid):
    x = 0
    while x < width:
        if row[x] != '#':
            x += 1
            continue
        end = x + 1
        while end < width and row[end] == '#':
            end += 1
        rects.append(f'<rect x="{x}" y="{y}" width="{end-x}" height="1" fill="{color(y)}"/>')
        x = end
svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" shape-rendering="crispEdges" role="img" aria-label="Votex pixel cloud">' + ''.join(rects) + '</svg>\n'

brand = project / 'packages/brand'
(brand / 'votex-cloud.svg').write_text(svg, encoding='utf-8')
public = project / 'apps/launcher/public/assets'
(public / 'votex-cloud.svg').write_text(svg, encoding='utf-8')

# Pixel rectangles stay aligned and unblurred. The rounded background is only
# the Windows app tile; the cloud itself has a transparent SVG counterpart.
size = 512
tile = Image.new('RGBA', (size, size), (0, 0, 0, 0))
gradient = Image.new('RGBA', (size, size))
pixels = gradient.load()
for y in range(size):
    for x in range(size):
        mix = (x+y)/(size*2)
        glow = max(0, 1-((x-120)**2+(y-100)**2)**0.5/550)
        pixels[x,y] = (int(25+mix*18+glow*20), int(47+mix*6+glow*32), int(92+mix*14+glow*60), 255)
mask = Image.new('L', (size, size), 0)
ImageDraw.Draw(mask).rounded_rectangle((1, 1, size-2, size-2), radius=108, fill=255)
tile.paste(gradient, (0, 0), mask)
draw = ImageDraw.Draw(tile)
draw.rounded_rectangle((2, 2, size-3, size-3), radius=107, outline=(164, 205, 255, 100), width=3)
unit = 432 // width
left = (size-width*unit)//2
top = (size-height*unit)//2
for y, row in enumerate(grid):
    for x, cell in enumerate(row):
        if cell == '#':
            draw.rectangle((left+x*unit, top+y*unit, left+(x+1)*unit-1, top+(y+1)*unit-1), fill=color(y))
tile.save(brand / 'votex-app-icon.png')
icons = project / 'apps/launcher/src-tauri/icons'
tile.resize((256, 256), Image.Resampling.LANCZOS).save(icons / 'icon.png')
tile.save(icons / 'icon.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])

args = argparse.ArgumentParser()
args.add_argument('--site', type=Path)
options = args.parse_args()
if options.site:
    (options.site / 'votex-cloud.svg').write_text(svg, encoding='utf-8')
    # The SVG has a dark rounded background only in its favicon variant.
    side = width + 4
    favicon = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {side} {side}"><rect width="{side}" height="{side}" rx="7" fill="#19305c"/><g transform="translate(2 {(side-height)/2})" shape-rendering="crispEdges">' + ''.join(rects) + '</g></svg>\n'
    (options.site / 'favicon.svg').write_text(favicon, encoding='utf-8')
print('VOTEX_PIXEL_CLOUD_ASSETS_EXPORTED')
