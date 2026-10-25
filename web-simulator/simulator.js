const $ = id => document.getElementById(id);
const apiOrigin = new URLSearchParams(location.search).get('api') ||
  (location.port === '8080' ? location.origin : `${location.protocol}//${location.hostname}:8080`);
const storageKey = 'biopulse-simulator-sessions-v1';
const metrics = {HR:['Heart rate','bpm'],PULSE:['Pulse rate','bpm'],SPO2:['Oxygen saturation','%'],RR:['Respiratory rate','breaths/min'],SBP:['Systolic pressure','mmHg'],DBP:['Diastolic pressure','mmHg'],TEMP:['Temperature','Cel']};
const colors = {ECG:'#8bd8a4',PPG:'#86cce1',RESP:'#c4b0e7'};
let csrf = '', sources = [], recordings = [], admissions = [], devices = [], selectedSession = null;
let socket = null, reconnectTimer = null, heartbeatTimer = null, seen = new Set();
let history = [];
try { history = JSON.parse(localStorage.getItem(storageKey) || '[]'); if (!Array.isArray(history)) history = []; } catch { history = []; }
const sourceById = id => sources.find(s => String(s.id) === String(id));
const recordingById = id => recordings.find(r => String(r.id) === String(id));
const selectedAdmission = () => Number($('admission').value);
const selectedRecording = () => Number($('recording').value);
const fmt = value => value == null ? '—' : String(value);

