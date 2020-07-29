@file:Suppress("FunctionName")

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
 * current state is (as of when the action was emitted).
 *
 * @sample MVFlowSamples.handler
 *
 * @sample MVFlowSamples.mutations
 */
typealias Handler<State, Action, Mutation> = (State, Action) -> Flow<Mutation>

/**
 * A function that on top of what [Handler] does, it also might emit side effects that are subscribed by an external
 * component.
 *
 * @sample MVFlowSamples.handlerWithEffects
 *
 * @see [Handler]
 */
// @formatter:off
typealias HandlerWithEffects<State, Action, Mutation, Effect> =
    (State, Action, EffectSender<Effect>) -> Flow<Mutation>
// @formatter:on

/**
 * Effect sender is an interface that allows you to send effects to be handled outside the handler when you use
 * [HandlerWithEffects].
 */
interface EffectSender<T> {
    /**
     * Send this effect to be handled somewhere externally. It suspends until the effect is received.
     *
     * Implementation detail: this event is sent to a buffered channel. It suspends until the channel is able to receive
     * this value (in normal situations should be very brief) or throws an exception if the channel reached its buffer
     * capacity. This channel acts as a buffer between the handler that sends effects and potential observers that
     * listen to them.
     *
     * If a handler is sending effects and there are no observers receiving them, this will buffer can eventually fill
     * up and subsequent calls of this method will suspend until one observer starts collecting the effects.
     *
     * @param effect the value to send.
     */
    suspend fun send(effect: T)

    /**
     * Submits this effect to be handled somewhere externally, returning immediately whether it was successful.
     *
     * Implementation detail: this event is sent to a buffered channel.
     *
     * It returns true if the effect was successfully submitted.
     *
     * @param effect the value to send.
     */
    fun offer(effect: T): Boolean
}

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
 * Interface that defines all the MVI logic of this library.
 *
 * @param State a class that holds all information about the current state represented in this MVFlow object.
 * @param Action a class that represents all the interactions that can happen inside this view and associated
 * information.
 */
interface MVFlow<State, Action> {
    /**
     * Call this method when a new [View] is ready to render the state of this MVFlow object.
     *
     * @param viewCoroutineScope the scope of the view. This will be used to launch a coroutine which will run listening
     * to actions until this scope is cancelled.
     * @param view the view that will render the state.
     * @param initialActions an optional list of Actions that can be passed to introduce an initial action into the
     * screen (for example, to trigger a refresh of data).
     * @param logger Optional [Logger] to log events inside this MVFlow object associated with this view (but not
     * others). If null, a default logger might be used.
     */
    fun takeView(
        viewCoroutineScope: CoroutineScope,
        view: View<State, Action>,
        initialActions: List<Action> = emptyList(),
        logger: Logger? = null
    )

    /**
     * This method adds an external source of actions into the MVFlow object.
     *
     * This might be useful if you need to update your state based on things happening outside the [View], such as
     * timers, external database updates, push notifications, etc.
     *
     * @param actions the flow of events. You might want to have a look at
     * [kotlinx.coroutines.flow.callbackFlow].
     * @param logger Optional [Logger] to log events inside this MVFlow object associated with this external Flow (but
     * not others). If null, a default logger might be used.
     */
    fun addExternalActions(
        actions: Flow<Action>,
        logger: Logger? = null
    )

    /**
     * An interface for a View used in MVFlow.
     *
     * @param State a class that defines what this class shows.
     * @param Action a class, usually a sealed class, that contains the information about all the interactions that can
     * happen inside this view.
     */
    interface View<State, Action> {
        /**
         * Function that renders the UI based on [state].
         */
        fun render(state: State)

        /**
         * Function to return a [kotlinx.coroutines.flow.Flow] of the actions that the user makes inside this view.
         *
         * You can create this in many ways, one suggestion is using
         * [FlowBinding](https://github.com/ReactiveCircus/FlowBinding) or doing it yourself like in this sample:
         *
         * @sample MVFlowSamples.flow
         */
        fun actions(): Flow<Action>
    }
}

/**
 * Extension of [MVFlow] to be used together with a [HandlerWithEffects].
 *
 * This interface allows you to observe the external effects.
 *
 * @param State a class that holds all information about the current state represented in this MVFlow object.
 * @param Action a class that represents all the interactions that can happen inside this view and associated
 * information.
 * @param Effect a class that represents things that you might need to handle outside the handler that deals with
 * UI actions. Typically used for navigation and one-off events that do not mutate the state.
 *
 */
