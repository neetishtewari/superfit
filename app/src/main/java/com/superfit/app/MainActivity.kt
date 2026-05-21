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

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var repository: DataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database, manager and repository
        healthConnectManager = HealthConnectManager(applicationContext)
        val database = SuperfitDatabase.getDatabase(applicationContext)
        repository = DataRepository(database, healthConnectManager)

        enableEdgeToEdge()
        setContent {
            SuperfitTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        healthConnectManager = healthConnectManager,
                        repository = repository
                    )
                }
            }
        }
    }
}
