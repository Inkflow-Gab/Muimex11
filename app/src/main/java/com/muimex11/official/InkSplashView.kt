package com.muimex11.official

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * Muimex11 splash: a run of ink glides in from the right edge toward the
 * center with a comet-style fluid trail, curls downward and wraps into a
 * glowing circle (two ripple waves escape the completed ring), the brand
 * wordmark fades in underneath, then the whole thing dissolves into the app.
 *
 * Everything is drawn on canvas with a single 0..1 timeline — no asset
 * files, no extra libraries, interruptible by a tap.
 */
internal class InkSplashView(context: Context) : View(context) {

    companion object {
        private const val DUR_LINE = 520f        // ink glide in
        private const val DUR_CURL = 640f        // wraps into a circle
        private const val DUR_LOGO = 900f        // brand reveal
        private const val DUR_FADE = 420f        // dissolve out
        private const val TOTAL = DUR_LINE + DUR_CURL + DUR_LOGO + DUR_FADE

        private val VIOLET = Color.parseColor("#7C4DFF")
        private val BLUE = Color.parseColor("#2563EB")
        private val CYAN = Color.parseColor("#22D3EE")
    }

    var onFinished: (() -> Unit)? = null

    private var startedAt = 0L
    private var finished = false
    private var skipping = false

    private var w = 0f
    private var h = 0f
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private val inkPath = Path()
    private val pathMeasure = PathMeasure()
    private val segPath = Path()
    private val arcRect = RectF()
    private val measurePos = FloatArray(2)
    private val measureTan = FloatArray(2)

