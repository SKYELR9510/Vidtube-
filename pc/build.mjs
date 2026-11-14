import { build } from 'esbuild';
await build({
  entryPoints: ['src/app.js'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: ['chrome120'],
  outfile: 'dist/app.js',
  minify: true,
  sourcemap: false,
  logLevel: 'info',
});
await build({
  entryPoints: ['src/pot.js'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: ['chrome120'],
  outfile: 'dist/pot.js',
  minify: true,
  logLevel: 'info',
});
import { copyFileSync, mkdirSync } from 'fs';
await build({ entryPoints: ['src/netbridge.js'], bundle: false, format: 'iife', target: ['chrome120'], outfile: 'dist/netbridge.js', minify: true, logLevel: 'info' });
mkdirSync('dist', { recursive: true });
copyFileSync('src/style.css', 'dist/style.css');
