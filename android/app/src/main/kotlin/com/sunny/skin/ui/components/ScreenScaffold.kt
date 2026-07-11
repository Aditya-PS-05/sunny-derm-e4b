package com.sunny.skin.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors

/**
 * Standard detail-screen frame: a circular back button, centred title, and an
 * optional trailing action, over the cream canvas. Content receives the top
 * padding so it can lay out below the header.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back",
                    tint = SunnyColors.TextPrimary, modifier = Modifier.size(26.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Box(Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp),
                contentAlignment = Alignment.Center) { trailing?.invoke() }
        }
        Box(Modifier.fillMaxSize()) { content(PaddingValues(top = 4.dp)) }
    }
}

@Composable
fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape, color = SunnyColors.Surface, shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
