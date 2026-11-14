// Puente seguro entre la interfaz y el proceso principal (contextIsolation + sandbox).
// Solo expone estas funciones; la página no tiene acceso a Node ni a Electron.
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('vtNative', {
  mintPot: (id) => ipcRenderer.invoke('mint-pot', id),
  // Ejecuta el código del reproductor de YouTube (descifrado) en una ventana aislada sin red
  evalPlayer: (code) => ipcRenderer.invoke('eval-player', code),
  net: {
    fetch: (id, url, method, headers, body) => ipcRenderer.invoke('net-fetch', { id, url, method, headers, body }),
    abort: (id) => ipcRenderer.send('net-abort', id),
  },
});
