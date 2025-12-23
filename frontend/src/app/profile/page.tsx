'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useAppSelector, useAppDispatch } from '@/store/hooks'
import { logout, updateUser } from '@/store/slices/authSlice'
import { userAPI } from '@/services/api'
import styles from './profile.module.css'

export default function ProfilePage() {
  const router = useRouter()
  const dispatch = useAppDispatch()
  const { user, isAuthenticated } = useAppSelector((state) => state.auth)

  const [isEditing, setIsEditing] = useState(false)
  const [formData, setFormData] = useState({
    name: user?.name || '',
    status: user?.status || 'active',
  })
  const [avatarFile, setAvatarFile] = useState<File | null>(null)
  const [avatarPreview, setAvatarPreview] = useState<string>(user?.avatarUrl || '')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')

  useEffect(() => {
    if (!isAuthenticated) {
      router.push('/login')
    }
  }, [isAuthenticated, router])

  useEffect(() => {
    if (user) {
      setFormData({
        name: user.name || '',
        status: user.status || 'active',
      })
      setAvatarPreview(user.avatarUrl || '')
    }
  }, [user])

  const handleLogout = () => {
    dispatch(logout())
    router.push('/login')
  }

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      // 파일 유효성 검사 (이미지만 허용)
      if (!file.type.startsWith('image/')) {
        setError('이미지 파일만 업로드 가능합니다')
        return
      }

      // 파일 크기 검사 (10MB 제한)
      if (file.size > 10 * 1024 * 1024) {
        setError('파일 크기는 10MB 이하여야 합니다')
        return
      }

      setAvatarFile(file)

      // 미리보기 생성
      const reader = new FileReader()
      reader.onloadend = () => {
        setAvatarPreview(reader.result as string)
      }
      reader.readAsDataURL(file)
      setError('')
    }
  }

  const handleSave = async () => {
    if (!formData.name.trim()) {
      setError('이름은 필수입니다')
      return
    }

    setIsLoading(true)
    setError('')
    setSuccessMessage('')

    try {
      const response = await userAPI.updateProfileWithAvatar(formData.name, formData.status, avatarFile)

      // Update Redux store and localStorage
      const updatedUser = response.data
      dispatch(updateUser(updatedUser))

      setSuccessMessage('프로필이 성공적으로 업데이트되었습니다. 잠시 후 이전 페이지로 돌아갑니다.')
      setIsEditing(false)
      setAvatarFile(null)

      // 1.5초 후 이전 페이지로 돌아가기 (전체 페이지 새로고침)
      setTimeout(() => {
        // 이전 페이지 URL이 있으면 그곳으로, 없으면 홈으로
        if (typeof window !== 'undefined' && document.referrer) {
          window.location.href = document.referrer
        } else {
          router.push('/')
        }
      }, 1500)
    } catch (err: any) {
      setError(err.response?.data?.message || '프로필 업데이트에 실패했습니다')
    } finally {
      setIsLoading(false)
    }
  }

  const handleCancel = () => {
    setFormData({
      name: user?.name || '',
      status: user?.status || 'active',
    })
    setAvatarFile(null)
    setAvatarPreview(user?.avatarUrl || '')
    setIsEditing(false)
    setError('')
    setSuccessMessage('')
  }

  if (!user) return null

  return (
    <div className={styles.container}>
      <div className={styles.profileCard}>
        <div className={styles.header}>
          <h1>프로필 설정</h1>
          <button onClick={() => router.back()} className={styles.backButton}>
            ← 뒤로가기
          </button>
        </div>

        {error && <div className={styles.error}>{error}</div>}
        {successMessage && <div className={styles.success}>{successMessage}</div>}

        <div className={styles.content}>
          <div className={styles.avatarSection}>
            {avatarPreview ? (
              <img src={avatarPreview} alt={formData.name} className={styles.avatarImage} />
            ) : (
              <div className={styles.avatar}>
                {formData.name?.charAt(0).toUpperCase() || 'U'}
              </div>
            )}
            {isEditing && (
              <div className={styles.inputGroup}>
                <label>프로필 이미지</label>
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleFileChange}
                  className={styles.fileInput}
                />
                <p className={styles.fileHint}>이미지 파일을 선택하세요 (최대 10MB)</p>
              </div>
            )}
          </div>

          <div className={styles.infoSection}>
            <div className={styles.infoItem}>
              <label>이름</label>
              {isEditing ? (
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleInputChange}
                  className={styles.input}
                  placeholder="이름을 입력하세요"
                />
              ) : (
                <div className={styles.value}>{user.name}</div>
              )}
            </div>

            <div className={styles.infoItem}>
              <label>이메일</label>
              <div className={styles.value}>{user.email}</div>
            </div>

            <div className={styles.infoItem}>
              <label>상태</label>
              {isEditing ? (
                <select
                  name="status"
                  value={formData.status}
                  onChange={handleInputChange}
                  className={styles.statusSelect}
                >
                  <option value="active">🟢 온라인</option>
                  <option value="away">🌙 자리비움</option>
                  <option value="dnd">🔴 다른 용무 중</option>
                  <option value="vacation">🏖️ 연차</option>
                  <option value="sick">🤒 병가</option>
                  <option value="offline">⚫ 오프라인</option>
                </select>
              ) : (
                <div className={styles.value}>
                  {(!user.status || user.status === 'active') && '🟢 온라인'}
                  {user.status === 'away' && '🌙 자리비움'}
                  {user.status === 'dnd' && '🔴 다른 용무 중'}
                  {user.status === 'vacation' && '🏖️ 연차'}
                  {user.status === 'sick' && '🤒 병가'}
                  {user.status === 'offline' && '⚫ 오프라인'}
                </div>
              )}
            </div>
          </div>

          <div className={styles.actions}>
            {isEditing ? (
              <>
                <button
                  onClick={handleSave}
                  disabled={isLoading}
                  className={styles.saveButton}
                >
                  {isLoading ? '저장 중...' : '저장'}
                </button>
                <button
                  onClick={handleCancel}
                  disabled={isLoading}
                  className={styles.cancelButton}
                >
                  취소
                </button>
              </>
            ) : (
              <>
                <button onClick={() => setIsEditing(true)} className={styles.editButton}>
                  프로필 수정
                </button>
                <button onClick={handleLogout} className={styles.logoutButton}>
                  로그아웃
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
