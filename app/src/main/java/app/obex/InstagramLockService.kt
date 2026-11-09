package app.obex

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Android's version of Konvo's Screen Time lock.
 * Android only tells this service when an Instagram window comes to the front
 * (see res/xml/lock_service_config.xml). It never reads screen content.
 * When Instagram comes to the front, this service opens Obex instead.
 */
class InstagramLockService : AccessibilityService() {

    private var lastRedirect = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != INSTAGRAM_PACKAGE) return
        if (!Prefs.isLockEnabled(this)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 700) return
        lastRedirect = now

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_FROM_LOCK, true)
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
