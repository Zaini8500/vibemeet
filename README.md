# VibeMeet — Random 1-to-1 Video Chat (MVP)

Original brand, original UI and original implementation. Inspired only by the
*generic* concept of random 1-to-1 video discovery. No third-party app's code,
assets, branding, icons, text or screenshots are used.

**Primary journey:** Install → Create account → Interests → START MATCHING →
Finding someone… → Connected → Video chat → NEXT → New person.

## What is actually built here (Phase 1 MVP, honestly scoped)

- **Android app (Kotlin, Jetpack Compose, MVVM, Hilt):** Splash, Google/email
  auth screens, 7-step onboarding in under 60 seconds, Home, animated
  Matching, full-screen Video Chat with floating local preview, NEXT, mute /
  camera / switch-camera / speaker, Report, Block, Favorites, Connections,
  Profile. Light + dark theme, English + Urdu string resources.
- **Play Billing verification (Oct 25 update):** implemented server-side
  via Google's Android Publisher API — products and subscriptions are only
  credited after Google confirms PURCHASED / ACTIVE, purchase tokens are
  UNIQUE so a replay cannot double-credit, and the Android app never grants
  premium/coins on a local Play callback alone. See `docs/PLAY_BILLING.md`.
  End-to-end testing still needs Play Console products + a service account.
- **Wiring (Oct 25 update):** screens are now wired to the live backend —
  DataStore token store, Retrofit + JWT interceptor (di/AppModule),
  VibeMeetRepository, real Auth/Home/Matching/Video ViewModels, Socket.IO
  signalling connected on Home, `match_found` → video, initiator creates the
  WebRTC offer, answer/ICE are relayed, and remote/local video render through
  SurfaceViewRenderer. NEXT closes the old PeerConnection, ends the session
  on the server, and starts a fresh match. Base URL is `10.0.2.2:8080` for
  the emulator — change it to your deployed HTTPS backend for a real phone.
- **Real WebRTC client:** `WebRtcManager` uses `org.webrtc` (PeerConnection,
  SDP offer/answer, ICE, adaptive video via `setVideo...` / bandwidth
  estimation callbacks). It does **not** simulate a call. If no peer, no
  STUN/TURN, or no network is available, it reports Connecting / Poor /
  Reconnecting / Disconnected — it never fakes "Connected".
- **Backend (Node.js + TypeScript + Express + Socket.IO):** JWT auth
  (access + refresh), bcrypt passwords, Google ID-token verification,
  profile/interests, Redis matchmaking queue with configurable weighted
  scoring, WebSocket signalling (offer/answer/ICE), session history, block
  (persistent, excludes future matches), report, favorites, coins and
  subscription-verification *interfaces*. PostgreSQL schema in `backend/db/schema.sql`.
- **Safety first:** 18+ age gate (date of birth checked server-side, full
  DOB never exposed in API responses), report categories, block, rate
  limits, text spam/abuse checks, and a moderation hook that samples —
  it does **not** record calls by default. See `docs/PLAY_CHECKLIST.md`.
- **Admin dashboard:** static web UI in `admin/` calling the backend
  admin APIs (RBAC: `admin` / `moderator` roles).

## What is NOT finished / needs real credentials before production

This is a production-*structured* MVP scaffold, not a published Play app.
Do not ship it until you supply these — each integration point is marked
`TODO (integration)` in code, never silently faked:

1. **TURN server** (e.g. coturn / Twilio / Cloudflare) — STUN is configured
   by default; TURN credentials must come from your backend at runtime,
   never hardcoded in the app. See `backend/src/services/turn.ts` note in
   `server.ts` comments and `docs/DEPLOYMENT.md`.
2. **Google Sign-In** OAuth client ID, **Google Play Billing** product IDs,
   **AdMob** unit IDs, **Firebase / push** (optional), and a real
   **translation provider** (Phase 3) and **image-moderation provider**
   (Phase 2). Their interfaces exist; keys do not.
3. **Play Console setup:** privacy policy URL, data-safety form, content
   rating questionnaire, account-deletion URL. Templates + checklist in
   `docs/`.
4. A second real device/user to test a live call — there are **no fake
   users in production code**. `MOCK_MODE` exists only for UI development
   and is off by default (`BuildConfig` / env flag, see below).
5. Google Sign-In is now set up (Oct 25, 2026): project VibeMeet
   (vibemeet-512621, account zaintahir7000@gmail.com), consent screen in
   Testing mode, Web client ID already put in
   `android/app/build.gradle.kts`:
   395071022050-j6bh5mpkbripkmnv2kr8ehtlh1ioplvs.apps.googleusercontent.com
   You must still set the same value as `GOOGLE_CLIENT_ID` in the backend
   env on your server. Android OAuth client still needs your SHA-1.
6. TURN is now implemented (Oct 25 update): the backend generates
   per-user, 24-hour TURN credentials (coturn REST API) in
   `GET /match/ice-servers` when `TURN_URLS` + `TURN_SHARED_SECRET` are set.
   You must still deploy the TURN server itself — see `docs/TURN_SETUP.md`
   for self-hosted coturn or a managed provider — and test a call on a
   network where P2P is blocked.

## Quick start

```bash
# Backend
cp .env.example .env   # fill DATABASE_URL, REDIS_URL, JWT secrets, TURN provider
cd backend && npm install && npm run dev
# or: docker compose up --build   (postgres + redis + backend)

# Android (Android Studio, latest stable)
# Open android/ , set minSdk 26 (Play requirement at time of writing:
# targetSdk = latest stable, see app/build.gradle.kts),
# add google-services.json for Google Sign-In if you enable it,
# run on a physical device (emulator camera/WebRTC is unreliable).
```

## Repo layout

```
backend/   Express + TS + Socket.IO, PostgreSQL schema, Redis matching
android/   Kotlin + Compose app
admin/     Web admin dashboard (calls /admin/*)
docs/      API.md, DEPLOYMENT.md, PLAY_CHECKLIST.md, policy templates
```

## Architecture (brief)

```
Android (Compose UI -> ViewModel -> Repository)
   |  REST (JWT) + WebSocket (Socket.IO, authenticated)
   v
Backend (Express) -> PostgreSQL (durable) + Redis (queue/presence/rate-limit)
   |  signalling only — media is peer-to-peer WebRTC
   v
Peer device (WebRTC, STUN, TURN fallback)
```

Matching weights (configurable, `MATCH_WEIGHTS` env / admin config):
shared interests 30%, language 20%, preferred region 20%,
user preferences 20%, connection quality 10%. Private matching inputs are
never returned to the other user.

## Costs kept low

P2P video (no media server), Redis for ephemeral state, Postgres only for
durable rows, compressed profile images, no AI API in the core call path.
Translation / icebreakers are opt-in Phase 3/4 features behind a provider
interface, not a per-call dependency.

## Tests

```bash
cd backend && npm test   # matching weights, age gate, block exclusion
# Android unit tests: android/app/src/test/... (age gate, matching prefs)
```
