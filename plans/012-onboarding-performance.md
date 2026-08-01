# 012 — Polish onboarding motion without adding spectacle

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Performance and reduced motion
- **Estimated scope**: 2 files, ~160 lines

## Problem

Pager progress drives layout `offset` in `OnboardingMotionScene.kt:122-156` and
indicator width in `OnboardingScreen.kt:420-429` every frame. The infinite scan
transition is still created when motion is disabled (`OnboardingMotionScene.kt:190-198`).
Reduced motion uses zero-duration swaps (`OnboardingScreen.kt:89-100,257-260`).

## Target

- Replace Dp offsets with `graphicsLayer.translationX/Y`; keep scale/rotation/opacity composited.
- Page indicator uses fixed-width containers and scaleX/alpha, never animated width.
- Do not instantiate/observe the infinite scan transition when motion is disabled.
- Reduced motion removes slide/rotation/scan but retains a 180ms opacity transition.
- Add only three purposeful cues: 100ms capture flash, timeline connector draw tied
  directly to pager progress, and final vault-lock check. No new loops.

## Boundaries

- Do not change onboarding copy, page count, acknowledgement or navigation.
- Keep total tap-driven UI transitions below 300ms; shorten the current 360ms entrance to 240ms.

## Verification

- **Mechanical**: compile/tests/lint.
- **Feel check**: drag pager slowly and quickly on a real device; inspect frame stability; test system animations off and large text.
- **Done when**: pager motion is GPU-composited and reduced motion retains continuity.