async function request(path, options = {}) {
  const response = await fetch(`${apiOrigin}/api/v1${path}`, {
    credentials:'include', ...options,
    headers:{'Content-Type':'application/json', ...(options.method ? {'X-XSRF-TOKEN':csrf} : {}), ...options.headers}
  });
  let body = null;
  try { body = await response.json(); } catch {}
  if (!response.ok) throw new Error(body?.detail || body?.title || `HTTP ${response.status}`);
  if (body?.csrfToken) csrf = body.csrfToken;
  return body;
}
const post = (path, body={}) => request(path,{method:'POST',body:JSON.stringify(body)});
function notice(message) { $('notice').textContent = message; if(message) log(message); }
function log(message) {
  const item = document.createElement('li');
  const time = document.createElement('time'); time.textContent = new Date().toLocaleTimeString();
  item.append(time, document.createTextNode(message)); $('events').prepend(item);
  while ($('events').children.length > 70) $('events').lastElementChild.remove();
}
function sourceOffset(at, recording=recordingById(selectedRecording())) {
  if (!at || !recording) return '—';
  const seconds = Math.max(0, (Date.parse(at)-Date.parse(recording.startAt))/1000);
  const minutes = Math.floor(seconds/60).toString().padStart(2,'0');
  return `${minutes}:${(seconds%60).toFixed(2).padStart(5,'0')}`;
}
function option(select, value, label) { const o=document.createElement('option'); o.value=value; o.textContent=label; select.append(o); }
function renderCatalog() {
  $('recording').replaceChildren(); option($('recording'),'','Select recording');
  for (const r of recordings.filter(r => sourceById(r.sourceId)?.name?.toUpperCase().includes('BIDMC')))
    option($('recording'), r.id, `${r.name} · ${((Date.parse(r.endAt)-Date.parse(r.startAt))/60000).toFixed(1)} min`);
  $('admission').replaceChildren(); option($('admission'),'','Select admission');
  for (const a of admissions.filter(a => a.status === 'ACTIVE')) option($('admission'),a.id,`Admission ${a.id} · patient ${a.patientId}`);
  renderHistory();
}
function renderRecording() {
  const r=recordingById(selectedRecording()), s=r&&sourceById(r.sourceId);
  $('source-detail').textContent=r ? `Recording ${r.name} · source ID ${r.sourceId} · ${sourceOffset(r.endAt,r)} source duration` : 'Select an imported BIDMC recording.';
  $('provenance').textContent=s ? `${s.provenance} · Data quality follows each received chunk and observation. Display times are offsets from recording start.` : 'Select a recording.';
  $('clock').textContent=r ? '00:00.00' : '—';
  $('clock-note').textContent=r ? 'Relative source time; not acquisition wall time' : 'Select a recording';
  updateControls();
}
async function renderAdmission() {
  const id=selectedAdmission(), admission=admissions.find(a=>a.id===id);
  $('patient-detail').textContent=admission ? `Admission ${id} · loading patient` : '';
  if (admission) {
    try { const p=await request(`/patients/${admission.patientId}`); $('patient-detail').textContent=`Demo patient ${p.code} · ${p.displayName} · admission ${id}`; }
    catch(e) { $('patient-detail').textContent=`Admission ${id} · ${e.message}`; }
  }
  renderDevices(); renderHistory(); closeSocket(); if(id) connectSocket(id);
  if(id) refreshVitals().catch(e=>log(e.message));
  updateControls();
}
function renderDevices() {
  $('devices').replaceChildren();
  if (!selectedAdmission()) { $('devices').textContent='Select an admission.'; return; }
  if (!devices.length) { $('devices').textContent='No visible devices.'; return; }
  for (const d of devices) {
    const row=document.createElement('div');row.className='device-row';
    const name=document.createElement('b');name.textContent=`${d.serial} · ${d.adapterType}`;
    const state=document.createElement('span');state.textContent=d.online?'ONLINE':'OFFLINE';
    row.append(name,state);$('devices').append(row);
  }
  const note=document.createElement('p');note.className='detail';note.textContent='Device assignment to this admission is validated by the backend when replay ingests data.';$('devices').append(note);
}
function renderHistory() {
  $('session').replaceChildren();option($('session'),'','Create or select session');$('history').replaceChildren();
  const visible=history.filter(h=>!selectedAdmission()||h.admissionId===selectedAdmission());
  for (const h of visible) {
    option($('session'),h.id,`#${h.id} · admission ${h.admissionId} · ${h.state || 'CREATED'}`);
    const row=document.createElement('div');row.className='history-row';
    const label=document.createElement('span');label.textContent=`#${h.id} · ${h.recordingName} · ${h.state || 'CREATED'} · ${h.speed}×`;
    const button=document.createElement('button');button.textContent='Open';button.onclick=()=>chooseSession(h.id);
    row.append(label,button);$('history').append(row);
  }
  if(!visible.length) $('history').textContent='No locally remembered sessions for this admission.';
  if(selectedSession) $('session').value=String(selectedSession.id);
}
function remember(session) {
  const r=recordingById(session.recordingId);
  history=[{id:session.id,admissionId:session.admissionId,recordingId:session.recordingId,
    recordingName:r?.name||`recording ${session.recordingId}`,state:session.state,speed:session.speed},
    ...history.filter(h=>h.id!==session.id)].slice(0,50);
  localStorage.setItem(storageKey,JSON.stringify(history));renderHistory();
}
async function chooseSession(id) {
  if(!id) {selectedSession=null;updateControls();return;}
  try {
    selectedSession=await request(`/simulation-sessions/${id}`);
    $('admission').value=String(selectedSession.admissionId);
    $('recording').value=String(selectedSession.recordingId);
    $('speed').value=String(selectedSession.speed);
    renderRecording(); await renderAdmission(); remember(selectedSession);renderSession();
  } catch(e) {notice(`Session ${id}: ${e.message}`);}
}
function renderSession() {
  const s=selectedSession;
  $('state').textContent=s?.state||'NO SESSION';$('state').className=`state ${s?.state||''}`;
  if(s) {
    $('clock').textContent=sourceOffset(s.cursorAt,recordingById(s.recordingId));
    $('clock-note').textContent=`${s.speed}× · ${s.state} · source-relative clock`;
  }
  updateControls();
}
function updateControls() {
  const state=selectedSession?.state;
  $('create').disabled=!selectedAdmission()||!selectedRecording();
  $('start').disabled=state!=='CREATED';$('pause').disabled=state!=='RUNNING';
  $('resume').disabled=state!=='PAUSED';$('stop').disabled=!['CREATED','RUNNING','PAUSED'].includes(state);
}
async function createSession() {
  try {
    const session=await post('/simulation-sessions',{admissionId:selectedAdmission(),recordingId:selectedRecording(),speed:Number($('speed').value)});
    selectedSession=session;remember(session);renderSession();notice(`Created session ${session.id}.`);
  } catch(e) {notice(`Create failed: ${e.message}`);}
}
async function action(name) {
  if(!selectedSession)return;
  try {selectedSession=await post(`/simulation-sessions/${selectedSession.id}/${name}`);remember(selectedSession);renderSession();notice(`Session ${selectedSession.id}: ${selectedSession.state}.`);}
  catch(e){notice(`${name}: ${e.message}`);}
}
async function refreshSession() {
  if(!selectedSession)return;
  try {
    const next=await request(`/simulation-sessions/${selectedSession.id}`);
    if(next.state!==selectedSession.state)log(`Session ${next.id}: ${next.state}`);
    selectedSession=next;remember(next);renderSession();
  } catch(e) {log(`Session refresh: ${e.message}`);}
}
function draw(metric,samples) {
  const canvas=$(metric.toLowerCase()), ctx=canvas.getContext('2d');
  ctx.clearRect(0,0,canvas.width,canvas.height);
  if(!samples?.length)return;
  const lo=Math.min(...samples),hi=Math.max(...samples),span=hi-lo||1;
  ctx.strokeStyle=colors[metric];ctx.lineWidth=2;ctx.beginPath();
  samples.forEach((v,i)=>{const x=i/(samples.length-1||1)*canvas.width,y=canvas.height-12-(v-lo)/span*(canvas.height-24);i?ctx.lineTo(x,y):ctx.moveTo(x,y)});ctx.stroke();
}
function acceptEvent(event) {
  if(event.type==='CONNECTED'){log('WebSocket connected · refreshing authoritative state');refreshSession();refreshVitals();return;}
  const payload=event.payload||{};
  const key=payload.eventId||event.eventId;
  if(key&&seen.has(key))return;
  if(key){seen.add(key);if(seen.size>3000)seen=new Set([...seen].slice(-1500));}
  if(event.type==='WAVEFORM'&&colors[payload.metric]) {
    draw(payload.metric,payload.samples);
    const id=payload.metric.toLowerCase();$(id+'-quality').textContent=payload.quality||event.quality||'UNKNOWN';
    $(id+'-time').textContent=`at ${sourceOffset(payload.observedAt)} · ${payload.samples?.length||0} samples`;
  } else if(event.type==='READING'&&metrics[payload.metric]) {
    updateVital(payload);log(`${payload.metric}: ${payload.value==null?'MISSING':payload.value+' '+payload.unit} · ${payload.quality}`);
  } else if(event.type==='SIMULATION') {refreshSession();}
  else if(event.type==='DEVICE_STATUS'){log('Device status changed');loadDevices();}
}
function connectSocket(admissionId) {
  const wsBase=apiOrigin.replace(/^http/,'ws');
  try {
    socket=new WebSocket(`${wsBase}/ws/monitoring?admissionId=${admissionId}`);
    socket.onmessage=e=>{try{acceptEvent(JSON.parse(e.data))}catch(err){log(`Event parse: ${err.message}`)}};
    socket.onclose=()=>{if(selectedAdmission()===admissionId){log('WebSocket disconnected · reconnecting');reconnectTimer=setTimeout(()=>connectSocket(admissionId),3000)}};
    socket.onerror=()=>{socket?.close()};
    heartbeatTimer=setInterval(()=>{if(socket?.readyState===WebSocket.OPEN)socket.send('ping')},20000);
  } catch(e){log(`WebSocket: ${e.message}`)}
}
function closeSocket(){clearTimeout(reconnectTimer);clearInterval(heartbeatTimer);if(socket){socket.onclose=null;socket.close();socket=null}}
function updateVital(m) {
  const card=$(`vital-${m.metric}`);if(!card)return;
  const missing=m.value==null;card.classList.toggle('missing',missing);
  card.querySelector('strong').textContent=missing?'—':Number(m.value).toFixed(m.metric==='TEMP'?1:0);
  card.querySelector('small').textContent=`${missing?'MISSING · ':''}${m.unit||metrics[m.metric][1]} · ${sourceOffset(m.observedAt)}`;
}
function initVitals(){for(const [code,[name,unit]] of Object.entries(metrics)){const card=document.createElement('div');card.id=`vital-${code}`;card.className='vital missing';card.innerHTML=`<span class="eyebrow">${name.toUpperCase()}</span><strong>—</strong><small>${unit} · no reading</small>`;$('vitals').append(card)}}
async function refreshVitals(){if(!selectedAdmission())return;const readings=await request(`/admissions/${selectedAdmission()}/measurements/latest`);for(const m of readings)updateVital(m)}
async function loadDevices(){try{devices=await request('/devices');renderDevices()}catch(e){log(`Devices: ${e.message}`)}}
async function loadCatalog(){
  const [src,rec,adm,dev]=await Promise.all([request('/sources'),request('/recordings'),request('/admissions?size=100'),request('/devices')]);
  sources=src;recordings=rec;admissions=adm.items||adm.content||[];devices=dev;renderCatalog();
  log(`Loaded ${recordings.length} source recordings and ${admissions.length} admissions.`);
}
async function health(){try{const h=await request('/health');$('health').textContent=`Backend ${h.status}`;$('health-dot').className='dot ok'}catch(e){$('health').textContent='Backend unavailable';$('health-dot').className='dot bad'}}
async function init(){
  initVitals();await health();
  try{const s=await request('/session');if(s.roles?.includes('ROLE_ADMIN')){$('login-panel').hidden=true;$('workspace').hidden=false;await loadCatalog()}else notice('Sign in with an administrator account to control simulator sessions.')}catch(e){notice(`Session: ${e.message}`)}
  setInterval(()=>{health();refreshSession();if(selectedAdmission())refreshVitals().catch(()=>{})},5000);
}
$('login-form').addEventListener('submit',async e=>{e.preventDefault();try{await post('/session',{username:$('username').value,password:$('password').value});$('password').value='';const session=await request('/session');if(!session.roles?.includes('ROLE_ADMIN'))throw new Error('Administrator role required');$('login-panel').hidden=true;$('workspace').hidden=false;notice('Signed in.');await loadCatalog()}catch(err){notice(`Sign in: ${err.message}`)}});
$('recording').onchange=renderRecording;$('admission').onchange=renderAdmission;
$('session').onchange=e=>chooseSession(Number(e.target.value));
$('create').onclick=createSession;for(const name of ['start','pause','resume','stop'])$(name).onclick=()=>action(name);
window.addEventListener('beforeunload',closeSocket);init();
