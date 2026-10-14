package com.nutricare.mobile.core

import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Tiny event bus: MainActivity feeds OAuth deep links here after importing
 * the session; the nav root listens and moves to Home. OAuth failures are
 * surfaced to the login screen so the user always gets feedback.
 */
object AuthEventBus {
    val sessionArrived = MutableSharedFlow<UserSession>(extraBufferCapacity = 1)
    val oauthError = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
