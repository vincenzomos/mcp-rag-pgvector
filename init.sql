CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_store (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT,
    metadata JSONB,
    embedding VECTOR(768)
    );

-- Table for storing timesheet data
CREATE TABLE IF NOT EXISTS timesheets (
    id SERIAL PRIMARY KEY,
    month_year VARCHAR(50) NOT NULL,
    assignment_name VARCHAR(255) NOT NULL,
    typecode VARCHAR(10) NOT NULL,
    hours INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_timesheets_month_year ON timesheets(month_year);
CREATE INDEX IF NOT EXISTS idx_timesheets_assignment ON timesheets(assignment_name);
CREATE INDEX IF NOT EXISTS idx_timesheets_typecode ON timesheets(typecode);
CREATE INDEX IF NOT EXISTS idx_timesheets_composite ON timesheets(month_year, assignment_name, typecode);
