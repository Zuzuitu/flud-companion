const VERSION = "0.24.1-selfhost";
const ONLINE_WINDOW_MS = 25_000;
const LAST_SEEN_WRITE_INTERVAL_MS = 8_000;
const COMMAND_MAX_AGE_MS = 10 * 60 * 1000;
const JSON_HEADERS = { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" };

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: JSON_HEADERS });
}
function text(body, type = "text/html; charset=utf-8", cache = "no-store") {
  return new Response(body, { headers: { "content-type": type, "cache-control": cache } });
}
function bearerToken(request) {
  const value = request.headers.get("authorization") || "";
  if (!value.startsWith("Bearer ")) return null;
  const token = value.slice(7).trim();
  return token.length >= 20 ? token : null;
}
async function tokenHash(token) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
function validDeviceId(value) { return /^[A-Za-z0-9_-]{16,128}$/.test(value); }
function safeError(error) { return (error?.message || String(error || "Unknown error")).slice(0, 300); }
const stateKey = (id) => `devices/${id}/state.json`;
const queuedKey = (id) => `devices/${id}/queued.json`;
const inflightKey = (id) => `devices/${id}/inflight.json`;
async function readJson(bucket, key) {
  const object = await bucket.get(key);
  if (!object) return null;
  try { return JSON.parse(await object.text()); } catch (_) { return null; }
}
async function putJson(bucket, key, value) {
  await bucket.put(key, JSON.stringify(value), { httpMetadata: { contentType: "application/json; charset=utf-8" } });
}
async function authenticate(env, deviceId, request, allowClaim = false) {
  const token = bearerToken(request);
  if (!token) return { ok: false, response: json({ error: "Unauthorized" }, 401) };
  const incomingHash = await tokenHash(token);
  let state = await readJson(env.MAILBOX, stateKey(deviceId));
  if (!state && allowClaim) {
    state = { tokenHash: incomingHash, createdAt: Date.now(), lastSeenAt: 0, deviceVersion: null, autoStartReady: false, autoStartMode: null, lastResult: null };
    await putJson(env.MAILBOX, stateKey(deviceId), state);
    return { ok: true, state };
  }
  if (!state || state.tokenHash !== incomingHash) return { ok: false, response: json({ error: "Unauthorized" }, 401) };
  return { ok: true, state };
}
async function bridgePoll(env, deviceId, request) {
  const auth = await authenticate(env, deviceId, request, true);
  if (!auth.ok) return auth.response;
  const now = Date.now();
  const state = auth.state;
  const version = (request.headers.get("x-flud-bridge-version") || "unknown").slice(0, 32);
  const autoStartReady = request.headers.get("x-flud-autostart") === "ready";
  const autoStartMode = (request.headers.get("x-flud-autostart-mode") || "unknown").slice(0, 64);
  if (!state.lastSeenAt || now - state.lastSeenAt >= LAST_SEEN_WRITE_INTERVAL_MS || state.deviceVersion !== version || state.autoStartReady !== autoStartReady || state.autoStartMode !== autoStartMode) {
    Object.assign(state, { lastSeenAt: now, deviceVersion: version, autoStartReady, autoStartMode });
    await putJson(env.MAILBOX, stateKey(deviceId), state);
  }
  let command = await readJson(env.MAILBOX, queuedKey(deviceId));
  if (command) {
    if (!command.at || now - command.at > COMMAND_MAX_AGE_MS) {
      await env.MAILBOX.delete(queuedKey(deviceId));
      command = null;
    } else {
      await putJson(env.MAILBOX, inflightKey(deviceId), command);
      await env.MAILBOX.delete(queuedKey(deviceId));
    }
  }
  return json({ ok: true, version: VERSION, command });
}
async function bridgeResult(env, deviceId, request) {
  const auth = await authenticate(env, deviceId, request, true);
  if (!auth.ok) return auth.response;
  let body;
  try { body = await request.json(); } catch (_) { return json({ error: "Expected JSON body" }, 400); }
  const id = typeof body?.id === "string" ? body.id.slice(0, 100) : "";
  if (!id) return json({ error: "Missing command id" }, 400);
  const result = { id, ok: body?.ok === true, message: typeof body?.message === "string" ? body.message.slice(0, 500) : null, package: typeof body?.package === "string" ? body.package.slice(0, 200) : null, at: Date.now() };
  Object.assign(auth.state, { lastSeenAt: Date.now(), lastResult: result });
  await putJson(env.MAILBOX, stateKey(deviceId), auth.state);
  await env.MAILBOX.delete(inflightKey(deviceId));
  return json({ ok: true, version: VERSION });
}
async function apiStatus(env, deviceId, request) {
  const auth = await authenticate(env, deviceId, request, false);
  if (!auth.ok) return auth.response;
  const [queued, inflight] = await Promise.all([env.MAILBOX.head(queuedKey(deviceId)), env.MAILBOX.head(inflightKey(deviceId))]);
  return json({ ok: true, online: Boolean(auth.state.lastSeenAt && Date.now() - auth.state.lastSeenAt <= ONLINE_WINDOW_MS), version: VERSION, deviceVersion: auth.state.deviceVersion || null, autoStartReady: auth.state.autoStartReady === true, autoStartMode: auth.state.autoStartMode || null, queueDepth: queued ? 1 : 0, inflight: Boolean(inflight), lastResult: auth.state.lastResult || null });
}
async function apiMagnet(env, deviceId, request) {
  const auth = await authenticate(env, deviceId, request, false);
  if (!auth.ok) return auth.response;
  let body;
  try { body = await request.json(); } catch (_) { return json({ error: "Expected JSON body" }, 400); }
  const magnet = typeof body?.magnet === "string" ? body.magnet.trim() : "";
  const autoStart = body?.autoStart === true;
  const requestId = typeof body?.requestId === "string" ? body.requestId.trim().slice(0, 100) : "";
  if (!magnet.toLowerCase().startsWith("magnet:?") || magnet.length > 12_000) return json({ error: "Invalid magnet URI" }, 400);
  if (requestId && !/^[A-Za-z0-9._:-]{8,100}$/.test(requestId)) return json({ error: "Invalid request ID" }, 400);
  const now = Date.now();
  const state = auth.state;
  if (requestId && state.lastClientRequestId === requestId && state.lastClientRequestAt && now - state.lastClientRequestAt < 120_000) {
    return json({ ok: true, queued: true, duplicate: true, autoStart, id: state.lastClientCommandId || null, version: VERSION }, 202);
  }
  if (!state.lastSeenAt || now - state.lastSeenAt > ONLINE_WINDOW_MS) return json({ error: "Android device is offline" }, 503);
  const [queued, inflight] = await Promise.all([readJson(env.MAILBOX, queuedKey(deviceId)), readJson(env.MAILBOX, inflightKey(deviceId))]);
  if (requestId) {
    const same = [queued, inflight].find((command) => command && command.requestId === requestId);
    if (same) return json({ ok: true, queued: true, duplicate: true, autoStart: same.autoStart === true, id: same.id || state.lastClientCommandId || null, version: VERSION }, 202);
  }
  if (queued || inflight) return json({ error: "A cloud command is already pending" }, 409);
  const command = { type: "magnet", id: crypto.randomUUID(), requestId: requestId || null, magnet, autoStart, at: now };
  await putJson(env.MAILBOX, queuedKey(deviceId), command);
  if (requestId) {
    Object.assign(state, { lastClientRequestId: requestId, lastClientCommandId: command.id, lastClientRequestAt: now });
    await putJson(env.MAILBOX, stateKey(deviceId), state);
  }
  return json({ ok: true, queued: true, autoStart, id: command.id, version: VERSION }, 202);
}

const ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1BC8C3"/><stop offset="1" stop-color="#0C9F9D"/></linearGradient></defs><rect width="512" height="512" rx="112" fill="#071011"/><circle cx="190" cy="256" r="84" fill="none" stroke="url(#g)" stroke-width="42"/><circle cx="322" cy="256" r="84" fill="none" stroke="#EAFBFA" stroke-width="42"/><rect x="221" y="235" width="70" height="42" rx="21" fill="#16B8B5"/><circle cx="256" cy="256" r="12" fill="#071011"/></svg>`;

function setupPage(origin) {
  const safe = String(origin).replace(/[<>]/g, "");
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#03070A"><title>Flud Companion Relay</title><style>:root{color-scheme:dark;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(90% 35% at 50% -5%,rgba(23,184,187,.16),transparent 68%),#020507;color:#f5f8fa;padding:24px 14px}main{max-width:620px;margin:auto}.brand{text-align:center;margin:12px 0 22px}.brand img{width:88px;height:88px;border-radius:24px}.brand h1{margin:10px 0 0;font-size:30px}.brand h1 span{color:#17b8bb;font-weight:450}.brand small{color:#7e8b96;letter-spacing:.18em}.card{border:1px solid rgba(120,181,201,.18);border-radius:24px;padding:22px;background:rgba(8,14,18,.88)}.ready{color:#68dc93;font-weight:700}.muted{color:#8d99a7;line-height:1.55}.url{display:block;overflow-wrap:anywhere;background:#03070b;border:1px solid rgba(127,168,184,.22);border-radius:15px;padding:14px;margin:16px 0}.actions{display:grid;grid-template-columns:1.2fr .8fr;gap:10px}.button,button{border:0;border-radius:15px;min-height:50px;padding:0 16px;font-weight:750;text-decoration:none;display:grid;place-items:center;cursor:pointer}.primary{background:linear-gradient(135deg,#34e0df,#12acc4);color:#001518}.secondary{background:#071017;border:1px solid rgba(126,173,190,.25);color:#dbe3e8}.steps{padding-left:22px;color:#cdd6db;line-height:1.55}.steps li{margin:10px 0}@media(max-width:460px){.actions{grid-template-columns:1fr}}</style></head><body><main><header class="brand"><img src="/icon.svg" alt="Flud Companion"><h1>Flud <span>Companion</span></h1><small>alexlab.media</small></header><section class="card"><div class="ready">● Remote relay online · ${VERSION}</div><h2>Your relay is ready</h2><p class="muted">This relay belongs to your Cloudflare account. There is no shared Flud Companion cloud account.</p><code id="relay" class="url">${safe}</code><div class="actions"><button id="copy" class="primary">Copy relay URL</button><a class="button secondary" href="/app">Open Remote PWA</a></div><ol class="steps"><li>Open Flud Companion Bridge on the Android device.</li><li>Choose <b>Quick setup → LAN + Remote</b>.</li><li>Paste this relay URL once.</li><li>Scan the <b>Remote QR</b> with your phone.</li></ol></section></main><script>copy.onclick=async()=>{try{await navigator.clipboard.writeText(location.origin);copy.textContent='Copied ✓'}catch(e){const r=document.createRange();r.selectNodeContents(relay);const s=getSelection();s.removeAllRanges();s.addRange(r)}}</script></body></html>`;
}

