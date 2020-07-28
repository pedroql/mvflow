package net.pedroloureiro.mvflow.samples.android.screens.lifecycle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import net.pedroloureiro.mvflow.MviView
import net.pedroloureiro.mvflow.samples.android.databinding.LifecycleActivityBinding
import net.pedroloureiro.mvflow.samples.android.screens.dummydialog.DummyDialogActivity
import net.pedroloureiro.mvflow.samples.android.screens.lifecycle.LifecycleMVFlow.Action
import net.pedroloureiro.mvflow.samples.android.screens.lifecycle.LifecycleMVFlow.Effect
import net.pedroloureiro.mvflow.samples.android.screens.lifecycle.LifecycleMVFlow.State

class LifecycleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Advanced lifecycle"
        val binding = LifecycleActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val view = object : MviView<State, Action> {
            override fun render(state: State) {
                binding.normalCounter.text = state.normalCounter.toString()
                binding.startedCounter.text = state.startedCounter.toString()
                binding.resumedCounter.text = state.resumedCounter.toString()

                binding.timersRunning.isChecked = state.timersRunning

                binding.delayedToggleTimers.isEnabled = state.delayedToggleWaiting.not()
                binding.toggleProgressBar.visibility = if (state.delayedToggleWaiting) View.VISIBLE else View.INVISIBLE
            }

            override fun actions(): Flow<Action> = callbackFlow {
                binding.delayedToggleTimers.setOnClickListener {
                    offer(Action.ToggleTimersDelayed)
                }
                binding.openDialog.setOnClickListener {
                    offer(Action.OpenDialog)
                }
                binding.resetTimers.setOnClickListener {
                    offer(Action.ResetTimers)
                }
                binding.timersRunning.setOnCheckedChangeListener { _, checked ->
                    offer(Action.SetTimers(checked))
                }

                // these are not really actions coming from the view, but a proof of concept to allow to see the
                // different ways you can use lifecycles to observe state updates how you want them.
                //
                // This sample highlighted some issues with the current API and we will address that soon.
                val ticker = tickerBroadcastChannel()
                lifecycleScope.launchWhenResumed {
                    ticker.openSubscription().consumeEach {
                        offer(Action.TickResumed)
                    }
                }

                lifecycleScope.launch {
                    ticker.openSubscription().consumeEach {
                        offer(Action.TickNormal)
                    }
                }
                lifecycleScope.launchWhenStarted {
                    ticker.openSubscription().consumeEach {
                        offer(Action.TickStarted)
                    }
                }
                awaitClose()
            }
        }

        val mvFlow = LifecycleMVFlow.create(lifecycleScope)
        lifecycleScope.launch {
            mvFlow.takeView(this, view)
        }

        lifecycleScope.launch {
            mvFlow.observeEffects().filterIsInstance<Effect.OpenDialog>()
                .collect {
                    DummyDialogActivity.launch(this@LifecycleActivity)
                }
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun tickerBroadcastChannel(): BroadcastChannel<Unit> {
        return ticker(
            delayMillis = 1000,
            initialDelayMillis = 0
        ).broadcast(capacity = Channel.CONFLATED)
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, LifecycleActivity::class.java)
            context.startActivity(intent)
        }
    }
}
