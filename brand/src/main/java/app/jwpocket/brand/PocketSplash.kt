package app.jwpocket.brand

import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider

/**
 * The launch sequence: the system splash (the icon on the suit color), then
 * [Overlay] takes over from the exact spot the system drew the icon and plays
 * the pop-out animation over the app, which is already composed underneath.
 *
 * The overlay starts with the activity's first frame, so it also plays where
 * the system shows no splash (Android TV); when the system splash does exit,
 * it restarts from the icon's exact position. Plays only on a fresh start
 * (not when the activity is restored); with animations turned off in the
 * system settings the app simply appears.
 */
class PocketSplash private constructor(play: Boolean) {

    /** The system splash, kept on screen until the overlay has drawn its first frame. */
    private var leaving: SplashScreenViewProvider? by mutableStateOf(null)
    /** Where the system drew the icon's full 108-unit viewport, in window pixels. */
    private var icon: Rect? by mutableStateOf(null)
    private var run by mutableIntStateOf(0)
    private var playing by mutableStateOf(play)

    /** Draw last, above the app. */
    @Composable
    fun Overlay() {
        if (!playing) return
        key(run) {
            PopOutSplash(
                icon = icon,
                onFirstFrame = { leaving?.remove(); leaving = null },
                onFinished = { playing = false },
            )
        }
    }

    companion object {
        /** Call before `super.onCreate()`. */
        fun install(activity: ComponentActivity, savedInstanceState: Bundle?): PocketSplash {
            val play = savedInstanceState == null && animationsEnabled()
            val splash = PocketSplash(play)
            activity.installSplashScreen().setOnExitAnimationListener { provider ->
                if (play) {
                    splash.icon = runCatching { provider.iconView.viewportInWindow() }.getOrNull()
                    splash.leaving = provider
                    splash.playing = true
                    splash.run++
                } else {
                    provider.remove()
                }
            }
            return splash
        }

        private fun animationsEnabled() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()

        /**
         * Android 12+ sizes the icon view to the 72-unit masked area (192dp) and draws the 108-unit
         * drawable past it; the compat splash on older versions draws the whole drawable in its 288dp view.
         */
        private fun android.view.View.viewportInWindow(): Rect {
            val xy = IntArray(2)
            getLocationInWindow(xy)
            val half = width / 2f * if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 108f / 72f else 1f
            val cx = xy[0] + width / 2f
            val cy = xy[1] + height / 2f
            return Rect(cx - half, cy - half, cx + half, cy + half)
        }
    }
}
