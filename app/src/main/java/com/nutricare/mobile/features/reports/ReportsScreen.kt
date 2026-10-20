package com.nutricare.mobile.features.reports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.BloodReport
import com.nutricare.mobile.data.OcrData
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.Rose
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReportTypeOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
)

val REPORT_TYPE_OPTIONS = listOf(
    ReportTypeOption(
        title = "General Blood Test",
        description = "Comprehensive Health & Biomarker Panel",
        icon = Icons.Filled.MonitorHeart
    ),
    ReportTypeOption(
        title = "Complete Blood Count (CBC)",
        description = "RBC, WBC, Hemoglobin & Platelets",
        icon = Icons.Filled.WaterDrop
    ),
    ReportTypeOption(
        title = "Lipid Profile",
        description = "Cholesterol, Triglycerides, HDL/LDL",
        icon = Icons.Filled.Favorite
    ),
    ReportTypeOption(
        title = "Kidney Function Test (KFT)",
        description = "Creatinine, Urea, eGFR & Electrolytes",
        icon = Icons.Filled.Healing
    ),
    ReportTypeOption(
        title = "Liver Function Test (LFT)",
        description = "Bilirubin, ALT, AST & Proteins",
        icon = Icons.Filled.Shield
    ),
)

data class ReportsUiState(
    val reports: List<BloodReport> = emptyList(),
    val selectedReportType: String = "General Blood Test",
    val isDropdownOpen: Boolean = false,
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val message: String? = null,
    val latestExtracted: OcrData? = null,
)

class ReportsViewModel : ViewModel() {
    val state = MutableStateFlow(ReportsUiState())

    fun selectReportType(type: String) {
        state.update { it.copy(selectedReportType = type, isDropdownOpen = false) }
    }

    fun toggleDropdown() {
        state.update { it.copy(isDropdownOpen = !it.isDropdownOpen) }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = com.nutricare.mobile.core.SessionStore.ensureActiveUser() ?: return@launch
                val reports = client.from("blood_reports").select() {
                    filter { eq("profile_id", user.id) }
                    order("report_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(20)
                }.decodeList<BloodReport>()
                state.update { it.copy(reports = reports, loading = false) }
            } catch (e: Exception) {
                state.update { it.copy(loading = false, message = "Could not load reports: ${e.message}") }
            }
        }
    }

    fun upload(context: Context, uri: Uri) {
        val currentType = state.value.selectedReportType
        state.update { it.copy(uploading = true, message = null, latestExtracted = null) }
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = com.nutricare.mobile.core.SessionStore.ensureActiveUser()
                    ?: throw Exception("Please sign in first")
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Could not read the selected file")
                val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
                val uriStr = uri.toString().lowercase()
                val ext = when {
                    mimeType.contains("pdf") || uriStr.endsWith(".pdf") -> "pdf"
                    mimeType.contains("png") || uriStr.endsWith(".png") -> "png"
                    mimeType.contains("webp") || uriStr.endsWith(".webp") -> "webp"
                    else -> "jpg"
                }
                val name = "${user.id}-report-${System.currentTimeMillis()}.$ext"
                try {
                    client.storage.from("reports").upload(name, bytes)
                } catch (storageErr: Exception) {
                    client.auth.refreshCurrentSession()
                    client.storage.from("reports").upload(name, bytes)
                }
                val fileUrl = client.storage.from("reports").publicUrl(name)

                val result = ServiceLocator.api.extractReport(fileUrl, currentType)
                if (!result.success) throw Exception(result.error ?: "Could not read the report")

                val extracted = result.data
                if (extracted != null) {
                    val reportDate = extracted.report_date?.takeIf { it.isNotBlank() }
                        ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                    val insertPayload = buildMap {
                        put("profile_id", user.id)
                        put("file_url", fileUrl)
                        put("report_date", reportDate)
                        extracted.creatinine?.let { put("creatinine", it) }
                        extracted.egfr?.let { put("egfr", it) }
                        extracted.potassium?.let { put("potassium", it) }
                        extracted.sodium?.let { put("sodium", it) }
                        extracted.glucose?.let { put("glucose", it) }
                        extracted.hemoglobin?.let { put("hemoglobin", it) }
                        extracted.albumin?.let { put("albumin", it) }
                        extracted.phosphorus?.let { put("phosphorus", it) }
                        extracted.blood_pressure?.let { put("blood_pressure", it) }
                    }
                    runCatching {
                        client.from("blood_reports").insert(insertPayload)
                    }
                }

                val flagged = result.flaggedValues?.fields?.isNotEmpty() == true
                state.update {
                    it.copy(
                        uploading = false,
                        latestExtracted = extracted,
                        message = if (flagged)
                            "✓ Saved $currentType! Some values outside normal ranges were flagged."
                        else "✓ $currentType analyzed and saved to your health records!",
                    )
                }
                load()
            } catch (e: Exception) {
                state.update { it.copy(uploading = false, message = e.message ?: "Upload failed") }
            }
        }
    }
}

