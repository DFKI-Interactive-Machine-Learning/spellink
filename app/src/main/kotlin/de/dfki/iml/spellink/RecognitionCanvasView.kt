package de.dfki.iml.spellink

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.AsyncTask
import android.os.CountDownTimer
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.ContextMenu
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import de.dfki.iml.spellink.ink.Ink
import de.dfki.iml.spellink.ink.Point
import de.dfki.iml.spellink.ink.Stroke
import de.dfki.iml.spellchecker.Hunspell
import de.dfki.iml.spellink.de.dfki.iml.spellink.Synthesizer
import org.jetbrains.kotlinx.multik.ndarray.operations.toFloatArray
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import kotlin.math.abs


data class Suggestion(val id: Int, val items: List<String>)
data class FloatPoint(val x: Float, val y: Float)

class RecognitionCanvasView(context: Context, attrs: AttributeSet?) : CanvasView(context, attrs) {
    companion object {
        init {
            System.loadLibrary("tensorflow_inference")
        }
    }

    private var userStyle = "style-10115" // adjust for the user
    private lateinit var hunspell: Hunspell
    private lateinit var synthesizer: Synthesizer
    private val tfRecognize: TensorFlowInferenceInterface = TensorFlowInferenceInterface(context.assets, "recognition_optimized_graph.pb")
    private val inkToText: MutableMap<Int, String> = mutableMapOf()
    private val inkIdIsMisspelled: MutableMap<Int, Boolean> = mutableMapOf()
    private val inkIdToCorrected: MutableMap<Int, List<String>> = mutableMapOf()
    private val inkIdToPaintLine: MutableMap<Int, Paint> = mutableMapOf()
    private var currentSuggestion = Suggestion(0, emptyList())


    private val misspelledLine = Paint().also {
        it.color = Color.RED
        it.style = Paint.Style.STROKE
        it.strokeWidth = 3f
        it.strokeCap = Paint.Cap.ROUND
        it.isAntiAlias = true
    }
    private var pathMap: HashMap<Int, Path> = HashMap()

    fun setStyle(style: String) {
        userStyle = style
    }

    override fun clear() {
        inkMap.clear()
        pathMap.clear()
        inkToText.clear()
        inkIdIsMisspelled.clear()
        inkIdToCorrected.clear()
        super.clear()
    }

