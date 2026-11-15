package com.fueltracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.time.Duration.Companion.milliseconds

/**
 * 当前日期边界监听。
 *
 * 每天本地零点自动更新；
 * 从后台返回前台时重新检查；
 * 返回 yyyy * 1000 + dayOfYear 作为日期变化标识。
 */
@Composable
fun rememberDateBoundaryKey(): Int {
    var dateKey by remember {
        mutableIntStateOf(currentDateBoundaryKey())
    }

    var resumeGeneration by remember {
        mutableIntStateOf(0)
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    // 从后台返回时刷新日期，同时重新计算下一次零点时间
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dateKey = currentDateBoundaryKey()
                resumeGeneration++
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 自动等待下一个本地零点
    LaunchedEffect(resumeGeneration) {
        while (true) {
            val now = System.currentTimeMillis()

            val nextMidnight = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            delay((nextMidnight - System.currentTimeMillis()).coerceAtLeast(1L).milliseconds)

            dateKey = currentDateBoundaryKey()
        }
    }

    return dateKey
}

private fun currentDateBoundaryKey(): Int {
    val calendar = Calendar.getInstance()

    return calendar.get(Calendar.YEAR) * 1000 +
            calendar.get(Calendar.DAY_OF_YEAR)
}