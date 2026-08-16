# Browser support

Flud Companion Web is designed for modern browsers on iPhone, Android, tablet and desktop.

## Beta priority

- Chrome on iPhone: primary day-to-day beta browser.
- Safari on iPhone/iPad: compatibility smoke test.
- Chrome on Android: compatibility smoke test.
- Current desktop Chromium/Safari-class browsers: functional compatibility target.

## Installation

The Remote controller is a PWA and registers a service worker. On supported browsers it can be added to the Home Screen / installed for app-like launching.

The local LAN controller is served directly by the Android Bridge over HTTP. Browser security rules can restrict clipboard APIs on local HTTP pages; manual long-press Paste remains the fallback.

## Storage

Pairing, language preference, recent-send history, Auto-start preference and the optional support-reminder counter use browser-local storage. Clearing site data removes those local settings.
