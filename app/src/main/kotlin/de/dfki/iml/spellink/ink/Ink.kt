package de.dfki.iml.spellink.ink

import fitpack.Spline
import fitpack.Splprep
import org.jetbrains.kotlinx.multik.api.*
import org.jetbrains.kotlinx.multik.api.math.cos
import org.jetbrains.kotlinx.multik.api.math.cumSum
import org.jetbrains.kotlinx.multik.api.math.sin
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt


data class Point(val x: Double, val y: Double) {
    constructor(x: Float, y: Float) : this(x.toDouble(), y.toDouble())

    private val eps = 1E-7

    fun eps_equals(point: Point?): Boolean {
        return point != null && abs(point.x - x) < eps && abs(point.y - y) < eps
    }

    fun toArray(): DoubleArray {
        return doubleArrayOf(x, y)
    }
}

class Stroke(_points: List<Point>) {
    var points: List<Point> = _points.toList()

    companion object {
        fun fromArray(array: MultiArray<Double, D2>): Stroke {
            assert(array.shape[1] == 2) { "Wrong array shape" }
            val points = mutableListOf<Point>()
            for (i in 0 until array.shape[0])
                points.add(Point(array[i][0], array[i][1]))
            return Stroke(points)
//            IntRange(0, array.shape[0]).map { array[it] }.map { Point(it.first(), it.last()) }
        }
    }

    private val eps = 1E-7
    val dotsPerUnit: Int = 8
    val xCoord = points.map { it.x }.toDoubleArray()
    val yCoord = points.map { it.y }.toDoubleArray()
    val xCoordFloat = points.map { it.x.toFloat() }.toFloatArray()
    val yCoordFloat = points.map { it.y.toFloat() }.toFloatArray()
    val xCoordMk = mk.ndarray(points.map { it.x })
    val yCoordMk = mk.ndarray(points.map { it.y })
    val asMk by lazy {
        mk.d2arrayIndices(points.size, 2) { i, j ->
            when (j) {
                0 -> points[i].x
                1 -> points[i].y
                else -> throw IllegalArgumentException("Wrong index")
            }
        }
    }
    val spline: Spline by lazy {
        createSpline()
    }
    val knots: DoubleArray by lazy {
        val splineLen = spline.t.last() //get the max value of the parameter t
        val knots = max(2, (dotsPerUnit * splineLen).toInt())
        linspace(0.0, splineLen, knots)
    }

    fun append(new_stroke: Stroke) {
        this.points += new_stroke.points
    }

    fun removeAdjacent() {
        points = points.removeAdjacent()
    }

    fun last(): Point? {
        return points.lastOrNull()
    }

    fun first(): Point? {
        return points.firstOrNull()
    }

    fun size() = points.size

    fun minX() = points.minOf { it.x }
    fun maxX() = points.maxOf { it.x }
    fun minY() = points.minOf { it.y }
    fun maxY() = points.maxOf { it.y }
    fun boundaryBox() = listOf(minX(), maxX(), minY(), maxY())

    private fun linspace(start: Double, stop: Double, samples: Int): DoubleArray {
        return mk.linspace<Double>(start, stop, samples).toDoubleArray()
    }

    fun createSpline(): Spline {
        assert(points.isNotEmpty())
        var x: MultiArray<Double, D1> = xCoordMk
        var y: MultiArray<Double, D1> = yCoordMk
        if (size() < 4) {
            val vec1 = linspace(0.0, 1.0, 4)
            val vec2 = linspace(1.0, 0.0, 4)
            val strokeNew = outer(vec1, points.first().toArray()) + outer(vec2, points.last().toArray())
            if (points.first() == points.last()) {
                val z = mk.zeros<Double>(4, 1)
                z + z
                val zz = z.append(mk.ndarray(linspace(0.0, -0.1, 4), 4, 1), axis = 1)
                strokeNew -= zz
            }
            x = strokeNew.view(1, 1)
            y = strokeNew.view(1, 1)
        }
        val dst = dist(x, y)
        val distAlong = mk.zeros<Double>(1).append(dst.cumSum())
        val matrix = arrayOf(x.toDoubleArray(), y.toDoubleArray())
        val spline = Splprep.splprep(matrix, null, distAlong.toDoubleArray(), null, null, 3, 0, 0.0, null, null, 0);
        return spline
    }

