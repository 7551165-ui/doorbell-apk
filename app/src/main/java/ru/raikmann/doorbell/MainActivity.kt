package ru.raikmann.doorbell

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

const val PREFS = "doorbell"
const val KEY_APT_ID = "apt_id"
const val BASE_URL = "https://cloud1.5855993.ru/door/web/"

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var setupLayout: LinearLayout
    private lateinit var etAptId: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        setupLayout = findViewById(R.id.setupLayout)
        etAptId = findViewById(R.id.etAptId)

        setupWebView()

        val savedAptId = getPrefs().getString(KEY_APT_ID, "")
        if (!savedAptId.isNullOrBlank()) {
            showWebView(savedAptId)
        } else {
            showSetup()
        }

        findViewById<Button>(R.id.btnGo).setOnClickListener { confirmSetup() }
        etAptId.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { confirmSetup(); true } else false
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false   // видео и звук без тапа
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                // страница сама покажет ошибку
            }
        }
    }

    private fun showSetup() {
        setupLayout.visibility = View.VISIBLE
        webView.visibility = View.GONE
        etAptId.requestFocus()
    }

    private fun showWebView(aptId: String) {
        setupLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(BASE_URL + aptId)
    }

    private fun confirmSetup() {
        val aptId = etAptId.text.toString().trim()
        if (aptId.isBlank()) return
        hideKeyboard()
        getPrefs().edit().putString(KEY_APT_ID, aptId).apply()
        showWebView(aptId)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etAptId.windowToken, 0)
    }

    private fun getPrefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.visibility == View.VISIBLE && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
