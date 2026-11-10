// Reproductor con controles de cristal, ±10 s, barra neón, subtítulos, calidad y modo cine.
import shaka from 'shaka-player/dist/shaka-player.compiled.js';
import { icon } from './icons.js';
import * as YT from './yt.js';
import { store, setSettings, savePosition, position } from './store.js';

shaka.polyfill.installAll();

const fmt = (s) => {
  s = Math.max(0, Math.floor(s || 0));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60;
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}` : `${m}:${String(x).padStart(2, '0')}`;
};
const badge = (h) => (h >= 2160 ? '4K' : h >= 1440 ? '2K' : h >= 720 ? 'HD' : '');

/**
 * Crea un reproductor dentro de `host`.
 * opts: { onEnded, onNext, getNext, onClose, cinema: {title, subtitle, actionsHtml, bindActions} , vertical }
 */
export function createPlayer(host, opts = {}) {
  host.innerHTML = `
    <div class="vp ${opts.vertical ? 'vp-vertical' : ''}" tabindex="0">
      <video class="vp-video" playsinline></video>
      <div class="vp-flash"></div>
      <div class="vp-spinner"><div class="spinner"></div></div>
      <div class="vp-error"></div>
      <div class="vp-ui">
        <div class="vp-top">
          <div class="vp-cine-left">
            <button class="vp-ic vp-exit" title="Salir de pantalla completa">${icon('down', 30)}</button>
            <img src="assets/wordmark.png" class="vp-logo" alt="Vidtube">
          </div>
          <div class="vp-grow"></div>
          <button class="vp-ic vp-cc" title="Subtítulos (C)">${icon('cc', 26)}</button>
          <div class="vp-menu-wrap">
            <button class="vp-ic vp-gear" title="Calidad">${icon('settings', 26)}</button>
            <div class="vp-menu"></div>
          </div>
          <button class="vp-ic vp-fs-top" title="Pantalla completa (F)">${icon('fullscreenExit', 28)}</button>
        </div>
        <div class="vp-cine-title">
          <button class="vp-title"><span></span>${icon('chevron', 34)}</button>
          <div class="vp-sub"></div>
        </div>
        <div class="vp-center">
          <button class="glass g-side vp-back10" title="Retroceder 10 s (←)">${icon('replay10', 30)}</button>
          <button class="glass g-main vp-play" title="Reproducir / pausa (espacio)">${icon('play', 44)}</button>
          <button class="glass g-side vp-fwd10" title="Adelantar 10 s (→)">${icon('forward10', 30)}</button>
        </div>
        <div class="vp-bottom">
          <div class="vp-row">
            <span class="vp-time">0:00 / 0:00</span>
            <button class="vp-ic vp-vol" title="Silenciar (M)">${icon('volume', 22)}</button>
            <input class="vp-volume" type="range" min="0" max="1" step="0.05">
            <div class="vp-grow"></div>
            <span class="vp-badge"></span>
            <button class="vp-ic vp-fs" title="Pantalla completa (F)">${icon('fullscreen', 26)}</button>
          </div>
          <div class="vp-seek"><div class="vp-track"><div class="vp-buf"></div><div class="vp-prog"></div><div class="vp-thumb"></div></div><div class="vp-tip"></div></div>
          <div class="vp-cine-actions"></div>
        </div>
      </div>
    </div>`;
  const root = host.querySelector('.vp');
  const video = root.querySelector('video');
  const $ = (s) => root.querySelector(s);
  const ui = $('.vp-ui');
  const player = new shaka.Player();
  player.attach(video);
  player.configure({
    streaming: { bufferingGoal: 45, rebufferingGoal: 1.5, retryParameters: { maxAttempts: 3 } },
    abr: { enabled: true },
    preferredTextLanguage: 'es',
  });

  let current = null; // { id, details }
  let src = null;
  let hideTimer = 0;
  let subsOn = false;
  let quality = store.settings.quality;
  let destroyed = false;
  let busy = false;

  video.volume = store.settings.volume ?? 1;
  $('.vp-volume').value = video.volume;

  // ---------- Mostrar / ocultar controles ----------
  const show = () => {
    root.classList.add('show');
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => { if (!video.paused && !root.querySelector('.vp-menu.open')) root.classList.remove('show'); }, 3200);
  };
  root.addEventListener('mousemove', show);
  root.addEventListener('mouseleave', () => { if (!video.paused) root.classList.remove('show'); });

  let clickTimer = 0;
  ui.addEventListener('click', (e) => {
    if (e.target !== ui && !e.target.classList.contains('vp-center') && !e.target.classList.contains('vp-cine-title')) return;
    // Un clic = pausa/sigue; doble clic = pantalla completa
    clearTimeout(clickTimer);
    clickTimer = setTimeout(() => togglePlay(), 220);
  });
  ui.addEventListener('dblclick', (e) => {
    if (e.target !== ui && !e.target.classList.contains('vp-center')) return;
    clearTimeout(clickTimer);
    const r = root.getBoundingClientRect();
    const x = e.clientX - r.left;
    if (x < r.width * 0.3) seekBy(-10);
    else if (x > r.width * 0.7) seekBy(10);
    else toggleFs();
  });

  const flash = (t) => {
    const f = $('.vp-flash');
    f.textContent = t;
    f.classList.remove('on'); void f.offsetWidth; f.classList.add('on');
  };
  const togglePlay = () => { if (video.paused) video.play().catch(() => {}); else video.pause(); show(); };
  const seekBy = (s) => {
    video.currentTime = Math.max(0, Math.min((video.duration || 0) - 0.5, video.currentTime + s));
    flash(s < 0 ? '−10 s' : '+10 s');
    show();
  };
  const isFs = () => document.fullscreenElement === root;
  const toggleFs = () => {
    if (isFs()) document.exitFullscreen(); else root.requestFullscreen().catch(() => {});
  };
  document.addEventListener('fullscreenchange', () => {
    root.classList.toggle('cinema', isFs());
    $('.vp-fs').innerHTML = icon(isFs() ? 'fullscreenExit' : 'fullscreen', 26);
    show();
  });

  $('.vp-play').onclick = togglePlay;
  $('.vp-back10').onclick = () => seekBy(-10);
  $('.vp-fwd10').onclick = () => seekBy(10);
  $('.vp-fs').onclick = toggleFs;
  $('.vp-fs-top').onclick = toggleFs;
  $('.vp-exit').onclick = () => { if (isFs()) document.exitFullscreen(); else opts.onClose?.(); };
  $('.vp-title').onclick = () => { if (isFs()) document.exitFullscreen(); opts.onTitle?.(); };

  const volIcon = () => { $('.vp-vol').innerHTML = icon(video.muted || video.volume === 0 ? 'mute' : 'volume', 22); };
  $('.vp-vol').onclick = () => { video.muted = !video.muted; volIcon(); };
  $('.vp-volume').oninput = (e) => { video.volume = +e.target.value; video.muted = false; volIcon(); setSettings({ volume: video.volume }); };

  // ---------- Barra de progreso ----------
  const seek = $('.vp-seek');
  let dragging = false;
  const fracAt = (e) => {
    const r = seek.querySelector('.vp-track').getBoundingClientRect();
    return Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
  };
  const paint = (f) => {
    $('.vp-prog').style.width = f * 100 + '%';
    $('.vp-thumb').style.left = f * 100 + '%';
  };
  seek.addEventListener('pointerdown', (e) => { dragging = true; seek.setPointerCapture(e.pointerId); paint(fracAt(e)); seek.classList.add('drag'); });
  seek.addEventListener('pointermove', (e) => {
    const f = fracAt(e);
    const tip = $('.vp-tip');
    tip.textContent = fmt(f * (video.duration || 0));
    tip.style.left = f * 100 + '%';
    if (dragging) paint(f);
  });
  seek.addEventListener('pointerup', (e) => {
    if (!dragging) return;
    dragging = false; seek.classList.remove('drag');
    if (video.duration) video.currentTime = fracAt(e) * video.duration;
    show();
  });

  video.addEventListener('timeupdate', () => {
    const d = video.duration || 0;
    $('.vp-time').textContent = `${fmt(video.currentTime)} / ${fmt(d)}`;
    if (!dragging && d) paint(video.currentTime / d);
    if (video.buffered.length && d) $('.vp-buf').style.width = (video.buffered.end(video.buffered.length - 1) / d) * 100 + '%';
    if (current && d && !src?.live && Math.floor(video.currentTime) % 5 === 0) savePosition(current.id, video.currentTime, d);
  });
  const setPlayIcon = () => { $('.vp-play').innerHTML = icon(video.paused ? 'play' : 'pause', 44); };
  video.addEventListener('play', () => { setPlayIcon(); show(); });
  video.addEventListener('pause', () => { setPlayIcon(); root.classList.add('show'); });
  video.addEventListener('waiting', () => root.classList.add('loading'));
  video.addEventListener('playing', () => root.classList.remove('loading'));
  video.addEventListener('canplay', () => root.classList.remove('loading'));
  video.addEventListener('ended', () => { if (opts.loop) { video.currentTime = 0; video.play(); } else opts.onEnded?.(); });

  // ---------- Teclado ----------
  const onKey = (e) => {
    if (destroyed || !document.body.contains(root)) return;
    const tag = (document.activeElement?.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea') return;
    const k = e.key.toLowerCase();
    if (k === ' ' || k === 'k') { e.preventDefault(); togglePlay(); }
    else if (k === 'arrowleft' || k === 'j') { e.preventDefault(); seekBy(-10); }
    else if (k === 'arrowright' || k === 'l') { e.preventDefault(); seekBy(10); }
    else if (k === 'f') toggleFs();
    else if (k === 'm') { video.muted = !video.muted; volIcon(); }
    else if (k === 'c') toggleSubs();
    else if (k === 'arrowup' && !opts.vertical) { e.preventDefault(); video.volume = Math.min(1, video.volume + 0.05); $('.vp-volume').value = video.volume; }
    else if (k === 'arrowdown' && !opts.vertical) { e.preventDefault(); video.volume = Math.max(0, video.volume - 0.05); $('.vp-volume').value = video.volume; }
  };
  document.addEventListener('keydown', onKey);

  // ---------- Subtítulos ----------
  const toggleSubs = async () => {
    if (!src?.captions?.length) { flash('Sin subtítulos'); return; }
    subsOn = !subsOn;
    if (subsOn && !player.getTextTracks().length) {
      const lang = navigator.language.slice(0, 2);
      const pick = [...src.captions].sort((a, b) => (a.lang.startsWith(lang) ? 0 : 1) - (b.lang.startsWith(lang) ? 0 : 1) || (a.auto ? 1 : 0) - (b.auto ? 1 : 0))[0];
      try { await player.addTextTrackAsync(pick.url, pick.lang, 'subtitles', 'text/vtt', undefined, pick.name); } catch (e) { flash('No se pudieron cargar'); subsOn = false; }
    }
    const t = player.getTextTracks()[0];
    if (t) player.selectTextTrack(t);
    player.setTextTrackVisibility(subsOn);
    $('.vp-cc').classList.toggle('on', subsOn);
    flash(subsOn ? 'Subtítulos activados' : 'Subtítulos desactivados');
  };
  $('.vp-cc').onclick = toggleSubs;

  // ---------- Calidad ----------
  const menu = $('.vp-menu');
  const heights = () => [...new Set(player.getVariantTracks().map((t) => t.height).filter(Boolean))].sort((a, b) => b - a);
  const applyQuality = () => {
    if (quality === 'auto') { player.configure({ abr: { enabled: true } }); return; }
    const tracks = player.getVariantTracks().filter((t) => t.height && t.height <= +quality).sort((a, b) => b.height - a.height || b.bandwidth - a.bandwidth);
    const t = tracks[0] || player.getVariantTracks().sort((a, b) => a.height - b.height)[0];
    if (t) { player.configure({ abr: { enabled: false } }); player.selectVariantTrack(t, true); }
  };
  const updateBadge = () => {
    const act = player.getVariantTracks().find((t) => t.active);
    $('.vp-badge').textContent = badge(act?.height || 0);
    $('.vp-badge').style.display = badge(act?.height || 0) ? '' : 'none';
  };
  player.addEventListener('adaptation', updateBadge);
  player.addEventListener('variantchanged', updateBadge);
  $('.vp-gear').onclick = (e) => {
    e.stopPropagation();
    const act = player.getVariantTracks().find((t) => t.active);
    menu.innerHTML = `<div class="vp-menu-title">Calidad</div>` +
      [['auto', `Automática${act?.height ? ` (${act.height}p)` : ''}`], ...heights().map((h) => [String(h), `${h}p${badge(h) ? ' ' + badge(h) : ''}`])]
        .map(([v, l]) => `<button data-q="${v}" class="${String(quality) === v ? 'sel' : ''}">${l}</button>`).join('') +
      `<div class="vp-menu-title">Velocidad</div>` +
      [0.5, 0.75, 1, 1.25, 1.5, 2].map((r) => `<button data-r="${r}" class="${video.playbackRate === r ? 'sel' : ''}">${r === 1 ? 'Normal' : r + 'x'}</button>`).join('');
    menu.classList.toggle('open');
  };
  menu.addEventListener('click', (e) => {
    const b = e.target.closest('button');
    if (!b) return;
    if (b.dataset.q) { quality = b.dataset.q; setSettings({ quality }); applyQuality(); }
    if (b.dataset.r) video.playbackRate = +b.dataset.r;
    menu.classList.remove('open');
  });
  document.addEventListener('click', () => menu.classList.remove('open'));

  // ---------- Cargar un video ----------
  async function load(id, info = {}, attempt = 0) {
    busy = true;
    try { return await loadInner(id, info, attempt); } finally { busy = false; }
  }
  async function loadInner(id, info, attempt) {
    current = { id, info };
    $('.vp-error').classList.remove('on');
    root.classList.add('loading');
    subsOn = false; $('.vp-cc').classList.remove('on');
    try {
      src = await YT.playable(id, info.seconds, attempt);
      if (destroyed || current?.id !== id) return;
      await player.unload();
      const start = attempt === 0 && !src.live ? position(id) : (video.currentTime || 0);
      await player.load(src.src, start || undefined, src.progressive ? 'video/mp4' : undefined);
      applyQuality();
      updateBadge();
      video.play().catch(() => {});
    } catch (e) {
      if (destroyed || current?.id !== id) return;
      const next = (src?.index ?? -1) + 1;
      // Si falló el video con este cliente, probamos el siguiente
      if (e?.code && src && next < 5 && attempt < 4) {
        YT.debug.push(`shaka ${e.code} con ${src.client}; probando otro`);
        return loadInner(id, info, next);
      }
      root.classList.remove('loading');
      const el = $('.vp-error');
      const why = e?.code === 1001 || e?.code === 1002
        ? `YouTube no entregó el video (código ${e.code}). Prueba Reintentar; si sigue, revisa Configuración › Diagnóstico.`
        : e?.code ? `Error del reproductor (código ${e.code}).` : (e?.message || 'Error desconocido');
      el.innerHTML = `<div><b>No se pudo reproducir</b><pre>${why.replace(/</g, '&lt;')}</pre><button class="btn-red">Reintentar</button></div>`;
      el.classList.add('on');
      el.querySelector('button').onclick = () => load(id, info, 0);
    }
  }
  player.addEventListener('error', (ev) => {
    const err = ev.detail;
    if (!current || busy) return;
    const next = (src?.index ?? -1) + 1;
    if (next < 5) { YT.debug.push(`shaka error ${err?.code} con ${src?.client}`); load(current.id, current.info, next); }
  });

  function setCinema({ title, subtitle, actions }) {
    $('.vp-title span').textContent = title || '';
    $('.vp-sub').textContent = subtitle || '';
    const box = $('.vp-cine-actions');
    box.innerHTML = '';
    if (actions) box.appendChild(actions);
  }

  root.classList.add('show');
  return {
    root,
    video,
    load,
    setCinema,
    get id() { return current?.id; },
    async destroy() {
      destroyed = true;
      document.removeEventListener('keydown', onKey);
      if (current && video.duration) savePosition(current.id, video.currentTime, video.duration);
      if (isFs()) document.exitFullscreen().catch(() => {});
      try { await player.destroy(); } catch {}
    },
  };
}
