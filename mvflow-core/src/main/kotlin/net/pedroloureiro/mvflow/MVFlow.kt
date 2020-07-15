package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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

class MVFlow<State, Action, Mutation>(
    initialState: State,
    private val handler: Handler<State, Action, Mutation>,
    private val reducer: Reducer<State, Mutation>,
    private val mvflowCoroutineScope: CoroutineScope,
    private val defaultLogger: Logger = {},
    private val actionCoroutineContext: CoroutineContext = Dispatchers.Default
) {
    private val state = MutableStateFlow(initialState)

    /**
     * stateBroadcastChannel is [kotlinx.coroutines.channels.Channel.Factory.CONFLATED] so it exhibits the same
     * behaviour (receives the same events) that the view would receive.
     */
    private val stateBroadcastChannel = BroadcastChannel<State>(Channel.CONFLATED)
    private val actionBroadcastChannel = BroadcastChannel<Pair<Action, State>>(Channel.BUFFERED)
    private val mutationBroadcastChannel = BroadcastChannel<Mutation>(Channel.BUFFERED)
    private val mutex = Mutex()

    private fun consumeActionFlow() = actionBroadcastChannel.openSubscription().consumeAsFlow()

    /**
     * Observe actions taking place in this MVFlow object.
     *
     * This is just a utility method that *you probably don't need*. Be mindful what you do with it. While this might
     * make some functionality easier to implement, you are risking breaking the MVI concept and its advantages!
     */
    fun observeActions() = consumeActionFlow().map { it.first }

    /**
     * Observe actions taking place in this MVFlow object together with the current state (as of when the action was
     * handled).
     *
     * This is just a utility method that *you probably don't need*. Be mindful what you do with it. While this might
     * make some functionality easier to implement, you are risking breaking the MVI concept and its advantages!
     *
     */
    fun observeActionsWithState() = consumeActionFlow()

    /**
     * Observe mutations taking place in this MVFlow object.
     *
     * This is just a utility method that *you probably don't need*. Be mindful what you do with it. While this might
     * make some functionality easier to implement, you are risking breaking the MVI concept and its advantages!
     *
     * Note: the observer is informed just after the mutation is applied in the current state
     */
    fun observeMutations() = mutationBroadcastChannel.openSubscription().consumeAsFlow()

    /**
     * Observe the current state of this MVFlow object. When you subscribe, you also get the current value as the first
     * emission.
     *
     * This is just a utility method that *you probably don't need*. Be mindful what you do with it. While this might
     * make some functionality easier to implement, you are risking breaking the MVI concept and its advantages!
     *
     * Note: this uses a [kotlinx.coroutines.channels.Channel.Factory.CONFLATED] channel so the states you see might
     * differ from the events the MVFlow object sees. If your observer or the view take too long collecting one value
     * and two or more values are emitted during that time, only the latest one would be emitted to the slow collector
     * (while the fast collecter could receive all of the values). In normal situations the collection should be very
     * quick and skipping states should not matter due to immutability.
     */
    fun observeState() = stateBroadcastChannel.openSubscription().consumeAsFlow()

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
                .onEach {
                    stateBroadcastChannel.offer(it)
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
            val currentValue = state.value
            actionBroadcastChannel.offer(action to currentValue)
            handler.invoke(currentValue, action)
                .onEach { mutation ->
                    mutex.withLock {
                        logger.invoke("Applying mutation $mutation from action $action")
                        state.value = reducer.invoke(state.value, mutation)
                    }
                    mutationBroadcastChannel.offer(mutation)
                }
                .launchIn(coroutineScope)
        }
    }
}
