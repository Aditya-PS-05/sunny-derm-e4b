# 013 — Refine lock, PIN and earned celebration feedback

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: LOW
- **Category**: Feedback and frequency
- **Estimated scope**: 4 files, ~190 lines

## Problem

Lock→PIN is a direct branch swap in `MainActivity.kt:61-76`. PIN create/confirm,
errors and success change instantly (`PinScreen.kt:65-101,140-159`). The existing
full-screen balloon drop (`Balloons.kt:49-113`) is long and has no explicit static
alternative.

## Target

- Lock→PIN: 220ms `EaseOut`, lock content fades and moves no more than 8dp; reduced motion fade-only.
- PIN dots fill in 120ms opacity/color. Wrong PIN: one 220ms, maximum 6dp horizontal
  shake plus red color; reduced motion red flash only. Success: dots morph to a
  check/fade for 180ms before unlock. Lockout displays a static countdown/progress
  ring; do not animate every second beyond text/progress update.
- Keypad itself does not enter/stagger and retains immediate ripple feedback.
- Balloon/celebration helper accepts motion-enabled and compact modes. Default
  earned celebration lasts at most 1.5s; reduced motion renders a still/fade.

## Boundaries

- Do not delay PIN verification, weaken lockout, log input or expose PIN digits.
- No looping lock, shield, mascot or keypad animations.

## Verification

- **Mechanical**: compile/tests/lint and PIN Android tests compile.
- **Feel check**: unlock success, wrong PIN, lockout, create/confirm mismatch, cancel, system animations off.
- **Done when**: security feedback is concise, accessible and never delays authentication.
