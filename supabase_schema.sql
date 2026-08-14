-- ═══════════════════════════════════════════════════════════
--   HALLO JOHOR — Supabase Schema
--   Jalankan file ini di Supabase SQL Editor
-- ═══════════════════════════════════════════════════════════

-- ── Grup Laporan ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS laporan_groups (
  id          TEXT PRIMARY KEY,
  name        TEXT NOT NULL,
  added_at    TIMESTAMPTZ DEFAULT NOW()
);

-- ── Laporan Counter ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS laporan_counter (
  id      INTEGER PRIMARY KEY DEFAULT 1,
  counter INTEGER NOT NULL DEFAULT 0
);
INSERT INTO laporan_counter (id, counter) VALUES (1, 0)
  ON CONFLICT (id) DO NOTHING;

-- ── Laporan Archive ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS laporan_archive (
  id                TEXT PRIMARY KEY,
  pelapor           TEXT,
  nama_pelapor      TEXT,
  kontak            TEXT,
  kategori          TEXT,
  kelurahan         TEXT,
  isi               TEXT,
  foto_url          TEXT,
  lokasi            TEXT,
  status            TEXT DEFAULT 'terkirim',
  tanggal           TIMESTAMPTZ DEFAULT NOW(),
  status_updated_at TIMESTAMPTZ
);

-- Aman dijalankan pada database yang sudah dibuat sebelum kolom kontak tersedia.
ALTER TABLE laporan_archive ADD COLUMN IF NOT EXISTS nama_pelapor TEXT;
ALTER TABLE laporan_archive ADD COLUMN IF NOT EXISTS kontak TEXT;

-- ── Feedback Queue ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS feedback_queue (
  id         TEXT PRIMARY KEY,
  status     TEXT DEFAULT 'pending',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  sent_at    TIMESTAMPTZ,
  data       JSONB DEFAULT '{}'
);

-- ── Status Notif Queue ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS status_notif_queue (
  id         TEXT PRIMARY KEY,
  status     TEXT DEFAULT 'pending',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  sent_at    TIMESTAMPTZ,
  data       JSONB DEFAULT '{}'
);

-- ── LiveChat Sessions ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS livechat_sessions (
  id              TEXT PRIMARY KEY,
  jid             TEXT NOT NULL,
  name            TEXT,
  status          TEXT DEFAULT 'active',
  started_at      TIMESTAMPTZ DEFAULT NOW(),
  last_message_at TIMESTAMPTZ DEFAULT NOW(),
  closed_at       TIMESTAMPTZ,
  unread          INTEGER DEFAULT 0,
  messages        JSONB DEFAULT '[]'
);

-- ── LiveChat Reply Queue ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS livechat_replies (
  id         TEXT PRIMARY KEY,
  status     TEXT DEFAULT 'pending',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  sent_at    TIMESTAMPTZ,
  data       JSONB DEFAULT '{}'
);

-- ── Group Routing ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS group_routing (
  id      INTEGER PRIMARY KEY DEFAULT 1,
  routing JSONB DEFAULT '{}'
);
INSERT INTO group_routing (id, routing) VALUES (1, '{}')
  ON CONFLICT (id) DO NOTHING;

-- ── Kegiatan Kecamatan ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS kegiatan (
  id         TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  nama       TEXT NOT NULL,
  deskripsi  TEXT,
  tempat     TEXT,
  tanggal    TEXT
);

-- ── Broadcast Channels ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS broadcast_channels (
  jid      TEXT PRIMARY KEY,
  name     TEXT,
  added_at TIMESTAMPTZ DEFAULT NOW()
);

-- ── Broadcast Queue ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS broadcast_queue (
  id         TEXT PRIMARY KEY,
  status     TEXT DEFAULT 'pending',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  sent_at    TIMESTAMPTZ,
  error      TEXT,
  data       JSONB DEFAULT '{}'
);

-- ── Weather Broadcast Schedule ────────────────────────────────
CREATE TABLE IF NOT EXISTS weather_broadcast_schedule (
  id             INTEGER PRIMARY KEY DEFAULT 1,
  enabled        BOOLEAN DEFAULT FALSE,
  channel_jid    TEXT DEFAULT '',
  last_sent_date TEXT
);
INSERT INTO weather_broadcast_schedule (id) VALUES (1)
  ON CONFLICT (id) DO NOTHING;

-- ── UMKM Binaan ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS umkm_binaan (
  id         TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ,
  nama       TEXT NOT NULL,
  kategori   TEXT,
  alamat     TEXT,
  maps_url   TEXT,
  kontak     TEXT
);

-- ══════════════════════════════════════════════════════════
--   FUNCTION: Atomic increment laporan counter
--   (dipanggil via supabase.rpc('increment_laporan_counter'))
-- ══════════════════════════════════════════════════════════

CREATE OR REPLACE FUNCTION increment_laporan_counter()
RETURNS INTEGER AS $$
DECLARE
  new_val INTEGER;
BEGIN
  UPDATE laporan_counter SET counter = counter + 1 WHERE id = 1
  RETURNING counter INTO new_val;
  RETURN new_val;
END;
$$ LANGUAGE plpgsql;

-- ══════════════════════════════════════════════════════════
--   IVA TEST SKRINING
--   Jalankan di Supabase SQL Editor
-- ══════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS iva_skrining (
  id            TEXT PRIMARY KEY,
  wa_number     TEXT NOT NULL,
  nama_panggil  TEXT,
  skor          INT NOT NULL DEFAULT 0,
  risiko        TEXT NOT NULL CHECK (risiko IN ('rendah', 'sedang', 'tinggi')),
  jawaban       JSONB,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_iva_risiko    ON iva_skrining (risiko);
CREATE INDEX IF NOT EXISTS idx_iva_created   ON iva_skrining (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_iva_wa_number ON iva_skrining (wa_number);

-- ══════════════════════════════════════════════════════════
--   ADMIN USERS — Multi-akun Dashboard
--   1 superadmin (akses penuh) + N admin kelurahan (terbatas)
--   Password disimpan sebagai hash scrypt (format salt:hash)
-- ══════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS admin_users (
  id            TEXT PRIMARY KEY,
  username      TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'kelurahan' CHECK (role IN ('superadmin', 'kelurahan')),
  kelurahan     TEXT,
  display_name  TEXT,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ DEFAULT NOW(),
  last_login_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_admin_users_username ON admin_users (username);

-- ══════════════════════════════════════════════════════════
--   ADMIN ACTIVITY LOG — Jejak audit seluruh aktivitas admin
-- ══════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS admin_activity_log (
  id              BIGSERIAL PRIMARY KEY,
  actor_username  TEXT NOT NULL,
  actor_role      TEXT NOT NULL,
  actor_kelurahan TEXT,
  action          TEXT NOT NULL,
  target_id       TEXT,
  detail          JSONB DEFAULT '{}',
  ip              TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_admin_log_created ON admin_activity_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_log_actor   ON admin_activity_log (actor_username);
CREATE INDEX IF NOT EXISTS idx_admin_log_action  ON admin_activity_log (action);
