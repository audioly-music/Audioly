'use strict';
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
let paused = false;
const motionButton = document.getElementById('motion-toggle');
const lyricButton = document.getElementById('lyrics-toggle');
function motionState() {
  document.documentElement.classList.toggle('motion-paused', paused);
  document.documentElement.classList.toggle('motion-playing', !paused);
  if (motionButton) { motionButton.textContent = paused ? 'Enable motion' : 'Pause motion'; motionButton.setAttribute('aria-pressed',String(paused)); }
  if (lyricButton) { lyricButton.textContent = paused ? 'Play preview' : 'Pause preview'; lyricButton.setAttribute('aria-label',paused ? 'Play lyrics preview' : 'Pause lyrics preview'); lyricButton.setAttribute('aria-pressed',String(paused)); }
}
motionButton?.addEventListener('click',()=>{paused=!paused;motionState();});
lyricButton?.addEventListener('click',()=>{paused=!paused;motionState();});
// Autoplay the silent lyric loop; the footer motion control remains available.
motionState();
const lyricTrack = document.getElementById('lyrics-track');
if (lyricTrack) {
  // Line timings and text supplied by the site owner. No word timings were supplied.
  const lines = [{"time": 3820, "text": "Ayy, ayy, ayy, ayy (ooh)"}, {"time": 8750, "text": "Ooh, ooh, ooh, oooh (ooh)"}, {"time": 14360, "text": "Ayy, ayy"}, {"time": 19380, "text": "Ooh, ooh, ooh, oooh"}, {"time": 23890, "text": ""}, {"time": 26990, "text": "Needless to say, I keep her in check"}, {"time": 29480, "text": "She was all bad-bad, nevertheless (yeah)"}, {"time": 32420, "text": "Callin' it quits now, baby, I'm a wreck (wreck)"}, {"time": 35220, "text": "Crash at my place, baby, you're a wreck (wreck)"}, {"time": 37480, "text": "Needless to say, I'm keeping her in check"}, {"time": 40080, "text": "She was all bad-bad, nevertheless"}, {"time": 43000, "text": "Callin' it quits now, baby, I'm a wreck"}, {"time": 45610, "text": "Crash at my place, baby, you're a wreck"}, {"time": 48270, "text": "Thinkin' in a bad way, losin' your grip"}, {"time": 50830, "text": "Screamin' at my face, baby, don't trip"}, {"time": 53570, "text": "Someone took a big L, don't know how that felt"}, {"time": 56220, "text": "Lookin' at you sideways, party on tilt"}, {"time": 59060, "text": "Ooh-ooh, some things you just can't refuse"}, {"time": 64150, "text": "She wanna ride me like a cruise"}, {"time": 66580, "text": "And I'm not tryna lose"}, {"time": 70470, "text": "Then you're left in the dust"}, {"time": 73270, "text": "Unless I stuck by ya"}, {"time": 75870, "text": "You're the sunflower"}, {"time": 78870, "text": "I think your love would be too much"}, {"time": 81120, "text": "Or you'll be left in the dust"}, {"time": 84010, "text": "Unless I stuck by ya"}, {"time": 86460, "text": "You're the sunflower"}, {"time": 89150, "text": "You're the sunflower"}, {"time": 91290, "text": "Every time I'm leavin' on ya"}, {"time": 93760, "text": "You don't make it easy, no, no"}, {"time": 96690, "text": "Wish I could be there for ya"}, {"time": 99330, "text": "Give me a reason to go"}, {"time": 102490, "text": "Every time I'm walkin' out"}, {"time": 104240, "text": "I can hear you tellin' me to turn around"}, {"time": 107190, "text": "Fightin' for my trust and you won't back down"}, {"time": 109750, "text": "Even if we gotta risk it all right now, oh"}, {"time": 112830, "text": "I know you're scared of the unknown (known)"}, {"time": 115270, "text": "You don't wanna be alone (alone)"}, {"time": 117970, "text": "I know I always come and go (and go)"}, {"time": 120450, "text": "But it's out of my control"}, {"time": 123560, "text": "And you'll be left in the dust"}, {"time": 126770, "text": "Unless I stuck by ya"}, {"time": 129460, "text": "You're the sunflower"}, {"time": 132160, "text": "I think your love would be too much"}, {"time": 134420, "text": "Or you'll be left in the dust"}, {"time": 137400, "text": "Unless I stuck by ya"}, {"time": 139430, "text": "You're the sunflower"}, {"time": 142450, "text": "You're the sunflower"}, {"time": 144750, "text": "Yeah"}, {"time": 146730, "text": ""}];
  const duration = lines.at(-1).time + 2000;
  const rows = lines.map(line => {
    const row=document.createElement('div');row.className='demo-line';
    row.textContent=line.text || '♪'; row.setAttribute('aria-hidden','true');
    lyricTrack.append(row);return row;
  });
  lyricTrack.setAttribute('aria-label','Sunflower lyrics, timed from supplied LRC');
  let elapsed=0,last=performance.now(),inView=true,current=-2,from=0,target=0,scroll=0,changedAt=0;
  let offsets=[];
  const measure=()=>{let y=0;offsets=rows.map(row=>{const top=y;y+=Math.max(44,row.getBoundingClientRect().height)+30;return top;});target=offsets[Math.max(0,current)]||0;};
  measure();document.fonts?.ready.then(measure);addEventListener('resize',measure);
  if('ResizeObserver' in window)new ResizeObserver(measure).observe(lyricTrack);
  if('IntersectionObserver' in window)new IntersectionObserver(entries=>{inView=entries[0].isIntersecting;}).observe(lyricTrack);
  const timeLabels=document.querySelectorAll('.track-times span');
  const progressBar=document.querySelector('.track-progress');
  const clock=ms=>`${Math.floor(ms/60000)}:${String(Math.floor(ms/1000)%60).padStart(2,'0')}`;
  function render(){
    let active=-1;for(let i=0;i<lines.length&&lines[i].time<=elapsed;i++)active=i;
    if(active!==current){current=active;from=scroll;target=offsets[Math.max(0,active)]||0;changedAt=elapsed;}
    const t=Math.min(1,Math.max(0,(elapsed-changedAt)/700));
    scroll=from+(target-from)*(1-Math.pow(1-t,4));
    rows.forEach((row,index)=>{
      const y=80+offsets[index]-scroll;
      row.style.transform=`translate3d(0,${y}px,0)`;
      row.style.opacity=index===active?'1':'.28';
      row.style.filter=index===active?'blur(0)':'blur(1px)';
      row.style.visibility=y < -200 || y > 420?'hidden':'visible';
      row.dataset.current=String(index===active);
    });
    if(timeLabels.length===3){timeLabels[0].textContent=clock(elapsed);timeLabels[1].textContent='Sunflower · silent animation';timeLabels[2].textContent='−'+clock(duration-elapsed);}
    progressBar?.style.setProperty('--song-progress',`${elapsed/duration*100}%`);
  }
  render();
  function advance(now){
    const delta=now-last;last=now;
    if(!paused&&!document.hidden&&inView){elapsed+=delta;if(elapsed>=duration){elapsed%=duration;current=-2;scroll=from=target=0;}render();}
    requestAnimationFrame(advance);
  }
  document.addEventListener('visibilitychange',()=>{last=performance.now();});
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
