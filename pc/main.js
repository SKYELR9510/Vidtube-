// Vidtube para PC (Electron): ventana principal y ajustes de red
const { app, BrowserWindow, session, shell, ipcMain, net } = require('electron');
const fs = require('fs');
const path = require('path');

// Quitamos "Electron" del user-agent: YouTube lo ve como un Chrome normal
app.userAgentFallback = app.userAgentFallback
  .replace(/ Electron\/\S+/, '')
  .replace(/ vidtube-pc\/\S+/i, '')
  .replace(/ Vidtube\/\S+/, '');

const UA = {
  ANDROID_VR: 'com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip',
  IOS: 'com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)',
  VISIONOS: 'com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; GB)',
  MWEB: 'Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)',
};

function createWindow() {
  const win = new BrowserWindow({
    width: 1380,
    height: 880,
    minWidth: 400,
    minHeight: 620,
    backgroundColor: '#08090B',
    autoHideMenuBar: true,
    title: 'Vidtube',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      // La app habla directo con YouTube (sin servidor intermedio)
      webSecurity: false,
      contextIsolation: true,
      nodeIntegration: false,
      backgroundThrottling: false,
    },
  });
  win.removeMenu();
  win.loadFile(path.join(__dirname, 'index.html'), { query: { v: app.getVersion() } });
  // Links externos (descargar actualización, etc.) en el navegador
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
  // Solo para pruebas automáticas en GitHub: captura pantallas y cierra
  if (process.env.VIDTUBE_SHOTS) runShots(win);
  // F12 abre las herramientas de diagnóstico
  win.webContents.on('before-input-event', (_e, input) => {
    if (input.type === 'keyDown' && input.key === 'F12') win.webContents.toggleDevTools();
  });
}

async function runShots(win) {
  const fs = require('fs');
  const dir = process.env.VIDTUBE_SHOTS;
  fs.mkdirSync(dir, { recursive: true });
  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  const shot = async (name) => fs.writeFileSync(path.join(dir, name + '.png'), (await win.webContents.capturePage()).toPNG());
  win.webContents.on('console-message', (_e, level, msg) => fs.appendFileSync(path.join(dir, 'console.txt'), `[${level}] ${msg}\n`));
  await wait(1200); await shot('0-splash');
  await wait(22000); await shot('1-home');
  const js = (code) => win.webContents.executeJavaScript(code).catch((e) => fs.appendFileSync(path.join(dir, 'console.txt'), 'JS ' + e + '\n'));
  await js("document.querySelector('#view').scrollTop = 600"); await wait(1500); await shot('2-home-scroll');
  await js("document.querySelector('#view').scrollTop = 1300"); await wait(1500); await shot('2b-home-scroll');
  await js("document.querySelector('.shelf .card .kebab')?.click()"); await wait(600); await shot('3-menu');
  await js("document.querySelector('.menu-btn').click(); document.querySelector('#view').scrollTop = 0; document.querySelector('.avatar-btn').click()"); await wait(1200); await shot('4-collapsed-profile');
  await js("document.querySelector('.avatar-btn').click(); document.querySelector('.menu-btn').click()");
  await js("window.__vt.go('explore')"); await wait(2000); await shot('5-explore');
  await js("window.__vt.go('search', {q: 'cumbia'})"); await wait(9000); await shot('6-search');
  await js("document.querySelector('.grid .card').click()"); await wait(25000); await shot('7-watch');
  // Prueba del reproductor flotante: salir del video mientras se reproduce
  await js("window.__vt.go('library')"); await wait(4000); await shot('8-mini-library');
  // Posición guardada de un video real del historial para ver "Continuar viendo"
  await js("(() => { const h = JSON.parse(localStorage.vt_history || '[]').filter((v) => v.seconds); const p = JSON.parse(localStorage.vt_positions || '{}'); h.slice(0, 3).forEach((v, i) => { if (!p[v.id]) p[v.id] = Math.floor(v.seconds * (0.3 + i * 0.2)); }); localStorage.vt_positions = JSON.stringify(p); })()");
  await js("window.__vt.go('home')"); await wait(15000); await shot('9-home-mini');
  await js("document.querySelector('.mini .m-open')?.click()"); await wait(4000); await shot('10-watch-again');
  await js("window.__vt.go('channel', {id: 'UCBR8-60-B28hp2BmDPdntcQ'})"); await wait(10000); await shot('11-channel');
  await js("window.__vt.go('settings')"); await wait(1500); await shot('12-settings');
  await js("window.__vt.go('subs')"); await wait(4000); await shot('13-subs');
  await js("window.__vt.go('shorts')"); await wait(14000); await shot('14-shorts');
  fs.writeFileSync(path.join(dir, 'debug.txt'), String(await js('window.__vt.debug().join("\\n")')));
  await js("window.__vt.go('home')"); await wait(1000);
  win.setSize(430, 900); await wait(15000); await shot('15-movil');
  win.setSize(1380, 880);
  await js("localStorage.setItem('vt_settings', JSON.stringify({light:true})); location.reload()"); await wait(22000); await shot('16-light');
  await js("document.querySelector('#view').scrollTop = 600"); await wait(1500); await shot('17-light-scroll');
  app.quit();
}

