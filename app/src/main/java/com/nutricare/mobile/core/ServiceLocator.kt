package com.nutricare.mobile.core

import android.content.Context
import com.nutricare.mobile.BuildConfigHolder
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Single place holding the app's dependencies. The Supabase client talks to
 * the exact same project the website uses — one database, one auth system.
 */
object ServiceLocator {
    lateinit var supabase: io.github.jan.supabase.SupabaseClient
        private set

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /** Calls the deployed NutriCare AI endpoints (keys stay server-side). */
    lateinit var api: ApiClient
        private set

    fun init(context: Context) {
        supabase = createSupabaseClient(BuildConfigHolder.SUPABASE_URL, BuildConfigHolder.SUPABASE_ANON_KEY) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }

        val ktor = HttpClient(Android) {
            engine {
                connectTimeout = 30_000
                socketTimeout = 60_000
            }
            install(ContentNegotiation) { json(json) }
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 60_000
            }
        }
        api = ApiClient(ktor, supabase)
    }
}
