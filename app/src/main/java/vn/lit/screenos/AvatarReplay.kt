package vn.lit.screenos

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets

/** Briefly hides Samsung's AR Emoji wallpaper so it plays its next animation. */
object AvatarReplay {
    const val PREF = "avatar_replay"
    const val INTERVAL_MS = 20_000L
    private const val WALLPAPER_PACKAGE = "com.samsung.android.aremoji"
    private var startedAt = 0L

    val running get() = startedAt != 0L && SystemClock.uptimeMillis() - startedAt < 1500

    fun available(context: Context) = try { context.packageManager.getPackageInfo(WALLPAPER_PACKAGE, 0); true } catch (_: Exception) { false }

    fun trigger(context: Context, displayId: Int): Boolean = try {
        startedAt = SystemClock.uptimeMillis()
        val options = ActivityOptions.makeCustomAnimation(context, 0, 0).setLaunchDisplayId(displayId)
        if (Build.VERSION.SDK_INT >= 33) options.setSplashScreenStyle(android.window.SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR)
        context.startActivity(Intent(context, AvatarReplayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS), options.toBundle())
        true
    } catch (_: RuntimeException) { startedAt = 0L; false }

    internal fun finished() { startedAt = 0L }
}

class AvatarReplayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }
        setContentView(View(this))
        window.insetsController?.hide(WindowInsets.Type.systemBars())
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        window.decorView.postOnAnimation { close() }
    }

    private fun close() {
        if (isFinishing) return
        finish()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        AvatarReplay.finished()
        CoverMonitorService.current?.schedule()
        super.onDestroy()
    }
}
