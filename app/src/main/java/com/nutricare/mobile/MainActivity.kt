package com.nutricare.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nutricare.mobile.core.AuthEventBus
import com.nutricare.mobile.core.GoogleOAuth
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.ui.nav.AppRoot
import com.nutricare.mobile.ui.theme.NutriTheme
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        setContent {
            NutriTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        com.nutricare.mobile.core.AppTelemetry.onAppForeground(this)
    }

    override fun onStop() {
        super.onStop()
        com.nutricare.mobile.core.AppTelemetry.onAppBackground(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    /**
     * Google OAuth returns here via nutricare://auth/callback. The callback
     * carries either PKCE (?code=) or implicit (#access_token=) credentials;
     * GoogleOAuth normalizes both into a session we import into the SDK.
     */
    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "nutricare" || data.host != "auth") return
        intent.data = null // consume — don't reprocess on recreation

        activityScope.launch {
            try {
                val session = GoogleOAuth.handleCallback(data)
                ServiceLocator.supabase.auth.importSession(session)
                AuthEventBus.sessionArrived.tryEmit(session)
            } catch (e: Exception) {
                AuthEventBus.oauthError.tryEmit(e.message ?: "Google sign-in failed. Please try again.")
            }
        }
    }
}
