# 011 — Visualize model and privacy architecture

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Explanatory graphics
- **Estimated scope**: 2 files, ~180 lines

## Problem

Model states replace one another immediately (`ModelSetupScreen.kt:83-188`), and
Privacy is a dense text stack (`PrivacyScreen.kt:22-130`). These rare screens can
explain system behavior visually without burdening frequent workflows.

## Target

- Model Setup: Download → Verify → Ready pipeline with current step highlighted;
  progress interpolates 180ms linear/`EaseOut`, success check appears once, failure
  uses a distinct neutral retry graphic. No celebratory bounce.
- Privacy: static device-vault/data-flow diagram showing photo → encrypted local
  storage and the separately disclosed HTTPS beta path. Add small section icons.
  Keep the diagram static; at most fade it in once over 180ms.

## Boundaries

- Do not overstate hardware backing, server retention, anonymity or model readiness.
- Do not add looping motion to Privacy.
- Preserve all disclosure text.

## Verification

- **Mechanical**: compile/tests/lint.
- **Feel check**: model not configured/downloading/verifying/ready/failed/server states; privacy in device/server modes and large text.
- **Done when**: the pipeline and data boundaries are understandable without reading every paragraph first.
