// Puente seguro entre la interfaz y el proceso principal
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('vtNative', {
  mintPot: (id) => ipcRenderer.invoke('mint-pot', id),
});
