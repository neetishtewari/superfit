package com.superfit.app.ui.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.superfit.app.data.UsageEvents
import com.superfit.app.domain.MonthRecap
import com.superfit.app.domain.RecapAutoplay
import com.superfit.app.domain.RecapCard
import com.superfit.app.domain.RecapWords
import com.superfit.app.domain.SetupItem
import com.superfit.app.theme.SuperfitTheme
import com.superfit.app.ui.share.CapturableStory
import com.superfit.app.ui.share.LocalShareCardHost
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.YearMonth

sealed interface RecapState {
    data object Loading : RecapState
    data object TooLittle : RecapState
    data class Ready(val recap: MonthRecap, val words: Map<String, RecapWords>) : RecapState
}

/**
 * What the recap needs from each phone: its music, its video maker and the one-tap fixes on the
 * "Get more from Superfit" card. Pictures are shared through [LocalShareCardHost].
 */
interface RecapHost {
    /** "Health Connect" or "Apple Health". */
    val healthName: String

    val musicMuted: Boolean

    /** Mutes or unmutes the music, returning whether it is now muted. */
    fun toggleMusic(): Boolean
    fun playMusic()
    fun pauseMusic()

    /**
     * Makes the month's story video with its music and opens the share sheet. [frame] draws story
     * card i at a point of its count-up (0-1). Throws when the phone can't make it.
     */
    suspend fun shareVideo(stories: Int, frame: suspend (Int, Float) -> ImageBitmap, onProgress: (Float) -> Unit)

    /** Returns what runs a setup fix; [onChanged] reloads the recap when it may have changed. */
    @Composable
    fun rememberSetupFix(onChanged: () -> Unit): (SetupItem) -> Unit
}

/** The month in story cards, swiped one at a time, each with its own Share button. */
@Composable
fun RecapStoryScreen(month: YearMonth, state: RecapState, host: RecapHost, inBackground: Boolean, onBack: () -> Unit, onReload: () -> Unit) {
    var muted by remember { mutableStateOf(host.musicMuted) }
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
                "Your ${monthName(month)}",
                color = SuperfitTheme.colors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (state is RecapState.Ready) {
                IconButton(onClick = { muted = host.toggleMusic() }) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (muted) "Turn music on" else "Turn music off",
                        tint = SuperfitTheme.colors.textPrimary
                    )
                }
            }
        }
        when (state) {
            RecapState.Loading -> Centered {
                CircularProgressIndicator(color = SuperfitTheme.colors.primaryAccent)
                Spacer(Modifier.height(12.dp))
                Text("Putting your ${monthName(month)} together…", color = SuperfitTheme.colors.textSecondary)
            }
            RecapState.TooLittle -> Centered {
                Text(
                    "Not enough logged in ${monthName(month)} for a recap yet. Log meals on a few days and it'll be here next month.",
                    color = SuperfitTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            is RecapState.Ready -> RecapPager(state, month, host, inBackground, onReload)
        }
    }
}

/**
 * The recap plays on its own like a story: each card stays up long enough to read, numbers count up
 * as it arrives, and a quiet original track plays. Tap the right of a card for the next one, the
 * left to go back, and hold to pause. It stops at the last story card; the "Get more from Superfit"
 * card is never played automatically.
 */