@Composable
fun ReportsScreen(vm: ReportsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.load() }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            vm.upload(context, cameraUri!!)
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.upload(context, uri)
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.upload(context, uri)
    }

    fun takeCameraPhoto() {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "report-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    val selectedOption = REPORT_TYPE_OPTIONS.find { it.title == state.selectedReportType }
        ?: REPORT_TYPE_OPTIONS.first()

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            Column {
                Text("Blood & Lab Reports", style = MaterialTheme.typography.headlineMedium)
                Text("Select your report type and upload a photo or scan.", style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- REPORT TYPE SELECTOR (Matching Web App) ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "REPORT TYPE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    // Trigger Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Mist, RoundedCornerShape(14.dp))
                            .border(
                                width = 1.dp,
                                color = if (state.isDropdownOpen) EmeraldDark else Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { vm.toggleDropdown() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE6F4EA), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(selectedOption.icon, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(selectedOption.title, style = MaterialTheme.typography.titleMedium)
                                Text(selectedOption.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Icon(
                            if (state.isDropdownOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = EmeraldDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Expandable Options List
                    AnimatedVisibility(
                        visible = state.isDropdownOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            REPORT_TYPE_OPTIONS.forEach { opt ->
                                val isSelected = opt.title == state.selectedReportType
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFFE6F4EA) else Color(0xFFFAFAFA),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) EmeraldDark.copy(alpha = 0.5f) else Color.Transparent,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { vm.selectReportType(opt.title) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            opt.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) EmeraldDark else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                opt.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) EmeraldDark else Color(0xFF0F172A)
                                            )
                                            Text(opt.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = EmeraldDark,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- UPLOAD ACTIONS (Camera & Gallery) ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCFBF1)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.UploadFile, null, tint = Teal, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Upload ${selectedOption.title}", style = MaterialTheme.typography.titleMedium)
                            Text("AI will prioritize biomarkers for this test.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (state.uploading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            CircularProgressIndicator(color = Teal, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Reading & analyzing with Gemini AI…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { takeCameraPhoto() },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Camera", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { documentLauncher.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(16.dp), tint = Rose)
                                Spacer(Modifier.width(4.dp))
                                Text("PDF", color = Color(0xFF0F172A), fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { documentLauncher.launch(arrayOf("application/pdf", "image/*")) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.1f).height(44.dp)
                            ) {
                                Icon(Icons.Filled.UploadFile, null, modifier = Modifier.size(16.dp), tint = Teal)
                                Spacer(Modifier.width(4.dp))
                                Text("All Files", color = Teal, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- STATUS / SUCCESS MESSAGE ---
        state.message?.let { m ->
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Mist)
                ) {
                    Text(m, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
        }

        // --- PREVIEW OF RECENTLY EXTRACTED BIOMARKERS ---
        state.latestExtracted?.let { ext ->
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Newly Extracted Biomarkers", style = MaterialTheme.typography.titleMedium, color = EmeraldDark)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("eGFR", ext.egfr, Modifier.weight(1f))
                            Metric("Creatinine", ext.creatinine, Modifier.weight(1f))
                            Metric("Glucose", ext.glucose, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Potassium", ext.potassium, Modifier.weight(1f))
                            Metric("Sodium", ext.sodium, Modifier.weight(1f))
                            Metric("Hb", ext.hemoglobin, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // --- REPORT HISTORY HEADER ---
        item {
            Spacer(Modifier.height(6.dp))
            Text("Past Blood Reports", style = MaterialTheme.typography.titleMedium)
        }

        if (state.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmeraldDark)
                }
            }
        } else if (state.reports.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                    Text("No reports yet — upload your first one above.", modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            items(state.reports.size) { i ->
                val r = state.reports[i]
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(r.report_date ?: "Report", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("eGFR", r.egfr, Modifier.weight(1f))
                            Metric("Creat.", r.creatinine, Modifier.weight(1f))
                            Metric("Glucose", r.glucose, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Potassium", r.potassium, Modifier.weight(1f))
                            Metric("Sodium", r.sodium, Modifier.weight(1f))
                            if (r.hemoglobin != null) {
                                Metric("Hb", r.hemoglobin, Modifier.weight(1f))
                            } else {
                                Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Metric(label: String, value: Double?, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value?.toString() ?: "—", style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
