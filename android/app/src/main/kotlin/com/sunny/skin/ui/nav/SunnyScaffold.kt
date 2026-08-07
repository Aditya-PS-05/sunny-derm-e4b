package com.sunny.skin.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.sunny.skin.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunny.skin.ui.i18n.stringResource
import com.sunny.skin.R
import com.sunny.skin.ui.theme.SunnyColors
import com.sunny.skin.ui.theme.SunnyMotion
import com.sunny.skin.ui.theme.sunnyPressScale

private data class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.OVERVIEW, R.string.nav_overview, Icons.Filled.Home),
    Tab(Routes.SAVED, R.string.nav_areas, Icons.Filled.PhotoLibrary),
    Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
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
    val captureInteraction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Compact floating pill matching the reference navigation geometry.
        Surface(
            modifier = Modifier.weight(1f).height(54.dp),
            shape = RoundedCornerShape(27.dp),
            color = SunnyColors.Surface,
            shadowElevation = 4.dp,
        ) {
            Row(
                Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TABS.forEachIndexed { index, tab ->
                    TabItem(
                        tab = tab,
                        selected = currentRoute == tab.route,
                        first = index == 0,
                        last = index == TABS.lastIndex,
                        onClick = { onSelectTab(tab.route) },
                    )
                }
            }
        }
        // The "+" capture button — always reachable (F-01 entry point).
        Surface(
            modifier = Modifier.size(54.dp)
                .sunnyPressScale(captureInteraction)
                .clip(CircleShape)
                .clickable(
                    interactionSource = captureInteraction,
                    indication = LocalIndication.current,
                    onClick = onCapture,
                ),
            shape = CircleShape,
            color = SunnyColors.Surface,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_photo),
                    tint = SunnyColors.Orange, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    tab: Tab,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(tab.labelRes)
    val interactionSource = remember { MutableInteractionSource() }
    // The reference keeps every tab monochrome and uses weight, not a large
    // background capsule, to identify the current destination.
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) SunnyColors.Orange else Color.Transparent,
        animationSpec = tween(durationMillis = 120, easing = SunnyMotion.EaseOut),
        label = "$label selected indicator",
    )
    val itemShape = RoundedCornerShape(
        topStart = if (first) 27.dp else 18.dp,
        topEnd = if (last) 27.dp else 18.dp,
        bottomEnd = if (last) 27.dp else 18.dp,
        bottomStart = if (first) 27.dp else 18.dp,
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .sunnyPressScale(interactionSource)
            .clip(itemShape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = SunnyColors.TextPrimary,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = SunnyColors.TextPrimary,
        )
        Spacer(Modifier.height(1.dp))
        Box(
            Modifier
                .width(16.dp)
                .height(3.dp)
                .background(indicatorColor, RoundedCornerShape(2.dp)),
        )
    }
}
