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
  // F12 abre las herramientas de diagnóstico
  win.webContents.on('before-input-event', (_e, input) => {
    if (input.type === 'keyDown' && input.key === 'F12') win.webContents.toggleDevTools();
  });
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
