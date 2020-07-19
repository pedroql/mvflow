package net.pedroloureiro.mvflow.samples.android.screens.counter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import net.pedroloureiro.mvflow.samples.android.R

class CounterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.counter_activity)
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, CounterActivity::class.java)
            context.startActivity(intent)
        }
    }
}
