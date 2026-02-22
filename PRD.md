# FocusGram — Product Requirements Document (Canvas)

> Working title: **FocusGram**

---

## Product Type

Personal-use Flutter mobile application (Android). WebView wrapper around Instagram for private, distraction-free use.

## Primary Goal

Allow full use of Instagram (feed, stories, notes, DMs, profile) **without Reels or Explore distractions**, while preserving the ability to open a Reel **only when it is sent directly in a message**. Reels must not be discoverable anywhere else in the app.

---

## 1. Problem Statement

Instagram's Reels and Explore experiences create compulsive, endless‑scroll behaviours. The user wants full functionality of Instagram *except* persistent exposure to Reels and similar autoplay distractions. Reels may be accessed intentionally and in a controlled way (session/time/cooldown), and Reels opened from DMs must not allow the user to scroll into other Reels.

---

## 2. Core Features (MVP + Integrated Phase 2)

These include all Phase 2 items integrated into the main product.

### 2.1 Embedded Instagram

* WebView loads `https://www.instagram.com`
* JavaScript enabled
* Custom user-agent to reduce login friction
* Cookie/session persistence stored locally

### 2.2 Global Reel & Explore Blocking (always-on)

* Remove/hide the Reels tab, Explore tab, and any UI element that reveals Reels elsewhere (profile grid toggles that surface Reels, Explore cards, thumbnails linking to `/reel/`).
* Block navigation to any URL containing `/reel/` or `/reels` unless in an active Reel session or when specifically opening a Reel message item.
* Inject a persistent CSS style (`hide-reels-style`) + MutationObserver to remove dynamic elements injected by Instagram's SPA.

### 2.3 DM‑Reel Exception (one‑off, isolated playback)

* If a Reel URL is received via Direct Message (DM) and the user taps it in the message thread, the app will allow opening that single Reel in an isolated player overlay.
* The isolated player must:

  * Load only the single Reel content (not the Reels feed).
  * Disable gestures/controls that would navigate to other Reels (no left/right swipe to next Reel).
  * Provide explicit controls: Play/Pause, Close, Share (if desired).
  * Respect session/time/cooldown and count viewing duration toward limits.

### 2.4 Session & Daily Controls (customizable)

Provide settings and enforcement for controlled Reel consumption:

**Settings**

* Daily Total Reel Time (configurable, e.g., 0–120 minutes)
* Per‑Session Reel Time Limit (configurable, e.g., 1–30 minutes)
* Session Cooldown Time (configurable, e.g., 5–180 minutes) — the minimum wait between sessions
* Session Shortcuts (preset buttons: 1, 5, 10, 15 minutes)

**Behavior/Enforcement**

* A session may be started by user explicitly (via FAB or DM Reel tap when allowed).
* When a session starts, a countdown runs; when it reaches zero, the session ends and Reels are blocked again.
* All viewing time (including DM‑opened Reel play) counts toward the daily total.
* If daily total is exhausted, Reel sessions are blocked until midnight local device time, or until user increases limit in settings.
* Cooldown prevents immediately starting a new session until cooldown expires. The cooldown may be overridden only by changing settings (confirmed by an intentional action) — optional: require PIN to override.

### 2.5 Additional Controls & UX

* Quick status indicator in app chrome showing: `Reels: blocked` / `Reels: session active (mm:ss left)` / `Daily left: XX min`.
* Modal Reel Session UI: when enabling a session, present a small modal confirming session length, remaining daily minutes, and cooldown on completion.
* Option to blur Reels instead of hide (toggle in settings) — still blocks navigation but visually indicates presence.
* Option for long‑press unlock: user must long‑press the Reel Session button for 2 seconds to start a session (reduces impulsive enabling).

---

## 3. Non‑Goals

* No public distribution via Play Store (personal use only)
* No scraping or automated interactions with Instagram
* No use of private Instagram APIs
* Not attempting to permanently alter Instagram servers or content

---

## 4. Functional Requirements (detailed)

| ID | Requirement                                                                               | Priority |
| -- | ----------------------------------------------------------------------------------------- | -------- |
| F1 | Load Instagram in WebView with persistent session                                         | High     |
| F2 | Inject/maintain CSS to hide Reels/Explore everywhere                                      | High     |
| F3 | Block navigation to `/reel` URLs globally unless ephemeral session or DM single‑Reel open | High     |
| F4 | Allow single‑Reel open from DM in isolated player (no swipe to other reels)               | High     |
| F5 | Provide UI to start a Reel session limited by per‑session and daily settings              | High     |
| F6 | Enforce session cooldowns between sessions                                                | High     |
| F7 | Track and persist daily usage and session history locally                                 | High     |
| F8 | Provide override/change settings with explicit confirm (optional PIN)                     | Medium   |
| F9 | Provide visual feedback and counters on main UI                                           | High     |

---

## 5. Technical Architecture

### Framework & Libraries

