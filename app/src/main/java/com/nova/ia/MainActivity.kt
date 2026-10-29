package com.nova.ia

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.LinearLayout

class MainActivity : Activity() {

    companion object {
        // Adresse du site Nova IA : à changer ici si le site change
        const val SITE_URL = "https://singular-medovik-cd3a06.netlify.app"
        private const val REQ_FILE = 1
        private const val REQ_PERMS = 2
        private val DEFAULT_BG = Color.parseColor("#111111")

        // Lit la couleur de fond du site tout en haut et tout en bas de l'écran
        private const val SAMPLE_JS = """
            (function(){
              function bg(x,y){
                var e=document.elementFromPoint(x,y);
                while(e){
                  var c=getComputedStyle(e).backgroundColor;
                  var m=c.match(/[\d.]+/g);
                  if(m&&(m.length<4||parseFloat(m[3])>0.5)) return m[0]+','+m[1]+','+m[2];
                  e=e.parentElement;
                }
                return '17,17,17';
              }
              var w=window.innerWidth,h=window.innerHeight;
              return bg(w/2,2)+';'+bg(w/2,h-2);
            })()
        """
    }

    private lateinit var web: WebView
    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private val handler = Handler(Looper.getMainLooper())
    private val colorPoll = object : Runnable {
        override fun run() {
            sampleColors()
            handler.postDelayed(this, 1500)
        }
    }

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingRequest: PermissionRequest? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()

        web = WebView(this)
        topBar = View(this).apply { setBackgroundColor(DEFAULT_BG) }
        bottomBar = View(this).apply { setBackgroundColor(DEFAULT_BG) }

        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DEFAULT_BG)
            addView(topBar, LinearLayout.LayoutParams(match, 0))
            addView(web, LinearLayout.LayoutParams(match, 0, 1f))
            addView(bottomBar, LinearLayout.LayoutParams(match, 0))
        }
        root.setOnApplyWindowInsetsListener { _, insets ->
            topBar.layoutParams.height = insets.systemWindowInsetTop
            bottomBar.layoutParams.height = insets.systemWindowInsetBottom
            topBar.requestLayout()
            bottomBar.requestLayout()
            insets.consumeSystemWindowInsets()
        }
        setContentView(root)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
        }

        web.setDownloadListener { url, _, _, _, _ ->
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val siteHost = Uri.parse(SITE_URL).host
                if ((uri.scheme == "http" || uri.scheme == "https") && uri.host == siteHost) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                sampleColors()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(null, offlineHtml(), "text/html", "utf-8", null)
                }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handleWebPermission(request) }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl(SITE_URL)
    }

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        updateIcons(topLight = false, bottomLight = false)
    }

    @Suppress("DEPRECATION")
    private fun updateIcons(topLight: Boolean, bottomLight: Boolean) {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (Build.VERSION.SDK_INT >= 23 && topLight) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26 && bottomLight) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags
    }

    private fun sampleColors() {
        web.evaluateJavascript(SAMPLE_JS) { result ->
            try {
                val parts = (result ?: return@evaluateJavascript).trim('"').split(";")
                if (parts.size == 2) {
                    val top = parseRgb(parts[0])
                    val bottom = parseRgb(parts[1])
                    topBar.setBackgroundColor(top)
                    bottomBar.setBackgroundColor(bottom)
                    updateIcons(isLight(top), isLight(bottom))
                }
            } catch (_: Exception) {}
        }
    }

    private fun parseRgb(p: String): Int {
        val n = p.split(",").map { it.trim().toFloat().toInt().coerceIn(0, 255) }
        return Color.rgb(n[0], n[1], n[2])
    }

    private fun isLight(c: Int): Boolean =
        (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 160

    private fun handleWebPermission(request: PermissionRequest) {
        val needed = mutableListOf<String>()
        for (res in request.resources) {
            when (res) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> needed.add(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> needed.add(Manifest.permission.CAMERA)
            }
        }
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            request.grant(request.resources)
        } else {
            pendingRequest = request
            requestPermissions(missing.toTypedArray(), REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == REQ_PERMS) {
            val req = pendingRequest
            pendingRequest = null
            if (req != null) {
                if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) {
                    req.grant(req.resources)
                } else {
                    req.deny()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
        handler.removeCallbacks(colorPoll)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        handler.post(colorPoll)
    }

    override fun onDestroy() {
        handler.removeCallbacks(colorPoll)
        web.destroy()
        super.onDestroy()
    }

    private fun offlineHtml() = """
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
        body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
        background:#111;color:#fff;font-family:sans-serif;text-align:center;padding:24px}
        h2{color:#F77F00} button{margin-top:20px;padding:12px 28px;border:0;border-radius:24px;
        background:#009E60;color:#fff;font-size:16px}
        </style></head><body>
        <h2>Pas de connexion</h2>
        <p>Nova IA a besoin d'internet pour fonctionner.</p>
        <button onclick="location.href='$SITE_URL'">Réessayer</button>
        </body></html>
    """.trimIndent()
}
