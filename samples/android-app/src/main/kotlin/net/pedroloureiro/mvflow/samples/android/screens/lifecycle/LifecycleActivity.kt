package net.pedroloureiro.mvflow.samples.android.screens.lifecycle

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import net.pedroloureiro.mvflow.MVFlow
import net.pedroloureiro.mvflow.samples.android.databinding.LifecycleActivityBinding
import net.pedroloureiro.mvflow.samples.android.screens.dummydialog.DummyDialogActivity
import net.pedroloureiro.mvflow.samples.android.screens.lifecycle.LifecycleMVFlow.Action
import net.pedroloureiro.mvflow.samples.android.screens.lifecycle.LifecycleMVFlow.State

class LifecycleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logLifeCycle(if (savedInstanceState == null) "onCreate" else "onCreate with saved instance state")
        title = "Advanced lifecycle"
        val binding = LifecycleActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /*
        Note: this is not the normal way to use this library. This is just a contrived example to show the difference
        between launch and launchWhenResumed (and other similar methods)
         */
        val viewNormal = StateActionView(binding.normalCounter, "normal")
        val mvFlowNormal = LifecycleMVFlow.create(lifecycleScope)
        val viewStarted = StateActionView(binding.startedCounter, "started")
        val mvFlowStarted = LifecycleMVFlow.create(lifecycleScope)
        val viewResumed = StateActionView(binding.resumedCounter, "resumed")
        val mvFlowResumed = LifecycleMVFlow.create(lifecycleScope)

        val initialActions = listOf(Action.StartCounter)
        lifecycleScope.launch {
            mvFlowNormal.takeView(this, viewNormal, initialActions)
        }
        lifecycleScope.launchWhenStarted {
            mvFlowStarted.takeView(this, viewStarted, initialActions)
        }
        lifecycleScope.launchWhenResumed {
            mvFlowResumed.takeView(this, viewResumed, initialActions)
        }

        binding.openDialog.setOnClickListener {
            DummyDialogActivity.launch(this)
        }
    }

    class StateActionView(private val textView: TextView, private val name: String) : MVFlow.View<State, Action> {
        override fun render(state: State) {
            Log.d("MYAPP", "lifecycle counter updated for $name with value ${state.counter}")
            textView.text = state.counter.toString()
        }

        override fun actions(): Flow<Action> = emptyFlow()
    }

    override fun onStart() {
        super.onStart()
        logLifeCycle("onStart")
    }

    override fun onResume() {
        super.onResume()
        logLifeCycle("onResume")
    }

    override fun onPause() {
        logLifeCycle("onPause")
        super.onPause()
    }

    override fun onStop() {
        logLifeCycle("onStop")
        super.onStop()
    }

    override fun onDestroy() {
        logLifeCycle("onDestroy")
        super.onDestroy()
    }

    private fun logLifeCycle(step: String) {
        Log.d("MYAPP", "lifecycle step $step ${javaClass.simpleName}@${hashCode().toString(16)}")
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, LifecycleActivity::class.java)
            context.startActivity(intent)
        }
    }
}
