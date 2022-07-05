package de.dfki.iml.spellink.ink

import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.eigVals
import org.jetbrains.kotlinx.multik.api.linalg.inv
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.ones
import org.jetbrains.kotlinx.multik.ndarray.complex.ComplexDouble
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.fold
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class Transformation(items: List<Double>) {
    val matrix: D2Array<Double>

    /**
     * items: flattened transformation matrix
     */
    init {
        matrix = mk.ndarray(items.slice(0..5) + mk[0.0, 0.0, 1.0], 3, 3)
    }

    companion object {
        fun identity(): Transformation {
            return Transformation(mk[1.0, 0.0, 0.0, 0.0, 1.0, 0.0])
        }

        fun translation(x: Double, y: Double): Transformation {
            return Transformation(mk[1.0, 0.0, x, 0.0, 1.0, y])
        }

        fun rotation(angle: Double): Transformation {
            return Transformation(
                mk[cos(angle), -sin(angle), 0.0,
                        sin(angle), cos(angle), 0.0]
            )
        }

        fun scale(factorX: Double, factorY: Double): Transformation {
            return Transformation(mk[factorX, 0.0, 0.0, 0.0, factorY, 0.0])
        }

        fun scale(factor: Double): Transformation {
            return Transformation(mk[factor, 0.0, 0.0, 0.0, factor, 0.0])
        }

        fun shear(xAngle: Double = 0.0, yAngle: Double = 0.0): Transformation {
            return Transformation(mk[1.0, tan(yAngle), 0.0, tan(xAngle), 1.0, 0.0])
        }

        fun mirror(angle: Double): Transformation {
            return Transformation(
                mk[cos(angle), sin(angle), 0.0,
                        sin(angle), -cos(angle), 0.0]
            )
        }
    }

    fun parameter(): List<Double> {
        return matrix.toDoubleArray().slice(mk[0, 3, 1, 4, 2, 5]) //TODO: check indexing
    }

    fun determinant(): Double {
        return mk.linalg.eigVals(matrix).fold(ComplexDouble.one) { r, it -> it * r }.re
    }

    operator fun rem(other: Transformation): Transformation {
        return Transformation(matrix.dot(other.matrix).toList())
    }

    operator fun rem(other: Ink): Ink {
        val newConcatenatedStrokes = this % other.concatenatedStrokes.asMk
        val sections = mk.math.cumSum(mk.ndarray(mk[0] + other.strokes.map { it.size() }))
    try {
        val newStrokes = sections.toList()
            .zipWithNext()
            .map { Stroke.fromArray(newConcatenatedStrokes[it.first..(it.second)]) }.toMutableList() //split
        return Ink(newStrokes, other.isUncorrupted)
    }catch(e:Exception){
        throw e
    }
    }

    operator fun rem(other: D2Array<Double>): D2Array<Double> {
        //other @ self.matrix[:2, :2].transpose() + self.matrix[:2, 2]
        val s1 = other.dot(matrix[0..2, 0..2].transpose())
        val s2 =
            mk.ones<Double>(s1.shape[0], 2).dot(mk.ndarray(mk[matrix[0, 2], 0.0, 0.0, matrix[1, 2]], 2, 2))
        return s1 + s2
    }

    fun invert(): Transformation {
        return Transformation(mk.linalg.inv(matrix).toList())
    }


}