const PAIRING_BOOTSTRAP_SCRIPT = `<script>(()=>{const D='fludRemoteDeviceId',T='fludRemoteCloudToken',C='fludPwaPairBootstrapV1';function valid(d,t){return d.length>=16&&t.length>=20}function setPair(d,t){if(!valid(d,t))return;const v=encodeURIComponent(JSON.stringify({d:d,t:t}));document.cookie=C+'='+v+'; Max-Age=86400; Path=/app; Secure; SameSite=Strict'}function getPair(){const p=document.cookie.split('; ').find(x=>x.startsWith(C+'='));if(!p)return null;try{return JSON.parse(decodeURIComponent(p.slice(C.length+1)))}catch(e){return null}}function clearPair(){document.cookie=C+'=; Max-Age=0; Path=/app; Secure; SameSite=Strict'}const q=new URLSearchParams(location.hash.replace(/^#/,'')),hd=(q.get('device')||'').trim(),ht=(q.get('token')||'').trim();if(valid(hd,ht)){localStorage.setItem(D,hd);localStorage.setItem(T,ht);setPair(hd,ht)}else if(!localStorage.getItem(D)||!localStorage.getItem(T)){const p=getPair();if(p&&valid(String(p.d||''),String(p.t||''))){localStorage.setItem(D,String(p.d));localStorage.setItem(T,String(p.t));clearPair()}}const s=document.getElementById('save'),f=document.getElementById('forget');if(s)s.addEventListener('click',()=>setTimeout(()=>setPair(document.getElementById('device').value.trim(),document.getElementById('token').value.trim()),0));if(f)f.addEventListener('click',clearPair)})();</script>`;

