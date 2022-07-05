package de.dfki.iml.spellink

import android.content.Context
import android.graphics.*
import android.os.CountDownTimer
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import de.dfki.iml.spellink.ink.Ink
import de.dfki.iml.spellink.ink.Point
import de.dfki.iml.spellink.ink.Stroke
import java.nio.file.Files
import kotlin.math.abs

data class DrawingStroke(val stroke: Stroke, val path: Path) {
    private fun randomId() = List(8) { ('0'..'9').random() }.joinToString("").toInt()
    val id = randomId()
}

open class CanvasView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    companion object {
        const val TOUCH_TOLERANCE = 2f
        const val STROKE_SIZE = 5f
        const val LOG_TAG = "SCRIBE"
    }

    private var stylusPointerIndex: Int = 0
    var position: MotionEvent.PointerCoords = MotionEvent.PointerCoords()
    private lateinit var bitmap: Bitmap
    private lateinit var bitmapCanvas: Canvas
    private var paintScreen: Paint = Paint()
    private val strokePoints: MutableList<Point> = mutableListOf()
    val strokes: MutableList<Stroke> = mutableListOf()
    var cTimer: CountDownTimer? = null
    val inkIdToPath: MutableMap<Int, Path> = mutableMapOf()
    val inkMap: MutableMap<Int, Ink> = mutableMapOf()


    val paintLine = Paint().also {
        it.color = Color.BLACK
        it.style = Paint.Style.STROKE
        it.strokeWidth = STROKE_SIZE
        it.strokeCap = Paint.Cap.ROUND
        it.isAntiAlias = true
    }

    private var currentPath: Path? = null
    private var previousPoint: Point? = null

    open fun clear() {
        currentPath = null
        previousPoint = null
        bitmap.eraseColor(Color.WHITE)
        inkMap.clear()
        inkIdToPath.clear()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmap)
        bitmap.eraseColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, 0f, 0f, paintScreen)
        // draw all inks we have
        for (path in inkIdToPath.values) {
            val line = paintLine
            canvas.drawPath(path, line)
        }
        // draw current stroke (which can be not completed yet)
        currentPath?.let {
            canvas.drawPath(it, paintLine)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val toolType = event.getToolType(pointerIndex)
        //consider events from stylus only
        if (toolType == MotionEvent.TOOL_TYPE_STYLUS) {
            if (action == MotionEvent.ACTION_DOWN ||
                action == MotionEvent.ACTION_POINTER_DOWN
            ) {
                stylusPointerIndex = pointerIndex// event.getPointerId(pointerIndex)
                touchStarted(event.getX(pointerIndex), event.getY(pointerIndex))
            } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP
            ) {
                touchEnded()
            } else if (action == MotionEvent.ACTION_MOVE) {
                touchMoved(event)
            }
            invalidate()
            return true
        }
        event.getPointerCoords(0, position)
        return super.onTouchEvent(event)
    }

    private fun saveInks() {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).resolve("style_ink.txt").toPath()
        val lines = mutableListOf<String>()
        for (stroke in strokes) {
            for (idx in stroke.points.indices) {
                val point = stroke.points[idx]
                val isLast = if (idx == stroke.points.size - 1) 1 else 0
                lines.add("${point.x},${point.y},${isLast}")
            }
        }
        Files.write(path, lines)
        Toast.makeText(context, "Style saved successfully", Toast.LENGTH_LONG).show()
        Log.d(LOG_TAG, "Saved in $path")

    }

    private fun touchMoved(event: MotionEvent) {
        // no support for multiple gestures, not required now
        // we assume there is only one stylus
//        val pointerIndex = event.findPointerIndex(pointerId) // in case of multitouch event find the proper touch

        val points = mutableListOf<Point>()
        Log.d(LOG_TAG, "HIST SIZE ${event.historySize}")
        for (i in 0 until event.historySize) {
            points.add(Point(event.getHistoricalX(stylusPointerIndex, i), event.getHistoricalY(stylusPointerIndex, i)))
        }
        points.add(Point(event.getX(stylusPointerIndex), event.getY(stylusPointerIndex)))
        val path = currentPath ?: return
        var lastPoint = previousPoint ?: return
        for (point in points) {
            val newX = point.x
            val newY = point.y
            val deltaX = abs(newX - lastPoint.x)
            val deltaY = abs(newY - lastPoint.y)
            if (deltaX >= TOUCH_TOLERANCE ||
                deltaY >= TOUCH_TOLERANCE
            ) {
                path.quadTo(lastPoint.x.toFloat(), lastPoint.y.toFloat(), (newX + lastPoint.x).toFloat() / 2, (newY + lastPoint.y).toFloat() / 2)
                val strokePoint = Point(newX, this.height - newY)
                strokePoints.add(strokePoint)
                lastPoint = point
            }
        }
        previousPoint = lastPoint
    }

    open fun touchEnded() {
        strokes.add(Stroke(strokePoints))
        strokePoints.clear()
        waitForSpace()
    }

    open fun touchStarted(x: Float, y: Float) {
        cancelSpace()
        currentPath = (currentPath ?: Path()).also { it.moveTo(x, y) }
        previousPoint = Point(x, y)
        val strokePoint = Point(x, this.height - y)
        strokePoints.add(strokePoint)
    }

    /**
     * use timer to separate words
     * too sensitive, consider using other techniques, i.e. recognizing multiple words and splitting them later
     * */
    private fun waitForSpace() {
        cTimer = object : CountDownTimer(500, 500) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                makeInk()
            }
        }
        (cTimer as CountDownTimer).start()
    }

    open fun makeInk():Ink {
        val ink = Ink(strokes)
        strokes.clear() // reset strokes
        inkMap[ink.id] = ink
        currentPath?.let {
            inkIdToPath[ink.id] = it
        }
        currentPath = null // reset path
        return ink
    }

    private fun cancelSpace() {
        cTimer?.cancel()
    }
}
