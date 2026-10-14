package com.nutricare.mobile.features.home

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Watch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.BloodReport
import com.nutricare.mobile.data.FoodUploadRow
import com.nutricare.mobile.data.Profile
import com.nutricare.mobile.data.UserDiseaseRow
import androidx.compose.material.icons.filled.Medication
import com.nutricare.mobile.ui.theme.Amber
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import com.nutricare.mobile.ui.theme.Sky
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val name: String = "",
    val conditions: List<String> = emptyList(),
    val latestReport: BloodReport? = null,
    val recentScans: List<FoodUploadRow> = emptyList(),
    val reports: List<BloodReport> = emptyList(),
    val loading: Boolean = true,
)

class HomeViewModel : ViewModel() {
    val state = MutableStateFlow(HomeUiState())

    fun load() {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull()
                if (user == null) { state.update { it.copy(loading = false) }; return@launch }

                val profile = runCatching {
                    client.from("profiles").select() {
                    filter { eq("id", user.id) }
                }.decodeSingleOrNull<Profile>()
                }.getOrNull()

                val diseases = runCatching {
                    client.from("user_diseases").select() {
                        filter { eq("profile_id", user.id) }
                    }.decodeList<UserDiseaseRow>()
                }.getOrDefault(emptyList())

                val reports = runCatching {
                    client.from("blood_reports").select() {
                        filter { eq("profile_id", user.id) }
                        order("report_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(10)
                    }.decodeList<BloodReport>()
                }.getOrDefault(emptyList())

                val scans = runCatching {
                    client.from("food_uploads").select() {
                        filter { eq("profile_id", user.id) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(3)
                    }.decodeList<FoodUploadRow>()
                }.getOrDefault(emptyList())

                state.update {
                    it.copy(
                        name = profile?.name?.split(" ")?.firstOrNull() ?: "there",
                        conditions = diseases.mapNotNull { d -> d.diseases?.name },
                        latestReport = reports.firstOrNull(),
                        reports = reports,
                        recentScans = scans,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                state.update { it.copy(loading = false) }
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (String) -> Unit = {}, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // Greeting
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(greeting(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.name} 👋", style = MaterialTheme.typography.headlineMedium)
                }
                Box(
                    Modifier
                        .size(46.dp)
                        .background(Brush.linearGradient(listOf(Emerald, Teal)), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        // Health summary card (MyFitnessPal-style stat strip)
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("HEALTH SUMMARY", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                        Icon(Icons.Filled.MonitorHeart, null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(value = if (state.loading) "···" else state.conditions.size.let { if (it == 0) "—" else it.toString() }, label = "Conditions", modifier = Modifier.weight(1f))
                        StatCell(value = if (state.loading) "···" else state.latestReport?.egfr?.toString() ?: "—", label = "eGFR", modifier = Modifier.weight(1f))
                        StatCell(value = if (state.loading) "···" else state.latestReport?.glucose?.toString() ?: "—", label = "Glucose", modifier = Modifier.weight(1f))
                    }
                    if (state.conditions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.conditions.take(3).forEach { c ->
                                Box(
                                    Modifier
                                        .background(Color.White, RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) { Text(c, style = MaterialTheme.typography.labelSmall, color = EmeraldDark) }
                            }
                        }
                    }
                }
            }
        }

        // Quick actions grid
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Modifier.weight(1f), Icons.Filled.PhotoCamera, "Scan Food", Emerald) { onNavigate("scan") }
                QuickAction(Modifier.weight(1f), Icons.Filled.MonitorHeart, "Reports", Teal) { onNavigate("reports") }
                QuickAction(Modifier.weight(1f), Icons.Filled.Chat, "AI Chat", Sky) { onNavigate("chat") }
            }
        }

        // Medications card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("medications") },
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Medication, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Medications & reminders", style = MaterialTheme.typography.titleMedium)
                        Text("Daily dose notifications", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF7C3AED))
                }
            }
        }

        // Wearables teaser
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate("wearables") },
            ) {

        // Biomarker trends chart
        item {
            val reportsAsc = state.reports.sortedBy { it.report_date ?: "" }
            TrendCard(reportsAsc)
        }
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Watch, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Connect your smartwatch", style = MaterialTheme.typography.titleMedium)
                        Text("Live heart rate over Bluetooth", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF7C3AED))
                }
            }
        }

        // Recent scans
        item {
            Text("Recent scans", style = MaterialTheme.typography.titleMedium)
        }
        if (state.recentScans.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                    Text(
                        "No scans yet — tap Scan to check your first meal 🍽️",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        } else {
            items(state.recentScans.size) { i ->
                val s = state.recentScans[i]
                ScanRow(s)
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "GOOD MORNING"
        h < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
}

@androidx.compose.runtime.Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Mist),
        modifier = modifier.clickable { onClick() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ScanRow(s: FoodUploadRow) {
    val score = s.safety_score
    val (color, bg) = when (score) {
        "Safe" -> EmeraldDark to Color(0xFFECFDF5)
        "Caution" -> Amber to Color(0xFFFFFBEB)
        "Avoid" -> Rose to Color(0xFFFFF1F2)
        else -> EmeraldDark to Mist
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.ai_analysis_json?.food?.name ?: "Meal scan", style = MaterialTheme.typography.titleMedium)
                Text(
                    s.created_at?.take(10) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                Modifier
                    .background(bg, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(score ?: "Processed", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun TrendCard(reportsAsc: List<BloodReport>) {
    val metrics = listOf("Glucose" to "glucose", "eGFR" to "egfr", "Creatinine" to "creatinine", "Potassium" to "potassium", "Sodium" to "sodium")
    val selected = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Glucose") }
    val key = metrics.first { it.first == selected.value }.second

    fun value(r: BloodReport): Double? = when (key) {
        "glucose" -> r.glucose
        "egfr" -> r.egfr
        "creatinine" -> r.creatinine
        "potassium" -> r.potassium
        "sodium" -> r.sodium
        else -> null
    }

    val points = reportsAsc.mapNotNull { r ->
        val v = value(r) ?: return@mapNotNull null
        (r.report_date?.take(5) ?: "") to v
    }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("BIOMARKER TRENDS", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                metrics.take(3).forEach { (label, _) ->
                    val active = selected.value == label
                    Box(
                        Modifier
                            .weight(1f)
                            .background(if (active) Color(0xFFECFDF5) else Mist, RoundedCornerShape(12.dp))
                            .clickable { selected.value = label }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                metrics.drop(3).forEach { (label, _) ->
                    val active = selected.value == label
                    Box(
                        Modifier
                            .weight(1f)
                            .background(if (active) Color(0xFFECFDF5) else Mist, RoundedCornerShape(12.dp))
                            .clickable { selected.value = label }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            TrendChart(points = points)
        }
    }
}