@Composable
private fun RecapPager(state: RecapState.Ready, month: YearMonth, host: RecapHost, inBackground: Boolean, onChanged: () -> Unit) {
    val cards = state.recap.cards
    val stories = cards.count { it !is RecapCard.Setup }
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    val shareHost = LocalShareCardHost.current
    var held by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    // Progress 0-1 while the video is being made; null otherwise.
    var making by remember { mutableStateOf<Float?>(null) }
    // The count-up point the video is drawing right now.
    var videoReveal by remember { mutableStateOf(1f) }
    val layers = remember { mutableMapOf<Int, GraphicsLayer>() }
    val progress = remember { Animatable(0f) }
    val page = pager.currentPage
    val onStory = page < cards.size && cards[page] !is RecapCard.Setup
    val paused = held || sharing || inBackground || making != null || pager.isScrollInProgress

    // Music plays while the story is on screen and not paused by a hold or the app going away.
    LaunchedEffect(inBackground, onStory, held, making == null) {
        if (!inBackground && onStory && !held && making == null) host.playMusic() else host.pauseMusic()
    }

    val progressPage = remember { intArrayOf(-1) }
    LaunchedEffect(page, paused, onStory) {
        if (progressPage[0] != page) {
            progress.snapTo(0f)
            progressPage[0] = page
        }
        if (!onStory || paused) return@LaunchedEffect
        val card = cards[page]
        val words = state.words[card.id]
        val total = RecapAutoplay.durationMs(words?.line.orEmpty(), words?.title.orEmpty())
        val left = ((1f - progress.value) * total).toInt()
        val began = TimeSource.Monotonic.markNow()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        // Never turn the page before the card has really been up for its time, whatever the animation clock did.
        val owed = left - began.elapsedNow().inWholeMilliseconds
        if (owed > 0) delay(owed)
        if (page + 1 < stories) {
            // Its own coroutine: the page turn itself pauses the timer, which would cancel it here.
            // Only while this card is still the one on screen and nobody is swiping.
            if (pager.currentPage == page && !pager.isScrollInProgress) scope.launch { pager.animateScrollToPage(page + 1) }
        } else if (!finished) {
            finished = true
            UsageEvents.recapWatched(stories)
        }
    }

    fun go(to: Int) {
        scope.launch { pager.animateScrollToPage(to.coerceIn(0, cards.size - 1)) }
    }

    Column(Modifier.fillMaxSize()) {
        // Story progress: one segment per story card, filled as each plays.
        Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(stories) { i ->
                val fill = when {
                    i < page || !onStory -> 1f
                    i == page -> progress.value
                    else -> 0f
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SuperfitTheme.colors.textTertiary.copy(alpha = 0.35f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(SuperfitTheme.colors.primaryAccent)
                    )
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 36.dp), pageSpacing = 14.dp) { index ->
            val card = cards[index]
            val words = state.words[card.id] ?: return@HorizontalPager
            if (card is RecapCard.Setup) {
                SetupPage(card, host, onChanged)
            } else {
                val reveal = when {
                    making != null -> RecapReveal.Full
                    index != page -> if (index < page) RecapReveal.Full else RecapReveal.Waiting
                    else -> RecapReveal.Playing
                }
                CompositionLocalProvider(LocalRecapReveal provides reveal, LocalRecapVideoReveal provides videoReveal.takeIf { making != null }) {
                    SharePage(
                        card,
                        words,
                        month,
                        onSharing = { sharing = it },
                        onLayer = { if (it != null) layers[index] = it else layers.remove(index) },
                        tapModifier = Modifier.pointerInput(index) {
                            detectTapGestures(
                                onPress = {
                                    held = true
                                    tryAwaitRelease()
                                    held = false
                                },
                                onTap = { at -> go(if (at.x < size.width / 3f) index - 1 else index + 1) }
                            )
                        }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    if (making != null) return@OutlinedButton
                    making = 0f
                    scope.launch {
                        val back = pager.currentPage
                        try {
                            // The video asks for each picture as it needs it: the card on screen, its
                            // numbers drawn at that point of the count-up, then captured.
                            val frame: suspend (Int, Float) -> ImageBitmap = { i, at ->
                                withContext(Dispatchers.Main) {
                                    if (pager.currentPage != i || pager.isScrollInProgress) {
                                        pager.scrollToPage(i)
                                        delay(250)
                                    }
                                    videoReveal = at
                                    withFrameNanos { }
                                    withFrameNanos { }
                                    val layer = layers[i] ?: error("Card $i not drawn")
                                    layer.toImageBitmap()
                                }
                            }
                            try {
                                host.shareVideo(stories, frame) { making = it }
                            } finally {
                                videoReveal = 1f
                                pager.scrollToPage(back)
                            }
                            UsageEvents.cardShared("recap_video")
                        } catch (e: Exception) {
                            UsageEvents.recordError(e)
                            shareHost.notice("Couldn't make the video on this phone. You can still share each card.")
                        } finally {
                            making = null
                        }
                    }
                },
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share as video", fontWeight = FontWeight.Bold)
            }
            if (cards.size > stories) {
                TextButton(onClick = { go(cards.size - 1) }) {
                    Text("Setup tips", color = SuperfitTheme.colors.textSecondary, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    making?.let { p ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SuperfitTheme.colors.cardBg)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(progress = { p }, color = SuperfitTheme.colors.primaryAccent)
                Spacer(Modifier.height(14.dp))
                Text("Making your video… ${(p * 100).toInt()}%", color = SuperfitTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SharePage(card: RecapCard, words: RecapWords, month: YearMonth, onSharing: (Boolean) -> Unit, onLayer: (GraphicsLayer?) -> Unit, tapModifier: Modifier) {
    val shareHost = LocalShareCardHost.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    DisposableEffect(layer) {
        onLayer(layer)
        onDispose { onLayer(null) }
    }
    var busy by remember { mutableStateOf(false) }
    // The card shrinks to leave room for its Share button on shorter screens, keeping its 9:16 shape.
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val cardWidth = if (constraints.hasBoundedHeight) minOf(maxWidth, ((maxHeight - 66.dp) * 9f / 16f).coerceAtLeast(120.dp)) else maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CapturableStory(layer, Modifier.width(cardWidth).then(tapModifier)) { RecapStory(card, words, month) }
            Button(
                onClick = {
                    busy = true
                    onSharing(true)
                    scope.launch {
                        try {
                            shareHost.share(layer.toImageBitmap())
                            UsageEvents.cardShared("recap_${card.id}")
                        } catch (e: Exception) {
                            shareHost.notice("Couldn't make the picture. Please try again.")
                        } finally {
                            busy = false
                            onSharing(false)
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
}

/** "Get more from Superfit": only what's missing, each with a one-tap fix. Not shareable. */
@Composable
private fun SetupPage(card: RecapCard.Setup, host: RecapHost, onChanged: () -> Unit) {
    val fix = host.rememberSetupFix(onChanged)
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
                SetupItem.HEALTH_CONNECT -> Triple("Steps, workouts and sleep", "Let Superfit read ${host.healthName} so activity counts without typing.", "Turn on")
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
                OutlinedButton(onClick = { fix(item) }, shape = RoundedCornerShape(50)) { Text(action, fontWeight = FontWeight.Bold) }
            }
        }
        Text("Only what's missing is listed.", color = SuperfitTheme.colors.textTertiary, fontSize = 12.sp)
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
}
