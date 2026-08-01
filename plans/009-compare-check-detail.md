# 009 — Clarify comparison, checklist and scan-history changes

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Direct manipulation and state indication
- **Estimated scope**: 3 files, ~260 lines

## Problem

Compare applies the computed transform in one frame (`CompareScreen.kt:139-162`)
and swaps modes directly (`:168-245`). Photo Check progress/row status jumps
(`CheckSessionScreen.kt:62-97,217-258`). Scan Detail history is visually flat and
save operations rely on minimal feedback.

## Target

- Compare selected mode indicator moves over 180ms `EaseInOut`; surfaces crossfade
  150ms. Alignment settles translation/scale/rotation over 220ms `EaseInOut` only
  when computation completes. User drag/wipe/fade remains 1:1 and unanimated.
- Date-tag emphasis varies continuously with fade instead of switching at 0.5.
- Add neutral Fade/Wipe/Blink/Side pictograms and a before→after date rail.
- Photo Check interpolates progress 180ms and morphs only the changed row’s icon/color/check over 180ms; completion gets a restrained one-shot check.
- Scan Detail adds a thumbnail timeline connector and a brief new-history/save confirmation.

## Boundaries

- Never animate gesture-driven values behind the finger.
- Never add growth, stability, urgency or risk graphics.
- Keep lesion photos sharp and undistorted after alignment settles.

## Verification

- **Mechanical**: compile/tests/lint.
- **Feel check**: switch compare modes, interrupt auto-alignment, drag/wipe/fade, complete/skip checklist rows, add history/note/reminder.
- **Done when**: system-computed changes settle smoothly and direct manipulation remains immediate.
