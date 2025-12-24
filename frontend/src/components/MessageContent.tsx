'use client'

import DocumentPreviewCard from './DocumentPreviewCard'
import styles from './MessageContent.module.css'

interface MentionInfo {
  userId?: number
  mentionType: string
}

interface MessageContentProps {
  content: string
  workspaceId: number
  mentions?: MentionInfo[]
  currentUserId?: number
}

export default function MessageContent({ content, workspaceId, mentions, currentUserId }: MessageContentProps) {
  // Parse content for document links and mentions
  const parseContent = () => {
    const parts: (string | JSX.Element)[] = []
    let processedContent = content
    let keyCounter = 0

    // First, process document links
    const documentLinkRegex = /https?:\/\/[^\s]+\/workspace\/(\d+)\/documents\/view\/(\d+)/g
    let match

    const tempParts: Array<{ type: 'text' | 'document', content: string | JSX.Element }> = []
    let lastIndex = 0

    while ((match = documentLinkRegex.exec(content)) !== null) {
      // Add text before link
      if (match.index > lastIndex) {
        tempParts.push({
          type: 'text',
          content: content.substring(lastIndex, match.index)
        })
      }

      // Add document preview
      const matchedWorkspaceId = parseInt(match[1])
      const documentId = parseInt(match[2])

      tempParts.push({
        type: 'document',
        content: (
          <DocumentPreviewCard
            key={`doc-${documentId}-${keyCounter++}`}
            documentId={documentId}
            workspaceId={matchedWorkspaceId}
          />
        )
      })

      lastIndex = match.index + match[0].length
    }

    // Add remaining text
    if (lastIndex < content.length) {
      tempParts.push({
        type: 'text',
        content: content.substring(lastIndex)
      })
    }

    // If no document links, add all content as text
    if (tempParts.length === 0) {
      tempParts.push({
        type: 'text',
        content: content
      })
    }

    // Now process mentions in text parts
    tempParts.forEach((part, partIndex) => {
      if (part.type === 'document') {
        parts.push(part.content as JSX.Element)
      } else {
        // Process mentions in this text part
        const textContent = part.content as string
        // Updated regex to support unicode characters (including Korean, Chinese, etc.)
        const mentionRegex = /@([a-zA-Z0-9_\u0080-\uFFFF]+)/gu
        const textParts: (string | JSX.Element)[] = []
        let lastMentionIndex = 0
        let mentionMatch

        while ((mentionMatch = mentionRegex.exec(textContent)) !== null) {
          // Add text before mention
          if (mentionMatch.index > lastMentionIndex) {
            textParts.push(textContent.substring(lastMentionIndex, mentionMatch.index))
          }

          // Add highlighted mention
          const mentionText = mentionMatch[0]
          textParts.push(
            <span key={`mention-${partIndex}-${keyCounter++}`} className={styles.mention}>
              {mentionText}
            </span>
          )

          lastMentionIndex = mentionMatch.index + mentionMatch[0].length
        }

        // Add remaining text
        if (lastMentionIndex < textContent.length) {
          textParts.push(textContent.substring(lastMentionIndex))
        }

        // If no mentions found, add the whole text
        if (textParts.length === 0) {
          textParts.push(textContent)
        }

        // Add all text parts
        textParts.forEach((textPart, idx) => {
          if (typeof textPart === 'string') {
            parts.push(<span key={`text-${partIndex}-${idx}`}>{textPart}</span>)
          } else {
            parts.push(textPart)
          }
        })
      }
    })

    return parts
  }

  return <>{parseContent()}</>
}
