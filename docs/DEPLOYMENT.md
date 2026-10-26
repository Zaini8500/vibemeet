# Deployment

1. Provision PostgreSQL 16 + Redis 7 (or `docker compose up --build`).
2. Apply `backend/db/schema.sql` with psql.
3. Set real env vars from `.env.example` — JWT secrets, DATABASE_URL, REDIS_URL, GOOGLE_CLIENT_ID, TURN provider.
4. `cd backend && npm install && npm run build && npm start` (behind HTTPS + a reverse proxy; never expose Redis/Postgres publicly).
5. TURN: run coturn or a managed TURN provider. Generate short-lived credentials per user in `GET /match/ice-servers`. Never ship TURN credentials in the APK.
6. Android: open `android/` in Android Studio, set `targetSdk` to the latest stable at release time, add `google-services.json` if Google Sign-In is enabled, set Play Billing product IDs (monthly $4.99, annual $29.99, coin packs 100/500/1000 — prices configurable via Play Console / Remote Config, not hardcoded in code), set AdMob IDs, and build a signed release bundle.
7. Refresh-token revocation: implement a Redis denylist for refresh tokens on logout / password change / ban before production (noted in auth.ts).
8. Admin role: replace the dev `x-admin-role` header check with a DB-backed `admin_users` table before any public deployment.

# Google Play release checklist

- [ ] Privacy policy + Terms + Community Guidelines hosted at public URLs (templates below are starting points, not legal advice — get a lawyer to review).
- [ ] Data Safety form: data collected (email, DOB for age check only, country, interests, profile photo), data shared (TURN provider, Play Billing, moderation provider if enabled), account deletion.
- [ ] In-app + web account deletion, with confirmation (API: `DELETE /auth/account`).
- [ ] Content rating questionnaire completed; app rated 18+ in practice (age gate enforced server-side).
- [ ] User-generated-content controls: report (9 categories), block (persistent), moderation queue, suspend/ban — all implemented in backend + admin.
- [ ] No ads during active video calls; premium removes ads.
- [ ] Safety features are free, never paywalled.
- [ ] No video recording by default; no fake users; no simulated calls.
- [ ] Permissions explained at request time (camera/mic only when matching starts).
- [ ] Test on low-end devices, poor networks, backgrounding, network switching, camera/mic unavailable.
- [ ] Closed testing track first, then staged rollout. Review current Play policies again at release — policies change.
