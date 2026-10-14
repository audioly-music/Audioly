'use strict';
document.getElementById('year').textContent = new Date().getFullYear();
const preference = matchMedia('(prefers-reduced-motion: reduce)');
let paused = preference.matches;
const motionButton = document.getElementById('motion-toggle');
function updateMotion() {
  document.documentElement.classList.toggle('motion-paused', paused);
  motionButton.textContent = paused ? 'Enable motion' : 'Pause motion';
  motionButton.setAttribute('aria-pressed', String(paused));
}
motionButton.addEventListener('click', () => { paused = !paused; updateMotion(); });
preference.addEventListener('change', event => { paused = event.matches; updateMotion(); });
updateMotion();
if ('IntersectionObserver' in window && !preference.matches) {
  const observer = new IntersectionObserver(entries => entries.forEach(entry => {
    if (entry.isIntersecting) { entry.target.dataset.visible = 'true'; observer.unobserve(entry.target); }
  }), {threshold: 0.06});
  document.querySelectorAll('.reveal').forEach(element => { element.dataset.visible = 'false'; observer.observe(element); });
}
let frame = 0;
const gallery = document.querySelector('.screen-gallery');
const hero = document.querySelector('.hero');
function paintScroll() {
  frame = 0;
  if (paused || document.hidden) return;
  const offset = Math.min(35, Math.max(0, -hero.getBoundingClientRect().top) * .055);
  hero.style.setProperty('--hero-shift', `${offset}px`);
  hero.style.setProperty('--hero-tilt', `${offset * .14}deg`);
  const rect = gallery.getBoundingClientRect();
  const galleryProgress = Math.min(1, Math.max(0, (innerHeight - rect.top) / (innerHeight + rect.height)));
  gallery.style.setProperty('--gallery-turn', `${(1 - galleryProgress) * 12}deg`);
  gallery.style.setProperty('--gallery-rise', `${(1 - galleryProgress) * 45}px`);
}
addEventListener('scroll', () => { if (!frame && !paused) frame = requestAnimationFrame(paintScroll); }, {passive: true});
const tabs = [...document.querySelectorAll('[role="tab"][data-platform]')];
function selectPlatform(platform, focus = false) {
  const selected = tabs.find(tab => tab.dataset.platform === platform);
  if (!selected) return;
  for (const tab of tabs) {
    const active = tab === selected;
    tab.setAttribute('aria-selected', String(active));
    tab.tabIndex = active ? 0 : -1;
    document.getElementById(tab.getAttribute('aria-controls')).hidden = !active;
  }
  if (focus) selected.focus();
}
tabs.forEach((tab, index) => {
  tab.addEventListener('click', () => selectPlatform(tab.dataset.platform));
  tab.addEventListener('keydown', event => {
    let next;
    if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
    if (event.key === 'ArrowLeft') next = (index + tabs.length - 1) % tabs.length;
    if (event.key === 'Home') next = 0;
    if (event.key === 'End') next = tabs.length - 1;
    if (next !== undefined) { event.preventDefault(); selectPlatform(tabs[next].dataset.platform, true); }
  });
});
document.querySelectorAll('[data-platform-link]').forEach(link => link.addEventListener('click', () => selectPlatform(link.dataset.platformLink)));
// Static production links remain usable when the API is unavailable or limited.
async function updateRelease() {
  try {
    const response = await fetch('https://api.github.com/repos/audioly-music/Audioly/releases/latest', {signal: AbortSignal.timeout(6000)});
    if (!response.ok) return;
    const release = await response.json();
    const version = String(release.tag_name || '').replace(/^v/, '');
    if (!/^[1-9][0-9]*\.[0-9]\.[0-9]$/.test(version) || !Array.isArray(release.assets)) return;
    const uploaded = release.assets.filter(asset => asset.state === 'uploaded');
    const apk = uploaded.find(asset => asset.name === 'Audioly.apk');
    if (!apk) return;
    document.querySelectorAll('[data-release-version]').forEach(element => { element.textContent = version; });
    document.querySelector('.release-pill').href = release.html_url;
    document.querySelectorAll('[data-asset],[data-asset-suffix]').forEach(link => {
      const name = link.dataset.asset || `Audioly-${version}-${link.dataset.assetSuffix}`;
      const asset = uploaded.find(item => item.name === name);
      if (asset && /^https:\/\/github\.com\/audioly-music\/Audioly\/releases\/download\//.test(asset.browser_download_url)) link.href = asset.browser_download_url;
    });
    document.querySelectorAll('[data-size-for]').forEach(element => {
      const asset = uploaded.find(item => item.name === element.dataset.sizeFor);
      if (asset) element.textContent = `${Math.round(asset.size / 1e6)} MB`;
    });
  } catch (_) { /* Offline and rate-limited visitors keep the working static links. */ }
}
updateRelease();

// An original text demo: no song lyrics or audio is distributed by this page.
const demoLines = ['Find your frequency.', 'Let the world fade away.', 'Every beat feels like home.', 'Keep the good moments close.', 'A soundtrack of your own.', 'Press play and feel more.'];
const lyricTrack = document.getElementById('lyrics-track');
const lyricButton = document.getElementById('lyrics-toggle');
let lyricsPaused = false, lyricElapsed = 1000, lastLyricTick = performance.now();
const renderedLines = demoLines.map((text, index) => {
  const line = document.createElement('div'); line.className = 'demo-line';
  text.split(' ').forEach((word, i) => {
    if (i) line.append(' ');
    const span = document.createElement('span'); span.className = 'demo-word'; span.textContent = word; line.append(span);
  });
  lyricTrack.append(line); return line;
});
function renderLyrics() {
  const slot = lyricElapsed / 3500;
  const current = Math.floor(slot) % renderedLines.length;
  const progress = slot % 1;
  renderedLines.forEach((line, index) => {
    let distance = (index - current + renderedLines.length) % renderedLines.length;
    if (distance > renderedLines.length / 2) distance -= renderedLines.length;
    line.style.setProperty('--line-position', String(distance));
    line.dataset.current = String(index === current);
    const words = [...line.querySelectorAll('.demo-word')];
    words.forEach((word, wordIndex) => {
      const fill = distance < 0 ? 100 : distance > 0 ? 0 : Math.min(100, Math.max(0, (progress * words.length - wordIndex) * 100));
      word.style.setProperty('--fill', `${fill}%`);
    });
  });
}
function lyricLabel() {
  const stopped = paused || lyricsPaused;
  document.documentElement.classList.toggle('lyrics-playing', !stopped);
  lyricButton.setAttribute('aria-pressed', String(stopped));
  lyricButton.textContent = stopped ? 'Play lyrics preview ▶' : 'Pause preview Ⅱ';
}
lyricButton.addEventListener('click', () => {
  if (paused) { paused = false; lyricsPaused = false; updateMotion(); }
  else lyricsPaused = !lyricsPaused;
  lyricLabel();
});
motionButton.addEventListener('click', lyricLabel);
preference.addEventListener('change', lyricLabel);
lyricLabel();
renderLyrics();
function advanceLyrics(now) {
  requestAnimationFrame(advanceLyrics); const delta = Math.min(150, now - lastLyricTick); lastLyricTick = now;
  const bounds = lyricTrack.getBoundingClientRect();
  if (paused || lyricsPaused || document.hidden || bounds.bottom < 0 || bounds.top > innerHeight) return;
  lyricElapsed += delta; renderLyrics();
}
requestAnimationFrame(advanceLyrics);
