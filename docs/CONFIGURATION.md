# Configuration Guide

This guide focuses on practical setup and safe operations. Keep changes small, test often, and roll out in steps.

## Configuration Layout

- Main settings: `plugins/ServerFeatures/config.yml`
- Feature-local files: `plugins/ServerFeatures/local/*.yml`
- Localization files: `plugins/ServerFeatures/lang/*.yml`

`config.yml` is generated at runtime and primarily controls feature enablement and feature-level settings.

Most features follow the same pattern:

- `enabled` toggle
- feature-specific settings under that feature section

## Runtime Control Commands

Use these commands during operations:

- `/serverfeatures list`
- `/serverfeatures info <feature>`
- `/serverfeatures enable <feature>`
- `/serverfeatures disable <feature>`
- `/serverfeatures reload <feature>`
- `/serverfeatures softreload <feature>`
- `/serverfeatures reloadlocal`

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

You can override messages without copying all language keys.

Use partial language files with only the entries you want to customize. Missing keys fall back to defaults.

## Troubleshooting Tips

- If a feature does not enable, verify plugin dependencies and feature dependencies first.
- If a setting seems ignored, check path names and indentation.
- Apply one change at a time when diagnosing configuration behavior.
