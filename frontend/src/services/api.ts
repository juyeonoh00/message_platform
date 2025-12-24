import axios from 'axios'
import { store } from '@/store'
import { updateTokens } from '@/store/slices/authSlice'

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080'

const api = axios.create({
  baseURL: `${API_URL}/api`,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Token refresh timer
let tokenRefreshTimer: NodeJS.Timeout | null = null

// JWT 토큰 디코딩 함수 (만료 시간 확인용)
const getTokenExpiration = (token: string): number | null => {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    const payload = JSON.parse(jsonPayload)
    return payload.exp * 1000 // Convert to milliseconds
  } catch (error) {
    console.error('Failed to decode token:', error)
    return null
  }
}

// 토큰 만료 전 자동 갱신 설정
export const setupTokenRefreshTimer = () => {
  // 기존 타이머 제거
  if (tokenRefreshTimer) {
    clearTimeout(tokenRefreshTimer)
    tokenRefreshTimer = null
  }

  if (typeof window === 'undefined') return

  const accessToken = localStorage.getItem('accessToken')
  if (!accessToken) return

  const expirationTime = getTokenExpiration(accessToken)
  if (!expirationTime) return

  const now = Date.now()
  const timeUntilExpiration = expirationTime - now

  // 만료 5분 전에 갱신 (또는 이미 만료되었으면 즉시 갱신)
  const refreshBeforeExpiration = 5 * 60 * 1000 // 5 minutes
  const timeUntilRefresh = Math.max(0, timeUntilExpiration - refreshBeforeExpiration)

  console.log(`⏰ Token will be refreshed in ${Math.floor(timeUntilRefresh / 1000 / 60)} minutes`)

  tokenRefreshTimer = setTimeout(async () => {
    console.log('🔄 Proactively refreshing token before expiration...')
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      // 갱신 성공 시 다음 갱신 타이머 설정
      setupTokenRefreshTimer()
    }
  }, timeUntilRefresh)
}

// 토큰 갱신 타이머 중지
export const clearTokenRefreshTimer = () => {
  if (tokenRefreshTimer) {
    clearTimeout(tokenRefreshTimer)
    tokenRefreshTimer = null
  }
}

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem('accessToken')
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor to handle token refresh
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        const refreshToken = localStorage.getItem('refreshToken')
        if (!refreshToken) {
          // No refresh token, redirect to login
          localStorage.removeItem('accessToken')
          localStorage.removeItem('refreshToken')
          localStorage.removeItem('user')
          window.location.href = '/login'
          return Promise.reject(error)
        }

        const response = await axios.post(`${API_URL}/api/auth/refresh`, {
          refreshToken,
        })

        const { accessToken, refreshToken: newRefreshToken } = response.data

        // Update Redux store (this will trigger WebSocket reconnection)
        store.dispatch(updateTokens({
          accessToken,
          refreshToken: newRefreshToken,
        }))

        originalRequest.headers.Authorization = `Bearer ${accessToken}`
        return api(originalRequest)
      } catch (refreshError) {
        // Refresh failed, logout user and redirect to login
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')
        window.location.href = '/login'
        return Promise.reject(refreshError)
      }
    }

    return Promise.reject(error)
  }
)

// Token refresh helper - can be used by WebSocket service
export const refreshAccessToken = async (): Promise<boolean> => {
  try {
    const refreshToken = typeof window !== 'undefined' ? localStorage.getItem('refreshToken') : null
    if (!refreshToken) {
      console.error('No refresh token available')
      clearTokenRefreshTimer()
      if (typeof window !== 'undefined') {
        window.location.href = '/login'
      }
      return false
    }

    const response = await axios.post(`${API_URL}/api/auth/refresh`, {
      refreshToken,
    })

    const { accessToken, refreshToken: newRefreshToken } = response.data

    // Update Redux store (this will trigger setupTokenRefreshTimer in providers.tsx)
    store.dispatch(updateTokens({
      accessToken,
      refreshToken: newRefreshToken,
    }))

    console.log('✅ Token refreshed successfully')
    return true
  } catch (error) {
    console.error('❌ Failed to refresh token:', error)
    // Clear tokens and redirect to login
    clearTokenRefreshTimer()
    if (typeof window !== 'undefined') {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('user')
      window.location.href = '/login'
    }
    return false
  }
}

