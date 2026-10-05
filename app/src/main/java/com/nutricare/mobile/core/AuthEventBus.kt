package com.nutricare.mobile.core

import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Tiny event bus: MainActivity feeds OAuth deep links here via the Supabase
 * SDK's handleDeeplinks(); the nav root listens and moves to Home when a
 * session arrives.
 */
object AuthEventBus {
    val sessionArrived = MutableSharedFlow<UserSession>(extraBufferCapacity = 1)
}
