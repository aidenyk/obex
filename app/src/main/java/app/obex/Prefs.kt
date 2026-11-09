package app.obex

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object Prefs {
    private const val FILE = "settings"
    private const val KEY_LOCK = "lock_enabled"

    fun isLockEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_LOCK, true)

    fun setLockEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_LOCK, enabled).apply()
    }

    /** Your profile address (e.g. "/yourname/") and picture, read from Instagram's page. */
    fun profilePath(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("profile_path", null)

    fun avatarUrl(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("avatar_url", null)

    fun setProfile(ctx: Context, path: String, avatarUrl: String?) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("profile_path", path).putString("avatar_url", avatarUrl).apply()
    }

    /** True when the user has switched on our Accessibility service in Android Settings. */
    fun isLockServiceEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, InstagramLockService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
