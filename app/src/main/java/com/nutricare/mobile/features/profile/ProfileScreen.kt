package com.nutricare.mobile.features.profile

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.Profile
import com.nutricare.mobile.data.UserDiseaseRow
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val profile: Profile? = null,
    val conditions: List<String> = emptyList(),
    val provider: String = "email",
    val loading: Boolean = true,
)

class ProfileViewModel : ViewModel() {
    val state = MutableStateFlow(ProfileUiState())
    val passwordEvent = MutableStateFlow<String?>(null)
    val changingPassword = MutableStateFlow(false)

    fun load() {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull() ?: return@launch
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
                state.update {
                    it.copy(
                        name = profile?.name ?: "",
                        email = user.email ?: "",
                        profile = profile,
                        conditions = diseases.mapNotNull { d -> d.diseases?.name },
                        // Google users sign in with Google — no app password exists
                        provider = user.identities?.firstOrNull()?.provider ?: "email",
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                state.update { it.copy(loading = false) }
            }
        }
    }

    fun changePassword(newPassword: String) {
        if (newPassword.length < 6) {
            passwordEvent.value = "New password must be at least 6 characters."
            return
        }
        changingPassword.value = true
        passwordEvent.value = null
        viewModelScope.launch {
            try {
                ServiceLocator.supabase.auth.updateUser {
                    password = newPassword
                }
                passwordEvent.value = "Password updated ✓"
            } catch (e: Exception) {
                passwordEvent.value = e.message ?: "Could not update the password."
            } finally {
                changingPassword.value = false
            }
        }
    }

    fun clearPasswordEvent() {
        passwordEvent.value = null
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            ServiceLocator.supabase.auth.signOut()
            onDone()
        }
    }
}

@Composable
fun ProfileScreen(onSignedOut: () -> Unit = {}, vm: ProfileViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Profile", style = MaterialTheme.typography.headlineMedium)

        // Identity card
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(Brush.linearGradient(listOf(com.nutricare.mobile.ui.theme.Emerald, com.nutricare.mobile.ui.theme.Teal)), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(state.name.ifBlank { "NutriCare Member" }, style = MaterialTheme.typography.titleLarge)
                Text(state.email, style = MaterialTheme.typography.bodySmall)
                state.profile?.goal?.let { goal ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .background(Color(0xFFECFDF5), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(goal, style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                    }
                }
            }
        }

        // Details
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("YOUR DETAILS", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Detail("Age", state.profile?.age?.toString(), Modifier.weight(1f))
                    Detail("Height", state.profile?.height?.let { "$it cm" }, Modifier.weight(1f))
                    Detail("Weight", state.profile?.weight?.let { "$it kg" }, Modifier.weight(1f))
                }
                Column {
                    Text("Conditions", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    if (state.conditions.isEmpty()) {
                        Text("None added yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.conditions.forEach { c ->
                                Box(
                                    Modifier
                                        .background(Color(0xFFF0FDFA), RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(c, style = MaterialTheme.typography.labelSmall, color = com.nutricare.mobile.ui.theme.Teal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Account settings — provider-aware
        if (state.provider == "google") {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                Text(
                    "You signed in with Google. Your password is managed by your Google account — there's nothing to change here.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            ChangePasswordCard(vm)
        }

        Button(
            onClick = { vm.signOut { onSignedOut() } },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF1F2), contentColor = Rose),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }

        Text(
            "NutriCare · Clinical AI Health Platform",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun Detail(label: String, value: String?, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Mist),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value ?: "—", style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}


@Composable
private fun ChangePasswordCard(vm: ProfileViewModel) {
    val event by vm.passwordEvent.collectAsState()
    val busy by vm.changingPassword.collectAsState()
    val newPassword = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
            Text("Change password", style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.OutlinedTextField(
                value = newPassword.value,
                onValueChange = { newPassword.value = it; vm.clearPasswordEvent() },
                label = { Text("New password") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            event?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.endsWith("✓")) EmeraldDark else MaterialTheme.colorScheme.error,
                )
            }
            androidx.compose.material3.Button(
                onClick = { vm.changePassword(newPassword.value) },
                enabled = !busy && newPassword.value.length >= 6,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Update password")
                }
            }
        }
    }
}
