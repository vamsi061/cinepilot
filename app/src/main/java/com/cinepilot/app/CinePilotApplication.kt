package com.cinepilot.app

import android.app.Application
import com.cinepilot.app.domain.GeminiClient
import com.cinepilot.app.domain.MockGeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CinePilotApplication : Application() {

    /**
     * Intent understanding is deliberately behind [GeminiClient]. The default is a
     * deterministic local mock so the app runs without a network key. A real Gemini
     * implementation must not ship a key inside the APK; see docs/specs/03_gemini.md.
     */
    var geminiClient: GeminiClient = MockGeminiClient()
        private set

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _stbConnectionState = MutableStateFlow<STBConnectionState>(STBConnectionState.Disconnected)
    val stbConnectionState: StateFlow<STBConnectionState> = _stbConnectionState.asStateFlow()

    fun setVoiceState(state: VoiceState) {
        _voiceState.value = state
    }

    fun setSTBConnectionState(state: STBConnectionState) {
        _stbConnectionState.value = state
    }
}

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data object Processing : VoiceState
    data class Heard(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

sealed interface STBConnectionState {
    data object Disconnected : STBConnectionState
    data object Connecting : STBConnectionState
    data object Connected : STBConnectionState
    data class Error(val message: String) : STBConnectionState
}