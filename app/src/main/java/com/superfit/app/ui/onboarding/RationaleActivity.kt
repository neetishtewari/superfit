package com.superfit.app.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.theme.ThemeConfig

class RationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) { ThemeConfig.onSystemDarkChanged(systemDark) }
            val isDark by ThemeConfig.isDarkTheme.collectAsState()
            SuperfitTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RationaleScreen(
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RationaleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val colors = SuperfitTheme.colors
    val darkBg = colors.bgStart
    val cardBg = colors.cardBgTranslucent
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    val textTertiary = colors.textTertiary
    val glassBorder = colors.glassBorder

    val neonGreen = Color(0xFF10B981)
    val electricCyan = Color(0xFF06B6D4)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Data Privacy & Integration",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = darkBg,
                    titleContentColor = textPrimary
                )
            )
        },
        containerColor = darkBg,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Branding/Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(neonGreen.copy(alpha = 0.15f), electricCyan.copy(alpha = 0.15f))
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(listOf(neonGreen.copy(alpha = 0.4f), electricCyan.copy(alpha = 0.4f))),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Why Superfit uses Health Connect",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textPrimary
                    )
                    Text(
                        text = "To see the full picture of your day, Superfit reads a few kinds of health data. Superfit's AI coach (powered by Google Gemini) uses them to set your daily targets and give advice that fits your day.",
                        fontSize = 14.sp,
                        color = textSecondary,
                        lineHeight = 20.sp
                    )
                }
            }

            Text(
                text = "What Superfit reads and why",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Start
            )

            // Permissions list
            val rationaleItems = listOf(
                RationaleItem(
                    title = "Steps & Activity",
                    description = "Shows how much you moved today, so your calorie target and the coach's advice match your day."
                ),
                RationaleItem(
                    title = "Active Calories",
                    description = "Adds the calories you burned in workouts, so you don't have to log them by hand."
                ),
                RationaleItem(
                    title = "Sleep",
                    description = "How long and how well you slept, shown in the app so you can see how sleep and your days line up."
                ),
                RationaleItem(
                    title = "Height & Weight",
                    description = "Needed to work out how many calories your body uses at rest, the starting point for your daily target."
                ),
                RationaleItem(
                    title = "Body Measurements",
                    description = "If a smart scale app saves body fat, lean mass, bone mass or BMR, Superfit shows them on your Journey and uses body fat for a more accurate calorie target (Katch-McArdle). Readings that don't look like yours are asked about first."
                )
            )

            rationaleItems.forEach { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = neonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = item.description,
                                fontSize = 13.sp,
                                color = textTertiary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = neonGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Acknowledge & Return",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Superfit complies with Google Play Health Connect developer policies. Your data is stored on your phone and backed up to your private Superfit account (Google Firebase). To create your coaching insights, a summary of your recent activity, sleep and meals is sent to Google's Gemini AI.",
                fontSize = 12.sp,
                color = textTertiary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

data class RationaleItem(
    val title: String,
    val description: String
)
