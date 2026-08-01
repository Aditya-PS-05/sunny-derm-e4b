# 006 — Animate meaningful Overview changes

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: HIGH
- **Category**: Physicality, accessibility, earned delight
- **Estimated scope**: 4 files, ~220 lines

## Problem

Front/back selection at `OverviewScreen.kt:95-125` swaps the body bitmap in
`BodyTemplate.kt:38-40` instantly. Stats, coverage and streak changes also jump.
The first landing instead spends 4.2 seconds on full-screen balloons
(`OverviewScreen.kt:67-68,197-200`, `Balloons.kt:49-88`) before the user earns a
health-tracking achievement. The selector and body canvas lack complete semantics.

## Target

- Body turn: 220ms `EaseInOut`, crossfade plus `rotationY` no more than 12 degrees;
  reduced motion is a 180ms crossfade.
- A newly scanned zone receives one 220ms orange illumination; never loop.
- Coverage and changed stat values interpolate over 180ms; weekly bars reveal with
  transform scaleY from 0.85 to 1, not animated height.
- Replace first-open balloons with a compact 1.2s mascot/sun fade. Reserve restrained
  confetti for an earned first saved scan/checklist completion. Static equivalent
  when motion is disabled.
- Front/back uses selectable-group/tab semantics; body canvas describes highlighted zones.

## Boundaries

- Do not imply clinical success, safety or improvement.
- Do not animate every Overview visit or every scroll.
- Keep top-level page itself static.

## Verification

- **Mechanical**: compile, tests, lint.
- **Feel check**: flip body rapidly, save first/new scans, revisit Overview, enable TalkBack and reduced motion.
- **Done when**: the body reads as one turning object and only meaningful data changes animate.
