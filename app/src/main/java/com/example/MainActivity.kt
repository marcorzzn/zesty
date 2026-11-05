package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * MainActivity - Picsart Studio APK Native Host
 * 
 * Configura una WebView a schermo intero ad altissime prestazioni:
 * - Accelerazione Hardware GPU per Canvas 2D e Shader WebGL
 * - Abilitazione di JavaScript, DomStorage e accesso ai file locali
 * - File Chooser nativo per importare foto dalla Galleria del dispositivo
 * - AndroidBridge e DownloadListener per salvare i file esportati direttamente nel rullino fotografico (MediaStore)
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Launcher per la selezione di immagini dalla Galleria nativa
    private val filePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val uriList = mutableListOf<Uri>()

                if (data?.clipData != null) {
                    val count = data.clipData!!.itemCount
                    for (i in 0 until count) {
                        uriList.add(data.clipData!!.getItemAt(i).uri)
                    }
                } else if (data?.data != null) {
                    uriList.add(data.data!!)
                }

                fileUploadCallback?.onReceiveValue(uriList.toTypedArray())
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configurazione Display a Schermo Intero & Edge-to-Edge
        configureFullScreen()

        // 2. Inizializzazione WebView con Accelerazione Hardware attiva
        webView = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.parseColor("#0B0B0E"))
        }

        // 3. Impostazioni WebSettings avanzate (WebGL, Shaders GLSL, File Locali, DomStorage)
        configureWebSettings(webView.settings)

        // 4. Ponte Nativo JavaScript -> Kotlin per salvataggio istantaneo nella Galleria
        webView.addJavascriptInterface(AndroidBridge(this), "AndroidBridge")

        // 5. WebChromeClient per messaggi console e selettore foto dalla Galleria
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                filePickerLauncher.launch(Intent.createChooser(intent, "Seleziona Immagine"))
                return true
            }
        }

        // 6. WebViewClient per mantenere la navigazione interna all'app
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                // Se è un link esterno HTTP(S), aprilo nel browser; se è locale resta dentro l'app
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    } catch (e: Exception) {
                        false
                    }
                } else {
                    false
                }
            }
        }

        // 7. DownloadListener di backup per intercettare download da link HTML5
        webView.setDownloadListener(DownloadListener { url, _, _, mimeType, _ ->
            if (url != null && (url.startsWith("data:image/") || url.startsWith("blob:"))) {
                saveDataUriImage(url, mimeType ?: "image/png")
            }
        })

        // 8. Gestione del pulsante Indietro (Back)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        setContentView(webView)

        // 9. Carica l'applicazione dal percorso asset locale sincronizzato
        // Usa direttamente l'editor HTML Picsart
        webView.loadUrl("file:///android_asset/www/assets/aistudio/deepseek_html_20261030_ba07c6_cf515316.html")
    }

    /**
     * Configura la visualizzazione immersiva a schermo intero
     */
    private fun configureFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.parseColor("#0B0B0E")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    /**
     * Abilita tutte le capacità richieste per Canvas 2D, WebGL e asset locali
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebSettings(settings: WebSettings) {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true

            // Fondamentale: permette all'HTML di caricare i file JSON, TTF e Shader locali
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true

            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }

    /**
     * Salva un'immagine codificata in Base64 direttamente nella Galleria (MediaStore)
     */
    fun saveBase64Image(base64Data: String, format: String, requestedFilename: String? = null) {
        try {
            val cleanBase64 = if (base64Data.contains(",")) {
                base64Data.substringAfter(",")
            } else {
                base64Data
            }

            val imageBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val ext = if (format.contains("jpeg") || format.contains("jpg")) "jpg"
            else if (format.contains("webp")) "webp" else "png"
            val mimeType = if (ext == "jpg") "image/jpeg" else if (ext == "webp") "image/webp" else "image/png"

            val filename = requestedFilename ?: "picsart_${System.currentTimeMillis()}.$ext"

            var outputStream: OutputStream? = null
            var savedUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (Scoped Storage / MediaStore moderno senza permessi invasivi)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PicsartStudio")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                savedUri = contentResolver.insert(collection, values)

                savedUri?.let { uri ->
                    outputStream = contentResolver.openOutputStream(uri)
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
            } else {
                // Android 9 e versioni precedenti
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val picsartDir = File(picturesDir, "PicsartStudio").apply { mkdirs() }
                val targetFile = File(picsartDir, filename)
                outputStream = FileOutputStream(targetFile)
                savedUri = Uri.fromFile(targetFile)

                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }

            outputStream?.use { it.write(imageBytes) }

            runOnUiThread {
                Toast.makeText(this, "Foto salvata in Galleria (PicsartStudio)!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("PicsartNative", "Errore salvataggio immagine", e)
            runOnUiThread {
                Toast.makeText(this, "Errore durante il salvataggio: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Gestisce il download di immagini da URL Data Scheme
     */
    private fun saveDataUriImage(dataUrl: String, mimeType: String) {
        val format = if (mimeType.contains("jpeg") || mimeType.contains("jpg")) "image/jpeg"
        else if (mimeType.contains("webp")) "image/webp" else "image/png"
        saveBase64Image(dataUrl, format)
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /**
     * Bridge JavaScript accessibile dall'HTML tramite `window.AndroidBridge`
     */
    inner class AndroidBridge(private val activity: Activity) {
        @JavascriptInterface
        fun saveImageToGallery(base64Data: String, format: String, fileName: String) {
            activity.runOnUiThread {
                this@MainActivity.saveBase64Image(base64Data, format, fileName)
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            activity.runOnUiThread {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
