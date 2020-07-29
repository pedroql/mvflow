package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runBlockingTest
import net.pedroloureiro.mvflow.MVFlowCounterHelper.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// These tests are long, contrived, and hard to read. Suggestions to improve them are most welcome!

internal class MVFlowTest {

    @Test
    fun `reducer calls are serialized`() {
        // In this test we create a flow and a view. We made the reducer function be very slow and
        // emit several actions quickly. If we don't synchronize access to the reducer function,
        // the same initial state would be fed to several mutations and the last one to complete
        // would win, overriding the other mutations that overlapped with it.
        //
        // in order to verify this test works, remove the use of the mutex in the implementation
        // and the test should fail
        runBlockingTest {
            val viewScope = CoroutineScope(coroutineContext + Job())

            val flow = MVFlow<IntState, Unit, Mutation>(
                initialState = 0,
                handler = { _, _ ->
                    flowOf(1, 2, 3)
                        .onEach { delay(20) }
                },
                reducer = slowReducer(),
                mvflowCoroutineScope = CoroutineScope(coroutineContext + Job()),
                defaultLogger = ::debug
            )

            val viewFake = ViewFake<IntState, Unit>(
                List(3) { Unit }.asFlow()
                    .onEach { delay(20) }
                    .buffer()
            )

            viewScope.launch {
                flow.takeView(this, viewFake.view)
            }
            advanceUntilIdle()
            viewScope.cancel()
            assertEquals(18, viewFake.states.last())
        }
    }

    private fun slowReducer(): (IntState, Mutation) -> Int {
        return { state, mutation ->
            runBlockingTest {
                debug("reducer delaying")
                delay(500)
                debug("reducer done")
            }
            state + mutation
        }
    }

    // This test is not testing the library itself, it's a somewhat simpler POC to verify the tests
    // in `reducer calls are serialized` work. Toggle between using or not the mutex and you'll see
    // that the calls are only synchronized if you use the mutex.
    @Test
    fun `meta reducer calls are serialized`() {
        var lastValue = -1
        runBlockingTest {
            val mutex = Mutex()
            val stateFlow = MutableStateFlow(0)
            val reducer = { state: Int, amount: Int ->
                runBlockingTest {
                    debug("reducer: delay")
                    delay(100)
                    debug("reducer: done")
                }
                state + amount
            }
            val handler = { _: Int, _: Unit ->
                flowOf(1, 1, 2)
                    .onEach { delay(15) }
                    .onEach { debug("Emitting mutation $it") }
            }
            val actions = flowOf(Unit, Unit, Unit, Unit)
                .onEach { delay(10) }
                // buffer is required so that this can emit a second event even before the first one
                // is completely handled. In other words, this allows to process actions in parallel
                .buffer()
                .onEach { debug("Emitting action $it") }

            // usually with view coroutine scope, here using runBlockingTest
            // usually with stateDispatcher (Main) but can be overridden in the MviView interface
            val stateJob = launch {
                stateFlow
                    .onEach { debug("Emitting state $it") }
                    .collect {
                        lastValue = it
                    }
            }

            // usually with view coroutine scope, here using runBlockingTest
            // usually with actionDispatcher (default) but can be overridden in the constructor
            launch {
                actions
                    .collect { action ->
                        handler.invoke(stateFlow.value, action)
                            .onEach {
                                mutex.withLock {
                                    stateFlow.value = reducer.invoke(stateFlow.value, it)
                                }
                            }
                            // usually with the mvflow scope
                            .launchIn(this)
                    }
            }
            // state flow never terminates so we need to let the flows progress and then terminate the
            // state job. I find this preferable to hard-coding a number of events
            // (stateFlow.take(123).collect{...}) as that can run out of sync with the tests
            advanceUntilIdle()
            stateJob.cancelAndJoin()
        }

        assertEquals(16, lastValue)
    }

    @Test
    fun `handler calls are concurrent`() {
        // This method simulates a handler that takes much longer to emit something from the first
        // action and emits something quickly from the second action.
        // The state is a Pair<Int, Int>. We will count the first and second elements of the pair
        // separately, but we insert a bigger delay when counting the pair.first. This means the
        // expected sequence of values is (0, 0) -> (0, 1) -> (1, 1) although we try to increment
        // the first element first.
        data class Action(val value: Int)

        val values = mutableListOf<PairState>()
        runBlockingTest {

            val flow = MVFlow<PairState, Action, Mutation>(
                initialState = Pair(0, 0),
                handler = { _, action ->
                    flowOf(action.value).onStart {
                        debug("handler delaying ${action.value} at $currentTime")
                        if (action.value == 0) {
                            delay(500)
                        } else {
                            delay(50)
                        }
                        debug("handler done for action ${action.value} at $currentTime")
                    }
                },
                reducer = { state, mutation ->
                    if (mutation == 0) {
                        state.copy(first = state.first + 1)
                    } else {
                        state.copy(second = state.second + 1)
                    }
                },
                mvflowCoroutineScope = this,
                defaultLogger = { debug(it) }
            )

            val view = object : MVFlow.View<Pair<Int, Int>, Action> {
                override fun render(state: Pair<Int, Int>) {
                    values.add(state)
                    debug("state at $currentTime")
                }

                override fun actions() = flowOf(Action(0), Action(1))
                    .onEach { delay(20) }
                    .buffer()
            }

            val viewScope = CoroutineScope(coroutineContext + Job())
            viewScope.launch {
                flow.takeView(this, view)
            }
            advanceUntilIdle()
            viewScope.cancel()
        }
        assertEquals(
            listOf(
                Pair(0, 0),
                Pair(0, 1),
                Pair(1, 1)
            ),
            values
        )
    }

    @Test
    fun `when the view scope finishes, the handling of previously emitted actions continues`() = runBlockingTest {
        val flow = MVFlowCounterHelper.createMVFlow(
            this,
            printLogs = true
        )

        val viewScope1 = CoroutineScope(coroutineContext + Job())
        val viewFake1 = MVFlowCounterHelper.createViewFake(
            flow {
                emit(Action.Action1)
                delay(5)
                emit(Action.Action1)
                delay(20)
                emit(Action.Action2)
            }
        )

        val viewFake2 = MVFlowCounterHelper.createViewFake(emptyFlow())

        viewScope1.launch {
            flow.takeView(this, viewFake1.view, logger = { println("View1: $it") })
        }

        advanceTimeBy(30)
        viewScope1.cancel()
        assertEquals(listOf(MVFlowCounterHelper.State(0)), viewFake1.states)

        val viewScope2 = CoroutineScope(coroutineContext + Job())

        // with this advance, the view will miss some updates
        advanceTimeBy(40)
        viewScope2.launch {
            flow.takeView(this, viewFake2.view, logger = { println("View2: $it") })
        }
        advanceUntilIdle()
        assertEquals(
            listOf(
                MVFlowCounterHelper.State(4),
                MVFlowCounterHelper.State(3)
            ),
            viewFake2.states
        )
        viewScope2.cancel()
    }

    @Test
    fun `simple takeView2 test`() = runBlockingTest {
        val flow = MVFlowCounterHelper.createMVFlow(
            this,
            printLogs = true
        )

        val viewScope1 = this
        val viewFake1 = MVFlowCounterHelper.createViewFake(
            flow {
                emit(Action.Action1)
                delay(5)
                emit(Action.Action1)
                delay(20)
                emit(Action.Action2)
            }
        )
        val viewJob = viewScope1.launch {
            flow.takeView(this, viewFake1.view)
        }
        advanceUntilIdle()
        assertEquals(
            listOf(
                MVFlowCounterHelper.State(0),
                MVFlowCounterHelper.State(1),
                MVFlowCounterHelper.State(2),
                MVFlowCounterHelper.State(4),
                MVFlowCounterHelper.State(3)
            ),
            viewFake1.states
        )
        viewJob.cancel()
    }
}

private fun debug(msg: String) {
    println(msg)
}

private typealias IntState = Int
private typealias PairState = Pair<Int, Int>
private typealias Mutation = Int
