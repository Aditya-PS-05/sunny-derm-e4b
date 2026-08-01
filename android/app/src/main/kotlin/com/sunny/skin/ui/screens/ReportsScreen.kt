package com.sunny.skin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sunny.skin.report.ReportStore
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.components.SunnyCard
import com.sunny.skin.ui.theme.SunnyColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onReportClick: (String) -> Unit,
    onGenerateReport: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ReportStore(context) }
    var reports by remember { mutableStateOf(store.list()) }
    val fmt = remember { SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reports = store.list() }

    ScreenScaffold(
        title = "Reports",
        onBack = onBack,
        trailing = {
            TextButton(onClick = onGenerateReport) {
                Text("Create", color = SunnyColors.OrangeText, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { inner ->
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                EmptyReportsState(onGenerateReport)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reports, key = { it.name }) { file ->
                    val id = file.nameWithoutExtension
                    SunnyCard(onClick = { onReportClick(id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ReportPageStack()
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Skin tracking report", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(fmt.format(Date(file.lastModified())),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SunnyColors.TextSecondary)
                                Text(id, style = MaterialTheme.typography.labelSmall,
                                    color = SunnyColors.TextTertiary)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                                tint = SunnyColors.TextTertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReportsState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 136.dp, height = 116.dp)) {
            Box(
                Modifier.align(Alignment.Center)
                    .offset(x = 15.dp, y = 7.dp)
                    .size(width = 78.dp, height = 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SunnyColors.SurfaceMuted),
            )
            Column(
                Modifier.align(Alignment.Center)
                    .offset(x = (-7).dp, y = (-3).dp)
                    .size(width = 82.dp, height = 100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SunnyColors.Surface)
                    .border(1.dp, SunnyColors.Divider, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Box(
                    Modifier.size(24.dp).clip(RoundedCornerShape(8.dp))
                        .background(SunnyColors.OrangeSoft),
                )
                Spacer(Modifier.height(13.dp))
                DocumentLine(1f)
                Spacer(Modifier.height(7.dp))
                DocumentLine(0.76f)
                Spacer(Modifier.height(7.dp))
                DocumentLine(0.88f)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "No reports yet",
            color = SunnyColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Reports you save will appear here.",
            color = SunnyColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SunnyColors.Action),
        ) {
            Text("Create report", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ReportPageStack() {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.offset(x = 4.dp, y = 2.dp)
                .size(width = 32.dp, height = 38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SunnyColors.SurfaceMuted),
        )
        Column(
            Modifier.offset(x = (-3).dp, y = (-2).dp)
                .size(width = 32.dp, height = 38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SunnyColors.OrangeSoft)
                .border(1.dp, SunnyColors.OrangeLight, RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 8.dp),
        ) {
            DocumentLine(1f, SunnyColors.Orange)
            Spacer(Modifier.height(5.dp))
            DocumentLine(0.72f, SunnyColors.OrangeLight)
            Spacer(Modifier.height(5.dp))
            DocumentLine(0.88f, SunnyColors.OrangeLight)
        }
    }
}

@Composable
private fun DocumentLine(fraction: Float, color: androidx.compose.ui.graphics.Color = SunnyColors.Divider) {
    Box(
        Modifier.fillMaxWidth(fraction).height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
