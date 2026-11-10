// Motor de YouTube: búsqueda, canales, info de videos y fuentes de reproducción.
import { Innertube, Platform, UniversalCache } from 'youtubei.js/web';
import { getChallenge, BotGuardClient } from 'bgutils-js/botguard';
import { WebPoMinter } from 'bgutils-js/webpo';

// Descifrado de firmas: YouTube.js nos pasa el código del reproductor y lo ejecutamos aquí
Platform.shim.eval = async (data, env) => {
  const props = [];
  if (env.n) props.push(`n: exportedVars.nFunction("${env.n}")`);
  if (env.sig) props.push(`sig: exportedVars.sigFunction("${env.sig}")`);
  const code = `${data.output}\nreturn { ${props.join(', ')} }`;
  return new Function(code)();
};

let yt = null;
let ready = null;
export const debug = [];
function log(...a) {
  const line = a.map((x) => (typeof x === 'string' ? x : JSON.stringify(x))).join(' ');
  debug.push(new Date().toLocaleTimeString() + ' ' + line);
  if (debug.length > 200) debug.shift();
  console.log('[vidtube]', ...a);
}

export function init() {
  if (!ready) {
    ready = (async () => {
      yt = await Innertube.create({
        lang: 'es',
        location: 'PE',
        retrieve_player: true,
        cache: new UniversalCache(false),
        fetch: (input, initArg) => fetch(input, initArg),
      });
      log('sesión lista');
      return yt;
    })().catch((e) => { ready = null; throw e; });
  }
  return ready;
}

// ---------- Token de "no soy un bot" (BotGuard), igual que en la app de Android ----------
const potCache = new Map();
const REQUEST_KEY = 'O43z0dpjhgX20SCx4KAo';
const GOOG_KEY = 'AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw';
let minterPromise = null;
async function getMinter() {
  const challenge = await getChallenge({ requestKey: REQUEST_KEY, fetchFunction: (u, o) => fetch(u, o), useYouTubeAPI: true });
  const js = challenge.interpreterJavascript?.privateDoNotAccessOrElseSafeScriptWrappedValue;
  if (js) new Function(js)();
  else if (challenge.interpreterUrl?.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue) {
    const url = challenge.interpreterUrl.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue;
    new Function(await (await fetch(url.startsWith('//') ? 'https:' + url : url)).text())();
  }
  const bg = await BotGuardClient.create({ program: challenge.program, globalName: challenge.globalName, globalObject: window });
  const webPoSignalOutput = [];
  const botguardResponse = await bg.snapshot({ webPoSignalOutput });
  const r = await fetch('https://www.youtube.com/api/jnn/v1/GenerateIT', {
    method: 'POST',
    headers: { 'content-type': 'application/json+protobuf', 'x-goog-api-key': GOOG_KEY, 'x-user-agent': 'grpc-web-javascript/0.1' },
    body: JSON.stringify([REQUEST_KEY, botguardResponse]),
  });
  const it = await r.json().catch(() => null);
  if (!it?.[0]) {
    // Sin token de integridad: usamos el token de respaldo que entrega YouTube
    if (it?.[3]) { log('uso token de respaldo'); return { mintAsWebsafeString: async () => it[3] }; }
    throw new Error('sin token de integridad (' + r.status + ')');
  }
  return WebPoMinter.create({ integrityToken: it[0], estimatedTtlSecs: it[1], mintRefreshThreshold: it[2], websafeFallbackToken: it[3] }, webPoSignalOutput);
}
async function mintPot(identifier) {
  if (potCache.has(identifier)) return potCache.get(identifier);
  // En la app instalada el token se genera en una ventana oculta de youtube.com (más confiable)
  if (window.vtNative?.mintPot) {
    try {
      const pot = await window.vtNative.mintPot(identifier);
      if (pot) { potCache.set(identifier, pot); log('token anti-bot listo (youtube.com)'); return pot; }
    } catch (e) { log('token (youtube.com) falló', String(e.message || e).slice(0, 160)); }
  }
  if (!minterPromise) minterPromise = getMinter().catch((e) => { minterPromise = null; throw e; });
  const minter = await minterPromise;
  const pot = await minter.mintAsWebsafeString(identifier);
  if (pot) { potCache.set(identifier, pot); log('token anti-bot listo'); }
  return pot;
}

