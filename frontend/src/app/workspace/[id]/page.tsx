'use client'

import { useEffect, useState, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useAppSelector, useAppDispatch } from '@/store/hooks'
import { setChannels, setCurrentChannel, removeChannel, resetUnreadCount, incrementUnreadCount } from '@/store/slices/channelSlice'
import { setMessages, addMessage, updateMessage, deleteMessage, addTypingUser, toggleMessageReaction } from '@/store/slices/messageSlice'
import { channelAPI, messageAPI, workspaceAPI, aiAPI, chatroomAPI, chatroomMessageAPI, searchAPI, userAPI, notificationAPI } from '@/services/api'
import { wsService } from '@/services/websocket'
import { Channel, Message as MessageType, WorkspaceMember, MessageDocument, Notification } from '@/types'
import MessageContent from '@/components/MessageContent'
import MentionInput from '@/components/MentionInput'
import styles from './workspace.module.css'

export default function WorkspacePage({
  params,
}: {
  params: { id: string }
}) {
  const workspaceId = parseInt(params.id)
  const router = useRouter()
  const dispatch = useAppDispatch()
  const { channels, currentChannel } = useAppSelector((state) => state.channel)
  const { channelMessages, chatroomMessages, typingUsers } = useAppSelector((state) => state.message)
  const { user, accessToken, isInitialized } = useAppSelector((state) => state.auth)
  const [messageInput, setMessageInput] = useState('')
  const [messageMentions, setMessageMentions] = useState<Array<{userId?: number, mentionType: string, displayText: string}>>([])
  const [threadMentions, setThreadMentions] = useState<Array<{userId?: number, mentionType: string, displayText: string}>>([])
  const [isTyping, setIsTyping] = useState(false)
  const [wsConnected, setWsConnected] = useState(false)
  const messageListRef = useRef<HTMLDivElement>(null)
  const threadListRef = useRef<HTMLDivElement>(null)
  const aiChatListRef = useRef<HTMLDivElement>(null)

  // Track previous message counts for auto-scroll
  const prevMessageCountRef = useRef<number>(0)
  const prevThreadCountRef = useRef<number>(0)
  const prevAiChatCountRef = useRef<number>(0)

  // Channel modal
  const [showChannelModal, setShowChannelModal] = useState(false)
  const [newChannelName, setNewChannelName] = useState('')
  const [newChannelDescription, setNewChannelDescription] = useState('')
  const [isPrivateChannel, setIsPrivateChannel] = useState(false)

  // DM modal
  const [showDMModal, setShowDMModal] = useState(false)
  const [workspaceMembers, setWorkspaceMembers] = useState<WorkspaceMember[]>([])
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null)

  // Invite modal
  const [showInviteModal, setShowInviteModal] = useState(false)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState('member')

  // Add channel member modal
  const [showAddMemberModal, setShowAddMemberModal] = useState(false)
  const [selectedChannelMemberId, setSelectedChannelMemberId] = useState<number | null>(null)

  // Thread state
  const [showThreadPanel, setShowThreadPanel] = useState(false)
  const [selectedThreadMessage, setSelectedThreadMessage] = useState<MessageType | null>(null)
  const [threadReplies, setThreadReplies] = useState<MessageType[]>([])
  const [threadReplyInput, setThreadReplyInput] = useState('')

  // Message actions state
  const [showEmojiPicker, setShowEmojiPicker] = useState<number | null>(null)
  const [showForwardModal, setShowForwardModal] = useState(false)
  const [messageToForward, setMessageToForward] = useState<MessageType | null>(null)

  // Confirmation modal state
  const [showConfirmModal, setShowConfirmModal] = useState(false)
  const [confirmModalData, setConfirmModalData] = useState<{
    title: string
    message: string
    confirmButtonText: string
    onConfirm: () => void
  } | null>(null)

  // Edit message state
  const [editingMessageId, setEditingMessageId] = useState<number | null>(null)
  const [editMessageInput, setEditMessageInput] = useState('')

  // Collapse state for sidebar sections
  const [isChannelsCollapsed, setIsChannelsCollapsed] = useState(false)
  const [isDMsCollapsed, setIsDMsCollapsed] = useState(false)

  // AI Chatbot state
  const [showAIChatbot, setShowAIChatbot] = useState(false)
  const [aiChatMessages, setAiChatMessages] = useState<Array<{ role: 'user' | 'ai', content: string }>>([])
  const [aiChatInput, setAiChatInput] = useState('')
  const [chatbotPosition, setChatbotPosition] = useState({ x: 100, y: 100 })
  const [isDragging, setIsDragging] = useState(false)
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 })

  // Chatroom state (DMs are now separate from channels)
  const [chatrooms, setChatrooms] = useState<any[]>([])
  const [currentChatroom, setCurrentChatroom] = useState<any>(null)

  // Notification state
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0)
  const [showNotificationsDropdown, setShowNotificationsDropdown] = useState(false)
  const notificationButtonRef = useRef<HTMLButtonElement>(null)

  // Channel header more menu state
  const [showChannelHeaderMenu, setShowChannelHeaderMenu] = useState(false)

  // Channel settings modal state
  const [showChannelSettingsModal, setShowChannelSettingsModal] = useState(false)
  const [channelMembers, setChannelMembers] = useState<any[]>([])
  const [settingsChannelName, setSettingsChannelName] = useState('')
  const [settingsIsPrivate, setSettingsIsPrivate] = useState(false)

  // Success/Info notification modal
  const [showNotificationModal, setShowNotificationModal] = useState(false)
  const [notificationMessage, setNotificationMessage] = useState('')

  // Search state
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searchResults, setSearchResults] = useState<MessageDocument[]>([])
  const [showSearchResults, setShowSearchResults] = useState(false)
  const [isSearching, setIsSearching] = useState(false)
  const searchInputRef = useRef<HTMLInputElement>(null)
  const searchDebounceTimer = useRef<NodeJS.Timeout | null>(null)

  // User profile modal state
  const [showUserProfileModal, setShowUserProfileModal] = useState(false)
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null)
  const [selectedUserProfile, setSelectedUserProfile] = useState<any>(null)

  // All channels are now regular channels (no DMs mixed in)
  const regularChannels = channels
  const directMessages = chatrooms

  // Reusable confirm dialog helper
  const showConfirm = (title: string, message: string, confirmButtonText: string, onConfirm: () => void) => {
    setConfirmModalData({ title, message, confirmButtonText, onConfirm })
    setShowConfirmModal(true)
  }

  // Reusable notification helper
  const showNotification = (message: string) => {
    setNotificationMessage(message)
    setShowNotificationModal(true)
  }

  // User profile modal helper
  const openUserProfile = async (userId: number | undefined | null) => {
    // Validate userId
    if (!userId || typeof userId !== 'number') {
      showNotification('사용자 ID가 유효하지 않습니다')
      return
    }

    if (userId === user?.id) {
      // 자기 자신이면 프로필 설정 페이지로 이동
      router.push('/profile')
      return
    }

    try {
      setSelectedUserId(userId)
      const response = await userAPI.getUserById(userId)
      setSelectedUserProfile(response.data)
      setShowUserProfileModal(true)
    } catch (error: any) {
      const errorMsg = error.response?.data?.message || '사용자 정보를 불러올 수 없습니다'
      showNotification(errorMsg)
    }
  }

  useEffect(() => {
    // Wait for auth to be initialized before checking
    if (!isInitialized) return

    if (!user || !accessToken) {
      router.push('/login')
      return
    }

    // Reset current channel and chatroom when workspace changes
    dispatch(setCurrentChannel(null))
    setCurrentChatroom(null)

    loadChannels()
    loadNotifications()
    connectWebSocket()

    // Electron 알림 클릭 시 네비게이션 처리
    if (typeof window !== 'undefined' && window.electron) {
      window.electron.onNavigateToMention((data) => {
        console.log('Navigate to mention:', data)

        if (data.channelId) {
          // 채널로 이동
          const channel = channels.find(c => c.id === data.channelId)
          if (channel) {
            dispatch(setCurrentChannel(channel))
            setCurrentChatroom(null)
          }
        } else if (data.chatroomId) {
          // 채팅방으로 이동
          router.push(`/workspace/${workspaceId}/chatroom/${data.chatroomId}`)
        }
      })
    }

    return () => {
      wsService.disconnect()
      setWsConnected(false)
    }
  }, [user, accessToken, isInitialized, workspaceId])

  const loadNotifications = async () => {
    try {
      const [notificationsResponse, countResponse] = await Promise.all([
        notificationAPI.getNotifications(workspaceId),
        notificationAPI.getUnreadCount(workspaceId),
      ])

      setNotifications(notificationsResponse.data)
      setUnreadNotificationsCount(countResponse.data)
    } catch (error) {
      console.error('Failed to load notifications:', error)
    }
  }

  const handleNotificationClick = async (notification: Notification) => {
    // Mark as read
    try {
      await notificationAPI.markAsRead(notification.id)
      setNotifications(prev =>
        prev.map(n => (n.id === notification.id ? { ...n, isRead: true } : n))
      )
      setUnreadNotificationsCount(prev => Math.max(0, prev - 1))
    } catch (error) {
      console.error('Failed to mark notification as read:', error)
    }

    // Navigate to the message
    if (notification.channelId) {
      const channel = channels.find(c => c.id === notification.channelId)
      if (channel) {
        dispatch(setCurrentChannel(channel))
        setCurrentChatroom(null)
      }
    } else if (notification.chatroomId) {
      const chatroom = chatrooms.find(c => c.id === notification.chatroomId)
      if (chatroom) {
        setCurrentChatroom(chatroom)
        dispatch(setCurrentChannel(null))
      }
    }

    setShowNotificationsDropdown(false)
  }

  const handleMarkAllAsRead = async () => {
    try {
      await notificationAPI.markAllAsRead(workspaceId)
      setNotifications(prev =>
        prev.map(n => ({ ...n, isRead: true }))
      )
      setUnreadNotificationsCount(0)
    } catch (error) {
      console.error('Failed to mark all as read:', error)
    }
  }

  const formatNotificationTime = (createdAt: string) => {
    const date = new Date(createdAt)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    const seconds = Math.floor(diff / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)

    if (days > 0) return `${days}일 전`
    if (hours > 0) return `${hours}시간 전`
    if (minutes > 0) return `${minutes}분 전`
    return '방금'
  }

  useEffect(() => {
    if (currentChannel) {
      loadMessages(currentChannel.id)

      // Only subscribe if WebSocket is connected
      if (wsConnected) {
        wsService.subscribeToChannel(currentChannel.id)
        wsService.subscribeToTyping(currentChannel.id)
      }

      // Reset unread count when viewing channel
      dispatch(resetUnreadCount(currentChannel.id))
    }
  }, [currentChannel, wsConnected])

  useEffect(() => {
    if (currentChatroom) {
      loadChatroomMessages(currentChatroom.id)

      // Only subscribe if WebSocket is connected
      if (wsConnected) {
        // Subscribe to chatroom messages (separate topic)
        wsService.subscribeToChatroom(currentChatroom.id)
        wsService.subscribeToChatroomTyping(currentChatroom.id)
      }
    }
  }, [currentChatroom, wsConnected])

  // Update read state when messages change in current channel
  useEffect(() => {
    // Only call API if user is authenticated and auth state is initialized
    if (!isInitialized || !accessToken || !user) {
      return
    }

    if (currentChannel) {
      const currentChannelMessages = channelMessages[currentChannel.id] || []
      if (currentChannelMessages.length > 0) {
        const lastMessage = currentChannelMessages[currentChannelMessages.length - 1]

        // Reset unread count
        dispatch(resetUnreadCount(currentChannel.id))

        // Update read state on backend
        messageAPI.updateReadState({
          channelId: currentChannel.id,
          lastReadMessageId: lastMessage.id,
        }).catch(error => {
          console.error('Failed to update read state', error)
        })
      }
    }
  }, [currentChannel, channelMessages, isInitialized, accessToken, user])

  const connectWebSocket = async () => {
    if (!accessToken) {
      console.error('No access token available')
      return
    }

    console.log('Attempting WebSocket connection...')

    try {
      await wsService.connect(accessToken)
      console.log('WebSocket connected successfully')
      setWsConnected(true)

      wsService.onMessage((message: MessageType) => {
        console.log('Received WebSocket message:', message)
        console.log('Message parentMessageId:', message.parentMessageId)
        console.log('Current selected thread:', selectedThreadMessage?.id)

        // Increment unread count (will only increment if not viewing this channel)
        dispatch(incrementUnreadCount(message.channelId))

        // If it's a thread reply
        if (message.parentMessageId) {
          console.log('This is a thread reply, parent:', message.parentMessageId)

          // Add to thread replies state (will show up if thread panel is open)
          setThreadReplies((prev) => {
            // Check if this reply already exists
            if (prev.some(r => r.id === message.id)) {
              console.log('Reply already exists, skipping')
              return prev
            }
            console.log('Adding reply to thread')
            return [...prev, message]
          })
        } else {
          // Add to main message list if it's not a thread reply
          console.log('Adding to main message list')
          dispatch(addMessage(message))
        }
      })

      // Chatroom message handler
      wsService.onChatroomMessage((message: any) => {
        console.log('Received WebSocket chatroom message:', message)
        // Add chatroom message to message list
        dispatch(addMessage(message))
      })

      wsService.onTyping((indicator) => {
        if (indicator.userId !== user?.id) {
          console.log('User typing:', indicator)
          dispatch(addTypingUser(indicator))
        }
      })

      // 메시지 수정 이벤트 핸들러
      wsService.onMessageUpdate((message: MessageType) => {
        console.log('Message updated via WebSocket:', message)
        dispatch(updateMessage(message))

        // 스레드 패널에서도 업데이트 반영
        if (selectedThreadMessage && selectedThreadMessage.id === message.id) {
          setSelectedThreadMessage(message)
        }
        setThreadReplies((prev) =>
          prev.map((reply) => (reply.id === message.id ? message : reply))
        )
      })

      // 메시지 삭제 이벤트 핸들러
      wsService.onMessageDelete((data: { channelId: number; messageId: number }) => {
        console.log('Message deleted via WebSocket:', data)
        dispatch(deleteMessage(data))

        // 스레드 패널에서도 삭제 반영
        setThreadReplies((prev) => prev.filter((reply) => reply.id !== data.messageId))

        // 삭제된 메시지가 현재 보고 있는 스레드의 부모 메시지라면 스레드 패널 닫기
        if (selectedThreadMessage && selectedThreadMessage.id === data.messageId) {
          handleCloseThread()
        }
      })

      // 멘션 알림 핸들러
      if (user) {
        wsService.subscribeToMentions(user.id)
        wsService.onMention((data) => {
          console.log('📨 Received mention notification:', data)
          // 읽지 않은 멘션 수 증가
          setUnreadMentions((prev) => prev + 1)

          // Electron 데스크톱 알림 표시
          if (typeof window !== 'undefined' && window.electron) {
            console.log('🔔 Electron is available, showing notification...')
            const message = data.payload?.message
            const sender = message?.sender

            const notificationData = {
              title: `${sender?.username || '누군가'}님이 멘션했습니다`,
              body: message?.content || '새로운 멘션이 있습니다',
              workspaceId: workspaceId,
              channelId: message?.channelId,
              chatroomId: message?.chatroomId,
            }

            console.log('📤 Sending notification to Electron:', notificationData)
            window.electron.notification.show(notificationData)
          } else {
            console.log('⚠️ Electron is not available, notification not shown')
          }
        })
      }
    } catch (error) {
      console.error('WebSocket connection failed:', error)
      setWsConnected(false)
      alert('WebSocket 연결 실패. 백엔드가 실행 중인지 확인하세요.')
    }
  }

  const loadChannels = async () => {
    try {
      const [channelsResponse, chatroomsResponse, membersResponse] = await Promise.all([
        channelAPI.getByWorkspace(workspaceId),
        chatroomAPI.getByWorkspace(workspaceId),
        workspaceAPI.getMembers(workspaceId),
      ])

      dispatch(setChannels(channelsResponse.data))
      setChatrooms(chatroomsResponse.data)
      setWorkspaceMembers(membersResponse.data)

      // Always select the first channel when loading channels for this workspace
      if (channelsResponse.data.length > 0) {
        dispatch(setCurrentChannel(channelsResponse.data[0]))
      }
    } catch (error) {
      console.error('Failed to load channels, chatrooms, and members', error)
    }
  }

  const loadChatrooms = async () => {
    try {
      const response = await chatroomAPI.getByWorkspace(workspaceId)
      setChatrooms(response.data)
    } catch (error) {
      console.error('Failed to load chatrooms', error)
    }
  }

  const loadMessages = async (channelId: number) => {
    try {
      const response = await messageAPI.getByChannel(channelId)
      dispatch(setMessages({ channelId, messages: response.data }))
    } catch (error) {
      console.error('Failed to load messages', error)
    }
  }

  const loadChatroomMessages = async (chatroomId: number) => {
    try {
      const response = await chatroomMessageAPI.getByChatroom(chatroomId)
      // Store chatroom messages separately
      dispatch(setMessages({ chatroomId, messages: response.data }))
    } catch (error) {
      console.error('Failed to load chatroom messages', error)
    }
  }

  const handleSendMessage = () => {
    if (!messageInput.trim()) return

    // Extract mention data
    const mentionedUserIds = messageMentions
      .filter(m => m.userId !== undefined)
      .map(m => m.userId!)
    const mentionTypes = messageMentions.map(m => m.mentionType)

    // Send to chatroom if chatroom is selected
    if (currentChatroom) {
      console.log('Sending chatroom message:', {
        workspaceId,
        chatroomId: currentChatroom.id,
        content: messageInput,
        mentionedUserIds,
        mentionTypes,
      })

      wsService.sendChatroomMessage({
        workspaceId,
        chatroomId: currentChatroom.id,
        content: messageInput,
        mentionedUserIds: mentionedUserIds.length > 0 ? mentionedUserIds : undefined,
        mentionTypes: mentionTypes.length > 0 ? mentionTypes : undefined,
      })
    }
    // Send to channel if channel is selected
    else if (currentChannel) {
      console.log('Sending channel message:', {
        workspaceId,
        channelId: currentChannel.id,
        content: messageInput,
        mentionedUserIds,
        mentionTypes,
      })

      wsService.sendMessage({
        workspaceId,
        channelId: currentChannel.id,
        content: messageInput,
        mentionedUserIds: mentionedUserIds.length > 0 ? mentionedUserIds : undefined,
        mentionTypes: mentionTypes.length > 0 ? mentionTypes : undefined,
      })
    }

    setMessageInput('')
    setMessageMentions([])
    setIsTyping(false)
  }

  const handleTyping = () => {
    if (!currentChannel && !currentChatroom) return

    if (!isTyping) {
      setIsTyping(true)

      if (currentChatroom) {
        wsService.sendChatroomTypingIndicator(currentChatroom.id, true)
      } else if (currentChannel) {
        wsService.sendTypingIndicator(currentChannel.id, true)
      }

      setTimeout(() => {
        setIsTyping(false)
        if (currentChatroom) {
          wsService.sendChatroomTypingIndicator(currentChatroom.id, false)
        } else if (currentChannel) {
          wsService.sendTypingIndicator(currentChannel.id, false)
        }
      }, 3000)
    }
  }

  const handleCreateChannel = async () => {
    if (!newChannelName.trim()) {
      alert('채널 이름을 입력해주세요')
      return
    }

    try {
      await channelAPI.create({
        workspaceId,
        name: newChannelName.trim(),
        description: newChannelDescription.trim() || undefined,
        isPrivate: isPrivateChannel,
      })

      await loadChannels()
      setShowChannelModal(false)
      setNewChannelName('')
      setNewChannelDescription('')
      setIsPrivateChannel(false)
    } catch (error) {
      console.error('Failed to create channel', error)
      alert('채널 생성에 실패했습니다')
    }
  }

  const handleDeleteCurrentChannel = async () => {
    if (!currentChannel) return

    showConfirm(
      '채널 삭제',
      `채널 "${currentChannel.name}"을(를) 삭제하시겠습니까?\n\n채널의 모든 메시지와 데이터가 삭제됩니다.`,
      '삭제',
      async () => {
        try {
          await channelAPI.delete(currentChannel.id)
          dispatch(removeChannel(currentChannel.id))
          setShowChannelHeaderMenu(false)
          setShowConfirmModal(false)
          alert('채널이 삭제되었습니다')
        } catch (error: any) {
          console.error('Failed to delete channel', error)
          if (error.response?.status === 403) {
            alert('채널을 삭제할 권한이 없습니다. 채널 생성자만 삭제할 수 있습니다.')
          } else {
            alert('채널 삭제에 실패했습니다')
          }
        }
      }
    )
  }

  const handleLeaveCurrentChannel = async () => {
    if (!currentChannel) return

    showConfirm(
      '채널 나가기',
      `채널 "${currentChannel.name}"에서 나가시겠습니까?`,
      '나가기',
      async () => {
        try {
          await channelAPI.leave(currentChannel.id)
          dispatch(removeChannel(currentChannel.id))
          setShowChannelHeaderMenu(false)
          setShowConfirmModal(false)
          alert('채널에서 나갔습니다')
        } catch (error: any) {
          console.error('Failed to leave channel', error)
          alert('채널 나가기에 실패했습니다')
        }
      }
    )
  }

  const handleOpenDMModal = async () => {
    setShowDMModal(true)
    try {
      const response = await workspaceAPI.getMembers(workspaceId)
      setWorkspaceMembers(response.data.filter((m: WorkspaceMember) => m.userId !== user?.id))
    } catch (error) {
      console.error('Failed to load workspace members', error)
    }
  }

  const handleCreateDM = async () => {
    if (!selectedMemberId) {
      alert('대화할 사용자를 선택해주세요')
      return
    }

    try {
      const response = await chatroomAPI.create({
        workspaceId,
        targetUserId: selectedMemberId,
      })

      // Switch to the chatroom
      setCurrentChatroom(response.data)
      dispatch(setCurrentChannel(null))
      await loadChatrooms()
      setShowDMModal(false)
      setSelectedMemberId(null)
    } catch (error) {
      console.error('Failed to create chatroom', error)
      alert('채팅방 생성에 실패했습니다')
    }
  }

  const handleInviteMember = async () => {
    if (!inviteEmail.trim()) {
      alert('이메일을 입력해주세요')
      return
    }

    // Email validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailRegex.test(inviteEmail)) {
      alert('올바른 이메일 형식이 아닙니다')
      return
    }

    try {
      await workspaceAPI.addMember(workspaceId, {
        email: inviteEmail,
        role: inviteRole,
      })

      alert('사용자를 초대했습니다')
      setShowInviteModal(false)
      setInviteEmail('')
      setInviteRole('member')
    } catch (error) {
      console.error('Failed to invite member', error)
      alert('사용자 초대에 실패했습니다')
    }
  }

  const handleOpenAddMemberModal = async () => {
    setShowAddMemberModal(true)
    try {
      const response = await workspaceAPI.getMembers(workspaceId)
      setWorkspaceMembers(response.data)
    } catch (error) {
      console.error('Failed to load workspace members', error)
    }
  }

  const handleAddChannelMember = async () => {
    if (!selectedChannelMemberId || !currentChannel) {
      alert('추가할 멤버를 선택해주세요')
      return
    }

    try {
      await channelAPI.addMember(currentChannel.id, selectedChannelMemberId)
      alert('멤버를 추가했습니다')
      setShowAddMemberModal(false)
      setSelectedChannelMemberId(null)
    } catch (error) {
      console.error('Failed to add member to channel', error)
      alert('멤버 추가에 실패했습니다')
    }
  }

  const handleCloseChatroom = async (chatroomId: number) => {
    try {
      await chatroomAPI.hide(chatroomId)

      // Remove from local chatroom list
      setChatrooms(chatrooms.filter(c => c.id !== chatroomId))

      // Clear current chatroom if it's the one being closed
      if (currentChatroom?.id === chatroomId) {
        setCurrentChatroom(null)
      }
    } catch (error) {
      console.error('Failed to close chatroom', error)
    }
  }

  const handleOpenThread = async (message: MessageType) => {
    console.log('Opening thread for message:', message.id, 'Content:', message.content)
    setSelectedThreadMessage(message)
    setShowThreadPanel(true)

    try {
      const response = await messageAPI.getThreadReplies(message.id)
      console.log('Loaded thread replies:', response.data.length, 'replies')
      setThreadReplies(response.data)
    } catch (error) {
      console.error('Failed to load thread replies', error)
    }
  }

  const handleSendThreadReply = () => {
    if (!threadReplyInput.trim() || !selectedThreadMessage || !currentChannel) {
      console.warn('Cannot send thread reply:', {
        hasInput: !!threadReplyInput.trim(),
        hasSelectedThread: !!selectedThreadMessage,
        hasCurrentChannel: !!currentChannel,
      })
      return
    }

    // Extract thread mention data
    const mentionedUserIds = threadMentions
      .filter(m => m.userId !== undefined)
      .map(m => m.userId!)
    const mentionTypes = threadMentions.map(m => m.mentionType)

    console.log('=== SENDING THREAD REPLY ===')
    console.log('Parent Message ID:', selectedThreadMessage.id)
    console.log('Parent Message Content:', selectedThreadMessage.content)
    console.log('Reply Content:', threadReplyInput)
    console.log('Channel ID:', currentChannel.id)
    console.log('Workspace ID:', workspaceId)
    console.log('Mentions:', { mentionedUserIds, mentionTypes })

    wsService.sendMessage({
      workspaceId,
      channelId: currentChannel.id,
      content: threadReplyInput,
      parentMessageId: selectedThreadMessage.id,
      mentionedUserIds: mentionedUserIds.length > 0 ? mentionedUserIds : undefined,
      mentionTypes: mentionTypes.length > 0 ? mentionTypes : undefined,
    })

    setThreadReplyInput('')
    setThreadMentions([])
  }

  const handleCloseThread = () => {
    setShowThreadPanel(false)
    setSelectedThreadMessage(null)
    setThreadReplies([])
    setThreadReplyInput('')
  }

  const handleEmojiClick = (messageId: number) => {
    setShowEmojiPicker(showEmojiPicker === messageId ? null : messageId)
  }

  const handleAddEmoji = async (messageId: number, emoji: string) => {
    if (!user || !currentChannel) return

    console.log('Toggle emoji reaction:', emoji, 'to message:', messageId)

    try {
      // Optimistically update UI
      dispatch(toggleMessageReaction({
        channelId: currentChannel.id,
        messageId,
        emoji,
        userId: user.id,
      }))

      // Call backend API
      await messageAPI.toggleReaction(messageId, emoji)

      setShowEmojiPicker(null)
    } catch (error) {
      console.error('Failed to toggle reaction:', error)
      // Revert optimistic update
      dispatch(toggleMessageReaction({
        channelId: currentChannel.id,
        messageId,
        emoji,
        userId: user.id,
      }))
      alert('이모지 반응 저장에 실패했습니다')
    }
  }

  const handleForwardMessage = (message: MessageType) => {
    setMessageToForward(message)
    setShowForwardModal(true)
  }

  const handleSendForward = (channelId: number) => {
    if (!messageToForward) return

    wsService.sendMessage({
      workspaceId,
      channelId,
      content: `[전달된 메시지]\n${messageToForward.content}`,
    })

    setShowForwardModal(false)
    setMessageToForward(null)
    alert('메시지가 전달되었습니다')
  }

  const handleStartEdit = (message: MessageType) => {
    setEditingMessageId(message.id)
    setEditMessageInput(message.content)
  }

  const handleCancelEdit = () => {
    setEditingMessageId(null)
    setEditMessageInput('')
  }

  const handleSaveEdit = async () => {
    if (!editingMessageId || !editMessageInput.trim()) return

    try {
      const response = await messageAPI.update(editingMessageId, editMessageInput)

      // Update the message in the local state
      if (currentChannel) {
        const currentChannelMessages = channelMessages[currentChannel.id] || []
        const updatedMessages = currentChannelMessages.map(msg =>
          msg.id === editingMessageId ? response.data : msg
        )
        dispatch(setMessages({ channelId: currentChannel.id, messages: updatedMessages }))

        // Also update thread replies if the edited message is in the thread
        setThreadReplies(prev => prev.map(reply =>
          reply.id === editingMessageId ? response.data : reply
        ))

        // Update the selected thread message if it's being edited
        if (selectedThreadMessage && selectedThreadMessage.id === editingMessageId) {
          setSelectedThreadMessage(response.data)
        }
      } else if (currentChatroom) {
        const currentChatroomMessages = chatroomMessages[currentChatroom.id] || []
        const updatedMessages = currentChatroomMessages.map(msg =>
          msg.id === editingMessageId ? response.data : msg
        )
        dispatch(setMessages({ chatroomId: currentChatroom.id, messages: updatedMessages }))
      }

      setEditingMessageId(null)
      setEditMessageInput('')
    } catch (error) {
      console.error('Failed to edit message', error)
      alert('메시지 수정에 실패했습니다')
    }
  }

  const handleDeleteMessage = async (messageId: number) => {
    showConfirm(
      '메시지 삭제',
      '이 메시지를 삭제하시겠습니까?',
      '삭제',
      async () => {
        try {
          await messageAPI.delete(messageId)

          // Reload messages to reflect deletion
          if (currentChannel) {
            await loadMessages(currentChannel.id)

            // If the deleted message is in a thread, reload thread replies
            if (selectedThreadMessage) {
              const response = await messageAPI.getThreadReplies(selectedThreadMessage.id)
              setThreadReplies(response.data)
            }
          }
          setShowConfirmModal(false)
        } catch (error) {
          console.error('Failed to delete message', error)
          alert('메시지 삭제에 실패했습니다')
        }
      }
    )
  }

  // Search handlers
  const handleSearchInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const keyword = e.target.value
    setSearchKeyword(keyword)

    // Clear previous timer
    if (searchDebounceTimer.current) {
      clearTimeout(searchDebounceTimer.current)
    }

    // If keyword is empty, clear results
    if (!keyword.trim()) {
      setSearchResults([])
      setShowSearchResults(false)
      setIsSearching(false)
      return
    }

    // Set new timer for debounced search
    setIsSearching(true)
    searchDebounceTimer.current = setTimeout(() => {
      performSearch(keyword.trim())
    }, 300) // 300ms debounce
  }

  const performSearch = async (keyword: string) => {
    try {
      const response = await searchAPI.search({
        workspaceId,
        keyword,
      })

      setSearchResults(response.data.results)
      setShowSearchResults(true)
      setIsSearching(false)
    } catch (error) {
      console.error('Search failed:', error)
      setIsSearching(false)
    }
  }

  const handleClearSearch = () => {
    setSearchKeyword('')
    setSearchResults([])
    setShowSearchResults(false)
    setIsSearching(false)
    if (searchDebounceTimer.current) {
      clearTimeout(searchDebounceTimer.current)
    }
  }

  const handleSearchResultClick = (result: MessageDocument) => {
    // Find the channel for this message
    if (result.metadata.channelId) {
      const channel = channels.find(c => c.id === result.metadata.channelId)
      if (channel) {
        dispatch(setCurrentChannel(channel))
        setCurrentChatroom(null)
        handleClearSearch()
      }
    }
  }

  const highlightKeyword = (text: string | null, keyword: string) => {
    if (!keyword || !text) return text || ''

    const parts = text.split(new RegExp(`(${keyword})`, 'gi'))
    return (
      <>
        {parts.map((part, index) =>
          part.toLowerCase() === keyword.toLowerCase() ? (
            <mark key={index} style={{ backgroundColor: '#ffeb3b', padding: '0 2px' }}>
              {part}
            </mark>
          ) : (
            part
          )
        )}
      </>
    )
  }

  const formatSearchResultTime = (createdAt: string) => {
    const date = new Date(createdAt)
    return date.toLocaleString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  }

  // Only show top-level messages (not thread replies) in main message list
  // Get messages for current channel or chatroom
  const currentMessages = currentChannel
    ? (channelMessages[currentChannel.id] || []).filter(msg => !msg.parentMessageId)
    : currentChatroom
    ? (chatroomMessages[currentChatroom.id] || []) // Chatrooms don't have threads, so no need to filter
    : []

  const currentTypingUsers = currentChannel
    ? typingUsers[currentChannel.id] || []
    : currentChatroom
    ? typingUsers[currentChatroom.id] || []
    : []

  // Auto-scroll to bottom only when new messages are added
  useEffect(() => {
    const currentCount = currentMessages.length
    if (messageListRef.current && currentCount > prevMessageCountRef.current) {
      messageListRef.current.scrollTop = messageListRef.current.scrollHeight
    }
    prevMessageCountRef.current = currentCount
  }, [currentMessages])

  // Auto-scroll to bottom only when new thread replies are added
  useEffect(() => {
    const currentCount = threadReplies.length
    if (threadListRef.current && showThreadPanel && currentCount > prevThreadCountRef.current) {
      threadListRef.current.scrollTop = threadListRef.current.scrollHeight
    }
    prevThreadCountRef.current = currentCount
  }, [threadReplies, showThreadPanel])

  // Auto-scroll to bottom only when new AI chat messages are added
  useEffect(() => {
    const currentCount = aiChatMessages.length
    if (aiChatListRef.current && currentCount > prevAiChatCountRef.current) {
      aiChatListRef.current.scrollTop = aiChatListRef.current.scrollHeight
    }
    prevAiChatCountRef.current = currentCount
  }, [aiChatMessages])

  // Load AI chat history when chatbot opens
  useEffect(() => {
    const loadAiChatHistory = async () => {
      if (showAIChatbot && aiChatMessages.length === 0) {
        try {
          const response = await aiAPI.getHistory()
          const history = response.data.map((item: any) => ({
            role: item.role,
            content: item.content
          }))
          setAiChatMessages(history)
        } catch (error) {
          console.error('AI 챗봇 히스토리 로드 실패:', error)
        }
      }
    }

    loadAiChatHistory()
  }, [showAIChatbot])

  // 날짜 포맷팅 헬퍼 함수
  const formatMessageTime = (createdAt: string | undefined) => {
    if (!createdAt) {
      console.log('No createdAt provided')
      return '방금'
    }

    try {
      // ISO 8601 형식이나 다른 형식을 처리
      const date = new Date(createdAt)

      // 유효한 날짜인지 확인
      if (isNaN(date.getTime())) {
        console.log('Invalid date:', createdAt)
        return '방금'
      }

      // 한국 시간으로 포맷
      return date.toLocaleTimeString('ko-KR', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: true,
      })
    } catch (error) {
      console.error('Date parsing error:', error, createdAt)
      return '방금'
    }
  }

  // AI Chatbot handlers
  const handleAIChat = async () => {
    if (!aiChatInput.trim()) return

    const userQuestion = aiChatInput
    setAiChatInput('')

    // Add user message
    setAiChatMessages(prev => [...prev, { role: 'user', content: userQuestion }])

    // Add loading message
    setAiChatMessages(prev => [...prev, {
      role: 'ai',
      content: '답변을 생성하고 있습니다...'
    }])

    try {
      // Call RAG API through backend
      const response = await aiAPI.chat({ question: userQuestion })

      // Remove loading message and add actual response
      setAiChatMessages(prev => {
        const newMessages = [...prev]
        newMessages[newMessages.length - 1] = {
          role: 'ai',
          content: response.data.answer || '죄송합니다. 응답을 생성할 수 없습니다.'
        }
        return newMessages
      })

    } catch (error) {
      console.error('AI 챗봇 API 호출 실패:', error)

      // Remove loading message and add error message
      setAiChatMessages(prev => {
        const newMessages = [...prev]
        newMessages[newMessages.length - 1] = {
          role: 'ai',
          content: '죄송합니다. 오류가 발생했습니다. 잠시 후 다시 시도해주세요.'
        }
        return newMessages
      })
    }
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true)
    setDragOffset({
      x: e.clientX - chatbotPosition.x,
      y: e.clientY - chatbotPosition.y
    })
  }

  const handleMouseMove = (e: MouseEvent) => {
    if (isDragging) {
      setChatbotPosition({
        x: e.clientX - dragOffset.x,
        y: e.clientY - dragOffset.y
      })
    }
  }

  const handleMouseUp = () => {
    setIsDragging(false)
  }

  useEffect(() => {
    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove)
      window.addEventListener('mouseup', handleMouseUp)
      return () => {
        window.removeEventListener('mousemove', handleMouseMove)
        window.removeEventListener('mouseup', handleMouseUp)
      }
    }
  }, [isDragging, dragOffset])

  return (
    <div className={styles.container}>
      <div className={styles.sidebar}>
        <div className={styles.workspaceHeader}>
          <div className={styles.workspaceTitleRow}>
            <h2>Workspace #{workspaceId}</h2>
            <button onClick={() => router.push('/workspaces')} className={styles.backButton} title="워크스페이스 목록으로">
              ✖️
            </button>
          </div>
          <button onClick={() => setShowInviteModal(true)} className={styles.inviteButton}>
            + 멤버 초대
          </button>
        </div>

        {/* Channels Section */}
        <div className={styles.section}>
          <div className={styles.sectionHeader} onClick={() => setIsChannelsCollapsed(!isChannelsCollapsed)}>
            <span>{isChannelsCollapsed ? '▶' : '▼'} Channels</span>
            <button
              onClick={(e) => {
                e.stopPropagation()
                setShowChannelModal(true)
              }}
              className={styles.addButton}
            >
              +
            </button>
          </div>
          {!isChannelsCollapsed && regularChannels.map((channel) => (
            <div
              key={channel.id}
              className={`${styles.channelItem} ${
                currentChannel?.id === channel.id ? styles.active : ''
              }`}
              onClick={() => {
                dispatch(setCurrentChannel(channel))
                setCurrentChatroom(null)
              }}
            >
              <span>
                {channel.isPrivate ? '🔒' : '#'} {channel.name}
              </span>
              {channel.unreadCount > 0 && (
                <span className={styles.unreadBadge}>{channel.unreadCount}</span>
              )}
            </div>
          ))}
        </div>

        {/* Direct Messages Section */}
        <div className={styles.section}>
          <div className={styles.sectionHeader} onClick={() => setIsDMsCollapsed(!isDMsCollapsed)}>
            <span>{isDMsCollapsed ? '▶' : '▼'} Direct Messages</span>
            <button
              onClick={(e) => {
                e.stopPropagation()
                handleOpenDMModal()
              }}
              className={styles.addButton}
            >
              +
            </button>
          </div>
          {!isDMsCollapsed && directMessages.map((chatroom) => (
            <div
              key={chatroom.id}
              className={`${styles.dmItem} ${
                currentChatroom?.id === chatroom.id ? styles.active : ''
              }`}
              onClick={() => {
                setCurrentChatroom(chatroom)
                dispatch(setCurrentChannel(null))
              }}
            >
              <div
                className={styles.dmAvatar}
                onClick={(e) => {
                  e.stopPropagation()
                  openUserProfile(chatroom.targetUserId)
                }}
                style={{ cursor: 'pointer' }}
              >
                {chatroom.avatarUrl ? (
                  <img src={chatroom.avatarUrl} alt={chatroom.name} />
                ) : (
                  <div className={styles.dmAvatarPlaceholder}>
                    {chatroom.name?.charAt(0)?.toUpperCase() || '?'}
                  </div>
                )}
              </div>
              <div className={styles.dmInfo}>
                <span className={styles.dmName}>{chatroom.name}</span>
              </div>
              <div className={styles.channelItemActions}>
                {chatroom.unreadCount > 0 && (
                  <span className={styles.unreadBadge}>{chatroom.unreadCount}</span>
                )}
                <button
                  onClick={(e) => {
                    e.stopPropagation()
                    handleCloseChatroom(chatroom.id)
                  }}
                  className={styles.closeChannelButton}
                  title="채팅방 닫기"
                >
                  ✕
                </button>
              </div>
            </div>
          ))}
        </div>

        {/* Document Archive Section */}
        <div
          className={styles.documentArchiveItem}
          onClick={() => router.push(`/workspace/${workspaceId}/documents`)}
        >
          <span>📁 문서 보관함</span>
        </div>

        {/* User Profile Section */}
        <div className={styles.userProfile}>
          <div className={styles.profileInfo}>
            <div className={styles.avatarContainer}>
              {user?.avatarUrl ? (
                <img src={user.avatarUrl} alt={user.name} className={styles.avatarImage} />
              ) : (
                <div className={styles.avatar}>
                  {user?.name?.charAt(0).toUpperCase() || 'U'}
                </div>
              )}
              <div className={`${styles.statusIndicator} ${
                user?.status === 'active' || !user?.status ? styles.statusActive :
                user?.status === 'away' ? styles.statusAway :
                user?.status === 'dnd' ? styles.statusDnd :
                user?.status === 'vacation' ? styles.statusVacation :
                user?.status === 'sick' ? styles.statusSick :
                styles.statusOffline
              }`}></div>
            </div>
            <div className={styles.userInfo}>
              <div className={styles.userName}>{user?.name || 'User'}</div>
              <div className={styles.userStatus}>
                {(!user?.status || user?.status === 'active') && '온라인'}
                {user?.status === 'offline' && '오프라인'}
                {user?.status === 'away' && '자리비움'}
                {user?.status === 'dnd' && '다른 용무 중'}
                {user?.status === 'vacation' && '연차'}
                {user?.status === 'sick' && '병가'}
              </div>
            </div>
          </div>
          <div className={styles.profileActions}>
            <button
              className={styles.settingsButton}
              onClick={() => router.push('/profile')}
              title="프로필 설정"
            >
              ⚙️
            </button>
          </div>
        </div>

        {/* Notifications Dropdown */}
        {showNotificationsDropdown && (
          <>
            <div
              style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                zIndex: 999,
              }}
              onClick={() => setShowNotificationsDropdown(false)}
            />
            <div className={styles.notificationsDropdown}>
              <div className={styles.notificationsHeader}>
                <h3>알림</h3>
                {unreadNotificationsCount > 0 && (
                  <button
                    className={styles.markAllReadButton}
                    onClick={handleMarkAllAsRead}
                  >
                    모두 읽음
                  </button>
                )}
              </div>
              <div className={styles.notificationsList}>
                {notifications.length === 0 ? (
                  <div className={styles.noNotifications}>
                    알림이 없습니다
                  </div>
                ) : (
                  notifications.map((notification) => (
                    <div
                      key={notification.id}
                      className={`${styles.notificationItem} ${
                        !notification.isRead ? styles.unread : ''
                      }`}
                      onClick={() => handleNotificationClick(notification)}
                    >
                      <div className={styles.notificationAvatar}>
                        {notification.senderAvatarUrl ? (
                          <img src={notification.senderAvatarUrl} alt={notification.senderName} />
                        ) : (
                          <div className={styles.notificationAvatarPlaceholder}>
                            {notification.senderName?.charAt(0).toUpperCase() || '?'}
                          </div>
                        )}
                      </div>
                      <div className={styles.notificationContent}>
                        <div className={styles.notificationText}>
                          {notification.content}
                        </div>
                        <div className={styles.notificationTime}>
                          {formatNotificationTime(notification.createdAt)}
                        </div>
                      </div>
                      {!notification.isRead && (
                        <div className={styles.notificationUnreadDot} />
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>
          </>
        )}
      </div>

      {/* Channel Create Modal */}
      {showChannelModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>새 채널 만들기</h3>
            <div className={styles.formGroup}>
              <label>채널 이름 *</label>
              <input
                type="text"
                value={newChannelName}
                onChange={(e) => setNewChannelName(e.target.value)}
                placeholder="예: 일반"
              />
            </div>
            <div className={styles.formGroup}>
              <label>설명 (선택)</label>
              <textarea
                value={newChannelDescription}
                onChange={(e) => setNewChannelDescription(e.target.value)}
                placeholder="채널 설명을 입력하세요"
                rows={3}
              />
            </div>
            <div className={styles.formGroup}>
              <label>
                <input
                  type="checkbox"
                  checked={isPrivateChannel}
                  onChange={(e) => setIsPrivateChannel(e.target.checked)}
                />
                비공개 채널로 만들기
              </label>
              <small>
                {isPrivateChannel
                  ? '초대받은 사람만 볼 수 있습니다'
                  : '워크스페이스의 모든 멤버가 볼 수 있습니다'}
              </small>
            </div>
            <div className={styles.modalActions}>
              <button
                onClick={() => {
                  setShowChannelModal(false)
                  setNewChannelName('')
                  setNewChannelDescription('')
                  setIsPrivateChannel(false)
                }}
              >
                취소
              </button>
              <button onClick={handleCreateChannel} className={styles.primaryButton}>
                생성
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DM Create Modal */}
      {showDMModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>Direct Message 시작하기</h3>
            <div className={styles.formGroup}>
              <label>대화할 사용자 선택</label>
              <select
                value={selectedMemberId || ''}
                onChange={(e) => setSelectedMemberId(Number(e.target.value))}
              >
                <option value="">선택하세요</option>
                {workspaceMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.userName} ({member.userEmail})
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.modalActions}>
              <button
                onClick={() => {
                  setShowDMModal(false)
                  setSelectedMemberId(null)
                }}
              >
                취소
              </button>
              <button onClick={handleCreateDM} className={styles.primaryButton}>
                시작
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Invite Member Modal */}
      {showInviteModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>워크스페이스에 멤버 초대</h3>
            <div className={styles.formGroup}>
              <label>이메일 *</label>
              <input
                type="email"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                placeholder="초대할 사용자의 이메일을 입력하세요"
              />
            </div>
            <div className={styles.formGroup}>
              <label>역할</label>
              <select value={inviteRole} onChange={(e) => setInviteRole(e.target.value)}>
                <option value="member">Member</option>
                <option value="admin">Admin</option>
              </select>
              <small>Admin은 멤버 추가/제거 권한이 있습니다</small>
            </div>
            <div className={styles.modalActions}>
              <button
                onClick={() => {
                  setShowInviteModal(false)
                  setInviteEmail('')
                  setInviteRole('member')
                }}
              >
                취소
              </button>
              <button onClick={handleInviteMember} className={styles.primaryButton}>
                초대
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Channel Member Modal */}
      {showAddMemberModal && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>채널에 멤버 추가</h3>
            <div className={styles.formGroup}>
              <label>추가할 멤버 선택</label>
              <select
                value={selectedChannelMemberId || ''}
                onChange={(e) => setSelectedChannelMemberId(Number(e.target.value))}
              >
                <option value="">선택하세요</option>
                {workspaceMembers.map((member) => (
                  <option key={member.userId} value={member.userId}>
                    {member.userName} ({member.userEmail})
                  </option>
                ))}
              </select>
            </div>
            <div className={styles.modalActions}>
              <button
                onClick={() => {
                  setShowAddMemberModal(false)
                  setSelectedChannelMemberId(null)
                }}
              >
                취소
              </button>
              <button onClick={handleAddChannelMember} className={styles.primaryButton}>
                추가
              </button>
            </div>
          </div>
        </div>
      )}

      <div className={styles.main}>
        {currentChannel || currentChatroom ? (
          <>
            <div className={styles.header}>
              <h2>
                {currentChatroom
                  ? `● ${currentChatroom.name}`
                  : `# ${currentChannel?.name || ''}`}
              </h2>
              {currentChannel && (
                <div className={styles.headerButtons}>
                  {currentChannel.isPrivate && (
                    <button onClick={handleOpenAddMemberModal} className={styles.addMemberButton}>
                      + 멤버 추가
                    </button>
                  )}
                  {/* Search Input */}
                  <div style={{ position: 'relative' }}>
                    <div className={styles.searchContainer}>
                      <input
                        ref={searchInputRef}
                        type="text"
                        className={styles.searchInput}
                        placeholder="메시지 검색..."
                        value={searchKeyword}
                        onChange={handleSearchInputChange}
                      />
                      {searchKeyword && (
                        <button
                          className={styles.searchClearButton}
                          onClick={handleClearSearch}
                        >
                          ✕
                        </button>
                      )}
                      {isSearching && (
                        <span className={styles.searchSpinner}>⏳</span>
                      )}
                    </div>
                    {/* Search Results Dropdown */}
                    {showSearchResults && (
                      <>
                        <div
                          style={{
                            position: 'fixed',
                            top: 0,
                            left: 0,
                            right: 0,
                            bottom: 0,
                            zIndex: 999,
                          }}
                          onClick={handleClearSearch}
                        />
                        <div className={styles.searchResultsDropdown}>
                          {searchResults.length === 0 ? (
                            <div className={styles.searchNoResults}>
                              검색 결과가 없습니다
                            </div>
                          ) : (
                            <>
                              <div className={styles.searchResultsHeader}>
                                {searchResults.length}개의 결과
                              </div>
                              <div className={styles.searchResultsList}>
                                {searchResults.map((result, index) => (
                                  <div
                                    key={result.metadata.chunkId || `search-${index}`}
                                    className={styles.searchResultItem}
                                    onClick={() => handleSearchResultClick(result)}
                                  >
                                    <div className={styles.searchResultHeader}>
                                      <span className={styles.searchResultUser}>
                                        {result.metadata.userName || '알 수 없음'}
                                      </span>
                                      <span className={styles.searchResultChannel}>
                                        #{result.metadata.channelName || '알 수 없음'}
                                      </span>
                                      {result.metadata.createdAt && (
                                        <span className={styles.searchResultTime}>
                                          {formatSearchResultTime(result.metadata.createdAt)}
                                        </span>
                                      )}
                                    </div>
                                    <div className={styles.searchResultContent}>
                                      {highlightKeyword(result.text, searchKeyword)}
                                    </div>
                                  </div>
                                ))}
                              </div>
                            </>
                          )}
                        </div>
                      </>
                    )}
                  </div>
                  <button
                    className={styles.headerIconButton}
                    onClick={() => setShowNotificationsDropdown(!showNotificationsDropdown)}
                    title="알림"
                    style={{ position: 'relative' }}
                  >
                    🔔
                    {unreadNotificationsCount > 0 && (
                      <span className={styles.notificationBadge}>{unreadNotificationsCount}</span>
                    )}
                  </button>
                  <button
                    className={styles.headerIconButton}
                    onClick={async () => {
                      setSettingsChannelName(currentChannel.name)
                      setSettingsIsPrivate(currentChannel.isPrivate)
                      setShowChannelSettingsModal(true)
                      // 채널 멤버 목록 로드
                      try {
                        if (currentChannel.isPrivate) {
                          // 비공개 채널: 채널 멤버만 표시
                          const res = await channelAPI.getMembers(currentChannel.id)
                          setChannelMembers(res.data)
                        } else {
                          // 공개 채널: 워크스페이스의 모든 멤버 표시
                          const res = await workspaceAPI.getMembers(workspaceId)
                          setChannelMembers(res.data.map((m: any) => ({
                            id: m.userId,
                            name: m.userName,
                            email: m.userEmail,
                            avatarUrl: m.userAvatarUrl,
                            role: m.role || 'member'
                          })))
                        }
                      } catch (err) {
                        console.error('Failed to load members:', err)
                      }
                    }}
                    title="채널 설정"
                  >
                    ⚙️
                  </button>
                </div>
              )}
            </div>

            <div className={styles.messageList} ref={messageListRef}>
              {currentMessages.map((msg) => (
                <div key={msg.id} className={styles.message}>
                  {/* Message Toolbar */}
                  <div className={styles.messageToolbar}>
                    <button
                      onClick={() => handleEmojiClick(msg.id)}
                      title="이모지 반응"
                    >
                      😊
                    </button>
                    {currentChannel && !currentChatroom && (
                      <button
                        onClick={() => handleOpenThread(msg)}
                        title="스레드 답글"
                      >
                        💬
                      </button>
                    )}
                    <button
                      onClick={() => handleForwardMessage(msg)}
                      title="메시지 전달"
                    >
                      ➤
                    </button>
                    {user?.id === msg.userId && (
                      <>
                        <button
                          onClick={() => handleStartEdit(msg)}
                          title="메시지 수정"
                        >
                          ✏️
                        </button>
                        <button
                          onClick={() => handleDeleteMessage(msg.id)}
                          title="메시지 삭제"
                        >
                          🗑️
                        </button>
                      </>
                    )}
                  </div>

                  <div className={styles.messageContainer}>
                    <div
                      className={styles.messageAvatar}
                      onClick={() => openUserProfile(msg.userId)}
                      style={{ cursor: 'pointer' }}
                    >
                      {msg.userAvatarUrl ? (
                        <img src={msg.userAvatarUrl} alt={msg.userName} />
                      ) : (
                        <div className={styles.avatarPlaceholder}>
                          {msg.userName?.charAt(0).toUpperCase() || 'U'}
                        </div>
                      )}
                    </div>
                    <div className={styles.messageBody}>
                      <div className={styles.messageHeader}>
                        <strong>{msg.userName}</strong>
                        <span className={styles.timestamp}>
                          {formatMessageTime(msg.createdAt)}
                          {msg.isEdited && <span className={styles.editedLabel}> (수정됨)</span>}
                        </span>
                      </div>

                  {editingMessageId === msg.id ? (
                    <div className={styles.editMessageArea}>
                      <input
                        type="text"
                        value={editMessageInput}
                        onChange={(e) => setEditMessageInput(e.target.value)}
                        onKeyPress={(e) => {
                          if (e.key === 'Enter') {
                            handleSaveEdit()
                          }
                          if (e.key === 'Escape') {
                            handleCancelEdit()
                          }
                        }}
                        autoFocus
                      />
                      <div className={styles.editActions}>
                        <button onClick={handleSaveEdit} className={styles.saveButton}>저장</button>
                        <button onClick={handleCancelEdit} className={styles.cancelButton}>취소</button>
                      </div>
                    </div>
                  ) : (
                    <div className={styles.messageContent}>
                      <MessageContent content={msg.content} workspaceId={workspaceId} currentUserId={user?.id} mentions={msg.mentions} />
                    </div>
                  )}

                  {/* Emoji Reactions Display */}
                  {msg.reactions && Object.keys(msg.reactions).length > 0 && (
                    <div className={styles.reactions}>
                      {Object.entries(msg.reactions).map(([emoji, userIds]) => (
                        <button
                          key={emoji}
                          className={`${styles.reactionBadge} ${user && userIds.includes(user.id) ? styles.reactionActive : ''}`}
                          onClick={() => handleAddEmoji(msg.id, emoji)}
                        >
                          {emoji} {userIds.length}
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Emoji Picker */}
                  {showEmojiPicker === msg.id && (
                    <div className={styles.emojiPicker}>
                      {['✅','👍', '❤️', '😂', '😮', '😢', '🎉', '🔥', '👏'].map((emoji) => (
                        <button
                          key={emoji}
                          onClick={() => handleAddEmoji(msg.id, emoji)}
                          className={styles.emojiButton}
                        >
                          {emoji}
                        </button>
                      ))}
                    </div>
                  )}

                  {msg.replyCount > 0 && (
                    <div className={styles.messageActions}>
                      <button
                        className={styles.replyButton}
                        onClick={() => handleOpenThread(msg)}
                      >
                        💬 {msg.replyCount}개 답글
                      </button>
                    </div>
                  )}
                    </div>
                  </div>
                </div>
              ))}

              {currentTypingUsers.length > 0 && (
                <div className={styles.typingIndicator}>
                  {currentTypingUsers.map((u) => u.userName).join(', ')} typing...
                </div>
              )}
            </div>

            <div className={styles.inputArea}>
              <MentionInput
                value={messageInput}
                onChange={(value, mentions) => {
                  setMessageInput(value)
                  setMessageMentions(mentions)
                  handleTyping()
                }}
                onSubmit={handleSendMessage}
                placeholder={
                  currentChatroom
                    ? `Message ${currentChatroom.name}`
                    : currentChannel
                    ? `Message #${currentChannel.name}`
                    : 'Select a channel or chatroom'
                }
                workspaceMembers={workspaceMembers}
              />
              <button onClick={handleSendMessage}>전송</button>
            </div>
          </>
        ) : (
          <div className={styles.noChannel}>
            <p>Select a channel or DM to start messaging</p>
          </div>
        )}
      </div>

      {/* Forward Message Modal */}
      {showForwardModal && messageToForward && (
        <div className={styles.modal}>
          <div className={styles.modalContent}>
            <h3>메시지 전달</h3>
            <div className={styles.forwardPreview}>
              <p><strong>전달할 메시지:</strong></p>
              <div className={styles.previewMessage}>{messageToForward.content}</div>
            </div>
            <div className={styles.formGroup}>
              <label>전달할 채널 선택</label>
              <div className={styles.channelList}>
                {channels.map((channel) => (
                  <button
                    key={channel.id}
                    className={styles.channelSelectButton}
                    onClick={() => handleSendForward(channel.id)}
                  >
                    {'# '}
                    {channel.name}
                  </button>
                ))}
              </div>
            </div>
            <div className={styles.modalActions}>
              <button
                onClick={() => {
                  setShowForwardModal(false)
                  setMessageToForward(null)
                }}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Thread Panel */}
      {showThreadPanel && selectedThreadMessage && (
        <div className={styles.threadPanel}>
          <div className={styles.threadHeader}>
            <div>
              <h3>스레드</h3>
              <div style={{ fontSize: '12px', color: '#616061', marginTop: '4px' }}>
                원본: {selectedThreadMessage.content.substring(0, 50)}
                {selectedThreadMessage.content.length > 50 ? '...' : ''}
              </div>
            </div>
            <button onClick={handleCloseThread} className={styles.closeButton}>
              ✕
            </button>
          </div>

          <div className={styles.threadContent} ref={threadListRef}>
            {/* Parent Message */}
            <div className={styles.threadParentMessage}>
              <div className={styles.messageContainer}>
                <div className={styles.messageAvatar}>
                  {selectedThreadMessage.userAvatarUrl ? (
                    <img src={selectedThreadMessage.userAvatarUrl} alt={selectedThreadMessage.userName} />
                  ) : (
                    <div className={styles.avatarPlaceholder}>
                      {selectedThreadMessage.userName?.charAt(0).toUpperCase() || 'U'}
                    </div>
                  )}
                </div>
                <div className={styles.messageBody}>
                  <div className={styles.messageHeader}>
                    <strong>{selectedThreadMessage.userName}</strong>
                    <span className={styles.timestamp}>
                      {formatMessageTime(selectedThreadMessage.createdAt)}
                    </span>
                  </div>
                  <div className={styles.messageContent}>
                    <MessageContent content={selectedThreadMessage.content} workspaceId={workspaceId} currentUserId={user?.id} mentions={selectedThreadMessage.mentions} />
                  </div>
                </div>
              </div>
              <div className={styles.threadDivider}>{threadReplies.length}개의 답글</div>
            </div>

            {/* Thread Replies */}
            <div className={styles.threadReplies}>
              {threadReplies.map((reply) => (
                <div key={reply.id} className={styles.threadReply}>
                  {/* Reply Toolbar */}
                  <div className={styles.messageToolbar}>
                    <button
                      onClick={() => handleEmojiClick(reply.id)}
                      title="이모지 반응"
                    >
                      😊
                    </button>
                    <button
                      onClick={() => handleForwardMessage(reply)}
                      title="메시지 전달"
                    >
                      ➤
                    </button>
                    {user?.id === reply.userId && (
                      <>
                        <button
                          onClick={() => handleStartEdit(reply)}
                          title="메시지 수정"
                        >
                          ✏️
                        </button>
                        <button
                          onClick={() => handleDeleteMessage(reply.id)}
                          title="메시지 삭제"
                        >
                          🗑️
                        </button>
                      </>
                    )}
                  </div>

                  <div className={styles.messageContainer}>
                    <div
                      className={styles.messageAvatar}
                      onClick={() => openUserProfile(reply.userId)}
                      style={{ cursor: 'pointer' }}
                    >
                      {reply.userAvatarUrl ? (
                        <img src={reply.userAvatarUrl} alt={reply.userName} />
                      ) : (
                        <div className={styles.avatarPlaceholder}>
                          {reply.userName?.charAt(0).toUpperCase() || 'U'}
                        </div>
                      )}
                    </div>
                    <div className={styles.messageBody}>
                      <div className={styles.messageHeader}>
                        <strong>{reply.userName}</strong>
                        <span className={styles.timestamp}>
                          {formatMessageTime(reply.createdAt)}
                          {reply.isEdited && <span className={styles.editedLabel}> (수정됨)</span>}
                        </span>
                      </div>

                  {editingMessageId === reply.id ? (
                    <div className={styles.editMessageArea}>
                      <input
                        type="text"
                        value={editMessageInput}
                        onChange={(e) => setEditMessageInput(e.target.value)}
                        onKeyPress={(e) => {
                          if (e.key === 'Enter') {
                            handleSaveEdit()
                          }
                          if (e.key === 'Escape') {
                            handleCancelEdit()
                          }
                        }}
                        autoFocus
                      />
                      <div className={styles.editActions}>
                        <button onClick={handleSaveEdit} className={styles.saveButton}>저장</button>
                        <button onClick={handleCancelEdit} className={styles.cancelButton}>취소</button>
                      </div>
                    </div>
                  ) : (
                    <div className={styles.messageContent}>
                      <MessageContent content={reply.content} workspaceId={workspaceId} currentUserId={user?.id} mentions={reply.mentions} />
                    </div>
                  )}

                  {/* Emoji Reactions Display */}
                  {reply.reactions && Object.keys(reply.reactions).length > 0 && (
                    <div className={styles.reactions}>
                      {Object.entries(reply.reactions).map(([emoji, userIds]) => (
                        <button
                          key={emoji}
                          className={`${styles.reactionBadge} ${user && userIds.includes(user.id) ? styles.reactionActive : ''}`}
                          onClick={() => handleAddEmoji(reply.id, emoji)}
                        >
                          {emoji} {userIds.length}
                        </button>
                      ))}
                    </div>
                  )}

                  {/* Emoji Picker */}
                  {showEmojiPicker === reply.id && (
                    <div className={styles.emojiPicker}>
                      {['✅','👍', '❤️', '😂', '😮', '😢', '🎉', '🔥', '👏'].map((emoji) => (
                        <button
                          key={emoji}
                          onClick={() => handleAddEmoji(reply.id, emoji)}
                          className={styles.emojiButton}
                        >
                          {emoji}
                        </button>
                      ))}
                    </div>
                  )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Thread Reply Input */}
          <div className={styles.threadInput}>
            <MentionInput
              value={threadReplyInput}
              onChange={(value, mentions) => {
                setThreadReplyInput(value)
                setThreadMentions(mentions)
              }}
              onSubmit={handleSendThreadReply}
              placeholder="답글 입력..."
              workspaceMembers={workspaceMembers}
            />
            <button onClick={handleSendThreadReply}>전송</button>
          </div>
        </div>
      )}

      {/* AI Chatbot Floating Button */}
      {!showAIChatbot && (
        <button
          className={styles.aiChatbotButton}
          onClick={() => setShowAIChatbot(true)}
          title="AI 도우미"
        >
          🤖
        </button>
      )}

      {/* AI Chatbot Popup */}
      {showAIChatbot && (
        <div
          className={styles.aiChatbotPopup}
          style={{
            left: `${chatbotPosition.x}px`,
            top: `${chatbotPosition.y}px`,
          }}
        >
          <div
            className={styles.aiChatbotHeader}
            onMouseDown={handleMouseDown}
          >
            <div className={styles.aiChatbotTitle}>
              <span>🤖 AI 도우미</span>
              <small>질문에 답변해드립니다</small>
            </div>
            <button
              className={styles.aiChatbotClose}
              onClick={() => setShowAIChatbot(false)}
            >
              ✕
            </button>
          </div>

          <div className={styles.aiChatbotMessages} ref={aiChatListRef}>
            {aiChatMessages.length === 0 ? (
              <div className={styles.aiChatbotWelcome}>
                <p>안녕하세요! 무엇을 도와드릴까요?</p>
                <p className={styles.aiChatbotHint}>아래에 질문을 입력해주세요</p>
              </div>
            ) : (
              aiChatMessages.map((msg, index) => (
                <div
                  key={index}
                  className={msg.role === 'user' ? styles.aiUserMessage : styles.aiAssistantMessage}
                >
                  <div className={styles.aiMessageIcon}>
                    {msg.role === 'user' ? '👤' : '🤖'}
                  </div>
                  <div className={styles.aiMessageContent}>
                    {msg.content}
                  </div>
                </div>
              ))
            )}
          </div>

          <div className={styles.aiChatbotInput}>
            <input
              type="text"
              placeholder="메시지를 입력하세요..."
              value={aiChatInput}
              onChange={(e) => setAiChatInput(e.target.value)}
              onKeyPress={(e) => {
                if (e.key === 'Enter') {
                  handleAIChat()
                }
              }}
            />
            <button onClick={handleAIChat}>전송</button>
          </div>
        </div>
      )}

      {/* Channel Settings Modal */}
      {showChannelSettingsModal && currentChannel && (
        <div className={styles.modal}>
          <div className={styles.settingsModalContent}>
            <div className={styles.settingsModalHeader}>
              <div>
                <h2>채널 설정</h2>
                <p className={styles.settingsSubtitle}>
                  {currentChannel.isPrivate ? '🔒' : '#'} {currentChannel.name}
                </p>
              </div>
              <button
                onClick={() => setShowChannelSettingsModal(false)}
                className={styles.closeButton}
              >
                ✕
              </button>
            </div>

            <div className={styles.settingsModalBody}>
              {/* 채널명 변경 */}
              <div className={styles.settingSection}>
                <h3 className={styles.settingSectionTitle}>채널명</h3>
                <div className={styles.settingInputGroup}>
                  <input
                    type="text"
                    value={settingsChannelName}
                    onChange={(e) => setSettingsChannelName(e.target.value)}
                    placeholder="새 채널명 입력"
                    className={styles.settingInput}
                  />
                  <button
                    onClick={async () => {
                      if (!settingsChannelName.trim()) {
                        showNotification('채널명을 입력해주세요')
                        return
                      }
                      try {
                        const response = await channelAPI.updateName(currentChannel.id, settingsChannelName)
                        dispatch(setCurrentChannel(response.data))
                        const channelsResponse = await channelAPI.getByWorkspace(workspaceId)
                        dispatch(setChannels(channelsResponse.data))
                        showNotification('채널명이 변경되었습니다')
                      } catch (error) {
                        console.error('Failed to update channel name:', error)
                        showNotification('채널명 변경에 실패했습니다')
                      }
                    }}
                    className={styles.settingActionButton}
                  >
                    변경
                  </button>
                </div>
              </div>

              {/* 비공개 여부 변경 (채널 생성자만) */}
              {currentChannel.createdBy === user?.id && (
                <div className={styles.settingSection}>
                  <h3 className={styles.settingSectionTitle}>채널 공개 설정</h3>
                  <div className={styles.settingToggleGroup}>
                    <label className={styles.settingToggle}>
                      <input
                        type="checkbox"
                        checked={settingsIsPrivate}
                        onChange={(e) => setSettingsIsPrivate(e.target.checked)}
                        className={styles.settingCheckbox}
                      />
                      <span>비공개 채널로 설정</span>
                    </label>
                    <button
                      onClick={async () => {
                        try {
                          const response = await channelAPI.updatePrivacy(currentChannel.id, settingsIsPrivate)
                          dispatch(setCurrentChannel(response.data))
                          const channelsResponse = await channelAPI.getByWorkspace(workspaceId)
                          dispatch(setChannels(channelsResponse.data))
                          showNotification('채널 공개 설정이 변경되었습니다')
                        } catch (error) {
                          console.error('Failed to update channel privacy:', error)
                          showNotification('채널 공개 설정 변경에 실패했습니다')
                        }
                      }}
                      className={styles.settingActionButton}
                    >
                      적용
                    </button>
                  </div>
                  <p className={styles.settingHint}>
                    비공개 채널은 초대된 멤버만 볼 수 있습니다
                  </p>
                </div>
              )}

              {/* 멤버 관리 */}
              <div className={styles.settingSection}>
                <div className={styles.settingSectionHeader}>
                  <h3 className={styles.settingSectionTitle}>
                    멤버 관리 ({channelMembers.length})
                  </h3>
                  {currentChannel.isPrivate && currentChannel.createdBy === user?.id && (
                    <button
                      onClick={() => {
                        setShowChannelSettingsModal(false)
                        handleOpenAddMemberModal()
                      }}
                      className={styles.addMemberSmallButton}
                    >
                      + 멤버 추가
                    </button>
                  )}
                </div>
                <div className={styles.membersList}>
                  {channelMembers.map((member) => (
                    <div key={member.id} className={styles.memberItem}>
                      <div className={styles.memberInfo}>
                        <div
                          className={styles.memberAvatarWrapper}
                          onClick={() => openUserProfile(member.id)}
                          style={{ cursor: 'pointer' }}
                        >
                          {member.avatarUrl ? (
                            <img src={member.avatarUrl} alt={member.name} className={styles.memberAvatar} />
                          ) : (
                            <div className={styles.memberAvatarPlaceholder}>
                              {member.name[0].toUpperCase()}
                            </div>
                          )}
                        </div>
                        <div className={styles.memberDetails}>
                          <div className={styles.memberName}>
                            {member.name}
                            {member.id === currentChannel.createdBy && (
                              <span className={styles.memberBadge}>관리자</span>
                            )}
                          </div>
                          <div className={styles.memberEmail}>{member.email}</div>
                        </div>
                      </div>
                      <div className={styles.memberActions}>
                        {currentChannel.createdBy === user?.id && member.id !== currentChannel.createdBy && (
                          <>
                            <select
                              value={member.role || 'member'}
                              onChange={async (e) => {
                                const newRole = e.target.value
                                const oldRole = member.role || 'member'

                                // 관리자를 일반 멤버로 강등하는 경우, 관리자가 2명 이상인지 확인
                                if (oldRole === 'admin' && newRole === 'member') {
                                  const adminCount = channelMembers.filter(m => m.role === 'admin' || m.id === currentChannel.createdBy).length
                                  if (adminCount < 2) {
                                    showNotification('최소 1명의 관리자가 필요합니다')
                                    return
                                  }
                                }

                                try {
                                  console.log('Updating member role:', {
                                    channelId: currentChannel.id,
                                    userId: member.id,
                                    newRole
                                  })
                                  await channelAPI.updateMemberRole(currentChannel.id, member.id, newRole)
                                  // 멤버 목록 다시 로드
                                  if (currentChannel.isPrivate) {
                                    const membersResponse = await channelAPI.getMembers(currentChannel.id)
                                    setChannelMembers(membersResponse.data)
                                  } else {
                                    const res = await workspaceAPI.getMembers(workspaceId)
                                    setChannelMembers(res.data.map((m: any) => ({
                                      id: m.userId,
                                      name: m.userName,
                                      email: m.userEmail,
                                      avatarUrl: m.userAvatarUrl,
                                      role: m.role || 'member'
                                    })))
                                  }
                                  showNotification('멤버 권한이 변경되었습니다')
                                } catch (error: any) {
                                  console.error('Failed to update member role:', error)
                                  console.error('Error details:', error.response?.data)
                                  const errorMsg = error.response?.data?.message || '멤버 권한 변경에 실패했습니다'
                                  showNotification(errorMsg)
                                }
                              }}
                              className={styles.memberRoleSelect}
                            >
                              <option value="member">일반 멤버</option>
                              <option value="admin">관리자</option>
                            </select>
                            {currentChannel.isPrivate && (
                              <button
                                onClick={() => {
                                  showConfirm(
                                    '멤버 추방',
                                    `${member.name}님을 채널에서 추방하시겠습니까?`,
                                    '추방',
                                    async () => {
                                      try {
                                        await channelAPI.removeMember(currentChannel.id, member.id)
                                        const membersResponse = await channelAPI.getMembers(currentChannel.id)
                                        setChannelMembers(membersResponse.data)
                                        setShowConfirmModal(false)
                                        showNotification('멤버가 추방되었습니다')
                                      } catch (error) {
                                        console.error('Failed to remove member:', error)
                                        showNotification('멤버 추방에 실패했습니다')
                                      }
                                    }
                                  )
                                }}
                                className={styles.memberRemoveButton}
                              >
                                추방
                              </button>
                            )}
                          </>
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* 채널 삭제 또는 나가기 */}
            <div className={styles.settingsModalFooter}>
              {currentChannel.createdBy === user?.id ? (
                <button
                  onClick={() => {
                    setShowChannelSettingsModal(false)
                    showConfirm(
                      '채널 삭제',
                      `채널 "${currentChannel.name}"을(를) 삭제하시겠습니까?\n\n채널의 모든 메시지와 데이터가 삭제됩니다.`,
                      '삭제',
                      async () => {
                        try {
                          await channelAPI.delete(currentChannel.id)
                          dispatch(removeChannel(currentChannel.id))
                          dispatch(setCurrentChannel(null))
                          setShowConfirmModal(false)
                          showNotification('채널이 삭제되었습니다')
                        } catch (error) {
                          console.error('Failed to delete channel:', error)
                          showNotification('채널 삭제에 실패했습니다')
                        }
                      }
                    )
                  }}
                  className={styles.dangerButton}
                >
                  채널 삭제하기
                </button>
              ) : (
                <button
                  onClick={() => {
                    setShowChannelSettingsModal(false)
                    showConfirm(
                      '채널 나가기',
                      `채널 "${currentChannel.name}"에서 나가시겠습니까?`,
                      '나가기',
                      async () => {
                        try {
                          await channelAPI.leave(currentChannel.id)
                          dispatch(removeChannel(currentChannel.id))
                          dispatch(setCurrentChannel(null))
                          setShowConfirmModal(false)
                          showNotification('채널에서 나갔습니다')
                        } catch (error) {
                          console.error('Failed to leave channel:', error)
                          showNotification('채널 나가기에 실패했습니다')
                        }
                      }
                    )
                  }}
                  className={styles.dangerButton}
                >
                  채널에서 나가기
                </button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal */}
      {showConfirmModal && confirmModalData && (
        <>
          <div
            className={styles.confirmBackdrop}
            onClick={() => setShowConfirmModal(false)}
          />
          <div className={styles.confirmModal}>
            <div className={styles.confirmModalBody}>
              <p>{confirmModalData.message}</p>
            </div>
            <div className={styles.confirmModalFooter}>
              <button
                className={styles.confirmCancelButton}
                onClick={() => setShowConfirmModal(false)}
              >
                취소
              </button>
              <button
                className={styles.confirmButton}
                onClick={() => confirmModalData.onConfirm()}
              >
                {confirmModalData.confirmButtonText}
              </button>
            </div>
          </div>
        </>
      )}

      {/* Notification Modal */}
      {showNotificationModal && (
        <>
          <div
            className={styles.confirmBackdrop}
            onClick={() => setShowNotificationModal(false)}
          />
          <div className={styles.confirmModal}>
            <div className={styles.confirmModalBody}>
              <p>{notificationMessage}</p>
            </div>
            <div className={styles.confirmModalFooter}>
              <button
                className={styles.notificationButton}
                onClick={() => setShowNotificationModal(false)}
              >
                확인
              </button>
            </div>
          </div>
        </>
      )}

      {/* User Profile Modal */}
      {showUserProfileModal && selectedUserProfile && (
        <>
          <div
            className={styles.modalBackdrop}
            onClick={() => {
              setShowUserProfileModal(false)
              setSelectedUserProfile(null)
              setSelectedUserId(null)
            }}
          />
          <div className={styles.userProfileModal}>
            <div className={styles.userProfileHeader}>
              <h2>프로필 정보</h2>
              <button
                onClick={() => {
                  setShowUserProfileModal(false)
                  setSelectedUserProfile(null)
                  setSelectedUserId(null)
                }}
                className={styles.closeButton}
              >
                ✕
              </button>
            </div>
            <div className={styles.userProfileContent}>
              <div className={styles.profileAvatarSection}>
                {selectedUserProfile.avatarUrl ? (
                  <img
                    src={selectedUserProfile.avatarUrl}
                    alt={selectedUserProfile.name}
                    className={styles.profileAvatarImage}
                  />
                ) : (
                  <div className={styles.profileAvatar}>
                    {selectedUserProfile.name?.charAt(0).toUpperCase() || 'U'}
                  </div>
                )}
              </div>
              <div className={styles.profileInfoSection}>
                <div className={styles.profileInfoItem}>
                  <label>이름</label>
                  <div className={styles.profileValue}>{selectedUserProfile.name}</div>
                </div>
                <div className={styles.profileInfoItem}>
                  <label>이메일</label>
                  <div className={styles.profileValue}>{selectedUserProfile.email}</div>
                </div>
                <div className={styles.profileInfoItem}>
                  <label>상태</label>
                  <div className={styles.profileValue}>
                    {(!selectedUserProfile.status || selectedUserProfile.status === 'active') && '🟢 온라인'}
                    {selectedUserProfile.status === 'away' && '🌙 자리비움'}
                    {selectedUserProfile.status === 'dnd' && '🔴 다른 용무 중'}
                    {selectedUserProfile.status === 'vacation' && '🏖️ 연차'}
                    {selectedUserProfile.status === 'sick' && '🤒 병가'}
                    {selectedUserProfile.status === 'offline' && '⚫ 오프라인'}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}