interface MVFlowWithEffects<State, Action, Effect> : MVFlow<State, Action> {

    /**
     * Observe external effects emitted by the handler.
     *
     * Note: each time you call this, it will return a different flow. Each flow can only be subscribed once.
     *
     * If you add a second observer
     */
    fun observeEffects(): Flow<Effect>
}

private class MVFlowImpl<State, Action, Mutation, Effect>(
    initialState: State,
    private val handler: HandlerWithEffects<State, Action, Mutation, Effect>,
    private val reducer: Reducer<State, Mutation>,
    private val mvflowCoroutineScope: CoroutineScope,
    private val defaultLogger: Logger = {}
) : MVFlowWithEffects<State, Action, Effect> {
    private val state = MutableStateFlow(initialState)
    private val externalEffectChannel = BroadcastChannel<Effect>(Channel.BUFFERED)
    private val mutex = Mutex()

    private inner class LoggingEffectSender(private val logger: Logger) : EffectSender<Effect> {
        override suspend fun send(effect: Effect) {
            logger.invoke("Sending external effect $effect")
            externalEffectChannel.send(effect)
        }

        override fun offer(effect: Effect): Boolean {
            logger.invoke("Offering external effect $effect")
            return externalEffectChannel.offer(effect).also { accepted ->
                if (!accepted) {
                    logger.invoke("Channel rejected previous effect!")
                }
            }
        }
    }

    override fun observeEffects() = externalEffectChannel.openSubscription().consumeAsFlow()

    override fun takeView(
        viewCoroutineScope: CoroutineScope,
        view: MVFlow.View<State, Action>,
        initialActions: List<Action>,
        logger: Logger?
    ) {
        viewCoroutineScope.launch {
            sendStateUpdatesIntoView(this, view, logger ?: defaultLogger)
            handleViewActions(this, view, initialActions, logger ?: defaultLogger)
        }
    }

    override fun addExternalActions(
        actions: Flow<Action>,
        logger: Logger?
    ) {
        mvflowCoroutineScope.launch {
            actions.collectIntoHandler(this, logger ?: defaultLogger)
        }
    }

    private fun sendStateUpdatesIntoView(
        callerCoroutineScope: CoroutineScope,
        view: MVFlow.View<State, Action>,
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
            }
            .launchIn(callerCoroutineScope)
    }

    private fun handleViewActions(
        coroutineScope: CoroutineScope,
        view: MVFlow.View<State, Action>,
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
                handler.invoke(state.value, action, LoggingEffectSender(logger))
                    .onEach { mutation ->
                        mutex.withLock {
                            logger.invoke("Applying mutation $mutation from action $action")
                            state.value = reducer.invoke(state.value, mutation)
                        }
                    }
                    .launchIn(mvflowCoroutineScope)
            }
        }
            .launchIn(callerCoroutineScope)
    }
}

// @formatter:off
private fun <State, Action, Mutation> Handler<State, Action, Mutation>.asHandlerWithEffects():
    HandlerWithEffects<State, Action, Mutation, Nothing> = { state, action, _ ->
        this(state, action)
    }
// @formatter=on

fun <State, Action, Mutation> MVFlow(
    initialState: State,
    handler: Handler<State, Action, Mutation>,
    reducer: Reducer<State, Mutation>,
    mvflowCoroutineScope: CoroutineScope,
    defaultLogger: Logger = {}
): MVFlow<State, Action> =
    MVFlowImpl(
        initialState,
        handler.asHandlerWithEffects(),
        reducer,
        mvflowCoroutineScope,
        defaultLogger
    )

fun <State, Action, Mutation, Effect> MVFlow(
    initialState: State,
    handler: HandlerWithEffects<State, Action, Mutation, Effect>,
    reducer: Reducer<State, Mutation>,
    mvflowCoroutineScope: CoroutineScope,
    defaultLogger: Logger = {}
): MVFlowWithEffects<State, Action, Effect> =
    MVFlowImpl(
        initialState,
        handler,
        reducer,
        mvflowCoroutineScope,
        defaultLogger
    )
