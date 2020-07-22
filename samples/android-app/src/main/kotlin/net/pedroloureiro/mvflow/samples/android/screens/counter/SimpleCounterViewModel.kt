package net.pedroloureiro.mvflow.samples.android.screens.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.pedroloureiro.mvflow.MVFlow

class SimpleCounterViewModel : ViewModel() {

    val mvFlow: MVFlow<CounterMVFlow.State, CounterMVFlow.Action, CounterMVFlow.Mutation>

    init {
        mvFlow = CounterMVFlow.create(coroutineScope = viewModelScope)
    }
}
