package com.nutricare.mobile.features.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.AnalyzeFoodResponse
import com.nutricare.mobile.ui.theme.Amber
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Rose
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ScanUiState(
    val description: String = "",
    val photoUri: Uri? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val result: AnalyzeFoodResponse? = null,
)

class ScanViewModel : ViewModel() {
    val state = MutableStateFlow(ScanUiState())

    fun onDescription(v: String) = state.update { it.copy(description = v, error = null) }
    fun onPhoto(v: Uri?) = state.update { it.copy(photoUri = v, error = null) }
    fun dismissResult() = state.update { it.copy(result = null) }

    fun analyze(context: android.content.Context) {
        val s = state.value
        if (s.loading) return
        if (s.description.isBlank() && s.photoUri == null) {
            state.update { it.copy(error = "Describe your meal or add a photo first.") }
            return
        }
        state.update { it.copy(loading = true, error = null, result = null) }
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = com.nutricare.mobile.core.SessionStore.ensureActiveUser()
                    ?: throw Exception("Please sign in first")

                var fileUrl: String? = null
                s.photoUri?.let { uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw Exception("Could not read the photo")
                    val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
                    val name = "${user.id}-food-${System.currentTimeMillis()}.$ext"
                    try {
                        client.storage.from("reports").upload(name, bytes)
                    } catch (storageErr: Exception) {
                        client.auth.refreshCurrentSession()
                        client.storage.from("reports").upload(name, bytes)
                    }
                    fileUrl = client.storage.from("reports").publicUrl(name)
                }

                val response = ServiceLocator.api.analyzeFood(
                    description = s.description.trim().ifBlank { null },
                    fileUrl = fileUrl,
                )
                if (!response.success || response.food == null || !response.is_food) {
                    val errMsg = if (!response.is_food) {
                        response.error ?: "The selected image does not appear to contain edible food or drink. Please choose or capture a photo of a meal or beverage."
                    } else {
                        response.error ?: "Could not analyze this food."
                    }
                    state.update { it.copy(loading = false, error = errMsg) }
                } else {
                    state.update { it.copy(loading = false, result = response) }
                }
            } catch (e: Exception) {
                val rawMsg = e.message ?: "Something went wrong."
                val cleanMsg = when {
                    rawMsg.contains("does not appear to contain", ignoreCase = true) ||
                    rawMsg.contains("not food", ignoreCase = true) ||
                    rawMsg.contains("non-food", ignoreCase = true) ->
                        "The selected image does not appear to contain food (e.g. an animal, person, or object was detected). Please choose or take a photo of an edible meal or beverage."
                    rawMsg.startsWith("Server error 422: ") ->
                        rawMsg.removePrefix("Server error 422: ").trim()
                    rawMsg.startsWith("Server error 400: ") ->
                        rawMsg.removePrefix("Server error 400: ").trim()
                    else -> rawMsg
                }
                state.update { it.copy(loading = false, error = cleanMsg) }
            }
        }
    }
}

