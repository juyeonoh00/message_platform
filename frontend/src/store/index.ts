import { configureStore } from '@reduxjs/toolkit'
import authReducer from './slices/authSlice'
import workspaceReducer from './slices/workspaceSlice'
import channelReducer from './slices/channelSlice'
import messageReducer from './slices/messageSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    workspace: workspaceReducer,
    channel: channelReducer,
    message: messageReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch
