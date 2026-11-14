// Vidtube para TV Samsung (Tizen): misma lógica que la PC, interfaz para control remoto
import 'core-js/stable';
import * as YT from '../../pc/src/yt.js';
import { icon } from '../../pc/src/icons.js';
import { store, onChange, addHistory, toggleLater, toggleLike, isLater, isLiked, isSub, toggleSub, addSearch, setSettings, interests, savePosition, position } from '../../pc/src/store.js';
import shaka from 'shaka-player/dist/shaka-player.compiled.js';

const VERSION = '__VERSION__';
const app = document.getElementById('app');
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const el = (html) => { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; };
const dot = (...p) => p.filter(Boolean).map(esc).join(' · ');
const loading = () => '<div class="loader"><div class="spinner"></div></div>';
function toast(msg) {
  const t = el(`<div class="toast">${esc(msg)}</div>`);
  document.body.appendChild(t);
  setTimeout(() => t.classList.add('on'), 10);
  setTimeout(() => { t.classList.remove('on'); setTimeout(() => t.remove(), 300); }, 2400);
}

// ---------- Teclas del control remoto ----------
const KEY = { LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13, BACK: 10009, ESC: 27, BACKSPACE: 8, PLAYPAUSE: 10252, PLAY: 415, PAUSE: 19, REW: 412, FF: 417, STOP: 413 };
try {
  window.tizen && tizen.tvinputdevice.registerKeyBatch(['MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaRewind', 'MediaFastForward', 'MediaStop']);
} catch (e) {}

// ---------- Navegación con flechas (busca el elemento más cercano en esa dirección) ----------
let focused = null;
function focusables() {
  return [...document.querySelectorAll('.f')].filter((e) => {
    if (e.closest('.hidden') || e.closest('.off')) return false;
    const r = e.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  });
}
function setFocus(e, scroll = true) {
  if (!e) return;
  if (focused) focused.classList.remove('focus');
  focused = e;
  e.classList.add('focus');
  // La barra lateral se abre (con nombres) cuando el foco está en ella
  app.querySelector('.rail').classList.toggle('open', e.classList.contains('nav'));
  const sb = e.closest('.search-box');
  document.querySelectorAll('.search-box').forEach((b) => b.classList.toggle('focused', b === sb));
  if (e.tagName === 'INPUT') e.focus(); else if (document.activeElement && document.activeElement.tagName === 'INPUT') document.activeElement.blur();
  if (scroll) {
    const row = e.closest('.row-scroll');
    if (row) {
      const r = e.getBoundingClientRect(), rr = row.getBoundingClientRect();
      if (r.left < rr.left + 40) row.scrollLeft -= rr.left + 40 - r.left;
      else if (r.right > rr.right - 40) row.scrollLeft += r.right - rr.right + 40;
    }
    const pane = e.closest('.scroll-y');
    if (pane) {
      const r = e.getBoundingClientRect(), pr = pane.getBoundingClientRect();
      if (r.top < pr.top + 120) pane.scrollTop -= pr.top + 120 - r.top;
      else if (r.bottom > pr.bottom - 60) pane.scrollTop += r.bottom - pr.bottom + 60;
    }
  }
}
function move(dir) {
  const list = focusables();
  if (!focused || !document.body.contains(focused) || !list.includes(focused)) { setFocus(list[0]); return; }
  const a = focused.getBoundingClientRect();
  const ax = a.left + a.width / 2, ay = a.top + a.height / 2;
  let best = null, bestScore = Infinity;
  const inRail = !!focused.closest('.rail');
  for (const e of list) {
    if (e === focused) continue;
    // Arriba/abajo no salta entre el menú lateral y el contenido (solo izquierda/derecha)
    if ((dir === 'up' || dir === 'down') && !!e.closest('.rail') !== inRail) continue;
    const b = e.getBoundingClientRect();
    const bx = b.left + b.width / 2, by = b.top + b.height / 2;
    let main, cross;
    if (dir === 'left') { if (b.right > a.left + 2) continue; main = a.left - b.right; cross = Math.abs(by - ay); }
    if (dir === 'right') { if (b.left < a.right - 2) continue; main = b.left - a.right; cross = Math.abs(by - ay); }
    if (dir === 'up') { if (b.bottom > a.top + 2) continue; main = a.top - b.bottom; cross = Math.abs(bx - ax); }
    if (dir === 'down') { if (b.top < a.bottom - 2) continue; main = b.top - a.bottom; cross = Math.abs(bx - ax); }
    const score = main + cross * 2.2;
    if (score < bestScore) { bestScore = score; best = e; }
  }
  if (best) setFocus(best);
}

