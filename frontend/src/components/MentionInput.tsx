'use client'

import { useState, useRef, useEffect } from 'react'
import { WorkspaceMember } from '@/types'
import styles from './MentionInput.module.css'

interface MentionData {
  userId?: number
  mentionType: string
  displayText: string
}

interface MentionInputProps {
  value: string
  onChange: (value: string, mentions: MentionData[]) => void
  onSubmit: () => void
  placeholder?: string
  workspaceMembers: WorkspaceMember[]
}

export default function MentionInput({
  value,
  onChange,
  onSubmit,
  placeholder = 'Message...',
  workspaceMembers,
}: MentionInputProps) {
  const [showSuggestions, setShowSuggestions] = useState(false)
  const [suggestionIndex, setSuggestionIndex] = useState(0)
  const [mentionQuery, setMentionQuery] = useState('')
  const [mentionStartPos, setMentionStartPos] = useState(-1)
  const [mentions, setMentions] = useState<MentionData[]>([])
  const inputRef = useRef<HTMLInputElement>(null)

  // Special mentions like @everyone
  const specialMentions = [
    { displayText: '@everyone', mentionType: 'channel', description: '채널의 모든 멤버에게 알림' },
  ]

  // Filter suggestions based on query
  const getSuggestions = () => {
    const query = mentionQuery.toLowerCase()

    // Filter special mentions
    const filteredSpecialMentions = specialMentions.filter(m =>
      m.displayText.toLowerCase().includes(query)
    )

    // Filter user mentions
    const filteredUserMentions = workspaceMembers
      .filter(m =>
        m.userName.toLowerCase().includes(query) ||
        m.userEmail.toLowerCase().includes(query)
      )
      .map(m => ({
        userId: m.userId,
        displayText: `@${m.userName}`,
        mentionType: 'user',
        description: m.userEmail,
      }))

    return [...filteredSpecialMentions, ...filteredUserMentions]
  }

  const suggestions = getSuggestions()

  // Handle input change
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value
    const cursorPos = e.target.selectionStart || 0

    // Check if we're typing after @
    const textBeforeCursor = newValue.substring(0, cursorPos)
    const lastAtSymbol = textBeforeCursor.lastIndexOf('@')

    if (lastAtSymbol !== -1) {
      const textAfterAt = textBeforeCursor.substring(lastAtSymbol + 1)

      // Check if there's no space after @ (valid mention in progress)
      if (!textAfterAt.includes(' ') && textAfterAt.length >= 0) {
        setMentionQuery(textAfterAt)
        setMentionStartPos(lastAtSymbol)
        setShowSuggestions(true)
        setSuggestionIndex(0)
      } else {
        setShowSuggestions(false)
      }
    } else {
      setShowSuggestions(false)
    }

    onChange(newValue, mentions)
  }

  // Handle suggestion selection
  const selectSuggestion = (suggestion: any) => {
    if (mentionStartPos === -1) return

    const beforeMention = value.substring(0, mentionStartPos)
    const afterCursor = value.substring(inputRef.current?.selectionStart || value.length)
    const newValue = beforeMention + suggestion.displayText + ' ' + afterCursor

    // Add mention to mentions array
    const newMention: MentionData = {
      userId: suggestion.userId,
      mentionType: suggestion.mentionType,
      displayText: suggestion.displayText,
    }

    const newMentions = [...mentions, newMention]
    setMentions(newMentions)
    onChange(newValue, newMentions)

    setShowSuggestions(false)
    setMentionQuery('')
    setMentionStartPos(-1)

    // Focus back on input
    setTimeout(() => {
      inputRef.current?.focus()
    }, 0)
  }

  // Handle keyboard navigation
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (showSuggestions && suggestions.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setSuggestionIndex((prev) => (prev + 1) % suggestions.length)
      } else if (e.key === 'ArrowUp') {
        e.preventDefault()
        setSuggestionIndex((prev) => (prev - 1 + suggestions.length) % suggestions.length)
      } else if (e.key === 'Enter') {
        e.preventDefault()
        selectSuggestion(suggestions[suggestionIndex])
        return
      } else if (e.key === 'Escape') {
        setShowSuggestions(false)
      }
    } else if (e.key === 'Enter') {
      e.preventDefault()
      onSubmit()
    }
  }

  // Reset mentions when value is cleared
  useEffect(() => {
    if (value === '') {
      setMentions([])
    }
  }, [value])

  return (
    <div className={styles.mentionInputContainer}>
      <input
        ref={inputRef}
        type="text"
        value={value}
        onChange={handleInputChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        className={styles.mentionInput}
      />

      {showSuggestions && suggestions.length > 0 && (
        <div className={styles.suggestionsDropdown}>
          {suggestions.map((suggestion, index) => (
            <div
              key={index}
              className={`${styles.suggestionItem} ${
                index === suggestionIndex ? styles.selected : ''
              }`}
              onClick={() => selectSuggestion(suggestion)}
              onMouseEnter={() => setSuggestionIndex(index)}
            >
              <div className={styles.suggestionMain}>
                <span className={styles.suggestionName}>{suggestion.displayText}</span>
              </div>
              {suggestion.description && (
                <div className={styles.suggestionDescription}>{suggestion.description}</div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
