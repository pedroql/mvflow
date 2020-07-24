package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
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

/**
 * Handler is a function that receives the current state and an action that just happened and acts on it.
 *
 * It returns a [kotlinx.coroutines.flow.Flow] of Mutation which is the way it can mutate the current state if it needs
 * to.
 *
 * Note: implementations should return straight away and do any operations inside the flow.
 *
 * Keep also in mind that Mutations should indicate how to change the state, but should not rely on/assume what the
 * current state is (as of when the action was emitted)
 *
 * @sample MVFlowSamples.handler
 *
 * @sample MVFlowSamples.mutations
 */
typealias Handler<State, Action, Mutation> = (State, Action) -> Flow<Mutation>

/**
 * Reducer applies a Mutation to a State, returning the resulting State.
 *
 * Reducer invocations can't run in parallel so make sure this method returns quickly. It should only contain
 * very simple logic to apply the mutation.
 *
 * @sample MVFlowSamples.reducer
 */
typealias Reducer<State, Mutation> = (State, Mutation) -> State

/**
 * Logger is a lambda you can provide to allow you to do some debugging using your favourite approach for it.
 *
 * You will receive information about when flows are started, completed, and emissions that are taking place (actions,
 * mutations) as well as reducer invocations.
 */
typealias Logger = (String) -> Unit

/**
 * An interface for a View used in MVFlow
 *
 * @param State a class that defines what this class shows
 * @param Action a class, usually a sealed class, that contains the information about all the interactions that can
 * happen inside this view.
 */
interface MviView<State, Action> {
    /**
     * Function that renders the UI based on [state]
     */
    fun render(state: State)

    /**
     * Function to return a [kotlinx.coroutines.flow.Flow] of the actions that the user makes inside this view
     *
     * You can create this in many ways, one suggestion is using
     * [FlowBinding](https://github.com/ReactiveCircus/FlowBinding) or doing it yourself like in this sample:
     *
     * @sample MVFlowSamples.flow
     */
    fun actions(): Flow<Action>
}

/**
 * Class that runs all the MVI logic of this library.
 *
 * @param State a class that holds all information about the current state represented in this MVFlow object.
 * @param Action a class that represents all the interactions that can happen inside this view and associated
 * information.
 * @param Mutation a class that contains the instructions required to mutate the current state to a new state.
 */
class MVFlow<State, Action, Mutation>(
    initialState: State,
    private val handler: Handler<State, Action, Mutation>,
    private val reducer: Reducer<State, Mutation>,
    private val mvflowCoroutineScope: CoroutineScope,
    private val defaultLogger: Logger = {}
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

    /**
     * Call this method when a new [MviView] is ready to render the state of this MVFlow object.
     *
     * @param viewCoroutineScope the scope of the view. This will be used to launch a coroutine which will run listening
     * to actions until this scope is cancelled.
     * @param view the view that will render the state.
     * @param initialActions an optional list of Actions that can be passed to introduce an initial action into the
     * screen (for example, to trigger a refresh of data).
     * @param logger Optional [Logger] to log events inside this MVFlow object associated with this view (but not
     * others).
     */
    fun takeView(
        viewCoroutineScope: CoroutineScope,
        view: MviView<State, Action>,
        initialActions: List<Action> = emptyList(),
        logger: Logger = defaultLogger
    ) {
        viewCoroutineScope.launch {
            sendStateUpdatesIntoView(this, view, logger)
            handleViewActions(this, view, initialActions, logger)
        }
    }

    /**
     * This method adds an external source of actions into the MVFlow object.
     *
     * This might be useful if you need to update your state based on things happening outside the [MviView], such as
     * timers, external database updates, push notifications, etc.
     *
     * @param actions the flow of events. You might want to have a look at
     * [kotlinx.coroutines.flow.callbackFlow].
     * @param logger Optional [Logger] to log events inside this MVFlow object associated with this external Flow (but
     * not others).
     */
    fun addExternalActions(
        actions: Flow<Action>,
        logger: Logger = defaultLogger
    ) {
        mvflowCoroutineScope.launch {
            actions.collectIntoHandler(this, logger)
        }
    }

    private fun sendStateUpdatesIntoView(
        callerCoroutineScope: CoroutineScope,
        view: MviView<State, Action>,
        logger: Logger
    ) {
        state
            .onStart {
                logger.invoke("State flow started")
            }
            .onCompletion {
                logger.invoke("State flow completed")
            }
            .onEach {
                logger.invoke("New state: $it")
                view.render(it)
                // only notify the listeners after the view renders the state
                stateBroadcastChannel.offer(it)
            }
            .launchIn(callerCoroutineScope)
    }

    private fun handleViewActions(
        coroutineScope: CoroutineScope,
        view: MviView<State, Action>,
        initialActions: List<Action>,
        logger: Logger
    ) {
        coroutineScope.launch {
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
        callerCoroutineScope: CoroutineScope,
        logger: Logger
    ) {
        onEach { action ->
            callerCoroutineScope.launch {
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
                    .launchIn(mvflowCoroutineScope)
            }
        }
            .launchIn(callerCoroutineScope)
    }
}
