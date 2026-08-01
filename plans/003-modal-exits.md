# 003 — Make glass dialogs enter and exit physically

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Interruptibility and physicality
- **Estimated scope**: 3 files, ~120 lines

## Problem

`GlassModal.kt:57-68` sets `shown=true` once and only declares entry. Reminder
dialogs at `ReminderControls.kt:143-149` declare exit, but callers remove the
Dialog immediately. Dismissal therefore snaps.

## Target

Centered modals use correct center origin: enter from scale `0.96f` + opacity in
220ms `EaseOut`; exit to scale `0.98f` + opacity in 160ms `EaseOut`. Scrim fades
over the same interval. Reduced motion keeps opacity but fixes scale at 1f.

## Steps

1. Refactor `LiquidGlassDialog` to own an internal visible state and call the external dismiss callback only after its 160ms exit completes.
2. Apply the same lifecycle to reminder dialogs in `ReminderControls.kt`.
3. Ensure back press, outside tap, cancel, save and destructive actions all request exit exactly once.
4. Keep content interactive during entry and disable repeated dismissal while exiting.

## Boundaries

- Do not change dialog content, validation, blur radius, security behavior, or business callbacks.
- No bounce; no scale below 0.96.

## Verification

- **Mechanical**: compile, unit tests and lint pass.
- **Feel check**: repeatedly open/dismiss backup, size, ABCDE, reminder and credential dialogs; no snap, duplicate callback, or stuck scrim.
- **Done when**: every glass dialog completes an interruptible exit.
