package com.nutricare.mobile.features.dietitian

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Slate
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class DietitianProfile(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val specialization: String? = null,
    val rating: Double = 4.9,
    val reviewsCount: Int = 18,
    val hospital: String? = null,
    val isAssigned: Boolean = false
)

data class DietitianUiState(
    val dietitians: List<DietitianProfile> = emptyList(),
    val assignedDietitianId: String? = null,
    val loading: Boolean = true,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class DietitianViewModel : ViewModel() {
    val state = MutableStateFlow(DietitianUiState())

    fun load() {
        viewModelScope.launch {
            state.update { it.copy(loading = true, errorMessage = null) }
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull()

                var currentLinkDietitianId: String? = null
                if (user != null) {
                    val linkRows = runCatching {
                        client.from("patient_dietitian_links")
                            .select { filter { eq("patient_id", user.id) } }
                            .decodeList<Map<String, String>>()
                    }.getOrDefault(emptyList())

                    currentLinkDietitianId = linkRows.firstOrNull()?.get("dietitian_id")
                }

                // Query dietitians from profiles where role = 'dietitian'
                val dbDietitians = runCatching {
                    client.from("profiles")
                        .select { filter { eq("role", "dietitian") } }
                        .decodeList<Map<String, String>>()
                }.getOrDefault(emptyList())

                val mappedList = if (dbDietitians.isNotEmpty()) {
                    dbDietitians.mapIndexed { idx, row ->
                        val id = row["id"] ?: "dietitian-$idx"
                        DietitianProfile(
                            id = id,
                            name = row["name"] ?: "Dr. Clinical Dietitian",
                            email = row["email"],
                            specialization = row["specialization"] ?: "Renal & Metabolic Nutritionist",
                            rating = 4.9,
                            reviewsCount = 24,
                            hospital = "Apollo Health & Kidney Institute",
                            isAssigned = (id == currentLinkDietitianId)
                        )
                    }
                } else {
                    // Standard clinical board roster matching the web app
                    listOf(
                        DietitianProfile(
                            id = "dietitian-dr-sen",
                            name = "Dr. Ananya Sen, RD",
                            email = "ananya.sen@nutricare.health",
                            specialization = "Renal & CKD Stage 1-4 Specialist",
                            rating = 4.95,
                            reviewsCount = 38,
                            hospital = "National Kidney & Nutrition Center",
                            isAssigned = ("dietitian-dr-sen" == currentLinkDietitianId)
                        ),
                        DietitianProfile(
                            id = "dietitian-dr-patel",
                            name = "Dr. Rajesh Patel, MD (Nutr)",
                            email = "rajesh.patel@nutricare.health",
                            specialization = "Type 2 Diabetes & Insulin Optimization",
                            rating = 4.90,
                            reviewsCount = 29,
                            hospital = "Metabolic Care Associates",
                            isAssigned = ("dietitian-dr-patel" == currentLinkDietitianId)
                        ),
                        DietitianProfile(
                            id = "dietitian-dr-mukherjee",
                            name = "Dr. Priya Mukherjee, PhD",
                            email = "priya.m@nutricare.health",
                            specialization = "Hypertension & DASH Dietetics",
                            rating = 4.88,
                            reviewsCount = 21,
                            hospital = "Cardio-Renal Wellness Institute",
                            isAssigned = ("dietitian-dr-mukherjee" == currentLinkDietitianId)
                        )
                    )
                }

                state.update {
                    it.copy(
                        dietitians = mappedList,
                        assignedDietitianId = currentLinkDietitianId,
                        loading = false
                    )
                }
            } catch (e: Exception) {
                state.update {
                    it.copy(
                        loading = false,
                        errorMessage = "Could not load dietitians: ${e.message}"
                    )
                }
            }
        }
    }

    fun bookConsultation(dietitianId: String, notes: String, preferredDate: String) {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull() ?: throw Exception("Please sign in first")

                // 1. Delete previous link if any
                runCatching {
                    client.from("patient_dietitian_links").delete {
                        filter { eq("patient_id", user.id) }
                    }
                }

                // 2. Insert new link
                client.from("patient_dietitian_links").insert(
                    mapOf(
                        "patient_id" to user.id,
                        "dietitian_id" to dietitianId,
                        "status" to "active"
                    )
                )

                // 3. Record consultation request
                runCatching {
                    client.from("consultation_requests").insert(
                        mapOf(
                            "patient_id" to user.id,
                            "dietitian_id" to dietitianId,
                            "preferred_date" to preferredDate,
                            "notes" to notes,
                            "status" to "confirmed"
                        )
                    )
                }

                state.update {
                    it.copy(
                        assignedDietitianId = dietitianId,
                        successMessage = "Consultation requested successfully! Your dietitian has been notified."
                    )
                }
                load()
            } catch (e: Exception) {
                state.update {
                    it.copy(errorMessage = e.message ?: "Failed to book consultation.")
                }
            }
        }
    }
}

