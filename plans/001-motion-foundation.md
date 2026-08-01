# 001 — Establish Sunny's motion foundation

- **Status**: DONE
- **Commit**: 409b801
- **Severity**: MEDIUM
- **Category**: Cohesion, timing, accessibility
- **Estimated scope**: 2 files, ~140 lines

## Problem

Motion is specified independently across the app. `Common.kt:202-205` uses default
`animate*AsState` specs, `SunnyNavHost.kt:92-95` uses default entrance specs, and
`GlassModal.kt:61-66` uses unrelated springs. There is no reusable duration,
easing, reduced-motion, or press-feedback vocabulary.

```kotlin
// Common.kt:202 — current
val thumbX by animateDpAsState(if (checked) trackW - thumb - pad else pad)
```

## Target

Create `ui/theme/Motion.kt` with these exact Compose values:

```kotlin
object SunnyMotion {
    const val PressMillis = 140
    const val StateMillis = 180
    const val ScreenEnterMillis = 240
    const val ScreenExitMillis = 180
    const val ModalEnterMillis = 220
    const val ModalExitMillis = 160
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    val DrawerEase = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
}
```

Add `rememberSunnyMotionEnabled()` backed by
`ValueAnimator.areAnimatorsEnabled()`. Reduced motion keeps 180ms opacity/color
feedback but removes translation, rotation, scale, confetti, and looping motion.
Update `SunnyToggle` so thumb translation uses `graphicsLayer.translationX`
instead of layout `offset`, and give thumb and track the same 180ms `EaseInOut`
spec. Add reusable pressed scale `0.97f` with 140ms `EaseOut` for Sunny cards,
chips, buttons and camera controls.

## Repo conventions to follow

- Motion remains dependency-free Jetpack Compose code.
- `OnboardingButton` in `OnboardingScreen.kt:435-456` is the existing press-scale exemplar.
- Visual colors continue to come from `SunnyColors`; do not add motion colors.

## Steps

1. Add `android/app/src/main/kotlin/com/sunny/skin/ui/theme/Motion.kt` with the exact tokens above.
2. Add a reusable press-scale state helper using `MutableInteractionSource`, `collectIsPressedAsState`, `animateFloatAsState`, and `graphicsLayer`.
3. Update `SunnyToggle` in `Common.kt` to use explicit 180ms specs and composited translation.
4. Update `SunnyChip` and clickable `SunnyCard` to use the shared 0.97 press response while retaining ripple/semantics.
5. Add unit-testable pure helpers only if necessary; do not add dependencies.

## Boundaries

- Do not animate non-clickable cards.
- Do not change colors, spacing, typography, navigation, or business logic.
- Do not animate width, height, padding, or layout offsets.
- If cited code has drifted, stop and report rather than improvising.

## Verification

- **Mechanical**: `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug` succeeds.
- **Feel check**: spam toggles/chips; each animation retargets smoothly, press scale is subtle, and no row shifts.
- **Reduced motion**: disable system animations; translation/scale disappear while color/ripple feedback remains.
- **Done when**: shared tokens exist and common controls no longer rely on implicit animation specs or layout-position animation.
