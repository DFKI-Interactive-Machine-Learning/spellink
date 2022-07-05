package de.dfki.iml.spellink.ink

import org.ejml.data.DMatrixRMaj
import org.ejml.dense.row.CommonOps_DDRM
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sign


fun Iterable<Point>.removeAdjacent(): List<Point> {
    var last: Point? = null
    return mapNotNull {
        if (it.eps_equals(last)) {
            null
        } else {
            last = it
            it
        }
    }
}

fun NDArray<Double, D1>.pow(b: Double): NDArray<Double, D1> {
    for (ax0 in this.indices)
        this[ax0] = this[ax0].pow(b)
    return this
}

fun <T : Number> NDArray<T, D1>.sign(): D1Array<Int> {
    return this.map { it.toDouble().sign.toInt() }.asD1Array()
}

fun <D : Dimension> NDArray<Double, D>.greather(other: NDArray<Double, D>): NDArray<Int, D> {
    return (this - other).map { (it > 0).compareTo(false) }
}

fun <D : Dimension> NDArray<Double, D>.lower(other: NDArray<Double, D>): NDArray<Int, D> {
    return (this - other).map { (it < 0).compareTo(false) }
}

fun <D : Dimension> NDArray<Double, D>.lower(n: Double): NDArray<Int, D> {
    return this.map { (it < n).compareTo(false) }
}

fun <T, D : Dimension> NDArray<T, D>.equalsTo(n: T): NDArray<Int, D> {
    return this.map { (it == n).compareTo(false) }
}

fun <D : Dimension> NDArray<Double, D>.equalsTo(other: NDArray<Double, D>): NDArray<Int, D> {
    return (this - other).map { (it == 0.0).compareTo(false) }
}

fun <D : Dimension> NDArray<Double, D>.mod(dm: Double): NDArray<Double, D> {
    return this.map { it.mod(dm) }
}

fun NDArray<Double, D2>.arctan2(): D1Array<Double> {
    //shape must be n*2
    assert(this.shape[1] == 2)
    val data = DoubleArray(this.shape[0])
    for (ax0 in 0 until this.shape[0])
        data[ax0] = atan2(this[ax0, 1], this[ax0, 0])
    return mk.ndarray(data)
}

@JvmName("arctan2d1")
fun NDArray<Double, D1>.arctan2(other: NDArray<Double, D1>): NDArray<Double, D1> {
    return mk.d1array(this.size) { i -> atan2(this[i], other[i]) }
}

@JvmName("arctan2d2")
fun NDArray<Double, D2>.arctan2(other: NDArray<Double, D2>): NDArray<Double, D2> {
    return mk.d2arrayIndices(this.shape[0], this.shape[1]) { i, j -> atan2(this[i, j], other[i, j]) }
}

fun <T> NDArray<T, D2>.fillDiag(i: Int, value: T): NDArray<T, D2> {
    for (ax0 in 0..this.shape[0] - 1) {
        if (ax0 + i >= 0 && ax0 + i < this.shape[1])
            this[ax0, ax0 + i] = value
    }
    return this
}


fun localExtrema(array: D1Array<Double>, isMax: Boolean): List<Double> {
    var candidates = mutableListOf<Double>()
    var res = mutableListOf<Double>()
    candidates.add(array.first())
    val comp = if (isMax) 1 else -1
    for (i in 1 until array.size) {
        if (array[i].compareTo(array[i - 1]) * comp > 0) {
            candidates.clear()
            candidates.add(array[i])
        }
        if (array[i].compareTo(array[i - 1]) * comp < 0) {
            res.addAll(candidates)
            candidates.clear()
        }
        if (array[i] == array[i - 1] && array[i] == candidates.lastOrNull()) {
            candidates.clear() //only to align with python implementation
//            candidates.add(array[i]) TODO: fix
        }
    }
    res.addAll(candidates)
    return res
}

fun localMin(array: D1Array<Double>): List<Double> = localExtrema(array, false)
fun localMax(array: D1Array<Double>): List<Double> = localExtrema(array, true)

@JvmName("diff1")
fun NDArray<Double, D1>.diff(): D1Array<Double> {
    return this[1..this.size] - this[0 until this.size]
}

@JvmName("diff2")
fun NDArray<Double, D2>.diff(): D2Array<Double> {
    return this[1..this.shape[0]] - this[0 until this.shape[0]]
}

fun outer(a: DoubleArray, b: DoubleArray): D2Array<Double> {
    val rows = a.size
    val cols = b.size
    return mk.d2arrayIndices(rows, cols) { i, j -> a[i] * b[j] }
}

/**
 * convert 2d array of shape (2,n) to list of points
 */
fun arrayToPoints(a: Array<DoubleArray>): List<Point> {
    return a[0].zip(a[1]).map { (x, y) -> Point(x, y) }
}


fun vander(x: NDArray<Double, D1>, order: Int): D2Array<Double> {
    var res = mk.stack(x, mk.ones(x.size))
    for (i in 2 until order) {
        val px = x.copy().pow(i.toDouble()).reshape(1, x.size)
        res = px.cat(res)
    }
    return res.transpose()
}

/**
 * Least squares polynomial fit
 */

fun toMatrix(a: D2Array<Double>): Array<DoubleArray> {
    val arr = Array(a.shape[0]) { DoubleArray(a.shape[1]) }
    for (i in 0 until a.shape[0])
        for (j in 0 until a.shape[1])
            arr[i][j] = a[i, j]
    return arr
}

fun <D : Dimension> solve(a: D2Array<Double>, b: NDArray<Double, D>): D2Array<Double> {
    val A = DMatrixRMaj(toMatrix(a))
    val B = if (b.shape.size == 1)
        DMatrixRMaj(b.toDoubleArray())
    else if (b.shape.size == 2)
        DMatrixRMaj(toMatrix(b.asD2Array()))
    else throw IllegalArgumentException("Wrong shape for b while solving ax=b")
    val x = DMatrixRMaj()
    val l = CommonOps_DDRM.solve(A, B, x)
    if (!l)
        println("Error in solve, singular matrix")
    return mk.ndarray(x.data, x.numRows, x.numCols)
}

fun <D : Dimension> polyfit(x: D1Array<Double>, y: NDArray<Double, D>, deg: Int): NDArray<Double, D2> {
    val order = deg + 1
    var lhs = vander(x, order)
    val rhs = y
    val scale = mk.math.sumD2(lhs * lhs, axis = 0).pow(0.5)
    val xlen = x.size
    val scaleMx = scale.repeat(xlen).reshape(xlen, 2)
    lhs = lhs / scaleMx
    val c = solve(lhs, rhs)
    val scaleM2 = scale.repeat(c.shape[1]).reshape(c.shape[1], 2)
    return (c.transpose() / scaleM2).transpose()
}

fun <T> fillMatrix(x: MultiArray<T, D1>, n: Int, axis: Int = 0): D2Array<T> {
    val m = x.repeat(n).reshape(n, x.size)
    return if (axis == 0)
        m
    else
        m.transpose()
}