# 005 — Clarify capture, body-guide, review and edit states

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Missed opportunities and state continuity
- **Estimated scope**: 4 files, ~220 lines

## Problem

Capture choices are visually generic (`CaptureScreen.kt:68-125`), body-guide
progress and checks snap (`BodyGuideScreen.kt:63-127`), analysis states are a
direct `when` replacement (`ReviewScanScreen.kt:200-220`), and replacing/editing
a photo has no continuity (`EditScanScreen.kt:101-112,203-246`).

## Target

- Capture cards gain code-native camera/library/body-map illustrations and shared press feedback.
- Body guide adds a compact persistent body map; newly completed zone gets a one-shot 220ms color/scale acknowledgement; progress interpolates 180ms.
- Review uses a compact Prepare → Quality → Analyse → Ready status line. State body transitions with 180ms opacity + at most 8dp vertical movement; reduced motion opacity-only.
- Edit crossfades old/new photo in 180ms, exposes a static “Description needs refresh” badge, and briefly highlights the refreshed analysis.

## Boundaries

- Do not add diagnostic, risk, severity or disease graphics.
- Do not animate analysis text row-by-row for more than 240ms total.
- Do not change capture or analysis logic.

## Verification

- **Mechanical**: compile, tests, lint.
- **Feel check**: capture from camera/library, complete a body zone, exercise every review state, replace and reanalyse a photo.
- **Done when**: each workflow state is visually understandable without decorative delay.