const APP_HTML = `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"><meta name="theme-color" content="#03070A"><meta name="apple-mobile-web-app-capable" content="yes"><link rel="manifest" href="/manifest.webmanifest"><link rel="icon" href="/icon.svg"><title>Flud Companion</title><style>:root{color-scheme:dark;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;--a:#17b8bb;--line:rgba(120,181,201,.18);--muted:#8d99a7}*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(90% 34% at 50% -6%,rgba(23,184,187,.14),transparent 66%),linear-gradient(180deg,#04090d,#010305 70%,#000);color:#f5f8fa;padding:18px 14px}main{max-width:620px;margin:auto}.top{display:flex;justify-content:flex-end;gap:8px}.top button,.top select{height:34px;border:1px solid rgba(23,184,187,.24);border-radius:11px;background:#050c10;color:#b8c5cb;padding:0 10px}.brand{text-align:center;margin:18px 0}.brand img{width:82px;height:82px;border-radius:23px}.brand h1{margin:8px 0 2px;font-size:30px}.brand h1 span{color:var(--a);font-weight:450}.brand small{color:#6f7d88}.mode{text-align:center;color:var(--a);margin-bottom:14px}.card{border:1px solid var(--line);border-radius:22px;background:rgba(8,14,18,.88);padding:17px;margin:10px 0}.row{display:flex;justify-content:space-between;align-items:center;gap:12px}.status{font-size:18px;font-weight:750}.dot{display:inline-block;width:8px;height:8px;border-radius:50%;background:#66717b;margin-right:7px}.dot.on{background:#56db8b}.dot.off{background:#ff6f79}.muted{color:var(--muted);font-size:13px;line-height:1.45}.input,textarea{width:100%;background:#03070b;border:1px solid rgba(127,168,184,.24);border-radius:15px;color:#f5f8fa;padding:13px;font:14px/1.45 inherit;margin-top:7px;outline:none}textarea{min-height:122px;resize:vertical}label{display:block;color:#8c98a6;font-size:12px;margin:10px 0 0}.actions{display:grid;grid-template-columns:1.2fr .8fr;gap:10px;margin-top:13px}button{cursor:pointer}.primary,.secondary,.danger{min-height:48px;border-radius:15px;border:0;padding:0 14px;font-weight:750}.primary{background:linear-gradient(135deg,#25c9c9,#0e9fb5);color:#001518}.secondary{background:#071017;border:1px solid rgba(126,173,190,.25);color:#dbe3e8}.danger{background:rgba(89,29,36,.3);border:1px solid rgba(255,111,121,.24);color:#ff8a91}.toggle{display:flex;justify-content:space-between;align-items:center;margin:14px 0 0;padding-top:12px;border-top:1px solid rgba(127,168,184,.12)}.msg{min-height:18px;font-size:12px;margin-top:9px}.ok{color:#73e39b}.err{color:#ff8991}.history div{padding:10px 2px;border-bottom:1px solid rgba(127,168,184,.1);font-size:13px}.tiny{text-align:center;color:#596570;font-size:10px;line-height:1.55;margin:22px 8px}.modal{position:fixed;inset:0;background:rgba(0,0,0,.72);display:none;align-items:flex-end;padding:14px}.modal.show{display:flex}.sheet{max-width:620px;width:100%;margin:auto;background:#071015;border:1px solid var(--line);border-radius:24px;padding:20px;max-height:88vh;overflow:auto}.sheet h2{margin-top:0}.sheet li{margin:10px 0;color:#cdd6db;line-height:1.5}@media(max-width:390px){.actions{grid-template-columns:1fr}}</style></head><body><main><div class="top"><button id="help">? <span data-k="help">How-to</span></button><select id="lang"><option value="en">EN</option><option value="ro">RO</option><option value="fr">FR</option><option value="de">DE</option></select><button id="beer">🍺</button></div><header class="brand"><img src="/icon.svg"><h1>Flud <span>Companion</span></h1><small>alexlab.media · Remote ${VERSION}</small></header><div class="mode">REMOTE · SELF-HOSTED</div><section class="card"><div class="row"><div><div class="muted" data-k="android">Android device</div><div class="status"><span id="dot" class="dot"></span><span id="state">Not paired</span></div></div><button id="refresh" class="secondary">↻</button></div><div id="detail" class="muted"></div></section><section class="card"><label data-k="magnet">Magnet link</label><textarea id="magnet" placeholder="magnet:?xt=urn:btih:..."></textarea><div class="toggle"><div><b data-k="auto">Auto-start download</b><div id="autoHint" class="muted"></div></div><input id="autoStart" type="checkbox"></div><div class="actions"><button id="send" class="primary" data-k="send">Send to Flud</button><button id="paste" class="secondary" data-k="paste">Paste</button></div><div id="message" class="msg"></div></section><section class="card"><div class="row"><div><b data-k="recent">Recent sends</b><div class="muted" data-k="browserOnly">Stored only in this browser</div></div><button id="clear" class="secondary" data-k="clear">Clear</button></div><div id="history" class="history"></div></section><section class="card"><b data-k="pairing">Saved pairing</b><label>Remote Device ID</label><input id="device" class="input"><label>Remote token</label><input id="token" class="input" type="password"><div class="actions"><button id="save" class="secondary" data-k="save">Save pairing</button><button id="forget" class="danger" data-k="forget">Forget</button></div></section><footer class="tiny">Independent companion for Flud. Not affiliated with, endorsed by, or sponsored by Delphi Softwares or the Flud developers.<br>“Flud” is used only to identify compatibility · alexlab.media</footer></main><div id="helpModal" class="modal"><div class="sheet"><h2 data-k="guide">Setup guide</h2><ol><li data-k="s1">Install Flud/Flud+ and Flud Companion Bridge on Android.</li><li data-k="s2">In Bridge choose Quick setup → LAN + Remote.</li><li data-k="s3">Deploy this relay in your own Cloudflare account and copy its HTTPS URL.</li><li data-k="s4">Paste the relay URL into Bridge and enable Remote relay.</li><li data-k="s5">Scan Remote QR with your phone. Device ID and token are saved locally in this browser.</li></ol><button id="closeHelp" class="secondary">Close</button></div></div><div id="beerModal" class="modal"><div class="sheet"><h2>🍺 <span data-k="support">Offer me a beer</span></h2><p class="muted" data-k="supportText">If Flud Companion saves you time, an optional small donation is appreciated. Thank you.</p><a href="https://www.paypal.me/AlexandruCiobanu00" target="_blank" rel="noopener" class="primary" style="display:grid;place-items:center;text-decoration:none;min-height:48px;border-radius:15px">PayPal</a><button id="closeBeer" class="secondary" style="width:100%;margin-top:10px">Close</button></div></div>${PAIRING_BOOTSTRAP_SCRIPT}<script>(()=>{const T={en:{help:'How-to',android:'Android device',magnet:'Magnet link',auto:'Auto-start download',send:'Send to Flud',paste:'Paste',recent:'Recent sends',browserOnly:'Stored only in this browser',clear:'Clear',pairing:'Saved pairing',save:'Save pairing',forget:'Forget',guide:'Setup guide',s1:'Install Flud/Flud+ and Flud Companion Bridge on Android.',s2:'In Bridge choose Quick setup → LAN + Remote.',s3:'Deploy this relay in your own Cloudflare account and copy its HTTPS URL.',s4:'Paste the relay URL into Bridge and enable Remote relay.',s5:'Scan Remote QR with your phone. Device ID and token are saved locally in this browser.',support:'Offer me a beer',supportText:'If Flud Companion saves you time, an optional small donation is appreciated. Thank you.'},ro:{help:'Ghid',android:'Dispozitiv Android',magnet:'Link magnet',auto:'Pornire automată descărcare',send:'Trimite către Flud',paste:'Lipește',recent:'Trimiteri recente',browserOnly:'Salvate doar în acest browser',clear:'Șterge',pairing:'Asociere salvată',save:'Salvează asocierea',forget:'Uită',guide:'Ghid de configurare',s1:'Instalează Flud/Flud+ și Flud Companion Bridge pe Android.',s2:'În Bridge alege Quick setup → LAN + Remote.',s3:'Instalează acest relay în propriul cont Cloudflare și copiază URL-ul HTTPS.',s4:'Lipește URL-ul relay-ului în Bridge și activează Remote relay.',s5:'Scanează Remote QR cu telefonul. Device ID și tokenul sunt salvate local în acest browser.',support:'Oferă-mi o bere',supportText:'Dacă Flud Companion îți economisește timp, o mică donație opțională este apreciată. Mulțumesc!'},fr:{help:'Guide',android:'Appareil Android',magnet:'Lien magnet',auto:'Démarrage automatique',send:'Envoyer à Flud',paste:'Coller',recent:'Envois récents',browserOnly:'Stockés uniquement dans ce navigateur',clear:'Effacer',pairing:'Association enregistrée',save:'Enregistrer',forget:'Oublier',guide:'Guide de configuration',s1:'Installez Flud/Flud+ et Flud Companion Bridge sur Android.',s2:'Dans Bridge choisissez Quick setup → LAN + Remote.',s3:'Déployez ce relay dans votre propre compte Cloudflare et copiez son URL HTTPS.',s4:'Collez l’URL du relay dans Bridge et activez Remote relay.',s5:'Scannez Remote QR avec le téléphone. Device ID et token restent dans ce navigateur.',support:'Offrez-moi une bière',supportText:'Si Flud Companion vous fait gagner du temps, un petit don facultatif est apprécié. Merci !'},de:{help:'Anleitung',android:'Android-Gerät',magnet:'Magnet-Link',auto:'Download automatisch starten',send:'An Flud senden',paste:'Einfügen',recent:'Letzte Sendungen',browserOnly:'Nur in diesem Browser gespeichert',clear:'Löschen',pairing:'Gespeicherte Kopplung',save:'Kopplung speichern',forget:'Vergessen',guide:'Einrichtungsanleitung',s1:'Installiere Flud/Flud+ und Flud Companion Bridge auf Android.',s2:'Wähle in Bridge Quick setup → LAN + Remote.',s3:'Stelle diesen Relay in deinem eigenen Cloudflare-Konto bereit und kopiere die HTTPS-URL.',s4:'Füge die Relay-URL in Bridge ein und aktiviere Remote relay.',s5:'Scanne Remote QR mit dem Telefon. Device ID und Token bleiben in diesem Browser.',support:'Spendier mir ein Bier',supportText:'Wenn Flud Companion dir Zeit spart, ist eine kleine freiwillige Spende willkommen. Danke!'}};const $=id=>document.getElementById(id),keys={lang:'fludCompanionLang',device:'fludRemoteDeviceId',token:'fludRemoteCloudToken',auto:'fludRemoteAutoStartV1',hist:'fludRemoteHistoryV1',count:'fludCompanionSuccessfulSendCountV1'};let helper=false,lang=localStorage.getItem(keys.lang)||((navigator.language||'en').slice(0,2));if(!T[lang])lang='en';$('lang').value=lang;$('device').value=localStorage.getItem(keys.device)||'';$('token').value=localStorage.getItem(keys.token)||'';$('autoStart').checked=localStorage.getItem(keys.auto)==='1';function tr(k){return T[lang][k]||T.en[k]||k}function translate(){document.documentElement.lang=lang;document.querySelectorAll('[data-k]').forEach(e=>e.textContent=tr(e.dataset.k));hint();render()}function ready(){return $('device').value.trim().length>=16&&$('token').value.trim().length>=20}function auth(){return{Authorization:'Bearer '+$('token').value.trim()}}function msg(x,ok=false){$('message').textContent=x;$('message').className='msg '+(ok?'ok':'err')}function hint(){$('autoHint').textContent=$('autoStart').checked?(helper?'Ready':'Accessibility helper must be enabled on Android.'):'Optional'}function consume(){const q=new URLSearchParams(location.hash.replace(/^#/,'')),d=(q.get('device')||'').trim(),t=(q.get('token')||'').trim();if(d.length>=16&&t.length>=20){$('device').value=d;$('token').value=t;localStorage.setItem(keys.device,d);localStorage.setItem(keys.token,t);history.replaceState(null,'',location.pathname+location.search)}}async function status(){if(!ready()){helper=false;$('state').textContent='Not paired';$('dot').className='dot';$('detail').textContent='Scan Remote QR or enter pairing details.';hint();return}try{const r=await fetch('/api/v1/device/'+encodeURIComponent($('device').value.trim())+'/status',{headers:auth(),cache:'no-store'}),j=await r.json();if(!r.ok)throw Error(j.error||r.status);helper=!!j.autoStartReady;$('state').textContent=j.online?'Online':'Offline';$('dot').className='dot '+(j.online?'on':'off');$('detail').textContent='Android '+(j.deviceVersion||'?')+' · queue '+(j.queueDepth||0)+(j.inflight?' · command in progress':'')+(helper?' · auto-start ready':' · auto-start helper off');hint()}catch(e){helper=false;$('state').textContent='Unavailable';$('dot').className='dot off';$('detail').textContent=e.message;hint()}}function hist(){try{return JSON.parse(localStorage.getItem(keys.hist)||'[]')}catch(e){return[]}}function render(){const h=$('history');h.innerHTML='';const list=hist();if(!list.length){h.innerHTML='<div class="muted">No sends yet.</div>';return}list.forEach(x=>{const d=document.createElement('div');d.textContent=(x.name||'Magnet')+' · '+new Date(x.at).toLocaleString(lang)+(x.auto?' · auto-start':'');d.onclick=()=>{$('magnet').value=x.magnet;$('autoStart').checked=!!x.auto;hint()};h.appendChild(d)})}function addHist(m,auto){let l=hist().filter(x=>x.magnet!==m);let name='Magnet';try{name=new URLSearchParams(m.split('?').slice(1).join('?')).get('dn')||'Magnet'}catch(e){}l.unshift({magnet:m,name,auto,at:Date.now()});localStorage.setItem(keys.hist,JSON.stringify(l.slice(0,12)));render()}function rid(){return crypto.randomUUID?crypto.randomUUID():'D'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2)}async function send(){if(!ready())return msg('Pair this browser first.');const m=$('magnet').value.trim();if(!m.toLowerCase().startsWith('magnet:?'))return msg('Paste a valid magnet link first.');if($('autoStart').checked&&!helper)return msg('Auto-start helper is not enabled on Android.');const payload={magnet:m,autoStart:$('autoStart').checked,requestId:rid()};let last;for(let i=0;i<3;i++){try{const r=await fetch('/api/v1/device/'+encodeURIComponent($('device').value.trim())+'/magnet',{method:'POST',headers:{...auth(),'Content-Type':'application/json'},body:JSON.stringify(payload)}),j=await r.json();if(r.ok){addHist(m,payload.autoStart);let n=Number(localStorage.getItem(keys.count)||0)+1;localStorage.setItem(keys.count,String(n));msg('Queued. Android should receive it within a few seconds.',true);$('magnet').value='';setTimeout(status,1800);if(n%50===0)setTimeout(()=>$('beerModal').classList.add('show'),400);return}last=Error(j.error||r.status);if(![429,500,502,503,504].includes(r.status))throw last}catch(e){last=e;if(i===2)break}await new Promise(r=>setTimeout(r,i?1300:650))}msg(last?.message||'Request failed')}consume();$('save').onclick=()=>{localStorage.setItem(keys.device,$('device').value.trim());localStorage.setItem(keys.token,$('token').value.trim());status()};$('forget').onclick=()=>{localStorage.removeItem(keys.device);localStorage.removeItem(keys.token);$('device').value='';$('token').value='';status()};$('refresh').onclick=status;$('send').onclick=send;$('paste').onclick=async()=>{try{$('magnet').value=await navigator.clipboard.readText()}catch(e){msg('Long-press the field and paste manually.')}};$('autoStart').onchange=()=>{localStorage.setItem(keys.auto,$('autoStart').checked?'1':'0');hint()};$('clear').onclick=()=>{localStorage.removeItem(keys.hist);render()};$('help').onclick=()=>$('helpModal').classList.add('show');$('closeHelp').onclick=()=>$('helpModal').classList.remove('show');$('beer').onclick=()=>$('beerModal').classList.add('show');$('closeBeer').onclick=()=>$('beerModal').classList.remove('show');$('lang').onchange=()=>{lang=$('lang').value;localStorage.setItem(keys.lang,lang);translate()};if('serviceWorker'in navigator)navigator.serviceWorker.register('/sw.js').catch(()=>{});translate();status()})();</script></body></html>`;

