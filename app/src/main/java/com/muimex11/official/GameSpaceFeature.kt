package com.muimex11.official

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import es.dmoral.toasty.Toasty

/**
 * MainActivity side of the Game Space floating dock: overlay-permission gate,
 * start/stop of [GameSpaceService] and status text.
 */
internal fun MainActivity.toggleGameSpace() {
    if (!Settings.canDrawOverlays(this)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
        return
    }

    val prefs = getSharedPreferences(Prefs.MAIN, MODE_PRIVATE)
    val intent = Intent(this, GameSpaceService::class.java)

    if (isGameSpaceEnabled) {
        stopService(intent)
        isGameSpaceEnabled = false
        prefs.edit().putBoolean(Prefs.KEY_GAMESPACE_ENABLED, false).apply()
        findViewById<TextView>(R.id.tvStatusGameSpace).text = getString(R.string.status_disabled)
        Toasty.success(this, getString(R.string.toast_gamespace_off), Toast.LENGTH_SHORT, true).show()
    } else {
        startService(intent)
        isGameSpaceEnabled = true
        prefs.edit().putBoolean(Prefs.KEY_GAMESPACE_ENABLED, true).apply()
        findViewById<TextView>(R.id.tvStatusGameSpace).text = getString(R.string.status_enabled)
        Toasty.success(this, getString(R.string.toast_gamespace_on), Toast.LENGTH_SHORT, true).show()
    }
}
