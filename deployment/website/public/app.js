'use strict';
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
let paused = reducedMotion.matches;
const motionButton = document.getElementById('motion-toggle');
const lyricButton = document.getElementById('lyrics-toggle');
function motionState() {
  document.documentElement.classList.toggle('motion-paused', paused);
  document.documentElement.classList.toggle('motion-playing', !paused);
  if (motionButton) { motionButton.textContent = paused ? 'Enable motion' : 'Pause motion'; motionButton.setAttribute('aria-pressed',String(paused)); }
  if (lyricButton) { lyricButton.textContent = paused ? '▶' : 'Ⅱ'; lyricButton.setAttribute('aria-label',paused ? 'Play lyrics preview' : 'Pause lyrics preview'); lyricButton.setAttribute('aria-pressed',String(paused)); }
}
motionButton?.addEventListener('click',()=>{paused=!paused;motionState();});
lyricButton?.addEventListener('click',()=>{paused=!paused;motionState();});
reducedMotion.addEventListener('change',event=>{paused=event.matches;motionState();});
motionState();
const lyricTrack = document.getElementById('lyrics-track');
if (lyricTrack) {
  const lines = ['Find your frequency.', 'Let the world fade away.', 'Every beat feels like home.', 'Keep the good moments close.', 'A soundtrack of your own.', 'Press play and feel more.'];
  const rows = lines.map(text => {
    const row=document.createElement('div'); row.className='demo-line';
    text.split(' ').forEach((word,index)=>{ if(index)row.append(' ');const span=document.createElement('span');span.className='demo-word';span.textContent=word;row.append(span); });
    lyricTrack.append(row);return {row,words:[...row.children]};
  });
  let elapsed=700,last=performance.now(),inView=true;
  if('IntersectionObserver' in window)new IntersectionObserver(entries=>{inView=entries[0].isIntersecting;}).observe(lyricTrack);
  function render(){
    const slot=elapsed/3600,current=Math.floor(slot)%rows.length,progress=slot%1;
    rows.forEach(({row,words},index)=>{
      let offset=(index-current+rows.length)%rows.length;if(offset===rows.length-1)offset=-1;
      row.style.setProperty('--line-position',String(offset));row.dataset.current=String(offset===0);
      words.forEach((word,i)=>word.style.setProperty('--fill',`${offset<0?100:offset>0?0:Math.min(100,Math.max(0,(progress*words.length-i)*100))}%`));
    });
  }
  render();
  function advance(now){const delta=Math.min(100,now-last);last=now;if(!paused&&!document.hidden&&inView){elapsed+=delta;render();}requestAnimationFrame(advance);}
  requestAnimationFrame(advance);
}
const hero=document.querySelector('.hero');let frame=0;
function scrollEffect(){frame=0;if(paused||!hero)return;const offset=Math.min(1,Math.max(0,-hero.getBoundingClientRect().top/700));hero.style.setProperty('--hero-turn',`${offset*5}deg`);hero.style.setProperty('--hero-lift',`${offset*-22}px`);}
addEventListener('scroll',()=>{if(!frame)frame=requestAnimationFrame(scrollEffect);},{passive:true});
// Accessible horizontal shelves. Native touch scrolling works without JavaScript.
document.querySelectorAll('[data-scroll]').forEach(button=>{
 const rail=document.getElementById(button.dataset.scroll);
 const sync=()=>{button.disabled=Number(button.dataset.direction)<0?rail.scrollLeft<2:rail.scrollLeft+rail.clientWidth>=rail.scrollWidth-2;};
 button.addEventListener('click',()=>rail.scrollBy({left:Number(button.dataset.direction)*Math.min(rail.clientWidth*.8,640),behavior:paused?'instant':'smooth'}));
 rail.addEventListener('scroll',sync,{passive:true});addEventListener('resize',sync);sync();
});
function bindTabs(selector,attribute,onSelect){
 const tabs=[...document.querySelectorAll(selector)];
 const select=(tab,focus=false)=>{tabs.forEach(t=>{t.setAttribute('aria-selected',String(t===tab));t.tabIndex=t===tab?0:-1;});onSelect(tab.dataset[attribute]);if(focus)tab.focus();};
 tabs.forEach((tab,index)=>{tab.addEventListener('click',()=>select(tab));tab.addEventListener('keydown',event=>{let i;if(event.key==='ArrowRight')i=(index+1)%tabs.length;if(event.key==='ArrowLeft')i=(index+tabs.length-1)%tabs.length;if(event.key==='Home')i=0;if(event.key==='End')i=tabs.length-1;if(i!==undefined){event.preventDefault();select(tabs[i],true);}});});
 return value=>{const tab=tabs.find(t=>t.dataset[attribute]===value);if(tab)select(tab);};
}
bindTabs('[data-desktop-tab]','desktopTab',value=>document.querySelectorAll('[data-desktop-panel]').forEach(panel=>{panel.hidden=panel.dataset.desktopPanel!==value;}));
const selectPlatform=bindTabs('[data-platform]','platform',value=>document.querySelectorAll('.download-panel').forEach(panel=>{panel.hidden=panel.id!==`panel-${value}`;}));
selectPlatform(location.hash.slice(1));addEventListener('hashchange',()=>selectPlatform(location.hash.slice(1)));
async function releaseInfo(){
 try{
  const response=await fetch('https://api.github.com/repos/audioly-music/Audioly/releases/latest',{signal:AbortSignal.timeout(6000)});if(!response.ok)return;
  const release=await response.json(),version=String(release.tag_name||'').replace(/^v/,'');
  if(!/^[1-9][0-9]*\.[0-9]\.[0-9]$/.test(version)||!Array.isArray(release.assets))return;
  const assets=release.assets.filter(a=>a.state==='uploaded');if(!assets.some(a=>a.name==='Audioly.apk'))return;
  document.querySelectorAll('[data-release-version]').forEach(e=>{e.textContent=version;});
  document.querySelectorAll('[data-asset],[data-asset-suffix]').forEach(link=>{const name=link.dataset.asset||`Audioly-${version}-${link.dataset.assetSuffix}`;const asset=assets.find(a=>a.name===name);if(asset&&asset.browser_download_url.startsWith('https://github.com/audioly-music/Audioly/releases/download/'))link.href=asset.browser_download_url;});
  document.querySelectorAll('[data-size-for]').forEach(e=>{const asset=assets.find(a=>a.name===e.dataset.sizeFor);if(asset)e.textContent=`${Math.round(asset.size/1e6)} MB`;});
 }catch(_){/* Static release links remain available. */}
}
async function statistics(){
 const results=await Promise.allSettled([
  fetch('https://api.github.com/repos/audioly-music/Audioly',{signal:AbortSignal.timeout(6000)}).then(r=>r.ok?r.json():null),
  fetch('https://api.github.com/repos/audioly-music/Audioly/releases?per_page=100',{signal:AbortSignal.timeout(6000)}).then(r=>r.ok?r.json():null)
 ]);
 const repo=results[0].status==='fulfilled'?results[0].value:null;if(Number.isFinite(repo?.stargazers_count))document.querySelectorAll('[data-stars]').forEach(e=>{e.textContent=`(${repo.stargazers_count.toLocaleString()})`;});
 const releases=results[1].status==='fulfilled'?results[1].value:null;if(Array.isArray(releases)){const total=releases.reduce((sum,r)=>sum+(r.assets||[]).reduce((n,a)=>n+(Number(a.download_count)||0),0),0);document.querySelectorAll('[data-download-count]').forEach(e=>{e.textContent=`${total.toLocaleString()} release downloads`;});}
}
releaseInfo();statistics();
