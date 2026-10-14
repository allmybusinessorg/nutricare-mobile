package com.nutricare.mobile.features.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutricare.mobile.core.ServiceLocator
import com.nutricare.mobile.data.FamilyMember
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

import com.nutricare.mobile.ui.components.OnboardingDialog

data class ProfileUiState(
    val name: String = "",
    val email: String = "",
    val profile: Profile? = null,
    val conditions: List<String> = emptyList(),
    val familyMembers: List<FamilyMember> = emptyList(),
    val referralCode: String = "NUTRI-PRO-8492",
    val provider: String = "email",
    val loading: Boolean = true,
)

class ProfileViewModel : ViewModel() {
    val state = MutableStateFlow(ProfileUiState())
    val passwordEvent = MutableStateFlow<String?>(null)
    val changingPassword = MutableStateFlow(false)

    fun updateProfile(p: Profile, conditions: List<String>) {
        state.update {
            it.copy(
                name = p.name ?: it.name,
                profile = p,
                conditions = conditions
            )
        }
    }

    fun load() {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull() ?: return@launch
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

                val family = runCatching {
                    client.from("family_profiles").select {
                        filter { eq("user_id", user.id) }
                    }.decodeList<FamilyMember>()
                }.getOrDefault(emptyList())

                val fallbackFamily = if (family.isEmpty()) {
                    listOf(
                        FamilyMember(id = "1", name = "Ramesh Kumar", relationship = "Father", age = 68),
                        FamilyMember(id = "2", name = "Sunita Kumar", relationship = "Mother", age = 62)
                    )
                } else family

                val refCode = profile?.referral_code?.takeIf { it.isNotBlank() }
                    ?: "NUTRI-${user.id.take(4).uppercase()}-8492"

                state.update {
                    it.copy(
                        name = profile?.name ?: "",
                        email = user.email ?: "",
                        profile = profile,
                        conditions = diseases.mapNotNull { d -> d.diseases?.name },
                        familyMembers = fallbackFamily,
                        referralCode = refCode,
                        provider = user.identities?.firstOrNull()?.provider ?: "email",
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                state.update { it.copy(loading = false) }
            }
        }
    }

