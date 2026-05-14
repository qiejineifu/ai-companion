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
import coil3.compose.AsyncImage
import com.aicompanion.core.ui.theme.*
import com.aicompanion.domain.model.Conversation
import com.aicompanion.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onNewGroupChat: () -> Unit = {},
    onNavigateToPersonas: () -> Unit,
    onNavigateToSettings: () -> Unit,
    groupConversations: List<com.aicompanion.domain.model.Conversation> = emptyList(),
    onGroupChatClick: ((String) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        // Top bar (plain Row, no inner Scaffold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(topBarGradient))
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
                IconButton(onClick = onNewGroupChat) {
                    Icon(Icons.Default.Group, "新建群聊", tint = Color.White, modifier = Modifier.size(28.dp))
                }
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
                // Group conversations
                if (groupConversations.isNotEmpty()) {
                    item(key = "section_group") {
                        Text(
                            "群聊", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = Pink500
                        )
                    }
                    items(groupConversations, key = { "group_${it.id}" }) { conv ->
                        GroupChatRow(
                            conv = conv,
                            personas = personas,
                            onClick = { onGroupChatClick?.invoke(conv.id) }
                        )
                    }
                }
                items(personas, key = { it.id }) { persona ->
                    val convInfo = conversations[persona.id]
                    ConversationRow(
                        persona = persona,
                        lastMessage = conversations[persona.id]?.lastMessage
                        ?: persona.firstMessage.ifBlank { "点击开始对话" },
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
                modifier = Modifier.size(56.dp).clip(CircleShape).background(
                    if (persona.avatarImageUri == null) avatarColor else Color.Transparent
                ),
                contentAlignment = Alignment.Center
            ) {
                if (persona.avatarImageUri != null) {
                    AsyncImage(
                        model = persona.avatarImageUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(persona.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
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
        Box(modifier = Modifier.fillMaxWidth().padding(start = 86.dp).height(0.5.dp).background(DividerPink))
    }
}

@Composable
private fun GroupChatRow(
    conv: Conversation,
    personas: List<Persona>,
    onClick: () -> Unit
) {
    val timeStr = if (conv.lastMessageAt > 0) {
        val now = System.currentTimeMillis()
        val diff = now - conv.lastMessageAt
        when {
            diff < 60_000 -> "刚刚"
            diff < 3600_000 -> "${diff / 60_000}分钟前"
            diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(conv.lastMessageAt))
            else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(conv.lastMessageAt))
        }
    } else ""

    val groupPersonaNames = conv.groupPersonaIds.mapNotNull { id ->
        personas.find { it.id == id }?.name
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group avatar
            val groupCircleMod = Modifier.size(56.dp).clip(CircleShape)
                .let { m -> if (conv.avatarImageUri == null) m.background(Brush.horizontalGradient(listOf(Pink400, Pink600))) else m.background(Color.Transparent) }
            Box(
                modifier = groupCircleMod,
                contentAlignment = Alignment.Center
            ) {
                if (conv.avatarImageUri != null) {
                    AsyncImage(model = conv.avatarImageUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Text("👥", fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("群聊", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (timeStr.isNotEmpty()) Text(timeStr, fontSize = 12.sp, color = TextGray)
                }
                Spacer(Modifier.height(4.dp))
                if (groupPersonaNames.isNotEmpty()) {
                    Text(groupPersonaNames.joinToString("、"), fontSize = 12.sp, color = Pink400, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (conv.lastMessagePreview.isNotBlank()) {
                    Text(conv.lastMessagePreview, fontSize = 14.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth().padding(start = 86.dp).height(0.5.dp).background(DividerPink))
}
