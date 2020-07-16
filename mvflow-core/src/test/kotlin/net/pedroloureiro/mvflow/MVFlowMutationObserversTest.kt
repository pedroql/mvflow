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
import net.pedroloureiro.mvflow.MVFlowCounterHelper.Mutation
import net.pedroloureiro.mvflow.MVFlowCounterHelper.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MVFlowMutationObserversTest {

    private fun TestCoroutineScope.defaultTemplate(): MVFlowCounterTestTemplate {
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
    fun `mutation observer is notified about mutations`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var observerJob: Job
        val observed = mutableListOf<Mutation>()
        runTestTemplate(template) {
            observerJob = launch {
                mvflow.observeMutations().toList(observed)
            }
        }

        observerJob.cancel()
        assertEquals(
            listOf(
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1),
                Mutation.Increment(1),
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1)
            ),
            observed
        )
    }

    @Test
    fun `able to observe midway through the mutation events`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var observerJob: Job
        val observed = mutableListOf<Mutation>()

        runTestTemplate(template) {
            observerJob = launch {
                // should miss one action from the view and another from the external actions
                delay(90)
                mvflow.observeMutations().toList(observed)
            }
        }
        observerJob.cancel()
        assertEquals(
            listOf(
                Mutation.Increment(1),
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1)
            ),
            observed
        )
    }

    @Test
    fun `two observers can start observing mutations at different points`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var observerJob1: Job
        lateinit var observerJob2: Job
        val observed1 = mutableListOf<Mutation>()
        val observed2 = mutableListOf<Mutation>()

        runTestTemplate(template) {
            observerJob1 = launch {
                mvflow.observeMutations().toList(observed1)
            }
            observerJob2 = launch {
                // should miss one action from the view and another from the external actions
                delay(90)
                mvflow.observeMutations().toList(observed2)
            }
        }
        observerJob1.cancel()
        observerJob2.cancel()
        assertEquals(
            listOf(
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1),
                Mutation.Increment(1),
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1)
            ),
            observed1
        )
        assertEquals(
            listOf(
                Mutation.Increment(1),
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1)
            ),
            observed2
        )
    }

    @Test
    fun `slow observers do not affect each others`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var observerJob1: Job
        lateinit var observerJob2: Job
        val observed1 = mutableListOf<Mutation>()
        val observed2 = mutableListOf<Mutation>()

        runTestTemplate(template) {
            observerJob1 = launch {
                mvflow.observeMutations().onEach { delay(500) }.toList(observed1)
            }
            observerJob2 = launch {
                mvflow.observeMutations().toList(observed2)
            }

            advanceTimeBy(400)
            assertEquals(0, observed1.size)
            assertEquals(7, observed2.size)
        }

        observerJob1.cancel()
        observerJob2.cancel()
        // at this point both observers should have observed all the events
        assertEquals(7, observed1.size)
        assertEquals(7, observed2.size)
    }

    @Test
    fun `observer can unsubscribe without affecting others`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var observerJob1: Job
        lateinit var observerJob2: Job
        val observed1 = mutableListOf<Mutation>()
        val observed2 = mutableListOf<Mutation>()

        runTestTemplate(template) {
            observerJob1 = launch {
                mvflow.observeMutations().toList(observed1)
            }
            observerJob2 = launch {
                mvflow.observeMutations().toList(observed2)
            }
            launch {
                delay(90)
                observerJob2.cancel()
            }
        }

        observerJob1.cancel()
        assertEquals(7, observed1.size)
        assertEquals(3, observed2.size)
    }

    @Test
    fun `a slow observer doesn't impact the actual view`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var observerJob: Job
        val observed = mutableListOf<Mutation>()
        runTestTemplate(template) {
            observerJob = launch {
                mvflow.observeMutations().onEach { delay(500) }.toList(observed)
            }
            // should be enough time to process everything
            advanceTimeBy(200)
            assertEquals(
                emptyList<Mutation>(),
                observed
            )
            assertEquals(
                listOf(
                    State(0),
                    State(1),
                    State(2),
                    State(1),
                    State(2),
                    State(3),
                    State(6),
                    State(5)
                ),
                template.viewFake.states
            )
        }

        // by now everything should have come through
        observerJob.cancel()
        assertEquals(
            listOf(
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1),
                Mutation.Increment(1),
                Mutation.Increment(1),
                Mutation.Multiply(2),
                Mutation.Increment(-1)
            ),
            observed
        )
    }
}
