package net.pedroloureiro.mvflow.samples.android.screens.lifecycle

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.transform
import net.pedroloureiro.mvflow.Handler
import net.pedroloureiro.mvflow.MVFlow
import net.pedroloureiro.mvflow.Reducer

object LifecycleMVFlow {
    data class State(
        val counter: Int = 0
    )

    sealed class Action {
        object StartCounter : Action()
    }

    sealed class Mutation {
        object Tick : Mutation()
    }

    /*
        Note: this is not the normal way to use this library. This is just a contrived example to show the difference
        between launch and launchWhenResumed (and other similar methods)
    */

    fun createHandler(): Handler<State, Action, Mutation> =
        { _, action ->
            when (action) {
                Action.StartCounter ->
                    tickerBroadcastChannel
                        .openSubscription()
                        .consumeAsFlow()
                        .transform {
                            emit(Mutation.Tick)
                        }
            }
        }

    @OptIn(ObsoleteCoroutinesApi::class)
    private val tickerBroadcastChannel =
        ticker(
            delayMillis = 1000,
            initialDelayMillis = 0
        ).broadcast(Channel.CONFLATED)

    val reducer: Reducer<State, Mutation> = { state, mutation ->
        when (mutation) {
            Mutation.Tick -> state.copy(counter = state.counter + 1)
        }
    }

    fun create(coroutineScope: CoroutineScope) =
        MVFlow(
            State(),
            createHandler(),
            reducer,
            coroutineScope,
            defaultLogger = { Log.d("MYAPP", it) }
        )
}