    private fun dist(x: MultiArray<Double, D1>, y: MultiArray<Double, D1>): D1Array<Double> {
        //TODO: refactor using length()
        val arr = mk.zeros<Double>(x.size - 1)//DoubleArray(x.size - 1)
        for (i in arr.indices) {
            arr[i] = sqrt((x[i + 1] - x[i]) * ((x[i + 1] - x[i])) + (y[i + 1] - y[i]) * (y[i + 1] - y[i]))
        }
        return arr
    }

    fun length(): Double {
        return (xCoordMk.diff().pow(2.0) + xCoordMk.diff().pow(2.0)).pow(0.5).sum()
    }

    fun xDistance(other: Stroke): Double {
        val bbox = boundaryBox()
        val otherBbox = other.boundaryBox()
        return max(bbox[0] - otherBbox[1], otherBbox[0] - bbox[1])
    }
}

class Ink(_strokes: MutableList<Stroke>, var isUncorrupted: Boolean = false) {
    private val eps = 1E-8
    private fun randomId() = List(8) { ('0'..'9').random() }.joinToString("").toInt()

    //    private fun randomId() = List(6) { (('a'..'z') + ('A'..'Z') + ('0'..'9')).random() }.joinToString("")
    val id = randomId()
    val concatenatedStrokes: Stroke
    var strokes: MutableList<Stroke>
    val featureNames = listOf(
        "x",
        "y",
        "high-pass filtered x",
        "stroke start",
        "stroke stop",
        "radius",
        "writing direction x",
        "writing direction y",
        "tangent x",
        "tangent y",
        "below a stroke",
        "above a stroke",
        "delta x",
        "delta y",
        "intersection",
//        "time"
    )

    init {
        if (!isUncorrupted) {
            strokes = update(_strokes)
            isUncorrupted = true
        } else {
            strokes = _strokes.toMutableList()
        }
        concatenatedStrokes = Stroke(strokes.flatMap { it.points })
    }

    fun length(): Double {
        return strokes.sumOf { it.length() }
    }

    val partLength by lazy {
        val deltas = concatenatedStrokes.asMk.diff()
        val distances = mk.math.sumD2(deltas * deltas, axis = 1).pow(0.5).cumSum()
        mk.zeros<Double>(1).append(distances)
    }
    val splines: List<Spline> by lazy {
        strokes.map { it.spline }
    }
    val knots: List<DoubleArray> by lazy {
        strokes.map { it.knots }
    }

    private val connectedStroke: Stroke by lazy {
        val res = splines.zip(knots)
            .map { (spline, knot) -> Splprep.splev(knot, spline, 0, 0) }
            .flatMap { arrayToPoints(it) }
        Stroke(res.toMutableList())
    }
    val xPosition by lazy { mk.ndarray(connectedStroke.xCoord) }
    val yPosition by lazy { mk.ndarray(connectedStroke.yCoord) }
    val strokeDer by lazy {
        val res = splines.zip(knots)
            .map { (spline, knot) -> Splprep.splev(knot, spline, 1, 0) }
            .flatMap { arrayToPoints(it) }
        Stroke(res.toMutableList())
    }

    val strokeCur by lazy {
        val res = splines.zip(knots)
            .map { (spline, knot) -> Splprep.splev(knot, spline, 2, 0) }
            .flatMap { arrayToPoints(it) }
        Stroke(res.toMutableList())
    }

    val tangent by lazy {
        val dm = eps + mk.math.sumD2(strokeDer.asMk * strokeDer.asMk, axis = 1)
        dm.pow(0.5)
        strokeDer.asMk / mk.stack(dm, dm).transpose()
    }

    private val radiusOfCurvature: NDArray<Double, D1> by lazy {
        val dm = (eps + strokeDer.xCoordMk * strokeCur.yCoordMk -
                strokeDer.yCoordMk * strokeCur.xCoordMk)
        val num = mk.math.sumD2(strokeDer.asMk * strokeDer.asMk, axis = 1)
        num.pow(3.0 / 2)
        num / dm
    }

