import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { Message, TypingIndicator } from '@/types'

interface MessageState {
  channelMessages: { [channelId: number]: Message[] }
  chatroomMessages: { [chatroomId: number]: Message[] }
  threadMessages: { [parentMessageId: number]: Message[] }
  typingUsers: { [channelId: number]: TypingIndicator[] }
  loading: boolean
  error: string | null
}

const initialState: MessageState = {
  channelMessages: {},
  chatroomMessages: {},
  threadMessages: {},
  typingUsers: {},
  loading: false,
  error: null,
}

const messageSlice = createSlice({
  name: 'message',
  initialState,
  reducers: {
    setMessages: (
      state,
      action: PayloadAction<{ channelId?: number; chatroomId?: number; messages: Message[] }>
    ) => {
      const { channelId, chatroomId, messages } = action.payload
      if (channelId) {
        state.channelMessages[channelId] = messages
      } else if (chatroomId) {
        state.chatroomMessages[chatroomId] = messages
      }
      state.loading = false
    },
    addMessage: (state, action: PayloadAction<Message>) => {
      const message = action.payload
      const channelId = message.channelId
      const chatroomId = (message as any).chatroomId

      if (channelId) {
        // Channel message
        if (!state.channelMessages[channelId]) {
          state.channelMessages[channelId] = []
        }

        // Check if message already exists
        const exists = state.channelMessages[channelId].some(
          (m) => m.id === message.id
        )

        if (!exists) {
          state.channelMessages[channelId].push(message)
        }
      } else if (chatroomId) {
        // Chatroom message
        if (!state.chatroomMessages[chatroomId]) {
          state.chatroomMessages[chatroomId] = []
        }

        // Check if message already exists
        const exists = state.chatroomMessages[chatroomId].some(
          (m) => m.id === message.id
        )

        if (!exists) {
          state.chatroomMessages[chatroomId].push(message)
        }
      }
    },
    setThreadMessages: (
      state,
      action: PayloadAction<{ parentMessageId: number; messages: Message[] }>
    ) => {
      state.threadMessages[action.payload.parentMessageId] = action.payload.messages
    },
    addThreadMessage: (
      state,
      action: PayloadAction<{ parentMessageId: number; message: Message }>
    ) => {
      const parentId = action.payload.parentMessageId
      if (!state.threadMessages[parentId]) {
        state.threadMessages[parentId] = []
      }

      const exists = state.threadMessages[parentId].some(
        (m) => m.id === action.payload.message.id
      )

      if (!exists) {
        state.threadMessages[parentId].push(action.payload.message)
      }
    },
    setTypingUsers: (
      state,
      action: PayloadAction<{ channelId: number; users: TypingIndicator[] }>
    ) => {
      state.typingUsers[action.payload.channelId] = action.payload.users
    },
    addTypingUser: (state, action: PayloadAction<TypingIndicator>) => {
      const channelId = action.payload.channelId
      if (!state.typingUsers[channelId]) {
        state.typingUsers[channelId] = []
      }

      // Remove existing entry for this user
      state.typingUsers[channelId] = state.typingUsers[channelId].filter(
        (u) => u.userId !== action.payload.userId
      )

      // Add new typing indicator if typing is true
      if (action.payload.isTyping) {
        state.typingUsers[channelId].push(action.payload)
      }
    },
    updateMessage: (state, action: PayloadAction<Message>) => {
      const message = action.payload
      const channelId = message.channelId
      const chatroomId = (message as any).chatroomId

      // Update in channel messages
      if (channelId && state.channelMessages[channelId]) {
        state.channelMessages[channelId] = state.channelMessages[channelId].map((m) =>
          m.id === message.id ? message : m
        )
      }

      // Update in chatroom messages
      if (chatroomId && state.chatroomMessages[chatroomId]) {
        state.chatroomMessages[chatroomId] = state.chatroomMessages[chatroomId].map((m) =>
          m.id === message.id ? message : m
        )
      }

      // Update in thread messages if it's a reply
      if (message.parentMessageId && state.threadMessages[message.parentMessageId]) {
        state.threadMessages[message.parentMessageId] = state.threadMessages[message.parentMessageId].map((m) =>
          m.id === message.id ? message : m
        )
      }
    },
    deleteMessage: (state, action: PayloadAction<{ channelId?: number; chatroomId?: number; messageId: number }>) => {
      const { channelId, chatroomId, messageId } = action.payload

      // Remove from channel messages
      if (channelId && state.channelMessages[channelId]) {
        state.channelMessages[channelId] = state.channelMessages[channelId].filter(
          (m) => m.id !== messageId
        )
      }

      // Remove from chatroom messages
      if (chatroomId && state.chatroomMessages[chatroomId]) {
        state.chatroomMessages[chatroomId] = state.chatroomMessages[chatroomId].filter(
          (m) => m.id !== messageId
        )
      }

      // Remove from thread messages
      Object.keys(state.threadMessages).forEach((parentId) => {
        state.threadMessages[Number(parentId)] = state.threadMessages[Number(parentId)].filter(
          (m) => m.id !== messageId
        )
      })

      // If the deleted message was a parent, remove its thread
      if (state.threadMessages[messageId]) {
        delete state.threadMessages[messageId]
      }
    },
    toggleMessageReaction: (
      state,
      action: PayloadAction<{ channelId?: number; chatroomId?: number; messageId: number; emoji: string; userId: number }>
    ) => {
      const { channelId, chatroomId, messageId, emoji, userId } = action.payload

      const updateReaction = (message: Message) => {
        if (!message.reactions) {
          message.reactions = {}
        }

        if (!message.reactions[emoji]) {
          message.reactions[emoji] = []
        }

        const userIndex = message.reactions[emoji].indexOf(userId)
        if (userIndex > -1) {
          // Remove reaction
          message.reactions[emoji].splice(userIndex, 1)
          if (message.reactions[emoji].length === 0) {
            delete message.reactions[emoji]
          }
        } else {
          // Add reaction
          message.reactions[emoji].push(userId)
        }
      }

      // Update in channel messages
      if (channelId && state.channelMessages[channelId]) {
        const message = state.channelMessages[channelId].find((m) => m.id === messageId)
        if (message) {
          updateReaction(message)
        }
      }

      // Update in chatroom messages
      if (chatroomId && state.chatroomMessages[chatroomId]) {
        const message = state.chatroomMessages[chatroomId].find((m) => m.id === messageId)
        if (message) {
          updateReaction(message)
        }
      }

      // Update in thread messages
      Object.values(state.threadMessages).forEach((messages) => {
        const message = messages.find((m) => m.id === messageId)
        if (message) {
          updateReaction(message)
        }
      })
    },
    clearMessages: (state, action: PayloadAction<{ channelId?: number; chatroomId?: number }>) => {
      const { channelId, chatroomId } = action.payload
      if (channelId) {
        delete state.channelMessages[channelId]
      } else if (chatroomId) {
        delete state.chatroomMessages[chatroomId]
      }
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload
    },
    setError: (state, action: PayloadAction<string | null>) => {
      state.error = action.payload
      state.loading = false
    },
  },
})

export const {
  setMessages,
  addMessage,
  updateMessage,
  deleteMessage,
  toggleMessageReaction,
  setThreadMessages,
  addThreadMessage,
  setTypingUsers,
  addTypingUser,
  clearMessages,
  setLoading,
  setError,
} = messageSlice.actions

export default messageSlice.reducer
