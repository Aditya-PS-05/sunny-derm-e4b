package com.sunny.skin.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sunny.skin.report.ReportStore
import com.sunny.skin.ui.components.CircleButton
import com.sunny.skin.ui.components.ScreenScaffold
import com.sunny.skin.ui.theme.SunnyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ReportDetailScreen(reportId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ReportStore(context) }

    var pages by remember(reportId) { mutableStateOf(emptyList<Bitmap>()) }
    LaunchedEffect(reportId) {
        pages = withContext(Dispatchers.IO) {
            store.withDecryptedReport(reportId) { renderPdf(it) } ?: emptyList()
        }
    }

    ScreenScaffold(
        title = reportId,
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
                CircleButton(onClick = { store.delete(reportId); onBack() }) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete", tint = SunnyColors.Danger,
                        modifier = Modifier.size(18.dp))
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            items(pages) { page ->
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White),
                )
            }
        }
    }
}

/** Rasterise every page of the decrypted PDF to a bitmap for in-app display. */
private fun renderPdf(file: File?): List<Bitmap> {
    if (file == null || !file.exists()) return emptyList()
    val pages = mutableListOf<Bitmap>()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
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
    }
    return pages
}
