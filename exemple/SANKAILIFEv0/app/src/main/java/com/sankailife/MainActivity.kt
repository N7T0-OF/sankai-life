package com.sankailife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sankailife.ui.navigation.SankaiNavGraph
import com.sankailife.ui.theme.SankaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SankaiApplication
        setContent {
            val themeMode by app.preferences.themeMode.collectAsState(initial = "dark")
            SankaiTheme(darkTheme = when(themeMode) {
                "light" -> false
                "auto"  -> isSystemDarkTheme()
                else    -> true
            }) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SankaiNavGraph()
                }
            }
        }
    }

    private fun isSystemDarkTheme(): Boolean {
        val uiMode = resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
