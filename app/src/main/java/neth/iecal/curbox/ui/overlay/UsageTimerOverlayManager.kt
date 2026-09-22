package neth.iecal.curbox.ui.overlay

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import neth.iecal.curbox.CrashLogger
import neth.iecal.curbox.R
import neth.iecal.curbox.services.BaseBlockingService

/**
 * Small on screen countdown of the usage left before a limited app or website gets blocked.
 * Shared by AppBlocker and KeywordBlocker; [show] and [hide] may be called from any thread.
 */
class UsageTimerOverlayManager(private val service: BaseBlockingService) {

    companion object {
        const val SOURCE_APP = "app"
        const val SOURCE_WEBSITE = "website"
        private const val TOP_MARGIN_DP = 40
        private const val TAG = "UsageTimerOverlay"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentSource: String? = null
    private var endElapsed = 0L

    @Volatile var isEnabled = false
        private set

    private val ticker = object : Runnable {
        override fun run() {
            val remaining = endElapsed - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                removeView()
                return
            }
            val view = overlayView
            if (view != null) {
                view.text = service.getString(R.string.usage_timer_overlay_text, format(remaining))
                // Some launchers leave an accessibility overlay unpainted until its window is
                // touched again, so push the params along with the new text.
                runCatching { windowManager?.updateViewLayout(view, layoutParams) }
            }
            val untilNextSecond = remaining % 1_000L
            handler.postDelayed(this, if (untilNextSecond > 0L) untilNextSecond else 1_000L)
        }
    }

    fun setup() {
        scope.launch {
            service.dataStoreManager.settings
                .map { it.isUsageTimerOverlayEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    isEnabled = enabled
                    Log.d(TAG, "Countdown enabled: $enabled")
                    if (!enabled) handler.post { removeView() }
                }
        }
    }

    /**
     * Shows the countdown for [source]. A newer call replaces the countdown shown by any source.
     * When [requiredForeground] is given, the call is dropped if another app is in front by the
     * time it runs, so a late website check cannot paint over a different app.
     */
    fun show(source: String, remainingMillis: Long, requiredForeground: String? = null) {
        if (!isEnabled || remainingMillis <= 0L) return
        handler.post {
            try {
                if (requiredForeground != null) {
                    val foreground = service.rootInActiveWindow?.packageName?.toString()
                    if (foreground != null && foreground != requiredForeground) {
                        Log.d(TAG, "Skipped countdown for $source, $foreground is in front")
                        return@post
                    }
                }
                currentSource = source
                endElapsed = SystemClock.elapsedRealtime() + remainingMillis
                addViewIfNeeded()
                Log.d(TAG, "Showing countdown for $source: ${remainingMillis}ms")
                handler.removeCallbacks(ticker)
                ticker.run()
            } catch (e: Exception) {
                CrashLogger(service).logNonFatalError(e)
                removeView()
            }
        }
    }

    /** Hides the countdown, or only the one shown by [source] when it is given. */
    fun hide(source: String? = null) {
        handler.post {
            if (source == null || source == currentSource) removeView()
        }
    }

    fun release() {
        scope.cancel()
        handler.post { removeView() }
    }

    @SuppressLint("InflateParams")
    private fun addViewIfNeeded() {
        if (overlayView != null) return
        val view = LayoutInflater.from(service)
            .inflate(R.layout.overlay_usage_timer, null) as TextView
        // The countdown changes every second. Keep it out of the accessibility tree so the
        // text updates do not reach our own service as window content changes.
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (TOP_MARGIN_DP * service.resources.displayMetrics.density).toInt()
        }

        val manager = windowManager
            ?: (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .also { windowManager = it }
        manager.addView(view, params)
        layoutParams = params
        overlayView = view
    }

    private fun removeView() {
        handler.removeCallbacks(ticker)
        currentSource = null
        val view = overlayView ?: return
        overlayView = null
        layoutParams = null
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
        }
    }

    private fun format(millis: Long): String {
        val totalSeconds = (millis + 999L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
