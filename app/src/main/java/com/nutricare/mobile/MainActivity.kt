package com.nutricare.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nutricare.mobile.core.AuthEventBus
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.ui.nav.AppRoot
import com.nutricare.mobile.ui.theme.NutriTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onNewIntent(intent)
        setContent {
            NutriTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Google OAuth returns here via nutricare://auth/callback#access_token=...
        // The SDK parses the session and imports it; we surface it to the UI.
        runCatching {
            ServiceLocator.supabase.handleDeeplinks(intent) { session ->
                AuthEventBus.sessionArrived.tryEmit(session)
            }
        }
    }
}
