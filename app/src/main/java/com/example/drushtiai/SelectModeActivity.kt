package com.example.drushtiai

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class SelectModeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_mode)

        findViewById<MaterialButton>(R.id.btnExamSurveillance).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnShopCctv).setOnClickListener {
            startActivity(Intent(this, WorkInProgressActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnAtmCctv).setOnClickListener {
            startActivity(Intent(this, WorkInProgressActivity::class.java))
        }
    }
}