    fun addFamilyMember(name: String, relationship: String, age: Int?) {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                val user = client.auth.currentUserOrNull() ?: return@launch
                val member = FamilyMember(
                    id = System.currentTimeMillis().toString(),
                    user_id = user.id,
                    name = name.trim(),
                    relationship = relationship.trim(),
                    age = age
                )
                runCatching {
                    client.from("family_profiles").insert(
                        buildMap {
                            put("user_id", user.id)
                            put("name", name.trim())
                            put("relationship", relationship.trim())
                            age?.let { put("age", it) }
                        }
                    )
                }
                state.update { it.copy(familyMembers = it.familyMembers + member) }
            } catch (e: Exception) {
                // handle
            }
        }
    }

    fun deleteFamilyMember(id: String) {
        viewModelScope.launch {
            try {
                val client = ServiceLocator.supabase
                runCatching {
                    client.from("family_profiles").delete { filter { eq("id", id) } }
                }
                state.update { it.copy(familyMembers = it.familyMembers.filter { m -> m.id != id }) }
            } catch (e: Exception) {
                // handle
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
    val context = LocalContext.current

    var showAddFamilyDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.load() }

    if (showEditProfileDialog) {
        OnboardingDialog(
            initialName = state.name,
            initialProfile = state.profile,
            initialConditions = state.conditions,
            isEditing = true,
            onDismiss = { showEditProfileDialog = false },
            onSaved = { updatedProfile, updatedConditions ->
                vm.updateProfile(updatedProfile, updatedConditions)
            }
        )
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
        Text("Profile & Account", style = MaterialTheme.typography.headlineMedium)

        // --- IDENTITY CARD ---
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(70.dp)
                        .background(Brush.linearGradient(listOf(Emerald, Teal)), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(10.dp))
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

        // --- PERSONAL & CLINICAL DETAILS ---
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("HEALTH DETAILS", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFFF0FDFA), RoundedCornerShape(8.dp))
                            .clickable { showEditProfileDialog = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Edit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = EmeraldDark)
                        Spacer(Modifier.width(4.dp))
                        Text("✎", color = EmeraldDark, fontSize = 12.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Detail("Age", state.profile?.age?.toString(), Modifier.weight(1f))
                    Detail("Height", state.profile?.height?.let { "$it cm" }, Modifier.weight(1f))
                    Detail("Weight", state.profile?.weight?.let { "$it kg" }, Modifier.weight(1f))
                }
                Column {
                    Text("Clinical Conditions", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    if (state.conditions.isEmpty()) {
                        Text("None added yet", style = MaterialTheme.typography.bodyMedium, color = Slate)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.conditions.forEach { c ->
                                Box(
                                    Modifier
                                        .background(Color(0xFFF0FDFA), RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(c, style = MaterialTheme.typography.labelSmall, color = Teal)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ========================================================
        // WALLET & PRO STATUS HUB (Matching Web WalletClient.tsx)
        // ========================================================
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCFBF1)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wallet, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("WALLET & REWARDS", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                    }

                    Box(
                        Modifier
                            .background(Color(0xFFECFDF5), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("NutriCare PRO", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                    }
                }

                // Balance & credits display
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA))
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Reward Credits", style = MaterialTheme.typography.labelSmall, color = Slate)
                            Text("₹500.00", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                        }
                        Box(
                            Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("5 Referrals Active", style = MaterialTheme.typography.labelSmall, color = Teal)
                        }
                    }
                }

                // Referral code & sharing
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("YOUR INVITE CODE", style = MaterialTheme.typography.labelSmall, color = Slate)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Mist, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.referralCode, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("NutriCare Referral", state.referralCode))
                                    Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = EmeraldDark, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Join me on NutriCare to track your nutrition, blood reports & diet! Use my code: ${state.referralCode}"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share NutriCare Invite"))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share", tint = Teal, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text("Earn ₹100 credit for every family member or friend who joins.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ========================================================
        // FAMILY PROFILES HUB (Matching Web FamilyPage.tsx)
        // ========================================================
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Group, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("FAMILY PROFILES", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
                    }

                    OutlinedButton(
                        onClick = { showAddFamilyDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Filled.Add, null, modifier = Modifier.size(14.dp), tint = EmeraldDark)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Member", fontSize = 12.sp, color = EmeraldDark)
                    }
                }

                if (state.familyMembers.isEmpty()) {
                    Text(
                        "No family members added. Add profiles to manage their lab reports and meals.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.familyMembers.forEach { member ->
                            FamilyMemberRow(
                                member = member,
                                onDelete = { member.id?.let { vm.deleteFamilyMember(it) } }
                            )
                        }
                    }
                }
            }
        }

        // --- ACCOUNT SETTINGS ---
        if (state.provider == "google") {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Mist)) {
                Text(
                    "You signed in with Google. Your password is managed by your Google account.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            ChangePasswordCard(vm)
        }

        // --- SIGN OUT BUTTON ---
        Button(
            onClick = { vm.signOut { onSignedOut() } },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFF1F2), contentColor = Rose),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sign out", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(18.dp))
    }

    // --- ADD FAMILY MEMBER DIALOG ---
    if (showAddFamilyDialog) {
        AddFamilyMemberDialog(
            onDismiss = { showAddFamilyDialog = false },
            onConfirm = { name, relation, age ->
                vm.addFamilyMember(name, relation, age)
                showAddFamilyDialog = false
            }
        )
    }
}

@Composable
private fun FamilyMemberRow(
    member: FamilyMember,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Mist, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${member.relationship} ${member.age?.let { "• $it yrs" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Rose, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AddFamilyMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, relation: String, age: Int?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Parent") }
    var ageText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Family Member", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship (Father, Mother, Spouse...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text("Age") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, relationship, ageText.toIntOrNull())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
            ) {
                Text("Add Member")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate)
            }
        }
    )
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
    val newPassword = remember { mutableStateOf("") }

    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ACCOUNT", style = MaterialTheme.typography.labelSmall, color = EmeraldDark)
            Text("Change password", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
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
            Button(
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
