# Sunny animation implementation plans

Plans are executed sequentially because every screen plan depends on the shared
motion vocabulary in plan 001. Status is updated after implementation and review.

| # | Plan | Severity | Status | Depends on |
|---|---|---|---|---|
| 001 | [Motion foundation](001-motion-foundation.md) | MEDIUM | DONE | — |
| 002 | [Navigation](002-navigation.md) | MEDIUM | DONE | 001 |
| 003 | [Modal exits](003-modal-exits.md) | MEDIUM | DONE | 001 |
| 004 | [Camera feedback](004-camera-feedback.md) | HIGH | DONE | 001 |
| 005 | [Capture and review](005-capture-review.md) | MEDIUM | DONE | 001, 003 |
| 006 | [Overview](006-overview.md) | HIGH | DONE | 001 |
| 007 | [Saved selection](007-saved-selection.md) | HIGH | DONE | 001, 003 |
| 008 | [Settings](008-settings.md) | MEDIUM | DONE | 001, 003 |
| 009 | [Compare, check and detail](009-compare-check-detail.md) | MEDIUM | DONE | 001 |
| 010 | [Reports](010-reports.md) | MEDIUM | DONE | 001, 003 |
| 011 | [Model and privacy](011-model-privacy.md) | MEDIUM | DONE | 001 |
| 012 | [Onboarding performance](012-onboarding-performance.md) | MEDIUM | DONE | 001 |
| 013 | [Lock, PIN and celebration](013-lock-pin-celebration.md) | LOW | DONE | 001, 012 |

Recommended execution order is numeric. Each implementation must pass its plan's
mechanical checks and receive an animation review before the next plan begins.
