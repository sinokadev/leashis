#!/usr/bin/env python3
"""Package only complete, verified build matrices; reject missing metadata and test classes."""
import hashlib,json,shutil,zipfile
from pathlib import Path
root=Path(__file__).resolve().parent.parent
matrix=json.loads((root/'versions.json').read_text());version='2.0.7'
dist=root/'dist';dist.mkdir(exist_ok=True)
artifacts=[]
def add(path,loader,mc,java):
 if not path.is_file():raise SystemExit(f'Missing artifact: {path}')
 with zipfile.ZipFile(path) as z:
  names=set(z.namelist())
  if any('/gametest/' in n for n in names):raise SystemExit(f'Test code leaked: {path}')
  if 'dev/sinoka/leashis/core/LeashEngine.class' not in names:raise SystemExit(f'Core missing: {path}')
  if loader=='fabric':
   manifest=json.loads(z.read('fabric.mod.json'))
   if manifest['depends']['minecraft']!=mc or manifest['environment']!='server':raise SystemExit(f'Invalid target metadata: {path}')
  if loader=='neoforge':
   metadata='META-INF/mods.toml' if mc in ['1.20.2','1.20.3','1.20.4'] else 'META-INF/neoforge.mods.toml'
   if metadata not in names or f'versionRange="[{mc}]"' not in z.read(metadata).decode():raise SystemExit(f'Invalid target metadata: {path}')
  for name in names:
   if name.endswith('.class'):
    major=int.from_bytes(z.read(name)[6:8],'big')
    if major>java+44:raise SystemExit(f'Java version too new: {path}: {name}')
 target=dist/path.name;shutil.copy2(path,target)
 artifacts.append({'file':target.name,'loader':loader,'minecraft':mc,'java':java,'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
add(root/f'bukkit/build/libs/leashis-bukkit-{version}.jar','bukkit',list(matrix),17)
for loader in ['fabric','neoforge']:
 report=root/f'build/matrix/{loader}-results.json'
 if not report.is_file():raise SystemExit(f'Missing build evidence: {report}')
 results=json.loads(report.read_text())
 for mc,spec in matrix.items():
  if loader=='neoforge' and spec['neoForge'] is None:continue
  if not results.get(mc,{}).get('passed'):raise SystemExit(f'Unverified build: {loader} {mc}')
  add(root/f'{loader}/build/{mc}/libs/leashis-{loader}-{mc}-{version}.jar',loader,mc,spec['java'])
(dist/'manifest.json').write_text(json.dumps({'version':version,'artifacts':artifacts},indent=2)+'\n')
(dist/'SHA256SUMS').write_text(''.join(f"{a['sha256']}  {a['file']}\n" for a in artifacts))
shutil.copy2(root/'README.md',dist/'README.md');shutil.copy2(root/'versions.json',dist/'versions.json')
shutil.copytree(root/'licenses',dist/'licenses',dirs_exist_ok=True)
shutil.copy2(root/'TEMPLATE_LICENSE.txt',dist/'TEMPLATE_LICENSE.txt')
shutil.copy2(root/'LICENSE',dist/'LICENSE')
shutil.copytree(root/'docs',dist/'docs',dirs_exist_ok=True)
bundle=dist/f'leashis-{version}-all-platforms.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED) as z:
 for path in sorted(dist.rglob('*')):
  if path.is_file() and path!=bundle:z.write(path,path.relative_to(dist))
print(f'Packaged {len(artifacts)} verified JARs: {bundle}')
