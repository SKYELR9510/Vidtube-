// Vidtube para PC: interfaz (Inicio, Shorts, Suscripciones, Biblioteca, Buscar, Canal, Video, Configuración)
import * as YT from './yt.js';
import { icon } from './icons.js';
import { createPlayer } from './player.js';
import {
  store, onChange, addHistory, removeHistory, clearHistory, toggleLater, toggleLike, isLater, isLiked,
  isSub, toggleSub, addSearch, setSettings, interests, position,
} from './store.js';

const VERSION = new URLSearchParams(location.search).get('v') || 'dev';
const REPO = 'SKYELR9510/Vidtube-';
const app = document.getElementById('app');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const el = (html) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };
const dot = (...p) => p.filter(Boolean).map(esc).join(' · ');
function toast(msg) {
  const t = el(`<div class="toast">${esc(msg)}</div>`);
  document.body.appendChild(t);
  setTimeout(() => t.classList.add('on'));
  setTimeout(() => { t.classList.remove('on'); setTimeout(() => t.remove(), 300); }, 2200);
}
const applyTheme = () => document.documentElement.dataset.theme = store.settings.light ? 'light' : 'dark';
applyTheme();
onChange(applyTheme);

// ---------- Estructura ----------
app.innerHTML = `
  <aside class="side" aria-label="Navegación principal">
    <div class="side-logo"><img class="logo" alt="Vidtube"><img class="mark" src="assets/mark.png" alt="Vidtube"></div>
    <nav>
      <button data-go="home" title="Inicio">${icon('home')}<span>Inicio</span></button>
      <button data-go="shorts" title="Shorts">${icon('bolt')}<span>Shorts</span></button>
      <button data-go="explore" title="Explorar">${icon('compass')}<span>Explorar</span></button>
      <button data-go="subs" title="Suscripciones">${icon('subs')}<span>Suscripciones</span></button>
      <button data-go="library" title="Biblioteca">${icon('bookmark')}<span>Biblioteca</span></button>
      <div class="side-sep"></div>
      <button data-go="settings" title="Configuración">${icon('settings')}<span>Configuración</span></button>
    </nav>
    <div class="side-subs"></div>
    <div class="side-promo"><img src="assets/mark.png" alt=""><b>Tu mundo de videos,</b><span>en un solo lugar</span></div>
  </aside>
  <div class="mainwrap">
    <header class="top">
      <button class="iconbtn menu-btn" title="Contraer o expandir el menú" aria-label="Contraer o expandir el menú">${icon('menu')}</button>
      <button class="iconbtn back" title="Atrás" aria-label="Atrás">${icon('back')}</button>
      <form class="searchbar" role="search">
        <span class="s-ic">${icon('search', 20)}</span>
        <input type="search" placeholder="Buscar videos, canales, música…" autocomplete="off" aria-label="Buscar">
        <button type="submit" class="s-go" title="Buscar">${icon('search', 20)}<span>Buscar</span></button>
        <div class="suggest"></div>
      </form>
      <div class="top-right">
        <span class="update-pill"></span>
        <div class="prof">
          <button class="avatar-btn" title="Tu perfil" aria-label="Tu perfil" aria-haspopup="menu">${icon('person', 22)}</button>
          <div class="prof-menu" role="menu"></div>
        </div>
      </div>
    </header>
    <main id="view"></main>
  </div>`;
const view = document.getElementById('view');
const logoImgs = () => document.querySelectorAll('img.logo').forEach((i) => i.src = store.settings.light ? 'assets/wordmark_light.png' : 'assets/wordmark.png');
logoImgs(); onChange(logoImgs);

// Barra lateral contraíble (se recuerda en esta PC)
const SIDE_KEY = 'vt_side_collapsed';
const setCollapsed = (on) => { app.classList.toggle('collapsed', on); try { localStorage.setItem(SIDE_KEY, on ? '1' : ''); } catch {} };
setCollapsed((() => { try { return !!localStorage.getItem(SIDE_KEY); } catch { return false; } })());
app.querySelector('.menu-btn').onclick = () => setCollapsed(!app.classList.contains('collapsed'));

// Menú del perfil (datos reales guardados en esta PC)
const prof = app.querySelector('.prof');
const profMenu = prof.querySelector('.prof-menu');
function paintProf() {
  const s = store.settings;
  profMenu.innerHTML = `
    <div class="pm-head"><span class="avatar-btn sm">${icon('person', 20)}</span><div><b>Vidtube en esta PC</b><span>${store.subs.length} suscripciones · ${store.later.length} en Mi lista</span></div></div>
    <button role="menuitem" data-go="library">${icon('bookmark', 20)}<span>Biblioteca</span></button>
    <button role="menuitem" data-list="history">${icon('history', 20)}<span>Historial</span></button>
    <button role="menuitem" data-list="later">${icon('clock', 20)}<span>Mi lista</span></button>
    <button role="menuitem" data-go="subs">${icon('subs', 20)}<span>Suscripciones</span></button>
    <div class="pm-sep"></div>
    <button role="menuitem" data-theme>${icon('sun', 20)}<span>${s.light ? 'Usar tema oscuro' : 'Usar tema claro'}</span></button>
    <button role="menuitem" data-go="settings">${icon('settings', 20)}<span>Configuración</span></button>`;
}
prof.querySelector('.avatar-btn').onclick = () => { paintProf(); prof.classList.toggle('open'); profMenu.querySelector('button')?.focus(); };
profMenu.onclick = (e) => {
  const b = e.target.closest('button'); if (!b) return;
  prof.classList.remove('open');
  if (b.hasAttribute('data-theme')) { setSettings({ light: !store.settings.light }); if (current?.name === 'settings') go('settings', {}, false); return; }
  if (b.dataset.list) go('list', { kind: b.dataset.list, title: b.dataset.list === 'history' ? 'Historial' : 'Ver más tarde' });
  else if (b.dataset.go) { stack.length = 0; go(b.dataset.go, {}, false); }
};
document.addEventListener('mousedown', (e) => { if (!prof.contains(e.target)) prof.classList.remove('open'); });

// ---------- Navegación ----------
const stack = [];
let current = null;
let cleanup = null;
async function go(name, params = {}, push = true) {
  closeMenu();
  if (cleanup) { const c = cleanup; cleanup = null; try { await c(name, params); } catch {} }
  // El reproductor flotante se cierra al abrir Shorts u otro video
  if (mini && (name === 'shorts' || (name === 'watch' && params.v?.id !== mini.video.id))) closeMini();
  if (push && current) stack.push(current);
  current = { name, params };
  document.querySelectorAll('.side nav button').forEach((b) => { const on = b.dataset.go === name; b.classList.toggle('on', on); on ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current'); });
  app.querySelector('.back').style.display = stack.length ? '' : 'none';
  view.scrollTop = 0;
  view.innerHTML = '';
  view.className = 'view-' + name;
  // Al ver un video la barra lateral se achica para que el reproductor sea más grande
  app.classList.toggle('compact', name === 'watch');
  const fn = routes[name];
  try { cleanup = (await fn(view, params)) || null; }
  catch (e) { view.innerHTML = errorBox(e, () => go(name, params, false)); bindRetry(() => go(name, params, false)); }
}
function back() { const prev = stack.pop(); if (prev) go(prev.name, prev.params, false); }
app.querySelector('.back').onclick = back;
window.addEventListener('mouseup', (e) => { if (e.button === 3) back(); });
app.querySelector('.side nav').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-go]');
  if (b) { stack.length = 0; go(b.dataset.go, {}, false); }
});
const emptyBox = (ic, title, text = '', btn = '') => `<div class="empty"><div class="empty-ic">${icon(ic, 34)}</div><h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ''}${btn}</div>`;
const errorBox = (e) => emptyBox('info', 'Algo salió mal', e?.message || String(e), '<button class="btn-red retry">Reintentar</button>');
const bindRetry = (fn) => view.querySelector('.retry')?.addEventListener('click', fn);
const loading = () => `<div class="loader"><div class="spinner"></div></div>`;
/** Esqueletos de carga (en lugar del círculo girando). */
const skCards = (n) => Array.from({ length: n }, () => '<div class="sk-card"><div class="sk sk-thumb"></div><div class="sk sk-line"></div><div class="sk sk-line short"></div></div>').join('');
const skeleton = (kind) => kind === 'home'
  ? `<div class="sk sk-hero"></div><div class="sk sk-h"></div><div class="sk-row">${skCards(5)}</div><div class="sk sk-h"></div><div class="sk-row">${skCards(5)}</div>`
  : `<div class="sk-grid">${skCards(8)}</div>`;

