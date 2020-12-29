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
import net.pedroloureiro.mvflow.MVFlowCounterHelper.Effect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MVFlowWithEffectsTest {

    private fun TestCoroutineScope.defaultTemplate(printLogs: Boolean = false): MVFlowCounterTestTemplate {
        return MVFlowCounterTestTemplate(
            this,
            viewActions = flowOf(Action.Action1, Action.Action1, Action.Action1)
                .onEach { delay(50) },
            externalActions = flowOf(Action.Action2, Action.Action2)
                .onEach { delay(76) },
            printLogs = printLogs
        )
    }

    @Test
    fun `can observe effects`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var job: Job
        val observed = mutableListOf<Effect>()

        runTestTemplate(template) {
            job = launch {
                mvflow.observeEffects().toList(observed)
            }
        }
        job.cancel()
        assertEquals(7, observed.size)
    }

    @Test
    fun `can have two different observers`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        lateinit var actionJob2: Job
        val observed1 = mutableListOf<Effect>()
        val observed2 = mutableListOf<Effect>()

        runTestTemplate(template) {
            actionJob1 = launch {
                mvflow.observeEffects().toList(observed1)
            }
            actionJob2 = launch {
                mvflow.observeEffects().toList(observed2)
            }
        }
        actionJob1.cancel()
        actionJob2.cancel()
        assertEquals(7, observed1.size)
        assertEquals(7, observed2.size)
    }

    @Test
    fun `can have two different observers starting at different times`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        lateinit var actionJob2: Job
        val observed1 = mutableListOf<Effect>()
        val observed2 = mutableListOf<Effect>()

        runTestTemplate(template) {
            actionJob1 = launch {
                mvflow.observeEffects().toList(observed1)
            }
            actionJob2 = launch {
                delay(151)
                mvflow.observeEffects().toList(observed2)
            }
        }
        actionJob1.cancel()
        actionJob2.cancel()
        assertEquals(7, observed1.size)
        assertEquals(4, observed2.size)
    }

    @Test
    fun `effect observer joins late`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        val observed1 = mutableListOf<Effect>()

        runTestTemplate(template) {
            actionJob1 = launch {
                delay(151)
                mvflow.observeEffects().toList(observed1)
            }
        }
        actionJob1.cancel()
        assertEquals(4, observed1.size)
    }

    @Test
    fun `one observer can unsubscribe without affecting the others`() = runBlockingTest {
        val template = defaultTemplate()

        lateinit var actionJob1: Job
        lateinit var actionJob2: Job
        val observed1 = mutableListOf<Effect>()
        val observed2 = mutableListOf<Effect>()

        runTestTemplate(template) {
            actionJob1 = launch {
                mvflow.observeEffects().toList(observed1)
            }
            actionJob2 = launch {
                mvflow.observeEffects().toList(observed2)
            }
            launch {
                delay(151)
                actionJob2.cancel()
            }
        }

        actionJob1.cancel()
        assertEquals(7, observed1.size)
        assertEquals(3, observed2.size)
    }
}
