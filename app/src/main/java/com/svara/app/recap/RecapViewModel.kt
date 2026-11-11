package com.svara.app.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.svara.app.data.repository.RecapRepository
import com.svara.app.domain.recap.Recap
import com.svara.app.domain.recap.RecapEngine
import com.svara.app.domain.recap.RecapKind
import com.svara.app.domain.recap.RecapPeriod
import com.svara.app.domain.repository.SettingsRepository
import com.svara.app.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One listed period: how many plays it has, and whether that is enough for a recap. */
data class PeriodEntry(val period: RecapPeriod, val plays: Int) {
    val unlocked: Boolean get() = plays >= RecapEngine.MIN_PLAYS
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapViewModel @Inject constructor(
    private val recaps: RecapRepository,
    private val settings: SettingsRepository,
    private val playback: PlaybackController,
    handle: SavedStateHandle,
) : ViewModel() {

    val historyEnabled: StateFlow<Boolean> = settings.settings.map { it.historyEnabled }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), settings.settings.value.historyEnabled)
    val autoAdvance: StateFlow<Boolean> = settings.settings.map { it.recapAutoAdvance }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), settings.settings.value.recapAutoAdvance)
    val homeCard: StateFlow<Boolean> = settings.settings.map { it.recapHomeCard }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), settings.settings.value.recapHomeCard)

    private val _kind = MutableStateFlow(RecapKind.WEEK)
    val kind: StateFlow<RecapKind> = _kind

    /** Periods with plays, newest first, with the one in progress always listed (for the unlock hint). */
    val periods: StateFlow<List<PeriodEntry>?> = combine(_kind, recaps.historyStamp, settings.settings.map { it.statsSince }.distinctUntilChanged()) { k, _, _ -> k }
        .flatMapLatest { k ->
            flow {
                val listed = recaps.periods(k).map { (p, n) -> PeriodEntry(p, n) }
                val current = recaps.current(k)
                emit(if (listed.none { it.period == current }) listOf(PeriodEntry(current, 0)) + listed else listed)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    /** The recap the story pager shows, from the route's period key. */
    val story: StateFlow<Recap?> = flow {
        val key = handle.get<String>("period")
        RecapPeriod.parse(key.orEmpty())?.let { emit(recaps.recap(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), null)

    fun setKind(k: RecapKind) { _kind.value = k }
    fun enableHistory() = viewModelScope.launch { settings.update { it.copy(historyEnabled = true) } }
    fun setAutoAdvance(on: Boolean) = viewModelScope.launch { settings.update { it.copy(recapAutoAdvance = on) } }
    fun setHomeCard(on: Boolean) = viewModelScope.launch { settings.update { it.copy(recapHomeCard = on) } }

    /** True when starting the recap's songs would replace something already queued. */
    fun hasQueue(): Boolean = playback.state.value.current != null

    /** Builds an ordinary queue from the recap's top songs; only called after the user agreed. */
    fun playTopSongs(recap: Recap) = playback.playQueue(recap.topSongs.map { it.songId })

    private companion object {
        const val STOP_MS = 5_000L
    }
}

/** Home's "Your Recap" card: the newest recap with enough plays, when the user wants the card. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecapHomeViewModel @Inject constructor(
    recaps: RecapRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val newest: StateFlow<Recap?> = combine(
        settings.settings.map { it.historyEnabled && it.recapHomeCard }.distinctUntilChanged(),
        recaps.historyStamp,
    ) { on, _ -> on }
        .flatMapLatest { on -> flow { emit(if (on) recaps.newestForHome() else null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
