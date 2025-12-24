import { Client, StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { Message, TypingIndicator, WebSocketMessage } from '@/types'
import { refreshAccessToken } from './api'

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8080/ws'

class WebSocketService {
  private client: Client | null = null
  private subscriptions: Map<string, StompSubscription> = new Map()
  private subscribedDestinations: Set<string> = new Set() // Track what we should be subscribed to
  private messageCallbacks: ((message: Message) => void)[] = []
  private chatroomMessageCallbacks: ((message: any) => void)[] = []
  private messageUpdateCallbacks: ((message: Message) => void)[] = []
  private messageDeleteCallbacks: ((data: { channelId: number; messageId: number }) => void)[] = []
  private typingCallbacks: ((indicator: TypingIndicator) => void)[] = []
  private mentionCallbacks: ((data: WebSocketMessage) => void)[] = []
  private isConnected: boolean = false
  private isRefreshingToken: boolean = false // Prevent multiple token refresh attempts

  connect(token: string): Promise<void> {
    console.log('🔌 Initializing WebSocket connection to:', WS_URL)
    console.log('🔑 Using token:', token ? 'Token present' : 'No token')

    return new Promise((resolve, reject) => {
      this.client = new Client({
        webSocketFactory: () => {
          console.log('🏭 Creating SockJS connection...')
          return new SockJS(WS_URL)
        },
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        // Use latest token from localStorage on reconnect
        beforeConnect: () => {
          const latestToken = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null
          if (latestToken && this.client) {
            console.log('🔄 Using latest token for reconnection')
            this.client.connectHeaders = {
              Authorization: `Bearer ${latestToken}`,
            }
          }
        },
        debug: (str) => {
          console.log('📡 STOMP:', str)
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
      })

      this.client.onConnect = () => {
        console.log('✅ WebSocket Connected Successfully!')
        this.isConnected = true

        // Resubscribe to all previous destinations after reconnection
        if (this.subscribedDestinations.size > 0) {
          console.log('🔄 Resubscribing to', this.subscribedDestinations.size, 'destinations after reconnection')
          this.resubscribeAll()
        }

        resolve()
      }

      this.client.onDisconnect = () => {
        console.warn('⚠️ WebSocket Disconnected')
        this.isConnected = false
      }

      this.client.onStompError = async (frame) => {
        console.error('❌ STOMP Error:', frame)
        console.error('Error headers:', frame.headers)
        console.error('Error body:', frame.body)
        this.isConnected = false

        // Check if it's an authentication error
        const errorMessage = frame.headers['message'] || frame.body || ''
        const isAuthError = errorMessage.includes('Invalid') ||
                           errorMessage.includes('expired') ||
                           errorMessage.includes('Authorization') ||
                           errorMessage.includes('token')

        if (isAuthError && !this.isRefreshingToken) {
          console.log('🔄 Authentication error detected, attempting to refresh token...')
          this.isRefreshingToken = true

          try {
            const refreshed = await refreshAccessToken()
            if (refreshed) {
              console.log('✅ Token refreshed, reconnecting WebSocket...')
              this.isRefreshingToken = false

              // Get the new token and reconnect
              const newToken = typeof window !== 'undefined' ? localStorage.getItem('accessToken') : null
              if (newToken) {
                // Deactivate current client
                if (this.client) {
                  this.client.deactivate()
                }

                // Reconnect with new token after a short delay
                setTimeout(() => {
                  this.connect(newToken).catch(err => {
                    console.error('❌ Reconnection failed:', err)
                  })
                }, 1000)
                return
              }
            }
          } catch (error) {
            console.error('❌ Token refresh failed:', error)
          }

          this.isRefreshingToken = false
        }

        reject(new Error(frame.headers['message'] || 'WebSocket connection failed'))
      }

      this.client.onWebSocketError = (error) => {
        console.error('❌ WebSocket Error:', error)
        this.isConnected = false
      }

      this.client.onWebSocketClose = (event) => {
        console.warn('🔌 WebSocket Closed:', event)
        this.isConnected = false
      }

      console.log('🚀 Activating WebSocket client...')
      this.client.activate()
    })
  }

  disconnect() {
    this.subscriptions.forEach((sub) => sub.unsubscribe())
    this.subscriptions.clear()
    this.subscribedDestinations.clear()
    this.messageCallbacks = []
    this.chatroomMessageCallbacks = []
    this.messageUpdateCallbacks = []
    this.messageDeleteCallbacks = []
    this.typingCallbacks = []
    this.mentionCallbacks = []

    if (this.client) {
      this.client.deactivate()
      this.client = null
    }

    this.isConnected = false
  }

  private resubscribeAll() {
    console.log('🔄 Resubscribing to all destinations...')

    // Clear old subscriptions
    this.subscriptions.clear()

    // Resubscribe to each tracked destination
    this.subscribedDestinations.forEach((destination) => {
      console.log('📝 Resubscribing to:', destination)

      try {
        let subscription: StompSubscription | undefined

        // Channel messages
        if (destination.startsWith('/topic/channel/') && !destination.includes('/typing')) {
          subscription = this.client?.subscribe(destination, (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body)
            if (wsMessage.type === 'MESSAGE') {
              this.messageCallbacks.forEach((callback) => callback(wsMessage.payload.message))
            } else if (wsMessage.type === 'MESSAGE_UPDATED') {
              this.messageUpdateCallbacks.forEach((callback) => callback(wsMessage.payload.message))
            } else if (wsMessage.type === 'MESSAGE_DELETED') {
              this.messageDeleteCallbacks.forEach((callback) =>
                callback({ channelId: wsMessage.payload.channelId, messageId: wsMessage.payload.messageId })
              )
            }
          })
        }
        // Channel typing indicators
        else if (destination.includes('/channel/') && destination.includes('/typing')) {
          subscription = this.client?.subscribe(destination, (message) => {
            const indicator: TypingIndicator = JSON.parse(message.body)
            this.typingCallbacks.forEach((callback) => callback(indicator))
          })
        }
        // Chatroom messages
        else if (destination.startsWith('/topic/chatroom/') && !destination.includes('/typing')) {
          subscription = this.client?.subscribe(destination, (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body)
            if (wsMessage.type === 'CHATROOM_MESSAGE') {
              this.chatroomMessageCallbacks.forEach((callback) => callback(wsMessage.payload.message))
            } else if (wsMessage.type === 'MESSAGE_UPDATED') {
              this.messageUpdateCallbacks.forEach((callback) => callback(wsMessage.payload.message))
            } else if (wsMessage.type === 'MESSAGE_DELETED') {
              this.messageDeleteCallbacks.forEach((callback) =>
                callback({ channelId: wsMessage.payload.channelId, messageId: wsMessage.payload.messageId })
              )
            }
          })
        }
        // Chatroom typing indicators
        else if (destination.includes('/chatroom/') && destination.includes('/typing')) {
          subscription = this.client?.subscribe(destination, (message) => {
            const indicator: TypingIndicator = JSON.parse(message.body)
            this.typingCallbacks.forEach((callback) => callback(indicator))
          })
        }
        // Mentions
        else if (destination === '/user/queue/mentions') {
          subscription = this.client?.subscribe(destination, (message) => {
            const wsMessage: WebSocketMessage = JSON.parse(message.body)
            this.mentionCallbacks.forEach((callback) => callback(wsMessage))
          })
        }

        if (subscription) {
          this.subscriptions.set(destination, subscription)
          console.log('✅ Resubscribed to:', destination)
        }
      } catch (error) {
        console.error('❌ Failed to resubscribe to:', destination, error)
      }
    })
  }

  getConnectionStatus(): boolean {
    return this.isConnected && this.client !== null && this.client.connected
  }

  subscribeToChannel(channelId: number) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping subscription to channel:', channelId)
      return
    }

    const destination = `/topic/channel/${channelId}`

    if (this.subscriptions.has(destination)) {
      return // Already subscribed
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        console.log(`📨 Received on ${destination}:`, message.body)
        const wsMessage: WebSocketMessage = JSON.parse(message.body)

        if (wsMessage.type === 'MESSAGE') {
          const msg: Message = wsMessage.payload.message
          console.log('💬 New message:', msg)
          this.messageCallbacks.forEach((callback) => callback(msg))
        } else if (wsMessage.type === 'MESSAGE_UPDATED') {
          // 메시지 수정 이벤트 처리
          const msg: Message = wsMessage.payload.message
          console.log('✏️ Message updated:', msg)
          this.messageUpdateCallbacks.forEach((callback) => callback(msg))
        } else if (wsMessage.type === 'MESSAGE_DELETED') {
          // 메시지 삭제 이벤트 처리
          const { channelId: deletedChannelId, messageId } = wsMessage.payload
          console.log('🗑️ Message deleted:', messageId)
          this.messageDeleteCallbacks.forEach((callback) => callback({ channelId: deletedChannelId, messageId }))
        }
      })

      this.subscriptions.set(destination, subscription)
      this.subscribedDestinations.add(destination) // Track for reconnection
      console.log(`✅ Subscribed to channel ${channelId}`)
    } catch (error) {
      console.error('❌ Failed to subscribe to channel:', error)
    }
  }

  unsubscribeFromChannel(channelId: number) {
    const destination = `/topic/channel/${channelId}`
    const subscription = this.subscriptions.get(destination)

    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      this.subscribedDestinations.delete(destination) // Remove from tracking
      console.log(`Unsubscribed from channel ${channelId}`)
    }
  }

  subscribeToChatroom(chatroomId: number) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping subscription to chatroom:', chatroomId)
      return
    }

    const destination = `/topic/chatroom/${chatroomId}`

    if (this.subscriptions.has(destination)) {
      return // Already subscribed
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        console.log(`📨 Received on ${destination}:`, message.body)
        const wsMessage: WebSocketMessage = JSON.parse(message.body)

        if (wsMessage.type === 'CHATROOM_MESSAGE') {
          const msg = wsMessage.payload.message
          console.log('💬 New chatroom message:', msg)
          this.chatroomMessageCallbacks.forEach((callback) => callback(msg))
        } else if (wsMessage.type === 'MESSAGE_UPDATED') {
          // 채팅방 메시지 수정 이벤트 처리
          const msg: Message = wsMessage.payload.message
          console.log('✏️ Chatroom message updated:', msg)
          this.messageUpdateCallbacks.forEach((callback) => callback(msg))
        } else if (wsMessage.type === 'MESSAGE_DELETED') {
          // 채팅방 메시지 삭제 이벤트 처리
          const { channelId: deletedChannelId, messageId } = wsMessage.payload
          console.log('🗑️ Chatroom message deleted:', messageId)
          this.messageDeleteCallbacks.forEach((callback) => callback({ channelId: deletedChannelId, messageId }))
        }
      })

      this.subscriptions.set(destination, subscription)
      this.subscribedDestinations.add(destination) // Track for reconnection
      console.log(`✅ Subscribed to chatroom ${chatroomId}`)
    } catch (error) {
      console.error('❌ Failed to subscribe to chatroom:', error)
    }
  }

  unsubscribeFromChatroom(chatroomId: number) {
    const destination = `/topic/chatroom/${chatroomId}`
    const subscription = this.subscriptions.get(destination)

    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      this.subscribedDestinations.delete(destination) // Remove from tracking
      console.log(`Unsubscribed from chatroom ${chatroomId}`)
    }
  }

  subscribeToTyping(channelId: number) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping typing subscription for channel:', channelId)
      return
    }

    const destination = `/topic/channel/${channelId}/typing`

    if (this.subscriptions.has(destination)) {
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        const indicator: TypingIndicator = JSON.parse(message.body)
        this.typingCallbacks.forEach((callback) => callback(indicator))
      })

      this.subscriptions.set(destination, subscription)
      this.subscribedDestinations.add(destination) // Track for reconnection
    } catch (error) {
      console.error('Failed to subscribe to typing:', error)
    }
  }

  subscribeToChatroomTyping(chatroomId: number) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping typing subscription for chatroom:', chatroomId)
      return
    }

    const destination = `/topic/chatroom/${chatroomId}/typing`

    if (this.subscriptions.has(destination)) {
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        const indicator: TypingIndicator = JSON.parse(message.body)
        this.typingCallbacks.forEach((callback) => callback(indicator))
      })

      this.subscriptions.set(destination, subscription)
      this.subscribedDestinations.add(destination) // Track for reconnection
      console.log(`✅ Subscribed to chatroom typing ${chatroomId}`)
    } catch (error) {
      console.error('Failed to subscribe to chatroom typing:', error)
    }
  }

  unsubscribeFromChatroomTyping(chatroomId: number) {
    const destination = `/topic/chatroom/${chatroomId}/typing`
    const subscription = this.subscriptions.get(destination)

    if (subscription) {
      subscription.unsubscribe()
      this.subscriptions.delete(destination)
      this.subscribedDestinations.delete(destination) // Remove from tracking
      console.log(`Unsubscribed from chatroom typing ${chatroomId}`)
    }
  }

  subscribeToMentions(userId: number) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping mentions subscription for user:', userId)
      return
    }

    const destination = `/user/queue/mentions`

    if (this.subscriptions.has(destination)) {
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      const subscription = this.client.subscribe(destination, (message) => {
        const wsMessage: WebSocketMessage = JSON.parse(message.body)
        this.mentionCallbacks.forEach((callback) => callback(wsMessage))
      })

      this.subscriptions.set(destination, subscription)
      this.subscribedDestinations.add(destination) // Track for reconnection
    } catch (error) {
      console.error('Failed to subscribe to mentions:', error)
    }
  }

  sendMessage(data: {
    workspaceId: number
    channelId: number
    content: string
    parentMessageId?: number
    mentionedUserIds?: number[]
    mentionTypes?: string[]
  }) {
    if (!this.getConnectionStatus()) {
      console.error('❌ WebSocket not connected. Cannot send message.')
      // 연결이 끊어진 경우 자동으로 로그인 페이지로 리다이렉트
      if (typeof window !== 'undefined') {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')
        window.location.href = '/login'
      }
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      console.log('📤 Sending message to /app/chat.sendMessage:', data)
      this.client.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify(data),
      })
      console.log('✅ Message sent successfully')
    } catch (error) {
      console.error('❌ Failed to send message:', error)
    }
  }

  sendTypingIndicator(channelId: number, isTyping: boolean) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping typing indicator.')
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      this.client.publish({
        destination: '/app/chat.typing',
        body: JSON.stringify({ channelId, isTyping }),
      })
    } catch (error) {
      console.error('Failed to send typing indicator:', error)
    }
  }

  sendChatroomTypingIndicator(chatroomId: number, isTyping: boolean) {
    if (!this.getConnectionStatus()) {
      console.warn('⚠️ WebSocket not connected. Skipping chatroom typing indicator.')
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      this.client.publish({
        destination: '/app/chatroom.typing',
        body: JSON.stringify({ channelId: chatroomId, isTyping }),
      })
    } catch (error) {
      console.error('Failed to send chatroom typing indicator:', error)
    }
  }

  sendChatroomMessage(data: {
    workspaceId: number
    chatroomId: number
    content: string
    mentionedUserIds?: number[]
    mentionTypes?: string[]
  }) {
    if (!this.getConnectionStatus()) {
      console.error('❌ WebSocket not connected. Cannot send chatroom message.')
      // 연결이 끊어진 경우 자동으로 로그인 페이지로 리다이렉트
      if (typeof window !== 'undefined') {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        localStorage.removeItem('user')
        window.location.href = '/login'
      }
      return
    }

    if (!this.client) {
      console.error('WebSocket client is not initialized')
      return
    }

    try {
      console.log('📤 Sending chatroom message to /app/chatroom.sendMessage:', data)
      this.client.publish({
        destination: '/app/chatroom.sendMessage',
        body: JSON.stringify(data),
      })
      console.log('✅ Chatroom message sent successfully')
    } catch (error) {
      console.error('❌ Failed to send chatroom message:', error)
    }
  }

  onMessage(callback: (message: Message) => void) {
    this.messageCallbacks.push(callback)
  }

  onChatroomMessage(callback: (message: any) => void) {
    this.chatroomMessageCallbacks.push(callback)
  }

  onMessageUpdate(callback: (message: Message) => void) {
    this.messageUpdateCallbacks.push(callback)
  }

  onMessageDelete(callback: (data: { channelId: number; messageId: number }) => void) {
    this.messageDeleteCallbacks.push(callback)
  }

  onTyping(callback: (indicator: TypingIndicator) => void) {
    this.typingCallbacks.push(callback)
  }

  onMention(callback: (data: WebSocketMessage) => void) {
    this.mentionCallbacks.push(callback)
  }
}

export const wsService = new WebSocketService()
