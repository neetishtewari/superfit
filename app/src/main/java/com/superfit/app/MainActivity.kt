package com.superfit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.superfit.app.data.DataRepository
import com.superfit.app.data.HealthConnectManager
import com.superfit.app.data.SuperfitDatabase
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeConfig

import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.content.Intent

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var repository: DataRepository
    private val triggerVoiceLog = MutableStateFlow(false)
    private val triggerFavoritesLog = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database, manager and repository
        healthConnectManager = HealthConnectManager(applicationContext)
        repository = DataRepository(applicationContext, healthConnectManager)

        ThemeConfig.initialize(applicationContext)
        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            val isDark by ThemeConfig.isDarkTheme.collectAsState()
            SuperfitTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val triggerVoice by triggerVoiceLog.collectAsState()
                    val triggerFavs by triggerFavoritesLog.collectAsState()
                    MainNavigation(
                        healthConnectManager = healthConnectManager,
                        repository = repository,
                        triggerVoiceLog = triggerVoice,
                        triggerFavoritesLog = triggerFavs,
                        onVoiceLogTriggeredHandled = { triggerVoiceLog.value = false },
                        onFavoritesLogTriggeredHandled = { triggerFavoritesLog.value = false }
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
        }
    }
}
