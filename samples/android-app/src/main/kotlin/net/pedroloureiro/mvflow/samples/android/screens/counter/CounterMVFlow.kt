package net.pedroloureiro.mvflow.samples.android.screens.counter

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import net.pedroloureiro.mvflow.Handler
import net.pedroloureiro.mvflow.MVFlow
import net.pedroloureiro.mvflow.MviView
import net.pedroloureiro.mvflow.Reducer
import kotlin.random.Random

object CounterMVFlow {
    data class State(
        val value: Int = 0,
        val backgroundOperations: Int = 0
    ) {
        val showProgress get() = backgroundOperations > 0
    }

    sealed class Action {
        object AddOne : Action()
        object AddMany : Action()
        object Reset : Action()
    }

    sealed class Mutation {
        data class Increment(val amount: Int) : Mutation()
        object BackgroundJobStarted : Mutation()
        object BackgroundJobFinished : Mutation()
        object Reset : Mutation()
    }

    // this interface just exists for a nicer syntax, it's not required
    interface View : MviView<State, Action>

    val handler: Handler<State, Action, Mutation> = { _, action ->
        when (action) {
            Action.AddOne -> flowOf(Mutation.Increment(1))
            Action.Reset -> flowOf(Mutation.Reset)
            Action.AddMany -> flow {
                emit(Mutation.BackgroundJobStarted)
                // pretend that we will start some work
                delay(Random.nextLong(50, 500))
                emit(Mutation.Increment(1))
                delay(Random.nextLong(500, 1000))
                emit(Mutation.Increment(2))
                delay(Random.nextLong(1500, 4000))
                emit(Mutation.Increment(1))
                emit(Mutation.BackgroundJobFinished)
            }
        }
    }

    val reducer: Reducer<State, Mutation> = { state, mutation ->
        when (mutation) {
            is Mutation.Increment -> state.copy(value = state.value + mutation.amount)
            Mutation.BackgroundJobStarted -> state.copy(backgroundOperations = state.backgroundOperations + 1)
            Mutation.BackgroundJobFinished -> state.copy(backgroundOperations = state.backgroundOperations - 1)
            Mutation.Reset -> {
                // we still have the "background operations" going on so we don't change that value
                state.copy(value = 0)
            }
        }
    }

    fun create(
        initialState: State = State(),
        coroutineScope: CoroutineScope
    ) = MVFlow(
        initialState,
        handler,
        reducer,
        coroutineScope,
        { Log.d("MYAPP", it)}
    )
}