// ---------- Estructura: barra lateral + contenido ----------
app.innerHTML = `
  <nav class="rail">
    <img class="rail-mark" src="assets/mark.png" alt="">
    <button class="f nav" data-go="home">${icon('home', 34)}<span>Inicio</span></button>
    <button class="f nav" data-go="search">${icon('compass', 34)}<span>Explorar</span></button>
    <button class="f nav" data-go="subs">${icon('subs', 34)}<span>Suscripciones</span></button>
    <button class="f nav" data-go="library">${icon('bookmark', 34)}<span>Biblioteca</span></button>
    <button class="f nav" data-go="settings">${icon('settings', 34)}<span>Configuración</span></button>
  </nav>
  <main id="view" class="scroll-y"></main>
  <div id="watch" class="hidden"></div>`;
const view = document.getElementById('view');
const watchBox = document.getElementById('watch');
app.querySelectorAll('.nav').forEach((b) => (b.onclick = () => { stack.length = 0; go(b.dataset.go, {}, false); }));

const stack = [];
let current = null;
let cleanup = null;
async function go(name, params = {}, push = true) {
  if (cleanup) { try { await cleanup(); } catch (e) {} cleanup = null; }
  if (push && current) stack.push(current);
  current = { name, params };
  app.querySelectorAll('.nav').forEach((b) => b.classList.toggle('on', b.dataset.go === name));
  view.scrollTop = 0;
  view.innerHTML = loading();
  try {
    cleanup = (await routes[name](params)) || null;
  } catch (e) {
    view.innerHTML = `<div class="empty"><h2>Algo salió mal</h2><p>${esc(e && e.message || e)}</p><button class="f btn-red retry">Reintentar</button></div>`;
    view.querySelector('.retry').onclick = () => go(name, params, false);
  }
  const first = view.querySelector('.f');
  setFocus(first || app.querySelector('.nav.on'));
}
function back() {
  if (!watchBox.classList.contains('hidden')) { closeWatch(); return; }
  const prev = stack.pop();
  if (prev) go(prev.name, prev.params, false);
  else if (focused && !focused.classList.contains('nav')) setFocus(app.querySelector('.nav.on'));
  else { try { tizen.application.getCurrentApplication().exit(); } catch (e) {} }
}

// ---------- Tarjetas y filas ----------
/** Barra roja con lo que ya viste del video. */
function progBar(v) {
  const pos = position(v.id);
  if (!pos || !v.seconds || v.live) return '';
  return `<span class="prog"><i style="width:${Math.min(100, Math.max(2, pos / v.seconds * 100)).toFixed(1)}%"></i></span>`;
}
function card(v, list) {
  const c = el(`
    <button class="f card ${v.short ? 'short' : ''}">
      <div class="thumb"><img src="${esc(v.thumb)}" alt="">${v.live ? '<span class="dur live"><i></i>EN VIVO</span>' : v.duration ? `<span class="dur">${esc(v.duration)}</span>` : ''}${progBar(v)}</div>
      <div class="title">${esc(v.title)}</div>
      <div class="sub">${dot(v.channel, v.views)}</div>
    </button>`);
  c.onclick = () => openWatch(v, list);
  return c;
}
function row(title, videos, sub) {
  const r = el(`<section class="row"><h2>${esc(title)}${sub ? `<small>${esc(sub)}</small>` : ''}</h2><div class="row-scroll"></div></section>`);
  const box = r.querySelector('.row-scroll');
  videos.forEach((v) => box.appendChild(card(v, videos)));
  return r;
}
function grid(videos) {
  const g = el('<div class="grid"></div>');
  videos.forEach((v) => g.appendChild(card(v, videos)));
  return g;
}
/** Destacados que pasan solos cada 3 segundos (se detiene mientras el foco está en ellos). */
function hero(list) {
  const items = list.slice(0, 5);
  const h = el(`<section class="hero">${items.map((v, i) => `
      <div class="hero-slide ${i === 0 ? 'on' : 'off'}">
        <img src="${esc((v.thumb || '').replace(/hq720|hqdefault|mqdefault/, 'maxresdefault'))}" alt="">
        <div class="hero-shade"></div>
        <div class="hero-info">
          ${v.live ? '<span class="live-badge"><i></i>EN VIVO</span>' : '<span class="kicker">DESTACADO</span>'}
          <h1>${esc(v.title)}</h1>
          <p>${dot(v.channel, v.views, v.published, v.live ? '' : v.duration)}</p>
          <div class="hero-btns">
            <button class="f btn-red play">${icon('play', 34)} Reproducir</button>
            <button class="f btn-glass save">${icon(isLater(v.id) ? 'check' : 'plus', 30)} <span>${isLater(v.id) ? 'En Mi lista' : 'Mi lista'}</span></button>
          </div>
        </div>
      </div>`).join('')}
      <div class="hero-dots">${items.map((_, i) => `<i class="${i === 0 ? 'on' : ''}"></i>`).join('')}</div>
    </section>`);
  const slides = [].slice.call(h.querySelectorAll('.hero-slide'));
  const dots = [].slice.call(h.querySelectorAll('.hero-dots i'));
  slides.forEach((sl, i) => {
    const v = items[i];
    sl.querySelector('.play').onclick = () => openWatch(v, list);
    sl.querySelector('.save').onclick = (e) => {
      const on = toggleLater(v);
      e.currentTarget.innerHTML = `${icon(on ? 'check' : 'plus', 30)} <span>${on ? 'En Mi lista' : 'Mi lista'}</span>`;
      toast(on ? 'Guardado en Mi lista' : 'Quitado de Mi lista');
    };
  });
  let cur = 0;
  const timer = setInterval(() => {
    if (!document.body.contains(h)) { clearInterval(timer); return; }
    if (items.length < 2 || (focused && h.contains(focused))) return;
    cur = (cur + 1) % items.length;
    slides.forEach((sl, k) => { sl.classList.toggle('on', k === cur); sl.classList.toggle('off', k !== cur); });
    dots.forEach((d, k) => d.classList.toggle('on', k === cur));
  }, 3000);
  return h;
}

