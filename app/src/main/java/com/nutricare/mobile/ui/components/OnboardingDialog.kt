package com.nutricare.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.Profile
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Slate
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

val CLINICAL_CONDITIONS = listOf(
    "Chronic Kidney Disease (CKD)",
    "Type 2 Diabetes",
    "Hypertension",
    "Fatty Liver",
    "None of the above / General Health"
)

val HEALTH_GOALS = listOf(
    "Manage Kidney Health",
    "Blood Sugar Control",
    "Heart Health & BP",
    "Healthy Weight Loss",
    "Overall Longevity & Wellness"
)

val GENDERS = listOf("Male", "Female", "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingDialog(
    initialName: String = "",
    initialProfile: Profile? = null,
    initialConditions: List<String> = emptyList(),
    isEditing: Boolean = false,
    onDismiss: () -> Unit,
    onSaved: (Profile, List<String>) -> Unit,
) {
    var step by remember { mutableIntStateOf(if (isEditing) 2 else 1) }

    val selectedConditions = remember {
        mutableStateListOf<String>().apply {
            addAll(initialConditions.ifEmpty { emptyList() })
        }
    }

    var name by remember { mutableStateOf(initialName.ifBlank { initialProfile?.name ?: "" }) }
    var ageText by remember { mutableStateOf(initialProfile?.age?.toString() ?: "") }
    var selectedGender by remember { mutableStateOf(initialProfile?.gender ?: "Male") }
    var heightText by remember { mutableStateOf(initialProfile?.height?.let { "$it" } ?: "") }
    var weightText by remember { mutableStateOf(initialProfile?.weight?.let { "$it" } ?: "") }
    var selectedGoal by remember { mutableStateOf(initialProfile?.goal ?: HEALTH_GOALS.first()) }

    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .padding(vertical = 24.dp),
        confirmButton = {},
        dismissButton = {},
        containerColor = Color.White,
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEditing) "Edit Health Profile" else "Health Onboarding",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (step == 1) "Step 1 of 2: Clinical focus" else "Step 2 of 2: Body metrics",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate
                        )
                    }

                    if (isEditing) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Slate)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Progress Bar
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(EmeraldDark)
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (step >= 2) EmeraldDark else Color(0xFFE2E8F0))
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (step == 1) {
                    // --- STEP 1: CONDITIONS ---
                    Text(
                        "Which conditions are you managing?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        "Our clinical AI personalizes daily nutrition rules based on these.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate
                    )

                    Spacer(Modifier.height(14.dp))

                    CLINICAL_CONDITIONS.forEach { condition ->
                        val isSelected = selectedConditions.contains(condition)
                        val isNone = condition.startsWith("None")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFFECFDF5) else Mist)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) EmeraldDark else Color(0xFFE2E8F0),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    if (isNone) {
                                        selectedConditions.clear()
                                        selectedConditions.add(condition)
                                    } else {
                                        selectedConditions.removeAll { it.startsWith("None") }
                                        if (isSelected) {
                                            selectedConditions.remove(condition)
                                        } else {
                                            selectedConditions.add(condition)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    condition,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) EmeraldDark else Color(0xFF334155)
                                )
                                if (isSelected) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (selectedConditions.isEmpty()) {
                                selectedConditions.add("None of the above / General Health")
                            }
                            step = 2
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Continue to Metrics →", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // --- STEP 2: BODY METRICS & GOALS ---
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. John Doe") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ageText,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 3) ageText = it },
                            label = { Text("Age (yrs)") },
                            placeholder = { Text("35") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text("Height (cm)") },
                            placeholder = { Text("175") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("Weight (kg)") },
                            placeholder = { Text("70") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Gender selection
                    Text("Biological Gender", style = MaterialTheme.typography.labelSmall, color = Slate)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GENDERS.forEach { g ->
                            val sel = selectedGender.equals(g, ignoreCase = true)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Color(0xFFECFDF5) else Mist)
                                    .border(1.dp, if (sel) EmeraldDark else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                    .clickable { selectedGender = g }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    g,
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) EmeraldDark else Color(0xFF475569)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Primary Health Goal
                    Text("Primary Health Goal", style = MaterialTheme.typography.labelSmall, color = Slate)
                    Spacer(Modifier.height(6.dp))
                    HEALTH_GOALS.forEach { goalOption ->
                        val sel = selectedGoal == goalOption
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) Color(0xFFECFDF5) else Mist)
                                .border(1.dp, if (sel) EmeraldDark else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                                .clickable { selectedGoal = goalOption }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    goalOption,
                                    fontSize = 13.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (sel) EmeraldDark else Color(0xFF334155)
                                )
                                if (sel) {
                                    Icon(Icons.Filled.Check, null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    errorMessage?.let { err ->
                        Spacer(Modifier.height(8.dp))
                        Text(err, color = Color(0xFFE11D48), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!isEditing) {
                            OutlinedButton(
                                onClick = { step = 1 },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text("← Back")
                            }
                        }

                        Button(
                            onClick = {
                                val parsedAge = ageText.trim().toIntOrNull()
                                val parsedHeight = heightText.trim().toDoubleOrNull()
                                val parsedWeight = weightText.trim().toDoubleOrNull()

                                if (parsedAge == null || parsedAge <= 0) {
                                    errorMessage = "Please enter a valid age."
                                    return@Button
                                }
                                errorMessage = null
                                saving = true

                                scope.launch {
                                    try {
                                        val client = ServiceLocator.supabase
                                        val user = client.auth.currentUserOrNull()
                                        if (user != null) {
                                            // 1. Direct Supabase save (instant & reliable)
                                            val profileMap = buildMap {
                                                put("id", user.id)
                                                if (name.isNotBlank()) put("name", name.trim())
                                                put("age", parsedAge)
                                                put("gender", selectedGender)
                                                parsedHeight?.let { put("height", it) }
                                                parsedWeight?.let { put("weight", it) }
                                                put("goal", selectedGoal)
                                            }
                                            runCatching {
                                                client.from("profiles").upsert(profileMap)
                                            }

                                            // 2. Also call API endpoint for email & sync
                                            runCatching {
                                                ServiceLocator.api.saveOnboarding(
                                                    userId = user.id,
                                                    name = name.trim().ifBlank { null },
                                                    age = parsedAge,
                                                    gender = selectedGender,
                                                    height = parsedHeight,
                                                    weight = parsedWeight,
                                                    goal = selectedGoal,
                                                    selectedDiseases = selectedConditions.toList()
                                                )
                                            }

                                            val updatedProfile = Profile(
                                                id = user.id,
                                                name = name.trim().ifBlank { initialProfile?.name },
                                                age = parsedAge,
                                                gender = selectedGender,
                                                height = parsedHeight,
                                                weight = parsedWeight,
                                                goal = selectedGoal,
                                                pro_status = initialProfile?.pro_status,
                                                pro_expires_at = initialProfile?.pro_expires_at,
                                                referral_code = initialProfile?.referral_code
                                            )
                                            onSaved(updatedProfile, selectedConditions.toList())
                                        }
                                        onDismiss()
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Failed to save profile. Please try again."
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(if (isEditing) 1f else 1.3f)
                                .height(50.dp)
                        ) {
                            if (saving) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text(if (isEditing) "Save Changes" else "Finish Setup ✓", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    )
}
