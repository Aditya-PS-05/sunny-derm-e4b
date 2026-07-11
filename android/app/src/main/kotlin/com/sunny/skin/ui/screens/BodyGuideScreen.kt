package com.sunny.skin.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sunny.skin.data.model.BodyPart
import com.sunny.skin.ui.SunnyViewModel
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors

private data class Pose(val part: BodyPart, val hint: String)

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

    ScreenScaffold(title = "Full-body scan", onBack = onBack) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        "Capture consistent photos head to toe. Even framing helps Sunny " +
                            "line up your photos and spot changes over time.",
                        style = MaterialTheme.typography.bodyMedium, color = SunnyColors.TextSecondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$done of ${FULL_BODY_POSES.size} zones captured",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / FULL_BODY_POSES.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = SunnyColors.OrangeLight, trackColor = SunnyColors.SurfaceMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(FULL_BODY_POSES) { pose ->
                PoseRow(pose, done = pose.part in captured) {
                    vm.beginGuidedCapture(pose.part, pose.hint)
                    onCapturePose()
                }
            }
        }
    }
}

@Composable
private fun PoseRow(pose: Pose, done: Boolean, onCapture: () -> Unit) {
    SunnyCard(onClick = onCapture) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pose.part.label, style = MaterialTheme.typography.titleMedium)
                Text(pose.hint, style = MaterialTheme.typography.bodyMedium,
                    color = SunnyColors.TextSecondary)
            }
            Spacer(Modifier.size(12.dp))
            if (done) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = SunnyColors.Success,
                        modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Retake", style = MaterialTheme.typography.bodyMedium,
                        color = SunnyColors.TextSecondary)
                }
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(SunnyColors.Orange),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CameraAlt, "Capture", tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