// Categorías (usan la búsqueda real) y sección Descubre
const CATS = [
  ['Películas', 'peliculas', 'movie'], ['Series', 'series', 'tv'], ['Música', 'musica', 'music'],
  ['Conciertos', 'conciertos', 'mic'], ['Deportes', 'deportes', 'sports'], ['Noticias', 'noticias', 'news'],
  ['Videojuegos', 'videojuegos', 'game'], ['Infantil', 'infantil', 'kids'], ['Tecnología', 'tecnologia', 'tech'],
  ['En vivo', 'envivo', 'live'],
];
function catChips() {
  const r = el('<section class="row cat-row"><div class="row-scroll"></div></section>');
  const box = r.querySelector('.row-scroll');
  CATS.forEach(([l, id, ic]) => { const b = el(`<button class="f chip">${icon(ic, 28)}${esc(l)}</button>`); b.onclick = () => go('cat', { id }); box.appendChild(b); });
  const more = el(`<button class="f chip">${icon('compass', 28)}Más categorías</button>`);
  more.onclick = () => go('search', {});
  box.appendChild(more);
  return r;
}
function discover() {
  const tiles = [['Películas', 'peliculas', 'movie', '#7a1020'], ['Series', 'series', 'tv', '#3b2a8a'], ['Música', 'musica', 'music', '#8a1f6e'],
    ['Deportes', 'deportes', 'sports', '#16704a'], ['Infantil', 'infantil', 'kids', '#a8611a'], ['Tecnología', 'tecnologia', 'tech', '#1a5f8a']];
  const r = el('<section class="row"><h2>Descubre<small>Explora por categoría</small></h2><div class="row-scroll"></div></section>');
  tiles.forEach(([l, q, ic, c]) => {
    const t = el(`<button class="f disc" style="background:linear-gradient(150deg,${c},#101114 85%)"><span class="disc-ic">${icon(ic, 90)}</span><b>${esc(l)}</b><small>Explorar ›</small></button>`);
    t.onclick = () => go('cat', { id: q });
    r.querySelector('.row-scroll').appendChild(t);
  });
  return r;
}
const skeleton = () => '<div class="sk-card"></div><div class="sk-card"></div><div class="sk-card"></div><div class="sk-card"></div>';

const interleave = (lists) => {
  const out = []; const seen = new Set(); const q = lists.map((l) => [...l]);
  while (q.some((l) => l.length)) q.forEach((l) => { const v = l.shift(); if (v && !seen.has(v.id)) { seen.add(v.id); out.push(v); } });
  return out;
};

