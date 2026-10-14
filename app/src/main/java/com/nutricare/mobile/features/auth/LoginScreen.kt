package com.nutricare.mobile.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.nutricare.mobile.BuildConfigHolder
import com.nutricare.mobile.core.GoogleOAuth
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    val email = MutableStateFlow("")
    val password = MutableStateFlow("")
    val isSignUp = MutableStateFlow(false)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val info = MutableStateFlow<String?>(null)
    val loggedIn = MutableStateFlow(false)

    fun submit() {
        val mail = email.value.trim()
        val pass = password.value
        if (!mail.contains('@') || pass.length < 6) {
            error.value = "Enter a valid email and a password of at least 6 characters."
            return
        }
        loading.value = true
        error.value = null
        info.value = null
        viewModelScope.launch {
            try {
                if (isSignUp.value) {
                    ServiceLocator.supabase.auth.signUpWith(Email) {
                        this.email = mail
                        this.password = pass
                    }
                    info.value = "Account created! If email confirmation is on, check your inbox, then sign in."
                    isSignUp.value = false
                } else {
                    ServiceLocator.supabase.auth.signInWith(Email) {
                        this.email = mail
                        this.password = pass
                    }
                    loggedIn.value = true
                }
            } catch (e: RestException) {
                error.value = e.message ?: "Authentication failed."
            } catch (e: Exception) {
                error.value = "Could not reach the server. Check your connection."
            } finally {
                loading.value = false
            }
        }
    }

    /**
     * Google sign-in, native-first:
     *  1. Credential Manager bottom-sheet account picker (the standard
     *     Android experience) -> Google ID token -> Supabase IDToken grant.
     *  2. If the picker isn't available on this device (e.g. no Google Play
     *     services or the OAuth client isn't registered yet), fall back to
     *     the browser PKCE flow.
     */
    fun signInWithGoogle(context: Context) {
        error.value = null
        loading.value = true
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(BuildConfigHolder.GOOGLE_WEB_CLIENT_ID)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(context, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleId = GoogleIdTokenCredential.createFrom(credential.data)
                    val session = GoogleOAuth.signInWithGoogleIdToken(googleId.idToken)
                    ServiceLocator.supabase.auth.importSession(session)
                    loggedIn.value = true
                } else {
                    error.value = "Unexpected Google response. Please try again."
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                // User closed the account picker — not an error.
            } catch (e: Exception) {
                android.util.Log.e("NutriGoogle", "Native picker failed: ${e.javaClass.simpleName}: ${e.message}", e)
                // Native picker unavailable (e.g. Android OAuth client not yet
                // registered in Google Cloud) -> browser flow fallback.
                try {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW, GoogleOAuth.start()
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    error.value = "Google sign-in is unavailable on this device."
                }
            } finally {
                loading.value = false
            }
        }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: AuthViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val email by vm.email.collectAsState()
    val password by vm.password.collectAsState()
    val isSignUp by vm.isSignUp.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val info by vm.info.collectAsState()
    val loggedIn by vm.loggedIn.collectAsState()

    LaunchedEffect(loggedIn) {
        if (loggedIn) onLoggedIn()
    }

    LaunchedEffect(Unit) {
        com.nutricare.mobile.core.AuthEventBus.oauthError.collect { msg ->
            vm.error.value = msg
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color(0x2210B981), Color.Transparent)))
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Box(
                Modifier
                    .size(64.dp)
                    .background(Brush.linearGradient(listOf(Emerald, Teal)), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("NutriCare", style = MaterialTheme.typography.headlineMedium)
            Text("Clinical AI, in your pocket", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(40.dp))

            Text(
                if (isSignUp) "Create your account" else "Welcome back",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (isSignUp) "Start your journey to smarter health." else "Sign in to continue your health journey.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 24.dp),
            )

            OutlinedTextField(
                value = email,
                onValueChange = { vm.email.value = it },
                label = { Text("Email address") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { vm.password.value = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            info?.let {
                Text(
                    it,
                    color = EmeraldDark,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            // Google sign-in (uses the same Google OAuth the website uses)
            androidx.compose.material3.OutlinedButton(
                onClick = { vm.signInWithGoogle(context) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(
                    "G",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF4285F4),
                )
                Spacer(Modifier.size(10.dp))
                Text("Continue with Google", color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))
            Text("— or with email —", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { vm.submit() },
                enabled = !loading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Text(if (isSignUp) "Create Account" else "Sign In")
                    Spacer(Modifier.size(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }

            TextButton(onClick = { vm.isSignUp.value = !isSignUp; vm.error.value = null; vm.info.value = null }) {
                Text(
                    if (isSignUp) "Already have an account? Sign in" else "New to NutriCare? Create one",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