// ---------- Utilidades para leer los datos ----------
export const txt = (x) => (x == null ? '' : typeof x === 'string' ? x : (x.text ?? (typeof x.toString === 'function' ? x.toString() : '')) || '');
const bestImg = (arr) => {
  if (!arr) return '';
  const list = Array.isArray(arr) ? arr : arr.image || arr.thumbnails || [];
  if (!list.length) return '';
  const sorted = [...list].sort((a, b) => (b.width || 0) - (a.width || 0));
  const pick = sorted.find((i) => (i.width || 0) <= 720) || sorted[0];
  let u = pick.url || '';
  if (u.startsWith('//')) u = 'https:' + u;
  return u;
};
const findChannelId = (node) => {
  const m = JSON.stringify(node).match(/"(?:browse_id|browseId)":"(UC[\w-]{22})"/);
  return m ? m[1] : '';
};
const durToSec = (t) => {
  if (!t) return 0;
  const p = String(t).split(':').map(Number);
  if (p.some(isNaN)) return 0;
  return p.reduce((a, b) => a * 60 + b, 0);
};

/** Convierte cualquier tarjeta de YouTube en un video simple para la interfaz. */
const withViews = (s) => (s && /^[\d.,\s]+\s?(K|M|B|mil|M de)?$/i.test(s.trim()) ? s.trim() + ' vistas' : s);

export function norm(n) {
  if (!n) return null;
  const t = n.type;
  try {
    if (t === 'Video' || t === 'GridVideo' || t === 'CompactVideo' || t === 'PlaylistVideo' || t === 'VideoCard') {
      const id = n.video_id || n.id;
      if (!id) return null;
      const durText = n.duration?.text || txt(n.length_text);
      return {
        id,
        title: txt(n.title),
        thumb: bestImg(n.thumbnails) || `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
        channel: n.author?.name || txt(n.short_byline_text) || '',
        channelId: n.author?.id || findChannelId(n.author) || '',
        avatar: bestImg(n.author?.thumbnails),
        views: withViews(txt(n.short_view_count) || txt(n.view_count)),
        published: txt(n.published),
        duration: durText,
        seconds: n.duration?.seconds || durToSec(durText),
        live: !!n.is_live || /EN VIVO|LIVE/i.test(JSON.stringify(n.badges || '')),
        short: false,
      };
    }
    if (t === 'LockupView') {
      if (n.content_type && n.content_type !== 'VIDEO') return null;
      const id = n.content_id;
      if (!id || id.length !== 11) return null;
      const meta = n.metadata;
      const rows = meta?.metadata?.metadata_rows || [];
      const parts = rows.map((r) => (r.metadata_parts || []).map((p) => txt(p.text))).filter((r) => r.length);
      const badges = JSON.stringify(n.content_image?.overlays || []);
      const dm = badges.match(/"text":"(\d{1,2}(?::\d{2}){1,2})"/);
      const live = /EN VIVO|LIVE|DIRECTO/i.test(badges);
      const flat = parts.flat();
      return {
        id,
        title: txt(meta?.title),
        thumb: bestImg(n.content_image?.image) || `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
        channel: parts[0]?.[0] || '',
        channelId: findChannelId(meta?.image) || findChannelId(rows[0]) || '',
        avatar: bestImg(meta?.image?.avatar?.image || meta?.image?.image),
        views: flat.find((s) => /visualiz|vistas|views/i.test(s)) || '',
        published: flat.find((s) => /hace|ago|emitido/i.test(s)) || '',
        duration: dm ? dm[1] : '',
        seconds: dm ? durToSec(dm[1]) : 0,
        live,
        short: false,
      };
    }
    if (t === 'ShortsLockupView' || t === 'ReelItem') {
      const id = n.on_tap_endpoint?.payload?.videoId || n.video_id || (n.entity_id || '').replace('shorts-shelf-item-', '');
      if (!id || id.length !== 11) return null;
      return {
        id,
        title: txt(n.overlay_metadata?.primary_text) || txt(n.title) || (n.accessibility_text || '').split(',')[0],
        thumb: bestImg(n.thumbnail_view_model?.image || n.thumbnail) || `https://i.ytimg.com/vi/${id}/oar2.jpg`,
        channel: '',
        channelId: '',
        avatar: '',
        views: txt(n.overlay_metadata?.secondary_text) || txt(n.views),
        published: '',
        duration: '',
        seconds: 0,
        live: false,
        short: true,
      };
    }
  } catch (e) {
    log('norm error', t, e.message);
  }
  return null;
}