    private val radiusNormalized: NDArray<Double, D1> by lazy {
        (radiusOfCurvature.sign().asType<Double>()) / (1.0 + abs(radiusOfCurvature))
    }

    val deltaY: D1Array<Double> by lazy { connectedStroke.yCoordMk.diff() }

    val deltaYExtended by lazy { mk.zeros<Double>(1).append(deltaY) }

    val deltaX: D1Array<Double> by lazy { connectedStroke.xCoordMk.diff() }

    val deltaXExtended by lazy { mk.zeros<Double>(1).append(deltaX) }

    val pressure: D2Array<Double> by lazy {
        val cuts = knots.map { it.size }
        val pressure = mk.ones<Double>(cuts.sum(), 2)
        for (i in mk.ndarray(cuts).cumSum()) {
            pressure[i - 1, 1] = 0.0
            pressure[i % pressure.shape[0], 0] = 0.0
        }
        pressure
    }

    val angleDirection by lazy {
        tangent.arctan2()
    }
    val writingDirection by lazy {
        mk.stack(angleDirection.cos(), angleDirection.sin()).transpose()
    }

/*
    @cached_property
    def _angle_curvature(self):
        """ The difference of to consecutives angles giving the writing direction
        """
        tmp = np.zeros_like(self._angle_direction)
        tmp[1:-1] = self._angle_direction[2:]-self._angle_direction[1:-1]
        return tmp
     */

    val roughTangent by lazy {
        val roughTangent = mutableListOf<List<Double>>()
        for ((knot, spline) in knots.zip(splines)) {
            val knotsBetween = mk.d1array(knot.size + 1) { i ->
                when (i) {
                    0 -> knot[0]
                    knot.size -> knot[knot.size - 1]
                    else -> (knot[i - 1] + knot[i]) / 2
                }
            }
            val y = Splprep.splev(knotsBetween.toDoubleArray(), spline, 0, 0)

            val posBetween = mk.d2arrayIndices(y[0].size, y.size) { i, j -> y[j][i] }
            var dposBetween = posBetween.diff()
            val dsum = mk.math.sumD2(dposBetween * dposBetween, 1)
            val dm = dsum.pow(0.5) + eps
            val dd = mk.stack(dm, dm).transpose()
            dposBetween = dposBetween.div(dd)

            dposBetween.toListD2().forEach { roughTangent.add(listOf(it[0], it[1])) }
        }
        mk.d2arrayIndices(roughTangent.size, 2) { i, j -> roughTangent[i][j] }
    }

    val boundaryBox = concatenatedStrokes.boundaryBox()

    private fun update(strokes: MutableList<Stroke>): MutableList<Stroke> {
        val strokesCopy = strokes.toMutableList()
        val connectedStrokes = connectGaplessStrokes(strokesCopy)
        return removeDuplicatedPoints(connectedStrokes)
    }

    private fun removeDuplicatedPoints(strokes: MutableList<Stroke>): MutableList<Stroke> {
        strokes.forEach { it.removeAdjacent() }
        return strokes
    }

    private fun connectGaplessStrokes(strokes: MutableList<Stroke>): MutableList<Stroke> {
        var idx = 0
        for (nextStroke in strokes.drop(1)) {
            val firstStroke = strokes[idx]
            if (firstStroke.last() == nextStroke.first()) {
                firstStroke.append(nextStroke)
                strokes.remove(nextStroke)
            } else {
                idx += 1
            }
        }
        return strokes
    }

    fun xLowPass(): D1Array<Double> {
        var lowPass = mk.zeros<Double>(0)
        var begin = 0
        for (knot in knots) {
            val end = begin + knot.size
            val knotNd = mk.ndarray(knot)
            val coeff = polyfit(knotNd, xPosition[begin..end] as D1Array<Double>, 1)
            val y = coeff[0, 0] * knotNd + coeff[1, 0]
            lowPass = lowPass.append(y)
            begin = end
        }
        assert(begin == xPosition.size)
        return lowPass
    }

    private val xLowPassFiltered by lazy {
        xPosition - xLowPass()
    }

