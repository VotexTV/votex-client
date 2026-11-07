"""Package the current optimized build without working data, credentials or toolchains."""
from pathlib import Path
import hashlib, json, os, re, shutil, zipfile

project = Path(__file__).resolve().parents[1]
version = json.loads((project / 'package.json').read_text())['version']
config = json.loads((project / 'apps/launcher/src-tauri/tauri.conf.json').read_text())
core_version = re.search(r'^mod_version=(.+)$', (project / 'minecraft/votex-core/gradle.properties').read_text(), re.M)[1].strip()
if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[a-z]+\.\d+)?', version):
    raise RuntimeError('Invalid release version')
output = project.parent / 'releases' / version
output.mkdir(parents=True, exist_ok=True)
native = project / 'apps/launcher/src-tauri/target/release'
exe = native / 'votex-launcher.exe'
installer = native / f'bundle/nsis/Votex Client_{config["version"]}_x64-setup.exe'
core = project / f'minecraft/votex-core/build/libs/votex-client-core-{core_version}.jar'
sources = project / f'minecraft/votex-core/build/libs/votex-client-core-{core_version}-sources.jar'
for artifact in [exe, installer, core, sources]:
    if not artifact.is_file(): raise RuntimeError(f'Missing current build: {artifact}')
    if artifact.suffix == '.exe' and artifact.read_bytes()[:2] != b'MZ': raise RuntimeError('Invalid PE artifact')
tag = f'VotexClient-{version}'
portable = output / f'{tag}-Portable'
(portable / 'resources').mkdir(parents=True, exist_ok=True)
shutil.copy2(exe, portable / 'Votex Client.exe')
shutil.copy2(core, portable / 'resources' / core.name)
shutil.copy2(installer, output / f'{tag}-Setup.exe')
shutil.copy2(core, output / core.name)
shutil.copy2(sources, output / sources.name)
for filename in ['README.md','LICENSE','THIRD-PARTY-NOTICES.md']:
    shutil.copy2(project / filename, portable / filename)
(portable/'docs').mkdir(exist_ok=True)
for filename in ['CREATOR-CREDITS.md','CATALOG-COVERAGE.md','AUTHENTICATION.md','TEST-RESULTS.md','CUSTOM-COSMETIC-FORMAT.md','PERFORMANCE-ENGINE.md']:
    shutil.copy2(project/'docs'/filename,portable/'docs'/filename)

excluded = {'node_modules','target','build','dist','.gradle','run','.git','work','evidence','releases'}
def archive(destination, root, allowed=None):
    with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED,compresslevel=6,strict_timestamps=False) as bundle:
        for directory, dirs, files in os.walk(root):
            current = Path(directory)
            dirs[:] = sorted(d for d in dirs if d not in excluded and not (current/d).is_symlink()
                             and (current != root or allowed is None or d in allowed))
            for name in sorted(files):
                file = current/name
                if file.is_symlink() or name.startswith('.env') or name.endswith(('.log','.pdb','.tmp','-source-page.html')): continue
                if current == root and allowed is not None and name not in allowed: continue
                bundle.write(file,str(Path(root.name)/file.relative_to(root)))
    with zipfile.ZipFile(destination) as bundle:
        invalid=bundle.testzip()
        if invalid: raise RuntimeError(f'Corrupt archive: {invalid}')

archive(output/f'{tag}-Portable.zip',portable)
archive(output/f'{tag}-Source.zip',project,{'apps','packages','minecraft','benchmarks','scripts','docs','assets-source','package.json','pnpm-lock.yaml','README.md','LICENSE','THIRD-PARTY-NOTICES.md','.gitignore'})
artifacts={}
for artifact in [output/f'{tag}-Setup.exe',output/f'{tag}-Portable.zip',output/f'{tag}-Source.zip',output/core.name,output/sources.name,portable/'Votex Client.exe']:
    artifacts[str(artifact.relative_to(output)).replace('\\','/')]={'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(),'bytes':artifact.stat().st_size}
manifest={'version':version,'build':'optimized-release','signed':False,'minecraftApiApproval':'pending','artifacts':artifacts}
(output/'SHA256.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'directory':str(output),**manifest},indent=2))
