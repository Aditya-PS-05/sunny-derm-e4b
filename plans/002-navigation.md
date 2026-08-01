# 002 — Add frequency-aware navigation continuity

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Spatial consistency
- **Estimated scope**: 2 files, ~100 lines

## Problem

`SunnyNavHost.kt:109-195` uses bare destinations. Drill-in routes such as scan
detail, reports, model setup and privacy teleport, while high-frequency tabs lack
a small selected-state cue. The current bottom-bar visibility also relies on
implicit specs at `SunnyNavHost.kt:92-95`.

## Target

- Top-level Overview/Saved/Settings: no full-page movement. Use at most a 120ms
  selected icon/color/indicator transition.
- Detail push: 240ms `SunnyMotion.EaseOut`, incoming translation no more than 8%
  screen width plus opacity; outgoing content fades only.
- Pop: 180ms `SunnyMotion.EaseOut`, reverse direction.
- Reduced motion: 180ms opacity-only.
- Bottom bar: explicit 220ms entrance / 160ms exit using `DrawerEase`, translated
  by at most 20% of its own height.

## Repo conventions to follow

- Keep the existing Navigation Compose graph and `openTab` state restoration.
- The bottom bar deliberately identifies tabs without a large selection capsule (`SunnyScaffold.kt:101-128`).

## Steps

1. Add explicit `enterTransition`, `exitTransition`, `popEnterTransition`, and `popExitTransition` to the `NavHost` or detail destinations.
2. Classify top-level routes so tab changes remain motionless/opacity-only.
3. Add a 3–4dp orange selected indicator or equivalent 120ms color emphasis inside `TabItem`; do not animate the page.
4. Replace default bottom-bar specs with shared tokens.
5. Branch all positional transforms on `rememberSunnyMotionEnabled()`.

## Boundaries

- Do not change route names, back-stack behavior, deep links, or capture flow logic.
- Do not stagger page contents.
- Do not use transitions over 300ms.

## Verification

- **Mechanical**: debug compile, unit tests and lint pass.
- **Feel check**: rapidly switch tabs (nearly instant), drill into and back from Scan Detail/Privacy/Reports (direction is clear), interrupt navigation mid-transition without restart artifacts.
- **Done when**: detail routes have spatial continuity and tabs stay fast.
