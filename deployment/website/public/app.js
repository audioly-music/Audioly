'use strict';
document.getElementById('year').textContent = new Date().getFullYear();
const canvas = document.getElementById('sound');
const ctx = canvas.getContext('2d');
const control = document.getElementById('motion');
const preference = matchMedia('(prefers-reduced-motion: reduce)');
let paused = preference.matches, visible = true, frame = 0, phase = 0, previous = 0, width = 0, height = 0;
let aimX = 0, aimY = 0, rotX = 0, rotY = 0;
function buttonState() { control.textContent = paused ? 'Play motion' : 'Pause motion'; control.setAttribute('aria-pressed', String(paused)); }
function size() { const rect = canvas.getBoundingClientRect(); width = rect.width; height = rect.height; const dpr = Math.min(devicePixelRatio || 1, 2); canvas.width = width * dpr; canvas.height = height * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0); draw(); }
function project(x,y,z) { const angle = -.48 + rotX, tilt = -.32 + rotY; const ax = x*Math.cos(angle)+z*Math.sin(angle), az = -x*Math.sin(angle)+z*Math.cos(angle); const ay = y*Math.cos(tilt)-az*Math.sin(tilt), bz = y*Math.sin(tilt)+az*Math.cos(tilt); const perspective = 750/(750+bz); const scale = Math.min(width/620,height/470); return {x:width*.52+ax*perspective*scale,y:height*.48+ay*perspective*scale,z:bz,scale:perspective*scale}; }
function draw() {
 ctx.clearRect(0,0,width,height);
 const bars = [];
 for(let row=0;row<12;row++) for(let col=0;col<24;col++) {
  const x=(col-11.5)*19,z=(row-5.5)*21;
  const envelope=Math.pow(Math.max(0,1-Math.abs(col-11.5)/14),.8);
  const wave=Math.sin(col*.42+row*.36+phase)*.5+.5;
  const length=18+envelope*(45+wave*108);
  const y=Math.sin(col*.23+row*.34+phase*.5)*25;
  const top=project(x,y-length/2,z), bottom=project(x,y+length/2,z);
  bars.push({top,bottom,col,row});
 }
 bars.sort((a,b)=>b.top.z-a.top.z);
 for(const bar of bars) {
  const {top,bottom,col,row}=bar;
  const hue=154+col*4.1+row*1.2;
  const alpha=.28+row/11*.64;
  const gradient=ctx.createLinearGradient(top.x,top.y,bottom.x,bottom.y);
  gradient.addColorStop(0,`hsla(${hue},95%,77%,${alpha})`);
  gradient.addColorStop(.42,`hsla(${hue},88%,57%,${alpha})`);
  gradient.addColorStop(1,`hsla(${hue+12},80%,35%,${alpha})`);
  ctx.lineCap='round';ctx.lineWidth=Math.max(2,8*top.scale);ctx.strokeStyle=gradient;
  ctx.beginPath();ctx.moveTo(top.x,top.y);ctx.lineTo(bottom.x,bottom.y);ctx.stroke();
  ctx.lineWidth=Math.max(.6,1.2*top.scale);ctx.strokeStyle=`hsla(${hue},100%,90%,${alpha*.55})`;
  ctx.beginPath();ctx.moveTo(top.x-1.5*top.scale,top.y+3);ctx.lineTo(bottom.x-1.5*top.scale,bottom.y-3);ctx.stroke();
 }
}
function loop(now) { frame=0; if(paused||!visible||document.hidden) {previous=0;return;} phase+=Math.min((now-(previous||now))/1000,.05)*.85;previous=now;rotX+=(aimX-rotX)*.04;rotY+=(aimY-rotY)*.04;draw();frame=requestAnimationFrame(loop); }
function resume(){ if(!frame&&!paused&&visible&&!document.hidden) frame=requestAnimationFrame(loop); }
control.addEventListener('click',()=>{paused=!paused;buttonState();resume();});
preference.addEventListener('change',e=>{paused=e.matches;buttonState();resume();});
canvas.addEventListener('pointermove',e=>{if(paused)return;const r=canvas.getBoundingClientRect();aimX=((e.clientX-r.left)/r.width-.5)*.4;aimY=((e.clientY-r.top)/r.height-.5)*.2;});
canvas.addEventListener('pointerleave',()=>{aimX=aimY=0;});
document.addEventListener('visibilitychange',resume);
new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;resume();}).observe(canvas);
new ResizeObserver(size).observe(canvas);buttonState();resume();

const allMotion = document.getElementById('all-motion');
function syncMotion(){document.documentElement.classList.toggle('motion-paused',paused);allMotion.textContent=paused?'Enable animations':'Pause animations';allMotion.setAttribute('aria-pressed',String(paused));updateScroll();}
allMotion.addEventListener('click',()=>{paused=!paused;buttonState();syncMotion();resume();});
control.addEventListener('click',syncMotion);
preference.addEventListener('change',syncMotion);
const reveals=[...document.querySelectorAll('.reveal')];
if('IntersectionObserver' in window){const revealObserver=new IntersectionObserver(entries=>{for(const entry of entries){if(entry.isIntersecting){entry.target.classList.add('is-visible');revealObserver.unobserve(entry.target);}}},{threshold:.08,rootMargin:'0px 0px -20px 0px'});for(const el of reveals)revealObserver.observe(el);document.documentElement.classList.add('motion-enabled');}
const device=document.querySelector('.brand-device');
const progress=document.querySelector('.scroll-progress');
let scrollQueued=false;
function updateScroll(){scrollQueued=false;const total=document.documentElement.scrollHeight-innerHeight;progress.style.transform='scaleX('+(total>0?Math.min(1,scrollY/total):0)+')';if(!paused){const shift=Math.min(scrollY,900);device.style.transform='translateY('+(-shift*.045)+'px) rotateY('+(-9+shift*.015)+'deg) rotateX(4deg)';}}
addEventListener('scroll',()=>{if(!scrollQueued){scrollQueued=true;requestAnimationFrame(updateScroll);}},{passive:true});
addEventListener('resize',updateScroll);syncMotion();
for(const id of ['quality-wave','mix-wave']){const container=document.getElementById(id);for(let i=0;i<64;i++){const bar=document.createElement('i');bar.style.setProperty('--h',(20+Math.abs(Math.sin(i*.37)*Math.cos(i*.16))*77)+'%');container.append(bar);}}
const qualityButton=document.getElementById('quality-toggle');qualityButton.addEventListener('click',()=>{const upgraded=qualityButton.getAttribute('aria-pressed')!=='true';qualityButton.setAttribute('aria-pressed',String(upgraded));qualityButton.textContent=upgraded?'Reset preview':'Preview upgrade';document.querySelector('.pipeline-demo').classList.toggle('upgraded',upgraded);document.getElementById('quality-status').textContent=upgraded?'Higher quality · when your source supports it':'Streaming · Opus';});
const lyricsButton=document.getElementById('lyrics-toggle');lyricsButton.addEventListener('click',()=>{const show=lyricsButton.getAttribute('aria-pressed')!=='true';lyricsButton.setAttribute('aria-pressed',String(show));lyricsButton.textContent=show?'Hide translation':'Show translation';document.getElementById('translation').hidden=!show;});
const crossfade=document.getElementById('crossfade');crossfade.addEventListener('input',()=>{const value=Number(crossfade.value);document.getElementById('mix-value').textContent=value+' seconds';document.querySelector('.mix-region').style.width=(value/12*100)+'%';document.querySelector('.mix-region').style.left=(50-value/12*50)+'%';});
