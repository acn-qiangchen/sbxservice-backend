-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT users_status_check CHECK (status IN ('active', 'inactive', 'suspended'))
);

-- Create index on email for faster lookups
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- Create user_preferences table
CREATE TABLE user_preferences (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    preference_key VARCHAR(100) NOT NULL,
    preference_value JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, preference_key)
);

-- Create index on user_id for faster lookups
CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);

-- Create audit log table for tracking changes
CREATE TABLE user_audit_logs (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    performed_by UUID,
    old_value JSONB,
    new_value JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX idx_user_audit_logs_user_id ON user_audit_logs(user_id);
CREATE INDEX idx_user_audit_logs_created_at ON user_audit_logs(created_at);

-- Create function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically update updated_at field on users table
CREATE TRIGGER update_users_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Create trigger to automatically update updated_at field on user_preferences table
CREATE TRIGGER update_user_preferences_updated_at
BEFORE UPDATE ON user_preferences
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Comments on tables and columns
COMMENT ON TABLE users IS 'Stores user account information';
COMMENT ON COLUMN users.id IS 'Unique identifier for the user';
COMMENT ON COLUMN users.email IS 'Email address of the user, used for login';
COMMENT ON COLUMN users.first_name IS 'First name of the user';
COMMENT ON COLUMN users.last_name IS 'Last name of the user';
COMMENT ON COLUMN users.status IS 'Current status of the user (active, inactive, suspended)';
COMMENT ON COLUMN users.created_at IS 'Timestamp when the user was created';
COMMENT ON COLUMN users.updated_at IS 'Timestamp when the user was last updated';

COMMENT ON TABLE user_preferences IS 'Stores user preferences as key-value pairs';
COMMENT ON COLUMN user_preferences.user_id IS 'Reference to the user';
COMMENT ON COLUMN user_preferences.preference_key IS 'Key for the preference setting';
COMMENT ON COLUMN user_preferences.preference_value IS 'Value of the preference setting stored as JSON';
COMMENT ON COLUMN user_preferences.updated_at IS 'Timestamp when the preference was last updated';

COMMENT ON TABLE user_audit_logs IS 'Audit log for tracking changes to users';
COMMENT ON COLUMN user_audit_logs.user_id IS 'Reference to the user that was changed';
COMMENT ON COLUMN user_audit_logs.action IS 'Type of action performed (e.g., create, update, delete)';
COMMENT ON COLUMN user_audit_logs.performed_by IS 'ID of the user who performed the action';
COMMENT ON COLUMN user_audit_logs.old_value IS 'Previous values before the change';
COMMENT ON COLUMN user_audit_logs.new_value IS 'New values after the change';
COMMENT ON COLUMN user_audit_logs.created_at IS 'Timestamp when the action was performed';
COMMENT ON COLUMN user_audit_logs.ip_address IS 'IP address of the client that performed the action';
COMMENT ON COLUMN user_audit_logs.user_agent IS 'User agent of the client that performed the action'; 