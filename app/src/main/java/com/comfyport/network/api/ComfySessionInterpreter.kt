package com.comfyport.network.api

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.comfyport.network.AppLogger
import com.comfyport.network.UrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Standard ComfyUI Workflow Interpreter.
 * Uses the live ComfyUI web session and official `window.app.graphToPrompt()` in an embedded
 * session WebView to convert UI workflows into 100% standard ComfyUI API execution prompts.
 */
object ComfySessionInterpreter {

    private var appContext: Context? = null
    private var sessionWebView: WebView? = null
    private var currentServerUrl: String = ""
    var isAppReady: Boolean = false
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeCallback: ((success: Boolean, result: String) -> Unit)? = null

    class InterpreterBridge {
        @JavascriptInterface
        fun onAppReady() {
            AppLogger.i("ComfySessionInterpreter", "ComfyUI session window.app is ready")
            isAppReady = true
        }

        @JavascriptInterface
        fun onSuccess(apiPromptJson: String) {
            AppLogger.i("ComfySessionInterpreter", "Successfully interpreted workflow via window.app.graphToPrompt()")
            val cb = activeCallback
            activeCallback = null
            cb?.invoke(true, apiPromptJson)
        }

        @JavascriptInterface
        fun onError(errorMsg: String) {
            AppLogger.e("ComfySessionInterpreter", "Error in window.app.graphToPrompt(): $errorMsg")
            val cb = activeCallback
            activeCallback = null
            cb?.invoke(false, errorMsg)
        }
    }

    val bridge = InterpreterBridge()

    fun registerActiveWebView(wv: WebView) {
        sessionWebView = wv
        isAppReady = true
        mainHandler.post {
            try {
                wv.addJavascriptInterface(bridge, "ComfySessionBridge")
                monitorForAppReady(wv)
            } catch (e: Exception) {
                AppLogger.w("ComfySessionInterpreter", "Could not attach bridge to active WebView: ${e.message}")
            }
        }
        AppLogger.i("ComfySessionInterpreter", "Registered active ComfyUI preview WebView as session interpreter")
    }