* Flutter (stable)
* `webview_flutter` for WebView
* `shared_preferences` for local persistence
* `intl` for date handling and resets
* Optional: `flutter_local_notifications` for session reminders/cooldown completion

### High-level Components

* **MainWebViewPage** — full‑screen WebView + top status bar + FAB for Reel Session
* **InjectionController** — handles JS/CSS injection, MutationObserver lifecycle, and re‑apply logic
* **NavigationGuard** — intercepts navigation requests and blocks `/reel` URLs when necessary
* **ReelPlayerOverlay** — isolated player used only for opening Reel from DM (no swiping)
* **SessionManager** — enforces per‑session timer, daily totals, cooldowns, and persistence
* **Settings** — UI for user to configure daily limit, session length, cooldown, blur/hide toggle

### JS/CSS Injection Patterns (examples)

* Insert a single `style` element with id `hide-reels-style` containing selectors for `href*="/reel"`, `href*="/reels"`, Reels tab anchors and Explore cards.
* MutationObserver that removes or hides any dynamically added nodes matching those selectors.
* Example-safe selectors: `a[href*="/reel"], a[href*="/reels"], nav a[href*="/reels"], [role="button"] [aria-label*="Reels"]`.

---

## 6. UX / Wireframes (textual)

**Main screen**

* WebView occupying most of the screen
* Top compact status bar: `Reels: Blocked • Daily left: 45m` (tappable to open Session modal)
* Floating Action Button (FAB) bottom-right: play icon — opens Reel Session modal

**Session modal**

* Presets: 1 / 5 / 10 / 15 minutes
* Input to set custom minutes
* Show `Daily left: X min` and `Cooldown: Y min remaining` if applicable
* Confirm button: `Start Session`

**DM Reel tap flow**

* User taps Reel link in DM
* If session active and daily left > 0 → open in ReelPlayerOverlay
* If session inactive → show small prompt: `Open this Reel? This will start a 5‑minute session (or choose length).` Confirm to open; counts toward session & daily totals.

**End of session**

* Overlay message: `Session ended. Reels are blocked.` with cooldown timer
* Option to extend session (only if daily minutes available and cooldown rules allow)

---

## 7. Data Model & Persistence

Stored locally via `shared_preferences` (or a small local DB if desired):

* `dailyDate` (YYYY-MM-DD) — date of last reset
* `dailyUsedMinutes` (int)
* `sessionActive` (bool) + `sessionExpiryTimestamp` (ms)
* `lastSessionEndTimestamp` (ms)
* `settings`: { dailyLimitMinutes, defaultSessionMinutes, cooldownMinutes, blurInsteadOfHide, requireLongPress }
* `sessionHistory[]` (timestamp, duration) — optional, capped locally

Reset logic: check `dailyDate` on app start / resume; if different from local device date, reset `dailyUsedMinutes` to 0 and update `dailyDate`.

---

## 8. Edge Cases & Rules

* If a Reel message contains a playlist or multiple reels link, block additional navigation — only allow the primary Reel to load.
* If Instagram tries to redirect from a DM Reel link into the Reels feed, intercept and force load of the single Reel content in `ReelPlayerOverlay`.
* If login prompts or security interstitials appear in WebView (2FA / suspicious login), surface them to the user; do not attempt to automate.
* If DOM selectors fail (Instagram update), fall back to broader `href*` checks and reapply; show a small banner to the user: `Reel blocker needs update` with troubleshooting.

---

## 9. Success Criteria

* Reels are not visible anywhere by default (tabs, explore, profile toggles)
* Tapping a Reel sent in DM opens only that Reel and does not allow navigating to others
* Session limits and daily totals are enforced reliably — user cannot bypass session/cooldown without changing settings and confirming
* UX is intuitive: starting/stopping sessions, seeing remaining time, and cooldowns are clear

---

## 10. Definition of Done

* App loads Instagram and preserves login across restarts
* Injected CSS/JS reliably hides Reels and blocks `/reel` navigation
* DM‑opened Reel flow works as isolated playback with no swipe navigation to other reels
* Session start/stop, daily enforcement, and cooldown behavior function and persist
* Settings screen implemented and persisting user preferences

---

## 11. Next Steps (recommended)

1. Create minimal Flutter skeleton with `webview_flutter` and SessionManager stub
2. Implement CSS/JS injection and test with local device Instagram login
3. Implement NavigationGuard and ReelPlayerOverlay
4. Add Settings and persistence
5. Test DM Reel flows thoroughly (multiple DM formats, external links)
6. Iterate selectors if Instagram DOM changes

---

## 12. Notes & Considerations

* This is for personal use only. Avoid publishing or distributing a wrapper app.
* Instagram may change behaviours; expect occasional maintenance.
* Consider adding a simple debug UI (visible only in dev builds) to reapply selectors and show blocked navigation attempts.

---

*End of PRD.*
