package com.nutricare.mobile.core

import com.nutricare.mobile.BuildConfigHolder
import com.nutricare.mobile.data.AnalyzeFoodRequest
import com.nutricare.mobile.data.AnalyzeFoodResponse
import com.nutricare.mobile.data.ChatSimpleRequest
import com.nutricare.mobile.data.ChatSimpleResponse
import com.nutricare.mobile.data.OcrRequest
import com.nutricare.mobile.data.OcrResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Talks to the deployed NutriCare backend (Vercel) for everything that needs
 * AI/secret keys: food analysis, report OCR, health chat. Authenticated with
 * the user's Supabase access token — the same identity as the website.
 */
class ApiClient(
    private val http: HttpClient,
    private val supabase: SupabaseClient,
) {
    private val baseUrl = BuildConfigHolder.API_BASE_URL

    private suspend fun authHeader(): String? =
        supabase.auth.currentAccessTokenOrNull()

    private suspend fun <T> post(path: String, body: Any, parse: (String) -> T): T {
        val token = authHeader()
        val response = http.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            if (token != null) bearerAuth(token)
            setBody(body)
        }
        val text = response.bodyAsText()
        if (response.status.value >= 400) {
            throw ApiException("Server error ${response.status.value}: ${text.take(200)}")
        }
        return parse(text)
    }

    suspend fun analyzeFood(description: String?, fileUrl: String?): AnalyzeFoodResponse =
        post("/api/analyze-food", AnalyzeFoodRequest(foodDescription = description, fileUrl = fileUrl)) {
            ServiceLocator.json.decodeFromString(it)
        }

    suspend fun extractReport(fileUrl: String, reportType: String? = null): OcrResponse =
        post("/api/ocr", OcrRequest(fileUrl = fileUrl, reportType = reportType)) { ServiceLocator.json.decodeFromString(it) }

    suspend fun chat(message: String): ChatSimpleResponse =
        post("/api/chat-simple", ChatSimpleRequest(message)) { ServiceLocator.json.decodeFromString(it) }
}

class ApiException(message: String) : Exception(message)
