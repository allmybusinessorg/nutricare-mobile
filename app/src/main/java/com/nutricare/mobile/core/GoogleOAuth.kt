package com.nutricare.mobile.core

import android.net.Uri
import com.nutricare.mobile.BuildConfigHolder
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google OAuth for the native app, done explicitly:
 *
 *  1. start()    -> builds the Supabase authorize URL with a PKCE challenge
 *                   and opens it in the browser.
 *  2. MainActivity receives nutricare://auth/callback and calls handleCallback().
 *  3. handleCallback() supports BOTH response shapes:
 *       - PKCE:   ?code=...  -> exchanged with the saved verifier
 *       - implicit: #access_token=... -> user fetched via /auth/v1/user
 *  4. Returns a UserSession which MainActivity imports into the SDK.
 */
object GoogleOAuth {

    private const val REDIRECT_URI = "nutricare://auth/callback"
    private const val AUTHORIZE_PATH = "/auth/v1/authorize"
    private const val TOKEN_PATH = "/auth/v1/token?grant_type=pkce"
    private const val USER_PATH = "/auth/v1/user"

    private var codeVerifier: String? = null

    fun start(): Uri {
        val verifier = randomVerifier()
        codeVerifier = verifier
        val challenge = base64UrlSha256(verifier)
        val url = "${BuildConfigHolder.SUPABASE_URL}$AUTHORIZE_PATH" +
            "?provider=google" +
            "&redirect_to=${Uri.encode(REDIRECT_URI)}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=s256"
        return Uri.parse(url)
    }

    /**
     * Parses the redirect URI and returns a full session. Throws with a
     * human-readable message on anything unexpected.
     */
    suspend fun handleCallback(uri: Uri?): UserSession {
        if (uri == null) throw IllegalStateException("No sign-in response received.")
        if (uri.getQueryParameter("error") != null) {
            throw IllegalStateException(uri.getQueryParameter("error_description") ?: "Sign-in was cancelled.")
        }

        // Implicit flow: tokens arrive in the URL fragment (#access_token=...)
        val fragment = uri.fragment
        if (!fragment.isNullOrBlank()) {
            val params = fragment.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to Uri.decode(it.substring(i + 1)) else null
            }.toMap()
            val accessToken = params["access_token"]
            val refreshToken = params["refresh_token"]
            val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
            if (accessToken != null) {
                val userJson = fetchUser(accessToken)
                val sessionJson = JSONObject().apply {
                    put("access_token", accessToken)
                    put("refresh_token", refreshToken ?: "")
                    put("expires_in", expiresIn)
                    put("token_type", params["token_type"] ?: "bearer")
                    put("user", JSONObject(userJson))
                }
                return decodeSession(sessionJson.toString())
            }
        }

        // PKCE flow: an auth code arrives as ?code=...
        val code = uri.getQueryParameter("code")
        if (code != null) {
            val verifier = codeVerifier
                ?: throw IllegalStateException("Sign-in session expired — please try again.")
            return exchangePkceCode(code, verifier)
        }

        throw IllegalStateException("Sign-in response was missing its credentials. Please try again.")
    }

    /**
     * Sign in with a Google ID token (Credential Manager native picker path):
     * exchanges the token at Supabase's grant_type=id_token endpoint, which
     * returns the full session JSON.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): UserSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("provider", "google")
                put("id_token", idToken)
            }.toString()
            val response = httpPost(
                "${BuildConfigHolder.SUPABASE_URL}/auth/v1/token?grant_type=id_token",
                body,
                bearer = null
            )
            decodeSession(response)
        }

    private suspend fun exchangePkceCode(code: String, verifier: String): UserSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("auth_code", code)
                put("code_verifier", verifier)
            }.toString()
            val response = httpPost("${BuildConfigHolder.SUPABASE_URL}$TOKEN_PATH", body, bearer = null)
            decodeSession(response)
        }

    private suspend fun fetchUser(accessToken: String): String =
        withContext(Dispatchers.IO) {
            httpGet("${BuildConfigHolder.SUPABASE_URL}$USER_PATH", accessToken)
        }

    private fun decodeSession(json: String): UserSession =
        ServiceLocator.json.decodeFromString(
            io.github.jan.supabase.auth.user.UserSession.serializer(), json
        )

    // ---- tiny HTTP helpers (no extra dependencies) ----

    private fun httpPost(url: String, body: String, bearer: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", BuildConfigHolder.SUPABASE_ANON_KEY)
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            connectTimeout = 15000
            readTimeout = 15000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        return readResponse(conn)
    }

    private fun httpGet(url: String, bearer: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("apikey", BuildConfigHolder.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $bearer")
            connectTimeout = 15000
            readTimeout = 15000
        }
        return readResponse(conn)
    }

    private fun readResponse(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (conn.responseCode !in 200..299) {
            android.util.Log.e("NutriGoogle", "Token exchange failed HTTP ${conn.responseCode}: $text")
            throw IllegalStateException("Google sign-in failed (HTTP ${conn.responseCode}): ${text.take(300)}. Please try again.")
        }
        return text
    }

    // ---- PKCE helpers ----

    private fun randomVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun base64UrlSha256(input: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(input.toByteArray()))

    private fun base64Url(bytes: ByteArray): String =
        android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
}
