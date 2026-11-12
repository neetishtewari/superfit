package com.superfit.app.ui.recap

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.superfit.app.data.DataRepository
import com.superfit.app.data.MonthlyRecapData
import com.superfit.app.data.UsageEvents
import com.superfit.app.domain.RecapCard
import com.superfit.app.domain.SetupItem
import com.superfit.app.widget.WidgetPin
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toKotlinYearMonth

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
            UsageEvents.screenOpened("recap")
            _state.value = RecapState.Ready(recap, MonthlyRecapData.words(appContext, recap))
        }
    }
}

/** The month in story cards (shared with the iPhone), with Android's music, video and setup fixes. */
@Composable
fun RecapScreen(viewModel: RecapViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val host = remember { AndroidRecapHost(context, viewModel.month, onOpenSettings) }
    DisposableEffect(host) { onDispose { host.release() } }
    var inBackground by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> inBackground = true
                Lifecycle.Event.ON_RESUME -> inBackground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    RecapStoryScreen(viewModel.month.toKotlinYearMonth(), state, host, inBackground, onBack, viewModel::reload)
}

private class AndroidRecapHost(context: Context, private val month: YearMonth, private val onOpenSettings: () -> Unit) : RecapHost {
    private val context = context.applicationContext
    private val activity = context
    private val music = RecapMusic(this.context, month)

    override val healthName = "Health Connect"
    override val musicMuted get() = music.muted

    override fun toggleMusic() = music.toggleMute()
    override fun playMusic() = music.play()
    override fun pauseMusic() = music.pause()
    fun release() = music.release()

    override suspend fun shareVideo(stories: Int, frame: suspend (Int, Float) -> ImageBitmap, onProgress: (Float) -> Unit) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "superfit-${month.month.name.lowercase()}-${month.year}.mp4")
        RecapVideo.make(context, stories, { i, at -> RecapVideo.frameOf(frame(i, at).asAndroidBitmap()) }, RecapMusic.trackFor(month), file, onProgress)
        RecapVideo.share(activity, file)
    }

    @Composable
    override fun rememberSetupFix(onChanged: () -> Unit): (SetupItem) -> Unit {
        val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }
        return { item ->
            when (item) {
                SetupItem.MIC -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                SetupItem.WIDGET -> if (!WidgetPin.requestPin(activity)) Toast.makeText(context, "Long-press your home screen and add the Superfit widget.", Toast.LENGTH_LONG).show()
                SetupItem.HEALTH_CONNECT, SetupItem.LOG_DAILY -> onOpenSettings()
            }
        }
    }
}

/** Today's recap entry: last month's, offered from the 1st. */
fun recapMonthToday(today: LocalDate = LocalDate.now()): YearMonth = MonthlyRecapData.offeredMonth(today)
