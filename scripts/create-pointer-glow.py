"""One procedural alpha sprite for bounded native GUI lighting; no catalogue assets changed."""
from pathlib import Path
from PIL import Image
root=Path(__file__).resolve().parents[1]
image=Image.new('RGBA',(256,256))
pixels=image.load()
for y in range(256):
 for x in range(256):
  distance=((x-127.5)/127.5)**2+((y-127.5)/127.5)**2
  pixels[x,y]=(255,255,255,round(255*max(0,1-distance)**3))
target=root/'minecraft/votex-core/src/main/resources/assets/votex_client/textures/gui/sprites/ui/pointer_glow.png'
image.save(target)
print(target)
