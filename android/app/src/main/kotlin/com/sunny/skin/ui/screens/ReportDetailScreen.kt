package com.sunny.skin.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.sunny.skin.ui.i18n.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.report.ReportStore
import com.sunny.skin.ui.components.CircleButton
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.rememberSunnyMotionEnabled
import com.sunny.skin.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ReportLoadState {
    data object Loading : ReportLoadState
    data object Empty : ReportLoadState
    data object Error : ReportLoadState
    data class Ready(val pages: List<Bitmap>) : ReportLoadState
}

@Composable
fun ReportDetailScreen(reportId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ReportStore(context) }
    val reportDate = remember(reportId) {
        store.file(reportId).takeIf { it.exists() }?.lastModified()?.let(Format::date)
    }
    var showDeleteConfirmation by remember(reportId) { mutableStateOf(false) }

    var loadState by remember(reportId) { mutableStateOf<ReportLoadState>(ReportLoadState.Loading) }
    LaunchedEffect(reportId) {
        loadState = ReportLoadState.Loading
        loadState = withContext(Dispatchers.IO) {
            try {
                val pages = store.openDecryptedReport(reportId)?.use { renderPdf(it) }
                    ?: return@withContext ReportLoadState.Error
                if (pages.isEmpty()) ReportLoadState.Empty else ReportLoadState.Ready(pages)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReportLoadState.Error
            }
        }
    }

    ScreenScaffold(
        title = reportDate?.let { "Report · $it" } ?: "Report",
        onBack = onBack,
        trailing = {
            androidx.compose.foundation.layout.Row {
                CircleButton(onClick = {
                    store.shareUri(reportId)?.let { uri ->
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share report"))
                    }
                }) {
                    Icon(Icons.Filled.Share, "Share", tint = SunnyColors.TextPrimary,
                        modifier = Modifier.size(18.dp))
                }
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                CircleButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete", tint = SunnyColors.Danger,
                        modifier = Modifier.size(18.dp))
                }
            }
        },
    ) { inner ->
        when (val state = loadState) {
            ReportLoadState.Loading -> ReportDocumentLoading(Modifier.fillMaxSize().padding(inner))
            ReportLoadState.Empty -> ReportDocumentMessage(
                title = "This report has no pages",
                message = "The saved file could not be displayed.",
                modifier = Modifier.fillMaxSize().padding(inner),
            )
            ReportLoadState.Error -> ReportDocumentMessage(
                title = "Report unavailable",
                message = "The saved report could not be opened.",
                modifier = Modifier.fillMaxSize().padding(inner),
            )
            is ReportLoadState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text(
                        if (state.pages.size == 1) "1 page" else "${state.pages.size} pages",
                        style = MaterialTheme.typography.labelLarge,
                        color = SunnyColors.TextSecondary,
                    )
                }
                items(state.pages) { page ->
                    ReportPage(page)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = SunnyColors.Surface,
            title = { Text("Delete this report?") },
            text = {
                Text(
                    "The encrypted report will be permanently removed from this phone. " +
                        "This cannot be undone.",
                    color = SunnyColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    store.delete(reportId)
                    onBack()
                }) {
                    Text("Delete report", color = SunnyColors.Danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = SunnyColors.TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ReportDocumentLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.width(156.dp).height(202.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SunnyColors.Surface)
                .border(1.dp, SunnyColors.Divider, RoundedCornerShape(12.dp))
                .padding(20.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                    .background(SunnyColors.OrangeSoft),
            )
            Spacer(Modifier.height(22.dp))
            LoadingLine(1f)
            Spacer(Modifier.height(10.dp))
            LoadingLine(0.72f)
            Spacer(Modifier.height(10.dp))
            LoadingLine(0.88f)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SunnyColors.SurfaceMuted),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Opening report…",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SunnyColors.TextPrimary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Decrypting and preparing pages on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
    }
}

@Composable
private fun LoadingLine(fraction: Float) {
    Box(
        Modifier.fillMaxWidth(fraction).height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(SunnyColors.SurfaceMuted),
    )
}

@Composable
private fun ReportDocumentMessage(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(20.dp))
                .background(SunnyColors.OrangeSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = SunnyColors.Orange,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = SunnyColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = SunnyColors.TextSecondary,
        )
    }
}

@Composable
private fun ReportPage(page: Bitmap) {
    val motionEnabled = rememberSunnyMotionEnabled()
    var visible by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (motionEnabled) {
            tween(SunnyMotion.StateMillis, easing = SunnyMotion.EaseOut)
        } else {
            snap()
        },
        label = "Report page reveal",
    )
    Image(
        bitmap = page.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color.White),
    )
}

/** Rasterise every page of the decrypted PDF to a bitmap for in-app display. */
private fun renderPdf(pfd: ParcelFileDescriptor): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    PdfRenderer(pfd).use { renderer ->
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                val scale = 2
                val bmp = Bitmap.createBitmap(
                    page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888,
                )
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pages.add(bmp)
            }
        }
    }
    return pages
}
