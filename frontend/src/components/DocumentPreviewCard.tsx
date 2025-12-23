'use client'

import { useEffect, useState } from 'react'
import { documentAPI } from '@/services/api'
import styles from './DocumentPreviewCard.module.css'

interface DocumentPreviewCardProps {
  documentId: number
  workspaceId: number
}

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

export default function DocumentPreviewCard({
  documentId,
  workspaceId,
}: DocumentPreviewCardProps) {
  const [documentInfo, setDocumentInfo] = useState<DocumentInfo | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchDocument = async () => {
      try {
        setLoading(true)
        const response = await documentAPI.getDocument(documentId)
        setDocumentInfo(response.data)
      } catch (error) {
        console.error('Failed to fetch document:', error)
      } finally {
        setLoading(false)
      }
    }

    fetchDocument()
  }, [documentId])

  const handleDownload = async (e: React.MouseEvent) => {
    e.preventDefault()
    e.stopPropagation()
    if (!documentInfo) return

    try {
      const response = await documentAPI.downloadDocument(documentId)
      const url = window.URL.createObjectURL(new Blob([response.data]))
      const link = window.document.createElement('a')
      link.href = url
      link.setAttribute('download', documentInfo.name)
      window.document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Failed to download document:', error)
      alert('파일 다운로드에 실패했습니다')
    }
  }

  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 Bytes'
    const k = 1024
    const sizes = ['Bytes', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i]
  }

  const getFileIcon = (contentType: string) => {
    if (contentType.includes('pdf')) return '📄'
    if (contentType.includes('word') || contentType.includes('document')) return '📝'
    if (contentType.includes('sheet') || contentType.includes('excel')) return '📊'
    if (contentType.includes('image')) return '🖼️'
    if (contentType.includes('figma')) return '🎨'
    return '📁'
  }

  if (loading) {
    return (
      <div className={styles.card}>
        <div className={styles.loading}>문서 정보 로딩 중...</div>
      </div>
    )
  }

  if (!documentInfo) {
    return (
      <div className={styles.card}>
        <div className={styles.error}>문서를 찾을 수 없습니다</div>
      </div>
    )
  }

  const viewUrl = `/workspace/${workspaceId}/documents/view/${documentId}`

  return (
    <a href={viewUrl} className={styles.card} target="_blank" rel="noopener noreferrer">
      <div className={styles.header}>
        <div className={styles.icon}>{getFileIcon(documentInfo.contentType)}</div>
        <div className={styles.info}>
          <div className={styles.fileName}>{documentInfo.name}</div>
          <div className={styles.metadata}>
            <span>{formatFileSize(documentInfo.fileSize)}</span>
            <span>•</span>
            <span>{documentInfo.uploaderName}</span>
          </div>
        </div>
      </div>
      {documentInfo.contentType.includes('image') && (
        <div className={styles.thumbnail}>
          <img src={documentInfo.fileUrl} alt={documentInfo.name} />
        </div>
      )}
      <div className={styles.actions}>
        <button onClick={handleDownload} className={styles.downloadButton}>
          다운로드
        </button>
        <span className={styles.viewLink}>미리보기 →</span>
      </div>
    </a>
  )
}
