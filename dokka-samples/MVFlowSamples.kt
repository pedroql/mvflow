object MVFlowSamples {
    fun handler() {
        // Good example: it returns a Flow immediately

        val good: Handler<State, Action, Mutation> = { _, _ ->
            flow {
                slowOperation1()
                emit(Mutation.A)
                slowOperation2()
                emit(Mutation.B)
            }
        }

        // Bad example: doing something slow before returning flow

        val bad: Handler<State, Action, Mutation> = { _, _ ->
            slowOperation1()
            flow {
                emit(Mutation.A)
                slowOperation2()
                emit(Mutation.B)
            }
        }
    }

    fun handlerWithEffects() {
        val example: Handler<State, Action, Mutation, Effect> = { _, _, effects: EffectProducer<Effect> ->
            flow {
                slowOperation1()
                emit(Mutation.A)
                slowOperation2()
                emit(Mutation.B)
                effects.send(Effects.OperationCompleted)
            }
        }
    }

    fun mutations() {

        // How to create good mutations:

        data class State(val counter: Int)
        sealed class Mutation {
            data class Add(val amount: Int)
            // The next mutation is a bad example, don't use it
            data class SetValue(val newValue: Int)
        }

        // Good example - explain how to modify the state

        val good: Handler<State, Action, Mutation> = { _, _ ->
            // simulate long work
            delay(1_000)
            emit(Mutation.Add(1))
        }
        // [good] always works. Even if 3 actions are emitted quickly, each one will emit an adition of 1 and the
        // reducer will work properly

        // Bad example - don't assume that state is still up to date. That is the reducer's job.

        val bad: Handler<State, Action, Mutation> = { state, _ ->
            // simulate long work
            delay(1_000)
            emit(Mutation.SetValue(state.counter+1))
        }

        // [bad] has a bug. If several actions are emitted in less than a second (the time it takes to do the long
        // operation in this example), when each of these actions complete, it will emit a mutation to set the
        // value to initial state + 1, overriding the changes of the previous actions that meanwhile had changed the
        // current state.
    }

    fun reducer() {
        data class State(val counter: Int)
        sealed class Mutation {
            data class Add(val amount: Int)
            data class Multiply(val amount: Int)
        }
        val reducer: Reducer<State, Mutation> = {
            state, mutation ->
            when (mutation) {
                is Add -> State(state.counter + mutation.value)
                is Multiply -> State(state.counter * mutation.value)
            }
        }
    }

    fun flow() {
        class MyActivity : AppCompatActivity(), MVFlow.View<State> {
            // could also be a Fragment, or a plain class you created.
            private val actionChannel = Channel<Action>()

            fun setupUi() {
                swipeRefreshLayout.setOnRefreshListener {
                    actionChannel.offer(Action.Load)
                }
            }

            override fun actions() = actionChannel.consumeAsFlow()
        }
    }
}
