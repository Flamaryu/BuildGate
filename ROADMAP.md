# BuildGate — Live Product Roadmap & Feature Tracker

🚀 **[Open the Live Interactive GitHub Roadmap Board](https://github.com/users/Flamaryu/projects/3)** · 💡 **[Suggest a Feature or Report a Bug](https://github.com/Flamaryu/BuildGate/issues/new/choose)** · ☕ **[Support on Ko-fi](https://ko-fi.com/flamaryu)**

> **How to use this live document:**
> - Vote on features by adding a **👍 reaction** on the linked GitHub Issue (sorted in the **🔥 Most Requested** view on our public board).
> - Move any feature row between status sections as it progresses (`Suggested` → `Considering` → `Planned` → `In Build` → `In Preview` → `Shipped` or `Rejected`).
> - Update **Votes / Engagement** and **Times Suggested** to prioritize what indie Android devs actually want most.

---

## Status Pipeline
`💡 Suggested` ➔ `🤔 Considering` ➔ `📋 Planned (To Add)` ➔ `🚧 In Build` ➔ `🧪 In Preview` ➔ `✅ Shipped` *(or `🚫 Rejected`)*

---

## ✅ Shipped (Added & Verified)
Features live in the Android app, Web Companion, and Supabase backend.

| ID | Feature | Area | Votes / Engagement | Times Suggested | Notes |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `BG` | KMP Android + Web Companion (WasmJS/JS) shared UI | Core | — | — | Single Compose codebase with URL hash routing |
| `BG` | Live Supabase Auth (PKCE + Email/Google) & Postgres RLS | Backend | — | — | Profiles, Karma ledger triggers, `feedback-media` bucket |
| `BG` | 3-Step Guided Closed-Testing Opt-In Wizard | Tester | — | — | Google Group ➔ Play Web Opt-In ➔ `PackageManager` detect |
| `BG` | Daily Testing Queue Playlist & 14-Day Streak + 1-Day Grace | Tester | — | — | Sequential 60s daily check-ins + Karma rewards |
| `BG` | Floating QA Overlay Pill (`QaOverlayService`) | Android QA | — | — | Pause-safe bug logging, 1-tap screenshot, 15s screen clip |
| `BG` | Direct **Verify & Launch Next** + **Close Pill** in Overlay | Android QA | — | — | Advance through daily queue without returning to BuildGate |
| `BG` | Opt-In Foreground Anti-Cheat (`UsageStatsManager`) | Anti-Cheat | — | — | Freezes 60s timer if target app is backgrounded or screen locks |
| `BG` | Real ISO/IEC 18004 Scannable Web ➔ Phone QR Handoff | Web / Mobile | — | — | Pure-Kotlin QR encoder launching `buildgate://app/<pkg>` |
| `BG` | Unified Tier-2 Deep-Link Handshake + 6-Stack Publish Wizard | Developer | — | — | No-Code vs. Deep-Link cards; Kotlin/Java/Flutter/RN/Unity/Godot split snippets + ADB test |
| `BG` | Role-Scoped QA & Triage Hub (Owner vs. Read-Only Known Issues) | Developer / Tester | — | — | Grouped App Picker, sub-tabs, +25⚡ bug bounties, Play Packet export |
| `BG` | Foldable Continuity (Cover ↔ Inner Screen) | Android UX | — | — | `configChanges` + `movableContentOf` + `rememberSaveable` |
| `BG-013` | Hosted Web Companion + Cloudflare R2 Media Storage | Web / Infra | 0 | 1 | Live at `https://buildgatehub.com` on Cloudflare Workers Static Assets + `buildgate-media` R2 bucket |

---

## 🧪 In Preview (Built / Testing Internally Before Public Rollout)
Features working in local/closed builds awaiting live testing and polish.

| ID | Feature | Area | Votes / Engagement | Times Suggested | Preview Status |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `BG-001` | Live Supabase Edge Function (`verify-deeplink-heartbeat`) | Anti-Cheat | 0 | 1 | Deployed to Supabase; needs live end-to-end test in one of the 3 closed-test games |
| `BG-002` | Google OAuth Sign-In Provider | Auth | 0 | 1 | Code & redirect URLs wired; needs Google Cloud OAuth Client ID/Secret in Supabase Dashboard |

---

## 🚧 In Build (Actively Being Developed)
Features currently in progress.

| ID | Feature | Area | Votes / Engagement | Times Suggested | Owner / Branch |
| :--- | :--- | :--- | :---: | :---: | :--- |
| — | *(Move next priority item from **Planned** here when starting work)* | — | — | — | — |

---

## 📋 Planned (Committed — To Add Next)
High-priority features approved for upcoming milestones.

| ID | Feature | Area | Votes / Engagement | Times Suggested | Priority | Summary |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| `BG-011` | **AI QA Mission Generator (Gemini)** | AI / Developer | 0 | 1 | P0 | Paste app description or release notes ➔ AI generates concrete, testable QA missions |
| `BG-012` | **Randomized / Stratified Mission Assignment** | AI / QA | 0 | 1 | P0 | Randomly assigns different missions across your 16+ testers so every screen/flow gets coverage instead of everyone testing screen 1 |
| `BG-014` | **Smart Daily Streak Push Reminders (FCM + Cron)** | Retention | 0 | 1 | P1 | Remind testers 2h before UTC midnight if they have unverified queued apps; alert devs before Day-14 roster drops below 12 |
| `BG-010` | **In-Overlay Mission Checklist & Pass/Fail Tap** | Android QA | 0 | 1 | P1 | Show the tester's assigned mission right inside the floating pill so they can mark Pass/Fail or attach proof without leaving the app |
| `BG-015` | **Edit / Update Published Campaign & Build Version** | Developer | 0 | 1 | P1 | Let app owners bump `currentBuildVersion`, update Play/Group links, or upgrade from No-Code to Deep-Link Handshake |

---

## 🤔 Considering (Thinking About Adding)
Strong ideas under evaluation — move to **Planned** once scoped or **Rejected** if out of scope.

| ID | Feature | Area | Votes / Engagement | Times Suggested | Why We're Thinking About It |
| :--- | :--- | :--- | :---: | :---: | :--- |
| `BG-003` | **AI Bug Deduplication & Repro Cleanup** | AI / Triage | 0 | 0 | Auto-clusters duplicate tester reports (*"4 testers hit this on Android 15"*) and formats messy notes into clean repro steps |
| `BG-004` | **AI-Drafted Play Console Production Access Answers** | AI / Developer | 0 | 0 | Uses your verified 14-day logs, device matrix, and fixed bugs to draft tailored answers to Google Play's Production Access questionnaire |
| `BG-005` | **Device & OS Coverage Matrix Recruiting** | QA / Matching | 0 | 0 | Let devs request hardware gaps (*"Need 3 Samsung or Android 13 testers"*) with bonus Karma for matching devices |
| `BG-006` | **Build-Update "Verify Fix" Regression Loop** | QA Pipeline | 0 | 0 | When a dev bumps version (`v1.6.0 ➔ v1.6.1`), auto-assign a quick "Verify Fix" mission for bugs marked Fixed |
| `BG-007` | **GitHub Issues / Linear / Discord Webhook Sync** | Integrations | 0 | 0 | Push triaged bugs (with screenshot, video clip, device specs) directly to a developer's repo or Discord channel |
| `BG-008` | **Tester Reliability Tiers & Anti-Churn Score** | Anti-Cheat | 0 | 0 | Surface 14-day completion rate & accepted bug ratio; prioritize reliable testers' apps and throttle Day-2 uninstallers |
| `BG-009` | **Optional Logcat / Crash Stack-Trace Attachment** | Android QA | 0 | 0 | Lightweight optional helper to attach recent crash logs alongside screenshots and 15s clips |

---

## 💡 Community Suggested (Inbox)
New ideas submitted by indie devs & testers. Triage into **Considering**, **Planned**, or **Rejected**.

| ID | Feature | Suggested By | Votes / Engagement | Times Suggested | GitHub Issue | Notes |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| — | *(Add community suggestions here as they come in)* | — | 0 | 0 | — | — |

---

## 🚫 Rejected / Won't Build
Documenting what we intentionally say **no** to (and why) saves contributors time and keeps BuildGate secure and Play-Store compliant.

| ID | Feature | Votes / Engagement | Times Suggested | Reason Rejected |
| :--- | :--- | :---: | :---: | :--- |
| `BG-R01` | Browser-only 60s check-ins (no Android device) | 0 | 0 | Easily spoofed; undermines Google Play's real-device 14-day engagement requirement |
| `BG-R02` | Mandatory `UsageStatsManager` for all testers | 0 | 0 | Hurts onboarding and Play Store review; kept strictly opt-in with graceful fallback |
| `BG-R03` | Automated / bot-driven app opening or auto-clicking | 0 | 0 | Violates Google Play policies and defeats real QA feedback |

---

## Quick Template for Adding a Feature
```markdown
| `BG-0XX` | **Feature Title** | Area | 0 | 1 | Short description of the developer/tester benefit |
```
