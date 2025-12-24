const { app, BrowserWindow, Menu, ipcMain, Notification, safeStorage } = require('electron');
const path = require('path');
const fs = require('fs');
const isDev = process.env.NODE_ENV === 'development';

let mainWindow;
let nextServer = null;

// Single instance lock - 한 번에 하나의 앱 인스턴스만 실행
const gotTheLock = app.requestSingleInstanceLock();

if (!gotTheLock) {
  // 이미 실행 중인 인스턴스가 있으면 종료
  app.quit();
} else {
  // 두 번째 인스턴스가 실행되려고 하면 첫 번째 인스턴스 창에 포커스
  app.on('second-instance', (event, commandLine, workingDirectory) => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) {
        mainWindow.restore();
      }
      mainWindow.focus();
      mainWindow.show();
    }
  });
}

async function startNextServer() {
  if (isDev) return; // 개발 모드에서는 별도로 Next.js 서버를 실행

  try {
    const next = require('next');
    const nextApp = next({
      dev: false,
      dir: path.join(__dirname, '..'),
    });

    await nextApp.prepare();
    const handle = nextApp.getRequestHandler();

    const { createServer } = require('http');
    nextServer = createServer((req, res) => {
      handle(req, res);
    });

    await new Promise((resolve, reject) => {
      nextServer.listen(3000, (err) => {
        if (err) reject(err);
        else resolve();
      });
    });

    console.log('> Next.js server started on http://localhost:3000');
  } catch (error) {
    console.error('Failed to start Next.js server:', error);
    throw error;
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1400,
    height: 900,
    minWidth: 800,
    minHeight: 600,
    show: false, // 처음엔 숨김 - ready-to-show 이벤트에서 표시
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: true,
    },
    icon: path.join(__dirname, '../public/chat_logo.png'),
    title: 'Messenger Platform',
  });

  // 창이 준비되면 표시
  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    if (isDev) {
      mainWindow.webContents.openDevTools();
    }
  });

  // 개발/프로덕션 모두 Next.js 서버에 연결
  mainWindow.loadURL('http://localhost:3000');

  // 메뉴바 제거
  Menu.setApplicationMenu(null);

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

// 커스텀 알림 창 관리
let customNotifications = [];

function createCustomNotification(data) {
  const { title, body, workspaceId, channelId, chatroomId } = data;

  // 화면 크기 가져오기
  const { screen } = require('electron');
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;

  // 기존 알림 개수에 따라 Y 위치 계산
  const notificationHeight = 205;
  const spacing = 1;
  const yPosition = height - (customNotifications.length + 1) * (notificationHeight + spacing) - 20;

  const notificationWindow = new BrowserWindow({
    width: 400,
    height: notificationHeight,
    x: width - 420,
    y: yPosition,
    frame: false,
    transparent: true,
    alwaysOnTop: true,
    skipTaskbar: true,
    resizable: false,
    show: false,
    webPreferences: {
      nodeIntegration: true,
      contextIsolation: false,
    },
  });

  // 개발/프로덕션 환경에 따라 경로 처리
  let notificationPath;
  if (isDev) {
    notificationPath = path.join(__dirname, 'notification.html');
  } else {
    // 프로덕션 환경에서 여러 경로 시도
    const possiblePaths = [
      path.join(process.resourcesPath, 'app.asar.unpacked', 'electron', 'notification.html'),
      path.join(app.getAppPath(), 'electron', 'notification.html'),
      path.join(__dirname, 'notification.html'),
    ];

    for (const tryPath of possiblePaths) {
      if (fs.existsSync(tryPath)) {
        notificationPath = tryPath;
        break;
      }
    }
  }

  console.log('Loading notification from:', notificationPath);

  if (!notificationPath || !fs.existsSync(notificationPath)) {
    console.error('❌ Notification HTML file not found!');
    console.error('Tried paths:', isDev ? [path.join(__dirname, 'notification.html')] : [
      path.join(process.resourcesPath, 'app.asar.unpacked', 'electron', 'notification.html'),
      path.join(app.getAppPath(), 'electron', 'notification.html'),
      path.join(__dirname, 'notification.html'),
    ]);
    return null;
  }

  notificationWindow.loadFile(notificationPath);

  notificationWindow.once('ready-to-show', () => {
    notificationWindow.show();
    notificationWindow.webContents.send('notification-data', { title, body, workspaceId, channelId, chatroomId });
  });

  // 알림 목록에 추가
  customNotifications.push(notificationWindow);

  // 창 닫힐 때 목록에서 제거
  notificationWindow.on('closed', () => {
    const index = customNotifications.indexOf(notificationWindow);
    if (index > -1) {
      customNotifications.splice(index, 1);
    }
    repositionNotifications();
  });

  return notificationWindow;
}

// 알림 위치 재조정
function repositionNotifications() {
  const { screen } = require('electron');
  const primaryDisplay = screen.getPrimaryDisplay();
  const { width, height } = primaryDisplay.workAreaSize;
  const notificationHeight = 205;
  const spacing = 1;

  customNotifications.forEach((notification, index) => {
    if (!notification.isDestroyed()) {
      const yPosition = height - (index + 1) * (notificationHeight + spacing) - 20;
      notification.setPosition(width - 420, yPosition);
    }
  });
}

