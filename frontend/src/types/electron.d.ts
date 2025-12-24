// Electron API 타입 정의
interface ElectronAPI {
  platform: string;
  versions: {
    node: string;
    chrome: string;
    electron: string;
  };
  notification: {
    show: (data: {
      title: string;
      body: string;
      workspaceId: number;
      channelId?: number;
      chatroomId?: number;
    }) => void;
  };
  focusWindow: () => void;
  onNavigateToMention: (callback: (data: {
    workspaceId: number;
    channelId?: number;
    chatroomId?: number;
  }) => void) => void;
  credentials: {
    save: (credentials: { email: string; password: string }) => Promise<{ success: boolean; error?: string }>;
    load: () => Promise<{ success: boolean; credentials: { email: string; password: string } | null; error?: string }>;
    delete: () => Promise<{ success: boolean; error?: string }>;
  };
}

interface Window {
  electron?: ElectronAPI;
  env?: {
    isDev: boolean;
  };
}
