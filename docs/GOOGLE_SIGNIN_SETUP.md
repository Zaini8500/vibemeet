# Google Sign-In setup for VibeMeet

Status: code is wired. This page tracks the console setup itself.

## What the code needs

- **Web OAuth client ID** — used in two places, same value in both:
  - Android: `GOOGLE_SERVER_CLIENT_ID` in `android/app/build.gradle.kts`
    (this is the ID passed to `requestIdToken()`)
  - Backend: `GOOGLE_CLIENT_ID` in the backend env
    (this is the audience checked in `POST /auth/google` and
    `POST /auth/google/complete`)
- **Android OAuth client** — needs your signing key SHA-1 fingerprint.
  Get it in Android Studio (Gradle → signingReport) or with:
  ```bash
  keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
  ```
  For a release build, use your release keystore's SHA-1, not the debug one.
  Until you supply a SHA-1, the Web client is the part that can be created
  first — it is the ID the code actually uses for the token.

## Console steps (what the browser task is doing)

1. Sign in to https://console.cloud.google.com/ with your Google account.
2. Create a project named **VibeMeet** (project ID can be auto-generated).
3. APIs & Services → OAuth consent screen:
   - User type: External (for testing, add yourself as a test user)
   - App name: VibeMeet
   - User support email: your signed-in Google account
   - Scopes: only `email` and `profile` (default) — nothing else is needed
   - Do NOT publish the consent screen yet; testing mode is enough for now.
4. APIs & Services → Credentials → Create Credentials → OAuth client ID:
   - Application type: **Web application**
   - Name: VibeMeet Web
   - Copy the **Client ID** (ends in `.apps.googleusercontent.com`).
   - The Client Secret is NOT needed by this app and must never be put
     in the Android app. The backend only checks the idToken against the
     Client ID.
5. Later, with your SHA-1: Credentials → OAuth client ID → **Android**,
   package name `com.vibemeet.app`, SHA-1 from step above.

## After the Client ID is known

1. Replace `YOUR_GOOGLE_WEB_CLIENT_ID` in `android/app/build.gradle.kts`.
2. Set `GOOGLE_CLIENT_ID=<same ID>` in the backend env and restart it.
3. Test: open the app, tap Continue with Google, pick an account.
   - Existing account → home screen.
   - New account → onboarding → account created via `/auth/google/complete`.

## Values (completed Oct 25, 2026)

- Google account used: zaintahir7000@gmail.com
- Project name: VibeMeet
- Project ID: vibemeet-512621
- Web Client ID: 395071022050-j6bh5mpkbripkmnv2kr8ehtlh1ioplvs.apps.googleusercontent.com
- OAuth consent screen: External, Testing (not published), support + developer contact zaintahir7000@gmail.com, only email/profile scopes, test user zaintahir7000@gmail.com
- 2-Step Verification: enabled permanently for this account (approved by Zain, required by Google Cloud)
- Android client created? No — waiting on SHA-1 (see above)
- Billing: not enabled, no billing settings changed
- Backend env to set on your server (not in this repo): GOOGLE_CLIENT_ID=395071022050-j6bh5mpkbripkmnv2kr8ehtlh1ioplvs.apps.googleusercontent.com
