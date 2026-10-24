package com.muimex11.official

import android.os.Build
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Samsung / One-UI deep tweaks panel. Every action shows its current state
 * and can be restored. All commands run through Shizuku shell on user 0.
 */
internal object SamsungPkgs {
    const val GOS = "com.samsung.android.game.gos"
    const val DEVICE_SECURITY = "com.samsung.android.sm.devicesecurity"
    val LOGGING = listOf("com.samsung.android.dqagent", "com.sec.android.diagmonagent")
    val BLOAT = listOf("com.samsung.android.app.spage", "com.sec.android.daemonapp")
}

internal data class SamsungRow(
    val view: LinearLayout,
    val subtitle: TextView
)

internal enum class SamsungAction {
    GOS, RAMPLUS, LOGGING, DEVICE_SECURITY, BLOAT;

    fun title(): Int = when (this) {
        GOS -> R.string.samsung_gos
        RAMPLUS -> R.string.samsung_ramplus
        LOGGING -> R.string.samsung_log
        DEVICE_SECURITY -> R.string.samsung_sec
        BLOAT -> R.string.samsung_bloat
    }
}

internal fun MainActivity.isSamsungDevice(): Boolean =
    Build.MANUFACTURER.contains("samsung", ignoreCase = true)

internal fun MainActivity.showSamsungBoostSheet() {
    if (!hasShizukuPermission()) return

    if (!isSamsungDevice()) {
        Sheet(this)
            .icon(R.drawable.ic_warning, 0xFFE09B00.toInt())
            .title(getString(R.string.dlg_samsung_title))
            .message(getString(R.string.samsung_not_samsung))
            .button(getString(R.string.btn_close), SheetStyle.TONAL)
            .show()
        return
    }

    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(samsungMaxBoostRow())

    val rows = LinkedHashMap<SamsungAction, SamsungRow>()
    SamsungAction.values().forEach { action ->
        val row = samsungRow(action)
        rows[action] = row
        content.addView(row.view)
    }

    val sheet = Sheet(this)
        .icon(R.drawable.ic_security, 0xFF1976D2.toInt())
        .title(getString(R.string.dlg_samsung_title))
        .message(getString(R.string.dlg_samsung_msg))
        .content(content)
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .button(getString(R.string.btn_restore_all_frozen), SheetStyle.TEXT, dismiss = false) {
            restoreAllSamsung { refreshSamsungStates(rows) }
        }
        .show()

    SamsungAction.values().forEach { action ->
        val row = rows[action] ?: return@forEach
        row.view.setOnClickListener {
            if (action == SamsungAction.GOS && !samsungActionState(action)) {
                Sheet(this)
                    .icon(R.drawable.ic_warning, 0xFFE53935.toInt())
                    .title(getString(R.string.samsung_gos_warning_title))
                    .message(getString(R.string.samsung_gos_warning_msg))
                    .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
                    .button(getString(R.string.samsung_gos_confirm_disable), SheetStyle.DANGER) {
                        toggleSamsungAction(action) { refreshSamsungStates(rows) }
                    }
                    .show()
            } else {
                toggleSamsungAction(action) { refreshSamsungStates(rows) }
            }
        }
    }
    refreshSamsungStates(rows)
}

// -------------------------------------------------------------- row build

