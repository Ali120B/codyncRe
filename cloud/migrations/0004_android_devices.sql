-- Android phones as paired devices. Rebuild devices (+ referencing tables) so the
-- platform CHECK can include 'android', same pattern as 0003_desktop_devices.sql.

CREATE TABLE devices_new (
  id            TEXT PRIMARY KEY,
  owner_user_id TEXT NOT NULL REFERENCES accounts(user_id),
  sign_pub      TEXT NOT NULL,
  name          TEXT NOT NULL,
  platform      TEXT NOT NULL CHECK (platform IN ('ios','macos','linux','windows','android')),
  created_at    INTEGER NOT NULL,
  last_used_at  INTEGER,
  revoked_at    INTEGER,
  UNIQUE (owner_user_id, sign_pub)
);
INSERT INTO devices_new SELECT * FROM devices;

CREATE TABLE access_requests_new (
  id          TEXT PRIMARY KEY,
  computer_id TEXT NOT NULL REFERENCES computers(id),
  device_id   TEXT NOT NULL REFERENCES devices_new(id),
  user_id     TEXT NOT NULL REFERENCES accounts(user_id),
  status      TEXT NOT NULL CHECK (status IN ('pending','approved','denied','expired','cancelled')),
  commit_hash  TEXT NOT NULL,
  host_nonce   TEXT,
  device_nonce TEXT,
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL,
  decided_at  INTEGER,
  grant_id    TEXT
);
INSERT INTO access_requests_new SELECT * FROM access_requests;

CREATE TABLE grants_new (
  id             TEXT PRIMARY KEY,
  computer_id    TEXT NOT NULL REFERENCES computers(id),
  device_id      TEXT NOT NULL REFERENCES devices_new(id),
  scopes         TEXT NOT NULL,
  status         TEXT NOT NULL CHECK (status IN ('active','revoked')),
  created_at     INTEGER NOT NULL,
  revoked_at     INTEGER,
  revoked_reason TEXT
);
INSERT INTO grants_new SELECT * FROM grants;

DROP TABLE access_requests;
DROP TABLE grants;
DROP TABLE devices;
ALTER TABLE devices_new RENAME TO devices;
ALTER TABLE access_requests_new RENAME TO access_requests;
ALTER TABLE grants_new RENAME TO grants;

CREATE INDEX access_requests_computer ON access_requests(computer_id, status);
CREATE UNIQUE INDEX access_requests_one_pending ON access_requests(computer_id, device_id) WHERE status = 'pending';
CREATE UNIQUE INDEX grants_active ON grants(computer_id, device_id) WHERE status = 'active';
