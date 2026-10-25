package com.muimex11.official

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Cold-start splash: renders [InkSplashView] full-bleed — ink sweeps in
 * from the side, curls into a fluid glowing circle, the brand reveals —
 * then hands off to [MainActivity] with a gentle cross-fade.
 * A tap anywhere skips straight to the fade-out.
 */
class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ink = InkSplashView(this)
        setContentView(ink)
        ink.onFinished = {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // leave without bouncing through MainActivity
        finish()
    }
}
