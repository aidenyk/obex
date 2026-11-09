package app.obex

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FROM_LOCK = "from_lock"
    }

    private lateinit var web: WebView
    private lateinit var dock: View
    private lateinit var dockDivider: View
    private lateinit var profileImage: ShapeableImageView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var settingsPanel: View
    private lateinit var lockSwitch: MaterialSwitch
    private lateinit var lockStatus: TextView
    private lateinit var lockSteps: View

    /** Current tab mode. Sent to guard.js, which only allows the home page on the Stories tab. */
    private var mode: Mode = Mode.MESSAGES

    /** Which dock button is highlighted. */
    private var selectedTab = R.id.tab_messages

    private val tabIcons = mapOf(
        R.id.tab_messages to (R.drawable.ic_dm to R.drawable.ic_dm_active),
        R.id.tab_stories to (R.drawable.ic_stories to R.drawable.ic_stories_active),
        R.id.tab_search to (R.drawable.ic_search to R.drawable.ic_search_active),
        R.id.tab_lock to (R.drawable.ic_lock to R.drawable.ic_lock_active),
    )

    /** The avatar address currently shown in the dock, so we don't download it twice. */
    private var shownAvatarUrl: String? = null

    private lateinit var guardScript: String
    private var documentStartSupported = false

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pendingPermission?.let(::grantWhatWeCan)
            pendingPermission = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        web = findViewById(R.id.web)
        dock = findViewById(R.id.dock)
        dockDivider = findViewById(R.id.dock_divider)
        profileImage = findViewById(R.id.profile_image)
        progress = findViewById(R.id.progress)
        settingsPanel = findViewById(R.id.settings_panel)
        lockSwitch = findViewById(R.id.lock_switch)
        lockStatus = findViewById(R.id.lock_status)
        lockSteps = findViewById(R.id.lock_steps)

        setupInsets()
        setupWebView()
        setupNav()
        setupSettings()
        setupBack()

        openTab(R.id.tab_messages)
    }

    // ---------- Layout ----------

    /** Keep content clear of the status bar, the gesture bar and the keyboard. */
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // Hide the dock while typing. Otherwise the dock's background runs under the gesture bar.
            dock.isVisible = !imeVisible
            dockDivider.isVisible = !imeVisible
            dock.setPadding(0, 0, 0, bars.bottom)
            v.setPadding(bars.left, bars.top, bars.right, if (imeVisible) ime.bottom else 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    // ---------- WebView ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Debug builds only: lets Claude inspect the page from the Mac to tune the hiding rules.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            // Look like normal Chrome, so Instagram doesn't nag you to "open the app".
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/[\\d.]+ "), "")
        }

        // No scroll bar flashing on the right edge while scrolling.
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false

        setupBridge()

        guardScript = buildGuardScript()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Runs before Instagram's own code on every page.
            WebViewCompat.addDocumentStartJavaScript(
                web, guardScript, UrlPolicy.BRIDGE_ORIGINS
            )
            documentStartSupported = true
        }

        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        web.setDownloadListener { url, _, _, _, _ -> openExternal(Uri.parse(url)) }
    }

    private fun buildGuardScript(): String {
        val css = assets.open("hide.css").bufferedReader().use { it.readText() }
        val js = assets.open("guard.js").bufferedReader().use { it.readText() }
        return "window.__RB_CSS = ${JSONObject.quote(css)};\n$js"
    }

    /** The page that most recently said hello, so we can tell it when the tab changes. */
    private var pageProxy: JavaScriptReplyProxy? = null

    private fun modeMessage() = JSONObject().put("mode", mode.js).toString()

    /**
     * Two-way messages with guard.js. Android only gives the "RB" object to instagram.com pages,
     * so no other website loaded in the app (e.g. during login) can send the app anything.
     */
    private fun setupBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        WebViewCompat.addWebMessageListener(web, "RB", UrlPolicy.BRIDGE_ORIGINS) { _, message, _, isMainFrame, replyProxy ->
            if (!isMainFrame) return@addWebMessageListener
            val data = try {
                JSONObject(message.data ?: return@addWebMessageListener)
            } catch (e: Exception) {
                return@addWebMessageListener
            }
            when (data.optString("type")) {
                "hello" -> {
                    pageProxy = replyProxy
                    replyProxy.postMessage(modeMessage())
                }
                "path" -> syncTabToPath(data.optString("path"))
                "profile" -> onProfile(data.optString("path"), data.optString("avatar"))
            }
        }
    }

    /** guard.js found your profile link and picture in Instagram's (hidden) bottom bar. */
    private fun onProfile(path: String, avatarUrl: String) {
        if (!Regex("^/[A-Za-z0-9._]+/$").matches(path)) return
        val avatar = avatarUrl.takeIf { url ->
            val uri = Uri.parse(url)
            uri.scheme == "https" && (uri.host.orEmpty().endsWith(".fbcdn.net") ||
                uri.host.orEmpty().endsWith(".cdninstagram.com"))
        }
        if (path != Prefs.profilePath(this) || avatar != Prefs.avatarUrl(this)) {
            Prefs.setProfile(this, path, avatar)
        }
        loadAvatar()
        if (openProfileWhenKnown) {
            openProfileWhenKnown = false
            openTab(R.id.tab_profile)
        }
    }

    /** Keeps the highlighted tab in step when you move around inside Instagram. */
    private fun syncTabToPath(path: String) {
        val (tab, newMode) = when {
            path.startsWith("/direct") -> R.id.tab_messages to Mode.MESSAGES
            // The home page only ever counts as Stories if you're already on the Stories tab.
            path == "/" && mode == Mode.STORIES -> R.id.tab_stories to Mode.STORIES
            path.startsWith("/explore/search") -> R.id.tab_search to Mode.SEARCH
            path == Prefs.profilePath(this) -> R.id.tab_profile to Mode.PROFILE
            else -> return
        }
        if (mode != newMode) {
            mode = newMode
            pageProxy?.postMessage(modeMessage())
        }
        if (!settingsPanel.isVisible) highlightTab(tab)
    }

    /** Shows your Instagram profile picture in the dock (downloaded once, then cached). */
    private fun loadAvatar() {
        val url = Prefs.avatarUrl(this) ?: return
        if (url == shownAvatarUrl) return
        shownAvatarUrl = url
        val cache = java.io.File(filesDir, "avatar.jpg")
        Thread {
            val bitmap = try {
                java.net.URL(url).openStream().use { input ->
                    cache.outputStream().use { input.copyTo(it) }
                }
                android.graphics.BitmapFactory.decodeFile(cache.path)
            } catch (e: Exception) {
                if (cache.exists()) android.graphics.BitmapFactory.decodeFile(cache.path) else null
            }
            if (bitmap != null) runOnUiThread {
                profileImage.setPadding(0, 0, 0, 0)
                profileImage.setImageBitmap(bitmap)
                highlightTab(selectedTab)
            }
        }.start()
    }

    private fun showCachedAvatar() {
        val cache = java.io.File(filesDir, "avatar.jpg")
        if (!cache.exists()) return
        android.graphics.BitmapFactory.decodeFile(cache.path)?.let {
            profileImage.setPadding(0, 0, 0, 0)
            profileImage.setImageBitmap(it)
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> when {
                    UrlPolicy.isInstagram(uri) && UrlPolicy.isBlockedPath(uri.path, mode) -> {
                        view.loadUrl(UrlPolicy.INBOX); true
                    }
                    UrlPolicy.loadsInApp(uri) -> false
                    else -> { openExternal(uri); true }
                }
                // Never hand off to the real Instagram app.
                "intent", "instagram" -> true
                // mailto:, tel:, etc.
                else -> { openExternal(uri); true }
            }
        }

        // Backup for phones whose WebView can't run scripts before the page (rare on a 2025 phone).
        override fun onPageCommitVisible(view: WebView, url: String) = injectFallback(view, url)
        override fun onPageFinished(view: WebView, url: String) = injectFallback(view, url)

        private fun injectFallback(view: WebView, url: String) {
            if (!documentStartSupported && UrlPolicy.isInstagram(Uri.parse(url))) {
                view.evaluateJavascript(guardScript, null)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            recreate()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.isVisible = newProgress < 100
            progress.setProgressCompat(newProgress, true)
        }

        /** Attach photos and videos to DMs. */
        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            val intent = params.createIntent().apply {
                if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            return try {
                filePicker.launch(intent); true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null; false
            }
        }

        /** Camera and microphone for voice messages and video. */
        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                if (!UrlPolicy.isInstagram(request.origin)) {
                    request.deny(); return@runOnUiThread
                }
                val missing = request.resources
                    .mapNotNull(::androidPermissionFor)
                    .distinct()
                    .filterNot(::hasPermission)
                if (missing.isEmpty()) {
                    grantWhatWeCan(request)
                } else {
                    pendingPermission?.deny()
                    pendingPermission = request
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) {
            if (pendingPermission == request) pendingPermission = null
        }
    }

    private fun androidPermissionFor(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        else -> null
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun grantWhatWeCan(request: PermissionRequest) {
        val ok = request.resources.filter { r -> androidPermissionFor(r)?.let(::hasPermission) == true }
        if (ok.isEmpty()) request.deny() else request.grant(ok.toTypedArray())
    }

    /** Opens non-Instagram links in Chrome (or whichever app handles them). */
    private fun openExternal(uri: Uri) {
        if (UrlPolicy.isInstagram(uri)) return // would open the real Instagram app
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
        }
    }

    // ---------- Tabs ----------

    private fun setupNav() {
        for (id in listOf(R.id.tab_messages, R.id.tab_stories, R.id.tab_search, R.id.tab_profile, R.id.tab_lock)) {
            // Tapping the tab you're already on reloads it.
            findViewById<View>(id).setOnClickListener { openTab(id) }
        }
        showCachedAvatar()
        loadAvatar()
        highlightTab(selectedTab)
    }

    /** Filled icon for the selected tab, outline for the rest (like Instagram). */
    private fun highlightTab(id: Int) {
        selectedTab = id
        for ((tab, icons) in tabIcons) {
            findViewById<android.widget.ImageView>(tab)
                .setImageResource(if (tab == id) icons.second else icons.first)
        }
        profileImage.strokeColor = android.content.res.ColorStateList.valueOf(
            if (id == R.id.tab_profile) ContextCompat.getColor(this, R.color.ig_icon)
            else android.graphics.Color.TRANSPARENT
        )
    }

    /** Set when you tap Profile before the app knows your username. */
    private var openProfileWhenKnown = false

    private fun openTab(id: Int) {
        highlightTab(id)
        if (id == R.id.tab_lock) {
            settingsPanel.isVisible = true
            refreshLockStatus()
            return
        }
        settingsPanel.isVisible = false
        val profilePath = Prefs.profilePath(this)
        val (newMode, url) = when (id) {
            R.id.tab_stories -> Mode.STORIES to UrlPolicy.STORIES
            R.id.tab_search -> Mode.SEARCH to UrlPolicy.SEARCH
            R.id.tab_profile ->
                if (profilePath != null) Mode.PROFILE to UrlPolicy.BASE + profilePath
                // Username not known yet: load the stories page, where Instagram shows it, then continue.
                else { openProfileWhenKnown = true; Mode.STORIES to UrlPolicy.STORIES }
            else -> Mode.MESSAGES to UrlPolicy.INBOX
        }
        mode = newMode
        web.loadUrl(url)
    }

    private fun currentPath(): String = web.url?.let { Uri.parse(it).path }.orEmpty()

    private fun isOnTabRoot(): Boolean {
        val path = currentPath()
        return path.isEmpty() || path == "/" || path.trimEnd('/') == "/direct/inbox" ||
            path.trimEnd('/') == "/explore/search" || path == Prefs.profilePath(this)
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    settingsPanel.isVisible -> openTab(R.id.tab_messages)
                    web.canGoBack() && !isOnTabRoot() -> web.goBack()
                    else -> moveTaskToBack(true)
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The lock sent us here. Show the inbox unless you're already in your DMs.
        if (intent.getBooleanExtra(EXTRA_FROM_LOCK, false)) {
            if (settingsPanel.isVisible || !currentPath().startsWith("/direct")) {
                openTab(R.id.tab_messages)
            }
        }
    }

    // ---------- Lock settings ----------

    private fun setupSettings() {
        lockSwitch.isChecked = Prefs.isLockEnabled(this)
        lockSwitch.setOnCheckedChangeListener { _, checked -> Prefs.setLockEnabled(this, checked) }
        findViewById<View>(R.id.open_accessibility).setOnClickListener { showLockDisclosure() }
        findViewById<View>(R.id.reload).setOnClickListener { openTab(R.id.tab_messages) }
    }

    /** Explains exactly what the Accessibility permission is used for before sending you to Settings. */
    private fun showLockDisclosure() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_message)
            .setPositiveButton(R.string.disclosure_continue) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.disclosure_cancel, null)
            .show()
    }

    private fun refreshLockStatus() {
        val on = Prefs.isLockServiceEnabled(this)
        lockStatus.setText(if (on) R.string.settings_service_on else R.string.settings_service_off)
        lockSteps.isVisible = !on
    }

    // ---------- Lifecycle ----------

    override fun onResume() {
        super.onResume()
        web.onResume()
        if (settingsPanel.isVisible) refreshLockStatus()
    }

    override fun onPause() {
        web.onPause()
        CookieManager.getInstance().flush() // keeps you signed in
        super.onPause()
    }

    override fun onDestroy() {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
        super.onDestroy()
    }
}