    override fun onCreateContextMenu(menu: ContextMenu?) {
        super.onCreateContextMenu(menu)
        loadSuggestions()
        currentSuggestion.items.forEach {
            menu?.add(Menu.NONE, currentSuggestion.id, Menu.NONE, it)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // draw underline for misspelled inks
        for ((inkId, ink) in inkMap) {
            if (inkIdIsMisspelled.getOrDefault(inkId, false)) {
                drawMisspelled(ink, canvas)
            }
        }
    }

    private fun drawMisspelled(ink: Ink, canvas: Canvas) {
        val (minima, maxima) = ink.getExtrema()
        if (!(minima.isEmpty() || maxima.isEmpty()) && Collections.min(minima) != Collections.max(maxima)) {
            val (xStart, xEnd, yBottom, yTop) = ink.boundaryBox
            var baseline = minima.toDoubleArray().median()
            // val meanline = maxima.toDoubleArray().median()
            var margin = 5
            val gap = 7
            if (baseline - yBottom < 20)
                margin += 12
            baseline = this.height - baseline
            baseline += margin
            // double red line
            // for wavy line see https://stackoverflow.com/questions/27267254/how-to-add-red-wavy-line-below-text-in-androids-textview
            canvas.drawLine(xStart.toFloat(), baseline.toFloat(), xEnd.toFloat(), baseline.toFloat(), misspelledLine)
            canvas.drawLine(xStart.toFloat(), baseline.toFloat() + gap, xEnd.toFloat(), baseline.toFloat() + gap, misspelledLine)
        }
    }

    private fun loadSuggestions() {
        currentSuggestion = Suggestion(0, emptyList())
        val y = this.height - position.y
        val x = position.x
        val targetInk = inkMap.values.firstOrNull {
            val box = it.boundaryBox
            (box[0] < x && x < box[1] && box[2] < y && y < box[3])
        }
        if (targetInk != null) {
            if (inkIdIsMisspelled.getOrDefault(targetInk.id, false)) {
                currentSuggestion = Suggestion(targetInk.id, inkIdToCorrected[targetInk.id] ?: listOf())
            }
        }
    }

    override fun makeInk(): Ink {
        val ink = super.makeInk()
        recognize(ink)
        return ink
    }

    fun recognize(ink: Ink) {
        Recognize(tfRecognize, object : RecogniserListener {
            override fun onResultsReady(recognitionResults: List<String>) {
                // run spellchecking after recognition
                Spellchecker(ink, hunspell, object : SpellcheckListener {
                    override fun onResultsReady(correctWord: String?) {
                        if (correctWord != null) {
                            inkToText[ink.id] = correctWord
                        } else {
                            // run correction
                            inkToText[ink.id] = recognitionResults.first()
                            SpellingCorrection(hunspell, object : SpellingCorrectionListener {
                                override fun onResultsReady(suggestions: List<String>) {
                                    inkIdToCorrected[ink.id] = suggestions
                                    inkIdIsMisspelled[ink.id] = true
                                    postInvalidate()
                                }
                            }).execute(recognitionResults.first())
                        }
                        updateRecognizedText()
                    }
                }).execute(recognitionResults)
            }
        }
        ).execute(ink)
    }

    fun setHunspell(mHunspell: Hunspell) {
        hunspell = mHunspell
    }

    fun setSynthesiser(_synthesizer: Synthesizer) {
        synthesizer = _synthesizer
    }

    fun updateRecognizedText() {
        val recognizedView: TextView = (context as Activity).findViewById(R.id.recognized)
        val text = inkToText.values.joinToString(" ")
        recognizedView.text = text
    }

    interface RecogniserListener {
        fun onResultsReady(recognitionResults: List<String>)
    }

    interface SpellcheckListener {
        fun onResultsReady(correctWord: String?)
    }

    interface SpellingCorrectionListener {
        fun onResultsReady(suggestions: List<String>)
    }

    interface SynthesizeListener {
        fun onResultsReady(newPath: Path?)
    }

    private class Recognize(tf: TensorFlowInferenceInterface, listener: RecogniserListener) :
        AsyncTask<Ink, Void, List<String>>() {
        private val tfReference: WeakReference<TensorFlowInferenceInterface> = WeakReference(tf)
        private val recogniserListener: RecogniserListener = listener
        private val threshold = 0.2

        fun predictionToString(value: ByteArray?): String {
            return value?.let { String(value, Charset.forName("UTF-8")) } ?: ""
        }

        override fun doInBackground(vararg inks: Ink): List<String> {
            val ink = inks[0]
            val normalizedInk = ink.applyNormalizations()
            val features = normalizedInk.features
            val featuresLength = features.shape[0]
            val tfRecognize = tfReference.get() ?: return emptyList()
            tfRecognize.feed("inputs/features:0", features.toFloatArray(), 1, featuresLength.toLong(), 15)
            tfRecognize.feed("inputs/length:0", intArrayOf(featuresLength), 1)
            try {
                tfRecognize.run(arrayOf("output/labels:0", "output/probabilities:0"), true)
                val prob = FloatArray(3)
                tfRecognize.fetch("output/probabilities:0", prob)
                val predictions = Array(3) { arrayOfNulls<ByteArray>(1) }
                tfRecognize.getTensor("output/labels:0").copyTo(predictions)
                val predictionStrings = predictions.map { predictionToString(it[0]!!) }
                val guesses = if (prob.maxOf { it } > threshold)
                    predictionStrings.withIndex().filter { prob[it.index] > threshold }.map { it.value }
                else
                    predictionStrings

                predictionStrings.forEach {
                    Log.d(LOG_TAG, "->${it}")
                }
                Log.d(LOG_TAG, prob.joinToString(", "))
                return guesses
            } catch (e: Exception) {
                Log.e(LOG_TAG, "tensorflow failed", e)
            }
            return emptyList()
        }

        override fun onPostExecute(result: List<String>) {
            recogniserListener.onResultsReady(result)
        }
    }

    /**
     * on device generation can be quite slow
     * */
    private class Synthesize(val synthesizer: Synthesizer, val style: String, val listener: SynthesizeListener) : AsyncTask<String, Void, Path>() {

        override fun doInBackground(vararg text: String?): Path? {
            val word = text[0] ?: return null
            val path = synthesizer.generateFromText(word, style)
            return path
        }

        override fun onPostExecute(result: Path?) {
            listener.onResultsReady(result)
        }

    }

    private class SpellingCorrection(val hunspell: Hunspell, val listener: SpellingCorrectionListener) :
        AsyncTask<String, Void?, List<String>>() {

        fun correctSpelling(word: String): List<String> {
            Log.d(LOG_TAG, "unknown word, making suggestions")
            return hunspell.suggest(word)?.toList() ?: emptyList()
        }

        override fun doInBackground(vararg guesses: String): List<String> {
            return correctSpelling(guesses[0])
        }

        override fun onPostExecute(suggestions: List<String>) {
            listener.onResultsReady(suggestions)
        }
    }

    private class Spellchecker(val ink: Ink, val hunspell: Hunspell, val listener: SpellcheckListener) : AsyncTask<List<String>, Void?, String?>() {

        override fun doInBackground(vararg guesses: List<String>): String? {
            return guesses[0].firstOrNull { !isMisspelled(it) }
        }

        private fun isMisspelled(word: String): Boolean = hunspell.spell(word) == 0

        override fun onPostExecute(correctWord: String?) {
            Log.d(LOG_TAG, "correct word -> $correctWord")
            listener.onResultsReady(correctWord)
        }
    }

    fun substitute(inkId: Int, text: CharSequence) {
        // start generation
        Synthesize(synthesizer, userStyle, object : SynthesizeListener {
            override fun onResultsReady(newPath: Path?) {
                if (newPath == null)
                    return
                val oldPath = inkIdToPath.remove(inkId) ?: return
                val scaleMatrix = Matrix()
                val translateMatrix = Matrix()
                val oldRec = RectF()
                val newRec = RectF()
                oldPath.computeBounds(oldRec, true)
                newPath.computeBounds(newRec, true)
                val scaleX = oldRec.width() / newRec.width()
                val scaleY = oldRec.height() / newRec.height()
                scaleMatrix.setScale(scaleX, scaleY)
                newPath.transform(scaleMatrix)
                newPath.computeBounds(newRec, true)
                val dx = oldRec.centerX() - newRec.centerX()
                val dy = oldRec.bottom - newRec.bottom
                translateMatrix.setTranslate(dx, dy)
                newPath.transform(translateMatrix)
                inkIdToPath[inkId] = newPath
                inkIdIsMisspelled[inkId] = false
                inkToText[inkId] = text.toString()
                updateRecognizedText()
                invalidate()
            }
        }).execute(text.toString())
    }

}

private fun DoubleArray.median(): Double = this.sorted().let {
    if (it.size % 2 == 0)
        (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
    else
        it[it.size / 2]
}
