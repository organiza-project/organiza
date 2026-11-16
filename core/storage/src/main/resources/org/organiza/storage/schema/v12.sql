-- Organiza local schema v12. See docs/schema.md and ADR-0031.
-- Daily minutes per app from this person's other linked devices, opened from sealed reports
-- (ADR-0028), so one device can show all of them. Never sent anywhere from here.

CREATE TABLE device_usage (
    device   TEXT NOT NULL,
    day      TEXT NOT NULL CHECK (day GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    app      TEXT NOT NULL,
    category TEXT NOT NULL,
    minutes  INTEGER NOT NULL CHECK (minutes >= 0),
    PRIMARY KEY (device, day, app)
) STRICT, WITHOUT ROWID;

CREATE INDEX device_usage_day ON device_usage (day);

PRAGMA user_version = 12;