    private val gradientInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD))
        color = Color.WHITE
    }
    private val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        color = Color.parseColor("#8B93A2")
    }

    private val curlInterpolator = DecelerateInterpolator(2f)
    private val timeline = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = TOTAL.toLong()
        interpolator = null
        addUpdateListener {
            postInvalidateOnAnimation()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // natural completion reveals; a tap-skip hands control to fadeOut()
                if (!skipping) revealApp()
            }
        })
    }

    init {
        setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                skipToFade()
                true
            } else false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startedAt = SystemClock.uptimeMillis()
        timeline.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        timeline.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.w = w.toFloat()
        this.h = h.toFloat()
        val short = min(w, h).toFloat()

        // Dead-center composition: ring + wordmark + tagline treated as one block.
        radius = short * 0.26f
        cx = w / 2f
        val ts = short * 0.075f
        val ts2 = short * 0.030f
        val bottomPad = ts * 1.1f + ts2 * 2.75f   // wordmark + tagline space below the ring
        cy = h / 2f - bottomPad / 2f

        gradientInk.shader = LinearGradient(
            0f, 0f, this.w, this.h,
            intArrayOf(VIOLET, BLUE, CYAN), null, Shader.TileMode.CLAMP
        )
        textPaint.textSize = ts
        textPaint.setShadowLayer(ts2 * 0.9f, 0f, 0f, Color.parseColor("#557C4DFF"))
        tagPaint.textSize = ts2
        tagPaint.color = Color.parseColor("#93A2BC")
    }

    /** View alpha handled globally at the end via setFade(). */
    @SuppressLint("CanvasSize")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = if (skipping) TOTAL else min(TOTAL, (SystemClock.uptimeMillis() - startedAt).toFloat())

        if (t <= DUR_LINE) {
            drawLine(canvas, t)
        } else {
            drawLine(canvas, DUR_LINE)
        }
        if (t > DUR_LINE) drawCircle(canvas, min(DUR_CURL, t - DUR_LINE))
        if (t > DUR_LINE + DUR_CURL) drawLogoReveal(canvas, min(DUR_LOGO, t - DUR_LINE - DUR_CURL))
        if (!skipping) {
            // gentle global fade at the very end (tap-skip fades via animate() instead)
            if (t > DUR_LINE + DUR_CURL + DUR_LOGO - 160f) {
                val fadeSpan = 160f + DUR_FADE
                val over = t - (DUR_LINE + DUR_CURL + DUR_LOGO - 160f)
                if (over > 0 && fadeSpan > 0) alpha = (1f - (over / fadeSpan)).coerceIn(0f, 1f)
            } else alpha = 1f
        }
        if (t >= TOTAL) revealApp()
    }

    // ------------------------------------------------------------ phases

    private fun lineProgress(t: Float): Float {
        val p = (t / DUR_LINE).coerceIn(0f, 1f)
        return easeInOutCubic(p)
    }

    /** Ink head position: glides in from the right edge, lands on the ring's top point. */
    private fun lineHead(t: Float): FloatArray {
        val p = lineProgress(t)
        val startX = w + dp(36)
        val x = startX + (cx - startX) * p
        // rides slightly above mid-screen, rising to the ring's top point;
        // the wave decays to zero so contact is seamless
        val y = cy - radius * p + sin(x * 0.010f) * dpF(7f) * (1f - p)
        return floatArrayOf(x, y)
    }

    private fun drawLine(canvas: Canvas, t: Float) {
        // trail: sample recent path points, decaying size/alpha (fluid ink)
        var i = 1
        while (i <= 14) {
            val bt = t - i * 26f
            if (bt <= 0f) break
            val p = lineHead(bt)
            val k = 1f - i / 15f
            trailBlob(canvas, p[0], p[1], dpF(2.2f + 3.6f * k), k * 0.5f)
            i++
        }
        val head = lineHead(t)
        val pulse = 1f + 0.22f * sin(t * 0.045f)
        trailBlob(canvas, head[0], head[1], dpF(6.5f) * pulse, 0.95f)
        trailBlob(canvas, head[0], head[1], dpF(2.6f), 1f)
    }

    private fun revealProgress(t: Float): Float {
        val p = (t / DUR_CURL).coerceIn(0f, 1f)
        return curlInterpolator.getInterpolation(p)
    }

    private fun drawCircle(canvas: Canvas, t: Float) {
        inkPath.reset()
        inkPath.rewind()
        // 270° (clockwise system) = the ring's top point; a full +360 sweep wraps
        // from right side down around and back to the top — same point the ink lands on
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        inkPath.addArc(arcRect, 270f, 360f)
        pathMeasure.setPath(inkPath, false)
        val len = pathMeasure.length
        val shown = len * revealProgress(t)
        if (shown <= 0f) return
        segPath.reset()
        segPath.rewind()
        pathMeasure.getSegment(0f, shown, segPath, true)

        // soft outer glow pass
        gradientInk.strokeWidth = dp(11)
        gradientInk.alpha = 34
        canvas.drawPath(segPath, gradientInk)
        // main ink stroke
        gradientInk.strokeWidth = dpF(5.5f)
        gradientInk.alpha = 255
        canvas.drawPath(segPath, gradientInk)

        // leading blob rides the arc tail
        if (shown < len - 1f || t < DUR_CURL * 0.92f) {
            pathMeasure.getPosTan(shown.coerceAtMost(len), measurePos, measureTan)
            val grow = (1f + 0.5f * sin(t * 0.05f))
            trailBlob(canvas, measurePos[0], measurePos[1], dpF(3.2f) * grow, 1f)
            trailBlob(canvas, measurePos[0], measurePos[1], dpF(8f) * grow, 0.35f)
        }
    }

    private fun drawLogoReveal(canvas: Canvas, t: Float) {
        // fluid fill creeping from the middle of the ring
        val fillP = ((t - 140f) / DUR_LOGO).coerceIn(0f, 1f)
        if (fillP > 0f) {
            fillInk.shader = RadialGradient(
                cx, cy, radius * fillP,
                intArrayOf(Color.parseColor("#4A7C4DFF"), Color.parseColor("#00000000")),
                null, Shader.TileMode.CLAMP
            )
            fillInk.alpha = (110 * Math.min(1.0, fillP * 1.2)).toInt()
            canvas.drawCircle(cx, cy, radius * fillP, fillInk)
        }

        // two soft ripple waves escaping the completed ring
        for (del in floatArrayOf(0.55f, 0.95f)) {
            val rp = ((t / DUR_LOGO - del) / 0.45f).coerceIn(0f, 1f)
            if (rp in 0f..1f) {
                gradientInk.strokeWidth = dpF(1.4f)
                gradientInk.alpha = ((1f - rp) * 130).toInt()
                canvas.drawCircle(cx, cy, radius * (1f + rp * 0.75f), gradientInk)
            }
        }

        // wordmark: rises while fading in, tracking settles, soft violet glow
        val start = 260f
        val tp = ((t - start) / (DUR_LOGO - start)).coerceIn(0f, 1f)
        if (tp > 0f) {
            val eased = OvershootCompat.getInterpolation(tp)
            textPaint.alpha = (255 * eased).toInt()
            textPaint.letterSpacing = -(eased * 0.055f)
            val ty = cy + radius + textPaint.textSize * 1.1f
            val rise = (1f - eased).coerceAtLeast(0f) * textPaint.textSize * 0.45f
            canvas.drawText("Muimex11", cx, ty - rise, textPaint)

            val tagA = ((tp - 0.35f) / 0.65f).coerceIn(0f, 1f)
            tagPaint.alpha = (200 * tagA).toInt()
            tagPaint.letterSpacing = 0.28f
            canvas.drawText("GAME OPTIMIZER · SHIZUKU", cx, ty + tagPaint.textSize * 2.4f - rise * 0.6f, tagPaint)
        }
    }

    private fun trailBlob(canvas: Canvas, x: Float, y: Float, r: Float, alphaK: Float) {
        if (r <= 0f || alphaK <= 0f) return
        fillInk.shader = null
        fillInk.alpha = (255 * alphaK * 0.9f).toInt()
        fillInk.color = Color.parseColor("#EA7C4DFF")
        canvas.drawCircle(x, y, min(r, radius * 0.9f), fillInk)
    }

    // ------------------------------------------------------------ control

    private fun skipToFade() {
        skipping = true
        timeline.cancel()
        fadeOut()
    }

    private fun fadeOut() {
        animate().alpha(0f).setDuration(240).withEndAction { revealApp() }.start()
    }

    private fun revealApp() {
        if (finished) return
        finished = true
        onFinished?.invoke()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    private object OvershootCompat {
        private val impl = android.view.animation.OvershootInterpolator(1.2f)
        fun getInterpolation(t: Float): Float = impl.getInterpolation(t)
    }
}

private fun easeInOutCubic(t: Float): Float {
    // 3t^3-2... classic smoothstep-style cubic ease: 4t^3 in first half,
    // mirrored rise fall on the second — no stdlib pow needed.
    if (t < 0.5f) return 4f * t * t * t
    val q = -2f * t + 2f
    return 1f - (q * q * q / 2f)
}
