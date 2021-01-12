package net.pedroloureiro.mvflow.samples.android.screens.counter

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import net.pedroloureiro.mvflow.samples.android.CollectingEffectSender
import org.junit.Assert
import org.junit.Before
import org.junit.Test

internal class CounterMVFlowHandlerTest {

    val handler = CounterMVFlow.handler
    lateinit var effectSender: CollectingEffectSender<CounterMVFlow.Effect>

    @Before
    fun setUp() {
        // (a bit of reflection here: )
        // typically you'd want to instantiate a new handler and assign it to `handler` but the way things were done,
        // handler is just one lambda in a kotlin object (aka a singleton). Depending on the
        // implementation details of your particular project, you might be able to instantiate the handler on demand
        //  handler = [not happening]
        effectSender = CollectingEffectSender()
    }

    @Test
    fun testAddOne() = runBlockingTest {
        val flow = handler.invoke(
            CounterMVFlow.State(),
            CounterMVFlow.Action.AddOne,
            effectSender
        ).toList()

        Assert.assertEquals(
            listOf<CounterMVFlow.Mutation>(
                CounterMVFlow.Mutation.Increment(1)
            ),
            flow
        )

        Assert.assertEquals(
            emptyList<CounterMVFlow.Effect>(),
            effectSender.effectsSeen
        )
    }

    @Test
    fun testAddMany() = runBlockingTest {
        val flow = handler.invoke(
            CounterMVFlow.State(),
            CounterMVFlow.Action.AddMany,
            effectSender
        ).toList()

        Assert.assertEquals(
            listOf(
                CounterMVFlow.Mutation.BackgroundJobStarted,
                CounterMVFlow.Mutation.Increment(1),
                CounterMVFlow.Mutation.Increment(2),
                CounterMVFlow.Mutation.Increment(1),
                CounterMVFlow.Mutation.BackgroundJobFinished
            ),
            flow
        )

        Assert.assertEquals(
            listOf(
                CounterMVFlow.Effect.ShowToast("This might take a while..."),
                CounterMVFlow.Effect.ShowToast("Background job finished")
            ),
            effectSender.effectsSeen
        )
    }

    // more actions to test
}
