import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { Channel } from '@/types'

interface ChannelState {
  channels: Channel[]
  currentChannel: Channel | null
  loading: boolean
  error: string | null
}

const initialState: ChannelState = {
  channels: [],
  currentChannel: null,
  loading: false,
  error: null,
}

const channelSlice = createSlice({
  name: 'channel',
  initialState,
  reducers: {
    setChannels: (state, action: PayloadAction<Channel[]>) => {
      state.channels = action.payload
      state.loading = false
    },
    setCurrentChannel: (state, action: PayloadAction<Channel | null>) => {
      state.currentChannel = action.payload

      // Save to localStorage
      if (typeof window !== 'undefined') {
        if (action.payload) {
          localStorage.setItem('currentChannelId', action.payload.id.toString())
        } else {
          localStorage.removeItem('currentChannelId')
        }
      }
    },
    addChannel: (state, action: PayloadAction<Channel>) => {
      state.channels.push(action.payload)
    },
    removeChannel: (state, action: PayloadAction<number>) => {
      state.channels = state.channels.filter((c) => c.id !== action.payload)
      // If deleted channel is current channel, clear it
      if (state.currentChannel?.id === action.payload) {
        state.currentChannel = null
        if (typeof window !== 'undefined') {
          localStorage.removeItem('currentChannelId')
        }
      }
    },
    updateChannelUnreadCount: (
      state,
      action: PayloadAction<{ channelId: number; unreadCount: number }>
    ) => {
      const channel = state.channels.find((c) => c.id === action.payload.channelId)
      if (channel) {
        channel.unreadCount = action.payload.unreadCount
      }
    },
    incrementUnreadCount: (state, action: PayloadAction<number>) => {
      const channel = state.channels.find((c) => c.id === action.payload)
      if (channel && state.currentChannel?.id !== action.payload) {
        channel.unreadCount += 1
      }
    },
    resetUnreadCount: (state, action: PayloadAction<number>) => {
      const channel = state.channels.find((c) => c.id === action.payload)
      if (channel) {
        channel.unreadCount = 0
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
  setChannels,
  setCurrentChannel,
  addChannel,
  removeChannel,
  updateChannelUnreadCount,
  incrementUnreadCount,
  resetUnreadCount,
  setLoading,
  setError,
} = channelSlice.actions

export default channelSlice.reducer
