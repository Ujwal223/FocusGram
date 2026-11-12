# Security Policy

FocusGram takes user security and privacy seriously. Because FocusGram operates as an intentional digital wellness layer on top of Instagram, preserving trust and technical boundaries is critical.

---

## Supported Versions

Only the latest major/minor release receives active security patches.

| Version | Supported          |
| ------- | ------------------ |
| 3.x.x   | ☑️ |
| < 2.1.0   | x                |

---

## Authentication Architecture & Threat Model

### Why FocusGram Presents Instagram's Web Login Screen

Users often ask why FocusGram shows a login screen requesting their primary Instagram credentials instead of a standard OAuth login or a scoped app-specific password.

1. **Meta Does Not Provide Consumer OAuth or App Passwords:**
   Meta does not allow third-party consumer apps to access feeds, stories, or direct messages through an official OAuth API. Furthermore, Meta does not offer user-generated app-specific passwords or personal API tokens for Instagram accounts.
2. **The GeckoView Sandbox (Black Box Isolation):**
   When you log in to Instagram inside FocusGram, the login page is loaded directly from official Meta servers (`https://www.instagram.com/accounts/login/`) inside an isolated **Mozilla GeckoView browser engine**.
3. **Zero Credential Access:**
   FocusGram's Kotlin codebase does not intercept, inspect, listen to, or log form inputs, password fields, or two-factor authentication (2FA) tokens. The login flow is a sealed black box: your credentials flow directly between your device's browser engine and Meta's servers over TLS (HTTPS).
4. **Session Cookie Isolation:**
   Authentication cookies are stored inside Mozilla GeckoView's isolated sandboxed profile directory on your device. FocusGram does not transmit or synchronize these cookies to any external server.

---

## Core Security Invariants

1. **Zero User Telemetry & Instagram Session Sandboxing:**
   FocusGram contains no analytics SDKs, no tracking beacons, no crash reporting services, and no developer telemetry. Core Instagram browsing, credentials, and session cookies flow strictly through GeckoView to Meta's official domains and never leave your device.
   Non-Instagram external network interactions are transparently limited to:
   - **GitHub Updates & Notices:** The app queries official GitHub endpoints (`api.github.com` and `raw.githubusercontent.com`) via standard unauthenticated GET requests to check for app updates and broadcast notices.
   - **Isolated Rewarded Ad Container:** To allow users to unlock extra credits or features without paying money, an isolated Android system WebView loads third-party rewarded ads (Adsterra). This WebView is completely sandboxed from GeckoView, operates with a clean cookie jar, and has zero access to Instagram credentials or browsing data.
2. **Client-Side Scripting Boundaries:**
   Content modifications (such as hiding Explore or blurring Reels) occur via strictly scoped client-side user scripts and WebExtensions (FocusGram Content Engine and Ghostify). Content scripts are strictly restricted to layout and presentation adjustments; they never touch password inputs, authentication headers, or private user credentials.
3. **Hardware-Backed Local Storage:**
   Biometric credentials and PINs configured for App Lock and Chat Lock are verified locally using AndroidX BiometricPrompt and Android Keystore (AES-256-GCM). PIN hashes never leave the device.
4. **Link Sanitization:**
   FocusGram strips tracking parameters (such as `fbclid`, `igsh`, and UTM parameters) before links are shared or opened externally.

---

## Security Audit Report (AI Agents)

For complete transparency, FocusGram undergoes deep automated security reviews conducted by autonomous AI Security Research Agents. The technical report is published directly in the repository under `Reports/`:

* **[Security Architecture & Audit Report](Reports/SECURITY_REPORT.md)**: Deep architectural threat analysis, OWASP MASVS v2.0 verification, cryptographic implementation review (KeyStore AES-256 GCM, constant-time comparisons), direct message notification sandboxing, DOM script injection protections, canonical path traversal prevention, and Android 12+ zero-backup configuration.

---

## Reporting a Vulnerability

If you discover a security vulnerability or privacy flaw within FocusGram:

1. **Do not open a public GitHub issue.**
2. Report the vulnerability privately via **[GitHub Private Vulnerability Reporting](https://github.com/Ujwal223/FocusGram/security/advisories/new)** or by emailing `notujwal@proton.me`.
3. Provide a clear description of the vulnerability, reproduction steps, and potential impact.
4. We will acknowledge receipt within 48 hours and work with you to coordinate a release before public disclosure.

### NOTE: Focusgram & its developer are incapable of providing any bounties.