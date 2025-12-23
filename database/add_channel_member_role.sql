-- Add role column to channel_members table
ALTER TABLE channel_members ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'member';

-- Update existing records to have default role
UPDATE channel_members SET role = 'member' WHERE role IS NULL OR role = '';

-- Add index for better query performance
CREATE INDEX IF NOT EXISTS idx_channel_members_role ON channel_members(role);
