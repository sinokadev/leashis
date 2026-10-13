#!/usr/bin/env python3
"""Build each declared Minecraft/loader pair and record evidence without claiming runtime coverage."""
import argparse,json,subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--loader',choices=['fabric','neoforge'],required=True);p.add_argument('--versions',nargs='*');p.add_argument('--report-name');a=p.parse_args()
matrix=json.loads((root/'versions.json').read_text())
logs=root/'build/matrix';logs.mkdir(parents=True,exist_ok=True)
report=logs/(a.report_name or f'{a.loader}-results.json')
results={}
for mc,spec in matrix.items():
 if a.versions and mc not in a.versions:continue
 if a.loader=='neoforge' and spec['neoForge'] is None:continue
 log=logs/f'{a.loader}-{mc}.log';start=time.time()
 with log.open('w') as f:
  r=subprocess.run(['./gradlew','--project-cache-dir',str(root/'.gradle'/f'matrix-{a.loader}-{mc}'),f'-Pmc={mc}',f'-Pplatforms={a.loader}',f':{a.loader}:build'],cwd=root,stdout=f,stderr=subprocess.STDOUT)
 results[mc]={'passed':r.returncode==0,'seconds':round(time.time()-start),'log':str(log.relative_to(root))}
 report.write_text(json.dumps(results,indent=2)+'\n')
 print(f'{a.loader} {mc}: {"PASS" if r.returncode==0 else "FAIL"} ({results[mc]["seconds"]}s)',flush=True)
