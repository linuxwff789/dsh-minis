package com.openminis.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.openminis.app.ui.theme.MinisTheme

/**
 * WebView client for the DSH web UI (http://127.0.0.1:3080).
 */
class DshWebActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinisTheme {
                var reloadKey by remember { mutableStateOf(0) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    view?.loadUrl(request?.url?.toString() ?: return false)
                                    return true
                                }
                                override fun onPageStarted(
                                    view: WebView?, url: String?, favicon: Bitmap?
                                ) {}
                            }
                            loadUrl("http://127.0.0.1:3080/")
                        }
                    },
                    update = { webView ->
                        // Retry load when server becomes reachable.
                        if (reloadKey > 0) webView.loadUrl("http://127.0.0.1:3080/")
                    }
                )
                // Floating reload button.
                Button(
                    onClick = { reloadKey++ },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text("Reload")
                }
            }
        }
    }
}
