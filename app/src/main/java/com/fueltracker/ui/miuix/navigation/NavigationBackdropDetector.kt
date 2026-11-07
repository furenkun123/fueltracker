package com.fueltracker.ui.miuix.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.Box

class NavigationBackdropDetector {

    private val contentRegions = mutableStateMapOf<String, Rect>()

    private var navigationArea: Rect? = null

    var hasNearbyContent by mutableStateOf(false)
        private set

    fun updateNavigation(
        bounds: Rect,
        marginPx: Float
    ) {
        navigationArea = Rect(
            left = bounds.left - marginPx,
            top = bounds.top - marginPx,
            right = bounds.right + marginPx,
            bottom = bounds.bottom + marginPx
        )
        updateDetection()
    }

    fun updateContent(
        key: String,
        bounds: Rect
    ) {
        contentRegions[key] = bounds
        updateDetection()
    }

    fun removeContent(key: String) {
        contentRegions.remove(key)
        updateDetection()
    }

    private fun updateDetection() {
        val area = navigationArea ?: return

        hasNearbyContent = contentRegions.values.any { bounds ->
            bounds.width > 0f &&
                    bounds.height > 0f &&
                    bounds.overlaps(area)
        }
    }
}

@Composable
fun NavigationContentRegion(
    detector: NavigationBackdropDetector,
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    DisposableEffect(detector, key) {
        onDispose {
            detector.removeContent(key)
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            if (coordinates.isAttached && coordinates.isPlaced) {
                detector.updateContent(
                    key = key,
                    bounds = coordinates.boundsInWindow()
                )
            }
        }
    ) {
        content()
    }
}