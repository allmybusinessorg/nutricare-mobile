package com.nutricare.mobile.core

import android.content.Context
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Persists the Supabase session in SharedPreferences so the user stays
 * logged in across app restarts. The SDK keeps sessions in memory only.
 */
object SessionStore {
    private const val PREFS = "nutricare_session"
    private const val KEY = "session_json"
    private var appContext: Context? = null

    fun attach(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // Restore a saved session (if any) right after startup.
        val saved = prefs.getString(KEY, null)
        if (saved != null) {
            runCatching {
                val session = ServiceLocator.json.decodeFromString(UserSession.serializer(), saved)
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        ServiceLocator.supabase.auth.importSession(session)
                    }
                }
            }
        }

        // Persist every authenticated session from now on; clear on sign-out.
        ServiceLocator.supabase.auth.sessionStatus
            .onEach { status ->
                when (status) {
                    is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> {
                        saveSession(status.session)
                    }
                    is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated -> {
                        clearSession()
                    }
                    else -> Unit
                }
            }
            .launchIn(CoroutineScope(Dispatchers.IO))
    }

    fun saveSession(session: UserSession) {
        val ctx = appContext ?: return
        runCatching {
            val jsonStr = ServiceLocator.json.encodeToString(UserSession.serializer(), session)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, jsonStr)
                .apply()
        }
    }

    fun clearSession() {
        val ctx = appContext ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY)
                .apply()
        }
    }

    fun getSavedAccessToken(): String? {
        val ctx = appContext ?: return null
        return runCatching {
            val jsonStr = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
            val session = ServiceLocator.json.decodeFromString(UserSession.serializer(), jsonStr)
            session.accessToken
        }.getOrNull()
    }

    fun getSavedRefreshToken(): String? {
        val ctx = appContext ?: return null
        return runCatching {
            val jsonStr = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
            val session = ServiceLocator.json.decodeFromString(UserSession.serializer(), jsonStr)
            session.refreshToken
        }.getOrNull()
    }
}
