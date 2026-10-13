#!/usr/bin/env python3
"""Start an isolated local development server, optionally run protocol clients, then stop it."""
import argparse,json,os,queue,subprocess,threading,time
from pathlib import Path
root=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser();p.add_argument('--loader',choices=['fabric','neoforge','paper','spigot','craftbukkit'],required=True);p.add_argument('--mc',required=True);p.add_argument('--port',type=int,default=25575);p.add_argument('--client-script');p.add_argument('--paper-dir');p.add_argument('--java',default='java');a=p.parse_args()
if a.loader in ['paper','spigot','craftbukkit']:
 work=Path(a.paper_dir);command=[a.java,'-Xmx1G','-jar','server.jar','--nogui'];cwd=work
else:
 work=root/a.loader/'run'/a.mc;command=['./gradlew','--project-cache-dir',str(root/'.gradle'/f'smoke-{a.loader}-{a.mc}'),f'-Pmc={a.mc}',f'-Pplatforms={a.loader}',f':{a.loader}:runServer'];cwd=root
work.mkdir(parents=True,exist_ok=True)
(work/'eula.txt').write_text('eula=true\n')
(work/'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={a.port}
online-mode=false
view-distance=2
simulation-distance=2
spawn-protection=0
level-type=minecraft:flat
generate-structures=false
max-tick-time=-1
sync-chunk-writes=false
pause-when-empty-seconds=-1
''')
logdir=root/'build/smoke';logdir.mkdir(parents=True,exist_ok=True)
log=(logdir/f'{a.loader}-{a.mc}.log').open('w')
process=subprocess.Popen(command,cwd=cwd,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1,start_new_session=True)
lines=queue.Queue()
def read():
 for line in process.stdout:log.write(line);log.flush();lines.put(line)
 lines.put(None)
reader=threading.Thread(target=read,daemon=True);reader.start()
ready=False;deadline=time.time()+300
try:
 while time.time()<deadline:
  try:line=lines.get(timeout=1)
  except queue.Empty:continue
  if line is None:break
  if 'Done (' in line:
   ready=True;break
 if not ready:raise RuntimeError('Server did not start; see smoke log')
 print(f'{a.loader} {a.mc}: server started',flush=True)
 if a.client_script:
  client=subprocess.Popen(['node',a.client_script,str(a.port),a.mc],cwd=root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
  for line in client.stdout:
   log.write('[client] '+line);log.flush()
   if line.startswith('CONSOLE '):
    process.stdin.write(line[len('CONSOLE '):]);process.stdin.flush()
   else:print(line,end='',flush=True)
  if client.wait()!=0:raise RuntimeError('Protocol client regression failed')
 else:
  time.sleep(2)
 process.stdin.write('stop\n');process.stdin.flush()
 process.wait(timeout=60)
 if process.returncode != 0:raise RuntimeError('Server process failed during shutdown')
 print(f'{a.loader} {a.mc}: smoke PASS',flush=True)
finally:
 if process.poll() is None:
  import signal
  os.killpg(process.pid,signal.SIGTERM)
  try: process.wait(timeout=10)
  except subprocess.TimeoutExpired: os.killpg(process.pid,signal.SIGKILL);process.wait()
 reader.join(timeout=10)
 log.close()