// Auth APIs
export const authAPI = {
  register: (data: { email: string; password: string; name: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string }) =>
    api.post('/auth/login', data),
  refresh: (refreshToken: string) =>
    api.post('/auth/refresh', { refreshToken }),
}

// Workspace APIs
export const workspaceAPI = {
  getAll: () => api.get('/workspaces'),
  getById: (id: number) => api.get(`/workspaces/${id}`),
  create: (data: { name: string; description?: string }) =>
    api.post('/workspaces', data),
  getMembers: (workspaceId: number) =>
    api.get(`/workspaces/${workspaceId}/members`),
  addMember: (workspaceId: number, data: { email: string; role?: string }) =>
    api.post(`/workspaces/${workspaceId}/members`, data),
  removeMember: (workspaceId: number, memberUserId: number) =>
    api.delete(`/workspaces/${workspaceId}/members/${memberUserId}`),
}

// Channel APIs
export const channelAPI = {
  getByWorkspace: (workspaceId: number) =>
    api.get(`/channels/workspace/${workspaceId}`),
  create: (data: {
    workspaceId: number
    name: string
    description?: string
    isPrivate?: boolean
  }) => api.post('/channels', data),
  join: (channelId: number) => api.post(`/channels/${channelId}/join`),
  leave: (channelId: number) => api.post(`/channels/${channelId}/leave`),
  addMember: (channelId: number, userId: number) =>
    api.post(`/channels/${channelId}/members`, { userId }),
  delete: (channelId: number) => api.delete(`/channels/${channelId}`),
  updateName: (channelId: number, name: string) =>
    api.put(`/channels/${channelId}/name`, { name }),
  removeMember: (channelId: number, userId: number) =>
    api.delete(`/channels/${channelId}/members/${userId}`),
  updatePrivacy: (channelId: number, isPrivate: boolean) =>
    api.put(`/channels/${channelId}/privacy`, { isPrivate }),
  getMembers: (channelId: number) =>
    api.get(`/channels/${channelId}/members`),
  updateMemberRole: (channelId: number, userId: number, role: string) =>
    api.put(`/channels/${channelId}/members/${userId}/role`, { role }),
}

// Chatroom APIs (Direct Messages)
export const chatroomAPI = {
  create: (data: { workspaceId: number; targetUserId: number }) =>
    api.post('/chatrooms', data),
  getByWorkspace: (workspaceId: number) =>
    api.get(`/chatrooms/workspace/${workspaceId}`),
  getById: (chatroomId: number) => api.get(`/chatrooms/${chatroomId}`),
  delete: (chatroomId: number) => api.delete(`/chatrooms/${chatroomId}`),
  hide: (chatroomId: number) => api.post(`/chatrooms/${chatroomId}/hide`),
}

// Message APIs (Channel Messages)
export const messageAPI = {
  getByChannel: (channelId: number, page: number = 0, size: number = 50) =>
    api.get(`/messages/channel/${channelId}?page=${page}&size=${size}`),
  getThreadReplies: (parentMessageId: number) =>
    api.get(`/messages/thread/${parentMessageId}`),
  send: (data: {
    workspaceId: number
    channelId: number
    content: string
    parentMessageId?: number
    mentionedUserIds?: number[]
    mentionTypes?: string[]
  }) => api.post('/messages', data),
  update: (messageId: number, content: string) =>
    api.put(`/messages/${messageId}`, { content }),
  delete: (messageId: number) => api.delete(`/messages/${messageId}`),
  updateReadState: (data: { channelId: number; lastReadMessageId: number }) =>
    api.post('/messages/read', data),
  toggleReaction: (messageId: number, emoji: string) =>
    api.post(`/messages/${messageId}/reactions`, { emoji }),
  getReactions: (messageId: number) =>
    api.get(`/messages/${messageId}/reactions`),
  getReactionsBulk: (messageIds: number[]) =>
    api.post('/messages/reactions/bulk', messageIds),
}

