package ru.raikmann.doorbell

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

const val PREFS = "doorbell"
const val KEY_PHONE = "phone"
const val KEY_URL = "resident_url"
const val API_URL = "https://cloud1.5855993.ru/door/api/resident/url"

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var setupLayout: LinearLayout
    private lateinit var etPhone: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView      = findViewById(R.id.webView)
        setupLayout  = findViewById(R.id.setupLayout)
        etPhone      = findViewById(R.id.etPhone)
        progressBar  = findViewById(R.id.progressBar)
        tvError      = findViewById(R.id.tvError)

        setupWebView()

        val savedUrl = getPrefs().getString(KEY_URL, "")
        if (!savedUrl.isNullOrBlank()) {
            showWebView(savedUrl)
        } else {
            showSetup()
        }

        findViewById<Button>(R.id.btnGo).setOnClickListener { confirmSetup() }
        etPhone.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { confirmSetup(); true } else false
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {}
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                if (req.url.host == "cloud1.5855993.ru") return false
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(req.url.toString())))
                val savedUrl = getPrefs().getString(KEY_URL, "")
                if (!savedUrl.isNullOrBlank()) view.loadUrl(savedUrl)
                return true
            }
        }
    }

    private fun showSetup() {
        setupLayout.visibility = View.VISIBLE
        webView.visibility = View.GONE
        progressBar.visibility = View.GONE
        tvError.visibility = View.GONE
        etPhone.requestFocus()
    }

    private fun showWebView(url: String) {
        setupLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        webView.loadUrl(url)
    }

    private fun confirmSetup() {
        val phone = etPhone.text.toString().trim()
        if (phone.isBlank()) return
        hideKeyboard()
        tvError.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        findViewById<Button>(R.id.btnGo).isEnabled = false

        Thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                OutputStreamWriter(conn.outputStream).use { it.write("{\"phone\":\"$phone\"}") }
                val body = if (conn.responseCode == 200)
                    conn.inputStream.bufferedReader().readText()
                else
                    conn.errorStream?.bufferedReader()?.readText() ?: "{}"
                val json = JSONObject(body)
                if (json.optBoolean("ok") && json.has("url")) {
                    val tokenUrl = json.getString("url")
                    val baseUrl = tokenUrl.substringBefore("?")
                    getPrefs().edit()
                        .putString(KEY_PHONE, phone)
                        .putString(KEY_URL, baseUrl)
                        .apply()
                    runOnUiThread { showWebView(tokenUrl); requestPinShortcut() }
                } else {
                    val err = json.optString("error", "Номер не найден в системе")
                    runOnUiThread { showError(err) }
                }
            } catch (e: Exception) {
                runOnUiThread { showError("Ошибка подключения к серверу") }
            }
        }.start()
    }

    private fun showError(msg: String) {
        progressBar.visibility = View.GONE
        tvError.text = msg
        tvError.visibility = View.VISIBLE
        findViewById<Button>(R.id.btnGo).isEnabled = true
    }

    private fun requestPinShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sm = getSystemService(ShortcutManager::class.java)
            if (sm.isRequestPinShortcutSupported) {
                val info = ShortcutInfo.Builder(this, "doorbell_main")
                    .setShortLabel("Домофон")
                    .setIcon(Icon.createWithResource(this, android.R.drawable.ic_menu_call))
                    .setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                    .build()
                sm.requestPinShortcut(info, null)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etPhone.windowToken, 0)
    }

    private fun getPrefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.visibility == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Домофон")
                    .setMessage("Изменить номер телефона?")
                    .setPositiveButton("Изменить") { _, _ ->
                        getPrefs().edit().remove(KEY_URL).remove(KEY_PHONE).apply()
                        etPhone.text.clear()
                        showSetup()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
