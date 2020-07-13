package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal object MVFlowCounterHelper {
    fun createFlowAndView(
        actionsFlow: Flow<Action>,
        coroutineScope: CoroutineScope
    ) =
        createMVFlow(
            coroutineScope
        ) to createViewFake(actionsFlow, coroutineScope)

    fun createViewFake(
        actionsFlow: Flow<Action>,
        coroutineScope: CoroutineScope
    ): ViewFake<State, Action> {
        return ViewFake<State, Action>(
            actionsFlow,
            coroutineScope
        )
    }

    fun createMVFlow(
        mvflowCoroutineScope: CoroutineScope
    ) =
        MVFlow<State, Action, Mutation>(
            State(0),
            { _, action ->
                when (action) {
                    Action.Action1 -> flowOf(
                        Mutation.Increment(1)
                    )
                    Action.Action2 -> flowOf(
                        Mutation.Multiply(2),
                        Mutation.Increment(-1)
                    )
                }
            },
            { state, mutation ->
                when (mutation) {
                    is Mutation.Increment -> State(state.counter + mutation.amount)
                    is Mutation.Multiply -> State(state.counter * mutation.amount)
                }
            },
            mvflowCoroutineScope,
            actionCoroutineContext = mvflowCoroutineScope.coroutineContext
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
