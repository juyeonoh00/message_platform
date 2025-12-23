-- ============================================
-- DM 기능 추가를 위한 스키마 수정
-- ============================================
-- 실행: mysql -h 218.38.54.88 -u root -p message < database/add_dm_support.sql

USE message;

-- ============================================
-- 1. channels 테이블에 is_direct_message 컬럼 추가
-- ============================================
ALTER TABLE channels
ADD COLUMN IF NOT EXISTS is_direct_message BOOLEAN NOT NULL DEFAULT FALSE
COMMENT 'true=DM, false=channel'
AFTER is_private;

-- ============================================
-- 2. name 컬럼 유지 (DM은 'DM_userid1_userid2' 형식으로 저장)
-- ============================================
-- DM의 경우 name은 'DM_작은ID_큰ID' 형식으로 저장되어 고유성 보장
-- 프론트엔드에서는 상대방 이름으로 표시됨

-- ============================================
-- 3. DM 조회를 위한 인덱스 추가
-- ============================================
ALTER TABLE channels
ADD INDEX IF NOT EXISTS idx_workspace_dm (workspace_id, is_direct_message);

-- ============================================
-- 4. 변경사항 확인
-- ============================================
DESCRIBE channels;

-- ============================================
-- 5. 기존 채널들의 is_direct_message를 FALSE로 설정
-- ============================================
UPDATE channels
SET is_direct_message = FALSE
WHERE is_direct_message IS NULL;

SELECT '✅ DM 기능 추가 완료!' AS status;
