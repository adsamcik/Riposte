---
title: "Privacy Policy"
description: "Privacy policy for the Riposte Android app"
---

**Last updated: February 21, 2026**

## Overview

Riposte ("the App") is an open-source Android application for organizing, searching, and sharing memes. The App is designed with privacy as a core principle — all processing happens entirely on your device.

## Data Collection

**Riposte does not collect, transmit, or store any personal data.** Specifically:

- ❌ No analytics or usage tracking
- ❌ No crash reporting or diagnostics sent to external servers
- ❌ No user accounts or authentication
- ❌ No advertising or ad tracking
- ❌ No data shared with third parties

## On-Device Processing

The App uses on-device machine learning for features such as:

- **Text recognition** (ML Kit) — extracts text visible in meme images
- **Image labeling** (ML Kit) — suggests emoji tags for imported memes
- **Semantic search** (MediaPipe / LiteRT) — enables natural language search across your collection

All ML processing runs entirely on your device. No images, text, or search queries are ever sent to external servers.

## Data Stored on Your Device

The App stores the following data locally on your device:

| Data | Purpose | Storage |
|------|---------|---------|
| Imported images | Your meme collection | Device storage |
| Emoji tags & metadata | Organization and search | Local SQLite database |
| Search embeddings | AI-powered semantic search | Local SQLite database |
| App preferences | Settings (theme, sharing format, etc.) | Android DataStore |

This data is accessible only to the App and is deleted when you uninstall the App or clear its data.

## Permissions

The App requests the following Android permissions:

| Permission | Reason |
|------------|--------|
| **Storage / Media access** | To import and manage meme images |
| **Internet** | Required by image loading library (Coil). No user data is transmitted. |

## Third-Party Services

The App uses the following on-device libraries. None of these transmit your data:

- **Google ML Kit** — on-device text recognition and image labeling
- **MediaPipe / LiteRT** — on-device embedding generation for semantic search
- **Coil** — image loading and caching (local files only in practice)

The App may use **Google Play In-App Review API** to prompt you for a rating. This is handled entirely by Google Play and does not transmit any app data.

## Children's Privacy

The App does not knowingly collect information from children under 13. Since no data is collected from any user, this policy applies equally to all ages.

## Changes to This Policy

If this privacy policy changes, the updated version will be posted at this URL with a new "Last updated" date. Since the App collects no data, significant changes are unlikely.

## Contact

If you have questions about this privacy policy, please open an issue on the [GitHub repository](https://github.com/adsamcik/meme-my-mood/issues).

## Open Source

Riposte is free software licensed under the [GNU GPL v3.0](https://github.com/adsamcik/meme-my-mood/blob/main/LICENSE). You can inspect the complete source code to verify these privacy claims.
