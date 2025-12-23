-- ============================================
-- 스키마 완전 정리 (중복 인덱스/FK 제거)
-- ============================================
-- 경고: 모든 데이터가 삭제됩니다!
-- 실행 전 백업 필수!

USE message;

-- ============================================
-- 1. 외래키 제약조건 모두 제거
-- ============================================
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 2. 모든 테이블 삭제 (순서 무관)
-- ============================================
DROP TABLE IF EXISTS mentions;
DROP TABLE IF EXISTS read_states;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS channel_members;
DROP TABLE IF EXISTS channels;
DROP TABLE IF EXISTS workspace_members;
DROP TABLE IF EXISTS workspaces;
DROP TABLE IF EXISTS users;

-- ============================================
-- 3. 외래키 체크 복원
-- ============================================
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 4. 테이블 삭제 확인
-- ============================================
SHOW TABLES;
-- 출력: Empty set (0.00 sec) 이어야 정상

-- ============================================
-- 다음 단계: schema_v2.sql 실행
-- ============================================
-- mysql -h 218.38.54.88 -u root -p message < database/schema_v2.sql
