package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

typealias Handler<State, Action, Mutation> = (State, Action) -> Flow<Mutation>
typealias Reducer<State, Mutation> = (State, Mutation) -> State
typealias Logger = (String) -> Unit

interface MviView<State, Action> {
    fun render(state: State)

    fun actions(): Flow<Action>

    val coroutineScope: CoroutineScope

    // the following method could have a better name (and maybe doesn't need to exist here)
    fun receiveStates(stateProducerBlock: () -> Flow<State>) {
        // make sure the test in [MVFlowTest] is in sync with this implementation
        coroutineScope.launch(stateDispatcher) {
            stateProducerBlock().collect { state ->
                render(state)
            }
        }
    }

    val stateDispatcher: CoroutineDispatcher
        get() = Dispatchers.Main
}

@ExperimentalCoroutinesApi
class MVFlow<State, Action, Mutation>(
    initialState: State,
    private val handler: Handler<State, Action, Mutation>,
    private val reducer: Reducer<State, Mutation>,
    private val mvflowCoroutineScope: CoroutineScope,
    private val defaultLogger: Logger = {},
    private val actionCoroutineContext: CoroutineContext = Dispatchers.Default
) {

    private val state = MutableStateFlow(initialState)
    private val mutex = Mutex()

    fun takeView(
        view: MviView<State, Action>,
        initialActions: List<Action> = emptyList(),
        logger: Logger = defaultLogger
    ) {
        sendStateUpdatesIntoView(view, logger)
        handleViewActions(view, initialActions, logger)
    }

    fun addExternalActions(
        actions: Flow<Action>,
        logger: Logger = defaultLogger
    ) {
        mvflowCoroutineScope.launch {
            actions.collectIntoHandler(mvflowCoroutineScope, logger)
        }
    }

    private fun sendStateUpdatesIntoView(
        view: MviView<State, Action>,
        logger: Logger
    ) {
        view.receiveStates {
            state
                .onStart {
                    logger.invoke("State flow started")
                }
                .onCompletion {
                    logger.invoke("State flow completed")
                }
                .onEach {
                    logger.invoke("New state: $it")
                }
        }
    }

    private fun handleViewActions(
        view: MviView<State, Action>,
        initialActions: List<Action>,
        logger: Logger
    ) {
        view.coroutineScope.launch(actionCoroutineContext) {
            view
                .actions()
                .onStart {
                    logger.invoke("View actions flow started")
                    emitAll(initialActions.asFlow())
                }
                .onCompletion {
                    logger.invoke("View actions flow completed")
                }
                .collectIntoHandler(this, logger)
        }
    }

    private suspend fun Flow<Action>.collectIntoHandler(
        coroutineScope: CoroutineScope,
        logger: Logger
    ) {
        collect { action ->
            logger.invoke("Received action $action")
            handler.invoke(state.value, action)
                .onEach { mutation ->
                    mutex.withLock {
                        logger.invoke("Applying mutation $mutation from action $action")
                        state.value = reducer.invoke(state.value, mutation)
                    }
                }
                .launchIn(coroutineScope)
        }
    }
}
