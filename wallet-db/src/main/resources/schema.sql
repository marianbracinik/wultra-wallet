CREATE TABLE IF NOT EXISTS wallet (
    owner VARCHAR(64) PRIMARY KEY
);
CREATE TABLE IF NOT EXISTS balance (
    owner VARCHAR(64) NOT NULL REFERENCES wallet(owner),
    currency CHAR(3) NOT NULL,
    amount DECIMAL(30, 4) NOT NULL CHECK (amount >= 0),
    PRIMARY KEY (owner, currency)
);
CREATE TABLE IF NOT EXISTS rate_snapshot (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    rate_date DATE NOT NULL,
    fetched_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS exchange_rate (
    currency CHAR(3) PRIMARY KEY,
    snapshot_id INTEGER DEFAULT 1 NOT NULL REFERENCES rate_snapshot(id),
    units INTEGER NOT NULL CHECK (units > 0),
    czk_rate DECIMAL(24, 12) NOT NULL CHECK (czk_rate > 0)
);
ALTER TABLE exchange_rate ADD COLUMN IF NOT EXISTS snapshot_id INTEGER DEFAULT 1 NOT NULL REFERENCES rate_snapshot(id);
MERGE INTO wallet (owner) KEY(owner) VALUES ('alice'), ('bob');
