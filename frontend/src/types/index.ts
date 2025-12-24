export interface User {
  id: number
  email: string
  name: string
  avatarUrl?: string
  status?: string
}

export interface AuthState {
  user: User | null
  accessToken: string | null
  refreshToken: string | null
  isAuthenticated: boolean
  isInitialized: boolean
}

export interface Workspace {
  id: number
  name: string
  description?: string
  ownerId: number
  role: string
  createdAt: string
}

export interface WorkspaceMember {
  id: number
  userId: number
  userName: string
  userEmail: string
  role: string
}

export interface Channel {
  id: number
  workspaceId: number
  name: string
  description?: string
  isPrivate: boolean
  createdBy: number
  isMember: boolean
  unreadCount: number
  createdAt: string
}

export interface MentionInfo {
  userId?: number
  mentionType: string
}

export interface Message {
  id: number
  workspaceId: number
  channelId: number
  userId: number
  userName: string
  userAvatarUrl?: string
  content: string
  parentMessageId?: number
  replyCount: number
  isEdited: boolean
  mentions?: MentionInfo[]
  reactions?: { [emoji: string]: number[] }
  createdAt: string
  updatedAt: string
  editedAt?: string
}

export interface TypingIndicator {
  userId: number
  userName: string
  channelId: number
  isTyping: boolean
}

export interface WebSocketMessage {
  type: string
  workspaceId: number
  channelId: number
  payload: any
  timestamp: string
}

export interface MessageDocument {
  text: string | null
  metadata: {
    accountNo?: string
    channelId?: number
    chunkId?: string
    threadId?: number
    documentId?: number
    userName?: string
    channelName?: string
    createdAt?: string
  }
}

export interface SearchResponse {
  results: MessageDocument[]
  totalCount: number
}

export interface SearchRequest {
  workspaceId: number
  channelId?: number
  keyword?: string
  startTime?: string
  endTime?: string
}

export interface Notification {
  id: number
  userId: number
  workspaceId: number
  type: string
  content: string
  channelId?: number
  chatroomId?: number
  messageId?: number
  senderId: number
  senderName: string
  senderAvatarUrl?: string
  isRead: boolean
  createdAt: string
  readAt?: string
}
