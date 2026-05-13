package com.aicompanion.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Pink50 = Color(0xFFFFF0F5)
private val Pink100 = Color(0xFFFFE0EC)
private val Pink200 = Color(0xFFFFC0D8)
private val Pink400 = Color(0xFFFF85A2)
private val Pink500 = Color(0xFFFF6B8A)
private val Pink600 = Color(0xFFF04F7A)
private val Pink700 = Color(0xFFE0386A)
private val Purple400 = Color(0xFFC4A5E8)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)
private val DividerColor = Color(0xFFF0E0E8)
private val ChatBg = Color(0xFFFFF5F7)

data class PersonaConversation(
    val persona: Persona,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0
)

@Composable
fun ConversationListScreen(
    personas: List<Persona>,
    conversations: Map<String, PersonaConversation>,
    activePersonaId: String?,
    onPersonaClick: (String) -> Unit,
    onNewConversation: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        // Top bar (plain Row, no inner Scaffold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Pink500, Pink600, Pink700)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("对话", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
                if (personas.isNotEmpty()) {
                    Text(
                        "${personas.size} 个角色等你聊天",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp
                    )
                }
            }
            if (personas.isNotEmpty()) {
                IconButton(onClick = onNewConversation) {
                    Icon(Icons.Default.Add, "新建对话", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Content
        if (personas.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(Pink100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Pink400, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("还没有角色", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    Spacer(Modifier.height(8.dp))
                    Text("先创建一个人设开始对话吧", fontSize = 14.sp, color = TextGray)
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onNavigateToPersonas,
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("创建人设")
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(personas, key = { it.id }) { persona ->
                    val convInfo = conversations[persona.id]
                    ConversationRow(
                        persona = persona,
                        lastMessage = convInfo?.lastMessage ?: persona.firstMessage.ifBlank { "点击开始对话" },
                        lastTime = convInfo?.lastMessageTime,
                        isActive = persona.id == activePersonaId,
                        onClick = { onPersonaClick(persona.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    persona: Persona,
    lastMessage: String,
    lastTime: Long?,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val timeStr = if (lastTime != null && lastTime > 0) {
        val now = System.currentTimeMillis()
        val diff = now - lastTime
        when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000}分钟前"
            diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastTime))
            else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(lastTime))
        }
    } else ""

    val avatarColors = listOf(
        Pink400, Color(0xFF64B5F6), Color(0xFF81C784),
        Color(0xFFFFB74D), Purple400, Color(0xFF4DD0E1),
        Color(0xFFE57373), Color(0xFFBA68C8)
    )
    val avatarColor = avatarColors[persona.name.firstOrNull()?.code?.mod(avatarColors.size) ?: 0]

    Surface(
        color = if (isActive) Pink50 else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(persona.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (timeStr.isNotEmpty()) Text(timeStr, fontSize = 12.sp, color = TextGray)
                }
                Spacer(Modifier.height(4.dp))
                Text(lastMessage, fontSize = 14.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (!isActive) {
        Box(modifier = Modifier.fillMaxWidth().padding(start = 86.dp).height(0.5.dp).background(DividerColor))
    }
}
