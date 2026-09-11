package com.orchords.orchordsai.ui.components.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup.LayoutParams
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private const val TAG = "WebView"

internal class MyWebChromeClient(private val state: WebViewState) : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        state.loadingProgress = newProgress / 100f
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        state.pageTitle = title
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        state.pushConsoleMessage(consoleMessage)
        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR || consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING) {
            Log.e(
                TAG,
                "onConsoleMessage: level=${consoleMessage.messageLevel()} line=${consoleMessage.lineNumber()}"
            )
        }
        return super.onConsoleMessage(consoleMessage)
    }
}

internal class MyWebViewClient(private val state: WebViewState) : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val allowed = isAllowedWebViewMainFrameUrl(state.securityProfile, request.url.toString())
        if (!allowed) return blockedResource()

        return when (state.securityProfile) {
            WebViewSecurityProfile.INTERNAL_TRUSTED ->
                WebViewLocalAssets.intercept(view.context.applicationContext, request.url) ?: blockedResource()
            WebViewSecurityProfile.EXTERNAL_WEB -> super.shouldInterceptRequest(view, request)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return !isAllowedWebViewMainFrameUrl(state.securityProfile, request.url.toString())
    }

    @Deprecated("Deprecated in Android")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url == null || !isAllowedWebViewMainFrameUrl(state.securityProfile, url)

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        state.isLoading = true
        state.currentUrl = url
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        state.isLoading = false
        state.loadingProgress = 0f
        state.pageTitle = view?.title
        state.canGoBack = view?.canGoBack() == true
        state.canGoForward = view?.canGoForward() == true
    }

    private fun blockedResource(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0)),
    )
}

private fun WebView.resetState(
    interfaces: Map<String, Any>,
    clearClients: Boolean = false,
) {
    stopLoading()
    interfaces.forEach { (name, _) ->
        removeJavascriptInterface(name)
    }
    if (clearClients) {
        webChromeClient = null
        webViewClient = WebViewClient()
    }
}

private fun WebView.release(interfaces: Map<String, Any>) {
    resetState(interfaces, clearClients = true)
    loadUrl("about:blank")
    clearHistory()
    removeAllViews()
    destroy()
}

