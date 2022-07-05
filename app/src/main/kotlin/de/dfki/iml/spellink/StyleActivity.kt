package de.dfki.iml.spellink

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.dfki.iml.spellink.ink.Ink


class StyleActivity : AppCompatActivity() {
    companion object {
        const val templateText = "quick brown fox jumped over the lazy dog"
        const val LOG_TAG = "SCRIBE"
    }

    lateinit var canvasView: CanvasView
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = getMenuInflater()
        inflater.inflate(R.menu.actionbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_clear -> {
            canvasView.clear()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style)
        canvasView = findViewById(R.id.canvasView)
        val templateTextView = findViewById<TextView>(R.id.quick_fox)
        val text = SpannableString("Please write down the following text to transfer style: \n$templateText")
        text.setSpan(RelativeSizeSpan(1.3f), 57, 57 + templateText.length, 0) // set size
        text.setSpan(StyleSpan(Typeface.BOLD), 57, 57 + templateText.length, 0) // set size
        templateTextView.text = text
    }

    fun onClick(view: View) {
        val joinedStrokes = canvasView.inkMap.values.flatMap { it.strokes }.toMutableList()
        val ink = Ink(joinedStrokes)
        Toast.makeText(this, "Searching for a similar style, please wait", Toast.LENGTH_SHORT)
        SimilarStyleManager(this.assets, object : SimilarStyleManagerListener {
            override fun onResultsReady(minStyle: String) {
                val returnIntent = Intent()
                returnIntent.putExtra("result", minStyle)
                setResult(RESULT_OK, returnIntent)
                finish()
            }
        }).execute(ink)
    }

}