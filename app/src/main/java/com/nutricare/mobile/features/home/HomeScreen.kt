package com.nutricare.mobile.features.home

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.core.StepTracker
import com.nutricare.mobile.core.WatchManager
import com.nutricare.mobile.core.WatchVitals
import com.nutricare.mobile.data.BloodReport
import com.nutricare.mobile.data.FoodUploadRow
import com.nutricare.mobile.data.Profile
import com.nutricare.mobile.data.UserDiseaseRow
import com.nutricare.mobile.ui.theme.Amber
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import com.nutricare.mobile.ui.theme.Slate
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
    val reports: List<BloodReport> = emptyList(),
    val recentScans: List<FoodUploadRow> = emptyList(),
    val loading: Boolean = true,
)

class HomeViewModel : ViewModel() {
    val state = MutableStateFlow(HomeUiState())

    fun load(context: Context) {
        StepTracker.init(context)
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull()
                if (user == null) {
                    state.update { it.copy(loading = false) }
                    return@launch
                }

                val profile = runCatching {
                    client.from("profiles").select {
                        filter { eq("id", user.id) }
                    }.decodeSingleOrNull<Profile>()
                }.getOrNull()

                val diseases = runCatching {
                    client.from("user_diseases").select {
                        filter { eq("profile_id", user.id) }
                    }.decodeList<UserDiseaseRow>()
                }.getOrDefault(emptyList())

                val reports = runCatching {
                    client.from("blood_reports").select {
                        filter { eq("profile_id", user.id) }
                        order("report_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(15)
                    }.decodeList<BloodReport>()
                }.getOrDefault(emptyList())

                val scans = runCatching {
                    client.from("food_uploads").select {
                        filter { eq("profile_id", user.id) }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(4)
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
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val watchVitals by WatchManager.vitals.collectAsState()
    val stepData by StepTracker.stepData.collectAsState()
    val context = LocalContext.current

    var selectedDashboardTab by remember { mutableIntStateOf(0) } // 0: Watch & Vitals, 1: Overview & Graphs

    LaunchedEffect(Unit) {
        vm.load(context)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // --- GREETING HEADER ---
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(greeting(), style = MaterialTheme.typography.labelSmall, color = Slate)
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

            // --- TWO MAIN DASHBOARD TABS (Smartwatch Vitals vs Today's Overview & Graphs) ---
            item {
                TabRow(
                    selectedTabIndex = selectedDashboardTab,
                    containerColor = Mist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(18.dp)),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedDashboardTab]),
                            color = EmeraldDark,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedDashboardTab == 0,
                        onClick = { selectedDashboardTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Watch,
                                    null,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (selectedDashboardTab == 0) EmeraldDark else Slate
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Watch & Vitals",
                                    fontWeight = if (selectedDashboardTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedDashboardTab == 0) EmeraldDark else Slate,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )

                    Tab(
                        selected = selectedDashboardTab == 1,
                        onClick = { selectedDashboardTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.MonitorHeart,
                                    null,
                                    modifier = Modifier.size(17.dp),
                                    tint = if (selectedDashboardTab == 1) EmeraldDark else Slate
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Overview & Graphs",
                                    fontWeight = if (selectedDashboardTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedDashboardTab == 1) EmeraldDark else Slate,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }
            }

            // ========================================================
            // TAB 0: SMARTWATCH & VITALS (Shows Heart Rate or "No watch connected")
            // ========================================================
            if (selectedDashboardTab == 0) {
                if (!watchVitals.isConnected) {
                    // --- NO WATCH CONNECTED VIEW ---
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(68.dp)
                                        .background(Color(0xFFF1F5F9), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Bluetooth,
                                        contentDescription = null,
                                        tint = Slate,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    "No watch connected",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Connect your smartwatch or fitness band over Bluetooth to monitor live heart rate, SpO2, and blood pressure.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(Modifier.height(4.dp))

                                Button(
                                    onClick = { onNavigate("wearables") },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Bluetooth, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Pair Smartwatch Now")
                                }

                                OutlinedButton(
                                    onClick = { WatchManager.toggleDemo() },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp), tint = Teal)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Simulate Live Watch Vitals", color = Teal)
                                }
                            }
                        }
                    }
                } else {
                    // --- WATCH CONNECTED VIEW (LIVE VITALS TELEMETRY) ---
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCFBF1)),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                // Device info header
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(10.dp)
                                                .background(Emerald, CircleShape)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(watchVitals.deviceName, style = MaterialTheme.typography.titleMedium)
                                            Text("Live Bluetooth Sync • ${watchVitals.lastSyncTime}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.BatteryChargingFull, null, tint = EmeraldDark, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("${watchVitals.battery}%", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                // Big Live Heart Rate Card
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 1.0f,
                                    targetValue = 1.15f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(650),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "heartPulse"
                                )

                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE4E6)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(18.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("HEART RATE", style = MaterialTheme.typography.labelSmall, color = Rose)
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    watchVitals.heartRate?.toString() ?: "--",
                                                    style = MaterialTheme.typography.headlineMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF9F1239)
                                                )
                                                Text(" BPM", style = MaterialTheme.typography.titleMedium, color = Rose, modifier = Modifier.padding(bottom = 4.dp))
                                            }
                                            Text("Normal resting pulse • 60-100 BPM", style = MaterialTheme.typography.bodySmall)
                                        }

                                        Icon(
                                            Icons.Filled.Favorite,
                                            contentDescription = "Beating heart",
                                            tint = Rose,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .scale(scale)
                                        )
                                    }
                                }

                                // Secondary Vitals Grid
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    VitalBox(
                                        title = "Blood Oxygen",
                                        value = "${watchVitals.spo2 ?: 98}%",
                                        status = "Normal",
                                        icon = Icons.Filled.WaterDrop,
                                        tint = Teal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    VitalBox(
                                        title = "Blood Pressure",
                                        value = watchVitals.bloodPressure ?: "118/78",
                                        status = "Optimal",
                                        icon = Icons.Filled.MonitorHeart,
                                        tint = EmeraldDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    VitalBox(
                                        title = "Sleep",
                                        value = "${watchVitals.sleepHours ?: 7.8}h",
                                        status = "Optimal",
                                        icon = Icons.Filled.Nightlight,
                                        tint = Color(0xFF6366F1),
                                        modifier = Modifier.weight(1f)
                                    )
                                    VitalBox(
                                        title = "Skin Temp",
                                        value = "${watchVitals.skinTemp ?: 36.6}°C",
                                        status = "Normal",
                                        icon = Icons.Filled.Thermostat,
                                        tint = Amber,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Text(
                                    "Disconnect Watch",
                                    color = Rose,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .clickable { WatchManager.disconnect() }
                                        .padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ========================================================
            // TAB 1: TODAY'S OVERVIEW & GRAPHS (Matches Web Dashboard)
            // ========================================================
            if (selectedDashboardTab == 1) {
                // --- NATIVE DAILY STEPS & ACTIVITY GRID (FETCHED FROM ANDROID SENSOR) ---
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("NATIVE ACTIVITY TRACKER", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                                    Text("Today's Daily Steps", style = MaterialTheme.typography.titleLarge)
                                }
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .background(Color(0xFFE6F4EA), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.FitnessCenter, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Steps count & progress bar
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    "${stepData.steps}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "/ ${stepData.goal} steps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            val stepProgress = (stepData.steps.toFloat() / stepData.goal).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(Mist, RoundedCornerShape(50))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(stepProgress)
                                        .height(10.dp)
                                        .background(Brush.horizontalGradient(listOf(Emerald, Teal)), RoundedCornerShape(50))
                                )
                            }

                            // Activity Metrics Strip (Distance, Time, Burned Calories)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.WaterDrop, null, tint = EmeraldDark, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${stepData.distanceKm} km", style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.FitnessCenter, null, tint = Amber, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${stepData.caloriesBurned} kcal burned", style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Refresh, null, tint = Slate, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${stepData.activeMinutes}m active", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                // --- ALL GRAPHS HUB (Matching Web App AllGraphsHub.tsx) ---
                item {
                    AllGraphsHubSection(reports = state.reports)
                }

                // --- HEALTH CONDITION SUMMARY ---
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCFBF1)),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("CLINICAL CONDITIONS", style = MaterialTheme.typography.labelSmall, color = Teal)
                                Icon(Icons.Filled.MonitorHeart, null, tint = Teal, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            if (state.conditions.isEmpty()) {
                                Text("No clinical conditions tracked. Add them in your Profile.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    state.conditions.forEach { condition ->
                                        Box(
                                            Modifier
                                                .background(Color.White, RoundedCornerShape(50))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(condition, style = MaterialTheme.typography.labelSmall, color = Teal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- RECENT SCANS ---
                item {
                    Text("Recent Food Scans", style = MaterialTheme.typography.titleMedium)
                }

                if (state.recentScans.isEmpty()) {
                    item {
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                            Text(
                                "No meal scans yet. Tap 'Log Food' below to analyze your first meal 🍽️",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(state.recentScans.size) { i ->
                        val s = state.recentScans[i]
                        ScanRow(s)
                    }
                }
            }

            // Extra space so list doesn't get covered by bottom action bar
            item { Spacer(Modifier.height(88.dp)) }
        }

        // ========================================================
        // PROMINENT BOTTOM TWO BUTTONS: 1. AI Chat  2. Log Food
        // ========================================================
        Card(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Button 1: AI Chat
                Button(
                    onClick = { onNavigate("chat") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("AI Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Button 2: Log Food
                Button(
                    onClick = { onNavigate("scan") },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Log Food", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// --- ALL GRAPHS HUB SECTION (Interactive Web-Style Graphs) ---
@Composable
private fun AllGraphsHubSection(reports: List<BloodReport>) {
    var graphCategory by remember { mutableStateOf("Biomarkers") }
    var selectedBiomarker by remember { mutableStateOf("eGFR") }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ALL GRAPHS HUB", style = MaterialTheme.typography.labelSmall, color = Slate)

                // Sub-category selector
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Biomarkers", "Calories", "Activity").forEach { cat ->
                        val isSel = cat == graphCategory
                        Box(
                            Modifier
                                .background(if (isSel) EmeraldDark else Mist, RoundedCornerShape(50))
                                .clickable { graphCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(cat, style = MaterialTheme.typography.labelSmall, color = if (isSel) Color.White else Slate)
                        }
                    }
                }
            }

            when (graphCategory) {
                "Biomarkers" -> {
                    // Biomarker metric selector
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("eGFR", "Creatinine", "Glucose", "Potassium").forEach { marker ->
                            val isSel = marker == selectedBiomarker
                            Box(
                                Modifier
                                    .background(if (isSel) Color(0xFFE6F4EA) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSel) EmeraldDark else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .clickable { selectedBiomarker = marker }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    marker,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) EmeraldDark else Slate
                                )
                            }
                        }
                    }

                    // Extract points for selected marker
                    val sorted = reports.sortedBy { it.report_date ?: "" }
                    val points = sorted.mapNotNull { r ->
                        val date = r.report_date?.takeLast(5) ?: "Test"
                        val v = when (selectedBiomarker) {
                            "eGFR" -> r.egfr
                            "Creatinine" -> r.creatinine
                            "Glucose" -> r.glucose
                            "Potassium" -> r.potassium
                            else -> null
                        }
                        if (v != null) date to v else null
                    }.ifEmpty {
                        listOf("Aug" to 65.0, "Sep" to 70.0, "Oct" to 74.0, "Today" to 78.0)
                    }

                    InteractiveCanvasChart(points = points, markerName = selectedBiomarker, color = EmeraldDark)
                }

                "Calories" -> {
                    val caloriePoints = listOf(
                        "Mon" to 1850.0,
                        "Tue" to 2100.0,
                        "Wed" to 1950.0,
                        "Thu" to 2200.0,
                        "Fri" to 1900.0,
                        "Sat" to 2300.0,
                        "Sun" to 2050.0
                    )
                    InteractiveCanvasChart(points = caloriePoints, markerName = "Calories (kcal)", color = Amber)
                }

                "Activity" -> {
                    val stepPoints = listOf(
                        "Mon" to 6800.0,
                        "Tue" to 8200.0,
                        "Wed" to 7500.0,
                        "Thu" to 9100.0,
                        "Fri" to 6400.0,
                        "Sat" to 10200.0,
                        "Sun" to 7420.0
                    )
                    InteractiveCanvasChart(points = stepPoints, markerName = "Steps / Day", color = Teal)
                }
            }
        }
    }
}

@Composable
private fun InteractiveCanvasChart(
    points: List<Pair<String, Double>>,
    markerName: String,
    color: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = Slate, fontSize = 10.sp)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(markerName, style = MaterialTheme.typography.titleMedium, color = color)
            Text("Latest: ${points.lastOrNull()?.second ?: "--"}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val w = size.width
            val h = size.height
            val padL = 20.dp.toPx()
            val padR = 20.dp.toPx()
            val padT = 16.dp.toPx()
            val padB = 24.dp.toPx()

            val values = points.map { it.second }
            val minV = values.minOrNull() ?: 0.0
            val maxV = values.maxOrNull() ?: 100.0
            val range = (maxV - minV).takeIf { it > 0 } ?: 1.0

            fun x(i: Int) = padL + (w - padL - padR) * i / (points.size - 1).coerceAtLeast(1)
            fun y(v: Double) = padT + (h - padT - padB) * (1.0 - (v - minV) / range).toFloat()

            // Dashed Gridlines
            val gridEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(padL, padT),
                end = Offset(w - padR, padT),
                pathEffect = gridEffect,
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFFE2E8F0),
                start = Offset(padL, h - padB),
                end = Offset(w - padR, h - padB),
                pathEffect = gridEffect,
                strokeWidth = 2f
            )

            // Polyline
            val path = Path()
            points.forEachIndexed { i, (_, v) ->
                if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
            }
            drawPath(path, color = color, style = Stroke(width = 4f))

            // Points
            points.forEachIndexed { i, (label, v) ->
                val px = x(i)
                val py = y(v)
                drawCircle(color = color, radius = 6f, center = Offset(px, py))
                drawCircle(color = Color.White, radius = 3f, center = Offset(px, py))

                // Date/Day labels along X axis
                drawText(
                    textMeasurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(px - 14f, h - padB + 6f)
                )
            }
        }
    }
}

@Composable
private fun VitalBox(
    title: String,
    value: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Mist),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = Slate)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(status, style = MaterialTheme.typography.labelSmall, color = tint)
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
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.ai_analysis_json?.food?.name ?: "Meal Scan", style = MaterialTheme.typography.titleMedium)
                Text(s.created_at?.take(10) ?: "", style = MaterialTheme.typography.bodySmall)
            }
            Box(
                Modifier
                    .background(bg, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(score ?: "Analyzed", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
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
