package de.dfki.iml.spellink

import android.content.res.AssetManager
import android.os.AsyncTask
import de.dfki.iml.features.noncached.Rubine
import de.dfki.iml.features.utils.enums.Feature
import de.dfki.iml.ink.Sketch
import de.dfki.iml.spellink.ink.Ink
import jnpy.Npy
import org.ejml.simple.SimpleMatrix
import kotlin.math.sqrt

typealias DigitalStroke = de.dfki.iml.ink.Stroke

interface SimilarStyleManagerListener {
    fun onResultsReady(minStyle: String)
}

class SimilarStyleManager(private val assetManager: AssetManager, val listener: SimilarStyleManagerListener) : AsyncTask<Ink, Void?, String>() {

    val invCovariance = loadCovarMatrix()
    private val styles = loadStyles()

    private fun loadStyleFeatures(idx: String): FloatArray {
        val stream = assetManager.open("styles/$idx")
        val npy = Npy(stream)
        return npy.floatElements()
    }

    private fun loadStyles(): Map<String, FloatArray> {
        return assetManager.list("styles")!!
            .filter { it.endsWith("features.npy") }
            .map { it.replace("-features.npy", "") to loadStyleFeatures(it) }.toMap()
    }

    private fun loadCovarMatrix(): SimpleMatrix {
        // covariance is a square matrix
        val stream = assetManager.open("styles/rubine_inv_covar.npy")
        val npy = Npy(stream)
        val data = npy.floatElements()
        val rowSize = sqrt(data.size.toFloat()).toInt()
        return SimpleMatrix(rowSize, rowSize, true, data)
    }

    private fun mahalanobis(x: FloatArray, y: FloatArray): Float {
        val xMatrix = SimpleMatrix(x.size, 1, true, x)
        val yMatrix = SimpleMatrix(y.size, 1, true, y)
        val delta = xMatrix.minus(yMatrix)
        val distance = delta.transpose().mult(invCovariance).mult(delta).trace()
        return sqrt(distance).toFloat()
    }

    private fun getSimilarStyle(ink: Ink): String {
        val inkSketch = toDigitalSketch(ink.applyNormalizations())
        val inkFeatures = getFeatures(inkSketch)
        var minStyle = styles.keys.first()
        var minDist = Float.MAX_VALUE
        for ((styleId, styleFeatures) in styles) {
            val dist = mahalanobis(inkFeatures, styleFeatures)
            if (dist < minDist) {
                minDist = dist
                minStyle = styleId
            }
        }
        return minStyle
    }

    private fun toDigitalSketch(ink: Ink): Sketch {
        return Sketch(
            ink.strokes.map {
                val len = it.size()
                val timestamp = LongArray(len) { 0 }
                val pressure = FloatArray(len) { 0f }
                DigitalStroke(it.xCoordFloat, it.yCoordFloat, timestamp, pressure)
            })
    }

    private fun getFeatures(sketch: Sketch): FloatArray {
        val rubineFeatures = Rubine()
        return rubineFeatures.calculateFeatureVector(
            sketch, arrayOf(
                Feature.rubine_f01,
                Feature.rubine_f02,
                Feature.rubine_f03,
                Feature.rubine_f04,
                Feature.rubine_f05,
                Feature.rubine_f06,
                Feature.rubine_f07,
                Feature.rubine_f08,
                Feature.rubine_f09,
                Feature.rubine_f10,
                Feature.rubine_f11
            )
        )
    }

    override fun doInBackground(vararg ink: Ink): String {
        return getSimilarStyle(ink[0])
    }

    override fun onPostExecute(minStyle: String) {
        listener.onResultsReady(minStyle)
    }

}