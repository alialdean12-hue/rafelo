CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS jobs (
  id TEXT PRIMARY KEY,
  url TEXT NOT NULL,
  status TEXT NOT NULL,          -- pending | working | done | failed
  created INTEGER NOT NULL,
  updated INTEGER NOT NULL,
  ip TEXT,
  result TEXT,                   -- JSON list of items from the reader
  error TEXT
);
CREATE INDEX IF NOT EXISTS jobs_status ON jobs(status, created);
CREATE INDEX IF NOT EXISTS jobs_ip ON jobs(ip, created);