// IPC 핸들러: 커스텀 알림 닫기
ipcMain.on('close-custom-notification', (event) => {
  const window = BrowserWindow.fromWebContents(event.sender);
  if (window) {
    window.close();
  }
});

// IPC 핸들러: 알림 클릭
ipcMain.on('notification-clicked', (event, data) => {
  const window = BrowserWindow.fromWebContents(event.sender);
  if (window) {
    window.close();
  }

  // 메인 윈도우로 이동
  if (mainWindow) {
    if (mainWindow.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow.focus();
    mainWindow.show();
    mainWindow.webContents.send('navigate-to-mention', data);
  }
});

// IPC 핸들러 설정
ipcMain.on('show-notification', (event, data) => {
  console.log('📢 Received notification request:', data);
  const { title, body, workspaceId, channelId, chatroomId } = data;

  // 커스텀 알림 사용 (기본값)
  const useCustomNotification = true;

  if (useCustomNotification) {
    // 커스텀 알림 창 표시
    console.log('🎨 Creating custom notification...');
    createCustomNotification(data);
    console.log('✅ Custom notification shown');
  } else {
    // 네이티브 알림 사용
    if (!Notification.isSupported()) {
      console.warn('⚠️ Notifications are not supported on this system');
      return;
    }

    console.log('✅ Notifications are supported, creating notification...');

    try {
      const notification = new Notification({
        title: title,
        body: body,
        icon: path.join(__dirname, '../public/chat_logo.png'),
        silent: false,
        timeoutType: 'never', // X 누를 때까지 사라지지 않음
        urgency: 'critical',
      });

      console.log('🔔 Notification created, showing...');

      // 알림 클릭 시 윈도우 포커스 및 해당 페이지로 이동
      notification.on('click', () => {
        console.log('👆 Notification clicked');
        if (mainWindow) {
          if (mainWindow.isMinimized()) {
            mainWindow.restore();
          }
          mainWindow.focus();
          mainWindow.show();

          // 해당 채널/채팅방으로 이동
          mainWindow.webContents.send('navigate-to-mention', { workspaceId, channelId, chatroomId });
        }
      });

      notification.on('show', () => {
        console.log('✅ Notification shown successfully');
      });

      notification.on('failed', (event, error) => {
        console.error('❌ Notification failed:', error);
      });

      notification.show();
    } catch (error) {
      console.error('❌ Error creating notification:', error);
    }
  }
});

// 윈도우 포커스 요청
ipcMain.on('focus-window', () => {
  if (mainWindow) {
    if (mainWindow.isMinimized()) {
      mainWindow.restore();
    }
    mainWindow.focus();
  }
});

// Credential 저장 경로
const getCredentialPath = () => {
  return path.join(app.getPath('userData'), 'credentials.enc');
};

// Credential 저장
ipcMain.handle('save-credentials', async (event, credentials) => {
  try {
    if (!safeStorage.isEncryptionAvailable()) {
      throw new Error('Encryption is not available on this system');
    }

    const buffer = safeStorage.encryptString(JSON.stringify(credentials));
    const credentialPath = getCredentialPath();

    fs.writeFileSync(credentialPath, buffer);
    return { success: true };
  } catch (error) {
    console.error('Failed to save credentials:', error);
    return { success: false, error: error.message };
  }
});

// Credential 불러오기
ipcMain.handle('load-credentials', async () => {
  try {
    const credentialPath = getCredentialPath();

    if (!fs.existsSync(credentialPath)) {
      return { success: true, credentials: null };
    }

    if (!safeStorage.isEncryptionAvailable()) {
      throw new Error('Encryption is not available on this system');
    }

    const buffer = fs.readFileSync(credentialPath);
    const credentials = JSON.parse(safeStorage.decryptString(buffer));

    return { success: true, credentials };
  } catch (error) {
    console.error('Failed to load credentials:', error);
    return { success: false, error: error.message, credentials: null };
  }
});

// Credential 삭제
ipcMain.handle('delete-credentials', async () => {
  try {
    const credentialPath = getCredentialPath();

    if (fs.existsSync(credentialPath)) {
      fs.unlinkSync(credentialPath);
    }

    return { success: true };
  } catch (error) {
    console.error('Failed to delete credentials:', error);
    return { success: false, error: error.message };
  }
});

app.whenReady().then(async () => {
  // Windows에서 알림이 제대로 표시되도록 App User Model ID 설정
  if (process.platform === 'win32') {
    app.setAppUserModelId('com.messengerplatform.app');
    console.log('✅ App User Model ID set for Windows notifications');
  }

  await startNextServer();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    if (nextServer) {
      nextServer.close();
    }
    app.quit();
  }
});

app.on('before-quit', () => {
  if (nextServer) {
    nextServer.close();
  }
});

// 외부 링크는 기본 브라우저에서 열기
app.on('web-contents-created', (event, contents) => {
  contents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http:') || url.startsWith('https:')) {
      require('electron').shell.openExternal(url);
      return { action: 'deny' };
    }
    return { action: 'allow' };
  });
});
