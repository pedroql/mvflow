package net.pedroloureiro.mvflow.samples.android.screens.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.pedroloureiro.mvflow.MVFlow
import net.pedroloureiro.mvflow.samples.android.databinding.HomeActivityBinding
import net.pedroloureiro.mvflow.samples.android.screens.counter.SimpleCounterActivity
import net.pedroloureiro.mvflow.samples.android.screens.lifecycle.LifecycleActivity

private typealias State = Unit
private typealias Mutation = Nothing

private sealed class Action {
    object OpenCountersExample : Action()
    object OpenLifecycleExample : Action()
}

/**
 * This is a very simple scree without any state so this is not the typical approach for a MVFlow.
 *
 * In this case, there is not state, which implies there are no mutations either, so we use
 * Unit instead for those classes.
 *
 * In normal cases you might prefer to have the MVFlow object code outside the activity. And you can do the same with
 * the view code too.
 */
class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mvFlow = MVFlow<State, Action, Mutation>(
            initialState = Unit,
            handler = { _, action ->
                flow<Nothing> {
                    when (action) {
                        Action.OpenCountersExample -> SimpleCounterActivity.launch(this@HomeActivity)
                        Action.OpenLifecycleExample -> LifecycleActivity.launch(this@HomeActivity)
                    }
                }.flowOn(Dispatchers.Main)
            },
            reducer = { _, _ -> Unit },
            mvflowCoroutineScope = lifecycleScope
        )

        val view = object : MVFlow.View<State, Action> {
            override fun render(state: State) {
                // nothing to do
            }

            override fun actions() = callbackFlow {
                binding.countersExampleButton.setOnClickListener {
                    offer(Action.OpenCountersExample)
                }
                binding.lifecycleExampleButton.setOnClickListener {
                    offer(Action.OpenLifecycleExample)
                }
                awaitClose()
            }
        }

        lifecycleScope.launchWhenStarted {
            mvFlow.takeView(this, view)
        }
    }
}
