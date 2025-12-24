const { contextBridge, ipcRenderer } = require('electron');

// Electron API를 안전하게 웹 페이지에 노출
contextBridge.exposeInMainWorld('electron', {
  // 필요한 경우 여기에 Electron API를 추가
  platform: process.platform,
  versions: {
    node: process.versions.node,
    chrome: process.versions.chrome,
    electron: process.versions.electron,
  },
  // 알림 기능
  notification: {
    show: (data) => {
      ipcRenderer.send('show-notification', data);
    },
  },
  // 윈도우 포커스
  focusWindow: () => {
    ipcRenderer.send('focus-window');
  },
  // 알림 클릭 시 네비게이션 이벤트 수신
  onNavigateToMention: (callback) => {
    ipcRenderer.on('navigate-to-mention', (event, data) => callback(data));
  },
  // Credential 관리
  credentials: {
    save: (credentials) => ipcRenderer.invoke('save-credentials', credentials),
    load: () => ipcRenderer.invoke('load-credentials'),
    delete: () => ipcRenderer.invoke('delete-credentials'),
  },
});

// 개발 환경 체크
contextBridge.exposeInMainWorld('env', {
  isDev: process.env.NODE_ENV === 'development',
});
