package com.twinspace.app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

open class CloneActivity : AppCompatActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: "https://example.com"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Clone"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#09090B"))
        }

        val toolbar = Toolbar(this).apply {
            setBackgroundColor(Color.parseColor("#131316"))
            setTitleTextColor(Color.parseColor("#F4F4F5"))
            this.title = title
            setNavigationOnClickListener { finish() }
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            if (::web.isInitialized && web.canGoBack()) web.goBack() else finish()
        }

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#09090B"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        root.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.loadUrl(url)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_CLONE_ID = "cloneId"
    }
}

class CloneHost1 : CloneActivity()
class CloneHost2 : CloneActivity()
class CloneHost3 : CloneActivity()
class CloneHost4 : CloneActivity()
class CloneHost5 : CloneActivity()
class CloneHost6 : CloneActivity()
class CloneHost7 : CloneActivity()
class CloneHost8 : CloneActivity()

object CloneHosts {
    private val classes = listOf(
        CloneHost1::class.java,
        CloneHost2::class.java,
        CloneHost3::class.java,
        CloneHost4::class.java,
        CloneHost5::class.java,
        CloneHost6::class.java,
        CloneHost7::class.java,
        CloneHost8::class.java,
    )

    fun activityFor(cloneId: String): Class<out CloneActivity> {
        val slot = cloneId.hashCode().and(0x7fffffff) % classes.size
        return classes[slot]
    }
}
