package net.pedroloureiro.mvflow.samples.android.screens.counter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import net.pedroloureiro.mvflow.samples.android.databinding.CounterActivityBinding
import net.pedroloureiro.mvflow.samples.android.screens.counter.CounterMVFlow.Action
import net.pedroloureiro.mvflow.samples.android.screens.counter.CounterMVFlow.State

class SimpleCounterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = CounterActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Simple counter"

        /*
         * Note: to keep this example short and simple, we don't deal with orientation changes or process death
         * (In those cases this would start the counter from 0)
         */

        val view = object : CounterMVFlow.View {
            override fun render(state: State) {
                binding.counterField.text = state.value.toString()
                binding.progressBar.visibility = if (state.showProgress) View.VISIBLE else View.INVISIBLE
            }

            override fun actions(): Flow<Action> = callbackFlow {
                binding.incrementButton.setOnClickListener {
                    offer(Action.AddOne)
                }
                binding.incrementMany.setOnClickListener {
                    offer(Action.AddMany)
                }
                binding.resetButton.setOnClickListener {
                    offer(Action.Reset)
                }
                awaitClose()
            }

            override val coroutineScope: CoroutineScope
                get() = this@SimpleCounterActivity.lifecycleScope
        }
        val mvFlow = CounterMVFlow.create(
            coroutineScope = lifecycleScope
        )
        mvFlow.takeView(view)
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, SimpleCounterActivity::class.java)
            context.startActivity(intent)
        }
    }
}
