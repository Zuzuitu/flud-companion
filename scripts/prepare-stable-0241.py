from pathlib import Path

relay = Path('selfhost/relay/src/index.js')
gradle = Path('app/build.gradle.kts')
changelog = Path('CHANGELOG.md')

s = relay.read_text()
orig = s

s = s.replace(
    '<section class="card"><b data-k="pairing">Saved pairing</b><label>Remote Device ID</label><input id="device" class="input"><label>Remote token</label><input id="token" class="input" type="password"><div class="actions"><button id="save" class="secondary" data-k="save">Save pairing</button><button id="forget" class="danger" data-k="forget">Forget</button></div></section>',
    '<section class="card"><div class="row"><div><b data-k="pairing">Saved pairing</b></div><button id="pairingVisibility" class="secondary">Hide</button></div><div id="pairingDetails"><label>Remote Device ID</label><input id="device" class="input"><label>Remote token</label><input id="token" class="input" type="password"><div class="actions"><button id="save" class="secondary" data-k="save">Save pairing</button><button id="forget" class="danger" data-k="forget">Forget</button></div></div></section>'
)

for old, new in [
    ("clear:'Clear',pairing:'Saved pairing'", "clear:'Clear',pairing:'Saved pairing',hidePairing:'Hide',showPairing:'Unhide'"),
    ("clear:'Șterge',pairing:'Asociere salvată'", "clear:'Șterge',pairing:'Asociere salvată',hidePairing:'Ascunde',showPairing:'Arată'"),
    ("clear:'Effacer',pairing:'Association enregistrée'", "clear:'Effacer',pairing:'Association enregistrée',hidePairing:'Masquer',showPairing:'Afficher'"),
    ("clear:'Löschen',pairing:'Gespeicherte Kopplung'", "clear:'Löschen',pairing:'Gespeicherte Kopplung',hidePairing:'Ausblenden',showPairing:'Einblenden'")
]:
    s = s.replace(old, new)

s = s.replace(
    "count:'fludCompanionSuccessfulSendCountV1'};let helper=false",
    "count:'fludCompanionSuccessfulSendCountV1',pairHidden:'fludRemotePairingHiddenV1'};let helper=false"
)

s = s.replace(
    "function translate(){document.documentElement.lang=lang;document.querySelectorAll('[data-k]').forEach(e=>e.textContent=tr(e.dataset.k));hint();render()}",
    "function pairingVisibility(){const hidden=localStorage.getItem(keys.pairHidden)==='1';$('pairingDetails').classList.toggle('hidden',hidden);$('pairingVisibility').textContent=hidden?tr('showPairing'):tr('hidePairing');$('pairingVisibility').setAttribute('aria-expanded',hidden?'false':'true')}function translate(){document.documentElement.lang=lang;document.querySelectorAll('[data-k]').forEach(e=>e.textContent=tr(e.dataset.k));pairingVisibility();hint();render()}"
)

s = s.replace(
    "$('clear').onclick=()=>{localStorage.removeItem(keys.hist);render()};",
    "$('clear').onclick=()=>{localStorage.removeItem(keys.hist);render()};$('pairingVisibility').onclick=()=>{const hidden=localStorage.getItem(keys.pairHidden)==='1';localStorage.setItem(keys.pairHidden,hidden?'0':'1');pairingVisibility()};"
)

s = s.replace("const C='flud-companion-v0241';", "const C='flud-companion-v0241-stable';")

if s == orig:
    raise SystemExit('No selfhost relay changes applied')
if 'pairingVisibility' not in s or 'fludRemotePairingHiddenV1' not in s:
    raise SystemExit('Pairing visibility patch incomplete')
relay.write_text(s)

g = gradle.read_text()
if 'versionCode = 31' in g:
    g = g.replace('versionCode = 31', 'versionCode = 32')
elif 'versionCode = 32' not in g:
    raise SystemExit('Unexpected versionCode')
gradle.write_text(g)

c = changelog.read_text()
stable = '''## 0.24.1 stable\n- Add a Hide / Unhide control for Saved pairing in the Remote PWA.\n- Small bug fixes and stability improvements.\n\n'''
if '## 0.24.1 stable' not in c:
    marker = '# Changelog\n\n'
    if marker in c:
        c = c.replace(marker, marker + stable, 1)
    else:
        c = stable + c
changelog.write_text(c)

print('Prepared 0.24.1 stable public source.')
