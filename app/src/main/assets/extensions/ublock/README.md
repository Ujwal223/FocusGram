# uBlock Origin Built-In Bundle for FocusGram Native

This directory contains the bundled, unpacked WebExtension for **uBlock Origin**, designed to run inside Mozilla GeckoView via `runtime.webExtensionController.installBuiltIn("resource://android/assets/extensions/ublock/")`.

## Capabilities
1. **Network-Level Interception:**
   - Blocks tracking beacons (`graph.instagram.com/logging_client_events`, `ajax/bz`).
   - Drops ad network calls and sponsored payloads before reaching the radio socket.
2. **Cosmetic Filtering:**
   - Injects semantic CSS rules and scriptlets via `content.js` at `document_start`.
   - Immune to dynamic Instagram class obfuscation.
3. **FOSS Compliance:**
   - Distributed under **GNU General Public License v3.0 (GPL-3.0)**.
   - Author: Raymond Hill (gorhill) — https://github.com/gorhill/uBlock.
