package net.pedroloureiro.mvflow

import kotlinx.coroutines.flow.Flow

class ViewFake<State, Action>(
    actionsFlow: Flow<Action>
) {
    val states = mutableListOf<State>()
    val view: MVFlow.View<State, Action>

    init {
        view = object : MVFlow.View<State, Action> {
            override fun render(state: State) {
                // accumulate all the received states in this property
                states.add(state)
            }

            override fun actions() = actionsFlow
        }
    }
}