// Chatroom Message APIs
export const chatroomMessageAPI = {
  getByChatroom: (chatroomId: number, page: number = 0, size: number = 50) =>
    api.get(`/chatroom-messages/chatroom/${chatroomId}?page=${page}&size=${size}`),
  send: (data: {
    workspaceId: number
    chatroomId: number
    content: string
    mentionedUserIds?: number[]
    mentionTypes?: string[]
  }) => api.post('/chatroom-messages', data),
  update: (messageId: number, content: string) =>
    api.put(`/chatroom-messages/${messageId}`, { content }),
  delete: (messageId: number) => api.delete(`/chatroom-messages/${messageId}`),
  updateReadState: (data: { chatroomId: number; lastReadMessageId: number }) =>
    api.post('/chatroom-messages/read', data),
}

// Search APIs
export const searchAPI = {
  search: (data: {
    workspaceId: number
    channelId?: number
    keyword?: string
    startTime?: string
    endTime?: string
  }) => api.post('/search', data),
}

// User APIs
export const userAPI = {
  getCurrentUser: () => api.get('/users/me'),
  updateProfile: (data: { name: string; avatarUrl?: string; status?: string }) =>
    api.put('/users/me', data),
  updateProfileWithAvatar: (name: string, status: string, avatarFile: File | null) => {
    const formData = new FormData()
    formData.append('name', name)
    formData.append('status', status)
    if (avatarFile) {
      formData.append('avatar', avatarFile)
    }
    return api.put('/users/me', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },
  getUserById: (userId: number) => api.get(`/users/${userId}`),
}

// AI Chatbot APIs
export const aiAPI = {
  chat: (data: { question: string; topK?: number; llmType?: string }) =>
    api.post('/ai/chat', data),
  getHistory: () => api.get('/ai/history'),
}

// Document APIs
export const documentAPI = {
  getCategoriesWithDocuments: (workspaceId: number) =>
    api.get(`/documents/workspace/${workspaceId}`),
  createCategory: (data: { name: string; workspaceId: number }) =>
    api.post('/documents/categories', data),
  deleteCategory: (categoryId: number) =>
    api.delete(`/documents/categories/${categoryId}`),
  uploadDocument: (categoryId: number, workspaceId: number, file: File) => {
    const formData = new FormData()
    formData.append('categoryId', categoryId.toString())
    formData.append('workspaceId', workspaceId.toString())
    formData.append('file', file)
    return api.post('/documents/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },
  downloadDocument: (documentId: number) =>
    api.get(`/documents/${documentId}/download`, {
      responseType: 'blob',
    }),
  deleteDocument: (documentId: number) =>
    api.delete(`/documents/${documentId}`),
  getDocument: (documentId: number) =>
    api.get(`/documents/${documentId}`),
}

// Notification APIs
export const notificationAPI = {
  getNotifications: (workspaceId: number) =>
    api.get(`/notifications/workspace/${workspaceId}`),
  getUnreadNotifications: (workspaceId: number) =>
    api.get(`/notifications/workspace/${workspaceId}/unread`),
  getUnreadCount: (workspaceId: number) =>
    api.get(`/notifications/workspace/${workspaceId}/unread/count`),
  markAsRead: (notificationId: number) =>
    api.put(`/notifications/${notificationId}/read`),
  markAllAsRead: (workspaceId: number) =>
    api.put(`/notifications/workspace/${workspaceId}/read-all`),
}

export default api
