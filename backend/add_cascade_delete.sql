-- CASCADE DELETE 설정 SQL 스크립트
-- 기존 FK를 삭제하고 ON DELETE CASCADE 옵션과 함께 재생성합니다

USE message;

-- ============================================
-- 1. WORKSPACE 관련 CASCADE
-- ============================================

-- workspace_members.workspace_id
ALTER TABLE workspace_members DROP FOREIGN KEY IF EXISTS FKfxr14evcbpmqxtn76bvfqr54u;
ALTER TABLE workspace_members
ADD CONSTRAINT FKfxr14evcbpmqxtn76bvfqr54u
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

-- channels.workspace_id
ALTER TABLE channels DROP FOREIGN KEY IF EXISTS FK3cmc6ke94nkikxmpcln571e4n;
ALTER TABLE channels
ADD CONSTRAINT FK3cmc6ke94nkikxmpcln571e4n
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

-- document_categories.workspace_id
ALTER TABLE document_categories DROP FOREIGN KEY IF EXISTS FK_category_workspace;
ALTER TABLE document_categories
ADD CONSTRAINT FK_category_workspace
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

-- documents.workspace_id
ALTER TABLE documents DROP FOREIGN KEY IF EXISTS FK_document_workspace;
ALTER TABLE documents
ADD CONSTRAINT FK_document_workspace
FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;

-- documents.category_id
ALTER TABLE documents DROP FOREIGN KEY IF EXISTS FK_document_category;
ALTER TABLE documents
ADD CONSTRAINT FK_document_category
FOREIGN KEY (category_id) REFERENCES document_categories(id) ON DELETE CASCADE;

-- documents.uploader_id
ALTER TABLE documents DROP FOREIGN KEY IF EXISTS FK_document_uploader;
ALTER TABLE documents
ADD CONSTRAINT FK_document_uploader
FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE CASCADE;

-- ============================================
-- 2. CHANNEL 관련 CASCADE
-- ============================================

-- channel_members.channel_id
ALTER TABLE channel_members DROP FOREIGN KEY IF EXISTS FKq1sathi2spwvf7qsm7l6gyj1q;
ALTER TABLE channel_members
ADD CONSTRAINT FKq1sathi2spwvf7qsm7l6gyj1q
FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE;

-- channel_messages.channel_id
ALTER TABLE channel_messages DROP FOREIGN KEY IF EXISTS FKs0hfjwo2ko4g7sjtp42x8tw80;
ALTER TABLE channel_messages
ADD CONSTRAINT FKs0hfjwo2ko4g7sjtp42x8tw80
FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE;

-- read_states.channel_id
ALTER TABLE read_states DROP FOREIGN KEY IF EXISTS FK_readstate_channel;
ALTER TABLE read_states
ADD CONSTRAINT FK_readstate_channel
FOREIGN KEY (channel_id) REFERENCES channels(id) ON DELETE CASCADE;

-- read_states.user_id
ALTER TABLE read_states DROP FOREIGN KEY IF EXISTS FK_readstate_user;
ALTER TABLE read_states
ADD CONSTRAINT FK_readstate_user
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ============================================
-- 3. MESSAGE 관련 CASCADE
-- ============================================

-- mentions.message_id
ALTER TABLE mentions DROP FOREIGN KEY IF EXISTS FKt2s27t4vidgrgtx43g3so9sov;
ALTER TABLE mentions
ADD CONSTRAINT FKt2s27t4vidgrgtx43g3so9sov
FOREIGN KEY (message_id) REFERENCES channel_messages(id) ON DELETE CASCADE;

-- message_reactions.message_id
ALTER TABLE message_reactions DROP FOREIGN KEY IF EXISTS FK_reaction_message;
ALTER TABLE message_reactions
ADD CONSTRAINT FK_reaction_message
FOREIGN KEY (message_id) REFERENCES channel_messages(id) ON DELETE CASCADE;

-- message_reactions.user_id
ALTER TABLE message_reactions DROP FOREIGN KEY IF EXISTS FK_reaction_user;
ALTER TABLE message_reactions
ADD CONSTRAINT FK_reaction_user
FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- ============================================
-- 결과 확인
-- ============================================
SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    DELETE_RULE
FROM
    information_schema.REFERENTIAL_CONSTRAINTS
WHERE
    CONSTRAINT_SCHEMA = 'message'
    AND DELETE_RULE = 'CASCADE'
ORDER BY
    TABLE_NAME;
