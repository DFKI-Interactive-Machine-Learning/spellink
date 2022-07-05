package de.dfki.iml.spellink

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import androidx.annotation.Nullable


class MainActivity : AppCompatActivity() {
    companion object {
        const val LAUNCH_STYLE_ACTIVITY = 1
    }

    private val defaultUserStyle = "style-10115"
    private var userStyle = defaultUserStyle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun onDrawClick(view: View) {
        val intent = Intent(this, CanvasActivity::class.java)
        intent.putExtra("style", userStyle);
        startActivity(intent)
    }

    fun onStyleClick(view: View) {
        val intent = Intent(this, StyleActivity::class.java)
        startActivityForResult(intent, LAUNCH_STYLE_ACTIVITY)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LAUNCH_STYLE_ACTIVITY) {
            if (resultCode == RESULT_OK) {
                val userStyle = data?.getStringExtra("result") ?: defaultUserStyle
            }
            if (resultCode == RESULT_CANCELED) {
                userStyle = defaultUserStyle
            }
        }
    }

}