// Vidtube para PC (Electron): ventana principal y ajustes de red
const { app, BrowserWindow, session, shell } = require('electron');
const path = require('path');

// Quitamos "Electron" del user-agent: YouTube lo ve como un Chrome normal
app.userAgentFallback = app.userAgentFallback
  .replace(/ Electron\/\S+/, '')
  .replace(/ vidtube-pc\/\S+/i, '')
  .replace(/ Vidtube\/\S+/, '');

const UA = {
  ANDROID_VR: 'com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip',
  IOS: 'com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)',
};

function createWindow() {
  const win = new BrowserWindow({
    width: 1380,
    height: 880,
    minWidth: 960,
    minHeight: 620,
    backgroundColor: '#09090B',
    autoHideMenuBar: true,
    title: 'Vidtube',
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
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
  await js("document.querySelector('#view').scrollTop = 900"); await wait(1500); await shot('2-home-scroll');
  await js("window.__vt.go('explore')"); await wait(2000); await shot('3-explore');
  await js("window.__vt.go('search', {q: 'cumbia'})"); await wait(9000); await shot('4-search');
  await js("document.querySelector('.card').click()"); await wait(15000); await shot('5-watch');
  await js("document.querySelector('.w-channel .avatar')?.click()"); await wait(10000); await shot('6-channel');
  await js("window.__vt.go('settings')"); await wait(1500); await shot('7-settings');
  await js("window.__vt.go('library')"); await wait(1500); await shot('8-library');
  await js("window.__vt.go('shorts')"); await wait(14000); await shot('9-shorts');
  fs.writeFileSync(path.join(dir, 'debug.txt'), String(await js('window.__vt.debug().join("\\n")')));
  await js("localStorage.setItem('vt_settings', JSON.stringify({light:true})); location.reload()"); await wait(20000); await shot('10-light');
  app.quit();
}

app.whenReady().then(() => {
  const filter = { urls: ['*://*.googlevideo.com/*', '*://*.youtube.com/*', '*://*.google.com/*', '*://*.ytimg.com/*'] };
  session.defaultSession.webRequest.onBeforeSendHeaders(filter, (details, callback) => {
    const h = details.requestHeaders;
    h['Origin'] = 'https://www.youtube.com';
    h['Referer'] = 'https://www.youtube.com/';
    // Los videos pedidos con el cliente de Android/iOS esperan su propio user-agent
    if (details.url.includes('googlevideo.com')) {
      if (/[?&]c=ANDROID_VR/.test(details.url)) h['User-Agent'] = UA.ANDROID_VR;
      else if (/[?&]c=IOS/.test(details.url)) h['User-Agent'] = UA.IOS;
    }
    callback({ requestHeaders: h });
  });
  createWindow();
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});

app.on('window-all-closed', () => app.quit());
