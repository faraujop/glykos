package com.glykos.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glykos.core.data.settings.OutputMode
import com.glykos.core.data.settings.SettingsRepository
import com.glykos.feature.monitor.MonitorController
import com.glykos.feature.monitor.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val controller: MonitorController,
) : ViewModel() {

    val status: StateFlow<ServiceStatus> = controller.status

    val email: StateFlow<String?> = settings.credentialsEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val outputMode: StateFlow<OutputMode> = settings.outputMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OutputMode.BOTH)

    fun saveCredentials(email: String, password: String) {
        viewModelScope.launch { settings.saveCredentials(email, password) }
    }

    fun clearCredentials() {
        viewModelScope.launch { settings.clearCredentials() }
    }

    fun setOutputMode(mode: OutputMode) {
        viewModelScope.launch { settings.setOutputMode(mode) }
    }

    fun start() = controller.start()

    fun stop() = controller.stop()
}
