package de.dfki.iml.spellink.de.dfki.iml.spellink

import android.content.res.AssetManager
import android.graphics.Path
import de.dfki.iml.spellink.ArrayWrapper
import de.dfki.iml.spellink.FloatPoint
import jnpy.Npy
import org.tensorflow.contrib.android.TensorFlowInferenceInterface
import java.nio.FloatBuffer

class Synthesizer(private val assetManager: AssetManager) {
    private val tf: TensorFlowInferenceInterface = TensorFlowInferenceInterface(assetManager, "synthesis_optimized_graph.pb")
    private val alphabet = setOf(
        '\u0000', ' ', '!', '"', '#', '\'', '(', ')', ',', '-', '.',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';',
        '?', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K',
        'L', 'M', 'N', 'O', 'P', 'R', 'S', 'T', 'U', 'V', 'W', 'Y',
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
        'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
        'y', 'z'
    )
    private val stylePhrase = "quick brown fox jumped over the lazy dog" // currently not used, style-features and style-strokes contain different phrases
    private val alphaToNum = alphabet
        .mapIndexed { idx, value -> value to idx }
        .toMap()

    private fun loadStyle(idx: String): FloatArray {
        val stream = assetManager.open("styles/$idx")
        val npy = Npy(stream)
        val data = npy.floatElements()
        return data
    }

    private fun loadStyleChars(idx: String): String {
        return assetManager.open("styles/$idx")
            .bufferedReader().use {
                it.readText()
            }
    }

    private fun loadStylesChars(): Map<String, String> {
        return assetManager.list("styles")!!
            .filter { it.endsWith("chars.txt") }
            .map { it.replace("-chars.txt", "") to loadStyleChars(it) }.toMap()
    }

    private fun loadStyles(): Map<String, FloatArray> {
        return assetManager.list("styles")!!
            .filter { it.endsWith("strokes.npy") }
            .map { it.replace("-strokes.npy", "") to loadStyle(it) }.toMap()
    }

    private val styles = loadStyles()
    private val styleChars = loadStylesChars()

    private fun encode(text: String): IntArray {
        return (text + '\u0000').filter { alphabet.contains(it) }
            .map { alphaToNum[it]!! }
            .toIntArray()
    }

    private fun getPrime(styleIdx: String): Pair<FloatArray, IntArray> {
        val prime = styles[styleIdx]!!
        val len = prime.size
        val paddedPrime = FloatArray(2200 * 3)
        paddedPrime.fill(0f)
        System.arraycopy(prime, 0, paddedPrime, 0, prime.size)
        return Pair(paddedPrime, intArrayOf(len / 3))
    }

    fun getChars(text: String, styleIdx: String): Pair<IntArray, IntArray> {
        val sText = styleChars[styleIdx] + " " + text
        val chars = encode(sText)
        val len = chars.size
        val paddedChars = IntArray(120)
        System.arraycopy(chars, 0, paddedChars, 0, chars.size)
        return Pair(paddedChars, intArrayOf(len))
    }

    private fun generate(text: String, styleIdx: String): Array<FloatArray> {
        val prime = true
        val numSamples = 1 //TODO use 3 to generate samples for all suggestions at once
        val sampleTsteps = 40 * text.length
        val bias = floatArrayOf(0.75f)
        val (xPrimeData, xPrimeLen) = getPrime(styleIdx)
        val xPrime = FloatBuffer.wrap(xPrimeData)
        val (chars, cLen) = getChars(text, styleIdx)
        tf.feed("Placeholder_7:0", prime)
        tf.feed("Placeholder_8:0", xPrime, 1, 2200, 3)
        tf.feed("Placeholder_9:0", xPrimeLen, 1)
        tf.feed("Placeholder_6:0", numSamples)
        tf.feed("Placeholder_5:0", sampleTsteps)
        tf.feed("Placeholder_3:0", chars, 1, 120)
        tf.feed("Placeholder_4:0", cLen, 1)
        tf.feed("PlaceholderWithDefault:0", bias, 1)
        tf.run(arrayOf("cond/Merge:0"), true)
        val outTensor = tf.getTensor("cond/Merge:0")
        val outArray = ArrayWrapper.getArray(outTensor.shape())
        outTensor.copyTo(outArray)
        //outArray contains offsets, transform back to coords
        if (outArray.isEmpty()) return arrayOf()
        return outArray[0] //return the first sample, correct this if numSamples>1
    }

    fun generateFromText(text: String, styleIdx: String): Path {
        val offsets = generate(text, styleIdx)
        val points = toPoints(offsets)
        return toPath(points)
    }

    private fun toPath(strokes: List<List<FloatPoint>>): Path {
        val path = Path()
        for (stroke in strokes) {
            path.moveTo(stroke.first().x, stroke.first().y)
            stroke.zipWithNext { f, n ->
                path.quadTo(f.x, f.y, (n.x + f.x) / 2, (n.y + f.y) / 2)
            }
        }
        return path
    }

    private fun toPoints(array: Array<FloatArray>): MutableList<List<FloatPoint>> {
        val result = mutableListOf<List<FloatPoint>>()
        var stroke = mutableListOf<FloatPoint>()
        var xcoord = 0f
        var ycoord = 0f
        for (row in array) {
            xcoord += row[0]
            ycoord += row[1]
            val p = row[2]
            stroke.add(FloatPoint(xcoord, -ycoord))
            if (p == 1f) {
                result.add(stroke.toList())
                stroke = mutableListOf()
            }
        }
        if (stroke.isNotEmpty())
            result.add(stroke.toList())
        return result
    }

}
