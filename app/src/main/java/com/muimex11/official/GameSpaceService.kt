package com.muimex11.official

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.math.abs
import kotlin.math.max

/**
 * RedMagic / Game-Space style floating dock.
 *
 * Bubble window: draggable, snaps to the nearest edge.
 * Panel window: expands next to the bubble and shows
 *  - the detected foreground app (icon + label) with a live FPS counter
 *  - quick boost rows (also see `items`): RAM boost, Game Boost for the
 *    current app, fixed perf, thermal bypass, FPS lock, focus mode, no
 *    blurs, resolution cycle, HW monitor, stop game.
 *
 * All commands run through Shizuku shell; no root required.
 */
class GameSpaceService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleRoot: View? = null
    private var panelRoot: View? = null

    private lateinit var bubble: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var itemContainer: LinearLayout
    private lateinit var appIconView: ImageView
    private lateinit var appNameView: TextView
    private lateinit var fpsChip: TextView
    private lateinit var scroll: ScrollView

    private var thermalBypass = false
    private var fixedPerf = false
    private var fgPkg: String? = null
    private var fgLabel: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private val overlayType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    override fun onBind(intent: Intent?): IBinder? = null

    override fun getResources(): android.content.res.Resources = AppTranslator.wrap(super.getResources())

    // ---------------------------------------------------------------- setup

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            buildBubble()
        } catch (e: Exception) {
            toast(getString(R.string.svc_failed, e.message ?: ""))
            stopSelf()
        }
    }

    /** Always-dark context so the dock looks identical over bright games. */
    private fun uiContext(): Context = AppTranslator.wrap(
        android.view.ContextThemeWrapper(
            run {
                val config = android.content.res.Configuration(resources.configuration)
                config.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES or
                    (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv())
                createConfigurationContext(config)
            },
            R.style.Theme_Muimex11
        )
    )

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun buildBubble() {
        bubbleRoot = LayoutInflater.from(uiContext()).inflate(R.layout.layout_game_space, null)
        AppTranslator.translateTree(bubbleRoot!!)
        bubble = bubbleRoot!!.findViewById(R.id.gsBubble)

        val flags = overlayFlags or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType, flags, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenBounds().width() - dp(46)
            y = screenBounds().height() / 3
        }

        wm.addView(bubbleRoot, bubbleParams)

        var initX = 0; var initY = 0
        var touchX = 0f; var touchY = 0f
        var dragging = false; var downAt = 0L

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = bubbleParams.x; initY = bubbleParams.y
                    touchX = e.rawX; touchY = e.rawY
                    dragging = false
                    downAt = android.os.SystemClock.uptimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - touchX; val dy = e.rawY - touchY
                    if (!dragging && (abs(dx) > 10 || abs(dy) > 10)) dragging = true
                    if (dragging) {
                        bubbleParams.x = initX + dx.toInt()
                        bubbleParams.y = initY + dy.toInt()
                        clampBubble()
                        update(bubbleRoot, bubbleParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging && android.os.SystemClock.uptimeMillis() - downAt < 350) {
                        if (::panel.isInitialized) collapse() else expand()
                    } else if (!::panel.isInitialized) {
                        snapToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun buildPanel() {
        val ctx = uiContext()
        panelRoot = LayoutInflater.from(ctx).inflate(R.layout.layout_game_space_panel, null)
        AppTranslator.translateTree(panelRoot!!)
        panel = panelRoot!!.findViewById(R.id.gsPanel)
        appIconView = panelRoot!!.findViewById(R.id.gsAppIcon)
        appNameView = panelRoot!!.findViewById(R.id.gsAppName)
        fpsChip = panelRoot!!.findViewById(R.id.gsFpsChip)
        scroll = panelRoot!!.findViewById(R.id.gsScroll)
        itemContainer = panelRoot!!.findViewById(R.id.gsItemContainer)
        fpsChip.text = getString(R.string.gs_fps_measuring)

        items.forEach { itemContainer.addView(buildRow(it)) }

        panelRoot!!.findViewById<ImageView>(R.id.gsBtnMinimize).setOnClickListener { collapse() }
        panelRoot!!.findViewById<ImageView>(R.id.gsBtnKill).setOnClickListener {
            toast(getString(R.string.toast_gamespace_off))
            stopSelf()
        }

        // Tap outside the panel collapses it.
        panel.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) {
                collapse()
                true
            } else false
        }

        val flags = overlayFlags or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType, flags, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    // -------------------------------------------------------------- expand

    private fun isExpanded() = panelRoot != null && panelRoot!!.parent != null

    private fun expand() {
        if (!::panel.isInitialized) buildPanel()
        if (isExpanded()) return

        refreshForegroundApp()
        refreshStates()
        try {
            wm.addView(panelRoot, panelParams)
            panel.alpha = 0f
            panelRoot!!.post {
                placePanel()
                panel.animate().alpha(1f).setDuration(130).start()
                capScrollHeight()
            }
            startFpsPolling()
        } catch (e: Exception) {
            toast(getString(R.string.svc_failed, e.message ?: ""))
        }
    }

    private fun collapse() {
        stopFpsPolling()
        try {
            if (isExpanded()) wm.removeView(panelRoot)
        } catch (_: Exception) {}
    }

    private fun placePanel() {
        val scr = screenBounds()
        val left = bubbleParams.x < scr.width() / 2
        panelParams.x = if (left)
            max(0, bubbleParams.x + bubble.width + dp(6))
        else
            max(0, bubbleParams.x - panel.width - dp(6))
        val top = bubbleParams.y + bubble.height / 2 - panel.height / 2
        panelParams.y = top.coerceIn(dp(8), max(dp(8), scr.height() - panel.height - dp(10)))
        update(panelRoot, panelParams)
    }

    private fun capScrollHeight() {
        val scr = screenBounds()
        itemContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val cap = (scr.height() * 0.42f).toInt()
        if (itemContainer.measuredHeight > cap && scroll.layoutParams.height != cap) {
            scroll.layoutParams = scroll.layoutParams.apply { height = cap }
        }
        update(panelRoot, panelParams)
    }

    private fun clampBubble() {
        val s = screenBounds()
        bubbleParams.x = bubbleParams.x.coerceIn(0, max(0, s.width() - bubble.width))
        bubbleParams.y = bubbleParams.y.coerceIn(0, max(0, s.height() - bubble.height))
    }

    private fun snapToEdge() {
        val s = screenBounds()
        if (bubble.width == 0) return
        val target = if (bubbleParams.x + bubble.width / 2 < s.width() / 2) 0 else s.width() - bubble.width
        ValueAnimator.ofInt(bubbleParams.x, target).apply {
            duration = 220
            addUpdateListener { a ->
                bubbleParams.x = a.animatedValue as Int
                update(bubbleRoot, bubbleParams)
            }
            start()
        }
    }

    // --------------------------------------------------------------- items

    private data class ItemSpec(
        val iconRes: Int,
        val titleRes: Int,
        val kind: Kind,
        val onCmd: String? = null,
        val offCmd: String? = null,
        val stateCmd: String? = null,
        val stateEquals: String? = null,
        val successToastRes: Int? = null
    )

    private enum class Kind { ONE_SHOT, TOGGLE_STATE, TOGGLE_SESSION, FG_GAME_MODE, MONITOR, RES_CYCLE, STOP_GAME }

    private val items = listOf(
        ItemSpec(
            R.drawable.ic_boost,
            R.string.gs_boost,
            Kind.ONE_SHOT,
            onCmd = "cmd activity kill-all && pm trim-caches 256G",
            successToastRes = R.string.gs_boost
        ),
        ItemSpec(
            R.drawable.ic_gamepad,
            R.string.gs_gamemode,
            Kind.FG_GAME_MODE,
            onCmd = "cmd game mode performance",
            offCmd = "cmd game mode standard",
            stateEquals = ""
        ),
        ItemSpec(
            R.drawable.ic_tune,
            R.string.gs_perf,
            Kind.TOGGLE_SESSION,
            onCmd = "cmd power set-fixed-performance-mode-enabled true",
            offCmd = "cmd power set-fixed-performance-mode-enabled false"
        ),
        ItemSpec(
            R.drawable.ic_fire,
            R.string.gs_thermal,
            Kind.TOGGLE_SESSION,
            onCmd = "cmd thermalservice override-status 0",
            offCmd = "cmd thermalservice reset"
        ),
        ItemSpec(
            R.drawable.ic_framerate,
            R.string.gs_fps,
            Kind.TOGGLE_STATE,
            onCmd = "settings put system peak_refresh_rate 120 && settings put system min_refresh_rate 120",
            offCmd = "settings delete system peak_refresh_rate && settings delete system min_refresh_rate",
            stateCmd = "settings get system peak_refresh_rate",
            stateEquals = "120"
        ),
        ItemSpec(
            R.drawable.ic_bell_off,
            R.string.gs_focus,
            Kind.TOGGLE_STATE,
            onCmd = "settings put global heads_up_notifications_enabled 0",
            offCmd = "settings put global heads_up_notifications_enabled 1",
            stateCmd = "settings get global heads_up_notifications_enabled",
            stateEquals = "0"
        ),
        ItemSpec(
            R.drawable.ic_blur_off,
            R.string.gs_blurs,
            Kind.TOGGLE_STATE,
            onCmd = "settings put global disable_window_blurs 1",
            offCmd = "settings put global disable_window_blurs 0",
            stateCmd = "settings get global disable_window_blurs",
            stateEquals = "1"
        ),
        ItemSpec(
            R.drawable.ic_resolution,
            R.string.gs_res,
            Kind.RES_CYCLE
        ),
        ItemSpec(
            R.drawable.ic_monitor,
            R.string.gs_monitor,
            Kind.MONITOR
        ),
        ItemSpec(
            R.drawable.ic_reset,
            R.string.gs_stopgame,
            Kind.STOP_GAME,
            successToastRes = R.string.gs_stop_ok
        )
    )

    // When we force Performance mode onto the current app we remember it here.
    private var gameBoostPkg: String? = null

    // ---------------------------------------------------------------- row UI

    private val STATUS_ON = Color.parseColor("#7DE28E")
    private val STATUS_OFF = Color.parseColor("#9AA3B2")
    private val STATUS_TAG = "gs_status_tag"

    private fun buildRow(spec: ItemSpec): View {
        val ctx = uiContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(13).toFloat()
                setColor(0x2EFFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(7) }
        }

        val iconBox = FrameLayout(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x2BFFFFFF)
            }
        }
        iconBox.addView(ImageView(ctx).apply {
            setImageResource(spec.iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#BD9BFB"))
            layoutParams = FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER)
        })
        row.addView(iconBox, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(11) })

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = getString(spec.titleRes)
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 1
        })
        val status = TextView(ctx).apply {
            text = getString(R.string.gs_off)
            textSize = 10.5f
            setTextColor(STATUS_OFF)
            setPadding(0, dp(1), 0, 0)
            tag = STATUS_TAG
        }
        col.addView(status)
        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        row.setOnClickListener { applyItem(spec, row) }
        row.tag = spec
        return row
    }

    private fun statusViewOf(row: View): TextView? {
        val col = (row as? android.view.ViewGroup)?.getChildAt(1) as? android.view.ViewGroup ?: return null
        for (i in 0 until col.childCount) {
            val v = col.getChildAt(i)
            if (v is TextView && v.tag == STATUS_TAG) return v
        }
        return null
    }

    private fun setRow(row: View, active: Boolean) {
        val tv = statusViewOf(row) ?: return
        tv.text = getString(if (active) R.string.gs_on else R.string.gs_off)
        tv.setTextColor(if (active) STATUS_ON else STATUS_OFF)
    }

    private fun setApplying(row: View) {
        val tv = statusViewOf(row) ?: return
        tv.text = getString(R.string.gs_applying)
        tv.setTextColor(STATUS_OFF)
    }

    // ------------------------------------------------------------ item logic

    private fun rowState(spec: ItemSpec): Boolean = when (spec.kind) {
        Kind.TOGGLE_SESSION -> when (spec.titleRes) {
            R.string.gs_thermal -> thermalBypass
            R.string.gs_perf -> fixedPerf
            else -> false
        }
        Kind.TOGGLE_STATE -> false // async; updated by refreshRowAsync
        Kind.FG_GAME_MODE -> gameBoostPkg != null && gameBoostPkg == fgPkg
        Kind.MONITOR -> RealtimeMonitorService.isRunning
        else -> false
    }

    private fun applyItem(spec: ItemSpec, row: View) {
        setApplying(row)
        scope.launch {
            var ok = false
            try {
                when (spec.kind) {
                    Kind.ONE_SHOT -> ok = exec(spec.onCmd!!)
                    Kind.TOGGLE_SESSION -> {
                        val active = rowState(spec)
                        ok = exec(if (active) spec.offCmd!! else spec.onCmd!!)
                        if (ok) when (spec.titleRes) {
                            R.string.gs_thermal -> thermalBypass = !active
                            R.string.gs_perf -> fixedPerf = !active
                            else -> {}
                        }
                    }
                    Kind.TOGGLE_STATE -> {
                        val active = queryState(spec)
                        ok = exec(if (active) spec.offCmd!! else spec.onCmd!!)
                    }
                    Kind.FG_GAME_MODE -> {
                        val pkg = fgPkg
                        if (pkg == null) {
                            toast(getString(R.string.gs_no_app))
                        } else {
                            val active = gameBoostPkg != null && gameBoostPkg == pkg
                            if (active) {
                                ok = exec("${spec.offCmd!!} $pkg")
                                if (ok) gameBoostPkg = null
                            } else {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                    toast(getString(R.string.toast_needs_a12))
                                } else {
                                    ok = exec("${spec.onCmd!!} $pkg")
                                    if (ok) gameBoostPkg = pkg
                                }
                            }
                        }
                    }
                    Kind.MONITOR -> {
                        try {
                            if (RealtimeMonitorService.isRunning) {
                                stopService(Intent(this@GameSpaceService, RealtimeMonitorService::class.java))
                            } else {
                                startService(Intent(this@GameSpaceService, RealtimeMonitorService::class.java))
                            }
                            ok = true
                        } catch (e: Exception) {
                            ok = false
                        }
                    }
                    Kind.RES_CYCLE -> ok = cycleResolution()
                    Kind.STOP_GAME -> {
                        ok = stopForegroundApp()
                        if (ok) collapse()
                    }
                }
            } catch (e: Exception) {
                ok = false
                toast(getString(R.string.svc_shizuku_missing))
            }

            withContext(Dispatchers.Main) {
                when (spec.kind) {
                    Kind.ONE_SHOT, Kind.RES_CYCLE, Kind.STOP_GAME, Kind.FG_GAME_MODE -> {
                        toast(getString(spec.successToastRes ?: R.string.gs_boost))
                        setRow(row, rowState(spec))
                    }
                    else -> {
                        if (!ok) toast(getString(R.string.gs_failed, "exit code"))
                        setRow(row, rowState(spec))
                    }
                }
                if (spec.kind == Kind.TOGGLE_STATE || spec.kind == Kind.MONITOR) {
                    refreshRowAsync(spec, row)
                }
            }
        }
    }

    private fun refreshRowAsync(spec: ItemSpec, row: View) {
        ioScope.launch {
            val state = try {
                when (spec.kind) {
                    Kind.TOGGLE_STATE -> queryState(spec)
                    Kind.MONITOR -> RealtimeMonitorService.isRunning
                    else -> rowState(spec)
                }
            } catch (_: Exception) { false }
            withContext(Dispatchers.Main) { setRow(row, state) }
        }
    }

    private fun refreshStates() {
        for (i in 0 until itemContainer.childCount) {
            val row = itemContainer.getChildAt(i)
            val spec = row.tag as? ItemSpec ?: continue
            when (spec.kind) {
                Kind.TOGGLE_SESSION, Kind.FG_GAME_MODE -> setRow(row, rowState(spec))
                Kind.TOGGLE_STATE, Kind.MONITOR -> refreshRowAsync(spec, row)
                else -> {}
            }
        }
    }

    private fun queryState(spec: ItemSpec): Boolean =
        spec.stateCmd != null && query(spec.stateCmd).trim() == spec.stateEquals

    // ------------------------------------------------- foreground app + FPS

    private val fgRegex = Regex("(?:topResumedActivity|ResumedActivity|mFocusedApp)[^\\n]*u0\\s+(\\w[\\w.]*)/")

    private fun detectForegroundPkg(): String? {
        val dump = query("dumpsys activity activities") + "\n" + query("dumpsys window windows")
        val candidates = fgRegex.findAll(dump).mapNotNull { it.groupValues.getOrNull(1) }
            .filter {
                it != packageName &&
                    it != "com.android.systemui" &&
                    it != "com.android.launcher3" &&
                    !it.contains("launcher", ignoreCase = true) &&
                    !it.endsWith(".home") &&
                    it != "com.google.android.googlequicksearchbox"
            }
            .distinct()
        return candidates.firstOrNull()
    }

    private fun refreshForegroundApp() {
        ioScope.launch {
            val pkg = try { detectForegroundPkg() } catch (_: Exception) { null }
            var label = ""
            var icon: android.graphics.drawable.Drawable? = null
            if (pkg != null) {
                try {
                    val info = packageManager.getApplicationInfo(pkg, 0)
                    label = packageManager.getApplicationLabel(info).toString()
                    icon = packageManager.getApplicationIcon(pkg)
                } catch (e: Exception) {
                    label = pkg.substringAfterLast('.')
                }
            }
            withContext(Dispatchers.Main) {
                fgPkg = pkg
                fgLabel = label
                if (pkg != null) {
                    appNameView.text = label
                    appIconView.setImageDrawable(icon)
                } else {
                    appNameView.text = getString(R.string.gs_menu_title)
                    appIconView.setImageResource(R.drawable.ic_gamespace)
                    appIconView.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#BD9BFB"))
                }
                // If Game Boost was left on for an app that changed, reset it.
                gameBoostPkg?.takeIf { it != pkg && it.isNotEmpty() }?.let { old ->
                    ioScope.launch {
                        try { exec("cmd game mode standard $old") } catch (_: Exception) {}
                    }
                    gameBoostPkg = null
                }
            }
        }
    }

    private var fpsPolling = false

    private fun startFpsPolling() {
        stopFpsPolling()
        fpsPolling = true
        ioScope.launch {
            var lastCount = -1L
            var lastAt = 0L
            while (fpsPolling) {
                try {
                    val pkg = detectForegroundPkg()
                    if (pkg != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val out = query("dumpsys gfxinfo $pkg")
                        val total = Regex("Total frames rendered:\\s*(\\d+)").find(out)
                            ?.groupValues?.getOrNull(1)?.toLongOrNull()
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (total != null && lastCount >= 0 && now - lastAt > 400) {
                            val fps = ((total - lastCount) * 1000f) / (now - lastAt)
                            val shown = fps.toInt().coerceIn(0, 999)
                            handler.post {
                                if (fpsPolling) {
                                    fpsChip.text = "$shown FPS"
                                    fpsChip.setTextColor(
                                        when {
                                            shown >= 50 -> STATUS_ON
                                            shown >= 30 -> Color.parseColor("#E0A800")
                                            else -> Color.parseColor("#FF6B6B")
                                        }
                                    )
                                }
                            }
                        }
                        if (total != null) lastCount = total
                        lastAt = now
                    }
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    private fun stopFpsPolling() {
        fpsPolling = false
    }

    // ---------------------------------------------------------------- shell

    private fun exec(cmd: String): Boolean {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        return p.waitFor() == 0
    }

    private fun query(cmd: String): String = try {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) {
        ""
    }

    private fun cycleResolution(): Boolean {
        val real = screenBounds()
        if (real.width() == 0) return false
        val cur = resources.displayMetrics
        val ratio = cur.widthPixels.toFloat() / real.width()
        val dpi = cur.densityDpi

        return when {
            ratio > 0.9f -> {
                val w = (real.width() * 0.75f).toInt(); val h = (real.height() * 0.75f).toInt()
                exec("wm size ${w}x${h} && wm density ${(dpi * 0.75f).toInt()}")
            }
            ratio > 0.6f -> {
                val w = (real.width() * 0.5f).toInt(); val h = (real.height() * 0.5f).toInt()
                exec("wm size ${w}x${h} && wm density ${(dpi * 0.5f).toInt()}")
            }
            else -> exec("wm size reset && wm density reset")
        }
    }

    private fun stopForegroundApp(): Boolean {
        val pkg = detectForegroundPkg() ?: return false
        return exec("am force-stop $pkg")
    }

    // -------------------------------------------------------------- helpers

    private fun update(v: View?, p: WindowManager.LayoutParams) {
        try {
            if (v != null) wm.updateViewLayout(v, p)
        } catch (_: Exception) {}
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun screenBounds(): android.graphics.Rect = try {
        @Suppress("DEPRECATION")
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(m)
        android.graphics.Rect(0, 0, m.widthPixels, m.heightPixels)
    } catch (e: Exception) {
        android.graphics.Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
    }

    private fun toast(msg: String) {
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            } else {
                handler.post {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        stopFpsPolling()
        try {
            bubbleRoot?.let { wm.removeView(it) }
        } catch (_: Exception) {}
        try {
            if (::panel.isInitialized) wm.removeView(panelRoot)
        } catch (_: Exception) {}
        bubbleRoot = null
        panelRoot = null
    }
}