@Suppress("DEPRECATION")
private fun WebSettings.applySecurityProfile(state: WebViewState) {
    allowFileAccess = false
    allowContentAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)
    safeBrowsingEnabled = true

    when (state.securityProfile) {
        WebViewSecurityProfile.EXTERNAL_WEB -> {
            javaScriptEnabled = false
            domStorageEnabled = false
        }
        WebViewSecurityProfile.INTERNAL_TRUSTED -> {
            javaScriptEnabled = state.javaScriptEnabled
            domStorageEnabled = state.javaScriptEnabled
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun WebView(
    state: WebViewState,
    modifier: Modifier = Modifier,
    onCreated: (WebView) -> Unit = {},
    onUpdated: (WebView) -> Unit = {},
) {
    val webChromeClient = remember { MyWebChromeClient(state) }
    val webViewClient = remember { MyWebViewClient(state) }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT
                    )

                    state.webView = this
                    onCreated(this)

                    settings.apply(state.settings)
                    settings.applySecurityProfile(state)

                    this.webChromeClient = webChromeClient
                    this.webViewClient = webViewClient

                    if (state.securityProfile == WebViewSecurityProfile.INTERNAL_TRUSTED) {
                        state.interfaces.forEach { (name, obj) ->
                            addJavascriptInterface(obj, name)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            onReset = {
                it.resetState(state.interfaces)
                Log.d(TAG, "AndroidView: Resetting WebView")
            },
            onRelease = {
                if (state.webView === it) {
                    state.webView = null
                }
                it.release(state.interfaces)
                Log.d(TAG, "AndroidView: Releasing WebView")
            },
            update = { webView ->
                state.webView = webView
                webView.settings.applySecurityProfile(state)
                if (state.securityProfile == WebViewSecurityProfile.INTERNAL_TRUSTED) {
                    state.interfaces.forEach { (name, obj) ->
                        webView.addJavascriptInterface(obj, name)
                    }
                }
                Log.d(TAG, "AndroidView: Updating WebView")

                when (val content = state.content) {
                    is WebContent.Url -> {
                        val url = content.url
                        val currentWebViewUrl = webView.url
                        if (
                            url.isNotEmpty() &&
                            isAllowedWebViewMainFrameUrl(state.securityProfile, url) &&
                            (currentWebViewUrl.isNullOrBlank() || url != currentWebViewUrl || state.forceReload)
                        ) {
                            webView.loadUrl(content.url, content.additionalHttpHeaders)
                            state.forceReload = false
                        }
                    }

                    is WebContent.Data -> {
                        if (
                            isAllowedWebViewMainFrameUrl(
                                WebViewSecurityProfile.INTERNAL_TRUSTED,
                                content.baseUrl.orEmpty(),
                            ) &&
                            (content != state.lastLoadedData || state.forceReload)
                        ) {
                            webView.loadDataWithBaseURL(
                                content.baseUrl,
                                content.data,
                                content.mimeType,
                                content.encoding,
                                content.historyUrl
                            )
                            state.lastLoadedData = content
                            state.forceReload = false
                        }
                    }

                    WebContent.NavigatorOnly -> Unit
                }
                onUpdated(webView)
            }
        )

        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.loadingProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

sealed class WebContent {
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
        val clearHistory: Boolean = false
    ) : WebContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val encoding: String = "utf-8",
        val mimeType: String? = null,
        val historyUrl: String? = null
    ) : WebContent()

    data object NavigatorOnly : WebContent()
}

@Stable
class WebViewState(
    initialContent: WebContent = WebContent.NavigatorOnly,
    val interfaces: Map<String, Any> = emptyMap(),
    val settings: WebSettings.() -> Unit = {},
    val securityProfile: WebViewSecurityProfile = WebViewSecurityProfile.INTERNAL_TRUSTED,
) {
    init {
        require(securityProfile == WebViewSecurityProfile.INTERNAL_TRUSTED || interfaces.isEmpty()) {
            "External WebView content cannot expose JavaScript interfaces"
        }
    }

    var content: WebContent by mutableStateOf(initialContent)
    internal var forceReload: Boolean by mutableStateOf(false)
    internal var lastLoadedData: WebContent.Data? = null

    var isLoading: Boolean by mutableStateOf(false)
        internal set
    var loadingProgress: Float by mutableFloatStateOf(0f)
        internal set

    var pageTitle: String? by mutableStateOf(null)
        internal set
    var currentUrl: String? by mutableStateOf(null)
        internal set

    var canGoBack: Boolean by mutableStateOf(false)
        internal set
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    var consoleMessages: List<ConsoleMessage> by mutableStateOf(emptyList())
        internal set

    var javaScriptEnabled: Boolean by mutableStateOf(
        securityProfile == WebViewSecurityProfile.INTERNAL_TRUSTED
    )

    internal var webView: WebView? by mutableStateOf(null)

    fun loadUrl(
        url: String,
        additionalHttpHeaders: Map<String, String> = emptyMap()
    ) {
        if (!isAllowedWebViewMainFrameUrl(securityProfile, url)) return
        forceReload =
            (content is WebContent.Url && (content as WebContent.Url).url == url) || forceReload
        content = WebContent.Url(url, additionalHttpHeaders)
    }

    fun loadData(
        data: String,
        baseUrl: String? = null,
        encoding: String = "utf-8",
        mimeType: String? = null,
        historyUrl: String? = null
    ) {
        if (!isAllowedWebViewMainFrameUrl(WebViewSecurityProfile.INTERNAL_TRUSTED, baseUrl.orEmpty())) return
        content = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl)
    }

    fun goBack() {
        webView?.goBack()
    }

    fun goForward() {
        webView?.goForward()
    }

    fun reload() {
        forceReload = true
        webView?.reload()
        if (content is WebContent.Data) {
            content = (content as WebContent.Data).copy()
        }
    }

    fun stopLoading() {
        webView?.stopLoading()
    }

    fun clearHistory() {
        webView?.clearHistory()
    }

    fun pushConsoleMessage(message: ConsoleMessage) {
        consoleMessages = consoleMessages + message
        if (consoleMessages.size > 64) {
            consoleMessages = consoleMessages.takeLast(64)
        }
    }
}

@Composable
fun rememberWebViewState(
    url: String = "about:blank",
    additionalHttpHeaders: Map<String, String> = emptyMap(),
    interfaces: Map<String, Any> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
    securityProfile: WebViewSecurityProfile = WebViewSecurityProfile.EXTERNAL_WEB,
) = remember(url, additionalHttpHeaders, interfaces, securityProfile) {
    WebViewState(
        initialContent = WebContent.Url(url, additionalHttpHeaders),
        interfaces = interfaces,
        settings = settings,
        securityProfile = securityProfile,
    )
}

@Composable
fun rememberWebViewState(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null,
    interfaces: Map<String, Any> = emptyMap(),
    settings: WebSettings.() -> Unit = {},
    securityProfile: WebViewSecurityProfile = WebViewSecurityProfile.INTERNAL_TRUSTED,
) = remember(data, baseUrl, encoding, mimeType, historyUrl, interfaces, securityProfile) {
    WebViewState(
        initialContent = WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl),
        interfaces = interfaces,
        settings = settings,
        securityProfile = securityProfile,
    )
}
