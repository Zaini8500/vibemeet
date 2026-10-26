# VibeMeet API

Base URL: `http://localhost:8080` (production: your HTTPS domain only).
Auth: `Authorization: Bearer <access token>` (15 min). Refresh via `POST /auth/refresh`.

## Auth
- `POST /auth/register` — email, password (min 8), username, displayName, dateOfBirth (YYYY-MM-DD), country (2-letter), gender. Returns 403 if under 18.
- `POST /auth/login` — email + password.
- `POST /auth/google` — `{ idToken }`. Returns 428 `onboarding_required` for a new Google user.
- `POST /auth/refresh`, `POST /auth/logout`, `DELETE /auth/account` (authenticated, in-app confirmation required).

## Profile
- `GET /profile`, `GET /profile/:id`, `PUT /profile` (displayName, bio, country, interests, languages), `POST /profile/photo` (server-side upload + safety check — see code TODO).

Responses never include email, phone, full date of birth, IP or device info.

## Matching & sessions
- `POST /match/start` `{ preferences }` → `{ status: "searching" }`; pairing is emitted over WebSocket as `match_found`.
- `POST /match/cancel`, `GET /match/status`, `GET /match/ice-servers` (STUN + short-lived TURN if configured).
- `POST /session/create` `{ peerId }`, `POST /session/end` `{ sessionId }`, `GET /session/history`.

## Social & safety
- `POST /favorite`, `DELETE /favorite/:id`, `POST /block` (persistent, blocks both directions in matching), `DELETE /block/:id`.
- `POST /report` `{ reportedUserId, category, description?, sessionId? }` — categories: nudity, harassment, hate, scam, spam, underage, fake, violence, other.
- `POST /chat/message` — rate-limited + spam/abuse gate; delivery is over WebSocket.

## Coins & subscriptions
- `GET /coins`, `POST /coins/purchase`, `GET /subscription`, `POST /subscription/verify` — the last two return 501 until Google Play Developer API verification is configured. Never credit from a client claim alone.

## WebSocket (Socket.IO, authenticated)
Events: client → `signal {to, sessionId, type, payload}`, `reaction`, `chat`, `call_state`, `ping` (every 20s).
Server → `match_found`, `signal`, `reaction`, `chat`, `call_state`.

## Admin (RBAC)
- `GET /admin/reports`, `POST /admin/reports/:id/action`, `POST /admin/users/:id/suspend`, `POST /admin/users/:id/ban`, `GET /admin/analytics`.