// ---------- Búsqueda (barra superior) ----------
const form = app.querySelector('.searchbar');
const input = form.querySelector('input');
const sug = form.querySelector('.suggest');
let sugTimer = 0;
form.onsubmit = (e) => { e.preventDefault(); const q = input.value.trim(); sug.classList.remove('open'); input.blur(); if (q) go('search', { q }); };
input.addEventListener('input', () => {
  clearTimeout(sugTimer);
  const q = input.value.trim();
  sugTimer = setTimeout(async () => {
    const list = q ? await YT.suggestions(q) : store.searches.slice(0, 8);
    renderSug(list, !q);
  }, 180);
});
input.addEventListener('focus', () => { if (!input.value.trim()) renderSug(store.searches.slice(0, 8), true); });
input.addEventListener('blur', () => setTimeout(() => sug.classList.remove('open'), 150));
function renderSug(list, recent) {
  sug.innerHTML = list.map((s) => `<button type="button" data-q="${esc(s)}">${icon(recent ? 'history' : 'search', 18)}<span>${esc(s)}</span></button>`).join('');
  sug.classList.toggle('open', list.length > 0);
}
sug.addEventListener('mousedown', (e) => {
  const b = e.target.closest('button[data-q]');
  if (b) { e.preventDefault(); input.value = b.dataset.q; form.requestSubmit(); }
});

// ---------- Tarjetas ----------
/** Progreso guardado de un video (para "Continuar viendo" y la barra roja de las tarjetas). */
function progressOf(v) {
  const pos = position(v.id);
  const d = v.seconds;
  if (!pos || !d || v.live) return null;
  return { pct: Math.min(100, Math.max(2, (pos / d) * 100)), left: Math.max(0, d - pos) };
}
const leftText = (sec) => sec >= 3600 ? `Quedan ${Math.floor(sec / 3600)} h ${Math.round((sec % 3600) / 60)} min` : `Quedan ${Math.max(1, Math.round(sec / 60))} min`;
function card(v, opts = {}) {
  const pr = v.short ? null : progressOf(v);
  const c = el(`
    <div class="card ${v.short ? 'card-short' : ''} ${opts.resume ? 'card-resume' : ''}" tabindex="0" role="link" aria-label="${esc(v.title)}">
      <div class="thumb">
        <img loading="lazy" decoding="async" src="${esc(v.thumb)}" alt="" onload="this.classList.add('ok')">
        ${opts.resume ? `<span class="play-ov">${icon('play', 30)}</span>` : ''}
        ${v.live ? '<span class="dur live"><i></i>EN VIVO</span>' : v.duration ? `<span class="dur">${esc(v.duration)}</span>` : ''}
        ${pr ? `<div class="prog"><i style="width:${pr.pct.toFixed(1)}%"></i></div>` : ''}
      </div>
      <div class="meta">
        ${opts.noAvatar || v.short || opts.resume ? '' : `<div class="avatar" data-ch="${esc(v.channelId)}">${v.avatar ? `<img src="${esc(v.avatar)}" alt="">` : esc((v.channel || '?').slice(0, 1))}</div>`}
        <div class="txt">
          <div class="title">${esc(v.title)}</div>
          ${v.short ? `<div class="sub">${esc(v.views)}</div>`
            : opts.resume && pr ? `<div class="sub ch" data-ch="${esc(v.channelId)}">${esc(v.channel)}</div><div class="sub resume-sub"><b>${Math.round(pr.pct)}%</b> · ${leftText(pr.left)}</div>`
            : `<div class="sub ch" data-ch="${esc(v.channelId)}">${esc(v.channel)}</div><div class="sub">${dot(v.views, v.published)}</div>`}
        </div>
        ${v.short ? '' : `<button class="iconbtn kebab" title="Más acciones" aria-label="Más acciones" aria-haspopup="menu">${icon('dots', 20)}</button>`}
      </div>
    </div>`);
  c.addEventListener('click', (e) => {
    const k = e.target.closest('.kebab');
    if (k) { e.stopPropagation(); openMenu(k, v, opts, c); return; }
    const ch = e.target.closest('[data-ch]');
    if (ch && ch.dataset.ch) { e.stopPropagation(); go('channel', { id: ch.dataset.ch }); return; }
    if (v.short) go('shorts', { start: v, list: opts.list }); else go('watch', { v });
  });
  c.addEventListener('keydown', (e) => { if (e.key === 'Enter' && e.target === c) c.click(); });
  return c;
}

