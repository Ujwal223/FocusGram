# FocusGram

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL%203.0-blue.svg)](LICENSE)
[![Flutter](https://img.shields.io/badge/Flutter-stable-blue?logo=flutter)](https://flutter.dev)
[![GitHub Downloads](https://img.shields.io/github/downloads/Ujwal223/FocusGram/total?label=total%20installs&color=blue)](https://github.com/Ujwal223/FocusGram/releases)

**Take back your time.** FocusGram is a distraction-free client for Instagram on Android that hides Reels and Explore, so you can stay connected without getting lost in the scroll.

[🌟 Star on GitHub](https://github.com/Ujwal223/FocusGram) | [📥 Download Latest APK](https://github.com/Ujwal223/FocusGram/releases)

---

## Why FocusGram?

Most people don't want to delete Instagram entirely—they just want to stop wasting hours on Reels. FocusGram surgically removes the parts of Instagram designed for compulsive scrolling, while keeping your feed, stories, and DMs fully functional.

### Key Benefits
- **Mental Health**: Stop the dopamine loop of endless autoplay videos.
- **Productivity**: Open Instagram to check a message or post a story, and get out in seconds.
- **Privacy**: No tracking, no analytics, and no third-party SDKs. Your data stays on your device.

---

## Master Your Usage

FocusGram doesn't just block Reels—it gives you tools to build better habits:

- ✅ **Controlled Reel Sessions**: Need to watch a Reel? Start a timed session (1 to 15 minutes). When the time is up, they're blocked again.
- ✅ **Daily Limits**: Set a maximum amount of Reel time per day.
- ✅ **Habit-Building Cooldowns**: Enforce a mandatory break between sessions to prevent bingeing.

---

## Installation

### 1. From GitHub (Current)
1. Go to the [Releases](https://github.com/Ujwal223/FocusGram/releases) page.
2. Download the `focusgram-release.apk`.
3. Open the file on your phone and allow "Install from unknown sources" if prompted.

### 2. From F-Droid (Soon)
We are currently in the process of submitting FocusGram to the F-Droid store for easier updates.

---

## Frequently Asked Questions

**Is my login safe?**
Yes. FocusGram uses a standard system WebView. Your credentials go directly to Instagram/Meta's servers, just like in a mobile browser. We do not (and cannot) see your password.

**Why is it free?**
FocusGram is Open Source software created by [Ujwal Chapagain](https://github.com/Ujwal223). It is built for everyone who wants a healthier relationship with social media.

---

## Development & Technical Details

<details>
<summary>View Technical Info</summary>

### Build from Source
```bash
flutter pub get
flutter build apk --release
```

### Permissions
- `INTERNET`: To load Instagram.
- `RECEIVE_BOOT_COMPLETED`: To keep your session timers and notifications accurate after a restart.

### Tech Stack
- **Framework**: Flutter (Dart)
- **Engine**: webview_flutter
- **License**: AGPL-3.0 (Affero General Public License)
</details>

---

## License

Copyright (C) 2025  Ujwal Chapagain

This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
