CREATE TABLE IF NOT EXISTS usage_counters (
    subject TEXT NOT NULL,
    month_key TEXT NOT NULL,
    day_key TEXT NOT NULL,
    month_count INTEGER NOT NULL DEFAULT 0 CHECK (month_count >= 0),
    day_count INTEGER NOT NULL DEFAULT 0 CHECK (day_count >= 0),
    last_request_id TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (subject, month_key)
);

CREATE INDEX IF NOT EXISTS usage_counters_updated_at
    ON usage_counters(updated_at);
