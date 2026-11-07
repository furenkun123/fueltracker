package com.fueltracker.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop

/**
 * 全 App 导航栏统一入口。
 */
@Composable
fun MiuixAppNavigationBar(
    isGlassEnabled: Boolean,
    isFloating: Boolean,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    items: List<MiuixNavItem>,
    modifier: Modifier = Modifier
) {
    if (isFloating) {
        MiuixGlassNavigationBar(
            modifier = modifier,
            backdrop = backdrop,
            isGlassEnabled = isGlassEnabled,
            isFloating = true,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            itemCount = items.size
        ) {
            items.forEachIndexed { index, item ->
                MiuixGlassNavigationItem(
                    selected = selectedIndex == index,
                    onClick = { onSelectedIndexChange(index) },
                    icon = item.icon,
                    label = item.label
                )
            }
        }
    } else {
        MiuixNavigationBar(
            modifier = modifier,
            isFloating = false,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            items = items
        )
    }
}