// ---------- Token anti-bot en una ventana oculta "dentro" de youtube.com ----------
let bgWin = null;
let bgReady = null;
const BG_URL = 'https://www.youtube.com/vidtube_bg';
function ensureBgWindow() {
  if (bgReady && bgWin && !bgWin.isDestroyed()) return bgReady;
  const ses = session.fromPartition('persist:vidtube-bg');
  try { ses.protocol.unhandle('https'); } catch {}
  ses.protocol.handle('https', (req) => {
    if (req.url === BG_URL) {
      return new Response('<!doctype html><html><head><meta charset="utf-8"><title>bg</title></head><body></body></html>', { headers: { 'content-type': 'text/html' } });
    }
    return net.fetch(req, { bypassCustomProtocolHandlers: true });
  });
  bgWin = new BrowserWindow({ show: false, width: 400, height: 300, webPreferences: { session: ses, contextIsolation: true, nodeIntegration: false, backgroundThrottling: false } });
  const code = fs.readFileSync(path.join(__dirname, 'dist', 'pot.js'), 'utf8');
  bgReady = bgWin.loadURL(BG_URL).then(() => bgWin.webContents.executeJavaScript(code + ';true'));
  bgReady.catch(() => { bgReady = null; });
  return bgReady;
}
ipcMain.handle('mint-pot', async (_e, identifier) => {
  await ensureBgWindow();
  return bgWin.webContents.executeJavaScript(`window.__vtMint(${JSON.stringify(String(identifier))})`);
});

app.whenReady().then(() => {
  const filter = { urls: ['*://*.googlevideo.com/*', '*://*.youtube.com/*', '*://*.google.com/*', '*://*.ytimg.com/*', '*://youtubei.googleapis.com/*'] };
  session.defaultSession.webRequest.onBeforeSendHeaders(filter, (details, callback) => {
    const h = details.requestHeaders;
    // Cliente visionOS (como la app de Android): sin Origin/Referer y con su propio user-agent
    if (details.url.includes('youtubei.googleapis.com') || /[?&]c=VISIONOS/.test(details.url)) {
      delete h['Origin']; delete h['Referer'];
      h['User-Agent'] = UA.VISIONOS;
      return callback({ requestHeaders: h });
    }
    h['Origin'] = 'https://www.youtube.com';
    h['Referer'] = 'https://www.youtube.com/';
    // Los videos pedidos con el cliente de Android/iOS esperan su propio user-agent
    if (details.url.includes('googlevideo.com')) {
      if (/[?&]c=ANDROID_VR/.test(details.url)) h['User-Agent'] = UA.ANDROID_VR;
      else if (/[?&]c=IOS/.test(details.url)) h['User-Agent'] = UA.IOS;
      else if (/[?&]c=MWEB/.test(details.url)) h['User-Agent'] = UA.MWEB;
    }
    callback({ requestHeaders: h });
  });
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => app.quit());
