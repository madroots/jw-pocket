package app.jwpocket.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * Timeline (ms). The camera zooms in on the icon where the system drew it,
 * and the jacket fades in around the pocket; the phone peeks out, wiggles,
 * leaps out with a spin, then flies at the viewer until its screen is the
 * whole window and turns into a hole onto the app underneath.
 */
private const val TOTAL = 1400f
private const val ZOOM_END = 400f
private const val PEEK_START = 420f
private const val PEEK_END = 670f
private const val DIP_START = 670f
private const val DIP_END = 730f
private const val LEAP_START = 720f
private const val LEAP_END = 960f
private const val SPIN_START = 770f
private const val SPIN_END = 980f
private const val FLY_START = 960f
private const val FADE_START = 1080f
private const val FADE_END = 1230f
private const val HOLE_START = 1160f
private const val HOLE_END = 1380f

/*
 * Scene geometry in icon units (the 108-unit viewport of
 * res/drawable/ic_launcher_foreground.xml); keep the two in step, because the
 * first frame has to match the system splash exactly.
 */
private const val PHONE_X = 54f
private const val PHONE_Y = 49.5f
private const val SCREEN_W = 24f
private const val SCREEN_H = 42f
private const val SCREEN_R = 3.4f
private const val TILT = -8f
private val WELT = floatArrayOf(28f, 65.5f, 80f, 60.5f, 80.8f, 68.7f, 28.8f, 73.7f)
private val WELT_LIGHT = floatArrayOf(28f, 65.5f, 80f, 60.5f, 80.15f, 62.1f, 28.15f, 67.1f)
private val MOUTH = floatArrayOf(28f, 65.1f, 80f, 60.1f, 80f, 61.5f, 28f, 66.5f)

/*
 * The suit around the pocket, in the same units: the pocket sits on the left
 * chest. Right-hand shapes are mirrored for the other side.
 */
private val JACKET = floatArrayOf(
    -24f, -34f, -64f, -36f, -100f, -28f, -116f, -8f, -122f, 60f, -126f, 600f,
    126f, 600f, 122f, 60f, 116f, -8f, 100f, -28f, 64f, -36f, 24f, -34f,
)
private val SHIRT = floatArrayOf(-24f, -34f, 24f, -34f, 0f, 150f)
private val COLLAR = floatArrayOf(22f, -37f, 3f, -9f, 15f, 4f)
private val LAPEL = floatArrayOf(22f, -37f, 38f, -31f, 41f, -12f, 35f, -9f, 49f, -4f, 1f, 145f)
private val BUTTONS = floatArrayOf(160f, 216f)

/*
 * The close-up: the camera zooms in on the icon's centre, so the pocket stays
 * where the system drew the icon. This much of the jacket shows around it on
 * a phone (the lapel on the left), and this much height leaves room above for
 * the leap on a wide screen.
 */
private const val ICON_CENTER = 54f
private const val CLOSE_WIDTH = 74f
private const val CLOSE_HEIGHT = 150f
private const val STITCHES = 14

/** Pocket mouth line: the phone is hidden below it while it's in the pocket. */
private fun mouthY(x: Float) = 65.5f - (x - 28f) * 5f / 52f

private val SplashBackground = Color(0xFF1D2234) // @color/brand_splash_background
private val Backdrop = Color(0xFF10131C)
private val SuitLight = Color(0xFF283048)
private val SuitDark = Color(0xFF121521)
private val LapelColor = Color(0xFF313A5A)
private val Shirt = Color(0xFFF3EEE6)
private val WeltColor = Color(0xFF2F3852)
private val Bezel = Color(0xFF0B0C10)
private val Coral = Color(0xFFFF3D5A)

@Composable
internal fun PopOutSplash(icon: Rect?, onFirstFrame: () -> Unit, onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val painter = remember { PopOutPainter() }
    LaunchedEffect(Unit) {
        // By the second frame callback the first overlay frame is on screen.
        withFrameNanos { }
        withFrameNanos { }
        onFirstFrame()
    }
    LaunchedEffect(Unit) {
        clock.animateTo(TOTAL, tween(TOTAL.toInt(), easing = LinearEasing))
        onFinished()
    }
    Canvas(
        Modifier
            .fillMaxSize()
            // Offscreen so the phone screen can punch a hole through to the app.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            // Swallows touches meant for the app underneath; a tap skips the rest.
            .pointerInput(Unit) { detectTapGestures { onFinished() } },
    ) {
        with(painter) { draw(clock.value, icon) }
    }
}

private class PopOutPainter {
    private val path = Path()
    private val clip = Path()

    // Camera: icon units -> pixels, recomputed every frame.
    private var scale = 1f
    private var origin = Offset.Zero
    private fun map(x: Float, y: Float) = Offset(origin.x + x * scale, origin.y + y * scale)

