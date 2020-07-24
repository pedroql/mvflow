package net.pedroloureiro.mvflow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MyTest {

    sealed class Action {
        object Button1 : Action()
        object Button2 : Action()
    }

    sealed class Mutation {
        data class ChangeA(val x: Int) : Mutation()
        data class ChangeB(val y: String) : Mutation()
    }

    interface Handler {
        // for each action, this method will emit a few mutations over a couple of seconds
        fun handle(action: Action): Flow<Mutation>
    }

    val viewActions: Flow<Action> = flowOf(
        Action.Button1,
        Action.Button2,
        Action.Button1,
        Action.Button2,
        Action.Button1,
        Action.Button2,
        Action.Button1,// these last two should not be sent
        Action.Button2
    )
        .onEach { delay(150) }

    val handler: Handler = object : Handler {
        override fun handle(action: Action): Flow<Mutation> =
            flowOf(
                Mutation.ChangeA(1),
                Mutation.ChangeB("b"),
                Mutation.ChangeA(1),
                Mutation.ChangeB("b")
            ).onEach { delay(500) }
    }


    @Test
    fun myTest() {

        var counter = 0
        runBlockingTest {
            // in my actual usage, I don't create the scopes myself
//            val viewScope = CoroutineScope(newCoroutineContext(coroutineContext))
//            val handlerScope = CoroutineScope(newCoroutineContext(coroutineContext))
            val viewScope = CoroutineScope(TestCoroutineDispatcher())
            val handlerScope = CoroutineScope(TestCoroutineDispatcher())
            launch {
                delay(901)
                println("will cancel view scope now")
                viewScope.cancel()
            }
            handlerScope.launch {
                viewActions
                    .buffer()
                    .collect { action ->
                        println("Handling action $action")
                        launch {
                            withContext(handlerScope.coroutineContext) {
                                launch {
                                    handler.handle(action)
                                        .collect {
                                            // do something with mutation
                                            println("Mutation $it")
                                            counter++
                                        }
                                    println("After 2nd collect")
                                }
                                println("After 2nd launch")
                            }
                            println("After withContext")
                        }
                    }
                println("After 1st collect")
            }
            println("After 1st launch")


//            handlerScope.launch {
//                viewActions
//                    .buffer()
//                    .collect { action ->
//                        println("Handling action $action")
////                        launch {
////                            withContext(handlerScope.coroutineContext) {
//                                launch {
//                                    handler.handle(action)
//                                        .collect {
//                                            // do something with mutation
//                                            println("Mutation $it")
//                                            counter++
//                                        }
//                                    println("After 2nd collect")
////                                }
////                                println("After 2nd launch")
////                            }
////                            println("After withContext")
//                        }
//                    }
//                println("After 1st collect")
//            }
//            println("After 1st launch")
        }
        assertEquals(24, counter)
    }

    class EventFlow<T> private constructor(
        private val channel: Channel<T>
    ) : Flow<T> by channel.receiveAsFlow() {
        suspend fun sendValue(value: T) {
            channel.send(value)
        }

        companion object {
            operator fun <T> invoke(): EventFlow<T> = EventFlow(Channel())
        }
    }

    @Test
    fun myTest2() {
        var counter = 0
        runBlockingTest {
            // in my actual use case, I don't create the scopes myself. They are given by the platform.
//            val viewScope = CoroutineScope(newCoroutineContext(coroutineContext))
//            val handlerScope = CoroutineScope(newCoroutineContext(coroutineContext))
            val viewScope = CoroutineScope(TestCoroutineDispatcher())
            val handlerScope = CoroutineScope(TestCoroutineDispatcher())
            launch {
                delay(901)
                log("will cancel view scope now $currentTime")
                viewScope.cancel()
            }
            viewScope.launch {
                viewActions
                    .buffer()
//                    .flowOn(viewScope) // tried this while replacing the first viewScope.launch with handleScope.launch - did not work
                    .collect { action ->
                        log("Handling action $action")
                        handlerScope.launch { // dit not work
//                        launch(handlerScope.coroutineContext) { // did not work
//                            withContext(handlerScope.coroutineContext) { // did not work (together with the next launch)
//                                launch {
                            handler.handle(action)
                                .collect {
                                    // do something with mutation
                                    log("Mutation $it")
                                    counter++
                                }
//                                }
//                            }
                        }
//                        }
                    }
            }
        }
    }


    @Test
    fun myTest3() {
        var counter = 0
        runBlockingTest {
            // in my actual use case, I don't create the scopes myself. They are given by the platform.
            val viewScope = CoroutineScope(TestCoroutineDispatcher())
            val handlerScope = CoroutineScope(TestCoroutineDispatcher())
            launch {
                delay(901)
                log("will cancel view scope now")
                viewScope.cancel()
            }
            val eventFlow = EventFlow<Action>()
            viewScope.launch {
                viewActions
                    .buffer()
                    .onEach {
//                        log("sending action $it")
                        eventFlow.sendValue(it)
                    }
                    .launchIn(viewScope)
            }
            handlerScope.launch {
                eventFlow
                    .buffer()
                    .collect { action ->
                        log("Handling action $action")
                        handlerScope.launch {
                            handler.handle(action)
                                .collect {
                                    // do something with mutation
                                    log("Mutation $it")
                                    counter++
                                }
                        }
                    }
            }
        }
    }

    @Test
    fun myTest4() {
        val channel = Channel<Action>(20)
        var counter = 0
        runBlockingTest {
            // in my actual use case, I don't create the scopes myself. They are given by the platform.
            val viewScope = CoroutineScope(TestCoroutineDispatcher())
            val handlerScope = CoroutineScope(TestCoroutineDispatcher())
            launch {
                delay(901)
                log("will cancel view scope now")
                log("how is handler scope before? ${handlerScope}")
                viewScope.cancel()
                log("how is handler scope after? ${handlerScope}")
            }
            viewScope.launch {
                viewActions
                    .onEach {
                        log("sending action $it")
                        channel.send(it)
                    }
                    .launchIn(viewScope)
            }
            handlerScope.launch {
                channel
                    .consumeAsFlow()
                    .collect { action ->
                        log("Handling action $action")
                        handlerScope.launch {
                            handler.handle(action)
                                .collect {
                                    // do something with mutation
                                    log("Mutation $it")
                                    counter++
                                }
                        }
                    }
            }
        }
    }

    @Test
    fun myTest5() {
        val channel = Channel<Action>(20)

        val counter = AtomicInteger(0)
        runBlockingTest {
            // in my actual use case, I don't create the scopes myself. They are given by the platform.
//            val viewScope = CoroutineScope(TestCoroutineDispatcher())
            val viewScope = TestCoroutineScope(coroutineContext+Job())

//            val viewScope = CoroutineScope(coroutineContext)
//            val handlerScope = CoroutineScope(coroutineContext)
            val handlerScope = CoroutineScope(coroutineContext)


            viewScope.launch {
                viewActions
                    .logFlow("actions")
                    .collect {
                        channel.send(it)
                    }
            }
            handlerScope.launch {
                channel
                    .consumeAsFlow()
                    .logFlow("channel")
                    .collect { action ->
                        log("Handling action $action")
                        handlerScope.launch {
                            handler.handle(action)
                                .logFlow("handler")
                                .collect {
                                    // do something with mutation
                                    log("Mutation $it")
                                    counter.incrementAndGet()
                                }
                        }
                    }
            }

            launch {
                delay(901)
                log("will cancel view scope now - $currentTime")
                viewScope.cancel()
                log("Handler scope after view scope cancelation: $handlerScope")
                val testCoroutineScope = this@runBlockingTest
                testCoroutineScope.coroutineContext[Job]
                log("Parent scope after view scope cancelation: ${(this@runBlockingTest).coroutineContext[Job]}")
            }
            launch {
                delay(9001)
                log("will cancel handler scope now - $currentTime")
                handlerScope.cancel()
            }
        }
        assertEquals(24, counter.get())
    }

    private fun <T> Flow<T>.logFlow(name: String): Flow<T> {
        return this
            .onStart {
                log("$name: flow starting on ${currentCoroutineContext()}")
            }
            .onEach {
                log("$name: flow sending $it")
            }
            .onCompletion {
                log("$name: flow completed")
            }
            .catch {
                log("$name: flow caught $it")
            }
    }

    fun log(msg: String) {
        println("$msg")
    }
}
/*
I have a view emitting a flow of Actions which is collected by a handler which emits a flow of Mutations (that will update
the state) and they have different lifecycle scopes (the handler can outlive the views).

How can I do it so that I can cancel the view's scope (no more Actions come through) yet the handling of the
actions still being processed is not cancelled?

Say the handler takes 5 seconds processing each action. When I cancel the view scope, I know that I should
expect more mutations to come through but they get cancelled too.

I've gone through the docs and tried many different alternatives but none worked.
 */
