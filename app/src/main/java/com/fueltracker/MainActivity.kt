
package com.fueltracker

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.fueltracker.ui.theme.FuelTrackerTheme
import com.fueltracker.ui.theme.ThemePreferences
import com.fueltracker.ui.miui.glass.GlassPreferences
import com.fueltracker.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemePreferences.init(applicationContext)
        GlassPreferences.init(applicationContext)

        // 启用 Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Android 14 及以下：显式设置状态栏透明
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
        }

        setContent {
            FuelTrackerTheme(
                themeMode = ThemePreferences.mode
            ) {
                // 根据应用实际主题判断系统栏图标颜色
                val isDarkTheme =
                    MaterialTheme.colorScheme.background.luminance() < 0.5f

                SideEffect {
                    WindowCompat.getInsetsController(
                        window,
                        window.decorView
                    ).apply {
                        // true：深色图标，适合浅色背景
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }

                val application = application as FuelTrackerApplication
                val initState by application.initState.collectAsState()

                when (val state = initState) {
                    is FuelTrackerApplication.InitState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is FuelTrackerApplication.InitState.Success -> {
                        FuelTrackerApp(viewModel = viewModel)
                    }

                    is FuelTrackerApplication.InitState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "初始化失败",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    application.startInitialization()
                                }
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }
            }
        }
    }
}