@Composable
fun ScanScreen(vm: ScanViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.onPhoto(state.photoUri) // uri pre-assigned below
    }
    var pendingCameraUri by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Uri?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        vm.onPhoto(uri)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "food-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        pendingCameraUri = uri
        vm.onPhoto(uri)
        cameraLauncher.launch(uri)
    }

    val previewBitmap: ImageBitmap? = androidx.compose.runtime.remember(state.photoUri) {
        val uri = state.photoUri ?: return@remember null
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("Food Scanner", style = MaterialTheme.typography.headlineMedium)
            Text("Snap or describe your meal — get a verdict in seconds.", style = MaterialTheme.typography.bodySmall)
        }

        // Photo capture card
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { launchCamera() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Take photo")
            }
            OutlinedButton(
                onClick = { pickLauncher.launch("image/*") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("From gallery")
            }
        }

        // Live Image Preview Thumbnail
        if (previewBitmap != null) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Selected Food Photo", style = MaterialTheme.typography.labelMedium, color = EmeraldDark)
                        }
                        Text(
                            "Remove ✕",
                            color = Rose,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable { vm.onPhoto(null) }
                                .padding(4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.Image(
                        bitmap = previewBitmap,
                        contentDescription = "Selected photo",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        } else if (state.photoUri != null) {
            Text("📷 Photo attached — AI will identify the food from it.", style = MaterialTheme.typography.bodySmall, color = EmeraldDark)
        }

        OutlinedTextField(
            value = state.description,
            onValueChange = { vm.onDescription(it) },
            label = { Text("Or describe your meal…") },
            placeholder = { Text("e.g. Macher Jhol with rice, or just 'banana'") },
            shape = RoundedCornerShape(16.dp),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { vm.analyze(context) },
            enabled = !state.loading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(10.dp))
                Text("Analyzing meal…")
            } else {
                Text("Analyze my meal")
            }
        }

        state.error?.let { err ->
            val isNonFood = err.contains("not food", ignoreCase = true) ||
                            err.contains("non-food", ignoreCase = true) ||
                            err.contains("not appear to contain", ignoreCase = true) ||
                            err.contains("animal", ignoreCase = true) ||
                            err.contains("cat", ignoreCase = true) ||
                            err.contains("person", ignoreCase = true) ||
                            err.contains("meal or beverage", ignoreCase = true)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNonFood) Color(0xFFFFFBEB) else Color(0xFFFFF1F2)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isNonFood) Color(0xFFFDE68A) else Color(0xFFFECDD3)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isNonFood) Icons.Filled.Error else Icons.Filled.Cancel,
                            contentDescription = null,
                            tint = if (isNonFood) Amber else Rose,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isNonFood) "Please Select a Food Picture" else "Analysis Notice",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isNonFood) Color(0xFF92400E) else Rose
                        )
                    }
                    Text(
                        err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isNonFood) Color(0xFF78350F) else Rose
                    )
                    if (isNonFood) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { launchCamera() },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Retake Photo", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { pickLauncher.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Choose Food Image", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Result
        state.result?.let { r ->
            val food = r.food!!
            val evaluation = r.evaluation
            val score = evaluation?.score ?: "Processed"
            val (icon, tint) = when (score) {
                "Safe" -> Icons.Filled.CheckCircle to EmeraldDark
                "Caution" -> Icons.Filled.Error to Amber
                "Avoid" -> Icons.Filled.Cancel to Rose
                else -> Icons.Filled.CheckCircle to EmeraldDark
            }

            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            Column {
                                Text(food.name, style = MaterialTheme.typography.titleLarge)
                                Text("${food.serving_size} · ${food.calories.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
                            Text(score, style = MaterialTheme.typography.labelSmall, color = tint)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Macro("Protein", "${food.protein_g.toInt()}g", Modifier.weight(1f))
                        Macro("Carbs", "${food.carbs_g.toInt()}g", Modifier.weight(1f))
                        Macro("Fat", "${food.fat_g.toInt()}g", Modifier.weight(1f))
                        Macro("Sugar", "${food.sugar_g.toInt()}g", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val flags = evaluation?.nutrientFlags?.map { it.nutrient }?.toSet() ?: emptySet()
                        Macro("Sodium", "${food.sodium_mg.toInt()}mg", Modifier.weight(1f), flagged = "Sodium" in flags)
                        Macro("Potassium", "${food.potassium_mg.toInt()}mg", Modifier.weight(1f), flagged = "Potassium" in flags)
                        Macro("Phosphorus", "${food.phosphorus_mg.toInt()}mg", Modifier.weight(1f), flagged = "Phosphorus" in flags)
                        Box(Modifier.weight(1f))
                    }
                }
            }

            if (evaluation?.reasoning?.isNotEmpty() == true) {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CLINICAL ASSESSMENT", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                        evaluation.reasoning.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (evaluation?.alternatives?.isNotEmpty() == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Try instead:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterVertically))
                    evaluation.alternatives.take(3).forEach { alt ->
                        Box(
                            Modifier
                                .background(Color(0xFFECFDF5), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text(alt, style = MaterialTheme.typography.labelSmall, color = EmeraldDark) }
                    }
                }
            }

            evaluation?.disclaimer?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Macro(label: String, value: String, modifier: Modifier = Modifier, flagged: Boolean = false) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (flagged) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = if (flagged) Amber else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
