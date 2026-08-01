package com.sunny.skin.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.BodyProgressFigure
import com.sunny.skin.ui.components.BodyZoneThumb
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled

private data class Pose(val part: BodyPart, val hint: String)
private data class GuideCompletion(val id: String, val capturedAt: Long, val part: BodyPart)

private const val RECENT_GUIDE_COMPLETION_MS = 10 * 60 * 1000L
private var lastAcknowledgedGuideCompletionId: String? = null

/**
 * A curated head-to-toe sequence. Consistent framing per zone is what makes the
 * before/after alignment (and therefore change detection) reliable — so each pose
 * carries a short framing tip that is also shown over the camera.
 */
private val FULL_BODY_POSES = listOf(
    Pose(BodyPart.FACE, "Face the camera in even light and fill the frame with your face."),
    Pose(BodyPart.NECK, "Tilt your chin up slightly so your neck is clearly visible."),
    Pose(BodyPart.CHEST, "Frame your upper chest square-on, arms relaxed at your sides."),
    Pose(BodyPart.ABDOMEN, "Stand straight and centre your stomach in the frame."),
    Pose(BodyPart.UPPER_BACK, "Use a mirror or ask someone to help; keep the camera level."),
    Pose(BodyPart.LOWER_BACK, "Same angle as your upper back so the two line up over time."),
    Pose(BodyPart.LEFT_ARM, "Extend your left arm; capture it from shoulder to wrist."),
    Pose(BodyPart.RIGHT_ARM, "Extend your right arm; capture it from shoulder to wrist."),
    Pose(BodyPart.LEFT_LEG, "Capture your left leg straight-on, thigh to ankle."),
    Pose(BodyPart.RIGHT_LEG, "Capture your right leg straight-on, thigh to ankle."),
)

@Composable
fun BodyGuideScreen(
    vm: SunnyViewModel,
    onBack: () -> Unit,
    onCapturePose: () -> Unit,
) {
    val scans by vm.scans.collectAsStateWithLifecycle()
    val captured = scans.map { it.scan.bodyPart }.toSet()
    val done = FULL_BODY_POSES.count { it.part in captured }
    val guidedParts = FULL_BODY_POSES.map { it.part }.toSet()
    val latestGuidedCompletion = scans.asSequence()
        .filter { it.scan.bodyPart in guidedParts && it.observations.size == 1 }
        .mapNotNull { scan ->
            scan.latest?.let { observation ->
                GuideCompletion(observation.id, observation.capturedAt, scan.scan.bodyPart)
            }
        }
        .maxByOrNull { it.capturedAt }
    val motionEnabled = rememberSunnyMotionEnabled()
    val progress by animateFloatAsState(
        targetValue = done.toFloat() / FULL_BODY_POSES.size,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseInOut)
        } else {
            snap()
        },
        label = "Guided capture progress",
    )
    var newlyCompleted by remember { mutableStateOf<BodyPart?>(null) }
    val completionPulse = remember { Animatable(0f) }

    LaunchedEffect(latestGuidedCompletion?.id) {
        val completion = latestGuidedCompletion ?: return@LaunchedEffect
        val isRecent = System.currentTimeMillis() - completion.capturedAt <= RECENT_GUIDE_COMPLETION_MS
        if (isRecent && completion.id != lastAcknowledgedGuideCompletionId) {
            lastAcknowledgedGuideCompletionId = completion.id
            newlyCompleted = completion.part
            completionPulse.snapTo(1f)
            completionPulse.animateTo(
                0f,
                animationSpec = tween(220, easing = SunnyMotion.EaseOut),
            )
            newlyCompleted = null
        }
    }

    ScreenScaffold(title = "Full-body photo check", onBack = onBack) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            SunnyCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactBodyMap(
                        highlighted = captured.map { it.zone }.toSet(),
                        modifier = Modifier.size(width = 56.dp, height = 92.dp),
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "$done of ${FULL_BODY_POSES.size} areas photographed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Keep framing and lighting consistent for useful comparisons.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SunnyColors.TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = SunnyColors.OrangeLight,
                            trackColor = SunnyColors.SurfaceMuted,
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Capture consistent photos head to toe. Even framing helps Sunny " +
                            "line up your photos and spot changes over time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                items(FULL_BODY_POSES, key = { it.part }) { pose ->
                    PoseRow(
                        pose = pose,
                        done = pose.part in captured,
                        completionPulse = if (newlyCompleted == pose.part) completionPulse.value else 0f,
                        motionEnabled = motionEnabled,
                    ) {
                        vm.beginGuidedCapture(pose.part, pose.hint)
                        onCapturePose()
                    }
                }
            }
        }
    }
}

@Composable
private fun PoseRow(
    pose: Pose,
    done: Boolean,
    completionPulse: Float,
    motionEnabled: Boolean,
    onCapture: () -> Unit,
) {
    SunnyCard(onClick = onCapture) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Custom asset: a zoomed crop of the body figure around this pose's
            // zone (the amber area + some body around it), not the whole figure.
            BodyZoneThumb(pose.part.side, pose.part.zone, Modifier.size(46.dp))
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pose.part.label, style = MaterialTheme.typography.titleMedium)
                Text(pose.hint, style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary)
            }
            Spacer(Modifier.size(10.dp))
            if (done) {
                val scale = if (motionEnabled) 1f + completionPulse * 0.06f else 1f
                val alpha = if (!motionEnabled && completionPulse > 0f) {
                    0.68f + (1f - completionPulse) * 0.32f
                } else {
                    1f
                }
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Captured",
                    tint = if (motionEnabled) {
                        lerp(SunnyColors.Success, SunnyColors.Orange, completionPulse)
                    } else {
                        SunnyColors.Success
                    },
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Capture",
                    tint = SunnyColors.TextTertiary)
            }
        }
    }
}

/** Compact, non-diagnostic progress map used only to orient the guided sequence. */
@Composable
private fun CompactBodyMap(highlighted: Set<com.sunny.skin.data.model.BodyZone>, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(SunnyColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        BodyProgressFigure(
            highlighted = highlighted,
            modifier = Modifier.size(width = 38.dp, height = 78.dp),
        )
    }
}
