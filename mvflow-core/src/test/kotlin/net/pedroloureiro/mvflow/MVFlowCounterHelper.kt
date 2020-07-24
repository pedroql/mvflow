package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach

/**
 * This class makes it easier to create a mvflow and view of a simple counter
 */
internal object MVFlowCounterHelper {
    fun createFlowAndView(
        actionsFlow: Flow<Action>,
        coroutineScope: CoroutineScope,
        delayMutations: Boolean = true,
        printLogs: Boolean = false
    ) =
        createMVFlow(
            coroutineScope,
            delayMutations,
            printLogs
        ) to createViewFake(actionsFlow)

    fun createViewFake(
        actionsFlow: Flow<Action>
    ): ViewFake<State, Action> {
        return ViewFake(
            actionsFlow
        )
    }

    fun createMVFlow(
        mvflowCoroutineScope: CoroutineScope,
        delayMutations: Boolean = true,
        printLogs: Boolean = false
    ) =
        MVFlow<State, Action, Mutation>(
            State(0),
            { _, action ->
                when (action) {
                    Action.Action1 ->
                        flowOf(
                            Mutation.Increment(1)
                        )
                            .onEach {
                                if (delayMutations) {
                                    delay(50)
                                }
                            }
                    Action.Action2 ->
                        flowOf(
                            Mutation.Multiply(2),
                            Mutation.Increment(-1)
                        )
                            .onEach {
                                if (delayMutations) {
                                    delay(40)
                                }
                            }
                }
            },
            { state, mutation ->
                when (mutation) {
                    is Mutation.Increment -> State(state.counter + mutation.amount)
                    is Mutation.Multiply -> State(state.counter * mutation.amount)
                }
            },
            mvflowCoroutineScope,
            defaultLogger = if (printLogs) { msg -> println(msg); Unit } else {
                { _ -> }
            }
        )

    sealed class Action {
        object Action1 : Action()
        object Action2 : Action()
    }

    data class State(val counter: Int)

    sealed class Mutation {
        data class Increment(val amount: Int) : Mutation()
        data class Multiply(val amount: Int) : Mutation()
    }
}
