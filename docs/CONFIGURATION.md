# Configuration Guide

This guide focuses on practical setup and safe operations. Keep changes small, test often, and roll out in steps.

## Configuration Layout

- Main settings: `plugins/ServerFeatures/config.yml` under `global.*`
- Feature settings: `plugins/ServerFeatures/features/<FeatureName>/config.yml`
- Framework messages: `plugins/ServerFeatures/lang/messages.yml` and optional language files
- Feature messages: `plugins/ServerFeatures/features/<FeatureName>/messages.yml` and optional language files
- Additional structured feature data: `plugins/ServerFeatures/local/*.yml`

Each feature file contains its own `enabled` toggle and settings. `config.yml` is reserved for values shared by
multiple features.

Feature reload behavior is file-scoped:

- `/serverfeatures reload <feature>` replaces that feature's runtime and preserves supported snapshot state
- `/serverfeatures softreload <feature>` refreshes only that feature's config and messages
- `/serverfeatures reloadlocal` refreshes framework messages
- `/serverfeatures reloadlocal <feature>` refreshes only that feature's messages

## Runtime Control Commands

Use these commands during operations:

- `/serverfeatures list`
- `/serverfeatures info <feature>`
- `/serverfeatures enable <feature>`
- `/serverfeatures disable <feature>`
- `/serverfeatures reload <feature>`
- `/serverfeatures softreload <feature>`
- `/serverfeatures reloadlocal`
- `/serverfeatures reloadlocal <feature>`

## Recommended Workflow

1. Enable only the features you currently need.
2. Roll out one feature (or one related group) at a time.
3. Validate logs and in-game behavior.
4. Move to the next feature only after verification.

This keeps incidents small and rollback simple.

## Environment-Specific Values

Treat production tokens, webhooks, and credentials as environment-specific values:

- keep secrets out of committed files;
- use your secret-management workflow;
- document expected variables for your team.

## Localization

Framework and feature messages are split intentionally.

- Put shared command/framework text in `lang/messages*.yml`.
- Put feature-owned text in `features/<FeatureName>/messages*.yml`.
- Use partial language files with only the entries you want to customize.
- Missing feature translations fall back to that feature's `messages.yml`, then to framework messages.

## Migration

Legacy installs are migrated during feature discovery:

- `config.yml -> features.<FeatureName>` moves to `features/<FeatureName>/config.yml`;
- feature-owned message roots move from `lang/messages*.yml` to the matching feature directory;
- `config.yml` keeps only global settings after migration.

Existing target values win over legacy values and newly injected defaults, including when old and new branch types
conflict. Missing children within an existing section are still filled. Legacy source data is removed only after the
destination write succeeds, so an interrupted migration is safe to retry. Back up the plugin directory before the
first 3.0 startup as you would for any configuration migration.

## Troubleshooting Tips

- If a feature does not enable, verify plugin dependencies and feature dependencies first.
- If a setting seems ignored, check path names and indentation.
- Apply one change at a time when diagnosing configuration behavior.
