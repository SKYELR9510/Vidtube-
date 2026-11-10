// Compila Vidtube para TV Samsung (Tizen) en tv/dist
import { build } from 'esbuild';
import { mkdirSync, copyFileSync, readFileSync, writeFileSync, cpSync } from 'fs';
import path from 'path';
const version = process.env.TV_VERSION || '1.0.0';
const out = path.resolve('../tv/dist');
mkdirSync(out, { recursive: true });
await build({
  entryPoints: ['../tv/src/app.js'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  // TV Samsung 2020 = Chrome 69 por dentro
  target: ['chrome69'],
  supported: { bigint: true },
  nodePaths: [path.resolve('node_modules')],
  outfile: path.join(out, 'tv.js'),
  minify: true,
  define: { __VERSION__: JSON.stringify(version) },
  logLevel: 'info',
});
const js = readFileSync(path.join(out, 'tv.js'), 'utf8').replace(/__VERSION__/g, version);
writeFileSync(path.join(out, 'tv.js'), js);
copyFileSync('../tv/index.html', path.join(out, 'index.html'));
copyFileSync('../tv/style.css', path.join(out, 'style.css'));
writeFileSync(path.join(out, 'config.xml'), readFileSync('../tv/config.xml', 'utf8').replace('version="1.0.0"', `version="${version}"`));
cpSync('../tv/assets', path.join(out, 'assets'), { recursive: true });
