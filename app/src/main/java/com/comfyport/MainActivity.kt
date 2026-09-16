package com.comfyport

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.comfyport.theme.ComfyPortTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.comfyport.theme.AppHighlightColor

class MainActivity : ComponentActivity() {
    private val viewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[com.comfyport.ui.MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.comfyport.network.AppLogger.init(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsState()
            val highlight = remember(settings.highlightColor) {
                AppHighlightColor.fromId(settings.highlightColor)
            }
            ComfyPortTheme(highlightColor = highlight) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isAppInForeground = true
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        isAppInForeground = false
        viewModel.onAppBackgrounded()
    }

    companion object {
        var isAppInForeground = false
    }
}