private fun MainActivity.samsungMaxBoostRow(): MaterialCardView {
    val accent = 0xFF1976D2.toInt()
    val card = MaterialCardView(this).apply {
        radius = dp(16).toFloat()
        setCardBackgroundColor(0x261976D2)
        strokeColor = accent
        strokeWidth = dp(2)
        isClickable = true
        isFocusable = true
        elevation = 0f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56)
        ).apply { topMargin = dp(14) }
    }
    val box = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(14), 0)
    }
    box.addView(TextView(this).apply {
        text = getString(R.string.samsung_max)
        textSize = 15f
        setTextColor(accent)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    box.addView(TextView(this).apply {
        text = getString(R.string.btn_boost)
        textSize = 13f
        setTextColor(accent)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    })
    card.addView(box)
    card.setOnClickListener {
        runSamsung(
            listOf(
                "cmd thermalservice override-status 0",
                "cmd activity kill-all",
                "pm trim-caches 256G",
                "sync"
            ).joinToString(" ; ")
        ) { ok ->
            if (ok) {
                Toasty.success(this, getString(R.string.samsung_max_done), Toast.LENGTH_LONG, true).show()
            } else {
                Toasty.error(this, getString(R.string.toast_cmd_failed, 1), Toast.LENGTH_LONG, true).show()
            }
        }
    }
    return card
}

private fun MainActivity.samsungRow(action: SamsungAction): SamsungRow {
    val act = this
    val row = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = Ui.ripple(act, Ui.tonalFill(act), dp(14))
        isClickable = true
        isFocusable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }
    val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    texts.addView(TextView(this).apply {
        text = getString(action.title())
        textSize = 15f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Ui.onSurface(act))
    })
    val sub = TextView(this).apply {
        textSize = 12f
        setTextColor(Ui.onSurfaceVariant(act))
        setPadding(0, dp(2), 0, 0)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    texts.addView(sub)
    row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(TextView(this).apply {
        text = "›"
        textSize = 20f
        setTextColor(Ui.onSurfaceVariant(act))
    })
    return SamsungRow(row, sub)
}

// ------------------------------------------------------------------ state

private fun MainActivity.userId0(): Int = android.os.Process.myUid() / 100000

private fun MainActivity.gosRemoved(): Boolean =
    runShell("pm list packages --user ${userId0()} ${SamsungPkgs.GOS}").out.trim().isEmpty()

private fun MainActivity.isFrozen(pkg: String): Boolean =
    runShell("pm list packages -d --user ${userId0()} $pkg").out.trim().isNotEmpty()

private fun MainActivity.ramPlusOff(): Boolean =
    runShell("settings get global ram_expand_size_list").out.trim() == "0"

private fun MainActivity.isInstalled(pkg: String): Boolean = try {
    packageManager.getPackageInfo(pkg, 0)
    true
} catch (e: Exception) {
    false
}

/** Boosted = the tweak is currently applied. */
private fun MainActivity.samsungActionState(action: SamsungAction): Boolean = when (action) {
    SamsungAction.GOS -> gosRemoved()
    SamsungAction.RAMPLUS -> ramPlusOff()
    SamsungAction.LOGGING -> SamsungPkgs.LOGGING.any { isInstalled(it) && isFrozen(it) }
    SamsungAction.DEVICE_SECURITY -> isInstalled(SamsungPkgs.DEVICE_SECURITY) && isFrozen(SamsungPkgs.DEVICE_SECURITY)
    SamsungAction.BLOAT -> SamsungPkgs.BLOAT.any { isInstalled(it) && isFrozen(it) }
}

private fun MainActivity.refreshSamsungStates(rows: Map<SamsungAction, SamsungRow>) {
    val act = this
    lifecycleScope.launch(Dispatchers.IO) {
        val states = SamsungAction.values().associateWith { samsungActionState(it) }
        withContext(Dispatchers.Main) {
            rows.forEach { (action, row) ->
                val on = states[action] == true
                row.subtitle.text = getString(
                    R.string.samsung_state_label,
                    getString(if (on) R.string.samsung_row_on else R.string.samsung_row_off)
                )
                row.subtitle.setTextColor(if (on) 0xFF2E9E5B.toInt() else Ui.onSurfaceVariant(act))
            }
            val activeCount = states.values.count { it }
            findViewById<TextView>(R.id.tvStatusSamsungBoost).text =
                if (activeCount == 0) getString(R.string.status_default)
                else getString(R.string.samsung_status_count, activeCount)
        }
    }
}

// ---------------------------------------------------------------- actions

private fun MainActivity.runSamsung(cmd: String, onDone: (Boolean) -> Unit) {
    lifecycleScope.launch(Dispatchers.IO) {
        val r = runShell(cmd)
        withContext(Dispatchers.Main) { onDone(r.code == 0) }
    }
}

