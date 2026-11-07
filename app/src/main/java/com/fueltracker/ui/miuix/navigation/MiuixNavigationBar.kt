package com.fueltracker.ui.miuix.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

//导航栏条目数据类。
data class AppNavItem(
    val icon: ImageVector,
    val label: String
)
//基础导航栏
@Composable
fun MiuixNavigationBar(
    modifier: Modifier = Modifier,
    isFloating: Boolean = true,
    selectedIndex: Int = 0,
    onSelectedIndexChange: (Int) -> Unit,
    items: List<AppNavItem>
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        if (isFloating) {
            FloatingNavigationBar(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                items.forEachIndexed { index, item ->
                    FloatingNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onSelectedIndexChange(index) },
                        icon = item.icon,
                        label = item.label
                    )
                }
            }
        } else {
            NavigationBar(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onSelectedIndexChange(index) },
                        icon = item.icon,
                        label = item.label
                    )
                }
            }
        }
    }
}

//全 App 导航栏统一入口。
@Composable
fun MiuixAppNavigationBar(
    isGlassEnabled: Boolean,
    isFloating: Boolean,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    backdrop: Backdrop,
    items: List<AppNavItem>,
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
            itemCount = items.size,
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
