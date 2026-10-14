'use strict';
const search = document.getElementById('doc-search');
const sections = [...document.querySelectorAll('[data-doc-section]')];
const links = [...document.querySelectorAll('[data-doc-link]')];
search.addEventListener('input', () => {
  const query = search.value.trim().toLowerCase(); let matches = 0;
  sections.forEach(section => {
    const found = !query || section.textContent.toLowerCase().includes(query);
    section.hidden = !found; if(found) matches++;
    const link = links.find(link => link.hash === `#${section.id}`); if(link) link.hidden = !found;
  });
  document.getElementById('search-result').textContent = query ? `${matches} matching sections` : '';
});
document.addEventListener('keydown', event => {
  if(event.key === '/' && !['INPUT','TEXTAREA'].includes(document.activeElement.tagName)) { event.preventDefault(); search.focus(); }
  if(event.key === 'Escape' && document.activeElement === search) { search.value = ''; search.dispatchEvent(new Event('input')); search.blur(); }
});
document.querySelectorAll('.copy-code').forEach(button => button.addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(button.closest('.code-block').querySelector('code').textContent); button.textContent = 'Copied'; }
  catch (_) { button.textContent = 'Select code to copy'; }
  setTimeout(() => { button.textContent = 'Copy'; }, 2000);
}));
if('IntersectionObserver' in window) { const observer = new IntersectionObserver(entries => {
  const current = entries.find(entry => entry.isIntersecting);
  if(current) links.forEach(link => link.classList.toggle('active', link.hash === `#${current.target.id}`));
}, {rootMargin:'-15% 0px -65% 0px'}); sections.forEach(section => observer.observe(section)); }