internal fun MainActivity.toggleSamsungAction(action: SamsungAction, onDone: () -> Unit) {
    val boosted = samsungActionState(action)
    when (action) {
        SamsungAction.GOS -> {
            if (boosted) {
                runSamsung("cmd package install-existing ${SamsungPkgs.GOS}") { ok ->
                    if (ok) Toasty.success(this, getString(R.string.samsung_gos_on), Toast.LENGTH_LONG, true).show()
                    onDone()
                }
            } else {
                runSamsung("pm uninstall -k --user ${userId0()} ${SamsungPkgs.GOS}") { ok ->
                    if (ok) {
                        Toasty.success(this, getString(R.string.samsung_gos_off), Toast.LENGTH_LONG, true).show()
                    } else {
                        Toasty.error(this, getString(R.string.toast_cmd_failed, 1), Toast.LENGTH_LONG, true).show()
                    }
                    onDone()
                }
            }
        }
        SamsungAction.RAMPLUS -> {
            val cmd = if (boosted) "settings delete global ram_expand_size_list"
            else "settings put global ram_expand_size_list 0"
            runSamsung(cmd) { ok ->
                if (ok) {
                    Toasty.success(
                        this,
                        getString(if (boosted) R.string.samsung_ramplus_on else R.string.samsung_ramplus_off),
                        Toast.LENGTH_LONG, true
                    ).show()
                }
                onDone()
            }
        }
        SamsungAction.LOGGING -> {
            freezeSet(SamsungPkgs.LOGGING, freeze = !boosted) { ok ->
                if (ok) Toasty.success(this, getString(if (boosted) R.string.samsung_log_on else R.string.samsung_log_off), Toast.LENGTH_SHORT, true).show()
                onDone()
            }
        }
        SamsungAction.DEVICE_SECURITY -> {
            freezeSet(listOf(SamsungPkgs.DEVICE_SECURITY), freeze = !boosted) { ok ->
                if (ok) Toasty.success(this, getString(if (boosted) R.string.samsung_sec_on else R.string.samsung_sec_off), Toast.LENGTH_SHORT, true).show()
                onDone()
            }
        }
        SamsungAction.BLOAT -> {
            freezeSet(SamsungPkgs.BLOAT, freeze = !boosted) { ok ->
                if (ok) Toasty.success(this, getString(if (boosted) R.string.samsung_bloat_on else R.string.samsung_bloat_frozen), Toast.LENGTH_SHORT, true).show()
                onDone()
            }
        }
    }
}

/** pm disable-user / enable a set of packages sequentially. */
private fun MainActivity.freezeSet(pkgs: List<String>, freeze: Boolean, done: (Boolean) -> Unit) {
    val installed = pkgs.filter { isInstalled(it) }
    if (installed.isEmpty()) {
        Toasty.info(this, getString(R.string.samsung_row_unknown), Toast.LENGTH_SHORT, true).show()
        done(false)
        return
    }
    lifecycleScope.launch(Dispatchers.IO) {
        var ok = false
        for (p in installed) {
            val cmd = if (freeze) "pm disable-user --user ${userId0()} $p" else "pm enable --user ${userId0()} $p"
            if (runShell(cmd).code == 0) ok = true
        }
        withContext(Dispatchers.Main) { done(ok) }
    }
}

/** Resets every Samsung action to default (card RESET pill). */
internal fun MainActivity.restoreAllSamsung(onDone: (() -> Unit)? = null) {
    lifecycleScope.launch(Dispatchers.IO) {
        val userId = userId0()
        val restore = buildList {
            add("cmd package install-existing ${SamsungPkgs.GOS}")
            SamsungPkgs.LOGGING.forEach { add("pm enable --user $userId $it") }
            add("pm enable --user $userId ${SamsungPkgs.DEVICE_SECURITY}")
            SamsungPkgs.BLOAT.forEach { add("pm enable --user $userId $it") }
            add("settings delete global ram_expand_size_list")
        }.joinToString(" ; ")
        runSamsung(restore) { _ ->
            lifecycleScope.launch(Dispatchers.Main) {
                Toasty.success(this@restoreAllSamsung, getString(R.string.samsung_all_restored), Toast.LENGTH_SHORT, true).show()
                onDone?.invoke()
            }
        }
    }
}
