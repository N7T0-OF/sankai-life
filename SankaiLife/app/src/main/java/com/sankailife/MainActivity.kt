package com.sankailife

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sankailife.core.ads.AdsManager
import com.sankailife.ui.navigation.SankaiNavGraph
import com.sankailife.ui.theme.SankaiTheme

class MainActivity : ComponentActivity() {

    private val demandeNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* refus = pas de mémos, l'app marche quand même */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        demanderPermissionNotifications()

        val app = application as SankaiApplication

        setContent {
            val themeMode by app.preferences.themeMode.collectAsState(initial = "dark")
            SankaiTheme(
                darkTheme = when (themeMode) {
                    "light" -> false
                    "auto" -> isSystemDarkTheme()
                    else -> true
                }
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SankaiNavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Garde une pub prête d'avance pour que le bouton soit instantané.
        AdsManager.preload(this)
    }

    /**
     * Android 13+ demande la permission d'envoyer des notifications.
     * Un refus ne bloque rien : seuls les rappels mémo sont perdus.
     */
    private fun demanderPermissionNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            demandeNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isSystemDarkTheme(): Boolean {
        val uiMode = resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
