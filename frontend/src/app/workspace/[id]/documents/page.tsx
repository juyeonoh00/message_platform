'use client'

import { useEffect, useState, useRef } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAppSelector } from '@/store/hooks'
import { documentAPI } from '@/services/api'
import Toast from '@/components/Toast'
import styles from './documents.module.css'

interface Document {
  id: number
  name: string
  fileUrl: string
  fileSize: number
  contentType: string
  categoryId: number
  workspaceId: number
  uploaderId: number
  uploaderName: string
  uploadedAt: string
}

interface Category {
  id: number
  name: string
  workspaceId: number
  createdAt: string
  documents: Document[]
}

export default function DocumentsPage() {
  const router = useRouter()
  const params = useParams()
  const workspaceId = Number(params.id)
  const { user, isInitialized } = useAppSelector((state) => state.auth)
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(false)
  const [showAddCategoryModal, setShowAddCategoryModal] = useState(false)
  const [newCategoryName, setNewCategoryName] = useState('')
  const [expandedCategories, setExpandedCategories] = useState<Set<number>>(new Set())
  const [uploadingCategoryId, setUploadingCategoryId] = useState<number | null>(null)
  const fileInputRefs = useRef<{ [key: number]: HTMLInputElement | null }>({})
  const [toastMessage, setToastMessage] = useState<string | null>(null)

  const fetchCategories = async () => {
    try {
      setLoading(true)
      const response = await documentAPI.getCategoriesWithDocuments(workspaceId)
      setCategories(response.data)
    } catch (error) {
      console.error('Failed to fetch categories:', error)
      alert('카테고리를 불러오는데 실패했습니다')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    // Wait for auth to be initialized before checking
    if (!isInitialized) return

    if (!user) {
      router.push('/login')
      return
    }

    fetchCategories()
  }, [user, isInitialized, router, workspaceId])

  const handleBackToWorkspace = () => {
    router.push(`/workspace/${workspaceId}`)
  }

  const handleAddCategory = async () => {
    if (!newCategoryName.trim()) {
      alert('카테고리 이름을 입력해주세요')
      return
    }

    try {
      await documentAPI.createCategory({
        name: newCategoryName,
        workspaceId,
      })
      setNewCategoryName('')
      setShowAddCategoryModal(false)
      fetchCategories()
    } catch (error) {
      console.error('Failed to create category:', error)
      alert('카테고리 생성에 실패했습니다')
    }
  }

  const handleDeleteCategory = async (categoryId: number) => {
    if (!confirm('이 카테고리와 모든 문서를 삭제하시겠습니까?')) {
      return
    }

    try {
      await documentAPI.deleteCategory(categoryId)
      fetchCategories()
    } catch (error) {
      console.error('Failed to delete category:', error)
      alert('카테고리 삭제에 실패했습니다')
    }
  }

  const handleFileUpload = (categoryId: number) => {
    fileInputRefs.current[categoryId]?.click()
  }

  const handleFileChange = async (
    event: React.ChangeEvent<HTMLInputElement>,
    categoryId: number
  ) => {
    const file = event.target.files?.[0]
    if (!file) return

    try {
      setUploadingCategoryId(categoryId)
      await documentAPI.uploadDocument(categoryId, workspaceId, file)
      fetchCategories()
      // Reset file input
      if (fileInputRefs.current[categoryId]) {
        fileInputRefs.current[categoryId]!.value = ''
      }
    } catch (error) {
      console.error('Failed to upload document:', error)
      alert('파일 업로드에 실패했습니다')
    } finally {
      setUploadingCategoryId(null)
    }
  }

  const handleDownload = async (documentId: number, fileName: string) => {
    try {
      const response = await documentAPI.downloadDocument(documentId)
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', fileName)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Failed to download document:', error)
      alert('파일 다운로드에 실패했습니다')
    }
  }

  const handleDeleteDocument = async (documentId: number) => {
    if (!confirm('이 문서를 삭제하시겠습니까?')) {
      return
    }

    try {
      await documentAPI.deleteDocument(documentId)
      fetchCategories()
    } catch (error) {
      console.error('Failed to delete document:', error)
      alert('문서 삭제에 실패했습니다')
    }
  }

  const handleCopyLink = (documentId: number) => {
    const link = `${window.location.origin}/workspace/${workspaceId}/documents/view/${documentId}`
    navigator.clipboard.writeText(link).then(
      () => {
        setToastMessage('링크가 클립보드에 복사되었습니다')
      },
      (err) => {
        console.error('Failed to copy link:', err)
        setToastMessage('링크 복사에 실패했습니다')
      }
    )
  }

  const toggleCategory = (categoryId: number) => {
    const newExpanded = new Set(expandedCategories)
    if (newExpanded.has(categoryId)) {
      newExpanded.delete(categoryId)
    } else {
      newExpanded.add(categoryId)
    }
    setExpandedCategories(newExpanded)
  }

  const getFileIcon = (contentType: string) => {
    if (contentType.includes('pdf')) return '📄'
    if (contentType.includes('word') || contentType.includes('document')) return '📝'
    if (contentType.includes('sheet') || contentType.includes('excel')) return '📊'
    if (contentType.includes('image')) return '🖼️'
    if (contentType.includes('figma')) return '🎨'
    return '📁'
  }

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes'
    const k = 1024
    const sizes = ['Bytes', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
  }

  const formatDate = (dateString: string): string => {
    const date = new Date(dateString)
    return date.toLocaleDateString('ko-KR')
  }

  const getDisplayedDocuments = (category: Category) => {
    const isExpanded = expandedCategories.has(category.id)
    return isExpanded ? category.documents : category.documents.slice(0, 4)
  }

  return (
    <div className={styles.container}>
      <div className={styles.sidebar}>
        <div className={styles.workspaceHeader}>
          <h2>문서 보관함</h2>
          <button onClick={handleBackToWorkspace} className={styles.backButton}>
            ← 뒤로 가기
          </button>
        </div>
      </div>

      <div className={styles.main}>
        <div className={styles.header}>
          <h1>📁 문서 보관함</h1>
          <button
            className={styles.uploadButton}
            onClick={() => setShowAddCategoryModal(true)}
          >
            + 카테고리 추가
          </button>
        </div>

        <div className={styles.content}>
          {loading ? (
            <div className={styles.loading}>로딩 중...</div>
          ) : categories.length === 0 ? (
            <div className={styles.empty}>
              <p>카테고리가 없습니다</p>
              <button
                className={styles.uploadButton}
                onClick={() => setShowAddCategoryModal(true)}
              >
                첫 카테고리 만들기
              </button>
            </div>
          ) : (
            <div className={styles.categoriesContainer}>
              {categories.map((category) => (
                <div key={category.id} className={styles.categorySection}>
                  <div className={styles.categoryHeader}>
                    <h2>{category.name}</h2>
                    <div className={styles.categoryActions}>
                      <input
                        type="file"
                        ref={(el) => (fileInputRefs.current[category.id] = el)}
                        style={{ display: 'none' }}
                        onChange={(e) => handleFileChange(e, category.id)}
                      />
                      <button
                        className={styles.categoryActionButton}
                        onClick={() => handleFileUpload(category.id)}
                        disabled={uploadingCategoryId === category.id}
                      >
                        {uploadingCategoryId === category.id
                          ? '업로드 중...'
                          : '+ 파일 업로드'}
                      </button>
                      <button
                        className={styles.categoryActionButton}
                        onClick={() => handleDeleteCategory(category.id)}
                      >
                        카테고리 삭제
                      </button>
                    </div>
                  </div>

                  {category.documents.length === 0 ? (
                    <div className={styles.emptyCategoryMessage}>
                      이 카테고리에 파일이 없습니다
                    </div>
                  ) : (
                    <>
                      <table className={styles.table}>
                        <thead>
                          <tr>
                            <th>파일명</th>
                            <th>크기</th>
                            <th>업로드한 사람</th>
                            <th>업로드 날짜</th>
                            <th>작업</th>
                          </tr>
                        </thead>
                        <tbody>
                          {getDisplayedDocuments(category).map((doc) => (
                            <tr key={doc.id}>
                              <td className={styles.fileName}>
                                <span className={styles.fileIcon}>
                                  {getFileIcon(doc.contentType)}
                                </span>
                                {doc.name}
                              </td>
                              <td>{formatFileSize(doc.fileSize)}</td>
                              <td>{doc.uploaderName}</td>
                              <td>{formatDate(doc.uploadedAt)}</td>
                              <td>
                                <button
                                  className={styles.actionButton}
                                  onClick={() => handleCopyLink(doc.id)}
                                  title="링크 복사"
                                >
                                  링크 복사
                                </button>
                                <button
                                  className={styles.actionButton}
                                  onClick={() => handleDownload(doc.id, doc.name)}
                                >
                                  다운로드
                                </button>
                                <button
                                  className={styles.actionButton}
                                  onClick={() => handleDeleteDocument(doc.id)}
                                >
                                  삭제
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>

                      {category.documents.length > 4 && (
                        <div className={styles.expandButtonContainer}>
                          <button
                            className={styles.expandButton}
                            onClick={() => toggleCategory(category.id)}
                          >
                            {expandedCategories.has(category.id)
                              ? `▲ 접기`
                              : `▼ ${category.documents.length - 4}개 더보기`}
                          </button>
                        </div>
                      )}
                    </>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Add Category Modal */}
      {showAddCategoryModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h2>카테고리 추가</h2>
            <div className={styles.formGroup}>
              <label>카테고리 이름</label>
              <input
                type="text"
                value={newCategoryName}
                onChange={(e) => setNewCategoryName(e.target.value)}
                placeholder="예: 프로젝트 문서, 회의록, 디자인..."
                autoFocus
              />
            </div>
            <div className={styles.modalActions}>
              <button onClick={handleAddCategory} className={styles.primaryButton}>
                추가
              </button>
              <button
                onClick={() => {
                  setShowAddCategoryModal(false)
                  setNewCategoryName('')
                }}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Toast Notification */}
      {toastMessage && (
        <Toast message={toastMessage} onClose={() => setToastMessage(null)} />
      )}
    </div>
  )
}