// ---------- Pantallas ----------
const routes = {
  async home() {
    const topics = interests(3);
    const mine = topics.length ? topics : ['música 2026', 'cumbia'];
    const rowsDef = [
      ['Música', '#musica'],
      ['Tendencias en Perú', 'tendencias perú'],
      ['Películas completas', '#peliculas'],
      ['Series', '#series'],
      ['Deportes hoy', '#deportes'],
      ['Noticias de hoy', '#noticias'],
      ['Conciertos', '#conciertos'],
      ['Infantil', '#infantil'],
    ];
    const subsLists = await Promise.all(store.subs.slice(0, 5).map((c) => YT.channelLatest(c.id, 4)));
    const forYou = interleave(await Promise.all(mine.map((q) => YT.search(q, { uploadDate: 'month' }).then((p) => p.videos).catch(() => []))).then((l) => [...l, ...subsLists]));
    const watched = new Set(store.history.map((v) => v.id));
    const fresh = forYou.filter((v) => !watched.has(v.id));
    view.innerHTML = '';
    view.appendChild(catChips());
    if (fresh.length) view.appendChild(hero(fresh));
    // Continuar viendo: videos con posición guardada
    const cont = store.history.filter((v) => !v.short && position(v.id) && v.seconds).slice(0, 15);
    if (cont.length) view.appendChild(row('Continuar viendo', cont));
    if (fresh.length > 5) view.appendChild(row('Tendencias para ti', fresh.slice(5, 25), 'Según lo que ves y buscas'));
    view.appendChild(discover());
    const recent = interleave(subsLists).filter((v) => !v.short).slice(0, 15);
    if (recent.length) view.appendChild(row('Novedades de tus suscripciones', recent));
    for (const [title, q] of rowsDef) {
      const holder = el(`<section class="row"><h2>${esc(title)}</h2><div class="row-scroll">${skeleton()}</div></section>`);
      view.appendChild(holder);
      (q.startsWith('#') ? YT.category(q.slice(1)) : YT.search(q)).then((p) => p.videos.length ? holder.replaceWith(row(title, p.videos.slice(0, 20))) : holder.remove()).catch(() => holder.remove());
    }
  },

  async cat({ id }) {
    const c = YT.CATEGORIES[id] || { label: id };
    view.innerHTML = `<h1 class="page-title">${esc(c.label)}</h1><div class="row-scroll">${skeleton()}</div>`;
    const p = await YT.category(id);
    view.innerHTML = `<h1 class="page-title">${esc(c.label)}</h1>`;
    view.appendChild(p.videos.length ? grid(p.videos) : el('<div class="empty"><h2>No hay videos ahora</h2></div>'));
  },

  async search({ q }) {
    view.innerHTML = `
      <div class="search-top">
        <div class="search-box">${icon('search', 36)}<input class="f" type="text" placeholder="Buscar en Vidtube" value="${esc(q || '')}"></div>
      </div>
      <div class="search-body"></div>`;
    const input = view.querySelector('input');
    const body = view.querySelector('.search-body');
    input.addEventListener('keydown', (e) => {
      if (e.keyCode === KEY.ENTER && input.value.trim()) { e.stopPropagation(); input.blur(); go('search', { q: input.value.trim() }, true); }
    });
    if (q) {
      addSearch(q);
      body.innerHTML = loading();
      const p = await YT.searchAll(q);
      body.innerHTML = `<h2 class="page-sub">Resultados para “${esc(q)}”</h2>`;
      body.appendChild(grid(p.videos));
      return;
    }
    const recent = store.searches.slice(0, 8);
    if (recent.length) {
      const r = el('<section class="row"><h2>Búsquedas recientes</h2><div class="chips"></div></section>');
      recent.forEach((s) => { const b = el(`<button class="f chip">${icon('history', 26)} ${esc(s)}</button>`); b.onclick = () => go('search', { q: s }); r.querySelector('.chips').appendChild(b); });
      body.appendChild(r);
    }
    const cats = [
      ['Películas', 'películas completas en español', 'movie', '#7A1F2B', '#2A0A10'], ['Series', 'series tráiler oficial', 'tv', '#3b2a8a', '#120b33'],
      ['Conciertos', 'concierto en vivo completo', 'mic', '#8a1f6e', '#2b0a22'], ['Videojuegos', 'videojuegos gameplay', 'game', '#2346B8', '#0B1640'],
      ['Música', 'música 2026', 'music', '#8E2DC6', '#2B0B42'], ['Deportes', 'deportes resumen', 'sports', '#1F8A4C', '#0A2E19'],
      ['Noticias', 'noticias hoy', 'news', '#B5462A', '#3A130A'], ['Infantil', 'canciones infantiles', 'kids', '#E0912B', '#4A2A06'],
      ['Ciencia y tecnología', 'ciencia y tecnología', 'science', '#1B8FA6', '#06303A'], ['Viajes', 'viajes vlog', 'travel', '#2B6FD6', '#0A2450'],
      ['Comida y recetas', 'recetas fáciles', 'food', '#C7502A', '#43160A'], ['Tendencias', 'tendencias perú', 'live', '#b0101f', '#3a050b'],
    ];
    const g = el('<section class="row"><h2>Explorar categorías</h2><div class="cats"></div></section>');
    cats.forEach(([l, qq, ic, a, b]) => {
      const t = el(`<button class="f cat" style="background:linear-gradient(135deg,${a},${b})">${icon(ic, 44)}<span>${esc(l)}</span></button>`);
      const map = { 'Películas': 'peliculas', 'Series': 'series', 'Conciertos': 'conciertos', 'Deportes': 'deportes', 'Noticias': 'noticias', 'Infantil': 'infantil', 'Música': 'musica', 'Videojuegos': 'videojuegos' };
      t.onclick = () => (map[l] ? go('cat', { id: map[l] }) : go('search', { q: qq }));
      g.querySelector('.cats').appendChild(t);
    });
    body.appendChild(g);
  },

  async subs() {
    const subs = store.subs;
    view.innerHTML = '<h1 class="page-title">Suscripciones</h1>';
    if (!subs.length) { view.appendChild(el('<div class="empty"><h2>Aún no tienes suscripciones</h2><p>Abre un video y toca Suscribirse.</p></div>')); return; }
    const r = el('<section class="row"><div class="row-scroll ch-row"></div></section>');
    subs.forEach((c) => {
      const b = el(`<button class="f ch"><div class="avatar">${c.avatar ? `<img src="${esc(c.avatar)}">` : ''}</div><span>${esc(c.name)}</span></button>`);
      b.onclick = () => go('channel', { id: c.id });
      r.querySelector('.row-scroll').appendChild(b);
    });
    view.appendChild(r);
    const lists = await Promise.all(subs.slice(0, 15).map((c) => YT.channelLatest(c.id, 6)));
    view.appendChild(el('<h2 class="page-sub">Lo más nuevo</h2>'));
    view.appendChild(grid(interleave(lists)));
  },

  async library() {
    view.innerHTML = '<h1 class="page-title">Biblioteca</h1>';
    const parts = [['Historial', store.history], ['Ver más tarde', store.later], ['Videos que me gustan', store.liked]];
    let any = false;
    parts.forEach(([t, l]) => { if (l.length) { any = true; view.appendChild(row(t, l.slice(0, 30))); } });
    if (!any) view.appendChild(el('<div class="empty"><h2>Tu biblioteca está vacía</h2><p>Los videos que veas y guardes aparecerán aquí.</p></div>'));
  },

  async channel({ id }) {
    const c = await YT.channel(id);
    view.innerHTML = '';
    const head = el(`<section class="ch-head">${c.banner ? `<img class="banner" src="${esc(c.banner)}">` : ''}
      <div class="ch-info"><div class="ring"><div class="avatar big">${c.avatar ? `<img src="${esc(c.avatar)}">` : ''}</div></div>
      <div><h1>${esc(c.name)}</h1><p>${esc(c.subs)}</p></div><button class="f btn-red sub"></button></div></section>`);
    const sb = head.querySelector('.sub');
    const paint = () => { const on = isSub(id); sb.textContent = on ? 'Suscrito' : 'Suscribirse'; sb.classList.toggle('subbed', on); };
    paint();
    sb.onclick = () => { const on = toggleSub({ id, name: c.name, avatar: c.avatar }); paint(); toast(on ? 'Suscrito a ' + c.name : 'Suscripción cancelada'); };
    view.appendChild(head);
    view.appendChild(grid(c.videos.map((x) => ({ ...x, channel: x.channel || c.name, channelId: id }))));
  },

  async settings() {
    const s = store.settings;
    const qOpts = ['auto', '2160', '1080', '720', '480'];
    view.innerHTML = `<h1 class="page-title">Configuración</h1>
      <div class="set">
        <button class="f set-row q"><b>Calidad</b><span>${s.quality === 'auto' ? 'Automática' : s.quality + 'p'}</span></button>
        <button class="f set-row a"><b>Reproducción automática</b><span>${s.autoplay ? 'Activada' : 'Desactivada'}</span></button>
        <button class="f set-row h"><b>Guardar historial</b><span>${s.saveHistory ? 'Activado' : 'Desactivado'}</span></button>
        <button class="f set-row d"><b>Diagnóstico</b><span>Registro para revisar problemas</span></button>
        <div class="set-row info"><b>Vidtube para TV</b><span>Versión ${esc(VERSION)}</span></div>
        <pre class="dbg hidden"></pre>
      </div>`;
    view.querySelector('.q').onclick = () => { const i = qOpts.indexOf(String(store.settings.quality)); setSettings({ quality: qOpts[(i + 1) % qOpts.length] }); go('settings', {}, false); };
    view.querySelector('.a').onclick = () => { setSettings({ autoplay: !store.settings.autoplay }); go('settings', {}, false); };
    view.querySelector('.h').onclick = () => { setSettings({ saveHistory: !store.settings.saveHistory }); go('settings', {}, false); };
    view.querySelector('.d').onclick = () => { const d = view.querySelector('.dbg'); d.textContent = YT.debug.join('\n') || 'Sin registros todavía.'; d.classList.toggle('hidden'); };
  },
};

