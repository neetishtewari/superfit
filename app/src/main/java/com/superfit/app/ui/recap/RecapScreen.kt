package com.superfit.app.ui.recap

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.AppAnalytics
import com.superfit.app.data.DataRepository
import com.superfit.app.data.MonthlyRecapData
import com.superfit.app.domain.MonthRecap
import com.superfit.app.domain.RecapCard
import com.superfit.app.domain.RecapWords
import com.superfit.app.domain.SetupItem
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.share.CapturableStory
import com.superfit.app.ui.share.shareImage
import com.superfit.app.widget.WidgetPin
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RecapState {
    data object Loading : RecapState
    data object TooLittle : RecapState
    data class Ready(val recap: MonthRecap, val words: Map<String, RecapWords>) : RecapState
}

class RecapViewModel(private val repository: DataRepository, private val appContext: Context, val month: YearMonth) : ViewModel() {
    private val _state = MutableStateFlow<RecapState>(RecapState.Loading)
    val state: StateFlow<RecapState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val recap = MonthlyRecapData.load(appContext, repository, month)
            if (recap == null || recap.cards.none { it !is RecapCard.Setup }) {
                _state.value = RecapState.TooLittle
                return@launch
            }
            if (month.isBefore(YearMonth.now())) MonthlyRecapData.markOpened(appContext, month)
            AppAnalytics.screenOpened("recap")
            _state.value = RecapState.Ready(recap, MonthlyRecapData.words(appContext, recap))
        }
    }
}

/** The month in story cards, swiped one at a time, each with its own Share button. */
@Composable
fun RecapScreen(viewModel: RecapViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .background(SuperfitTheme.colors.bgStart)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SuperfitTheme.colors.textPrimary)
            }
            Text(
                "Your ${monthName(viewModel.month)}",
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        when (val s = state) {
            RecapState.Loading -> Centered {
                CircularProgressIndicator(color = SuperfitTheme.colors.primaryAccent)
                Spacer(Modifier.height(12.dp))
                Text("Putting your ${monthName(viewModel.month)} together…", color = SuperfitTheme.colors.textSecondary)
            }
            RecapState.TooLittle -> Centered {
                Text(
                    "Not enough logged in ${monthName(viewModel.month)} for a recap yet. Log meals on a few days and it'll be here next month.",
                    color = SuperfitTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            is RecapState.Ready -> RecapPager(s, viewModel.month, onOpenSettings, viewModel::reload)
        }
    }
}

@Composable
private fun RecapPager(state: RecapState.Ready, month: YearMonth, onOpenSettings: () -> Unit, onChanged: () -> Unit) {
    val cards = state.recap.cards
    val pager = rememberPagerState { cards.size }
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 36.dp), pageSpacing = 14.dp) { page ->
            val card = cards[page]
            val words = state.words[card.id] ?: return@HorizontalPager
            if (card is RecapCard.Setup) {
                SetupPage(card, onOpenSettings, onChanged)
            } else {
                SharePage(card, words, month)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            cards.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pager.currentPage) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) SuperfitTheme.colors.primaryAccent else SuperfitTheme.colors.textTertiary)
                )
            }
        }
    }
}

@Composable
private fun SharePage(card: RecapCard, words: RecapWords, month: YearMonth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CapturableStory(layer, Modifier.fillMaxWidth()) { RecapStory(card, words, month) }
        Button(
            onClick = {
                busy = true
                scope.launch {
                    try {
                        shareImage(context, layer.toImageBitmap().asAndroidBitmap())
                        AppAnalytics.cardShared("recap_${card.id}")
                    } catch (e: Exception) {
                        Toast.makeText(context, "Couldn't make the picture. Please try again.", Toast.LENGTH_SHORT).show()
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = SuperfitTheme.colors.primaryAccent, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share this card", fontWeight = FontWeight.Bold)
        }
    }
}

/** "Get more from Superfit": only what's missing, each with a one-tap fix. Not shareable. */
@Composable
private fun SetupPage(card: RecapCard.Setup, onOpenSettings: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SuperfitTheme.colors.cardBg)
            .border(1.5.dp, SuperfitTheme.colors.primaryAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("GET MORE FROM SUPERFIT", color = SuperfitTheme.colors.primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text("${card.items.size} quick ${if (card.items.size == 1) "thing" else "things"} for a smarter recap next month", color = SuperfitTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        card.items.forEach { item ->
            val (title, detail, action) = when (item) {
                SetupItem.HEALTH_CONNECT -> Triple("Steps, workouts and sleep", "Let Superfit read Health Connect so activity counts without typing.", "Turn on")
                SetupItem.MIC -> Triple("Log by voice", "Allow the mic and just say what you ate or did.", "Allow")
                SetupItem.WIDGET -> Triple("Home-screen widget", "Log a meal without opening the app.", "Add")
                SetupItem.LOG_DAILY -> Triple("Log every day", "You logged ${card.loggedDays} of ${card.daysInMonth} days. A 9 pm reminder helps.", "Remind me")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = SuperfitTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(detail, color = SuperfitTheme.colors.textSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = {
                        when (item) {
                            SetupItem.MIC -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            SetupItem.WIDGET -> if (!WidgetPin.requestPin(context)) Toast.makeText(context, "Long-press your home screen and add the Superfit widget.", Toast.LENGTH_LONG).show()
                            SetupItem.HEALTH_CONNECT, SetupItem.LOG_DAILY -> onOpenSettings()
                        }
                    },
                    shape = RoundedCornerShape(50)
                ) { Text(action, fontWeight = FontWeight.Bold) }
            }
        }
        Text("Only what's missing is listed.", color = SuperfitTheme.colors.textTertiary, fontSize = 12.sp)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}

/** Today's recap entry: last month's, offered from the 1st. */
fun recapMonthToday(today: LocalDate = LocalDate.now()): YearMonth = MonthlyRecapData.offeredMonth(today)
