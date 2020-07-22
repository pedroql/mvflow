package net.pedroloureiro.mvflow.samples.android.screens.lifecycle

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import net.pedroloureiro.mvflow.Handler
import net.pedroloureiro.mvflow.MVFlow
import net.pedroloureiro.mvflow.Reducer

object LifecycleMVFlow {
    data class State(
        val normalCounter: Int = 0,
        val startedCounter: Int = 0,
        val resumedCounter: Int = 0,
        val timersRunning: Boolean = false,
        val delayedToggleWaiting: Boolean = false
    )

    sealed class Action {
        data class SetTimers(val running: Boolean) : Action()
        object ToggleTimersDelayed : Action()
        object OpenDialog : Action()
        object ResetTimers : Action()
        object TickNormal : Action()
        object TickStarted : Action()
        object TickResumed : Action()
    }

    sealed class Mutation {
        data class SetTimersRunning(val running: Boolean) : Mutation()
        data class DelayedToggleWaiting(val waiting: Boolean) : Mutation()

        /**
         * A tick, meant to increment the timers
         */
        object TickNormal : Mutation()
        object TickStarted : Mutation()
        object TickResumed : Mutation()

        object ResetTimers : Mutation()
        object ToggleTimers : Mutation()
    }

    val handler: Handler<State, Action, Mutation> = { _, action ->
        when (action) {
            Action.OpenDialog -> {
                // no op - we will listen externally to this and act there
                emptyFlow()
            }

            is Action.SetTimers -> flowOf(Mutation.SetTimersRunning(action.running))
            Action.ToggleTimersDelayed -> flow {
                emit(Mutation.DelayedToggleWaiting(true))
                delay(5000)
                emit(Mutation.ToggleTimers)
                emit(Mutation.DelayedToggleWaiting(false))
            }

            Action.ResetTimers -> flowOf(Mutation.ResetTimers)

            Action.TickNormal -> flowOf(Mutation.TickNormal)
            Action.TickStarted -> flowOf(Mutation.TickStarted)
            Action.TickResumed -> flowOf(Mutation.TickResumed)
        }
    }

    val reducer: Reducer<State, Mutation> = { state, mutation ->
        when (mutation) {
            is Mutation.SetTimersRunning -> state.copy(timersRunning = mutation.running)
            is Mutation.DelayedToggleWaiting -> state.copy(delayedToggleWaiting = mutation.waiting)
            Mutation.TickNormal -> state.copy(normalCounter = state.normalCounter + 1)
            Mutation.TickStarted -> state.copy(startedCounter = state.startedCounter + 1)
            Mutation.TickResumed -> state.copy(resumedCounter = state.resumedCounter + 1)
            Mutation.ResetTimers -> state.copy(normalCounter = 0, startedCounter = 0, resumedCounter = 0)
            Mutation.ToggleTimers -> state.copy(timersRunning = state.timersRunning.not())
        }
    }

    fun create(coroutineScope: CoroutineScope) =
        MVFlow(
            State(),
            handler,
            reducer,
            coroutineScope,
            defaultLogger = { Log.d("MVFLOW", it) }
        )
}
