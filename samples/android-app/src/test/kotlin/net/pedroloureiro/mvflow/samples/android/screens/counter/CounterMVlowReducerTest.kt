package net.pedroloureiro.mvflow.samples.android.screens.counter

import org.junit.Assert
import org.junit.Test

class CounterMVlowReducerTest {
    // see comment in CounterMVFlowHandlerTest::setUp
    val reducer = CounterMVFlow.reducer

    @Test
    fun testReducerIncrement() {

        val actual = reducer.invoke(
            CounterMVFlow.State(0, 0),
            CounterMVFlow.Mutation.Increment(2)
        )

        Assert.assertEquals(
            CounterMVFlow.State(
                value = 2,
                backgroundOperations = 0
            ),
            actual
        )
    }

    @Test
    fun testReducerBackgroundMutation() {

        val actual = reducer.invoke(
            CounterMVFlow.State(0, 0),
            CounterMVFlow.Mutation.BackgroundJobStarted
        )

        Assert.assertEquals(
            CounterMVFlow.State(
                value = 0,
                backgroundOperations = 1
            ),
            actual
        )
    }

    // more mutations/scenarios to test
}
