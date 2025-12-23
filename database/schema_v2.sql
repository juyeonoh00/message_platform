-- ============================================
-- Messenger Platform - PRODUCTION GRADE SCHEMA
-- ============================================
-- 작성자: Senior Backend Engineer
-- 목적: FK 정합성, UNIQUE 제약, 성능 인덱스 최적화
-- ============================================

DROP DATABASE IF EXISTS message;
CREATE DATABASE message CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE message;

-- ============================================
-- 1. USERS TABLE
-- ============================================
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE COMMENT '이메일 (로그인 ID)',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 해시',
    name VARCHAR(100) NOT NULL COMMENT '사용자 이름',
    avatar_url VARCHAR(500) COMMENT '프로필 이미지 URL',
    status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active|away|dnd|offline',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_email (email),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='사용자 테이블';

-- ============================================
-- 2. WORKSPACES TABLE
-- ============================================
CREATE TABLE workspaces (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT 'Workspace 이름',
    description VARCHAR(500) COMMENT 'Workspace 설명',
    owner_id BIGINT NOT NULL COMMENT 'Workspace 소유자 (삭제 시 CASCADE)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_owner_id (owner_id),

    -- FK: owner 삭제 시 Workspace도 삭제
    CONSTRAINT fk_workspace_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Workspace 테이블';

-- ============================================
-- 3. WORKSPACE_MEMBERS TABLE (중복 방지 필수)
-- ============================================
CREATE TABLE workspace_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT 'owner|admin|member',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ✅ UNIQUE 제약: 동일 유저가 동일 Workspace에 중복 가입 방지
    UNIQUE KEY uk_workspace_user (workspace_id, user_id),

    INDEX idx_workspace_id (workspace_id),
    INDEX idx_user_id (user_id),
    INDEX idx_role (role),

    -- FK: Workspace 삭제 시 멤버십도 삭제
    CONSTRAINT fk_wm_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK: User 삭제 시 멤버십도 삭제
    CONSTRAINT fk_wm_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Workspace 멤버십 (중복 방지 UNIQUE)';

-- ============================================
-- 4. CHANNELS TABLE
-- ============================================
CREATE TABLE channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(100) COMMENT '채널 이름 (DM의 경우 NULL)',
    description VARCHAR(500) COMMENT '채널 설명',
    is_private BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'true=private, false=public',
    created_by BIGINT NOT NULL COMMENT '채널 생성자 (삭제되어도 채널 유지)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_workspace_id (workspace_id),
    INDEX idx_is_private (is_private),
    INDEX idx_created_by (created_by),
    INDEX idx_workspace_private (workspace_id, is_private),
    INDEX idx_workspace_dm (workspace_id, is_direct_message),

    -- FK: Workspace 삭제 시 Channel도 삭제
    CONSTRAINT fk_channel_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK: 생성자 삭제되어도 채널은 유지 (RESTRICT 또는 SET NULL 선택)
    -- 여기서는 생성자 정보만 참조하므로 RESTRICT (삭제 방지)
    CONSTRAINT fk_channel_creator
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='채널 테이블 (public/private 구분)';

-- ============================================
-- 5. CHANNEL_MEMBERS TABLE (중복 방지 필수)
-- ============================================
-- ✅ 정책: public 채널은 자동 가입 (앱 로직), private 채널은 명시적 row 필요
CREATE TABLE channel_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ✅ UNIQUE 제약: 동일 유저가 동일 채널에 중복 가입 방지
    UNIQUE KEY uk_channel_user (channel_id, user_id),

    INDEX idx_channel_id (channel_id),
    INDEX idx_user_id (user_id),

    -- FK: Channel 삭제 시 멤버십도 삭제
    CONSTRAINT fk_cm_channel
        FOREIGN KEY (channel_id) REFERENCES channels(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK: User 삭제 시 멤버십도 삭제
    CONSTRAINT fk_cm_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='채널 멤버십 (private 채널 전용, public은 선택적)';

-- ============================================
-- 6. MESSAGES TABLE
-- ============================================
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL COMMENT '메시지가 속한 Workspace',
    channel_id BIGINT NOT NULL COMMENT '메시지가 속한 Channel',
    user_id BIGINT NOT NULL COMMENT '메시지 작성자',
    content TEXT NOT NULL COMMENT '메시지 내용',
    parent_message_id BIGINT COMMENT '스레드 부모 메시지 ID (NULL 가능)',
    is_edited BOOLEAN NOT NULL DEFAULT FALSE COMMENT '수정 여부',
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '삭제 여부 (soft delete)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- ✅ 성능 핵심 인덱스
    INDEX idx_channel_created (channel_id, created_at DESC) COMMENT '채팅 페이징 핵심',
    INDEX idx_parent_created (parent_message_id, created_at ASC) COMMENT '스레드 조회',
    INDEX idx_workspace_channel (workspace_id, channel_id),
    INDEX idx_user_id (user_id),
    INDEX idx_deleted (is_deleted),

    -- FK: Channel 삭제 시 메시지도 삭제
    CONSTRAINT fk_message_channel
        FOREIGN KEY (channel_id) REFERENCES channels(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK: User 삭제 시 메시지 유지 (작성자 정보는 별도 필드로 저장 권장)
    -- 실무에서는 ON DELETE SET NULL + user_name 컬럼 추가 고려
    CONSTRAINT fk_message_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    -- FK: 부모 메시지 삭제 시 자식 메시지도 삭제
    CONSTRAINT fk_message_parent
        FOREIGN KEY (parent_message_id) REFERENCES messages(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='메시지 테이블 (스레드 지원)';

-- ============================================
-- 7. MENTIONS TABLE
-- ============================================
-- ✅ 정책: @user는 mentioned_user_id 필수, @channel/@here는 NULL
CREATE TABLE mentions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL COMMENT '멘션이 포함된 메시지',
    mentioned_user_id BIGINT COMMENT '@user 멘션 대상 (NULL 가능)',
    mention_type VARCHAR(20) NOT NULL COMMENT 'user|channel|here|everyone',
    is_read BOOLEAN NOT NULL DEFAULT FALSE COMMENT '읽음 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ✅ UNIQUE 제약: 동일 메시지에서 동일 유저를 중복 멘션 방지
    -- mention_type이 user일 때만 유효 (channel/here는 user_id가 NULL)
    UNIQUE KEY uk_message_user_type (message_id, mentioned_user_id, mention_type),

    -- ✅ 성능 인덱스
    INDEX idx_mentioned_user_read (mentioned_user_id, is_read) COMMENT '읽지 않은 멘션 조회',
    INDEX idx_message_id (message_id),
    INDEX idx_mention_type (mention_type),

    -- FK: Message 삭제 시 Mention도 삭제
    CONSTRAINT fk_mention_message
        FOREIGN KEY (message_id) REFERENCES messages(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK: User 삭제 시 Mention도 삭제 (NULL 허용)
    CONSTRAINT fk_mention_user
        FOREIGN KEY (mentioned_user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='멘션 테이블 (@user/@channel/@here 지원)';

-- ============================================
-- 8. READ_STATES TABLE (중복 방지 필수)
-- ============================================
CREATE TABLE read_states (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_message_id BIGINT COMMENT '✅ 마지막 읽은 메시지 ID (NULL 허용)',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- ✅ UNIQUE 제약: 동일 유저가 동일 채널에 중복 read_state 방지
    UNIQUE KEY uk_channel_user (channel_id, user_id),

    INDEX idx_channel_id (channel_id),
    INDEX idx_user_id (user_id),

    -- FK: Channel 삭제 시 ReadState도 삭제
    CONSTRAINT fk_rs_channel
        FOREIGN KEY (channel_id) REFERENCES channels(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- FK: User 삭제 시 ReadState도 삭제
    CONSTRAINT fk_rs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- ✅ FK: 메시지 삭제 시 last_read_message_id를 NULL로 설정
    CONSTRAINT fk_rs_message
        FOREIGN KEY (last_read_message_id) REFERENCES messages(id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='읽음 상태 테이블 (UNIQUE + ON DELETE SET NULL)';

-- ============================================
-- 검증 쿼리
-- ============================================
-- 테이블 목록
SHOW TABLES;

-- FK 제약 확인
SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    DELETE_RULE,
    UPDATE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = 'message'
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- UNIQUE 제약 확인
SELECT
    TABLE_NAME,
    INDEX_NAME,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLUMNS
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'message' AND INDEX_NAME LIKE 'uk_%'
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- 인덱스 확인
SELECT
    TABLE_NAME,
    INDEX_NAME,
    GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS COLUMNS,
    NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'message'
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, NON_UNIQUE, INDEX_NAME;