function collect(list) {
  const out = [];
  const seen = new Set();
  const walk = (n) => {
    if (!n) return;
    if (Array.isArray(n)) return n.forEach(walk);
    const v = norm(n);
    if (v) { if (!seen.has(v.id)) { seen.add(v.id); out.push(v); } return; }
    if (n.contents) walk(n.contents);
    if (n.items) walk(n.items);
  };
  walk(list);
  return out;
}

// ---------- Búsqueda ----------
export async function search(query, opts = {}) {
  await init();
  const s = await yt.search(query, { type: opts.type || 'video', upload_date: opts.uploadDate, sort_by: opts.sortBy });
  const page = { videos: collect(s.results || []), channels: [], more: null };
  page.channels = (s.results || []).filter((r) => r.type === 'Channel').map((c) => ({
    id: c.id || c.author?.id,
    name: c.author?.name || txt(c.title),
    avatar: bestImg(c.author?.thumbnails),
    subs: txt(c.video_count) || txt(c.subscriber_count),
  }));
  if (s.has_continuation) {
    page.more = async () => {
      const n = await s.getContinuation();
      return wrapContinuation(n);
    };
  }
  return page;
}
function wrapContinuation(s) {
  return {
    videos: collect(s.results || s.videos || []),
    channels: [],
    more: s.has_continuation ? async () => wrapContinuation(await s.getContinuation()) : null,
  };
}

export async function searchAll(query) {
  await init();
  const s = await yt.search(query);
  const results = s.results || [];
  const channels = results.filter((r) => r.type === 'Channel').map((c) => ({
    id: c.id || c.author?.id,
    name: c.author?.name || txt(c.title),
    avatar: bestImg(c.author?.thumbnails),
    subs: txt(c.subscriber_count) || txt(c.video_count),
  }));
  return {
    videos: collect(results),
    channels,
    more: s.has_continuation ? async () => wrapContinuation(await s.getContinuation()) : null,
  };
}

export async function shorts(query) {
  await init();
  const s = await yt.search(query, { type: 'shorts' });
  return collect(s.results || []).map((v) => ({ ...v, short: true }));
}

export async function suggestions(q) {
  await init();
  try { return (await yt.getSearchSuggestions(q)).slice(0, 8); } catch { return []; }
}

// ---------- Canal ----------
export async function channel(id) {
  await init();
  const ch = await yt.getChannel(id);
  const md = ch.metadata || {};
  const header = JSON.stringify(ch.header || {});
  const subsMatch = header.match(/"text":"([^"]*suscriptor[^"]*)"/i) || header.match(/"text":"([^"]*subscriber[^"]*)"/i);
  const bannerMatch = header.match(/"banner":\{[^]*?"url":"([^"]+)"/);
  let videos = [];
  let more = null;
  try {
    const v = await ch.getVideos();
    videos = collect(v.videos || v.current_tab?.content?.contents || []);
    if (v.has_continuation) {
      const next = async (src) => {
        const n = await src.getContinuation();
        return { videos: collect(n.videos || []), more: n.has_continuation ? () => next(n) : null };
      };
      more = () => next(v);
    }
  } catch (e) { log('canal sin videos', e.message); }
  return {
    id,
    name: md.title || '',
    avatar: bestImg(md.avatar),
    banner: bannerMatch ? bannerMatch[1] : '',
    description: md.description || '',
    subs: subsMatch ? subsMatch[1] : '',
    videos,
    more,
    getShorts: async () => {
      try { const s = await ch.getShorts(); return collect(s.videos || []).map((x) => ({ ...x, short: true, channel: md.title, channelId: id })); }
      catch { return []; }
    },
  };
}

export async function channelLatest(id, max = 6) {
  try {
    const ch = await yt.getChannel(id);
    const v = await ch.getVideos();
    return collect(v.videos || []).slice(0, max).map((x) => ({
      ...x,
      channel: x.channel || ch.metadata?.title || '',
      channelId: id,
      avatar: x.avatar || bestImg(ch.metadata?.avatar),
    }));
  } catch { return []; }
}

