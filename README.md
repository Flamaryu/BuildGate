# BuildGate — Holistic Closed Testing & Verified QA Pipeline

BuildGate is a **Kotlin Multiplatform (Android + Companion Web)** closed-testing hub and QA pipeline built for indie Android developers navigating Google Play's 14-day closed-testing requirement.

## Key Capabilities

* **Guided 3-Step Closed-Testing Onboarding:** Walks testers through `1. Google Group` → `2. Play Store Web Opt-In` → `3. Automatic Package & Version Detection` (using Play-Store-compliant `<queries>`).
* **Sequential "Daily Testing Queue" Playlist:** Step through all enrolled 60-second daily app check-ins back-to-back in one flow.
* **Android Floating QA Overlay Pill (`QaOverlayService`):**
  * Draggable `SYSTEM_ALERT_WINDOW` pill over the tested app showing live timer progress.
  * **1-Tap Frame-Clean Screenshot** & **Capped 15s / 720p MP4 Screen Clip** via Android 14–17 `MediaProjection`.
  * Automatically **pauses the active timer & holds the Daily Queue** while logging a bug, auto-attaching `DeviceMetadata`, `appId`, `buildVersion`, and media.
  * Includes an **Ongoing QA Notification Fallback** if overlay permission is skipped.
* **Two-Tier Anti-Cheat Verification:**
  * *Tier 1 (Zero-SDK):* Real Android package inspection + monotonic background session timer (`supportsDeviceVerification = false` on Web with QR handoff to prevent browser spoofing).
  * *Tier 2 (Cryptographic Deep-Link Handshake):* Optional 10-line Kotlin callback snippet hitting the `verify-deeplink-heartbeat` Supabase Edge Function.
* **Play Console Production Access Packet Exporter:** Auto-compiles cohort buffer health, hardware diversity matrices, missions tested, and resolved bugs per build into ready-to-paste answers for Google Play's Production Access questionnaire.

---

## Running Locally (Offline Demo Mode Out of the Box)

When `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` are not set in `local.properties`, BuildGate runs automatically in reactive **Offline Demo Mode**:

```bash
# Build Android Debug APK
./gradlew :androidApp:assembleDebug

# Run Companion Web App (WasmJS - recommended)
./gradlew :webApp:wasmJsBrowserDevelopmentRun

# Run Unit Tests
./gradlew :shared:testAndroidHostTest
```

---

## Connecting Your Hosted Supabase Cloud Project (No Docker Required)

1. Create a free project at [supabase.com/dashboard](https://supabase.com/dashboard).
2. Link the repo and push the Postgres schema, RLS policies, Storage rules, and Edge Function:
   ```bash
   supabase login
   supabase link --project-ref YOUR_PROJECT_REF
   supabase db push --linked
   supabase functions deploy verify-deeplink-heartbeat --project-ref YOUR_PROJECT_REF
   ```
3. Add your project URL and publishable key to `local.properties` (git-ignored):
   ```properties
   SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
   SUPABASE_PUBLISHABLE_KEY=sb_publishable_YOUR_KEY_HERE
   ```
4. *(For Google Sign-In)* Enable the **Google** provider under **Authentication → Providers** in Supabase Studio and add `buildgate://auth` + `https://buildgate.dev/auth/callback` under **Authentication → URL Configuration → Redirect URLs**.
