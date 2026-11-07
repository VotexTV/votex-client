"""Stage actual-mesh SVGs for new wings and revised headsets; rasterize with render-kenney-previews.mjs."""
from pathlib import Path
import json, runpy, sys, shutil

root = Path(__file__).resolve().parents[1]
stage = root / 'work/cosmetics2-20261106/reference-previews'
stage.mkdir(exist_ok=True)
web = root / 'apps/launcher/public/assets/catalog'
ids = ['vanilla-feather-wings', 'crimson-dread-wings', 'autumn-canopy-wings', 'ivory-seraph-wings', 'obsidian-seraph-wings', 'creator-headphones', 'orbit-headphones']
items = json.loads((web / 'index.json').read_text(encoding='utf-8'))
(stage / 'index.json').write_text(json.dumps([i for i in items if i['id'] in ids]), encoding='utf-8')
for id in ids:
    (stage / f'{id}.json').write_bytes((web / f'{id}.json').read_bytes())
    model=json.loads((web / f'{id}.json').read_text(encoding='utf-8'))
    for texture in {p['texture'] for p in model['parts'] if p.get('texture')}:
        (stage / 'textures').mkdir(exist_ok=True)
        shutil.copy2(web / 'textures' / texture, stage / 'textures' / texture)
sys.argv = [str(root / 'scripts/preview-kenney-cosmetics.py'), str(stage)]
runpy.run_path(sys.argv[0], run_name='__main__')
