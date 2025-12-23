-- Create message_reactions table
CREATE TABLE IF NOT EXISTS message_reactions (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    emoji VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_message_user_emoji UNIQUE (message_id, user_id, emoji)
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_message_reactions_message_id ON message_reactions(message_id);
CREATE INDEX IF NOT EXISTS idx_message_reactions_user_id ON message_reactions(user_id);

-- Add foreign key constraints (optional, adjust table names as needed)
-- ALTER TABLE message_reactions ADD CONSTRAINT fk_message_reactions_message
--     FOREIGN KEY (message_id) REFERENCES channel_messages(id) ON DELETE CASCADE;

-- ALTER TABLE message_reactions ADD CONSTRAINT fk_message_reactions_user
--     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
