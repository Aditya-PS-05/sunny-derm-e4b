# 007 — Stabilize Saved filtering and selection mode

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: HIGH
- **Category**: Interruptibility and visual continuity
- **Estimated scope**: 1 file, ~220 lines

## Problem

Entering selection simultaneously replaces the header (`SavedScreen.kt:165-217`),
inserts row controls (`:699-742`) and adds the action dock (`:304-345`). Rows shift
in one frame. Explicit sort/filter reorders also teleport; empty states are text-only.

## Target

- Header uses interruptible 180ms `AnimatedContent` with opacity and at most 8dp movement.
- Reserve the 36dp selection-control slot so rows never shift horizontally; check
  scales 0.92→1 with opacity over 140ms.
- Action dock enters from 20% of its own height in 220ms `DrawerEase`, exits in 160ms.
- Use lazy-list item placement animation for committed filter/sort changes only;
  search keystrokes remain immediate with no animation.
- Add code-native empty illustrations: body-map/plus for no scans, magnifier for no matches.
- Expose selected semantics on each row and announce selected count.

## Boundaries

- Do not animate during query typing.
- Do not alter filtering, sorting, report or deletion logic.

## Verification

- **Mechanical**: compile/tests/lint.
- **Feel check**: long-press and rapidly toggle selection, select all, sort/filter, type search, use TalkBack.
- **Done when**: no row jumps and selection is visually/semantically stable.
