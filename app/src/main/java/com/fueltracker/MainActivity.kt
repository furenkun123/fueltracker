package com.fueltracker

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fueltracker.data.ExpenseCategoryStore
import com.fueltracker.ui.theme.FuelTrackerTheme
import com.fueltracker.ui.theme.ThemeMode
import com.fueltracker.ui.theme.ThemePreferences
import com.fueltracker.viewmodel.MainViewModel
import com.fueltracker.notif.ParkingNotification
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemePreferences.init(applicationContext)
        ExpenseCategoryStore.init(applicationContext)
        ParkingNotification.ensureChannel(applicationContext)
        enableEdgeToEdge()

        // Android 13+ 首次进入时请求通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* 不管用户怎么选，都继续 */ }
                    .launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode = ThemePreferences.mode

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT  -> false
                ThemeMode.DARK   -> true
            }

            // ★ 关键: 让状态栏图标颜色跟随主题
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDark   // 亮色主题→深色图标
                        isAppearanceLightNavigationBars = !isDark
                    }
                }
            }

            FuelTrackerTheme(themeMode = themeMode) {
                FuelTrackerApp(viewModel = viewModel)
            }
        }
    }
}