// ---------- Video ----------
export async function details(id) {
  await init();
  const info = await yt.getInfo(id).catch(async (e) => {
    log('getInfo WEB falló, uso next', e.message);
    return yt.getInfo(id, { client: 'MWEB' });
  });
  const b = info.basic_info || {};
  const owner = info.secondary_info?.owner;
  const related = collect(info.watch_next_feed || []);
  const desc = info.secondary_info?.description;
  return {
    id,
    info,
    title: b.title || txt(info.primary_info?.title),
    channel: owner?.author?.name || b.author || '',
    channelId: owner?.author?.id || b.channel_id || '',
    avatar: bestImg(owner?.author?.thumbnails),
    subs: /^n\/?a$/i.test(txt(owner?.subscriber_count).trim()) ? '' : txt(owner?.subscriber_count),
    views: txt(info.primary_info?.view_count?.view_count) || txt(info.primary_info?.view_count?.short_view_count),
    published: txt(info.primary_info?.relative_date) || txt(info.primary_info?.published),
    date: txt(info.primary_info?.published),
    likes: b.like_count ?? -1,
    description: txt(desc) || b.short_description || '',
    seconds: b.duration || 0,
    live: !!b.is_live,
    related,
    year: (b.publish_date || '').slice(0, 4) || (txt(info.primary_info?.published).match(/(19|20)\d{2}/) || [''])[0],
  };
}

export async function comments(id) {
  await init();
  try {
    const c = await yt.getComments(id);
    const list = (c.contents || []).map((t) => t.comment).filter(Boolean).map((x) => ({
      author: x.author?.name || '',
      avatar: bestImg(x.author?.thumbnails),
      text: txt(x.content),
      date: txt(x.published_time) || txt(x.published),
      likes: txt(x.like_count) || '',
    }));
    return { list, count: txt(c.header?.count) || '' };
  } catch (e) {
    return { list: [], count: '', error: e.message };
  }
}

// ---------- Reproducción ----------
const xmlEsc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