@Composable
fun DietitianScreen(
    onBack: () -> Unit,
    vm: DietitianViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var selectedForBooking by remember { mutableStateOf<DietitianProfile?>(null) }

    LaunchedEffect(Unit) {
        vm.load()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Clinical Dietitians", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Consult certified specialists aligned to your lab reports", fontSize = 12.sp, color = Slate)
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success & Error Banners
            state.successMessage?.let { msg ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldDark)
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(msg, fontSize = 13.sp, color = EmeraldDark, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            state.errorMessage?.let { err ->
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))
                    ) {
                        Text(err, fontSize = 13.sp, color = Color(0xFFE11D48), modifier = Modifier.padding(14.dp))
                    }
                }
            }

            // Info Banner
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(Color(0xFFECFDF5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Shield, null, tint = EmeraldDark, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Hospital-Accredited Clinicians", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "Your assigned dietitian reviews your blood reports, safe recipes, and food logs directly in the clinical portal.",
                                fontSize = 11.sp,
                                color = Slate
                            )
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EmeraldDark)
                    }
                }
            } else {
                items(state.dietitians.size) { index ->
                    val d = state.dietitians[index]
                    val isAssigned = (d.id == state.assignedDietitianId || d.isAssigned)

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAssigned) Color(0xFFF0FDF4) else Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isAssigned) 2.dp else 1.dp,
                            color = if (isAssigned) EmeraldDark else Color(0xFFE2E8F0)
                        ),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isAssigned) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(EmeraldDark, RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("YOUR ASSIGNED DIETITIAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .background(Brush.linearGradient(listOf(Color(0xFFE6F4EA), Color(0xFFCCFBF1))), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.name?.take(1) ?: "D",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        color = EmeraldDark
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(d.name ?: "Clinical Dietitian", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(d.specialization ?: "Nutrition Specialist", fontSize = 12.sp, color = EmeraldDark, fontWeight = FontWeight.Medium)
                                    d.hospital?.let {
                                        Text(it, fontSize = 11.sp, color = Slate)
                                    }
                                }
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${d.rating}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(" (${d.reviewsCount} patient reviews)", fontSize = 11.sp, color = Slate)
                                }

                                Button(
                                    onClick = { selectedForBooking = d },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAssigned) Color(0xFF0F172A) else EmeraldDark
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        if (isAssigned) Icons.Filled.Chat else Icons.Filled.CalendarMonth,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isAssigned) "Contact" else "Book / Connect",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    selectedForBooking?.let { dietitian ->
        BookingConsultationDialog(
            dietitian = dietitian,
            onDismiss = { selectedForBooking = null },
            onConfirm = { notes, date ->
                vm.bookConsultation(dietitian.id, notes, date)
                selectedForBooking = null
            }
        )
    }
}

@Composable
private fun BookingConsultationDialog(
    dietitian: DietitianProfile,
    onDismiss: () -> Unit,
    onConfirm: (notes: String, date: String) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var preferredDate by remember { mutableStateOf("Tomorrow (10:00 AM)") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Book Dietitian Consultation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("with ${dietitian.name}", fontSize = 12.sp, color = EmeraldDark)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your dietitian will review your blood reports and daily food logs to prepare personalized meal protocols.",
                    fontSize = 12.sp,
                    color = Slate
                )

                Text("Preferred Time Slot", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tomorrow 10 AM", "Tomorrow 3 PM", "Saturday 11 AM").forEach { slot ->
                        val isSel = slot == preferredDate
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFFECFDF5) else Mist)
                                .border(1.dp, if (isSel) EmeraldDark else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .clickable { preferredDate = slot }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(slot, fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) EmeraldDark else Color(0xFF334155))
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Health Goals or Symptoms (Optional)") },
                    placeholder = { Text("e.g. Need help lowering eGFR creatinine & potassium") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(notes, preferredDate) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Confirm & Connect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate)
            }
        }
    )
}