// ---------- Reproductor a pantalla completa ----------
shaka.polyfill.installAll();
let wp = null; // estado del video abierto

function fmt(s) {
  s = Math.max(0, Math.floor(s || 0));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}` : `${m}:${String(x).padStart(2, '0')}`;
}
async function cleanVtt(url) {
  const raw = await (await fetch(url)).text();
  const out = ['WEBVTT', '']; let last = '';
  raw.replace(/\r/g, '').split(/\n\n+/).forEach((b) => {
    const lines = b.split('\n'); const t = lines.findIndex((l) => l.indexOf('-->') >= 0);
    if (t < 0) return;
    const text = lines.slice(t + 1).map((l) => l.replace(/<[^>]+>/g, '').trim()).filter(Boolean);
    const fresh = text.filter((l) => l !== last);
    if (!fresh.length) return;
    last = text[text.length - 1];
    out.push(lines[t].replace(/(-->\s*\S+).*$/, '$1'), ...fresh.slice(-2), '');
  });
  return URL.createObjectURL(new Blob([out.join('\n')], { type: 'text/vtt' }));
}

async function openWatch(v, list) {
  if (wp) await closeWatch(true);
  watchBox.classList.remove('hidden');
  view.classList.add('hidden');
  app.querySelector('.rail').classList.add('hidden');
  watchBox.innerHTML = `
    <video class="tv-video"></video>
    <div class="w-spinner"><div class="spinner"></div></div>
    <div class="w-flash"></div>
    <div class="w-error hidden"></div>
    <div class="w-ui show">
      <div class="w-top"><img src="assets/wordmark.png" class="w-logo"><div class="w-grow"></div><span class="w-badge"></span></div>
      <div class="w-title"><h1>${esc(v.title)}</h1><p class="w-sub">${esc(v.channel || '')}</p></div>
      <div class="w-center">
        <button class="f glass side back10">${icon('replay10', 44)}</button>
        <button class="f glass main play">${icon('pause', 60)}</button>
        <button class="f glass side fwd10">${icon('forward10', 44)}</button>
      </div>
      <div class="w-bottom">
        <div class="w-time">0:00 / 0:00</div>
        <div class="w-track"><div class="w-buf"></div><div class="w-prog"></div><div class="w-thumb"></div></div>
        <div class="w-actions">
          <button class="f pill like">${icon('thumbUp', 30)}<span>Me gusta</span></button>
          <button class="f pill save">${icon('playlistAdd', 30)}<span>Guardar</span></button>
          <button class="f pill cc">${icon('cc', 30)}<span>Subtítulos</span></button>
          <button class="f pill sub hidden">${icon('subs', 30)}<span>Suscribirse</span></button>
          <div class="w-grow"></div>
          <button class="f next-card hidden"><img><div><small>Siguiente:</small><b></b></div>${icon('play', 40)}</button>
        </div>
        <div class="w-related hidden"><h2>Más videos</h2><div class="row-scroll"></div></div>
      </div>
    </div>`;
  const $ = (s) => watchBox.querySelector(s);
  const video = $('video');
  const ui = $('.w-ui');
  const player = new shaka.Player(video);
  let rn = 0;
  player.getNetworkingEngine().registerRequestFilter((type, req) => {
    const u = req.uris && req.uris[0] || '';
    if (u.indexOf('googlevideo.com') < 0) return;
    const url = new URL(u);
    const range = req.headers.Range || req.headers.range;
    if (range) { url.searchParams.set('range', range.replace(/^bytes=/, '')); delete req.headers.Range; delete req.headers.range; }
    url.searchParams.set('rn', String(++rn));
    url.searchParams.set('alr', 'yes');
    req.uris = [url.toString()];
    req.method = 'POST';
    req.body = new Uint8Array([0x78, 0]).buffer;
  });
  player.configure({ streaming: { bufferingGoal: 30, bufferBehind: 20, rebufferingGoal: 2 }, abr: { enabled: true } });
  wp = { v, list, player, video, src: null, attempt: 0, startedAt: null, retried: 0, hideTimer: 0, subsOn: false, next: null, details: null };

  const show = () => {
    ui.classList.add('show');
    clearTimeout(wp.hideTimer);
    wp.hideTimer = setTimeout(() => { if (!video.paused && wp) ui.classList.remove('show'); }, 4000);
  };
  wp.show = show;
  const flash = (t) => { const f = $('.w-flash'); f.textContent = t; f.classList.remove('on'); void f.offsetWidth; f.classList.add('on'); };
  wp.flash = flash;
  wp.seek = (s) => { video.currentTime = Math.max(0, Math.min((video.duration || 0) - 1, video.currentTime + s)); flash(s < 0 ? '−10 s' : '+10 s'); show(); };
  wp.toggle = () => { if (video.paused) video.play(); else video.pause(); show(); };
  const setPlayIcon = () => { $('.play').innerHTML = icon(video.paused ? 'play' : 'pause', 60); };
  video.addEventListener('play', setPlayIcon);
  video.addEventListener('pause', () => { setPlayIcon(); ui.classList.add('show'); });
  video.addEventListener('waiting', () => watchBox.classList.add('loading'));
  video.addEventListener('playing', () => watchBox.classList.remove('loading'));
  video.addEventListener('timeupdate', () => {
    const d = video.duration || 0;
    $('.w-time').textContent = `${fmt(video.currentTime)} / ${fmt(d)}`;
    if (d) { const f = video.currentTime / d; $('.w-prog').style.width = f * 100 + '%'; $('.w-thumb').style.left = f * 100 + '%'; }
    if (video.buffered.length && d) $('.w-buf').style.width = (video.buffered.end(video.buffered.length - 1) / d) * 100 + '%';
    if (d && Math.floor(video.currentTime) % 5 === 0) savePosition(v.id, video.currentTime, d);
    if (wp && wp.startedAt == null && video.currentTime > 0) wp.startedAt = video.currentTime;
  });
  video.addEventListener('ended', () => { if (store.settings.autoplay && wp && wp.next) openWatch(wp.next, wp.list); });
  $('.back10').onclick = () => wp.seek(-10);
  $('.fwd10').onclick = () => wp.seek(10);
  $('.play').onclick = () => wp.toggle();
  const paintLike = () => $('.like').classList.toggle('on', isLiked(v.id));
  const paintSave = () => { const on = isLater(v.id); $('.save').classList.toggle('on', on); $('.save span').textContent = on ? 'Guardado' : 'Guardar'; };
  paintLike(); paintSave();
  $('.like').onclick = () => { const on = toggleLike(v); paintLike(); toast(on ? 'Agregado a Videos que me gustan' : 'Quitado'); };
  $('.save').onclick = () => { const on = toggleLater(v); paintSave(); toast(on ? 'Guardado en Ver más tarde' : 'Quitado de Ver más tarde'); };
  $('.cc').onclick = async () => {
    const caps = wp.src && wp.src.captions || [];
    if (!caps.length) { flash('Este video no tiene subtítulos'); return; }
    wp.subsOn = !wp.subsOn;
    if (wp.subsOn && !player.getTextTracks().length) {
      const pick = caps.slice().sort((a, b) => (a.lang.indexOf('es') === 0 ? 0 : 1) - (b.lang.indexOf('es') === 0 ? 0 : 1) || (a.auto ? 1 : 0) - (b.auto ? 1 : 0))[0];
      try { await player.addTextTrackAsync(await cleanVtt(pick.url), pick.lang, 'subtitles', 'text/vtt'); } catch (e) { wp.subsOn = false; flash('No se pudieron cargar'); return; }
    }
    const t = player.getTextTracks()[0]; if (t) player.selectTextTrack(t);
    player.setTextTrackVisibility(wp.subsOn);
    $('.cc').classList.toggle('on', wp.subsOn);
    flash(wp.subsOn ? 'Subtítulos activados' : 'Subtítulos desactivados');
  };

  async function load(attempt) {
    if (!wp || wp.v !== v) return;
    $('.w-error').classList.add('hidden');
    watchBox.classList.add('loading');
    const resume = video.currentTime || 0;
    try {
      const src = await YT.playable(v.id, v.seconds, attempt);
      if (!wp || wp.v !== v) return;
      wp.src = src;
      await player.unload();
      const q = store.settings.quality;
      await player.load(src.src, src.live ? undefined : (resume || position(v.id) || undefined));
      if (q !== 'auto') {
        const t = player.getVariantTracks().filter((x) => x.height && x.height <= +q).sort((a, b) => b.height - a.height)[0];
        if (t) { player.configure({ abr: { enabled: false } }); player.selectVariantTrack(t, true); }
      }
      const act = player.getVariantTracks().find((x) => x.active);
      $('.w-badge').textContent = act && act.height >= 2160 ? '4K' : act && act.height >= 720 ? 'HD' : '';
      video.play().catch(() => {});
      show();
    } catch (e) {
      if (!wp || wp.v !== v) return;
      const pos = wp.src && wp.src.pos != null ? wp.src.pos : attempt;
      if (e && e.code && pos + 1 < 5 && attempt < 4) { YT.debug.push(`TV shaka ${e.code} con ${wp.src && wp.src.client}`); wp.src = null; return load(pos + 1); }
      watchBox.classList.remove('loading');
      const err = $('.w-error');
      err.innerHTML = `<div><h2>No se pudo reproducir</h2><p>${esc(e && e.code ? 'Error del reproductor (' + e.code + ')' : (e && e.message || e))}</p><button class="f btn-red">Reintentar</button></div>`;
      err.classList.remove('hidden');
      err.querySelector('button').onclick = () => load(0);
      setFocus(err.querySelector('button'));
    }
  }
  player.addEventListener('error', (ev) => {
    if (!wp || !wp.src) return;
    const err = ev.detail || {};
    YT.debug.push(`TV error ${err.code} con ${wp.src.client} (HTTP ${err.data && err.data[1]})`);
    const ok = wp.startedAt != null && video.currentTime - wp.startedAt > 20;
    const pos = wp.src.pos || 0;
    wp.src = null;
    if (ok && wp.retried < 3) { wp.retried++; load(pos); } else if (pos + 1 < 5) load(pos + 1);
  });

  addHistory(v);
  setFocus($('.play'));
  load(0);

  // Detalles: canal, siguiente y relacionados
  try {
    const d = await YT.details(v.id);
    if (!wp || wp.v !== v) return;
    wp.details = d;
    const full = { ...v, title: d.title || v.title, channel: d.channel || v.channel, channelId: d.channelId || v.channelId, avatar: d.avatar || v.avatar, seconds: d.seconds || v.seconds };
    wp.v = full; Object.assign(v, full);
    addHistory(full);
    const mins = full.seconds ? (full.seconds >= 3600 ? `${Math.floor(full.seconds / 3600)}h ${Math.floor((full.seconds % 3600) / 60)}min` : `${Math.max(1, Math.round(full.seconds / 60))} min`) : '';
    $('.w-sub').textContent = [full.channel, mins, d.year].filter(Boolean).join(' • ');
    const watched = new Set(store.history.slice(0, 50).map((x) => x.id));
    wp.next = d.related.find((r) => !watched.has(r.id)) || d.related[0] || null;
    if (wp.next) {
      const n = $('.next-card'); n.classList.remove('hidden');
      n.querySelector('img').src = wp.next.thumb; n.querySelector('b').textContent = wp.next.title;
      n.onclick = () => openWatch(wp.next, d.related);
    }
    if (full.channelId) {
      const sb = $('.sub'); sb.classList.remove('hidden');
      const paint = () => { const on = isSub(full.channelId); sb.querySelector('span').textContent = on ? 'Suscrito' : 'Suscribirse'; sb.classList.toggle('on', on); };
      paint();
      sb.onclick = () => { const on = toggleSub({ id: full.channelId, name: full.channel, avatar: full.avatar }); paint(); toast(on ? 'Suscrito a ' + full.channel : 'Suscripción cancelada'); };
    }
    if (d.related.length) {
      const rel = $('.w-related'); rel.classList.remove('hidden');
      d.related.slice(0, 20).forEach((r) => rel.querySelector('.row-scroll').appendChild(card(r, d.related)));
    }
  } catch (e) {}
}

async function closeWatch(keep) {
  if (!wp) return;
  const w = wp; wp = null;
  clearTimeout(w.hideTimer);
  try { if (w.video.duration) savePosition(w.v.id, w.video.currentTime, w.video.duration); } catch (e) {}
  try { await w.player.destroy(); } catch (e) {}
  watchBox.innerHTML = '';
  if (keep) return;
  watchBox.classList.add('hidden');
  view.classList.remove('hidden');
  app.querySelector('.rail').classList.remove('hidden');
  setFocus(view.querySelector('.f') || app.querySelector('.nav.on'));
}

// ---------- Manejo de teclas ----------
document.addEventListener('keydown', (e) => {
  const k = e.keyCode;
  const inWatch = !!wp && !watchBox.classList.contains('hidden');
  if (k === KEY.BACK || k === KEY.ESC || (k === KEY.BACKSPACE && !(document.activeElement && document.activeElement.tagName === 'INPUT'))) {
    e.preventDefault();
    if (inWatch && wp.video && wp.ui !== null) { const ui = watchBox.querySelector('.w-ui'); if (ui && ui.classList.contains('show') && !wp.video.paused) { ui.classList.remove('show'); return; } }
    back();
    return;
  }
  if (inWatch) {
    const ui = watchBox.querySelector('.w-ui');
    if (k === KEY.PLAYPAUSE) { wp.toggle(); return; }
    if (k === KEY.PLAY) { wp.video.play(); return; }
    if (k === KEY.PAUSE) { wp.video.pause(); return; }
    if (k === KEY.REW) { wp.seek(-10); return; }
    if (k === KEY.FF) { wp.seek(10); return; }
    if (k === KEY.STOP) { back(); return; }
    // Con los controles ocultos: ← → adelantan/retroceden, cualquier otra tecla los muestra
    if (!ui.classList.contains('show') && watchBox.querySelector('.w-error').classList.contains('hidden')) {
      e.preventDefault();
      if (k === KEY.LEFT) wp.seek(-10);
      else if (k === KEY.RIGHT) wp.seek(10);
      else { wp.show(); setFocus(watchBox.querySelector('.play')); }
      return;
    }
    wp.show();
  }
  if (k === KEY.LEFT) { e.preventDefault(); if (!(document.activeElement && document.activeElement.tagName === 'INPUT')) move('left'); }
  else if (k === KEY.RIGHT) { e.preventDefault(); if (!(document.activeElement && document.activeElement.tagName === 'INPUT')) move('right'); }
  else if (k === KEY.UP) { e.preventDefault(); move('up'); }
  else if (k === KEY.DOWN) { e.preventDefault(); move('down'); }
  else if (k === KEY.ENTER) {
    if (focused && focused.tagName === 'INPUT') return;
    e.preventDefault();
    if (focused) focused.click();
  }
});

// ---------- Arranque ----------
(async () => {
  document.documentElement.dataset.theme = 'dark';
  try { await YT.init(); } catch (e) { YT.debug.push('init: ' + (e && e.message)); }
  setTimeout(() => document.getElementById('splash').classList.add('hide'), 400);
  setTimeout(() => { const s = document.getElementById('splash'); if (s) s.remove(); }, 1200);
  go('home', {}, false);
})();
