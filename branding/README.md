# VibeMeet branding

Original logo, created Oct 25, 2026. Not copied from any other app.

- Concept: two overlapping rounded chat bubbles forming a V, with a video
  camera lens at the centre — two people, one video chat.
- Colours: violet → pink gradient background, teal + pink bubbles.
  Matches the app theme: primary #6C4CF1, secondary #FF6B9D,
  tertiary #00C2A8.
- Files:
  - `vibemeet-logo.png` — 512×512 master (also the Play Store icon size)
  - `media-generation-vibemeet-logo-*.webp` — original generated source
  - Android launcher: `android/app/src/main/res/mipmap-*/ic_launcher.png`
    and `ic_launcher_round.png` (48/72/96/144/192 px), wired in
    `AndroidManifest.xml` as `android:icon` / `android:roundIcon`
  - In-app: `android/app/src/main/res/drawable/vibemeet_logo.png`,
    shown on the Splash screen

For a release build, Android Studio's Image Asset Studio can regenerate
adaptive-icon foreground/background layers from the 512px master if you
want a different background shape — the PNGs above already work as a
standard launcher icon.
