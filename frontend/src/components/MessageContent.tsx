'use client'

import DocumentPreviewCard from './DocumentPreviewCard'

interface MessageContentProps {
  content: string
  workspaceId: number
}

export default function MessageContent({ content, workspaceId }: MessageContentProps) {
  // 문서 링크 패턴 감지: /workspace/{workspaceId}/documents/view/{documentId}
  const documentLinkRegex = /https?:\/\/[^\s]+\/workspace\/(\d+)\/documents\/view\/(\d+)/g

  const parts: (string | JSX.Element)[] = []
  let lastIndex = 0
  let match

  while ((match = documentLinkRegex.exec(content)) !== null) {
    // 링크 이전 텍스트 추가
    if (match.index > lastIndex) {
      parts.push(content.substring(lastIndex, match.index))
    }

    // 문서 ID 추출
    const matchedWorkspaceId = parseInt(match[1])
    const documentId = parseInt(match[2])

    // DocumentPreviewCard 추가
    parts.push(
      <DocumentPreviewCard
        key={`doc-${documentId}-${match.index}`}
        documentId={documentId}
        workspaceId={matchedWorkspaceId}
      />
    )

    lastIndex = match.index + match[0].length
  }

  // 남은 텍스트 추가
  if (lastIndex < content.length) {
    parts.push(content.substring(lastIndex))
  }

  // 문서 링크가 없으면 원본 텍스트 반환
  if (parts.length === 0) {
    return <>{content}</>
  }

  return (
    <>
      {parts.map((part, index) =>
        typeof part === 'string' ? <span key={index}>{part}</span> : part
      )}
    </>
  )
}
