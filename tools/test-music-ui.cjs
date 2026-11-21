// Fault injection against the actual launcher script, with no playback commands to real apps.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
class Element{
  constructor(){this.disabled=false;this.hidden=false;this.value=0;this.dataset={};this.children=[];this.textContent='';}
  removeAttribute(k){delete this[k];}setAttribute(k,v){this[k]=v;}focus(){document.activeElement=this;}replaceChildren(...c){this.children=c;}append(...c){this.children.push(...c);}reset(){}getClientRects(){return [{}];}querySelectorAll(){return [];}
}
const elements=new Map(),buttons=['prev','toggle','next'].map(c=>{const e=new Element();e.dataset.media=c;return e;});
const document={activeElement:null,getElementById(id){if(!elements.has(id))elements.set(id,new Element());return elements.get(id);},querySelectorAll(){return buttons;},createElement(){return new Element();}};
const timers=new Map();let failSnapshot=false,failCover=true,coverRequests=0,commandReply;
const snapshot={title:'Regression track',artist:'Regression artist',app:'Fixture',playing:true,position:1000,duration:20000,cover_id:1,selected:'auto',sources:[],caps:{toggle:true,next:true,prev:true,seek:true,volume:true},volume:.6};
const invoke=async(name)=>{if(name==='media_snapshot'){if(failSnapshot)throw Error('fixture disconnect');return structuredClone(snapshot);}if(name==='media_cover'){coverRequests++;if(failCover)throw Error('fixture cover error');return [137,80,78,71];}if(name==='media_command')return new Promise(r=>commandReply=r);};
const URLClass=class extends URL{static createObjectURL(){return 'blob:fixture';}static revokeObjectURL(){}};
vm.runInNewContext(fs.readFileSync(require('node:path').join(__dirname,'../launcher/src/music.js'),'utf8'),{document,window:{__TAURI__:{core:{invoke}}},performance:{now:()=>1000},Date,URL:URLClass,Blob,Uint8Array,localStorage:{getItem:()=>null,setItem(){}},setInterval(fn,ms){timers.set(ms,fn);},setTimeout});
const el=id=>document.getElementById(id),flush=()=>new Promise(r=>setImmediate(r));
(async()=>{
  await flush();assert.equal(el('miniTitle').textContent,snapshot.title);assert.equal(buttons[1].disabled,false);assert.equal(el('fullCover').hidden,true);
  failCover=false;await timers.get(900)();assert.ok(coverRequests>=2);assert.equal(el('fullCover').hidden,false);
  buttons[1].onclick();await flush();assert.equal(el('musicSeek').disabled,true);assert.equal(el('musicVolume').disabled,true);assert.equal(el('musicSource').disabled,true);commandReply();await flush();await flush();
  failSnapshot=true;await timers.get(900)();assert.ok(buttons.every(b=>b.disabled));assert.equal(el('musicSeek').disabled,true);assert.equal(el('musicVolume').disabled,true);assert.equal(el('fullCover').hidden,true);assert.notEqual(el('miniTitle').textContent,snapshot.title);
  failSnapshot=false;await timers.get(900)();assert.equal(el('miniTitle').textContent,snapshot.title);assert.equal(el('fullCover').hidden,false);assert.equal(el('musicSeek').disabled,false);
  console.log('PASS music UI: cover failure retains metadata; cover retries; pending commands lock controls; disconnect clears stale metadata and controls; reconnect restores playback view');
})().catch(e=>{console.error(e);process.exitCode=1;});
