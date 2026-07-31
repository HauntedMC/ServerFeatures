# PlayerLanguage

> Paper · Feature ID `playerlanguage` · disabled by default

## Overview

Loads and exposes a player's preferred language so backend feature messages can follow the same network preference.

## Commands and permissions

No backend command is registered; preference changes are normally handled by ProxyFeatures.

## Configuration

Configure default language, supported canonical locale codes, cache/persistence behavior, and fallback order in `features/PlayerLanguage/`.

## Integrations and placeholders

Coordinates with ProxyFeatures PlayerLanguage and DataRegistry/DataProvider. No feature-specific PlaceholderAPI expansion is registered.

## Runtime and developer notes

Hot-path localization reads use cached state; persistence and network synchronization stay asynchronous. Unknown, blank, or unsupported values fall back predictably. Normalize case and separators identically on proxy and backend and generation-fence late loads after logout/disable.

Entry point: `serverfeatures-platform-paper/src/main/java/nl/hauntedmc/serverfeatures/features/playerlanguage/PlayerLanguage.java`.

## Troubleshooting

Different canonicalization (`en_US` versus `en-us`) can produce apparent preference loss across components.
