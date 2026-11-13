package com.superfit.app.ui.recap

import com.superfit.app.data.LogRepository
import com.superfit.app.data.Now
import com.superfit.app.data.Prefs
import com.superfit.app.data.RecapData
import com.superfit.app.data.UsageEvents
import com.superfit.app.domain.RecapCard
import com.superfit.app.domain.RecapSetup
import com.superfit.app.domain.ScaleReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

/** One month's recap for the iPhone, loaded the same way as Android's RecapViewModel. */
class RecapModel(
    private val log: LogRepository,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
    val month: YearMonth,
    private val setup: suspend () -> RecapSetup,
    private val scale: () -> List<ScaleReading>
) {
    private val _state = MutableStateFlow<RecapState>(RecapState.Loading)
    val state: StateFlow<RecapState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        scope.launch {
            val today = Now.today()
            val recap = RecapData.load(log, prefs, month, today, setup(), scale())
            if (recap == null || recap.cards.none { it !is RecapCard.Setup }) {
                _state.value = RecapState.TooLittle
                return@launch
            }
            if (month < today.yearMonth) RecapData.markOpened(prefs, month, today)
            UsageEvents.screenOpened("recap")
            _state.value = RecapState.Ready(recap, RecapData.words(prefs, recap, today))
        }
    }

    companion object {
        /** Last month, while Home offers its recap (from the 1st until just after it's opened) and it has something to say. */
        suspend fun homeOffer(log: LogRepository, prefs: Prefs, setup: RecapSetup, scale: List<ScaleReading>): YearMonth? {
            val today = Now.today()
            if (!RecapData.showOnHome(prefs, today)) return null
            val month = RecapData.offeredMonth(today)
            val recap = RecapData.load(log, prefs, month, today, setup, scale) ?: return null
            return month.takeIf { recap.cards.any { it !is RecapCard.Setup } }
        }
    }
}
