package com.nutricare.mobile.data

import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String? = null,
    val name: String? = null,
    val age: Int? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val gender: String? = null,
    val goal: String? = null,
    val pro_status: Boolean? = null,
    val pro_expires_at: String? = null,
    val referral_code: String? = null,
)

@Serializable
data class FamilyMember(
    val id: String? = null,
    val user_id: String? = null,
    val name: String = "",
    val relationship: String = "",
    val age: Int? = null,
    val gender: String? = null,
    val created_at: String? = null,
)

@Serializable
data class UserDiseaseRow(
    val diseases: DiseaseRef? = null,
)

@Serializable
data class DiseaseRef(
    val name: String? = null,
)

@Serializable
data class BloodReport(
    val id: String? = null,
    val report_date: String? = null,
    val egfr: Double? = null,
    val creatinine: Double? = null,
    val potassium: Double? = null,
    val sodium: Double? = null,
    val glucose: Double? = null,
    val hemoglobin: Double? = null,
    val albumin: Double? = null,
    val phosphorus: Double? = null,
    val blood_pressure: String? = null,
    val file_url: String? = null,
)

@Serializable
data class FoodUploadRow(
    val id: String? = null,
    val safety_score: String? = null,
    val ai_analysis_json: StoredAnalysis? = null,
    val created_at: String? = null,
)

@Serializable
data class StoredAnalysis(
    val food: FoodNutrition? = null,
    val evaluation: Evaluation? = null,
)

@Serializable
data class FoodNutrition(
    val name: String = "",
    val calories: Double = 0.0,
    val protein_g: Double = 0.0,
    val carbs_g: Double = 0.0,
    val fat_g: Double = 0.0,
    val fiber_g: Double? = null,
    val sugar_g: Double = 0.0,
    val sodium_mg: Double = 0.0,
    val potassium_mg: Double = 0.0,
    val phosphorus_mg: Double = 0.0,
    val serving_size: String = "1 serving",
)

@Serializable
data class Evaluation(
    val score: String? = null,
    val reasoning: List<String> = emptyList(),
    val alternatives: List<String> = emptyList(),
    val nutrientFlags: List<NutrientFlag> = emptyList(),
    val disclaimer: String? = null,
)

@Serializable
data class NutrientFlag(
    val nutrient: String = "",
    val value: Double = 0.0,
    val limit: Double = 0.0,
    val status: String = "",
)

// ---- API request/response shapes for the deployed AI endpoints ----

@Serializable
data class Medication(
    val id: String? = null,
    val name: String = "",
    val dosage: String? = null,
    val time_to_take: String? = null,
)

@Serializable
data class AnalyzeFoodRequest(
    val foodDescription: String? = null,
    val fileUrl: String? = null,
)

@Serializable
data class AnalyzeFoodResponse(
    val success: Boolean = false,
    val food: FoodNutrition? = null,
    val evaluation: Evaluation? = null,
    val error: String? = null,
)

@Serializable
data class OcrRequest(
    val fileUrl: String,
    val reportType: String? = null,
)

@Serializable
data class OcrResponse(
    val success: Boolean = false,
    val data: OcrData? = null,
    val flaggedValues: FlaggedValues? = null,
    val error: String? = null,
)

@Serializable
data class OcrData(
    val report_date: String? = null,
    val creatinine: Double? = null,
    val egfr: Double? = null,
    val potassium: Double? = null,
    val sodium: Double? = null,
    val glucose: Double? = null,
    val hemoglobin: Double? = null,
    val albumin: Double? = null,
    val phosphorus: Double? = null,
    val blood_pressure: String? = null,
    val audioSummary: String? = null,
)

@Serializable
data class FlaggedValues(
    val fields: List<String> = emptyList(),
    val message: String? = null,
)

@Serializable
data class ChatSimpleRequest(
    val message: String,
)

@Serializable
data class ChatSimpleResponse(
    val reply: String? = null,
    val provider: String? = null,
    val error: String? = null,
)
