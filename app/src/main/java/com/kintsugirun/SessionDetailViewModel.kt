package com.kintsugirun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val repository: WorkoutRepository,
    private val initialSessionId: Long? = null
) : ViewModel() {

    private val _session = MutableStateFlow<WorkoutSession?>(null)
    val session: StateFlow<WorkoutSession?> = _session.asStateFlow()

    init {
        initialSessionId?.let { loadSession(it) }
    }

    fun loadSession(sessionId: Long) {
        viewModelScope.launch {
            repository.getSessionById(sessionId).collect { s ->
                _session.value = s
            }
        }
    }

    fun deleteSession(sessionId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            onDeleted()
        }
    }
}
