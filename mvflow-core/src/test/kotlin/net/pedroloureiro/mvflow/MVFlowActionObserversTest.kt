package net.pedroloureiro.mvflow

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import net.pedroloureiro.mvflow.MVFlowCounterHelper.Action
import net.pedroloureiro.mvflow.MVFlowCounterHelper.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MVFlowActionObserversTest {

    private fun TestCoroutineScope.defaultTemplate(): MVFlowCounterTestTemplate {
        return MVFlowCounterTestTemplate(
            this,
            viewActions = flowOf(Action.Action1, Action.Action1, Action.Action1)
                .onEach { delay(50) },
            externalActions = flowOf(Action.Action2, Action.Action2)
                .onEach { delay(75) }
        )
    }

    @Test
    fun `action observer is notified about actions from view and external events`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob: Job
        val observed = mutableListOf<Action>()
        runTestTemplate(template) {
            actionJob = launch {
                mvflow.observeActions().toList(observed)
            }
        }

        actionJob.cancel()
        assertEquals(5, observed.size)
    }

    @Test
    fun `able to observe midway through the action events`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob: Job
        val observed = mutableListOf<Action>()

        runTestTemplate(template) {
            actionJob = launch {
                // should miss one action from the view and another from the external actions
                delay(90)
                mvflow.observeActions().toList(observed)
            }
        }
        actionJob.cancel()
        assertEquals(3, observed.size)
    }

    @Test
    fun `two observers can start observing actions at different points`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        lateinit var actionJob2: Job
        val observed1 = mutableListOf<Action>()
        val observed2 = mutableListOf<Action>()

        runTestTemplate(template) {
            actionJob1 = launch {
                mvflow.observeActions().toList(observed1)
            }
            actionJob2 = launch {
                // should miss one action from the view and another from the external actions
                delay(90)
                mvflow.observeActions().toList(observed2)
            }
        }
        actionJob1.cancel()
        actionJob2.cancel()
        assertEquals(5, observed1.size)
        assertEquals(3, observed2.size)
    }

    @Test
    fun `verify observing actions with state`() = runBlockingTest {
        val template = MVFlowCounterTestTemplate(
            this,
            flowOf(Action.Action1, Action.Action1, Action.Action1)
                .onEach { delay(50) },
            flowOf(Action.Action2, Action.Action2)
                .onEach { delay(80) },
            // not delaying mutations to make reasoning about the expected state easier
            delayMutations = false
        )

        lateinit var actionJob: Job
        val observed = mutableListOf<Pair<Action, State>>()

        runTestTemplate(template) {
            actionJob = launch {
                mvflow.observeActionsWithState().toList(observed)
            }
        }

        actionJob.cancel()
        assertEquals(
            listOf(
                Action.Action1 to State(0),
                Action.Action2 to State(1),
                Action.Action1 to State(1),
                Action.Action1 to State(2),
                Action.Action2 to State(3)
            ),
            observed
        )
    }

    @Test
    fun `slow observers do not affect each others`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        lateinit var actionJob2: Job
        val observed1 = mutableListOf<Action>()
        val observed2 = mutableListOf<Action>()

        runTestTemplate(template) {
            actionJob1 = launch {
                mvflow.observeActions().onEach { delay(500) }.toList(observed1)
            }
            actionJob2 = launch {
                mvflow.observeActions().toList(observed2)
            }

            advanceTimeBy(400)
            assertEquals(0, observed1.size)
            assertEquals(5, observed2.size)
        }

        actionJob1.cancel()
        actionJob2.cancel()
        // at this point both observers should have observed all the events
        assertEquals(5, observed1.size)
        assertEquals(5, observed2.size)
    }

    @Test
    fun `observer can unsubscribe without affecting others`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        lateinit var actionJob2: Job
        val observed1 = mutableListOf<Action>()
        val observed2 = mutableListOf<Action>()

        runTestTemplate(template) {
            actionJob1 = launch {
                mvflow.observeActions().toList(observed1)
            }
            actionJob2 = launch {
                mvflow.observeActions().toList(observed2)
            }
            launch {
                delay(90)
                actionJob2.cancel()
            }
        }

        actionJob1.cancel()
        assertEquals(5, observed1.size)
        assertEquals(2, observed2.size)
    }

    @Test
    fun `a slow observer doesn't impact the view`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob: Job
        val observed = mutableListOf<Action>()
        runTestTemplate(template) {
            actionJob = launch {
                mvflow.observeActions().onEach { delay(500) }.toList(observed)
            }
            advanceTimeBy(250)
            assertEquals(
                emptyList<Action>(),
                observed
            )
            assertEquals(
                listOf(
                    State(0),
                    State(1),
                    State(2),
                    State(3),
                    State(2),
                    State(4),
                    State(5),
                    State(4)
                ),
                template.viewFake.states
            )
        }

        actionJob.cancel()
        assertEquals(5, observed.size)
    }
}