    private val encased: NDArray<Double, D2> by lazy {
        val x = xPosition
        val y = yPosition
        val a = deltaY / deltaX
        val b = y[0 until y.size] - a * x[0 until y.size]
        val rhs = outer(a.toDoubleArray(), x.toDoubleArray()).transpose() + fillMatrix(b, x.size, 0)
        val pointIsAbove = fillMatrix(y, b.size, 1).greather(rhs)
        val conditions =
            (pressure[0..pressure.shape[0] - 1, 1] * pressure[1..pressure.shape[0], 0]).equalsTo(mk.ones(pressure.shape[0] - 1))
        val x1 = fillMatrix(x, x.size - 1, 1)
        val x2 = fillMatrix(x[0..x.size - 1], x.size, 0)
        val x3 = fillMatrix(x[1..x.size], x.size, 0)
        val tmp = mk.zeros<Double>(x.size, x.size - 1).greather((x1 - x2) * (x1 - x3))
        val cc = fillMatrix(conditions, x.size, 0)// .repeat().reshape(x.size, conditions.size)
        val encasedFromBelow = mk.math.sumD2(pointIsAbove * tmp * cc, 1).sign()
        val notPointIsAbove = mk.ones<Int>(pointIsAbove.shape[0], pointIsAbove.shape[1]) - pointIsAbove
        val encasedFromTop = mk.math.sumD2(notPointIsAbove * tmp * cc, 1).sign()
        mk.stack(encasedFromBelow, encasedFromTop).transpose().asType<Double>()
    }

    val intersection: NDArray<Double, D1> by lazy {
        val phi = deltaX.arctan2(deltaY)
        val xDiffs = fillMatrix(xPosition, xPosition.size - 1, 0) -
                fillMatrix(xPosition[0..xPosition.size - 1], xPosition.size, 1)
        val yDiffs = fillMatrix(yPosition, yPosition.size - 1, 0) - fillMatrix(
            yPosition[0..yPosition.size - 1], yPosition.size, 1
        )

        val phiDiffs = fillMatrix(phi, xDiffs.shape[1], 1) - xDiffs.arctan2(yDiffs)

        val relativeOrientation = phiDiffs.mod(2 * PI).lower(PI)

        var conditions = (relativeOrientation[sl.bounds, 0..relativeOrientation.shape[1] - 1] +
                relativeOrientation[sl.bounds, 1..relativeOrientation.shape[1]]).equalsTo(1) //xor
        val cc = (pressure[0..pressure.shape[0] - 1, 1] * pressure[1..pressure.shape[0], 0]).equalsTo(1.0)
        conditions = conditions * fillMatrix(cc, conditions.shape[0])

        conditions.fillDiag(0, 0)
        conditions.fillDiag(1, 0)
        conditions.fillDiag(-1, 0)

        conditions = conditions * conditions.transpose()
        val cond = mk.math.sumD2(conditions, 0).sign()
        val ret = (mk.zeros<Int>(1).append(cond) + cond.append(mk.zeros(1))).sign()
        ret.asType<Double>()
    }

    fun collectFeatures(): List<MultiArray<Double, D1>> {
        return listOf(
            xPosition,
            yPosition,
            xLowPassFiltered,
            pressure[sl.bounds, 0],
            pressure[sl.bounds, 1],
            radiusNormalized,
            writingDirection[sl.bounds, 0],
            writingDirection[sl.bounds, 1],
            roughTangent[sl.bounds, 0],
            roughTangent[sl.bounds, 1],
            encased[sl.bounds, 0],
            encased[sl.bounds, 1],
            deltaXExtended,
            deltaYExtended,
            intersection
        )
    }

    /**
     * current model accept floats
     */
    val features: D2Array<Float>
        get() {
            val featuresList = collectFeatures()
            return mk.d2arrayIndices(featuresList[0].size, featuresList.size) { i, j ->
                featuresList[j][i].toFloat()
            }
        }

    fun getExtrema(): Pair<List<Double>, List<Double>> {
        val minima = strokes.flatMap { localMin(it.yCoordMk) }
        val maxima = strokes.flatMap { localMax(it.yCoordMk) }
        return Pair(minima, maxima)
    }

    fun applyNormalizations(): Ink {
        val n = Normalization()
        return n.apply(this)
    }
}