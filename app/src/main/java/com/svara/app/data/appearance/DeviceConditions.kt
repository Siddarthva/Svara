package com.svara.app.data.appearance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.svara.app.appearance.EffectLimits
import com.svara.app.appearance.EffectsPolicy
import com.svara.app.appearance.ThermalLevel
import com.svara.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Battery saver and thermal status. Listens only while the app is in the foreground
 * ([start]/[stop] are driven by the process lifecycle), so it costs nothing in the background.
 */
@Singleton
class DeviceConditions @Inject constructor(@ApplicationContext private val context: Context) {

    private val power = context.getSystemService(PowerManager::class.java)
    private val _batterySaver = MutableStateFlow(power?.isPowerSaveMode == true)
    val batterySaver: StateFlow<Boolean> = _batterySaver.asStateFlow()

    private val _thermal = MutableStateFlow(currentThermal())
    val thermal: StateFlow<ThermalLevel> = _thermal.asStateFlow()

    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            _batterySaver.value = power?.isPowerSaveMode == true
        }
    }

    private val thermalListener: Any? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) PowerManager.OnThermalStatusChangedListener { status -> _thermal.value = ThermalLevel.fromStatus(status) } else null

    fun start() {
        if (started) return
        started = true
        _batterySaver.value = power?.isPowerSaveMode == true
        _thermal.value = currentThermal()
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
            power.addThermalStatusListener(ContextCompat.getMainExecutor(context), thermalListener as PowerManager.OnThermalStatusChangedListener)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { context.unregisterReceiver(receiver) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
            runCatching { power.removeThermalStatusListener(thermalListener as PowerManager.OnThermalStatusChangedListener) }
        }
    }

    private fun currentThermal(): ThermalLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) ThermalLevel.fromStatus(power.currentThermalStatus) else ThermalLevel.NONE
}

/** The one place effect limits are decided; every screen reads [limits]. */
@Singleton
class EffectsRepository @Inject constructor(
    themes: ThemeRepository,
    customization: CustomizationRepository,
    conditions: DeviceConditions,
    @ApplicationScope scope: CoroutineScope,
) {
    val limits: StateFlow<EffectLimits> = combine(
        themes.resolved,
        customization.customization,
        conditions.batterySaver,
        conditions.thermal,
    ) { theme, custom, saver, thermal ->
        EffectsPolicy.resolve(custom.performanceMode ?: theme.spec.defaultTier, saver, thermal)
    }.stateIn(scope, SharingStarted.Eagerly, EffectsPolicy.limitsFor(com.svara.app.appearance.PerformanceMode.BALANCED))
}
