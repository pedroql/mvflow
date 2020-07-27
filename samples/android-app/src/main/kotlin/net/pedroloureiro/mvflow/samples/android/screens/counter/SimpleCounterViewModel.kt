package net.pedroloureiro.mvflow.samples.android.screens.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.pedroloureiro.mvflow.MVFlowWithEffects

class SimpleCounterViewModel : ViewModel() {

    val mvFlow: MVFlowWithEffects<CounterMVFlow.State, CounterMVFlow.Action, CounterMVFlow.Effect>

    init {
        mvFlow = CounterMVFlow.create(coroutineScope = viewModelScope)
    }
}
