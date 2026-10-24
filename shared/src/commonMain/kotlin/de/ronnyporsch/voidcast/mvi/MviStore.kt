package de.ronnyporsch.voidcast.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class MviStore<S, E, F>(initialState: S) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<F>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val currentState: S get() = _state.value

    abstract fun onEvent(event: E)

    protected fun setState(reducer: S.() -> S) {
        _state.value = _state.value.reducer()
    }

    protected fun emitEffect(effect: F) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    protected fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
