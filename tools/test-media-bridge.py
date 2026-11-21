"""Exercise our own helper and virtual local source; never control the user's SMTC players."""
import concurrent.futures, json, pathlib, subprocess, time, urllib.request, urllib.error, base64
root = pathlib.Path(__file__).resolve().parent.parent
exe = root / 'launcher/src-tauri/target/release/mraz1visual-media.exe'
child = subprocess.Popen([str(exe)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
try:
    handshake = json.loads(child.stdout.readline())
    base = f"http://127.0.0.1:{handshake['port']}"
    def request(path, payload=None, auth=True):
        data = None if payload is None else payload if isinstance(payload,bytes) else json.dumps(payload).encode()
        headers = {'Content-Type':'application/json'}
        if auth: headers['X-Mraz1Visual-Token'] = handshake['token']
        req = urllib.request.Request(base + path, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=6) as r: return r.status, r.read()
        except urllib.error.HTTPError as e: return e.code, e.read()
    assert request('/now', auth=False)[0] == 403
    assert request('/now')[0] == 200
    local = {'title':'Mraz IPC fixture','artist':'Local test','playing':True,'position':1200,'duration':8000,'cover_id':4000000000,'caps':{'toggle':True,'next':True,'prev':True,'seek':True,'volume':True}}
    assert request('/local', local)[0] == 200
    for _ in range(20):
        snapshot = json.loads(request('/now')[1])
        if any(s['id'] == 'local' for s in snapshot['sources']): break
        time.sleep(.1)
    assert request('/select', {'id':'local'})[0] == 200
    for _ in range(20):
        snapshot = json.loads(request('/now')[1])
        if snapshot['selected'] == 'local': break
        time.sleep(.1)
    assert snapshot['title'] == local['title'] and snapshot['caps']['seek']
    first_cover=snapshot['cover_id']
    png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ9kAAAAASUVORK5CYII=')
    assert request('/local/cover',png)[0]==200
    assert request('/local',local)[0]==200
    for _ in range(20):
        late=json.loads(request('/now')[1])
        if late['cover_id']!=first_cover:break
        time.sleep(.1)
    assert late['cover_id']!=first_cover,'late cover never notified the UI'
    assert request('/cover')[1]==png,'metadata refresh discarded the late cover'
    assert request('/command', {'command':'shell'})[0] == 409
    with concurrent.futures.ThreadPoolExecutor() as pool:
        result = pool.submit(request, '/command', {'command':'volume','value':38})
        commands = []
        for _ in range(20):
            commands = json.loads(request('/local/commands')[1])
            if commands: break
            time.sleep(.1)
        assert len(commands) == 1 and commands[0]['value'] == 38
        assert json.loads(request('/local/commands')[1]) == [], 'command delivered more than once'
        assert request('/local/result', {'id':commands[0]['id'],'ok':True})[0] == 200
        assert result.result()[0] == 200
    assert request('/select', {'id':'closed-source'})[0] == 409
    print('PASS auth, native read, local metadata/selection, late cover notification/preservation, capability validation, bounded command queue and actual acknowledgement')
    print('SMTC sources observed:', len([s for s in snapshot['sources'] if s['id'] != 'local']))
finally:
    child.stdin.close()
    try: child.wait(timeout=5)
    except subprocess.TimeoutExpired: child.kill(); child.wait(); raise AssertionError('helper remained alive after parent pipe closed')
print('PASS helper exits on parent shutdown; no external playback commands sent')
