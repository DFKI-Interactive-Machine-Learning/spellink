package de.dfki.iml.spellink.ink

import org.jetbrains.kotlinx.multik.api.d1array
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import java.util.Collections.max
import java.util.Collections.min
import kotlin.math.PI
import kotlin.math.roundToInt

class Normalization() {

    val normalization = listOf(
        ::normalizedSkewAndMean,
        ::normalizedSkewAndMean,
        ::normalizedBaseline,
        ::normalizedWidth,
        ::normalizedLeft
    )

    fun apply(ink: Ink): Ink {
        var newInk = ink
        for (normalization in normalization) {
            newInk = normalization(newInk)
        }
        return newInk
    }


    fun normalizedMean(ink: Ink): Transformation {
        val mean: NDArray<Double, D1> = mk.stat.mean(ink.concatenatedStrokes.asMk, axis = 0)
        val transformation = Transformation.translation(-mean[0], -mean[1])
        return transformation
    }

    fun normalizedSkewAndMean(ink: Ink): Ink {
        val partLength = ink.partLength

        //    try:
        //    except (ValueError, np.RankWarning, RuntimeWarning, FloatingPointError):
        //     TODO(DV): single dot need special handling
        //    raise NormalizationWarning("Single dot is not Normalizeable")
        val coeffs = polyfit(partLength, ink.concatenatedStrokes.asMk, 1) // line
        val polyX = mk.d1array(coeffs.shape[0]){coeffs[0,it]}
        val polyY = mk.d1array(coeffs.shape[0]){coeffs[1,it]}
        val n = mk.linalg.norm(polyX.reshape(polyX.size, 1))
        if (n < 0.1)
            print("Skew detection is ambigous (${n})")

        var angle = Math.atan2(polyX[1], polyX[0])

        val transformation = if (n < 0.23) {
            angle = (angle / (PI / 2)).roundToInt() * PI / 2
            val transformationSkew = Transformation.rotation(-angle)
            val transformationMean = normalizedMean(ink)

            transformationMean % transformationSkew
        } else {
            (Transformation.rotation(-angle) %
                    Transformation.translation(-polyY[0], -polyY[1]))
        }
        return transformation % ink
    }

    fun normalizedLeft(ink: Ink): Ink {
        val minX = ink.boundaryBox[0]
        val transformation = Transformation.translation(-minX, 0.0)
        return transformation % ink
    }

    private fun tiltedInkLength(ink: Ink, angle: Double): Double {
        val tiltedInk = Transformation.shear(yAngle = angle) % ink
        return tiltedInk.length()
    }

    /**
     * not used
     */
    fun normalizedSlant(ink: Ink): Ink {
        TODO("implement minimize as in scipy.optimize.minimize")
        //    val angle = minimize(lambda a : tilted_ink_length (ink, a), 0).x
        //    val transformation = Transformation.shear(y_angle = angle)
        //    return transformation @ ink, transformation
    }

    fun normalizedBaseline(ink: Ink): Ink {
        val (minima, maxima) = ink.getExtrema()
        var height = 1.0
        var baseline = 0.0
        if (!(minima.isEmpty() || maxima.isEmpty()) && min(minima) != max(maxima)) {
            val mn = minima.filter { it <= 1E-12 }
            val mx = maxima.filter { it >= -1E-12 }
            baseline = mn.toDoubleArray().average()
            val meanline = mx.toDoubleArray().average()
            height = meanline - baseline
        }
        val transformation = (Transformation.scale(1.0 / height) %
                Transformation.translation(0.0, -baseline))
        val transformedInk = transformation % ink

        if (transformedInk.boundaryBox[2] < -4)
            println("Baseline normalization failed")

        if (transformedInk.boundaryBox[3] > 4)
            println("Baseline normalization failed")
        return transformedInk
    }

    fun findIntersections(ink: Ink, y: Double): Int {
        val isLower = ink.concatenatedStrokes.yCoord.map { it < y }
        var intersections = 0
        for (i in 1 until isLower.size)
            if (isLower[i] != isLower[i - 1]) intersections += 1
        // in python there is off by one error
        // return intersections+1 instead of intersections
        return intersections+1
    }

    fun normalizedWidth(ink: Ink): Ink {
        val intersections = findIntersections(ink, 0.5)
        val bb = ink.boundaryBox
        val minX = bb[0]
        val maxX = bb[1]
        val width = maxX - minX
        val p = intersections / (4 * width)
        val scaleWidth =
            if ((intersections >= 2) && width > 0)
                intersections / (4 * width)
            else
                1.0
        val transformation = Transformation.scale(scaleWidth, 1.0)
        return transformation % ink
    }
}