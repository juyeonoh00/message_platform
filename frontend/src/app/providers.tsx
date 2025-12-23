'use client'

import { Provider } from 'react-redux'
import { store } from '@/store'
import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '@/store/hooks'
import { loadFromStorage } from '@/store/slices/authSlice'
import { setupTokenRefreshTimer, clearTokenRefreshTimer } from '@/services/api'

function AuthLoader({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch()
  const accessToken = useAppSelector((state) => state.auth.accessToken)

  useEffect(() => {
    // Load auth credentials from localStorage on app start
    dispatch(loadFromStorage())
  }, [dispatch])

  useEffect(() => {
    // Setup token refresh timer when accessToken changes
    if (accessToken) {
      console.log('🔐 Setting up token refresh timer...')
      setupTokenRefreshTimer()
    } else {
      // Clear timer when logged out
      clearTokenRefreshTimer()
    }

    // Cleanup on unmount
    return () => {
      clearTokenRefreshTimer()
    }
  }, [accessToken])

  return <>{children}</>
}

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <Provider store={store}>
      <AuthLoader>{children}</AuthLoader>
    </Provider>
  )
}
