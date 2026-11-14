// Genera las notas de "Por qué se actualiza" a partir de los cambios desde la versión anterior
// de la misma plataforma. Uso: node notas.mjs <android|pc|tv> <patrón de etiqueta> <salida> <rutas...>
import { execSync } from 'child_process';
import { writeFileSync } from 'fs';

const [platform, pattern, out, ...paths] = process.argv.slice(2);
const sh = (c) => { try { return execSync(c, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(); } catch { return ''; } };
const prev = sh(`git describe --tags --abbrev=0 --match "${pattern}" HEAD`);
const range = prev ? `${prev}..HEAD` : 'HEAD~10..HEAD';
const SEP = '@@@fin@@@';
const commits = sh(`git log --no-merges --pretty=%B${SEP} ${range} -- ${paths.map((p) => `"${p}"`).join(' ')}`).split(SEP).map((c) => c.trim()).filter(Boolean);
// Si el cambio trae líneas "Notas:" / "Notas-PC:" / "Notas-Android:" / "Notas-TV:" (en palabras simples), se usan esas;
// si no, el título del cambio.
const LABEL = { android: 'Android', pc: 'PC', tv: 'TV' }[platform];
const subjects = [];
for (const c of commits) {
  const lines = c.split('\n');
  const own = lines.filter((l) => new RegExp(`^Notas(-${LABEL})?:\\s*`, 'i').test(l)).map((l) => l.replace(/^Notas(-\w+)?:\s*/i, ''));
  const tagged = lines.some((l) => /^Notas(-\w+)?:/i.test(l));
  if (own.length) subjects.push(...own);
  else if (!tagged) subjects.push(lines[0]);
}

const OTHERS = { android: /^(PC|TV|Windows|Samsung)\b/i, pc: /^(Android|TV|Samsung|celular)\b/i, tv: /^(Android|PC|Windows|celular)\b/i }[platform] || /^$/;
const SKIP = /^(capturas?|guion|docs|ci|prueba|tests?)\b/i;
const items = [];
for (const s of subjects) {
  for (let piece of s.split(/;\s+/)) {
    piece = piece.trim();
    if (!piece || OTHERS.test(piece)) continue;
    piece = piece.replace(/^(PC|Android|TV)\s*:\s*/i, '');
    if (SKIP.test(piece)) continue;
    piece = piece.charAt(0).toUpperCase() + piece.slice(1);
    if (!items.includes(piece)) items.push(piece);
  }
}
const list = (items.length ? items : ['Mejoras internas de estabilidad y rendimiento']).slice(0, 12);
writeFileSync(out, '## Por qué se actualiza\n' + list.map((x) => '- ' + x).join('\n') + '\n');
console.log(`Notas desde ${prev || '(inicio)'}:\n` + list.join('\n'));
