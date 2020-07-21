package net.pedroloureiro.mvflow.samples.android.screens.dummydialog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import net.pedroloureiro.mvflow.samples.android.databinding.DummyDialogActivityBinding

class DummyDialogActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = DummyDialogActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.button.setOnClickListener {
            finish()
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, DummyDialogActivity::class.java)
            context.startActivity(intent)
        }
    }
}
