'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAppSelector, useAppDispatch } from '@/store/hooks'
import { setWorkspaces, setCurrentWorkspace } from '@/store/slices/workspaceSlice'
import { workspaceAPI } from '@/services/api'
import { Workspace } from '@/types'
import styles from './workspaces.module.css'

export default function WorkspacesPage() {
  const router = useRouter()
  const dispatch = useAppDispatch()
  const { workspaces } = useAppSelector((state) => state.workspace)
  const { user, isInitialized } = useAppSelector((state) => state.auth)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newWorkspaceName, setNewWorkspaceName] = useState('')

  useEffect(() => {
    // Wait for auth to be initialized before checking
    if (!isInitialized) return

    if (!user) {
      router.push('/login')
      return
    }

    loadWorkspaces()
  }, [user, isInitialized])

  const loadWorkspaces = async () => {
    try {
      const response = await workspaceAPI.getAll()
      dispatch(setWorkspaces(response.data))
    } catch (error) {
      console.error('Failed to load workspaces', error)
    }
  }

  const handleCreateWorkspace = async () => {
    if (!newWorkspaceName.trim()) return

    try {
      const response = await workspaceAPI.create({ name: newWorkspaceName })
      dispatch(setWorkspaces([...workspaces, response.data]))
      setNewWorkspaceName('')
      setShowCreateModal(false)
    } catch (error) {
      console.error('Failed to create workspace', error)
    }
  }

  const handleSelectWorkspace = (workspace: Workspace) => {
    dispatch(setCurrentWorkspace(workspace))
    router.push(`/workspace/${workspace.id}`)
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <h1>Your Workspaces</h1>
        <button onClick={() => setShowCreateModal(true)} className={styles.createButton}>
          + Create Workspace
        </button>
      </div>

      <div className={styles.workspaceGrid}>
        {workspaces.map((workspace) => (
          <div
            key={workspace.id}
            className={styles.workspaceCard}
            onClick={() => handleSelectWorkspace(workspace)}
          >
            <h3>{workspace.name}</h3>
            <p>{workspace.description || 'No description'}</p>
            <span className={styles.role}>{workspace.role}</span>
          </div>
        ))}
      </div>

      {showCreateModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h2>Create New Workspace</h2>
            <input
              type="text"
              placeholder="Workspace name"
              value={newWorkspaceName}
              onChange={(e) => setNewWorkspaceName(e.target.value)}
            />
            <div className={styles.modalActions}>
              <button onClick={handleCreateWorkspace}>Create</button>
              <button onClick={() => setShowCreateModal(false)}>Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
