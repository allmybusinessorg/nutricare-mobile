package com.nutricare.mobile.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nutricare.mobile.ui.theme.Emerald
import com.nutricare.mobile.ui.theme.EmeraldDark
import com.nutricare.mobile.ui.theme.Mist
import com.nutricare.mobile.ui.theme.Rose
import com.nutricare.mobile.ui.theme.Teal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
)

data class ChatUiState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            isUser = false,
            text = "Hello! 👋 I'm your Clinical AI Assistant. Ask me about your lab values, diet for your conditions, or what to eat today.",
        ),
    ),
    val input: String = "",
    val loading: Boolean = false,
)

class ChatViewModel : ViewModel() {
    val state = MutableStateFlow(ChatUiState())

    fun onInput(v: String) = state.update { it.copy(input = v) }

    fun send() {
        val s = state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.loading) return
        state.update {
            it.copy(
                input = "",
                loading = true,
                messages = it.messages + ChatMessage(isUser = true, text = text),
            )
        }
        viewModelScope.launch {
            try {
                val reply = ServiceLocator.api.chat(text)
                val answer = reply.reply
                    ?: "I couldn't generate a response right now. ${reply.error ?: "Please try again."}"
                state.update { it.copy(loading = false, messages = it.messages + ChatMessage(false, answer)) }
            } catch (e: Exception) {
                state.update {
                    it.copy(
                        loading = false,
                        messages = it.messages + ChatMessage(false, "Sorry — I couldn't reach the AI service. Check your connection and try again."),
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // Header
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color(0xFFECFDF5), Color(0xFFF0FDFA))))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(Brush.linearGradient(listOf(Emerald, Teal)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Clinical AI Assistant", style = MaterialTheme.typography.titleMedium)
                    Text("Personalized with your health data", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.size(8.dp)) }
            items(state.messages) { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = if (m.isUser) 18.dp else 4.dp,
                            topEnd = 18.dp,
                            bottomStart = 18.dp,
                            bottomEnd = if (m.isUser) 4.dp else 18.dp,
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (m.isUser) EmeraldDark else Color.White,
                        ),
                        elevation = CardDefaults.cardElevation(1.dp),
                        modifier = Modifier.widthIn(max = 300.dp),
                    ) {
                        Text(
                            m.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (m.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
            if (state.loading) {
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = EmeraldDark, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Analyzing your data…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Spacer(Modifier.size(8.dp)) }
        }

        // Input bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = { vm.onInput(it) },
                placeholder = { Text("Ask about your health…") },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
                maxLines = 3,
            )
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.IconButton(
                onClick = { vm.send() },
                enabled = !state.loading && state.input.isNotBlank(),
                modifier = Modifier
                    .size(52.dp)
                    .background(EmeraldDark, RoundedCornerShape(16.dp)),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White)
            }
        }
    }
}