function manifest() {
  return text(JSON.stringify({ name: "Flud Companion", short_name: "Flud Companion", start_url: "/app", scope: "/", display: "standalone", background_color: "#020508", theme_color: "#03070A", icons: [{ src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any maskable" }] }), "application/manifest+json; charset=utf-8", "public, max-age=3600");
}
function serviceWorker() {
  const code = "const C='flud-companion-v0241';self.addEventListener('install',e=>e.waitUntil(caches.open(C).then(c=>c.addAll(['/app','/manifest.webmanifest','/icon.svg']))));self.addEventListener('activate',e=>e.waitUntil(Promise.all([self.clients.claim(),caches.keys().then(ks=>Promise.all(ks.filter(k=>k!==C&&k.startsWith('flud-companion-')).map(k=>caches.delete(k))))])));self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;if(e.request.mode==='navigate'){e.respondWith(fetch(e.request).catch(()=>caches.match('/app')));return}e.respondWith(fetch(e.request).catch(()=>caches.match(e.request)));});";
  return text(code, "application/javascript; charset=utf-8", "no-cache");
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (!env.MAILBOX) return json({ error: "MAILBOX R2 binding is missing", version: VERSION }, 503);
    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/setup")) return text(setupPage(url.origin));
    if (request.method === "GET" && (url.pathname === "/app" || url.pathname === "/app/")) return text(APP_HTML);
    if (request.method === "GET" && url.pathname === "/icon.svg") return text(ICON_SVG, "image/svg+xml; charset=utf-8", "public, max-age=86400");
    if (request.method === "GET" && url.pathname === "/manifest.webmanifest") return manifest();
    if (request.method === "GET" && url.pathname === "/sw.js") return serviceWorker();
    if (request.method === "GET" && url.pathname === "/health") return json({ status: "ok", version: VERSION, transport: "https-r2-mailbox" });
    if (request.method === "GET" && url.pathname === "/relay.json") return json({ name: "Flud Companion Relay", version: VERSION, status: "ok", transport: "https-r2-mailbox", app: `${url.origin}/app` });

    let match = url.pathname.match(/^\/bridge\/(poll|result)\/([A-Za-z0-9_-]+)$/);
    if (match) {
      const [, action, deviceId] = match;
      if (!validDeviceId(deviceId)) return json({ error: "Invalid device ID" }, 400);
      try {
        if (action === "poll") return request.method === "GET" ? await bridgePoll(env, deviceId, request) : json({ error: "Method not allowed" }, 405);
        return request.method === "POST" ? await bridgeResult(env, deviceId, request) : json({ error: "Method not allowed" }, 405);
      } catch (error) {
        console.error("bridge request failed", safeError(error));
        return json({ error: "Relay internal error", detail: safeError(error), version: VERSION }, 500);
      }
    }

    match = url.pathname.match(/^\/api\/v1\/device\/([A-Za-z0-9_-]+)\/(magnet|status)$/);
    if (match) {
      const [, deviceId, action] = match;
      if (!validDeviceId(deviceId)) return json({ error: "Invalid device ID" }, 400);
      try {
        if (action === "status") return request.method === "GET" ? await apiStatus(env, deviceId, request) : json({ error: "Method not allowed" }, 405);
        return request.method === "POST" ? await apiMagnet(env, deviceId, request) : json({ error: "Method not allowed" }, 405);
      } catch (error) {
        console.error("api request failed", safeError(error));
        return json({ error: "Relay internal error", detail: safeError(error), version: VERSION }, 500);
      }
    }
    if (/^\/bridge\/[A-Za-z0-9_-]+$/.test(url.pathname)) return json({ error: "WebSocket relay retired; Android Bridge v0.4+ required" }, 410);
    return json({ error: "Not found", version: VERSION }, 404);
  },
};
