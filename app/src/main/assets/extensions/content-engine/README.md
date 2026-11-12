# FocusGram Content Engine Built-In Bundle

This directory contains the built-in WebExtension for the **FocusGram Content Engine**, designed to run inside Mozilla GeckoView via `runtime.webExtensionController.installBuiltIn("resource://android/assets/extensions/content-engine/")`.

## Capabilities
1. **Network-Level Interception:**
   - Blocks tracking beacons (`graph.instagram.com/logging_client_events`, `ajax/bz`).
   - Drops ad network calls and sponsored payloads before reaching the radio socket.
2. **Cosmetic Filtering:**
   - Injects semantic CSS rules and scriptlets via `content.js` and `focusgram_content.js` at `document_start`.
   - Immune to dynamic Instagram class obfuscation.
3. **Ghost Mode (Stealth Messaging):**
   - Bundles `ghost.js` (Ghostify) for unseen stories/messages and typing indicator suppression.
4. **FOSS Compliance:**
   - Distributed under **GNU General Public License v3.0 (GPL-3.0)**.