    fun DrawScope.draw(t: Float, icon: Rect?) {
        val w = size.width
        val h = size.height

        // Zoom in on the icon where the system drew it, the suit fading in around it.
        val focus = icon?.center ?: Offset(w / 2f, h / 2f)
        val startScale = (icon?.width ?: 288.dp.toPx()) / 108f
        val closeScale = min(w / CLOSE_WIDTH, h / CLOSE_HEIGHT)
        scale = lerp(startScale, closeScale, ease(phase(t, 0f, ZOOM_END)))
        origin = focus - Offset(ICON_CENTER * scale, ICON_CENTER * scale)

        drawSuit(phase(t, 0f, ZOOM_END - 100f))

        // The phone in the scene: peek and wiggle, a little dip, then the leap and a spin.
        val peek = phase(t, PEEK_START, PEEK_END)
        val leap = phase(t, LEAP_START, LEAP_END)
        val dx = -6f * leap
        val dy = -5f * ease(phase(t, PEEK_START, PEEK_START + 130f)) +
            2.5f * bell(phase(t, DIP_START, DIP_END)) -
            30f * easeOutCubic(leap)
        val rotation = TILT + 7f * sin(peek * 3f * PI.toFloat()) * (1f - peek) +
            360f * ease(phase(t, SPIN_START, SPIN_END))

        drawWhoosh(dx, bell(phase(t, LEAP_START + 50f, LEAP_END)))

        // Flight: from the scene to the screen filling the window (turned sideways on a landscape screen).
        val fly = ease(phase(t, FLY_START, TOTAL))
        val landscape = w > h
        val targetW = (if (landscape) h else w) * 1.02f
        val targetH = (if (landscape) w else h) * 1.02f
        val center = lerp(map(PHONE_X + dx, PHONE_Y + dy), Offset(w / 2f, h / 2f), fly)
        val screenW = lerp(SCREEN_W * scale, targetW, fly)
        val screenH = lerp(SCREEN_H * scale, targetH, fly)
        val turn = lerp(rotation, if (landscape) 450f else 360f, fly)
        val corner = lerp(SCREEN_R * scale, 0f, fly)
        val content = 1f - phase(t, FADE_START, FADE_END)
        val hole = phase(t, HOLE_START, HOLE_END)

        if (fly > 0f) {
            // Out of the pocket and in front of everything; the suit dims behind it.
            drawWelt()
            drawRect(Color.Black, alpha = 0.35f * fly)
            drawPhone(center, screenW, screenH, turn, corner, content, hole)
        } else {
            // Still in or just out of the pocket: hide whatever is below the mouth.
            clip.polygon(-400f, mouthY(-400f), 500f, mouthY(500f), 500f, -400f, -400f, -400f)
            clipPath(clip) { drawPhone(center, screenW, screenH, turn, corner, content, hole) }
            drawWelt()
        }
    }

