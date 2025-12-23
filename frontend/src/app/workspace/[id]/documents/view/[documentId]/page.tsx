'use client'

import { useEffect, useState } from 'react'
import { useRouter, useParams } from 'next/navigation'
import { useAppSelector } from '@/store/hooks'
import { documentAPI } from '@/services/api'
import mammoth from 'mammoth'
import styles from './documentView.module.css'

interface DocumentInfo {
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

export default function DocumentViewPage() {
  const router = useRouter()
  const params = useParams()
  const workspaceId = Number(params.id)
  const documentId = Number(params.documentId)
  const { user, isInitialized } = useAppSelector((state) => state.auth)
  const [document, setDocument] = useState<DocumentInfo | null>(null)
  const [loading, setLoading] = useState(true)
  const [docxHtml, setDocxHtml] = useState<string>('')
  const [docxLoading, setDocxLoading] = useState(false)

  const fetchDocument = async () => {
    try {
      setLoading(true)
      const response = await documentAPI.getDocument(documentId)
      setDocument(response.data)
    } catch (error) {
      console.error('Failed to fetch document:', error)
      alert('문서를 불러오는데 실패했습니다')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!isInitialized) return

    if (!user) {
      router.push('/login')
      return
    }

    fetchDocument()
  }, [user, isInitialized, router, documentId])

  useEffect(() => {
    if (!document) return

    const isDocx =
      document.contentType.includes('word') ||
      document.contentType.includes('document') ||
      document.contentType.includes('officedocument.wordprocessingml')

    if (isDocx) {
      renderDocx()
    }
  }, [document])

  const renderDocx = async () => {
    if (!document) return

    try {
      setDocxLoading(true)
      const response = await documentAPI.downloadDocument(documentId)
      const arrayBuffer = await response.data.arrayBuffer()

      const result = await mammoth.convertToHtml({ arrayBuffer })
      setDocxHtml(result.value)
    } catch (error) {
      console.error('Failed to render DOCX:', error)
    } finally {
      setDocxLoading(false)
    }
  }

  const handleDownload = async () => {
    if (!document) return

    try {
      const response = await documentAPI.downloadDocument(documentId)
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', document.name)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Failed to download document:', error)
      alert('파일 다운로드에 실패했습니다')
    }
  }

  const handleBackToDocuments = () => {
    router.push(`/workspace/${workspaceId}/documents`)
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
    return date.toLocaleString('ko-KR')
  }

  const getFileIcon = (contentType: string) => {
    if (contentType.includes('pdf')) return '📄'
    if (contentType.includes('word') || contentType.includes('document')) return '📝'
    if (contentType.includes('sheet') || contentType.includes('excel')) return '📊'
    if (contentType.includes('image')) return '🖼️'
    if (contentType.includes('figma')) return '🎨'
    return '📁'
  }

  const isPreviewable = (contentType: string): boolean => {
    return (
      contentType.includes('image') ||
      contentType.includes('pdf') ||
      contentType.includes('word') ||
      contentType.includes('document') ||
      contentType.includes('officedocument.wordprocessingml')
    )
  }

  const isDocx = (contentType: string): boolean => {
    return (
      contentType.includes('word') ||
      contentType.includes('document') ||
      contentType.includes('officedocument.wordprocessingml')
    )
  }

  if (loading) {
    return (
      <div className={styles.container}>
        <div className={styles.loading}>로딩 중...</div>
      </div>
    )
  }

  if (!document) {
    return (
      <div className={styles.container}>
        <div className={styles.error}>문서를 찾을 수 없습니다</div>
      </div>
    )
  }

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <button onClick={handleBackToDocuments} className={styles.backButton}>
          ← 문서 목록으로
        </button>
        <h1>문서 미리보기</h1>
      </div>

      <div className={styles.documentCard}>
        <div className={styles.documentHeader}>
          <div className={styles.fileIcon}>{getFileIcon(document.contentType)}</div>
          <div className={styles.documentInfo}>
            <h2 className={styles.fileName}>{document.name}</h2>
            <div className={styles.metadata}>
              <span>크기: {formatFileSize(document.fileSize)}</span>
              <span>업로드: {document.uploaderName}</span>
              <span>날짜: {formatDate(document.uploadedAt)}</span>
            </div>
          </div>
        </div>

        <div className={styles.actions}>
          <button onClick={handleDownload} className={styles.downloadButton}>
            다운로드
          </button>
        </div>

        {isPreviewable(document.contentType) && (
          <div className={styles.previewContainer}>
            {document.contentType.includes('image') ? (
              <img
                src={document.fileUrl}
                alt={document.name}
                className={styles.previewImage}
              />
            ) : document.contentType.includes('pdf') ? (
              <iframe
                src={document.fileUrl}
                className={styles.previewPdf}
                title={document.name}
              />
            ) : isDocx(document.contentType) ? (
              <div className={styles.docxPreview}>
                {docxLoading ? (
                  <div className={styles.loading}>문서 변환 중...</div>
                ) : (
                  <div
                    className={styles.docxContent}
                    dangerouslySetInnerHTML={{ __html: docxHtml }}
                  />
                )}
              </div>
            ) : null}
          </div>
        )}

        {!isPreviewable(document.contentType) && (
          <div className={styles.noPreview}>
            <p>이 파일 형식은 미리보기를 지원하지 않습니다</p>
            <p>다운로드 버튼을 클릭하여 파일을 다운로드하세요</p>
          </div>
        )}
      </div>
    </div>
  )
}