// Menú contextual de las tarjetas (⋮)
let menuEl = null;
function closeMenu() { menuEl?.remove(); menuEl = null; }
function openMenu(anchor, v, opts, cardEl) {
  if (menuEl && menuEl.dataset.id === v.id) { closeMenu(); return; }
  closeMenu();
  const items = [
    ['later', isLater(v.id) ? 'Quitar de Mi lista' : 'Guardar en Mi lista', isLater(v.id) ? 'playlistCheck' : 'playlistAdd'],
    ['like', isLiked(v.id) ? 'Quitar de Videos que me gustan' : 'Agregar a Videos que me gustan', 'thumbUp'],
    v.channelId && ['channel', 'Ir al canal', 'subs'],
    ['copy', 'Copiar enlace', 'share'],
    opts.onRemove && ['remove', 'Quitar de esta lista', 'trash'],
  ].filter(Boolean);
  const m = el(`<div class="ctx" role="menu">${items.map(([k, l, ic]) => `<button role="menuitem" data-k="${k}">${icon(ic, 20)}<span>${esc(l)}</span></button>`).join('')}</div>`);
  m.dataset.id = v.id;
  document.body.appendChild(m);
  const r = anchor.getBoundingClientRect();
  m.style.left = Math.max(8, Math.min(innerWidth - m.offsetWidth - 8, r.right - m.offsetWidth)) + 'px';
  m.style.top = (r.bottom + m.offsetHeight + 8 > innerHeight ? Math.max(8, r.top - m.offsetHeight - 4) : r.bottom + 4) + 'px';
  m.addEventListener('click', (e) => {
    const b = e.target.closest('button'); if (!b) return;
    closeMenu();
    const k = b.dataset.k;
    if (k === 'later') toast(toggleLater(v) ? 'Guardado en Mi lista' : 'Quitado de Mi lista');
    else if (k === 'like') toast(toggleLike(v) ? 'Agregado a Videos que me gustan' : 'Quitado de Videos que me gustan');
    else if (k === 'channel') go('channel', { id: v.channelId });
    else if (k === 'copy') { navigator.clipboard.writeText('https://youtu.be/' + v.id); toast('Enlace copiado'); }
    else if (k === 'remove') { opts.onRemove(); cardEl.remove(); }
  });
  m.addEventListener('keydown', (e) => {
    const bs = [...m.querySelectorAll('button')]; const i = bs.indexOf(document.activeElement);
    if (e.key === 'ArrowDown') { e.preventDefault(); bs[(i + 1) % bs.length].focus(); }
    if (e.key === 'ArrowUp') { e.preventDefault(); bs[(i - 1 + bs.length) % bs.length].focus(); }
  });
  menuEl = m;
  m._top = view.scrollTop;
  m.querySelector('button').focus({ preventScroll: true });
}
document.addEventListener('mousedown', (e) => { if (menuEl && !menuEl.contains(e.target) && !e.target.closest('.kebab')) closeMenu(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { closeMenu(); prof.classList.remove('open'); } });
view.addEventListener('scroll', () => { if (menuEl && Math.abs(view.scrollTop - menuEl._top) > 40) closeMenu(); }, { passive: true });

function grid(videos, opts) {
  const g = el(`<div class="grid ${opts?.short ? 'grid-short' : ''}"></div>`);
  videos.forEach((v) => g.appendChild(card(v, { ...opts, list: videos })));
  return g;
}
/** Lista infinita: carga más al bajar. */
function infinite(container, page, opts) {
  const g = grid(page.videos, opts);
  container.appendChild(g);
  let more = page.more;
  const sentinel = el('<div class="sentinel"></div>');
  container.appendChild(sentinel);
  const seen = new Set(page.videos.map((v) => v.id));
  let busy = false;
  const io = new IntersectionObserver(async (ents) => {
    if (!ents[0].isIntersecting || busy || !more) return;
    busy = true;
    sentinel.innerHTML = `<div class="sk-grid">${skCards(4)}</div>`;
    try {
      const p = await more();
      p.videos.filter((v) => !seen.has(v.id)).forEach((v) => { seen.add(v.id); g.appendChild(card(v, opts)); });
      more = p.more;
    } catch { more = null; }
    sentinel.innerHTML = '';
    busy = false;
  }, { root: view, rootMargin: '600px' });
  io.observe(sentinel);
  return () => io.disconnect();
}
function chips(list, selected, onPick) {
  const row = el('<div class="chips" role="tablist"></div>');
  list.forEach(([id, label, ic]) => {
    const b = el(`<button class="chip ${id === selected ? 'on' : ''}" role="tab" aria-selected="${id === selected}">${ic ? icon(ic, 18) : ''}<span>${esc(label)}</span></button>`);
    b.onclick = () => onPick(id);
    row.appendChild(b);
  });
  return row;
}
const section = (title, onMore, sub) => {
  const s = el(`<div class="section"><div class="sec-t"><h2>${esc(title)}</h2>${sub ? `<span>${esc(sub)}</span>` : ''}</div>${onMore ? `<button class="more">Ver todo ${icon('chevron', 18)}</button>` : ''}</div>`);
  if (onMore) s.querySelector('.more').onclick = onMore;
  return s;
};
/** Fila horizontal con flechas para desplazarse. */
function shelf(videos, opts = {}) {
  const wrap = el(`<div class="shelf-wrap at-start"><button class="sh-nav prev" aria-label="Anterior">${icon('first', 28)}</button><div class="shelf ${opts.cls || ''}"></div><button class="sh-nav next" aria-label="Siguiente">${icon('chevron', 28)}</button></div>`);
  const row = wrap.querySelector('.shelf');
  videos.forEach((v) => row.appendChild(card(v, { ...opts, list: videos })));
  const upd = () => { wrap.classList.toggle('at-start', row.scrollLeft < 8); wrap.classList.toggle('at-end', row.scrollLeft + row.clientWidth >= row.scrollWidth - 8); };
  row.addEventListener('scroll', upd, { passive: true });
  requestAnimationFrame(upd);
  wrap.querySelector('.prev').onclick = () => row.scrollBy({ left: -row.clientWidth * 0.85, behavior: 'smooth' });
  wrap.querySelector('.next').onclick = () => row.scrollBy({ left: row.clientWidth * 0.85, behavior: 'smooth' });
  return wrap;
}
/** Banner cinematográfico: destacados que pasan solos cada 3 segundos (se detiene al pasar el mouse). */
function hero(list) {
  const items = list.slice(0, 5);
  const h = el(`<div class="hero" aria-roledescription="carrusel">${items.map((v, i) => `
      <div class="hero-slide ${i === 0 ? 'on' : ''}">
        <img src="${esc(v.thumb.replace(/hq720|hqdefault|mqdefault/, 'maxresdefault'))}" onerror="this.onerror=null;this.src='${esc(v.thumb)}'" alt="">
        <div class="hero-shade"></div>
        <div class="hero-info">
          ${v.live ? '<span class="live-badge"><i></i>EN VIVO</span>' : '<span class="hero-kicker">Destacado</span>'}
          <h1>${esc(v.title)}</h1>
          <div class="hero-ch">${v.avatar ? `<img src="${esc(v.avatar)}" alt="">` : ''}<b>${esc(v.channel)}</b></div>
          <p>${dot(v.views, v.published, v.live ? '' : v.duration)}</p>
          <div class="hero-btns">
            <button class="btn-red big play">${icon('play', 24)} Reproducir</button>
            <button class="btn-glass save">${icon(isLater(v.id) ? 'check' : 'plus', 22)} <span>${isLater(v.id) ? 'En Mi lista' : 'Mi lista'}</span></button>
          </div>
        </div>
      </div>`).join('')}
      <div class="hero-dots">${items.map((_, i) => `<button class="${i === 0 ? 'on' : ''}" data-i="${i}" aria-label="Destacado ${i + 1}"></button>`).join('')}</div>
    </div>`);
  const slides = [...h.querySelectorAll('.hero-slide')];
  const dots = [...h.querySelectorAll('.hero-dots button')];
  slides.forEach((sl, i) => {
    const v = items[i];
    sl.querySelector('.play').onclick = () => go('watch', { v });
    sl.querySelector('img').onclick = () => go('watch', { v });
    sl.querySelector('.save').onclick = (e) => {
      const on = toggleLater(v);
      e.currentTarget.innerHTML = `${icon(on ? 'check' : 'plus', 22)} <span>${on ? 'En Mi lista' : 'Mi lista'}</span>`;
      toast(on ? 'Guardado en Mi lista' : 'Quitado de Mi lista');
    };
  });
  let cur = 0;
  let paused = false;
  const show = (i) => {
    cur = (i + items.length) % items.length;
    slides.forEach((sl, k) => sl.classList.toggle('on', k === cur));
    dots.forEach((d, k) => d.classList.toggle('on', k === cur));
  };
  dots.forEach((d) => (d.onclick = () => show(+d.dataset.i)));
  h.addEventListener('mouseenter', () => (paused = true));
  h.addEventListener('mouseleave', () => (paused = false));
  h.addEventListener('focusin', () => (paused = true));
  h.addEventListener('focusout', () => (paused = false));
  const timer = setInterval(() => {
    if (!document.body.contains(h)) { clearInterval(timer); return; }
    if (!paused && items.length > 1) show(cur + 1);
  }, 3000);
  return h;
}

// ---------- Contenido del Inicio ----------
// Cada categoría usa la búsqueda real de YouTube
const CATS = [
  ['todo', 'Todo', 'apps'], ['películas tráiler 2026', 'Películas', 'movie'], ['series tráiler oficial', 'Series', 'tv'],
  ['música 2026', 'Música', 'music'], ['concierto en vivo completo', 'Conciertos', 'mic'], ['deportes resumen', 'Deportes', 'sports'],
  ['noticias hoy', 'Noticias', 'news'], ['videojuegos', 'Videojuegos', 'game'], ['canciones infantiles', 'Infantil', 'kids'],
  ['tecnología novedades', 'Tecnología', 'tech'],
];
const DISCOVER = [
  ['películas tráiler 2026', 'Películas', 'movie', '#7a1020'], ['series tráiler oficial', 'Series', 'tv', '#3b2a8a'],
  ['música 2026', 'Música', 'music', '#8a1f6e'], ['deportes resumen', 'Deportes', 'sports', '#16704a'],
  ['canciones infantiles', 'Infantil', 'kids', '#a8611a'], ['tecnología novedades', 'Tecnología', 'tech', '#1a5f8a'],
];
const catLabel = (cat) => CATS.find((c) => c[0] === cat)?.[1] || cat;
function catChips(cat) {
  const extra = interests(3).filter((q) => !CATS.some((c) => c[0] === q)).map((q) => [q, q, 'history']);
  const row = chips([...CATS, ...extra], cat, (c) => go('home', { cat: c }, false));
  row.classList.add('cat-chips');
  const more = el(`<button class="chip chip-more">${icon('compass', 18)}<span>Más categorías</span></button>`);
  more.onclick = () => go('explore');
  row.appendChild(more);
  return row;
}
function discover() {
  const box = el(`<div><div class="section"><div class="sec-t"><h2>Descubre</h2><span>Explora por categoría</span></div><button class="more">Ver todo ${icon('chevron', 18)}</button></div><div class="discover"></div></div>`);
  box.querySelector('.more').onclick = () => go('explore');
  const g = box.querySelector('.discover');
  DISCOVER.forEach(([q, label, ic, c]) => {
    const t = el(`<button class="disc" style="--c:${c}"><span class="disc-ic">${icon(ic, 64)}</span><b>${esc(label)}</b><small>Explorar ${icon('chevron', 16)}</small></button>`);
    t.onclick = () => go('home', { cat: q }, false);
    g.appendChild(t);
  });
  return box;
}
const interleave = (lists) => {
  const out = []; const seen = new Set();
  const q = lists.map((l) => [...l]);
  while (q.some((l) => l.length)) q.forEach((l) => { const v = l.shift(); if (v && !seen.has(v.id)) { seen.add(v.id); out.push(v); } });
  return out;
};
async function loadHome(cat) {
  const watched = new Set(store.history.map((v) => v.id));
  if (cat !== 'todo') {
    const q = cat === 'mix' ? 'mix ' + (interests(1)[0] || 'música') : cat;
    const p = await YT.search(q, { uploadDate: cat === 'en vivo ahora' ? undefined : 'month' });
    if (p.videos.length < 8) return YT.search(q);
    return p;
  }
  // Todo = tus temas + lo nuevo de tus suscripciones + algo de música y tendencias
  const topics = interests(3);
  const base = topics.length ? topics : ['música 2026', 'cumbia', 'reggaeton'];
  const subs = store.subs.slice(0, 6);
  const errs = [];
  const fail = (e) => { errs.push(e && e.message || String(e)); YT.debug.push('Inicio: ' + (e && e.message || e)); return []; };
  const jobs = [
    ...base.map((q) => YT.search(q, { uploadDate: 'month' }).then((p) => p.videos).catch(fail)),
    YT.search('tendencias perú').then((p) => p.videos).catch(fail),
    ...subs.map((c) => YT.channelLatest(c.id, 4)),
  ];
  const lists = await Promise.all(jobs);
  const recent = interleave(lists.slice(lists.length - subs.length)).filter((v) => !v.short);
  const mixed = interleave(lists).filter((v) => !watched.has(v.id));
  if (!mixed.length) throw new Error('No se pudo cargar el Inicio.' + (errs.length ? ' Motivo: ' + errs[0] : ' Revisa tu conexión.'));
  return { videos: mixed, recent, more: () => YT.search(base[0]).then((p) => ({ videos: p.videos, more: p.more })) };
}

const routes = {
  async home(v, { cat = 'todo' }) {
    v.appendChild(catChips(cat));
    const body = el(`<div class="home">${skeleton('home')}</div>`);
    v.appendChild(body);
    const p = await loadHome(cat);
    body.innerHTML = '';
    const vids = p.videos;
    if (vids.length < 12) return infinite(body, p);
    body.appendChild(hero(vids.slice(0, 5)));
    if (cat !== 'todo') {
      const title = 'Lo más visto · ' + catLabel(cat);
      body.appendChild(section(title, () => go('more', { cat, title })));
      body.appendChild(shelf(vids.slice(5, 13)));
      body.appendChild(section('Más de ' + catLabel(cat)));
      return infinite(body, { videos: vids.slice(13), more: p.more });
    }
    // Continuar viendo: videos con posición guardada
    const cont = store.history.filter((x) => !x.short && progressOf(x)).slice(0, 12);
    if (cont.length) {
      body.appendChild(section('Continuar viendo', () => go('list', { kind: 'history', title: 'Historial' })));
      body.appendChild(shelf(cont, { resume: true, cls: 'shelf-cont' }));
    }
    body.appendChild(section('Tendencias para ti', () => go('more', { cat, title: 'Tendencias para ti' }), 'Según lo que ves y buscas'));
    body.appendChild(shelf(vids.slice(5, 17)));
    body.appendChild(discover());
    const recent = (p.recent || []).slice(0, 12);
    if (recent.length) {
      body.appendChild(section('Novedades de tus suscripciones', () => go('subs')));
      body.appendChild(shelf(recent));
    }
    const shown = new Set(recent.map((x) => x.id));
    body.appendChild(section('Recomendado para ti'));
    return infinite(body, { videos: vids.slice(17).filter((x) => !shown.has(x.id)), more: p.more });
  },

  async more(v, { cat, title }) {
    v.appendChild(el(`<h1 class="page-title">${esc(title)}</h1>`));
    const body = el(`<div>${skeleton('grid')}</div>`);
    v.appendChild(body);
    const queries = cat === 'todo' ? ['música 2026', 'videojuegos', 'películas tráiler', 'podcast', 'tendencias'] : [cat];
    const lists = await Promise.all(queries.map((q) => YT.search(q).catch(() => ({ videos: [] }))));
    body.innerHTML = '';
    return infinite(body, { videos: interleave(lists.map((l) => l.videos)), more: lists[0].more });
  },

  async search(v, { q }) {
    input.value = q;
    addSearch(q);
    v.appendChild(el(`<h1 class="page-title">Resultados para “${esc(q)}”</h1>`));
    const body = el(`<div>${skeleton('grid')}</div>`);
    v.appendChild(body);
    const p = await YT.searchAll(q);
    body.innerHTML = '';
    if (p.channels.length) {
      const row = el('<div class="ch-results"></div>');
      p.channels.slice(0, 3).forEach((c) => {
        const r = el(`<div class="ch-result"><div class="avatar big">${c.avatar ? `<img src="${esc(c.avatar)}">` : ''}</div><div><b>${esc(c.name)}</b><span>${esc(c.subs)}</span></div></div>`);
        r.onclick = () => go('channel', { id: c.id });
        row.appendChild(r);
      });
      body.appendChild(row);
    }
    if (!p.videos.length) { body.appendChild(el(emptyBox('search', 'Sin resultados', 'Prueba con otras palabras.'))); return; }
    return infinite(body, p);
  },

  async explore(v) {
    v.appendChild(section('Explorar categorías'));
    const cats = [
      ['Películas', 'películas completas en español', 'movie', '#7A1F2B', '#2A0A10'],
      ['Series', 'series tráiler oficial', 'tv', '#3b2a8a', '#120b33'],
      ['Conciertos', 'concierto en vivo completo', 'mic', '#8a1f6e', '#2b0a22'],
      ['En vivo ahora', 'en vivo ahora', 'live', '#b0101f', '#3a050b'],
      ['Podcasts', 'podcast', 'mic', '#4a4f5a', '#15171b'],
      ['Mixes para ti', 'mix ' + (interests(1)[0] || 'música'), 'music', '#6d2bb0', '#200b38'],
      ['Videojuegos', 'videojuegos gameplay', 'game', '#2346B8', '#0B1640'],
      ['Música', 'música 2026', 'music', '#8E2DC6', '#2B0B42'],
      ['Deportes', 'deportes resumen', 'sports', '#1F8A4C', '#0A2E19'],
      ['Noticias', 'noticias hoy', 'news', '#B5462A', '#3A130A'],
      ['Infantil', 'canciones infantiles', 'kids', '#E0912B', '#4A2A06'],
      ['Ciencia y tecnología', 'ciencia y tecnología', 'science', '#1B8FA6', '#06303A'],
      ['Viajes y estilo de vida', 'viajes vlog', 'travel', '#2B6FD6', '#0A2450'],
      ['Comida y recetas', 'recetas fáciles', 'food', '#C7502A', '#43160A'],
    ];
    const g = el('<div class="cats"></div>');
    cats.forEach(([label, q, ic, a, b]) => {
      const t = el(`<button class="cat" style="background:linear-gradient(135deg,${a},${b})">${icon(ic, 34)}<span>${esc(label)}</span></button>`);
      t.onclick = () => go('search', { q });
      g.appendChild(t);
    });
    v.appendChild(g);
  },

  async watch(v, { v: video }) {
    v.innerHTML = `
      <div class="watch">
        <div class="watch-main">
          <div class="player-host"></div>
          <div class="w-info">${loading()}</div>
        </div>
        <aside class="watch-side"><div class="autoplay-row"></div><div class="related"></div></aside>
      </div>`;
    const info = v.querySelector('.w-info');
    let details = null;
    let nextVideo = null;
    let full = video;
    let p, popts;
    if (mini && mini.video.id === video.id) {
      ({ p, popts } = takeMini());
      v.querySelector('.player-host').appendChild(p.root);
    } else {
      popts = {};
      p = createPlayer(v.querySelector('.player-host'), popts);
      p.load(video.id, video);
    }
    popts.onClose = () => back();
    popts.onTitle = () => info.querySelector('.desc')?.classList.add('open');
    popts.onEnded = () => { if (store.settings.autoplay && nextVideo) go('watch', { v: nextVideo }, false); };
    // Al salir mientras se reproduce, el video sigue en el reproductor flotante
    const leave = (next) => {
      if (next !== 'watch' && next !== 'shorts' && !p.video.paused && !p.video.ended && !document.fullscreenElement) return toMini(p, popts, full);
      return p.destroy();
    };
    addHistory(video);
    try {
      details = await YT.details(video.id);
    } catch (e) {
      info.innerHTML = `<h1 class="w-title">${esc(video.title)}</h1><p class="muted">No se pudieron cargar los detalles: ${esc(e.message)}</p>`;
      return leave;
    }
    full = { ...video, title: details.title || video.title, channel: details.channel || video.channel, channelId: details.channelId || video.channelId, avatar: details.avatar || video.avatar, seconds: details.seconds || video.seconds };
    addHistory(full);
    const watched = new Set(store.history.slice(0, 50).map((x) => x.id));
    nextVideo = details.related.find((r) => !watched.has(r.id)) || details.related[0] || null;

    const likes = details.likes >= 0 ? Intl.NumberFormat('es', { notation: 'compact' }).format(details.likes + (isLiked(video.id) ? 1 : 0)) : '';
    info.innerHTML = `
      <h1 class="w-title">${esc(full.title)}</h1>
      <div class="w-bar">
        <div class="w-channel card-bg">
          <div class="avatar big" data-ch="${esc(full.channelId)}">${full.avatar ? `<img src="${esc(full.avatar)}">` : ''}</div>
          <div class="w-chname" data-ch="${esc(full.channelId)}"><b>${esc(full.channel)}</b><span>${esc(details.subs)}</span></div>
          <button class="btn-red sub"></button>
        </div>
        <div class="w-actions">
          <div class="pill likes"><button class="like">${icon('thumbUp', 20)}<span>${likes}</span></button><i></i><button class="dislike">${icon('thumbDown', 20)}</button></div>
          <button class="pill share">${icon('share', 20)}<span>Compartir</span></button>
          <button class="pill save">${icon('playlistAdd', 20)}<span>Agregar a lista</span></button>
        </div>
      </div>
      <div class="desc card-bg"><b>${dot(details.views, details.published)}</b><div class="desc-text">${esc(details.description)}</div><button class="desc-more">más</button></div>
      <div class="comments card-bg"><h3>Comentarios</h3><div class="c-list">${loading()}</div></div>`;
    const $ = (s) => info.querySelector(s);
    info.querySelectorAll('[data-ch]').forEach((x) => x.onclick = () => full.channelId && go('channel', { id: full.channelId }));
    const subBtn = $('.sub');
    const paintSub = () => { const on = isSub(full.channelId); subBtn.textContent = on ? 'Suscrito' : 'Suscribirse'; subBtn.classList.toggle('subbed', on); };
    paintSub();
    subBtn.onclick = () => { if (!full.channelId) return; const on = toggleSub({ id: full.channelId, name: full.channel, avatar: full.avatar }); paintSub(); toast(on ? 'Suscrito a ' + full.channel : 'Suscripción cancelada'); };
    const paintLike = () => { $('.like').classList.toggle('on', isLiked(video.id)); };
    paintLike();
    $('.like').onclick = () => { const on = toggleLike(full); paintLike(); $('.dislike').classList.remove('on'); toast(on ? 'Agregado a Videos que me gustan' : 'Quitado de Videos que me gustan'); };
    $('.dislike').onclick = (e) => { e.currentTarget.classList.toggle('on'); if (isLiked(video.id)) { toggleLike(full); paintLike(); } };
    $('.share').onclick = () => { navigator.clipboard.writeText('https://youtu.be/' + video.id); toast('Enlace copiado'); };
    const paintSave = () => { const on = isLater(video.id); $('.save').innerHTML = `${icon(on ? 'playlistCheck' : 'playlistAdd', 20)}<span>${on ? 'En tu lista' : 'Agregar a lista'}</span>`; $('.save').classList.toggle('on', on); };
    paintSave();
    $('.save').onclick = () => { const on = toggleLater(full); paintSave(); toast(on ? 'Agregado a Ver más tarde' : 'Quitado de Ver más tarde'); };
    $('.desc-more').onclick = () => $('.desc').classList.toggle('open');

    // Modo cine (pantalla completa): título, canal • duración • año, botones y "Siguiente"
    const mins = full.seconds ? (full.seconds >= 3600 ? `${Math.floor(full.seconds / 3600)}h ${Math.floor((full.seconds % 3600) / 60)}min` : `${Math.max(1, Math.round(full.seconds / 60))} min`) : '';
    const cineActions = el(`<div class="cine-row">
      <div class="pill glassy likes"><button class="like">${icon('thumbUp', 22)}<span>${likes}</span></button><i></i><button class="dislike">${icon('thumbDown', 22)}</button></div>
      <button class="pill glassy share">${icon('share', 22)}<span>Compartir</span></button>
      <button class="pill glassy save">${icon('playlistAdd', 22)}<span>Agregar a lista</span></button>
      <div class="vp-grow"></div>
      ${nextVideo ? `<button class="next-card"><img src="${esc(nextVideo.thumb)}"><div><small>Siguiente:</small><b>${esc(nextVideo.title)}</b></div><i></i>${icon('play', 30)}</button>` : ''}
    </div>`);
    cineActions.querySelector('.like').onclick = () => $('.like').click();
    cineActions.querySelector('.dislike').onclick = () => $('.dislike').click();
    cineActions.querySelector('.share').onclick = () => $('.share').click();
    cineActions.querySelector('.save').onclick = () => { $('.save').click(); cineActions.querySelector('.save').innerHTML = $('.save').innerHTML; };
    cineActions.querySelector('.next-card')?.addEventListener('click', () => go('watch', { v: nextVideo }, false));
    p.setCinema({ title: full.title, subtitle: [full.channel, mins, details.year].filter(Boolean).join(' • '), actions: cineActions });

    // Columna derecha: siguiente + relacionados
    const auto = v.querySelector('.autoplay-row');
    const paintAuto = () => {
      auto.innerHTML = `<div><b>Reproducción automática</b><span>${store.settings.autoplay && nextVideo ? 'A continuación: ' + esc(nextVideo.title) : store.settings.autoplay ? '' : 'Desactivada'}</span></div><label class="switch"><input type="checkbox" ${store.settings.autoplay ? 'checked' : ''}><i></i></label>`;
      auto.querySelector('input').onchange = (e) => { setSettings({ autoplay: e.target.checked }); paintAuto(); };
    };
    paintAuto();
    const rel = v.querySelector('.related');
    details.related.forEach((r) => {
      const row = el(`<div class="rel"><div class="thumb"><img loading="lazy" src="${esc(r.thumb)}">${r.duration ? `<span class="dur">${esc(r.duration)}</span>` : ''}</div><div class="txt"><div class="title">${esc(r.title)}</div><div class="sub">${esc(r.channel)}</div><div class="sub">${dot(r.views, r.published)}</div></div></div>`);
      row.onclick = () => go('watch', { v: r }, false);
      rel.appendChild(row);
    });

    // Comentarios
    YT.comments(video.id).then((c) => {
      const box = $('.c-list');
      if (!box) return;
      info.querySelector('.comments h3').textContent = 'Comentarios' + (c.count ? ' · ' + c.count.replace(/comentarios?/i, '').trim() : '');
      box.innerHTML = c.list.length ? c.list.slice(0, 40).map((x) => `
        <div class="cm"><div class="avatar">${x.avatar ? `<img src="${esc(x.avatar)}">` : ''}</div>
        <div><b>${esc(x.author)}</b> <span class="muted">${esc(x.date)}</span><p>${esc(x.text)}</p>${x.likes ? `<span class="muted">${icon('thumbUp', 14)} ${esc(x.likes)}</span>` : ''}</div></div>`).join('')
        : '<p class="muted">No hay comentarios.</p>';
    });
    return leave;
  },

  async shorts(v, { start, list }) {
    v.innerHTML = `<div class="shorts"><div class="short-stage"><div class="player-host"></div>
      <div class="short-info"></div>
      <div class="short-actions">
        <button class="sa like">${icon('thumbUp', 26)}<span>Me gusta</span></button>
        <button class="sa share">${icon('share', 26)}<span>Compartir</span></button>
        <button class="sa save">${icon('playlistAdd', 26)}<span>Guardar</span></button>
      </div></div>
      <div class="short-nav"><button class="glass up" title="Anterior (↑)">${icon('down', 30)}</button><button class="glass dn" title="Siguiente (↓)">${icon('down', 30)}</button></div></div>`;
    let items = list && list.length ? list.filter((x) => x.short) : [];
    if (start && !items.some((x) => x.id === start.id)) items.unshift(start);
    let idx = Math.max(0, items.findIndex((x) => x.id === start?.id));
    const topics = [...interests(4), 'música', 'humor', 'fútbol', 'baile', 'recetas', 'viral'];
    let round = 0;
    const loadMore = async () => {
      const q = topics[round++ % topics.length];
      const more = await YT.shorts(q).catch((e) => { YT.debug.push('shorts ' + q + ': ' + e.message); return []; });
      const seen = new Set(items.map((x) => x.id));
      items.push(...more.filter((x) => !seen.has(x.id)).sort(() => Math.random() - 0.5));
    };
    // Probamos varios temas hasta encontrar Shorts
    for (let tries = 0; !items.length && tries < 5; tries++) { v.querySelector('.short-info').innerHTML = loading(); await loadMore(); }
    if (!items.length) throw new Error('No se encontraron Shorts. Revisa tu conexión y toca Reintentar.');
    const p = createPlayer(v.querySelector('.player-host'), { vertical: true, loop: true, onClose: () => back() });
    const infoBox = v.querySelector('.short-info');
    const show = async () => {
      const s = items[idx];
      p.load(s.id, s);
      addHistory(s);
      infoBox.innerHTML = `<b>${esc(s.title)}</b><span>${esc(s.views)}</span>`;
      v.querySelector('.like').classList.toggle('on', isLiked(s.id));
      v.querySelector('.save').classList.toggle('on', isLater(s.id));
      if (idx >= items.length - 3) loadMore();
      YT.details(s.id).then((d) => {
        if (items[idx]?.id !== s.id) return;
        s.channel = d.channel; s.channelId = d.channelId; s.avatar = d.avatar;
        infoBox.innerHTML = `<div class="short-ch" data-ch="${esc(d.channelId)}"><div class="avatar">${d.avatar ? `<img src="${esc(d.avatar)}">` : ''}</div><b>${esc(d.channel)}</b></div><b>${esc(s.title)}</b><span>${esc(s.views || d.views)}</span>`;
        infoBox.querySelector('[data-ch]').onclick = () => go('channel', { id: d.channelId });
      }).catch(() => {});
    };
    const step = (d) => { const n = idx + d; if (n < 0 || n >= items.length) return; idx = n; show(); };
    v.querySelector('.up').onclick = () => step(-1);
    v.querySelector('.dn').onclick = () => step(1);
    v.querySelector('.like').onclick = (e) => { const on = toggleLike(items[idx]); e.currentTarget.classList.toggle('on', on); };
    v.querySelector('.save').onclick = (e) => { const on = toggleLater(items[idx]); e.currentTarget.classList.toggle('on', on); toast(on ? 'Guardado en Ver más tarde' : 'Quitado'); };
    v.querySelector('.share').onclick = () => { navigator.clipboard.writeText('https://youtube.com/shorts/' + items[idx].id); toast('Enlace copiado'); };
    let wheelLock = 0;
    const onWheel = (e) => { if (Date.now() < wheelLock) return; wheelLock = Date.now() + 600; step(e.deltaY > 0 ? 1 : -1); };
    const onKey = (e) => { if (e.key === 'ArrowDown') { e.preventDefault(); step(1); } if (e.key === 'ArrowUp') { e.preventDefault(); step(-1); } };
    v.querySelector('.short-stage').addEventListener('wheel', onWheel, { passive: true });
    document.addEventListener('keydown', onKey);
    show();
    return () => { document.removeEventListener('keydown', onKey); return p.destroy(); };
  },

  async subs(v) {
    const subs = store.subs;
    v.appendChild(el('<h1 class="page-title">Suscripciones</h1>'));
    if (!subs.length) { v.appendChild(el(emptyBox('subs', 'Aún no tienes suscripciones', 'Entra a un canal y toca Suscribirse para ver aquí sus videos nuevos.', '<button class="btn-red go-explore">Explorar</button>'))); v.querySelector('.go-explore').onclick = () => go('explore'); return; }
    const row = el('<div class="sub-row"></div>');
    subs.forEach((c) => {
      const b = el(`<button class="sub-ch"><div class="avatar big">${c.avatar ? `<img src="${esc(c.avatar)}">` : ''}</div><span>${esc(c.name)}</span></button>`);
      b.onclick = () => go('channel', { id: c.id });
      row.appendChild(b);
    });
    v.appendChild(row);
    const body = el(`<div>${skeleton('grid')}</div>`);
    v.appendChild(body);
    const lists = await Promise.all(subs.slice(0, 20).map((c) => YT.channelLatest(c.id, 8)));
    body.innerHTML = '';
    const vids = interleave(lists);
    if (!vids.length) { body.appendChild(el(emptyBox('subs', 'Sin videos nuevos'))); return; }
    body.appendChild(grid(vids));
  },

  async library(v) {
    v.appendChild(el('<h1 class="page-title">Biblioteca</h1>'));
    const lists = [
      ['history', 'Historial', 'history', store.history],
      ['later', 'Mi lista (Ver más tarde)', 'clock', store.later],
      ['liked', 'Videos que me gustan', 'thumbUp', store.liked],
    ];
    const menu = el('<div class="lib-menu"></div>');
    lists.forEach(([k, label, ic, list]) => {
      const b = el(`<button class="lib-item card-bg"><span class="tile">${icon(ic, 24)}</span><div><b>${label}</b><span>${list.length} videos</span></div>${icon('chevron', 22)}</button>`);
      b.onclick = () => go('list', { kind: k, title: label });
      menu.appendChild(b);
    });
    v.appendChild(menu);
    if (store.history.length) {
      v.appendChild(section('Historial', () => go('list', { kind: 'history', title: 'Historial' })));
      v.appendChild(shelf(store.history.slice(0, 16)));
    }
    if (store.later.length) {
      v.appendChild(section('Ver más tarde', () => go('list', { kind: 'later', title: 'Ver más tarde' })));
      v.appendChild(shelf(store.later.slice(0, 16)));
    }
  },

  async list(v, { kind, title }) {
    v.appendChild(el(`<h1 class="page-title">${esc(title)}</h1>`));
    const list = store[kind] || [];
    if (!list.length) { v.appendChild(el(emptyBox(kind === 'history' ? 'history' : kind === 'later' ? 'clock' : 'thumbUp', 'No hay videos aquí', kind === 'later' ? 'Usa “Mi lista” o el menú ⋮ de una tarjeta para guardar videos.' : ''))); return; }
    const remove = { history: (x) => removeHistory(x.id), later: (x) => toggleLater(x), liked: (x) => toggleLike(x) }[kind];
    const g = el('<div class="grid"></div>');
    list.forEach((x) => g.appendChild(card(x, { onRemove: () => remove(x) })));
    v.appendChild(g);
  },

  async channel(v, { id }) {
    v.innerHTML = loading();
    const c = await YT.channel(id);
    v.innerHTML = '';
    const head = el(`
      <div class="channel-head">
        ${c.banner ? `<img class="banner" src="${esc(c.banner)}">` : ''}
        <div class="ch-row">
          <div class="ring"><div class="avatar huge">${c.avatar ? `<img src="${esc(c.avatar)}">` : ''}</div></div>
          <div class="ch-text"><h1>${esc(c.name)}</h1><span>${esc(c.subs)}</span><p>${esc(c.description).slice(0, 220)}</p></div>
          <button class="btn-red sub"></button>
        </div>
      </div>`);
    const subBtn = head.querySelector('.sub');
    const paint = () => { const on = isSub(id); subBtn.textContent = on ? 'Suscrito' : 'Suscribirse'; subBtn.classList.toggle('subbed', on); };
    paint();
    subBtn.onclick = () => { const on = toggleSub({ id, name: c.name, avatar: c.avatar }); paint(); toast(on ? 'Suscrito a ' + c.name : 'Suscripción cancelada'); };
    v.appendChild(head);
    const body = el('<div></div>');
    let stop = null;
    const show = async (tab) => {
      if (stop) stop();
      tabs.replaceWith(tabs = chips([['videos', 'Videos'], ['shorts', 'Shorts']], tab, show));
      body.innerHTML = '';
      if (tab === 'videos') {
        const vids = c.videos.map((x) => ({ ...x, channel: x.channel || c.name, channelId: id, avatar: x.avatar || c.avatar }));
        stop = infinite(body, { videos: vids, more: c.more }, { noAvatar: true });
      } else {
        body.innerHTML = loading();
        const s = await c.getShorts();
        body.innerHTML = '';
        body.appendChild(s.length ? grid(s, { short: true }) : el('<div class="empty"><h3>Este canal no tiene Shorts</h3></div>'));
      }
    };
    let tabs = chips([['videos', 'Videos'], ['shorts', 'Shorts']], 'videos', show);
    v.appendChild(tabs);
    v.appendChild(body);
    await show('videos');
    return () => stop?.();
  },

  async settings(v) {
    const s = store.settings;
    const sw = (key, on) => `<label class="switch"><input type="checkbox" data-k="${key}" ${on ? 'checked' : ''}><i></i></label>`;
    v.innerHTML = `<div class="settings-wrap">
      <h1 class="page-title">Configuración</h1>
      <div class="set-group"><h4>Apariencia</h4><div class="card-bg">
        <div class="set-row"><span class="tile">${icon('sun', 22)}</span><div><b>Modo claro</b><span>${s.light ? 'Activado' : 'Desactivado · la app usa el modo oscuro'}</span></div>${sw('light', s.light)}</div>
      </div></div>
      <div class="set-group"><h4>Reproducción y calidad</h4><div class="card-bg">
        <div class="set-row"><span class="tile">${icon('hq', 22)}</span><div><b>Calidad predeterminada</b><span>Menos calidad = menos datos</span></div>
          <select data-k="quality">${['auto', '2160', '1440', '1080', '720', '480', '360'].map((q) => `<option value="${q}" ${String(s.quality) === q ? 'selected' : ''}>${q === 'auto' ? 'Automática' : q + 'p'}</option>`).join('')}</select></div>
        <div class="set-row"><span class="tile">${icon('skip', 22)}</span><div><b>Reproducción automática</b><span>Al terminar un video, sigue con uno relacionado</span></div>${sw('autoplay', s.autoplay)}</div>
      </div></div>
      <div class="set-group"><h4>Privacidad</h4><div class="card-bg">
        <div class="set-row"><span class="tile">${icon('history', 22)}</span><div><b>Guardar historial</b><span>Videos vistos y búsquedas recientes</span></div>${sw('saveHistory', s.saveHistory)}</div>
        <button class="set-row clear"><span class="tile">${icon('trash', 22)}</span><div><b>Borrar historial</b><span>Videos vistos, búsquedas y posiciones</span></div>${icon('chevron', 22)}</button>
      </div></div>
      <div class="set-group"><h4>Acerca de</h4><div class="card-bg">
        <button class="set-row upd"><span class="tile">${icon('update', 22)}</span><div><b>Buscar actualizaciones</b><span class="upd-st">Desde GitHub</span></div>${icon('chevron', 22)}</button>
        <div class="set-row"><span class="tile">${icon('info', 22)}</span><div><b>Vidtube para PC</b><span>Versión ${esc(VERSION)} · Funciona con YouTube.js · Atajos: espacio, ← →, F, M, C, P</span></div></div>
        <button class="set-row dbg"><span class="tile">${icon('bug', 22)}</span><div><b>Diagnóstico</b><span>Registro para revisar problemas de reproducción</span></div>${icon('chevron', 22)}</button>
        <pre class="dbg-log"></pre>
      </div></div></div>`;
    v.querySelectorAll('input[data-k]').forEach((i) => i.onchange = () => { setSettings({ [i.dataset.k]: i.checked }); if (i.dataset.k === 'light') go('settings', {}, false); });
    v.querySelector('select').onchange = (e) => setSettings({ quality: e.target.value });
    v.querySelector('.clear').onclick = () => { if (confirm('¿Borrar historial? Se borrarán los videos vistos, las búsquedas y las posiciones.')) { clearHistory(); toast('Historial borrado'); } };
    v.querySelector('.upd').onclick = async () => { v.querySelector('.upd-st').textContent = 'Revisando…'; const r = await checkUpdate(true); v.querySelector('.upd-st').textContent = r; };
    v.querySelector('.dbg').onclick = () => { const l = v.querySelector('.dbg-log'); l.textContent = YT.debug.join('\n') || 'Sin registros todavía.'; l.classList.toggle('open'); };
  },
};

// ---------- Explorar desde la barra de búsqueda vacía ----------
input.addEventListener('keydown', (e) => { if (e.key === 'Escape') input.blur(); });
app.querySelector('.searchbar button').addEventListener('click', (e) => { if (!input.value.trim()) { e.preventDefault(); go('explore'); } });

// ---------- Reproductor flotante ----------
let mini = null;
function toMini(p, popts, video) {
  const d = el(`<div class="mini" role="region" aria-label="Reproductor flotante">
      <div class="mini-host"><div class="mini-hit" title="Volver al video"></div></div>
      <div class="mini-bar">
        <div class="mini-txt"><b>${esc(video.title)}</b><span>${esc(video.channel || '')}</span></div>
        <button class="iconbtn m-play" title="Pausar o reproducir" aria-label="Pausar o reproducir"></button>
        <button class="iconbtn m-pip" title="Imagen en imagen" aria-label="Imagen en imagen">${icon('pip', 20)}</button>
        <button class="iconbtn m-open" title="Volver al video" aria-label="Volver al video">${icon('expand', 20)}</button>
        <button class="iconbtn m-close" title="Cerrar" aria-label="Cerrar">${icon('close', 20)}</button>
      </div>
    </div>`);
  d.querySelector('.mini-host').prepend(p.root);
  p.root.classList.remove('show');
  document.body.appendChild(d);
  const paint = () => { d.querySelector('.m-play').innerHTML = icon(p.video.paused ? 'play' : 'pause', 22); };
  paint();
  p.video.addEventListener('play', paint);
  p.video.addEventListener('pause', paint);
  popts.onEnded = () => closeMini();
  popts.onClose = () => closeMini();
  popts.onTitle = () => {};
  const open = () => go('watch', { v: video });
  d.querySelector('.mini-hit').onclick = open;
  d.querySelector('.m-open').onclick = open;
  d.querySelector('.m-play').onclick = () => (p.video.paused ? p.video.play() : p.video.pause());
  d.querySelector('.m-pip').onclick = async () => {
    try { document.pictureInPictureElement ? await document.exitPictureInPicture() : await p.video.requestPictureInPicture(); }
    catch (e) { toast('Imagen en imagen no disponible'); }
  };
  d.querySelector('.m-close').onclick = () => closeMini();
  mini = { p, popts, video, d, paint };
}
function takeMini() {
  const m = mini; mini = null;
  m.p.video.removeEventListener('play', m.paint);
  m.p.video.removeEventListener('pause', m.paint);
  m.d.remove();
  return m;
}
function closeMini() {
  if (!mini) return;
  const m = takeMini();
  if (document.pictureInPictureElement) document.exitPictureInPicture().catch(() => {});
  m.p.destroy();
}

// ---------- Suscripciones en la barra lateral ----------
function paintSideSubs() {
  const box = app.querySelector('.side-subs');
  const subs = store.subs;
  box.innerHTML = subs.length ? `<h5>Suscripciones</h5>` + subs.slice(0, 12).map((c) => `<button data-id="${esc(c.id)}"><div class="avatar sm">${c.avatar ? `<img src="${esc(c.avatar)}">` : ''}</div><span>${esc(c.name)}</span></button>`).join('') : '';
  box.querySelectorAll('button').forEach((b) => b.onclick = () => go('channel', { id: b.dataset.id }));
}
paintSideSubs(); onChange(paintSideSubs);

// ---------- Actualizaciones (GitHub) ----------
const verNum = (s) => String(s).split('.').map((n) => parseInt(n, 10) || 0);
const newer = (a, b) => { const x = verNum(a), y = verNum(b); for (let i = 0; i < 3; i++) { if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0); } return false; };
async function checkUpdate(manual) {
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30`);
    const rel = (await r.json()).find((x) => (x.assets || []).some((a) => a.name.endsWith('.exe')));
    if (!rel) return 'No hay versiones para PC todavía';
    const asset = rel.assets.find((a) => a.name.endsWith('.exe'));
    const ver = (asset.name.match(/(\d+\.\d+\.\d+)/) || [])[1];
    if (ver && VERSION !== 'dev' && newer(ver, VERSION)) {
      const pill = app.querySelector('.update-pill');
      pill.innerHTML = `<button class="btn-red">${icon('update', 18)} Actualizar a ${esc(ver)}</button>`;
      pill.querySelector('button').onclick = () => window.open(asset.browser_download_url);
      if (manual) window.open(asset.browser_download_url);
      return `Disponible: ${ver} (se descarga en tu navegador)`;
    }
    return 'Tienes la última versión';
  } catch (e) {
    return 'No se pudo revisar: ' + e.message;
  }
}

// Acceso para pruebas automáticas
window.__vt = { go, debug: () => YT.debug, mini: () => !!mini };

// ---------- Arranque ----------
(async () => {
  try { await YT.init(); } catch (e) { console.error(e); YT.debug.push('No se pudo iniciar: ' + (e && e.message)); }
  setTimeout(() => document.getElementById('splash')?.classList.add('hide'), 600);
  setTimeout(() => document.getElementById('splash')?.remove(), 1300);
  go('home');
  setTimeout(() => checkUpdate(false), 4000);
})();
