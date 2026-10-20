package com.nutricare.mobile.features.medications

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.Medication
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import com.nutricare.mobile.ui.theme.Teal
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationsUiState(
    val medications: List<Medication> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

class MedicationsViewModel : ViewModel() {
    val state = MutableStateFlow(MedicationsUiState())
    val showAddDialog = MutableStateFlow(false)

    fun load() {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = com.nutricare.mobile.core.SessionStore.ensureActiveUser() ?: return@launch
                val meds = client.from("medications").select() {
                    filter { eq("profile_id", user.id) }
                    order("time_to_take", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }.decodeList<Medication>()
                state.update { it.copy(medications = meds, loading = false) }
            } catch (e: Exception) {
                state.update { it.copy(loading = false, message = "Could not load medications: ${e.message}") }
            }
        }
    }

    fun add(context: android.content.Context, name: String, dosage: String, time: String) {
        if (name.isBlank() || time.isBlank()) return
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = com.nutricare.mobile.core.SessionStore.ensureActiveUser()
                    ?: throw Exception("Please sign in first")
                val formattedTime = if (time.length == 5) "$time:00" else time
                val inserted = client.from("medications").insert(
                    mapOf(
                        "profile_id" to user.id,
                        "name" to name.trim(),
                        "dosage" to dosage.trim(),
                        "time_to_take" to formattedTime,
                    )
                ) { select() }.decodeList<Medication>()

                inserted.firstOrNull()?.let {
                    MedicationReminders.ensureChannel(context)
                    MedicationReminders.schedule(context, it)
                }
                state.update { it.copy(message = null) }
                showAddDialog.value = false
                load()
            } catch (e: Exception) {
                state.update { it.copy(message = e.message ?: "Could not save the medication.") }
            }
        }
    }

    fun delete(context: android.content.Context, medication: Medication) {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                medication.id?.let { id ->
                    client.from("medications").delete { filter { eq("id", id) } }
                }
                MedicationReminders.cancel(context, medication)
                load()
            } catch (e: Exception) {
                state.update { it.copy(message = e.message ?: "Could not delete.") }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(onBack: () -> Unit, vm: MedicationsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val showAdd by vm.showAddDialog.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.load()
        MedicationReminders.ensureChannel(context)
    }

    // Notification permission on Android 13+
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !MedicationReminders.hasNotificationPermission(context)
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("Medications", style = MaterialTheme.typography.headlineMedium)
                    Text("Daily reminders — we'll notify you at each dose time.", style = MaterialTheme.typography.bodySmall)
                }
            }

            state.message?.let { m ->
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))) {
                        Text(m, color = Rose, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EmeraldDark)
                    }
                }
            } else if (state.medications.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Medication, null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(34.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No medications yet", style = MaterialTheme.typography.titleMedium)
                            Text("Tap + to add your first medicine and reminder time.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                items(state.medications.size) { i ->
                    val m = state.medications[i]
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF0FDFA), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.Medication, null, tint = Teal, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.titleMedium)
                                Text(m.dosage ?: "", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    m.time_to_take?.take(5) ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldDark,
                                )
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    Icons.Filled.Delete, "Delete",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { vm.delete(context, m) },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { vm.showAddDialog.value = true },
            containerColor = EmeraldDark,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, "Add medication")
        }
    }

    if (showAdd) {
        AddMedicationDialog(
            onDismiss = { vm.showAddDialog.value = false },
            onAdd = { name, dosage, time -> vm.add(context, name, dosage, time) },
        )
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AddMedicationDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    val name = remember { mutableStateOf("") }
    val dosage = remember { mutableStateOf("") }
    val timeState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    val showTimePicker = remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add medication") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name.value,
                    onValueChange = { name.value = it },
                    label = { Text("Medicine name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = dosage.value,
                    onValueChange = { dosage.value = it },
                    label = { Text("Dosage (e.g. 1 tablet)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker.value = true }
                ) {
                    OutlinedTextField(
                        value = String.format("%02d:%02d", timeState.hour, timeState.minute),
                        onValueChange = { },
                        label = { Text("Reminder time") },
                        readOnly = true,
                        enabled = false,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showTimePicker.value) {
                    TimePicker(state = timeState)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    name.value,
                    dosage.value,
                    String.format("%02d:%02d", timeState.hour, timeState.minute),
                )
            }, enabled = name.value.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
