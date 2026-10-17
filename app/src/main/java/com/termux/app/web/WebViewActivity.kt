package com.termux.app.web

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.app.ui.theme.Accent
import com.termux.app.ui.theme.AccentSoft
import com.termux.app.ui.theme.Bg
import com.termux.app.ui.theme.BgElevated
import com.termux.app.ui.theme.CodyTheme
import com.termux.app.ui.theme.TextMuted
import com.termux.app.ui.theme.TextPrimary
import java.io.File

/**
 * In-app browser for links returned by the agent, provider sign-in / API-key pages and the
 * locally hosted `opencode web` dashboard served from http://127.0.0.1:<port>.
 *
 * It also previews local 3D models: [startModel] hands the file to [ViewerServer], which serves the
 * bundled viewer and the model from one origin so WebGL is not blocked as cross-origin.
 */
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        /** Absolute path of a local file to preview instead of a web page. */
        const val EXTRA_MODEL_PATH = "model_path"

        private const val TAG = "WebViewActivity"

        fun start(context: Context, url: String, title: String? = null) {
            val intent = Intent(context, WebViewActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Unable to open WebViewActivity for $url", e)
            }
        }

        /** Previews a local 3D model (.glb) through the bundled viewer. */
        fun startModel(context: Context, file: File, title: String? = null) {
            val intent = Intent(context, WebViewActivity::class.java)
                .putExtra(EXTRA_MODEL_PATH, file.absolutePath)
                .putExtra(EXTRA_TITLE, title ?: file.name)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "Unable to open the 3D viewer for ${file.absolutePath}", e)
            }
        }
    }

    // Held on the activity rather than remembered in composition so onSaveInstanceState can
    // reach it; the AndroidView factory publishes the view here.
    private val webViewState = mutableStateOf<WebView?>(null)

    // The WebView does not exist yet while onCreate runs, so the restore bundle is parked here
    // and consumed on the first AndroidView update instead.
    private var pendingRestore: Bundle? = null

    // Resolved before setContent: a model path is swapped for the viewer's loopback URL here
    // because starting a server is a side effect that does not belong in composition.
    private var launchUrl: String? = null

    private var isModel = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRestore = savedInstanceState

        val modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)?.trim()?.takeIf { it.isNotEmpty() }
        if (modelPath != null) {
            isModel = true
            launchUrl = ViewerServer.start(this, File(modelPath))
        } else {
            val raw = intent.getStringExtra(EXTRA_URL)?.trim()
            val scheme = runCatching { android.net.Uri.parse(raw).scheme?.lowercase() }.getOrNull()
            launchUrl = if (raw.isNullOrBlank() || (scheme != null && scheme !in setOf("http", "https"))) null else raw
        }

        setContent {
            CodyTheme {
                val launchTitle = remember {
                    intent.getStringExtra(EXTRA_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
                }

                var currentUrl by remember { mutableStateOf(launchUrl) }
                var progress by remember { mutableStateOf(0) }
                var pageTitle by remember { mutableStateOf(launchTitle) }
                var error by remember { mutableStateOf(false) }
                var urlMissing by remember { mutableStateOf(launchUrl.isNullOrBlank()) }
                val loaded = remember { mutableStateOf(false) }

                BackHandler {
                    val wv = webViewState.value
                    if (wv?.canGoBack() == true) wv.goBack() else finish()
                }

                Column(Modifier.fillMaxSize().background(Bg)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(BgElevated)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                            )
                        }
                        Text(
                            text = pageTitle ?: currentUrl ?: "Browser",
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { webViewState.value?.reload() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Reload", tint = TextMuted)
                        }
                        // Pointless for a model: the viewer's server dies with this activity.
                        if (!isModel) {
                            IconButton(onClick = {
                                val url = currentUrl ?: return@IconButton
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: ActivityNotFoundException) {
                                    Log.w(TAG, "No browser available for $url", e)
                                }
                            }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open in browser", tint = TextMuted)
                            }
                        }
                    }

                    if (progress < 100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = Accent,
                        )
                    }

                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        if (urlMissing) {
                            Text(
                                text = "No page to open.",
                                modifier = Modifier.align(Alignment.Center),
                                color = TextMuted,
                            )
                        } else {
                            AndroidView(
                                factory = { context ->
                                    WebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.javaScriptCanOpenWindowsAutomatically = true
                                        settings.setSupportMultipleWindows(false)
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.builtInZoomControls = false
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        settings.cacheMode = WebSettings.LOAD_DEFAULT

                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView,
                                                request: WebResourceRequest,
                                            ): Boolean {
                                                val scheme = request.url.scheme?.lowercase()
                                                // Web pages stay inside this WebView; anything else
                                                // (mailto:, intent:, ...) goes to the system.
                                                if (scheme == "http" || scheme == "https") return false
                                                return try {
                                                    view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                                    true
                                                } catch (e: ActivityNotFoundException) {
                                                    Log.w(TAG, "No handler for ${request.url}", e)
                                                    true
                                                }
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                currentUrl = url
                                                error = false
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                currentUrl = url
                                                progress = 100
                                                // A caller-supplied label (e.g. "Sign in") stays put;
                                                // otherwise follow the document title.
                                                if (launchTitle == null) pageTitle = view?.title
                                            }

                                            // The parameter cannot be called `error`: that name is the
                                            // compose flag this callback sets.
                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest,
                                                err: WebResourceError?,
                                            ) {
                                                if (request.isForMainFrame) error = true
                                            }

                                            override fun onReceivedSslError(
                                                view: WebView?,
                                                handler: SslErrorHandler,
                                                error: SslError?,
                                            ) {
                                                // Never proceed: the system rejected this certificate.
                                                handler.cancel()
                                            }
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                progress = newProgress
                                            }
                                        }

                                        webViewState.value = this
                                    }
                                },
                                update = { view ->
                                    webViewState.value?.settings?.setMixedContentMode(
                                        WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE,
                                    )

                                    // Load only once, so recomposition never restarts the page.
                                    if (!loaded.value) {
                                        loaded.value = true
                                        val bundle = pendingRestore
                                        val restored = bundle != null && view.restoreState(bundle) != null
                                        if (!restored) {
                                            launchUrl?.let { view.loadUrl(it) }
                                        }
                                        view.url?.let { currentUrl = it }
                                    }
                                },
                            )

                            if (error) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Bg)
                                        .align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text("Couldn't load this page.", color = TextMuted)
                                    TextButton(onClick = {
                                        error = false
                                        webViewState.value?.reload()
                                    }) {
                                        Text("Retry", color = AccentSoft)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webViewState.value?.saveState(outState)
    }

    /**
     * Releases the loopback viewer server. Without this the socket and its accept thread outlive
     * the preview: [ViewerServer] is a process-wide singleton, so every model opened afterwards
     * would reuse a URL pointing at a model the user is no longer looking at.
     *
     * Deliberately not on `onStop`: rotating the device or a brief trip to the background should
     * not throw away the preview that is still showing behind us.
     */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && isModel) ViewerServer.stop()
    }
}
