import { build } from 'esbuild';
await build({
  entryPoints: ['src/app.js'],
  bundle: true,
  format: 'esm',
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
mkdirSync('dist', { recursive: true });
copyFileSync('src/style.css', 'dist/style.css');
