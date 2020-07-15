package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch

class ViewFake<State, Action>(
    actionsFlow: Flow<Action>,
    coroutineScope: CoroutineScope
) {
    val states = mutableListOf<State>()
    private lateinit var collectionJob: Job
    val view: MviView<State, Action>

    init {
        view = object : MviView<State, Action> {
            override fun render(state: State) {
                // no real rendering happening
                throw IllegalStateException("Should never be called")
            }

            override fun actions() = actionsFlow

            override val coroutineScope = coroutineScope

            override fun receiveStates(stateProducerBlock: () -> Flow<State>) {
                collectionJob = coroutineScope.launch {
                    stateProducerBlock().toList(states)
                }
            }
        }
    }

    fun cancelCollection() = collectionJob.cancel()
}
