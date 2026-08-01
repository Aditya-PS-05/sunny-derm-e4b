# 004 — Stabilize camera guidance and capture feedback

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: HIGH
- **Category**: State indication, feedback, accessibility
- **Estimated scope**: 1 file, ~180 lines

## Problem

`CameraScreen.kt:188-200,284-294,484-510` can change quality guidance several
times per second, but border, icon, color and text switch in one frame. The
shutter at `CameraScreen.kt:447-456` has no pressed/capturing visual state. Focus
at `:296-305` appears without familiar reticle motion. Guidance has no live-region
semantics.

## Target

- Debounce/hysteresis: show a new quality state only after it remains stable for
  250ms; errors remain immediate.
- Guidance crossfade/color transition: 180ms `EaseOut`, no container movement.
- Focus reticle: 160ms from scale 1.15 to 1 and opacity 0.4 to 1, then fade after
  600ms; reduced motion uses opacity only.
- Shutter: press scale 0.95 over 140ms, brief 100ms white capture flash, light
  haptic feedback, and visible disabled/capturing state.
- Zoom selection: fixed 34dp layout box; selected visual scales 1.0 vs 0.88 using
  140ms transform, never animate `size`.
- Mark guidance as a polite accessibility live region.

## Steps

1. Introduce a stable guidance presentation state separate from raw preview measurements.
2. Crossfade guidance icon/message and animate border color with shared tokens.
3. Replace zoom-size changes with a fixed box and `graphicsLayer` scale.
4. Animate focus reticle and clear it after the defined interval.
5. Add shutter interaction source, press scale, capture flash and haptic.
6. Add live-region semantics without announcing every raw frame.

## Boundaries

- Never animate or blur the lesion preview itself.
- Do not change camera capture, quality thresholds, alignment math, or permissions.
- Keep all motion outside the central lesion guide where possible.

## Verification

- **Mechanical**: compile/lint; existing camera tests pass.
- **Feel check**: real device in changing light, rapid zoom taps, focus taps, successful/error capture; guidance must not flicker and shutter must feel immediate.
- **Done when**: camera feedback is stable, accessible and visibly acknowledges capture.
