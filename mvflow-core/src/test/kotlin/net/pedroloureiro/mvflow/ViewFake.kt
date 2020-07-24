package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class ViewFake<State, Action>(
    actionsFlow: Flow<Action>,
    coroutineScope: CoroutineScope
) {
    val states = mutableListOf<State>()
    val view: MviView<State, Action>

    init {
        view = object : MviView<State, Action> {
            override fun render(state: State) {
                // accumulate all the received states in this property
                states.add(state)
            }

            override fun actions() = actionsFlow

            override val coroutineScope = coroutineScope // TODO do I need this?
        }
    }
}
