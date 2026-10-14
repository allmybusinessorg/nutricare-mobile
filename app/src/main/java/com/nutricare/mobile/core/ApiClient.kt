package com.nutricare.mobile.core

import com.nutricare.mobile.BuildConfigHolder
import com.nutricare.mobile.data.AnalyzeFoodRequest
import com.nutricare.mobile.data.AnalyzeFoodResponse
import com.nutricare.mobile.data.ChatSimpleRequest
import com.nutricare.mobile.data.ChatSimpleResponse
import com.nutricare.mobile.data.OcrRequest
import com.nutricare.mobile.data.OcrResponse
import com.nutricare.mobile.data.OnboardingRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Talks to the deployed NutriCare backend (Vercel) for everything that needs
 * AI/secret keys: food analysis, report OCR, health chat, and onboarding.
 * Authenticated with the user's Supabase access token — the same identity as the website.
 */
class ApiClient(
    private val http: HttpClient,
    private val supabase: SupabaseClient,
) {
    private val baseUrl = BuildConfigHolder.API_BASE_URL

    private fun authHeader(): String? {
        return try {
            supabase.auth.currentSessionOrNull()?.accessToken
                ?: supabase.auth.currentAccessTokenOrNull()
        } catch (_: Exception) {
            supabase.auth.currentAccessTokenOrNull()
        }
    }

    private suspend fun <T> post(path: String, body: Any, parse: (String) -> T): T {
        val token = authHeader()
        val response = http.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            if (!token.isNullOrBlank()) {
                header("Authorization", "Bearer $token")
                header("Cookie", "sb-lanbwwevtjnzvxfunupc-auth-token=$token; sb-access-token=$token")
            }
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

    suspend fun saveOnboarding(
        userId: String,
        name: String?,
        age: Int?,
        gender: String?,
        height: Double?,
        weight: Double?,
        goal: String?,
        selectedDiseases: List<String>
    ): Boolean =
        post("/api/onboarding", OnboardingRequest(
            userId = userId,
            name = name,
            age = age,
            gender = gender,
            height = height,
            weight = weight,
            goal = goal,
            selectedDiseases = selectedDiseases
        )) {
            true
        }
}

class ApiException(message: String) : Exception(message)
