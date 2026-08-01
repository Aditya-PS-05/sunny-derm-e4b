# 008 — Add calm security and operation feedback to Settings

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: State indication and visual graphics
- **Estimated scope**: 1 file, ~190 lines

## Problem

Dependent rows such as Change PIN (`SettingsScreen.kt:121-133`) and contribution
status (`:261-279`) snap into layout. Export only adds a small spinner
(`:435-462`) and full deletion ends in a toast (`:488-499`). Dense security
settings have no visual summary.

## Target

- Add a compact static device-vault status graphic summarizing PIN, encrypted local storage and selected analysis source; never show risk scores.
- Dependent rows reveal with 180ms opacity plus `animateContentSize` only where the
  content change is infrequent; use opacity-only for reduced motion.
- Export becomes a clear preparing→share state with determinate/indeterminate visual and disabled duplicate action.
- Successful delete shows an in-app calm check/vault-empty confirmation for 1.2s rather than only a toast.

## Boundaries

- Do not expose tokens, secrets or extra health data.
- Do not animate password typing or destructive confirmation countdowns.
- Preserve all security behavior and text.

## Verification

- **Mechanical**: compile/tests/lint.
- **Feel check**: toggle PIN/server/contribution, export success/failure, delete all, reduced motion.
- **Done when**: security state and operation completion are explicit without playful spectacle.
