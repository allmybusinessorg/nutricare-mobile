package com.nutricare.mobile.features.reports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.BloodReport
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReportsUiState(
    val reports: List<BloodReport> = emptyList(),
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val message: String? = null,
)

class ReportsViewModel : ViewModel() {
    val state = MutableStateFlow(ReportsUiState())

    fun load() {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull() ?: return@launch
                val reports = client.from("blood_reports").select() {
                    filter { eq("profile_id", user.id) }
                    order("report_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(10)
                }.decodeList<BloodReport>()
                state.update { it.copy(reports = reports, loading = false) }
            } catch (e: Exception) {
                state.update { it.copy(loading = false, message = "Could not load reports: ${e.message}") }
            }
        }
    }

    fun upload(context: android.content.Context, uri: Uri) {
        state.update { it.copy(uploading = true, message = null) }
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull() ?: throw Exception("Please sign in first")
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Could not read the file")
                val ext = context.contentResolver.getType(uri)?.substringAfterLast('/') ?: "jpg"
                val name = "${user.id}-report-${System.currentTimeMillis()}.$ext"
                client.storage.from("reports").upload(name, bytes)
                val fileUrl = client.storage.from("reports").publicUrl(name)

                val result = ServiceLocator.api.extractReport(fileUrl)
                if (!result.success) throw Exception(result.error ?: "Could not read the report")

                val flagged = result.flaggedValues?.fields?.isNotEmpty() == true
                state.update {
                    it.copy(
                        uploading = false,
                        message = if (flagged)
                            "Report saved — some values were outside plausible ranges and were excluded. Verify manually."
                        else "Report analyzed and saved ✓",
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
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { vm.load() }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.upload(context, uri)
    }

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
                Text("Blood Reports", style = MaterialTheme.typography.headlineMedium)
                Text("Upload a lab report — AI reads your biomarkers.", style = MaterialTheme.typography.bodySmall)
            }
        }

        item {
            // Upload CTA
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickLauncher.launch("image/*") },
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.uploading) {
                        CircularProgressIndicator(color = Teal, modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    } else {
                        Icon(Icons.Filled.UploadFile, null, tint = Teal, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(if (state.uploading) "Reading your report…" else "Upload lab report", style = MaterialTheme.typography.titleMedium)
                        Text("Photo or scan of your blood test", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        state.message?.let { m ->
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                    Text(m, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                }
            }
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
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(r.report_date ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("eGFR", r.egfr, Modifier.weight(1f))
                            Metric("Creat.", r.creatinine, Modifier.weight(1f))
                            Metric("Glucose", r.glucose, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Potassium", r.potassium, Modifier.weight(1f))
                            Metric("Sodium", r.sodium, Modifier.weight(1f))
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
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
