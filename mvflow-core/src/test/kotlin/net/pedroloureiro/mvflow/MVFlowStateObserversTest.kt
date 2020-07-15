package net.pedroloureiro.mvflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import net.pedroloureiro.mvflow.MVFlowCounterHelper.Action.Action1
import net.pedroloureiro.mvflow.MVFlowCounterHelper.Action.Action2
import net.pedroloureiro.mvflow.MVFlowCounterHelper.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MVFlowStateObserversTest {
    private val normalStateSequence = listOf(
        State(0),
        State(1),
        State(2),
        State(1),
        State(2),
        State(3),
        State(6),
        State(5)
    )

    @Test
    fun `state observer is notified about updates`() = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob: Job
        val observed = mutableListOf<State>()
        runTestTemplate(template) {
            observerJob = launch {
                mvflow.observeState().toList(observed)
            }
        }

        observerJob.cancel()
        assertEquals(
            normalStateSequence,
            observed
        )
    }

    private fun TestCoroutineScope.defaultMvflow(): MVFlowCounterTestTemplate {
        return MVFlowCounterTestTemplate(
            this,
            viewActions = flowOf(Action1, Action1, Action1)
                .onEach { delay(50) },
            externalActions = flowOf(Action2, Action2)
                .onEach { delay(76) },
            delayMutations = false
        )
    }

    @Test
    fun `able to observe midway through the state events`() = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob: Job
        val observed = mutableListOf<State>()

        runTestTemplate(template) {
            observerJob = launch {
                // should miss one action from the view and another from the external actions
                delay(90)
                mvflow.observeState().toList(observed)
            }
        }
        observerJob.cancel()
        assertEquals(
            normalStateSequence.drop(3),
            observed
        )
    }

    @Test
    fun `two observers can start observing state at different points`() = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob1: Job
        lateinit var observerJob2: Job
        val observed1 = mutableListOf<State>()
        val observed2 = mutableListOf<State>()

        runTestTemplate(template) {
            observerJob1 = launch {
                mvflow.observeState().toList(observed1)
            }
            observerJob2 = launch {
                // should miss one action from the view and another from the external actions
                delay(90)
                mvflow.observeState().toList(observed2)
            }
        }
        observerJob1.cancel()
        observerJob2.cancel()
        assertEquals(
            normalStateSequence,
            observed1
        )
        assertEquals(
            normalStateSequence.drop(3),
            observed2
        )
    }

    @Test
    fun `slow observers do not affect each other`() = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob1: Job
        lateinit var observerJob2: Job
        val observed1 = mutableListOf<State>()
        val observed2 = mutableListOf<State>()

        runTestTemplate(template) {
            observerJob1 = launch {
                template.mvflow.observeState().onEach { delay(500) }.toList(observed1)
            }
            observerJob2 = launch {
                template.mvflow.observeState().toList(observed2)
            }

            advanceTimeBy(400)
            // at this point, the view and the fast observer should have received all updates
            assertEquals(emptyList<State>(), observed1)
            assertEquals(normalStateSequence, template.viewFake.states)
            assertEquals(normalStateSequence, observed2)
        }

        println("before cancelling observers - $currentTime")
        observerJob1.cancel()
        observerJob2.cancel()
        // at this point the slow observer should have received the first and last state updates. (remaining are missed
        // because of how long it took to handle the first event
        assertEquals(normalStateSequence, template.viewFake.states)
        assertEquals(
            listOf(
                normalStateSequence.first(),
                normalStateSequence.last()
            ),
            observed1
        )
        assertEquals(normalStateSequence, observed2)
    }

    @Test
    fun `observer can unsubscribe without affecting others`() = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob1: Job
        lateinit var observerJob2: Job
        val observed1 = mutableListOf<State>()
        val observed2 = mutableListOf<State>()

        runTestTemplate(template) {
            observerJob1 = launch {
                mvflow.observeState().toList(observed1)
            }
            observerJob2 = launch {
                mvflow.observeState().toList(observed2)
            }
            launch {
                delay(90)
                observerJob2.cancel()
            }
        }

        observerJob1.cancel()
        assertEquals(normalStateSequence, observed1)
        assertEquals(normalStateSequence.take(4), observed2)
    }

    @Test
    fun `a slow observer doesn't impact the view receiving fresh states`() = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob: Job
        val observed = mutableListOf<State>()
        runTestTemplate(template) {
            observerJob = launch {
                mvflow.observeState().onEach { delay(300) }.toList(observed)
            }

            advanceTimeBy(200)
            // this should be enough time to process everything
            assertEquals(
                normalStateSequence,
                template.viewFake.states
            )
            assertEquals(
                emptyList<State>(),
                observed
            )
        }

        observerJob.cancel()
        assertEquals(
            normalStateSequence,
            template.viewFake.states
        )
        // observer is so slow it should only see the first and last states.
        assertEquals(
            listOf(
                normalStateSequence.first(),
                normalStateSequence.last()
            ),
            observed
        )
    }

    @Test
    fun `late observer still receives latest state`()  = runBlockingTest {
        val template = defaultMvflow()

        lateinit var observerJob: Job
        val observed = mutableListOf<State>()
        runTestTemplate(template) {
            observerJob = launch {
                delay(500)
                mvflow.observeState().toList(observed)
            }
            advanceTimeBy(200)
            // meanwhile the view got updates
            assertEquals(
                normalStateSequence,
                template.viewFake.states
            )
        }

        observerJob.cancel()
        assertEquals(
            listOf(normalStateSequence.last()),
            observed
        )
    }
}
