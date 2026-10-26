-- VibeMeet PostgreSQL schema. Run with psql. No fake/seed users are created.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email CITEXT UNIQUE, phone TEXT UNIQUE,
  username TEXT UNIQUE NOT NULL, display_name TEXT NOT NULL,
  date_of_birth DATE NOT NULL, country CHAR(2) NOT NULL,
  gender TEXT CHECK (gender IN ('female','male','non_binary','prefer_not_to_say')),
  bio TEXT DEFAULT '', profile_photo_url TEXT,
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','suspended','banned')),
  verification_status TEXT NOT NULL DEFAULT 'basic' CHECK (verification_status IN ('none','basic','advanced')),
  is_premium BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS auth_credentials (
  user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  password_hash TEXT, google_sub TEXT UNIQUE
);
CREATE TABLE IF NOT EXISTS interests (id SERIAL PRIMARY KEY, name TEXT UNIQUE NOT NULL, category TEXT NOT NULL);
INSERT INTO interests (name, category) VALUES
 ('Gaming','fun'),('Football','sports'),('Cricket','sports'),('Music','arts'),
 ('Movies','arts'),('Technology','learning'),('Travel','lifestyle'),('Fitness','health'),
 ('Food','lifestyle'),('Cars','fun'),('Anime','arts'),('Education','learning'),
 ('Business','learning'),('Photography','arts')
ON CONFLICT (name) DO NOTHING;
CREATE TABLE IF NOT EXISTS user_interests (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  interest_id INT REFERENCES interests(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, interest_id)
);
CREATE TABLE IF NOT EXISTS user_languages (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE, language CHAR(2) NOT NULL,
  PRIMARY KEY (user_id, language)
);
CREATE TABLE IF NOT EXISTS matches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_a UUID REFERENCES users(id), user_b UUID REFERENCES users(id),
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(), ended_at TIMESTAMPTZ,
  duration_seconds INT, status TEXT NOT NULL DEFAULT 'active'
);
CREATE TABLE IF NOT EXISTS blocks (
  blocker_id UUID REFERENCES users(id) ON DELETE CASCADE,
  blocked_id UUID REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (blocker_id, blocked_id)
);
CREATE TABLE IF NOT EXISTS favorites (
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  favorite_id UUID REFERENCES users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (user_id, favorite_id)
);
CREATE TABLE IF NOT EXISTS reports (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id UUID REFERENCES users(id), reported_user_id UUID REFERENCES users(id),
  category TEXT NOT NULL CHECK (category IN ('nudity','harassment','hate','scam','spam','underage','fake','violence','other')),
  description TEXT DEFAULT '', session_id UUID REFERENCES matches(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','reviewed','actioned','dismissed'))
);
CREATE TABLE IF NOT EXISTS subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  product_id TEXT NOT NULL, platform TEXT NOT NULL DEFAULT 'google_play',
  status TEXT NOT NULL, expires_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS coins (user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE, balance INT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID REFERENCES users(id),
  product_id TEXT NOT NULL, amount NUMERIC(10,2) NOT NULL, currency CHAR(3) NOT NULL DEFAULT 'USD',
  platform TEXT NOT NULL DEFAULT 'google_play', status TEXT NOT NULL, purchase_token TEXT UNIQUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_matches_users ON matches(user_a, user_b);
CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status, created_at);
-- Privacy: never SELECT date_of_birth, email or phone into peer-facing responses.
-- Those columns are only for auth / age verification / account management.
