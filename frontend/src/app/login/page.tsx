'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAppDispatch } from '@/store/hooks'
import { setCredentials } from '@/store/slices/authSlice'
import { authAPI } from '@/services/api'
import styles from './login.module.css'

export default function LoginPage() {
  const router = useRouter()
  const dispatch = useAppDispatch()
  const [isLogin, setIsLogin] = useState(true)
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    name: '',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [rememberMe, setRememberMe] = useState(false)

  // Electron 환경에서 저장된 credential 불러오기
  useEffect(() => {
    const loadSavedCredentials = async () => {
      if (typeof window !== 'undefined' && window.electron?.credentials) {
        try {
          const result = await window.electron.credentials.load()
          if (result.success && result.credentials) {
            setFormData((prev) => ({
              ...prev,
              email: result.credentials!.email,
              password: result.credentials!.password,
            }))
            setRememberMe(true)
          }
        } catch (err) {
          console.error('Failed to load saved credentials:', err)
        }
      }
    }

    loadSavedCredentials()
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const response = isLogin
        ? await authAPI.login({ email: formData.email, password: formData.password })
        : await authAPI.register(formData)

      const { accessToken, refreshToken, userId, email, name, avatarUrl, status } = response.data

      dispatch(
        setCredentials({
          user: { id: userId, email, name, avatarUrl, status },
          accessToken,
          refreshToken,
        })
      )

      // Electron 환경에서 credential 저장 또는 삭제
      if (typeof window !== 'undefined' && window.electron?.credentials && isLogin) {
        try {
          if (rememberMe) {
            await window.electron.credentials.save({
              email: formData.email,
              password: formData.password,
            })
          } else {
            await window.electron.credentials.delete()
          }
        } catch (err) {
          console.error('Failed to save/delete credentials:', err)
        }
      }

      router.push('/workspaces')
    } catch (err: any) {
      setError(err.response?.data?.message || 'Authentication failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className={styles.container}>
      <div className={styles.formCard}>
        <h1>{isLogin ? 'Login' : 'Register'}</h1>

        <form onSubmit={handleSubmit}>
          {!isLogin && (
            <div className={styles.formGroup}>
              <label>Name</label>
              <input
                type="text"
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                required
              />
            </div>
          )}

          <div className={styles.formGroup}>
            <label>Email</label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              required
            />
          </div>

          <div className={styles.formGroup}>
            <label>Password</label>
            <input
              type="password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              required
            />
          </div>

          {isLogin && typeof window !== 'undefined' && window.electron?.credentials && (
            <div className={styles.checkboxGroup}>
              <label>
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                <span>아이디 기억하기</span>
              </label>
            </div>
          )}

          {error && <div className={styles.error}>{error}</div>}

          <button type="submit" disabled={loading} className={styles.submitButton}>
            {loading ? 'Processing...' : isLogin ? 'Login' : 'Register'}
          </button>
        </form>

        <button
          onClick={() => setIsLogin(!isLogin)}
          className={styles.toggleButton}
        >
          {isLogin ? 'Need an account? Register' : 'Have an account? Login'}
        </button>
      </div>
    </div>
  )
}