    fun initSession(context: Context, serverUrl: String) {
        appContext = context.applicationContext
        val normalized = UrlValidator.normalizeUrl(serverUrl)
        if (normalized.isBlank()) return
        if (sessionWebView != null && currentServerUrl == normalized && isAppReady) return

        currentServerUrl = normalized
        isAppReady = false

        mainHandler.post {
            try {
                if (sessionWebView == null) {
                    sessionWebView = WebView(context.applicationContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        addJavascriptInterface(bridge, "ComfySessionBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                monitorForAppReady(this@apply)
                            }
                        }
                    }
                }
                sessionWebView?.loadUrl(normalized)
            } catch (e: Exception) {
                AppLogger.e("ComfySessionInterpreter", "Failed to initialize session WebView: ${e.localizedMessage}")
            }
        }
    }

    private fun monitorForAppReady(wv: WebView) {
        val js = """
            (function() {
                var checkCount = 0;
                function check() {
                    checkCount++;
                    if (window.app && typeof window.app.graphToPrompt === 'function') {
                        if (window.ComfySessionBridge) {
                            window.ComfySessionBridge.onAppReady();
                        }
                    } else if (checkCount < 80) {
                        setTimeout(check, 250);
                    }
                }
                check();
            })();
        """.trimIndent()
        wv.evaluateJavascript(js, null)
    }

    suspend fun convert(context: Context?, uiJsonString: String, serverUrl: String): String? = withContext(Dispatchers.Main) {
        val ctx = context?.applicationContext ?: appContext
        if (ctx != null && serverUrl.isNotBlank()) {
            initSession(ctx, serverUrl)
        }

        val wv = sessionWebView ?: return@withContext null

        // Check if window.app is already ready in the WebView DOM
        wv.evaluateJavascript("(function() { return Boolean(window.app && typeof window.app.graphToPrompt === 'function'); })()") { res ->
            if (res == "true" || res == "\"true\"") {
                isAppReady = true
            }
        }

        // Wait up to 10 seconds for window.app to be ready if it's still initializing
        if (!isAppReady) {
            val ready = withTimeoutOrNull(10000L) {
                suspendCancellableCoroutine<Boolean> { cont ->
                    val checkRunnable = object : Runnable {
                        override fun run() {
                            if (isAppReady) {
                                if (cont.isActive) cont.resume(true)
                            } else {
                                wv.evaluateJavascript("(function() { return Boolean(window.app && typeof window.app.graphToPrompt === 'function'); })()") { r ->
                                    if (r == "true" || r == "\"true\"") {
                                        isAppReady = true
                                        if (cont.isActive) cont.resume(true)
                                    } else {
                                        mainHandler.postDelayed(this, 250)
                                    }
                                }
                            }
                        }
                    }
                    mainHandler.post(checkRunnable)
                    cont.invokeOnCancellation {
                        mainHandler.removeCallbacks(checkRunnable)
                    }
                }
            }
            if (ready != true && !isAppReady) {
                AppLogger.w("ComfySessionInterpreter", "Session interpreter window.app timed out while initializing")
                return@withContext null
            }
        }

        // Run the official ComfyUI graphToPrompt interpreter
        withTimeoutOrNull(15000L) {
            suspendCancellableCoroutine<String?> { cont ->
                activeCallback = { success, result ->
                    if (cont.isActive) {
                        if (success) cont.resume(result) else cont.resume(null)
                    }
                }

                // Polling fallback in case JavascriptInterface callback is delayed or blocked
                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (activeCallback == null) return
                        wv.evaluateJavascript("(function() { if (window._comfySessionDone) { return window._comfySessionError ? ('ERR:' + window._comfySessionError) : window._comfySessionResult; } return null; })()") { pollRes ->
                            if (pollRes != null && pollRes != "null" && pollRes != "\"\"" && activeCallback != null) {
                                val cb = activeCallback
                                activeCallback = null
                                val cleaned = if (pollRes.startsWith("\"") && pollRes.endsWith("\"")) {
                                    try { com.google.gson.JsonParser.parseString(pollRes).asString } catch (_: Exception) { pollRes }
                                } else pollRes
                                if (cleaned.startsWith("ERR:")) {
                                    cb?.invoke(false, cleaned.removePrefix("ERR:"))
                                } else {
                                    cb?.invoke(true, cleaned)
                                }
                            } else if (activeCallback != null) {
                                mainHandler.postDelayed(this, 300)
                            }
                        }
                    }
                }
                mainHandler.postDelayed(pollRunnable, 300)
                cont.invokeOnCancellation {
                    mainHandler.removeCallbacks(pollRunnable)
                    activeCallback = null
                }

                val escapedJson = com.google.gson.Gson().toJson(uiJsonString)
                val js = """
                    (function() {
                        window._comfySessionDone = false;
                        window._comfySessionError = null;
                        window._comfySessionResult = null;
                        try {
                            var raw = $escapedJson;
                            var parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
                            if (parsed && parsed.workflow && parsed.workflow.nodes) parsed = parsed.workflow;

                            Promise.resolve().then(function() {
                                if (parsed && parsed.nodes && typeof window.app.loadGraphData === 'function') {
                                    return window.app.loadGraphData(parsed);
                                } else if (typeof window.app.loadApiJson === 'function') {
                                    return window.app.loadApiJson(parsed);
                                }
                            }).then(function() {
                                return window.app.graphToPrompt();
                            }).then(function(res) {
                                var output = (res && res.output) ? res.output : res;
                                var jsonStr = JSON.stringify(output);
                                window._comfySessionResult = jsonStr;
                                window._comfySessionDone = true;
                                if (window.ComfySessionBridge && typeof window.ComfySessionBridge.onSuccess === 'function') {
                                    window.ComfySessionBridge.onSuccess(jsonStr);
                                }
                            }).catch(function(err) {
                                var errStr = err.message || String(err);
                                window._comfySessionError = errStr;
                                window._comfySessionDone = true;
                                if (window.ComfySessionBridge && typeof window.ComfySessionBridge.onError === 'function') {
                                    window.ComfySessionBridge.onError(errStr);
                                }
                            });
                        } catch(syncErr) {
                            var syncErrStr = syncErr.message || String(syncErr);
                            window._comfySessionError = syncErrStr;
                            window._comfySessionDone = true;
                            if (window.ComfySessionBridge && typeof window.ComfySessionBridge.onError === 'function') {
                                window.ComfySessionBridge.onError(syncErrStr);
                            }
                        }
                    })();
                """.trimIndent()
                wv.evaluateJavascript(js, null)
            }
        }
    }
}
