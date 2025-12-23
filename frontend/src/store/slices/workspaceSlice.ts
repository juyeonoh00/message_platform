import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { Workspace } from '@/types'

interface WorkspaceState {
  workspaces: Workspace[]
  currentWorkspace: Workspace | null
  loading: boolean
  error: string | null
}

const initialState: WorkspaceState = {
  workspaces: [],
  currentWorkspace: null,
  loading: false,
  error: null,
}

const workspaceSlice = createSlice({
  name: 'workspace',
  initialState,
  reducers: {
    setWorkspaces: (state, action: PayloadAction<Workspace[]>) => {
      state.workspaces = action.payload
      state.loading = false
    },
    setCurrentWorkspace: (state, action: PayloadAction<Workspace>) => {
      state.currentWorkspace = action.payload

      // Save to localStorage
      if (typeof window !== 'undefined') {
        localStorage.setItem('currentWorkspaceId', action.payload.id.toString())
      }
    },
    addWorkspace: (state, action: PayloadAction<Workspace>) => {
      state.workspaces.push(action.payload)
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
  setWorkspaces,
  setCurrentWorkspace,
  addWorkspace,
  setLoading,
  setError,
} = workspaceSlice.actions

export default workspaceSlice.reducer
