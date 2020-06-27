package net.pedroloureiro.mvflow

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class MVFlowTest {

    @BeforeEach
    fun setUp() {
    }

    @Test
    fun `reducer calls are serialized`() {
        // in order to verify this test works, remove the use of the mutex in the implementation
        var lastCounterValue = -1
        runBlockingTest {
            lateinit var collectionJob: Job
            val flow = MVFlow<State, Unit, Mutation>(
                initialState = 0,
                handler = { _, _ ->
                    flowOf(1, 2, 3)
                        .onEach { delay(20) }
                },
                reducer = { state, mutation ->
                    runBlockingTest {
                        println("reducer delaying")
                        delay(500)
                        println("reducer done")
                    }
                    state + mutation
                },
                mvflowCoroutineScope = this,
                defaultLogger = { debug(it) },
                actionCoroutineContext = this.coroutineContext
            )

            val view = object : MviView<State, Unit> {
                override fun render(state: State) {
                    lastCounterValue = state
                    println("state at $currentTime")
                }

                override fun actions() = List(3) { Unit }.asFlow()
                    .onEach { delay(20) }
                    .buffer()

                override val coroutineScope: CoroutineScope
                    get() = this@runBlockingTest

                // we need to override this because we need to terminate the job that does the
                // collection
                override fun receiveStates(stateProducerBlock: () -> Flow<State>) {
                    // make sure this implementation stays up to date with the default definition
                    collectionJob = coroutineScope.launch {
                        stateProducerBlock().collect { state ->
                            render(state)
                        }
                    }
                }
            }

            flow.takeView(view)
            advanceUntilIdle()
            collectionJob.cancelAndJoin()
            debug("Time at end: $currentTime")
        }
        assertEquals(18, lastCounterValue)
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
    }
}

private fun debug(msg: String) {
    println(msg)
}
private typealias State = Int
private typealias Mutation = Int
