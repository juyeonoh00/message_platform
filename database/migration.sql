-- ============================================
-- DB 마이그레이션 스크립트
-- DM과 채널 분리를 위한 테이블 구조 변경
-- ============================================

-- 1. 기존 messages 테이블을 channel_messages로 이름 변경
-- ============================================
ALTER TABLE messages RENAME TO channel_messages;

-- 2. channels 테이블에서 is_direct_message 컬럼 제거
-- ============================================
-- 먼저 is_direct_message 관련 인덱스 제거
ALTER TABLE channels DROP INDEX IF EXISTS idx_is_direct_message;
ALTER TABLE channels DROP INDEX IF EXISTS idx_workspace_direct;

-- is_direct_message 컬럼 제거
ALTER TABLE channels DROP COLUMN IF EXISTS is_direct_message;

-- 3. 채팅방(Chatroom) 테이블 생성
-- ============================================
CREATE TABLE IF NOT EXISTS chatroom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL COMMENT '참가자 username들 저장',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_workspace_id (workspace_id),
    INDEX idx_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. 채팅방 멤버(ChatroomMember) 테이블 생성
-- ============================================
CREATE TABLE IF NOT EXISTS chatroom_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chatroom_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chatroom_user (chatroom_id, user_id),
    INDEX idx_chatroom_id (chatroom_id),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (chatroom_id) REFERENCES chatroom(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. 채팅방 메시지(ChatroomMessage) 테이블 생성
-- ============================================
CREATE TABLE IF NOT EXISTS chatroom_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chatroom_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    edited_at DATETIME NULL,
    INDEX idx_chatroom_created (chatroom_id, created_at),
    INDEX idx_user_id (user_id),
    INDEX idx_deleted (is_deleted),
    FOREIGN KEY (chatroom_id) REFERENCES chatroom(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. 채팅방 읽음 상태(ChatroomReadState) 테이블 생성
-- ============================================
CREATE TABLE IF NOT EXISTS chatroom_read_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chatroom_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chatroom_user (chatroom_id, user_id),
    INDEX idx_chatroom_read_chatroom_id (chatroom_id),
    INDEX idx_chatroom_read_user_id (user_id),
    FOREIGN KEY (chatroom_id) REFERENCES chatroom(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. 채팅방 멘션(ChatroomMention) 테이블 생성
-- ============================================
CREATE TABLE IF NOT EXISTS chatroom_mentions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chatroom_message_id BIGINT NOT NULL,
    mentioned_user_id BIGINT NULL,
    mention_type VARCHAR(20) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_chatroom_message_user_type (chatroom_message_id, mentioned_user_id, mention_type),
    INDEX idx_chatroom_mentioned_user_read (mentioned_user_id, is_read),
    INDEX idx_chatroom_message_id (chatroom_message_id),
    INDEX idx_chatroom_mention_type (mention_type),
    FOREIGN KEY (chatroom_message_id) REFERENCES chatroom_messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. AI 챗봇 히스토리(AiChatHistory) 테이블 생성
-- ============================================
CREATE TABLE IF NOT EXISTS ai_chat_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    role VARCHAR(20) NOT NULL COMMENT 'user 또는 ai',
    content TEXT NOT NULL COMMENT '대화 내용',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_created (user_id, created_at),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 데이터 마이그레이션 (옵션)
-- 기존 DM 데이터를 새 chatroom 구조로 이동
-- ============================================

-- 주의: 아래 마이그레이션은 기존에 DM 데이터가 있는 경우에만 실행하세요.
-- 기존에 is_direct_message = true 였던 채널들을 chatroom으로 마이그레이션

-- STEP 1: 기존 DM 채널들을 chatroom 테이블로 복사
-- (이 단계는 기존 데이터가 있을 때만 실행)
/*
INSERT INTO chatroom (id, workspace_id, name, created_by, created_at)
SELECT
    c.id,
    c.workspace_id,
    c.name,
    c.created_by,
    c.created_at
FROM channels c
WHERE c.is_direct_message = TRUE;
*/

-- STEP 2: 기존 DM 채널 멤버들을 chatroom_members로 복사
/*
INSERT INTO chatroom_members (chatroom_id, user_id, joined_at)
SELECT
    cm.channel_id as chatroom_id,
    cm.user_id,
    cm.joined_at
FROM channel_members cm
INNER JOIN channels c ON cm.channel_id = c.id
WHERE c.is_direct_message = TRUE;
*/

-- STEP 3: 기존 DM 메시지들을 chatroom_messages로 복사
/*
INSERT INTO chatroom_messages (id, chatroom_id, user_id, content, is_edited, is_deleted, created_at, updated_at, edited_at)
SELECT
    m.id,
    m.channel_id as chatroom_id,
    m.user_id,
    m.content,
    m.is_edited,
    m.is_deleted,
    m.created_at,
    m.updated_at,
    m.edited_at
FROM channel_messages m
INNER JOIN channels c ON m.channel_id = c.id
WHERE c.is_direct_message = TRUE
AND m.parent_message_id IS NULL;  -- DM에는 스레드가 없으므로 최상위 메시지만
*/

-- STEP 4: 기존 DM 읽음 상태를 chatroom_read_states로 복사
/*
INSERT INTO chatroom_read_states (chatroom_id, user_id, last_read_message_id, updated_at)
SELECT
    rs.channel_id as chatroom_id,
    rs.user_id,
    rs.last_read_message_id,
    rs.updated_at
FROM read_states rs
INNER JOIN channels c ON rs.channel_id = c.id
WHERE c.is_direct_message = TRUE;
*/

-- STEP 5: 기존 DM 멘션을 chatroom_mentions로 복사
/*
INSERT INTO chatroom_mentions (chatroom_message_id, mentioned_user_id, mention_type, is_read)
SELECT
    men.message_id as chatroom_message_id,
    men.mentioned_user_id,
    men.mention_type,
    men.is_read
FROM mentions men
INNER JOIN channel_messages m ON men.message_id = m.id
INNER JOIN channels c ON m.channel_id = c.id
WHERE c.is_direct_message = TRUE;
*/

-- STEP 6: 기존 DM 관련 데이터 삭제 (마이그레이션 완료 후)
/*
-- DM 멘션 삭제
DELETE men FROM mentions men
INNER JOIN channel_messages m ON men.message_id = m.id
INNER JOIN channels c ON m.channel_id = c.id
WHERE c.is_direct_message = TRUE;

-- DM 읽음 상태 삭제
DELETE rs FROM read_states rs
INNER JOIN channels c ON rs.channel_id = c.id
WHERE c.is_direct_message = TRUE;

-- DM 메시지 삭제
DELETE m FROM channel_messages m
INNER JOIN channels c ON m.channel_id = c.id
WHERE c.is_direct_message = TRUE;

-- DM 채널 멤버 삭제
DELETE cm FROM channel_members cm
INNER JOIN channels c ON cm.channel_id = c.id
WHERE c.is_direct_message = TRUE;

-- DM 채널 삭제
DELETE FROM channels WHERE is_direct_message = TRUE;
*/

-- ============================================
-- 마이그레이션 완료 확인 쿼리
-- ============================================

-- 테이블 확인
SELECT 'channel_messages 테이블 확인' as status, COUNT(*) as count FROM channel_messages;
SELECT 'chatroom 테이블 확인' as status, COUNT(*) as count FROM chatroom;
SELECT 'chatroom_members 테이블 확인' as status, COUNT(*) as count FROM chatroom_members;
SELECT 'chatroom_messages 테이블 확인' as status, COUNT(*) as count FROM chatroom_messages;
SELECT 'chatroom_read_states 테이블 확인' as status, COUNT(*) as count FROM chatroom_read_states;
SELECT 'chatroom_mentions 테이블 확인' as status, COUNT(*) as count FROM chatroom_mentions;
SELECT 'ai_chat_history 테이블 확인' as status, COUNT(*) as count FROM ai_chat_history;

-- 채널 테이블에 is_direct_message 컬럼이 없는지 확인
SHOW COLUMNS FROM channels LIKE 'is_direct_message';

-- ============================================
-- Chatroom 테이블에 hiddenBy 컬럼 추가 (채팅방 숨김 기능)
-- ============================================
ALTER TABLE chatroom ADD COLUMN IF NOT EXISTS hidden_by VARCHAR(500) COMMENT '쉼표로 구분된 사용자 ID 목록 (채팅방을 숨긴 사용자들)';

-- ============================================
-- 마이그레이션 완료!
-- ============================================
