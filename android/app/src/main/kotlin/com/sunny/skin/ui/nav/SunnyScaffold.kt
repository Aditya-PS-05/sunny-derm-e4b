package com.sunny.skin.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.theme.SunnyColors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.OVERVIEW, "Overview", Icons.Filled.Accessibility),
    Tab(Routes.SAVED, "Saved", Icons.Filled.PhotoLibrary),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/**
 * The floating bottom bar from the design: three tabs (Overview / Saved /
 * Settings) in a pill, with a separate circular "+" capture button to its right.
 */
@Composable
fun SunnyBottomBar(
    currentRoute: String?,
    onSelectTab: (String) -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Minimal flat pill — no shadow, compact height.
        Surface(
            modifier = Modifier.weight(1f).height(58.dp),
            shape = RoundedCornerShape(29.dp),
            color = SunnyColors.Surface,
        ) {
            Row(
                Modifier.fillMaxHeight().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        onClick = { onSelectTab(tab.route) },
                    )
                }
            }
        }
        // The "+" capture button — always reachable (F-01 entry point).
        Surface(
            modifier = Modifier.size(58.dp).clip(CircleShape).clickable(onClick = onCapture),
            shape = CircleShape,
            color = SunnyColors.Surface,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = "New scan",
                    tint = SunnyColors.TextPrimary, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    // Reference design: selected tab sits in a soft grey capsule with orange
    // icon + label; unselected tabs are dark on the white pill.
    val tint = if (selected) SunnyColors.Orange else SunnyColors.TextPrimary
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) SunnyColors.SurfaceMuted else Color.Transparent,
                RoundedCornerShape(22.dp),
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}
