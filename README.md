<div align="center"> 
 [ This APK Doesn't require the "Advanced Flow" to install on Android Devices. ]
</div>
<div align="center">

<img width="120" height="120" alt="FocusGram Logo" src="assets/images/focusgram.png" style="border-radius: 28px;" />

# FocusGram

**Digital wellness & intentionality layer for Instagram on Android**

An open-source Android application built with 100% native Kotlin and Jetpack Compose that loads Instagram inside Mozilla GeckoView with embedded WebExtensions (uBlock Origin), client-side content filters, and hardware-backed wellness controls.

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL_3.0-blue.svg?style=for-the-badge)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Release](https://img.shields.io/github/v/release/Ujwal223/FocusGram?style=for-the-badge&color=0969da)](https://github.com/Ujwal223/FocusGram/releases)
[![Downloads](https://img.shields.io/github/downloads/Ujwal223/FocusGram/total?style=for-the-badge&color=fa8900)](https://github.com/Ujwal223/FocusGram/releases)
[![Uptodown](https://img.shields.io/badge/Uptodown-Download-00A859?style=for-the-badge&logo=android&logoColor=white)](https://focusgram.en.uptodown.com/android)
[![Support](https://img.shields.io/badge/Support-BuyMeMomo-FFDD00.svg?style=for-the-badge&logo=buymeacoffee&logoColor=black)](https://buymemomo.com/ujwal)

<br/>
</div>


---

## The Problem & The Solution

<p align="center">
  <img src="assets/images/app-demo.png" alt="FocusGram App Demo" width="100%" />
</p>

Modern social platforms are engineered around algorithmic feedback loops, autoplaying short-form videos, and recommendation feeds designed to maximize screen time at the expense of intentionality. Most users do not want to quit Instagram entirely—they want to message friends, reply to stories, or share moments without losing hours to an endless Reel rabbit hole.

**FocusGram solves this at the browser engine level:**
Instead of modifying Instagram's proprietary binary or relying on unofficial reverse-engineered APIs, FocusGram embeds a sandboxed **Mozilla GeckoView browser engine** (`org.mozilla.geckoview:geckoview-omni`) paired with an embedded **uBlock Origin** WebExtension. All ad-blocking, element suppression, and session guardrails operate strictly client-side on the rendered web DOM.

```
┌─────────────────────────────────────────────────────────────────┐
│                    FocusGram Android Shell                      │
├─────────────────┬───────────────────────────────┬───────────────┤
│    Wellness     │         Guardrails            │   App Lock    │
│  [Screen Time]  │     [Timed Reel Session]      │ [Biometrics]  │
├─────────────────┴───────────────────────────────┴───────────────┤
│                 Jetpack Compose Native UI Shell                 │
├─────────────────────────────────────────────────────────────────┤
│            Mozilla GeckoView Sandboxed Browser Engine           │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  Embedded uBlock Origin Extension + Content UserScripts │   │
│   │  (Ad blocking, Autoplay suppression, Element blurring) │   │
│   └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                 Meta Official Web Servers (HTTPS)
```

---

## Core Capabilities

<table>
  <tr>
    <td width="50%">
      <h3>Focus Guardrails</h3>
      <ul>
        <li><b>Timed Reel Sessions</b>: Watch Reels in structured blocks (1–30 min) before a gentle pause.</li>
        <li><b>Daily Screen Time Limits</b>: Set strict daily quotas for overall usage.</li>
        <li><b>Mindfulness Breath Gate</b>: A 10-second reflection pause before entering the app.</li>
        <li><b>Cooldown Periods</b>: Enforce mandatory break intervals between sessions.</li>
      </ul>
    </td>
    <td width="50%">
      <h3>Calm Feeds & Content Control</h3>
      <ul>
        <li><b>Blur Explore & Feed</b>: Keep recommendation grids blurred until tapped.</li>
        <li><b>Autoplay Blocker</b>: Prevents intrusive video playback from hijacking attention.</li>
        <li><b>Minimal Mode</b>: Strips unnecessary clutter down to essential DMs and Feed.</li>
        <li><b>Grayscale Filter</b>: Eliminates vibrant dopamine colors on demand or on schedule.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>Privacy & Security Locks</h3>
      <ul>
        <li><b>Biometric & PIN App Lock</b>: Secure the entire app behind Android Keystore authentication.</li>
        <li><b>Individual Chat Lock</b>: Lock specific sensitive direct message conversations with separate PINs.</li>
        <li><b>Link Sanitizer</b>: Strips <code>fbclid</code> and tracking parameters before sharing.</li>
        <li><b>Zero Data Transmission</b>: No telemetry, no analytics, no third-party network SDKs.</li>
      </ul>
    </td>
    <td width="50%">
      <h3>Native Android Experience</h3>
      <ul>
        <li><b>Modern Apple-Style Onboarding</b>: Elegant guided setup explaining permissions and boundaries.</li>
        <li><b>Update Checker</b>: In-app GitHub release notifications with one-tap downloads.</li>
        <li><b>Dynamic Launcher Icons</b>: Custom app icons including OLED Pitch Black, Abstract, and Classic.</li>
        <li><b>Reel History & Local Snapshots</b>: Save and revisit content stored completely offline in Room DB.</li>
      </ul>
    </td>
  </tr>
</table>

---

## Architecture

FocusGram is built natively for Android using standard FOSS components:

| Layer | Implementation | Purpose |
| :--- | :--- | :--- |
| **User Interface** | Jetpack Compose (Material 3) | Declarative UI, Cupertino-style controls, OLED black styling |
| **Web Engine** | Mozilla GeckoView Omni (`MPL-2.0`) | Full web standards compliance, standards-isolated browsing sandbox |
| **Ad Blocking** | Bundled uBlock Origin WebExtension | Hardware-accelerated cosmetic and network ad filtering |
| **Local Storage** | AndroidX Room & DataStore | Relational database for screen time & DataStore preferences |
| **Security** | AndroidX Security Crypto & Biometrics | Keystore-backed AES-256 MasterKey and fingerprint authentication |
| **Concurrency** | Kotlin Coroutines & Flow | Asynchronous I/O and reactive state pipelines |

---

## Quick Start

### Installation

- **Direct APK**: Download the latest release from [GitHub Releases](https://github.com/Ujwal223/FocusGram/releases). Available in architecture splits (`arm64-v8a`, `armeabi-v7a`, `x86_64`) and Universal APK.
- **Uptodown**: Download verified builds on [FocusGram on Uptodown](https://focusgram.en.uptodown.com/android).
- **Uncloned Edition (`com.instagram.android`)**: Replace the official Instagram app directly on your device via our [Uncloned Distribution Channel](https://ujwal223.github.io/FocusGram/uncloned/).

---

### Building from Source

#### Prerequisites
- Android 8.0+ (API Level 26+)
- JDK 21 (Temurin / Adoptium recommended)
- Android SDK Command-line Tools (Build-Tools 35.0.0)

#### Build Commands

```bash
# Clone repository
git clone https://github.com/Ujwal223/FocusGram.git
cd FocusGram

# Run unit tests
./gradlew :app:testDebugUnitTest

# Build debug APK
./gradlew :app:assembleDebug

# Build standard release APKs (Universal & ABI splits)
./gradlew :app:assembleRelease

# Build uncloned edition with official package name (com.instagram.android)
./gradlew :app:assembleRelease -PuseInstaPackage=true

# Or launch the interactive build selector
./build-release.sh
```

---

## Security & Privacy Boundary

> [!IMPORTANT]
> **Why FocusGram Presents Instagram's Web Login Screen:**  
> Meta does not offer public OAuth or user-generated app passwords for third-party consumer apps. To connect to Instagram securely without risking account bans or scraping violations, FocusGram loads the official web login directly from `https://www.instagram.com/` inside an isolated Mozilla GeckoView browser session.

- **Zero Credential Interception**: The login interface is rendered inside GeckoView as a sealed black box. FocusGram's Kotlin codebase cannot inspect, intercept, or log passwords or 2FA codes.
- **Strictly Local Storage**: All settings, screen time stats, and session records stay encrypted on your phone.
- **No Third-Party SDKs**: Zero Google Firebase, zero analytics, zero crash reporting services, and zero network calls from Kotlin to external servers.

For detailed security architecture, threat model explanations, and vulnerability disclosure instructions, see **[SECURITY.md](SECURITY.md)**.  
For contribution guidelines and development standards, see **[CONTRIBUTING.md](CONTRIBUTING.md)**.

---

## Frequently Asked Questions

**Will this get my Instagram account banned?**  
No. FocusGram's web traffic is identical to accessing Instagram inside a standard Firefox or Chrome browser on Android. FocusGram does not use Instagram's private internal API, does not automate actions, and does not scrape content.

**Can I use FocusGram alongside the official Instagram app?**  
Yes! The default release uses package name `com.ujwal.focusgram` so it installs side-by-side with Instagram. If you wish to replace the official app completely so system links open in FocusGram automatically, build or download the Uncloned Edition (`com.instagram.android`).

---

## License & Support

Copyright © 2025–2026 [Ujwal Chapagain](https://github.com/Ujwal223).  
Licensed under the [GNU Affero General Public License v3.0 (AGPL-3.0)](LICENSE).

If FocusGram has helped you regain control of your attention, support continuous open-source development:

[![Buy Me a Momo](https://img.shields.io/badge/-%F0%9F%A5%9F%20Buy%20Me%20a%20Momo-FF6B35?style=for-the-badge&labelColor=1a1a1a)](https://buymemomo.com/ujwal)
