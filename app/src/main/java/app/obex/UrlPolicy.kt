package app.obex

import android.net.Uri

/** Which tab the user is on. The route guard uses it: the home page "/" is only allowed on the Stories tab. */
enum class Mode(val js: String) { MESSAGES("messages"), STORIES("stories"), SEARCH("search"), PROFILE("profile") }

/**
 * The rules for which Instagram addresses are allowed.
 * The same rules exist in assets/guard.js, which catches page changes made inside the website;
 * this file catches full page loads. Keep the two in sync.
 */
object UrlPolicy {
    const val BASE = "https://www.instagram.com"
    const val INBOX = "$BASE/direct/inbox/"
    const val STORIES = "$BASE/"
    const val SEARCH = "$BASE/explore/search/"

    /** The only pages allowed to message the app, and to run Obex's script. */
    val BRIDGE_ORIGINS = setOf("https://www.instagram.com", "https://instagram.com")

    private val instagramHosts = listOf("instagram.com")

    /** Sites that may load inside the app (login, security checks). Everything else opens in Chrome. */
    private val inAppHosts = instagramHosts + listOf("facebook.com", "fbcdn.net", "cdninstagram.com")

    private fun hostMatches(host: String?, roots: List<String>): Boolean =
        host != null && roots.any { host == it || host.endsWith(".$it") }

    fun isInstagram(uri: Uri): Boolean = hostMatches(uri.host, instagramHosts)

    fun loadsInApp(uri: Uri): Boolean = hostMatches(uri.host, inAppHosts)

    fun isBlockedPath(rawPath: String?, mode: Mode): Boolean {
        val path = rawPath.orEmpty().ifEmpty { "/" }
        if (path == "/") return mode != Mode.STORIES
        // The endless Reels feed and reel-audio pages. Single reels (/reel/ID/ or /reels/ID/) are allowed.
        if (path == "/reels" || path == "/reels/" || path.startsWith("/reels/audio")) return true
        // The Explore page is blocked. Search (people, keyword results, hashtags) is allowed.
        if (path == "/explore" || path.startsWith("/explore/")) {
            return !(path.startsWith("/explore/search") || path.startsWith("/explore/tags/"))
        }
        return false
    }
}