    /** The suit around the pocket: fades in over the plain splash color while the camera moves in. */
    private fun DrawScope.drawSuit(alpha: Float) {
        drawRect(SplashBackground)
        if (alpha <= 0f) return
        drawRect(Backdrop, alpha = alpha)
        path.polygon(*JACKET)
        drawPath(path, Brush.linearGradient(listOf(SuitLight, SuitDark), Offset.Zero, Offset(size.width, size.height)), alpha = alpha)
        clipPath(path) {
            val step = 7f * scale
            var x = origin.x + 3f * scale
            x -= floor(x / step) * step
            val stripe = (0.6f * scale).coerceIn(1f, 1.5.dp.toPx())
            while (x < size.width) {
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), stripe, alpha = 0.07f * alpha)
                x += step
            }
        }
        path.polygon(*SHIRT)
        drawPath(path, Shirt, alpha = alpha)
        for (flip in booleanArrayOf(false, true)) {
            path.polygon(*COLLAR, flip = flip)
            drawPath(path, Color.White, alpha = alpha)
        }
        val edge = (1.2f * scale).coerceAtMost(3.dp.toPx())
        for (flip in booleanArrayOf(false, true)) {
            path.polygon(*LAPEL, flip = flip)
            drawPath(path, LapelColor, alpha = alpha)
            val sign = if (flip) -1f else 1f
            // Lapel edge down to the button, and the notch.
            drawLine(Color.Black, map(sign * LAPEL[8], LAPEL[9]), map(sign * LAPEL[10], LAPEL[11]), edge, alpha = 0.35f * alpha)
            drawLine(Color.Black, map(sign * LAPEL[6], LAPEL[7]), map(sign * LAPEL[8], LAPEL[9]), edge, alpha = 0.35f * alpha)
        }
        drawLine(Color.Black, map(0f, LAPEL[11]), map(0f, 600f), edge, alpha = 0.4f * alpha)
        for (y in BUTTONS) drawCircle(Bezel, 3.2f * scale, map(0f, y), alpha = alpha)
    }

    /** The breast-pocket welt: shadow, band, top highlight, mouth shadow, stitching. */
    private fun DrawScope.drawWelt() {
        path.polygon(*WELT, dx = 0.6f, dy = 2.6f)
        drawPath(path, Color.Black, alpha = 0.35f)
        path.polygon(*WELT)
        drawPath(path, WeltColor)
        path.polygon(*WELT_LIGHT)
        drawPath(path, Color.White, alpha = 0.18f)
        path.polygon(*MOUTH)
        drawPath(path, Color.Black, alpha = 0.45f)
        val a = Offset(30.5f, 67.7f)
        val b = Offset(78f, 63.1f)
        for (i in 0 until STITCHES) {
            val from = lerp(a, b, i / STITCHES.toFloat())
            val to = lerp(a, b, (i + 0.55f) / STITCHES)
            drawLine(Color.White, map(from.x, from.y), map(to.x, to.y), 0.8f * scale, StrokeCap.Round, alpha = 0.45f)
        }
    }

    /** Two quick motion streaks under the phone as it leaves the pocket. */
    private fun DrawScope.drawWhoosh(dx: Float, alpha: Float) {
        if (alpha <= 0f) return
        for (x in floatArrayOf(46f, 54f, 62f)) {
            val top = if (x == 54f) 47f else 51f
            drawLine(Color.White, map(x + dx, 58f), map(x + dx, lerp(58f, top, alpha)), 0.7f * scale, StrokeCap.Round, alpha = 0.3f * alpha)
        }
    }

    /** The phone in pixels; [content] fades the play mark, [hole] opens the screen onto the app. */
    private fun DrawScope.drawPhone(center: Offset, screenW: Float, screenH: Float, degrees: Float, corner: Float, content: Float, hole: Float) {
        val unit = screenW / SCREEN_W
        val bezel = 2f * unit
        val screen = Rect(center.x - screenW / 2f, center.y - screenH / 2f, center.x + screenW / 2f, center.y + screenH / 2f)
        rotate(degrees, center) {
            drawRoundRect(Bezel, screen.topLeft - Offset(bezel, bezel), Size(screenW + 2f * bezel, screenH + 2f * bezel), CornerRadius(corner + bezel * 0.8f))
            drawRoundRect(Coral, screen.topLeft, screen.size, CornerRadius(corner))
            if (content > 0f) {
                path.roundedPlay(center.x + 0.5f * unit, center.y - 6f * unit, 11f * unit, 1.8f * unit)
                drawPath(path, Color.White, alpha = content)
            }
            drawRoundRect(Bezel, Offset(center.x - 4f * unit, screen.top + 2f * unit), Size(8f * unit, 1.6f * unit), CornerRadius(0.8f * unit))
            if (hole > 0f) drawRoundRect(Color.Black, screen.topLeft, screen.size, CornerRadius(corner), alpha = hole, blendMode = BlendMode.DstOut)
        }
    }

    /** Polygon from icon-unit x,y pairs, mapped through the camera; [flip] mirrors it across the suit's centre line. */
    private fun Path.polygon(vararg xy: Float, dx: Float = 0f, dy: Float = 0f, flip: Boolean = false) {
        reset()
        for (i in xy.indices step 2) {
            val p = map((if (flip) -xy[i] else xy[i]) + dx, xy[i + 1] + dy)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }

    /** The launcher icon's play mark (pixels): a triangle centred on its centroid, corners rounded by [r]. */
    private fun Path.roundedPlay(cx: Float, cy: Float, h: Float, r: Float) {
        val w = h * 0.88f
        val xs = floatArrayOf(cx - w / 3f, cx + 2f * w / 3f, cx - w / 3f)
        val ys = floatArrayOf(cy - h / 2f, cy, cy + h / 2f)
        reset()
        for (i in 0 until 3) {
            val prev = (i + 2) % 3
            val next = (i + 1) % 3
            val inX = xs[i] + (xs[prev] - xs[i]) * r / hypot(xs[prev] - xs[i], ys[prev] - ys[i])
            val inY = ys[i] + (ys[prev] - ys[i]) * r / hypot(xs[prev] - xs[i], ys[prev] - ys[i])
            val outX = xs[i] + (xs[next] - xs[i]) * r / hypot(xs[next] - xs[i], ys[next] - ys[i])
            val outY = ys[i] + (ys[next] - ys[i]) * r / hypot(xs[next] - xs[i], ys[next] - ys[i])
            if (i == 0) moveTo(inX, inY) else lineTo(inX, inY)
            quadraticTo(xs[i], ys[i], outX, outY)
        }
        close()
    }
}

private fun phase(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)
private fun ease(x: Float) = FastOutSlowInEasing.transform(x)
private fun easeOutCubic(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun bell(x: Float) = sin(x * PI.toFloat())
private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
