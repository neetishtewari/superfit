package com.superfit.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.superfit.app.data.DataRepository
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.data.SuperfitDatabase
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeConfig
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var repository: DataRepository
    private val triggerVoiceLog = MutableStateFlow(false)
    private val triggerFavoritesLog = MutableStateFlow(false)
    private val triggerPhotoLog = MutableStateFlow(false)
    private val openRecap = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database, manager and repository
        healthConnectManager = HealthConnectManager(applicationContext)
        repository = DataRepository(applicationContext, healthConnectManager)

        ThemeConfig.initialize(applicationContext)
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) { ThemeConfig.onSystemDarkChanged(systemDark) }
            val isDark by ThemeConfig.isDarkTheme.collectAsState()
            // Status and nav bar icons follow the app's Light/Dark choice, not the phone's,
            // so the clock stays readable when the two differ.
            DisposableEffect(isDark) {
                val style = if (isDark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SuperfitTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val triggerVoice by triggerVoiceLog.collectAsState()
                    val triggerFavs by triggerFavoritesLog.collectAsState()
                    val triggerPhoto by triggerPhotoLog.collectAsState()
                    val recapMonth by openRecap.collectAsState()
                    MainNavigation(
                        healthConnectManager = healthConnectManager,
                        repository = repository,
                        triggerVoiceLog = triggerVoice,
                        triggerFavoritesLog = triggerFavs,
                        triggerPhotoLog = triggerPhoto,
                        onVoiceLogTriggeredHandled = { triggerVoiceLog.value = false },
                        onFavoritesLogTriggeredHandled = { triggerFavoritesLog.value = false },
                        onPhotoLogTriggeredHandled = { triggerPhotoLog.value = false },
                        openRecapMonth = recapMonth,
                        onOpenRecapHandled = { openRecap.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.superfit.app.ACTION_VOICE_LOG") {
            triggerVoiceLog.value = true
        } else if (intent?.action == "com.superfit.app.ACTION_FAVORITES_LOG") {
            triggerFavoritesLog.value = true
        } else if (intent?.action == "com.superfit.app.ACTION_PHOTO_LOG") {
            triggerPhotoLog.value = true
        } else if (intent?.action == com.superfit.app.data.RecapNotificationWorker.ACTION_OPEN_RECAP) {
            openRecap.value = intent.getStringExtra(com.superfit.app.data.RecapNotificationWorker.EXTRA_MONTH)
        }
    }
}
