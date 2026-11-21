"""Keep the native helper's dependency notices with the bundled executable."""
import json,pathlib,subprocess,shutil
root=pathlib.Path(__file__).resolve().parent.parent
metadata=json.loads(subprocess.check_output(['cargo','metadata','--offline','--format-version','1','--filter-platform','x86_64-pc-windows-msvc'],cwd=root/'launcher/src-tauri',stderr=subprocess.DEVNULL))
packages={p['id']:p for p in metadata['packages']}
nodes={n['id']:n for n in metadata['resolve']['nodes']}
roots={'windows','serde','serde_json','hex','sha2','tokio'}
pending=[p['id'] for p in metadata['packages'] if p['name'] in roots]
visited=set();notices=[]
out=root/'mod/src/main/resources/META-INF/licenses/native-media'
while pending:
    identity=pending.pop()
    if identity in visited:continue
    visited.add(identity);package=packages[identity];pending.extend(nodes.get(identity,{}).get('dependencies',[]))
    directory=pathlib.Path(package['manifest_path']).parent
    files=[p for p in directory.iterdir() if p.is_file() and p.name.lower().startswith(('license','copying','notice','copyright'))]
    if package.get('license_file'):
        p=pathlib.Path(package['license_file'])
        if not p.is_absolute():p=directory/p
        if p.is_file() and p not in files:files.append(p)
    destination=out/(package['name']+'-'+package['version']);destination.mkdir(parents=True,exist_ok=True)
    for p in files:shutil.copy2(p,destination/p.name)
    notices.append({'crate':package['name'],'version':package['version'],'license':package.get('license'),'files':[p.name for p in files]})
(out/'dependencies.json').write_text(json.dumps(sorted(notices,key=lambda n:n['crate']),ensure_ascii=False,indent=2),encoding='utf8')
print('PASS native helper notices:',len(notices),'dependency packages,',sum(len(n['files']) for n in notices),'license/notice files')
