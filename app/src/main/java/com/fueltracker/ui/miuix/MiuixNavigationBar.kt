package com.fueltracker.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

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