async function buildDash(sd, seconds, pot) {
  const player = yt.session.player;
  const fmts = (sd.adaptive_formats || []).filter((f) => f.init_range && f.index_range && f.mime_type);
  const video = fmts.filter((f) => f.has_video && /avc1|vp9|vp09/.test(f.mime_type) && !/av01/.test(f.mime_type));
  let audio = fmts.filter((f) => f.has_audio && !f.has_video && !f.is_drc);
  const def = audio.filter((f) => !f.audio_track || f.audio_track.audio_is_default);
  if (def.length) audio = def;
  if (!video.length || !audio.length) return null;
  const url = async (f) => {
    let u = await f.decipher(player);
    if (pot) u += (u.includes('?') ? '&' : '?') + 'pot=' + encodeURIComponent(pot);
    return u;
  };
  const rep = async (f) => {
    const [mime, codecs] = f.mime_type.split(';').map((s) => s.trim());
    const c = (codecs || '').replace(/codecs=|"/g, '');
    const attrs = f.has_video
      ? `width="${f.width}" height="${f.height}" frameRate="${f.fps || 30}"`
      : `audioSamplingRate="${f.audio_sample_rate || 44100}"`;
    return {
      mime,
      xml: `<Representation id="${f.itag}${f.has_video ? '' : 'a' + (f.audio_track?.id || '')}" codecs="${xmlEsc(c)}" bandwidth="${f.bitrate}" ${attrs}>` +
        `<BaseURL>${xmlEsc(await url(f))}</BaseURL>` +
        `<SegmentBase indexRange="${f.index_range.start}-${f.index_range.end}"><Initialization range="${f.init_range.start}-${f.init_range.end}"/></SegmentBase>` +
        `</Representation>`,
    };
  };
  const group = async (list, type) => {
    const reps = await Promise.all(list.map(rep));
    const byMime = {};
    reps.forEach((r) => (byMime[r.mime] = byMime[r.mime] || []).push(r.xml));
    return Object.entries(byMime).map(([mime, xs]) =>
      `<AdaptationSet mimeType="${mime}" contentType="${type}" subsegmentAlignment="true">${xs.join('')}</AdaptationSet>`).join('');
  };
  const dur = seconds || Math.round((Number(fmts[0].approx_duration_ms) || 0) / 1000) || 1;
  const mpd = `<?xml version="1.0" encoding="UTF-8"?>` +
    `<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" minBufferTime="PT1.5S" mediaPresentationDuration="PT${dur}S" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011">` +
    `<Period>${await group(video, 'video')}${await group(audio, 'audio')}</Period></MPD>`;
  return 'data:application/dash+xml;base64,' + btoa(unescape(encodeURIComponent(mpd)));
}

/**
 * Busca una forma de reproducir el video probando varios "clientes" de YouTube
 * (como hace la app de Android). Devuelve la fuente para el reproductor.
 */
let preferred = Math.min(3, Number(localStorage.getItem('vt_client4') || 0) || 0);
/** El cliente que reprodujo bien queda primero para los siguientes videos. */
export function rememberClient(index) {
  if (index == null || index === preferred) return;
  preferred = index;
  try { localStorage.setItem('vt_client4', String(index)); } catch {}
  log('cliente preferido ahora', index);
}

export async function playable(id, seconds, attempt = 0) {
  await init();
  // ANDROID_VR e iOS se cortan a los pocos segundos en conexiones normales: solo clientes web
  // MWEB con el token ligado al video o a la sesión (YouTube cambia cuál pide); luego TV y WEB
  const base = ['MWEB:video', 'MWEB:sesion', 'TV:video', 'WEB:video'];
  const order = [base[preferred], ...base.filter((_, i) => i !== preferred)];
  const errors = [];
  for (const entry of order.slice(attempt)) {
    const [client, binding] = entry.split(':');
    try {
      // Token del reproductor (ligado al video) y token de descarga (ligado a la sesión)
      let pot = null;
      let gvsPot = null;
      if (client === 'WEB' || client === 'MWEB' || client === 'TV') {
        try { pot = await mintPot(id); } catch (e) { log('token falló', e.message); }
        const visitor = yt.session.context.client.visitorData;
        if (binding === 'sesion') { try { gvsPot = visitor ? await mintPot(visitor) : pot; } catch (e) { gvsPot = pot; } }
        else gvsPot = pot;
      }
      const b = await yt.getBasicInfo(id, pot ? { client, po_token: pot } : { client });
      const st = b.playability_status?.status;
      if (st && st !== 'OK') { errors.push(`${client}: ${b.playability_status?.reason || st}`); log('cliente', client, 'no disponible:', b.playability_status?.reason || st); continue; }
      const sd = b.streaming_data;
      if (!sd) { errors.push(`${client}: sin datos`); continue; }
      const captions = (b.captions?.caption_tracks || []).map((c) => ({
        url: c.base_url + '&fmt=vtt',
        lang: c.language_code,
        name: txt(c.name),
        auto: c.kind === 'asr',
      }));
      if (b.basic_info?.is_live || (!sd.adaptive_formats?.length && (sd.hls_manifest_url || sd.dash_manifest_url))) {
        const live = sd.hls_manifest_url || sd.dash_manifest_url;
        if (live) { log('reproduciendo con', client, '(en vivo)'); return { src: live, live: true, captions, index: base.indexOf(entry), pos: order.indexOf(entry), client: entry }; }
      }
      const dash = await buildDash(sd, seconds, gvsPot || pot);
      if (dash) { log('reproduciendo con', entry); return { src: dash, captions, index: base.indexOf(entry), pos: order.indexOf(entry), client: entry }; }
      if (sd.hls_manifest_url) return { src: sd.hls_manifest_url, captions, index: base.indexOf(entry), pos: order.indexOf(entry), client: entry };
      const muxed = (sd.formats || [])[0];
      if (muxed) return { src: await muxed.decipher(yt.session.player), client, progressive: true, captions, index: base.indexOf(entry), pos: order.indexOf(entry), client: entry };
      errors.push(`${client}: sin formatos`);
    } catch (e) {
      errors.push(`${client}: ${e.message}`);
    }
  }
  log('no se pudo reproducir', errors);
  const err = new Error('No se pudo reproducir este video.\n' + errors.join('\n'));
  err.details = errors;
  throw err;
}
