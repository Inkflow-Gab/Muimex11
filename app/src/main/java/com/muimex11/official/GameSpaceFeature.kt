package com.muimex11.official

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import es.dmoral.toasty.Toasty

/**
 * MainActivity side of the Game Space floating dock: overlay-permission gate,
 * start/stop of [GameSpaceService] and status text.
 *
 * The live [GameSpaceService.isRunning] flag is the source of truth, so the
 * dock can always be re-opened — even after it was closed from its own
 * floating panel (X button), which used to leave a stale "enabled" state and
 * made the first tap look dead.
 */
internal fun MainActivity.toggleGameSpace() {
    if (!Settings.canDrawOverlays(this)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
        return
    }

    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    val intent = Intent(this, GameSpaceService::class.java)
    val enable = !GameSpaceService.isRunning

    if (enable) {
        try {
            startService(intent)
        } catch (e: Exception) {
            Toasty.error(this, getString(R.string.svc_failed, e.message ?: ""), Toast.LENGTH_LONG, true).show()
            return
        }
    } else {
        stopService(intent)
    }

    isGameSpaceEnabled = enable
    prefs.edit().putBoolean(Prefs.KEY_GAMESPACE_ENABLED, enable).apply()
    findViewById<TextView>(R.id.tvStatusGameSpace).text =
        getString(if (enable) R.string.status_enabled else R.string.status_disabled)
    Toasty.success(
        this,
        getString(if (enable) R.string.toast_gamespace_on else R.string.toast_gamespace_off),
        Toast.LENGTH_SHORT,
        true
    ).show()
}
