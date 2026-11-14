// Prueba en GitHub: abre la app del TV (1920×1080) y toma capturas
const { app, BrowserWindow } = require('electron');
const fs = require('fs'); const path = require('path');
app.whenReady().then(async () => {
  const dir = process.env.SHOTS; fs.mkdirSync(dir, { recursive: true });
  const w = new BrowserWindow({ width: 1920, height: 1080, useContentSize: true, show: true, webPreferences: { webSecurity: false } });
  w.webContents.on('console-message', (_e, l, m) => fs.appendFileSync(path.join(dir, 'console.txt'), `[${l}] ${m}\n`));
  await w.loadFile(path.join(__dirname, 'dist', 'index.html'));
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const shot = async (n) => fs.writeFileSync(path.join(dir, n + '.png'), (await w.webContents.capturePage()).toPNG());
  const key = async (k, n = 1) => { for (let i = 0; i < n; i++) { w.webContents.sendInputEvent({ type: 'keyDown', keyCode: k }); w.webContents.sendInputEvent({ type: 'keyUp', keyCode: k }); await wait(400); } };
  await wait(20000); await shot('tv-1-home');
  await key('Down', 2); await wait(800); await shot('tv-2-row');
  await key('Down', 2); await wait(800); await shot('tv-2b-descubre');
  await key('Left', 6); await wait(800); await shot('tv-3-rail');
  await key('Up', 5); await key('Down'); await key('Return'); await wait(4000); await shot('tv-4-explorar');
  await key('Down', 2); await key('Return'); await wait(9000); await shot('tv-5-results');
  await key('Down'); await key('Return'); await wait(12000); await shot('tv-6-watch');
  await key('Up'); await wait(800); await shot('tv-7-watch-ui');
  app.quit();
});
