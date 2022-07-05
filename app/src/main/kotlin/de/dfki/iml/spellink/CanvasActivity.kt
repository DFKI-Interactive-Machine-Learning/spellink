package de.dfki.iml.spellink

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import de.dfki.iml.spellchecker.Hunspell
import de.dfki.iml.spellink.de.dfki.iml.spellink.Synthesizer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths


class CanvasActivity : AppCompatActivity() {
    lateinit var recognizedTextView: TextView
    val LOG_TAG = "SCRIBE"
    lateinit var canvasView: RecognitionCanvasView
    // put hunspell files into the HunspellDictionary in the root dir of the device!
    var dicPath = "/HunspellDictionary/en_US.dic"
    var affPath = "/HunspellDictionary/en_US.aff"
    lateinit var mHunspell: Hunspell

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = getMenuInflater()
        inflater.inflate(R.menu.actionbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_clear -> {
            canvasView.clear()
            recognizedTextView.text = ""
            true
        }
        R.id.action_save -> {
            Toast.makeText(this, "Note saved successfully", Toast.LENGTH_LONG).show()
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_canvas_simple)
        canvasView = findViewById(R.id.canvasView)
        recognizedTextView = findViewById<TextView>(R.id.recognized)
        recognizedTextView.setMovementMethod(ScrollingMovementMethod())
        registerForContextMenu(canvasView)
        val userStyle = intent.getStringExtra("style") ?: ""
        canvasView.setStyle(userStyle)
        val sdcardPath = Environment.getExternalStorageDirectory()
        dicPath = sdcardPath.toString() + dicPath
        affPath = sdcardPath.toString() + affPath
        // affPath= getExternalFilesDir(null) + affPath; //TODO: copy files from assets and use new storage model
        try {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
                Log.d(LOG_TAG, "Permission is granted")
                Files.readAllBytes(Paths.get(affPath)) //check if we can read hunspell files
            }
            Files.readAllBytes(Paths.get(affPath)) //check if we can read hunspell files
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Access denied")
            e.printStackTrace()
        }
        InitializeHunspell(affPath, dicPath, object : InitializeHunspellListener {
            override fun onResultsReady(hunspell: Hunspell) {
                canvasView.setHunspell(hunspell)
                mHunspell = hunspell
            }

        }).execute()
        InitializeSynthesizer(object : InitializeSynthesizerListener {
            override fun onResultsReady(synthesizer: Synthesizer) {
                canvasView.setSynthesiser(synthesizer)
            }
        }).execute(this.baseContext.assets)
    }

    interface InitializeHunspellListener {
        fun onResultsReady(hunspell: Hunspell)
    }

    interface InitializeSynthesizerListener {
        fun onResultsReady(synthesizer: Synthesizer)
    }

    private class InitializeHunspell(val affPath: String, val dicPath: String, val listener: InitializeHunspellListener) :
        AsyncTask<Void, Unit, Hunspell>() {
        val LOG_TAG = "SCRIBE"
        override fun doInBackground(vararg params: Void): Hunspell {
            Log.d(LOG_TAG, "InitializeHunspell doInBackground")
            val hunspell = Hunspell()

            // the *.aff and *.dic files paths are passed to load Hunspell
            hunspell.init(affPath, dicPath)
            Log.d(LOG_TAG, "init hunspell")
            return hunspell
        }

        override fun onPostExecute(hunspell: Hunspell) {
            Log.d(LOG_TAG, "hunspell init finished")
            listener.onResultsReady(hunspell)
        }

    }

    private class InitializeSynthesizer(val listener: InitializeSynthesizerListener) : AsyncTask<AssetManager, Unit, Synthesizer>() {
        val LOG_TAG = "SCRIBE"

        override fun doInBackground(vararg assets: AssetManager): Synthesizer {
            Log.d(LOG_TAG, "InitializeSynthesizer doInBackground")
            return Synthesizer(assets[0])
        }

        override fun onPostExecute(synthesizer: Synthesizer) {
            Log.d(LOG_TAG, "synthesiser init finished")
            listener.onResultsReady(synthesizer)
        }
    }

    override fun onDestroy() {
        mHunspell.finalize()
        super.onDestroy()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val text = item.title
        Toast.makeText(this, "Generation for `$text` started", Toast.LENGTH_LONG)
        canvasView.substitute(item.itemId, text)
//        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        return super.onOptionsItemSelected(item)
    }
}