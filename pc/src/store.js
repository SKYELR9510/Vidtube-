// Datos guardados en esta PC (historial, listas, suscripciones, ajustes)
const read = (k, d) => { try { const v = localStorage.getItem('vt_' + k); return v ? JSON.parse(v) : d; } catch { return d; } };
const write = (k, v) => { try { localStorage.setItem('vt_' + k, JSON.stringify(v)); } catch {} };
const listeners = new Set();
export const onChange = (fn) => { listeners.add(fn); return () => listeners.delete(fn); };
const emit = () => listeners.forEach((f) => f());

export const store = {
  get history() { return read('history', []); },
  get later() { return read('later', []); },
  get liked() { return read('liked', []); },
  get subs() { return read('subs', []); },
  get searches() { return read('searches', []); },
  get settings() { return { light: false, quality: 'auto', autoplay: true, saveHistory: true, volume: 1, ...read('settings', {}) }; },
};

const slim = (v) => ({
  id: v.id, title: v.title, thumb: v.thumb, channel: v.channel, channelId: v.channelId,
  avatar: v.avatar, views: v.views, published: v.published, duration: v.duration, seconds: v.seconds, short: !!v.short,
});

export function addHistory(v) {
  if (!store.settings.saveHistory) return;
  write('history', [slim(v), ...store.history.filter((x) => x.id !== v.id)].slice(0, 400));
  emit();
}
export function removeHistory(id) { write('history', store.history.filter((x) => x.id !== id)); emit(); }
export function clearHistory() { write('history', []); write('searches', []); write('positions', {}); emit(); }

function toggleIn(key, v) {
  const list = read(key, []);
  const has = list.some((x) => x.id === v.id);
  write(key, has ? list.filter((x) => x.id !== v.id) : [slim(v), ...list]);
  emit();
  return !has;
}
export const toggleLater = (v) => toggleIn('later', v);
export const toggleLike = (v) => toggleIn('liked', v);
export const isLater = (id) => store.later.some((x) => x.id === id);
export const isLiked = (id) => store.liked.some((x) => x.id === id);

export const isSub = (id) => !!id && store.subs.some((c) => c.id === id);
export function toggleSub(c) {
  const list = store.subs;
  const has = list.some((x) => x.id === c.id);
  write('subs', has ? list.filter((x) => x.id !== c.id) : [{ id: c.id, name: c.name, avatar: c.avatar }, ...list]);
  emit();
  return !has;
}

export function addSearch(q) {
  q = q.trim();
  if (!q || !store.settings.saveHistory) return;
  write('searches', [q, ...store.searches.filter((s) => s.toLowerCase() !== q.toLowerCase())].slice(0, 30));
}

export function setSettings(patch) { write('settings', { ...store.settings, ...patch }); emit(); }

export const position = (id) => read('positions', {})[id] || 0;
export function savePosition(id, sec, dur) {
  const p = read('positions', {});
  if (dur && (sec < 15 || sec > dur - 20)) delete p[id]; else p[id] = Math.floor(sec);
  const keys = Object.keys(p);
  if (keys.length > 300) delete p[keys[0]];
  write('positions', p);
}

/** Temas que más te interesan (búsquedas y canales que más ves). */
export function interests(max = 5) {
  const counts = new Map();
  store.searches.slice(0, 12).forEach((s, i) => counts.set(s, (counts.get(s) || 0) + 6 - Math.min(i, 5)));
  store.history.slice(0, 60).forEach((v) => { if (v.channel) counts.set(v.channel, (counts.get(v.channel) || 0) + 1); });
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, max).map(([q]) => q);
}
