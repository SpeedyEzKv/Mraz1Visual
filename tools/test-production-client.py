"""Launch the shipped, remapped JAR in an isolated offline profile using cached official runtime files."""
import json, pathlib, platform, re, shutil, subprocess, sys, time
root=pathlib.Path(__file__).resolve().parent.parent
runtime=root/'launcher/portable/data'
game=root/'.migration/production-smoke/1.21.11'
game.mkdir(parents=True,exist_ok=True)
mods=game/'mods';mods.mkdir(exist_ok=True)
jar=root/'mod/build/libs/all/mraz1visual-0.1.0+mc1.21.11.jar'
shutil.copy2(jar,mods/jar.name)
for p in (runtime/'game/mods').glob('*.jar'):
    if p.name.startswith(('fabric-api-','sodium-','iris-')):shutil.copy2(p,mods/p.name)
vanilla=json.loads((runtime/'versions/1.21.11/1.21.11.json').read_text(encoding='utf8'))
fabric=json.loads((runtime/'versions/fabric-loader-0.19.5-1.21.11/fabric-loader-0.19.5-1.21.11.json').read_text(encoding='utf8'))
def allowed(rules):
    if not rules:return True
    result=False
    for rule in rules:
        os=rule.get('os',{})
        if os.get('name','windows')!='windows':continue
        if os.get('arch') and not re.search(os['arch'],'amd64'):continue
        if os.get('version') and not re.search(os['version'],platform.version()):continue
        if any(v for v in rule.get('features',{}).values()):continue
        result=rule.get('action')=='allow'
    return result
def library(lib):
    artifact=lib.get('downloads',{}).get('artifact',{})
    if artifact.get('path'):return runtime/'libraries'/artifact['path']
    parts=lib['name'].split(':');group,name,version=parts[:3];classifier='-'+parts[3] if len(parts)>3 else ''
    return runtime/'libraries'/group.replace('.','/')/name/version/f'{name}-{version}{classifier}.jar'
cp=[]
for lib in fabric['libraries']+vanilla['libraries']:
    if allowed(lib.get('rules')):
        path=library(lib)
        if not path.is_file():raise RuntimeError('Missing cached runtime library: '+str(path))
        if path not in cp:cp.append(path)
cp.append(runtime/'versions/1.21.11/1.21.11.jar')
variables={'natives_directory':str(game/'natives'),'launcher_name':'mraz-production-test','launcher_version':'1','classpath':';'.join(map(str,cp)),
    'classpath_separator':';','library_directory':str(runtime/'libraries'),'version_name':fabric['id'],'version_type':'release','game_directory':str(game),
    'assets_root':str(runtime/'assets'),'assets_index_name':vanilla['assetIndex']['id'],'auth_player_name':'MrazBuildSmoke',
    'auth_uuid':'b27bed781f453816b9c1e85070ab51da','auth_access_token':'0','auth_xuid':'0','clientid':'0','user_type':'legacy','user_properties':'{}'}
def expand(s):
    for k,v in variables.items():s=s.replace('${'+k+'}',v)
    return s
def arguments(arr):
    out=[]
    for a in arr:
        if isinstance(a,str):out.append(expand(a))
        elif allowed(a.get('rules')):
            values=a['value'] if isinstance(a['value'],list) else [a['value']]
            out.extend(map(expand,values))
    return out
args=['-Xmx3G','-Dmraz1.uiSmoke=true','-Dmraz1.worldSmoke=true','-Dmraz1.productionSmoke=true','-Dfabric.development=false']
if '--verify-wardrobe-restart' in sys.argv:args.append('-Dmraz1.verifySavedPulse=true')
args+=arguments(vanilla['arguments']['jvm'])+arguments(fabric['arguments']['jvm'])+[fabric['mainClass']]
args+=arguments(vanilla['arguments']['game'])+arguments(fabric['arguments']['game'])+['--width','1600','--height','900']
args_file=game/'launch.args'
args_file.write_text('\n'.join('"'+a.replace('\\','\\\\').replace('"','\\"')+'"' for a in args),encoding='utf8')
java=next((runtime/'runtime/java21').glob('*/bin/java.exe'))
if '--prepare-only' in sys.argv:
    print('PASS production profile prepared:',len(cp),'cached classpath entries, isolated game directory')
    sys.exit(0)
report=game/'integration-smoke-1.21.11.txt'
if report.exists():report.unlink()
with (game/'production.log').open('w',encoding='utf8') as log:
    child=subprocess.Popen([str(java),'@'+str(args_file)],cwd=game,stdout=log,stderr=subprocess.STDOUT,creationflags=0x08000000)
    try:code=child.wait(timeout=540)
    except subprocess.TimeoutExpired:child.terminate();child.wait(timeout=15);raise RuntimeError('Production smoke timed out')
text=report.read_text(encoding='utf8') if report.exists() else ''
if code or not text or any(line.startswith('FAIL ') for line in text.splitlines()):
    print('\n'.join(line for line in text.splitlines() if line.startswith('FAIL ')))
    raise RuntimeError(f'Production client failed ({code}); see {game}/production.log')
print('PASS production remapped JAR:',sum(line.startswith('PASS ') for line in text.splitlines()),'checks; isolated profile, offline test identity, cached runtime')
