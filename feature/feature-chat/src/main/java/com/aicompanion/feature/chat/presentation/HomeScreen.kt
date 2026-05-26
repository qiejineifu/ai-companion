package com.aicompanion.feature.chat.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import android.content.Context
import com.aicompanion.core.common.UnreadTracker
import com.aicompanion.domain.model.ExperienceEntry
import com.aicompanion.domain.model.MomentEntry
import com.aicompanion.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.*

// === Soft pink theme colors ===
private val BgGradientStart = Color(0xFFFFF0F3)
private val BgGradientEnd = Color(0xFFFFF8F9)
private val AccentPink = Color(0xFFFF8DAC)
private val TextDark = Color(0xFF3D2D35)
private val TextGray = Color(0xFF999999)
private val TextLightGray = Color(0xFFB0B0B0)
private val CardWhite = Color.White
private val CardSemiWhite = Color(0xF0FFF0F5)
private val DividerColor = Color(0xFFF0E0E5)

// Relationship tag colors
private val relationColors = mapOf(
    "恋人" to Color(0xFFFF6B9B),
    "知己" to Color(0xFF9B7FD4),
    "闺蜜" to Color(0xFFFF9B8A),
    "学长" to Color(0xFF6BA3FF),
    "妹妹" to Color(0xFFFFB06B),
    "青梅" to Color(0xFF7FD49B),
    "同事" to Color(0xFF91A0B0),
    "网友" to Color(0xFFD49BDA)
)

private val relationBgColors = mapOf(
    "恋人" to Color(0x33FF6B9B),
    "知己" to Color(0x339B7FD4),
    "闺蜜" to Color(0x33FF9B8A),
    "学长" to Color(0x336BA3FF),
    "妹妹" to Color(0x33FFB06B),
    "青梅" to Color(0x337FD49B),
    "同事" to Color(0x3391A0B0),
    "网友" to Color(0x33D49BDA)
)

@Composable
fun HomeScreen(
    personas: List<Persona>,
    conversations: Map<String, PersonaConversation>,
    groupConversations: List<com.aicompanion.domain.model.Conversation>,
    onPersonaClick: (String) -> Unit,
    onGroupChatClick: (String) -> Unit,
    onNewConversation: () -> Unit,
    onNewGroupChat: () -> Unit = {},
    onNavigateToPersonas: () -> Unit,
    onNavigateToSettings: () -> Unit,
    characterIllustrationPath: String? = null,
    context: Context? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("对话", "动态", "经历")
    val unreadCounts = remember(personas, context) {
        if (context != null) UnreadTracker.getAll(context, personas.map { it.id })
        else emptyMap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgGradientStart, BgGradientEnd)))
    ) {
        // === Top nav bar ===
        TopNavBar(onNewClick = onNewConversation, onGroupClick = onNewGroupChat)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // === Welcome card ===
            item {
                WelcomeCard(illustrationPath = characterIllustrationPath)
            }

            // === Tab bar ===
            item {
                TabBar(tabs = tabs, selectedIndex = selectedTab, onTabSelected = { selectedTab = it })
            }

            // === Tab content ===
            when (selectedTab) {
                0 -> {
                    // 对话 tab
                    item {
                        Spacer(Modifier.height(4.dp))
                    }
                    // Single persona chats
                    conversations.entries.forEachIndexed { _, (personaId, conv) ->
                        item(key = "chat_$personaId") {
                            ChatListItem(
                                persona = conv.persona,
                                relationTag = conv.persona.relationshipType,
                                lastMessage = conv.lastMessage,
                                time = conv.lastMessageTime,
                                unreadCount = unreadCounts[personaId] ?: 0,
                                onClick = { onPersonaClick(personaId) }
                            )
                        }
                    }
                    // Group chats
                    groupConversations.forEach { group ->
                        item(key = "group_${group.id}") {
                            GroupChatListItem(
                                conversation = group,
                                personas = personas,
                                onClick = { onGroupChatClick(group.id) }
                            )
                        }
                    }
                    if (conversations.isEmpty() && groupConversations.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💕", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("还没有对话", color = TextGray, fontSize = 14.sp)
                                    Text("点击 + 开始一段新的故事吧", color = TextLightGray, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // 动态 tab — all personas' moments
                    val allMoments = personas.flatMap { p -> p.moments.map { it to p } }
                        .sortedByDescending { it.first.createdAt }
                    if (allMoments.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📭", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("还没有人发朋友圈", color = TextGray, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        var lastDateLabel = ""
                        allMoments.forEach { (moment, persona) ->
                            val dateStr = SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(moment.createdAt))
                            val showDivider = dateStr != lastDateLabel
                            if (showDivider) {
                                lastDateLabel = dateStr
                                item(key = "date_$dateStr") {
                                    Text(dateStr, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                        textAlign = TextAlign.Center, color = TextGray, fontSize = 13.sp)
                                }
                            }
                            item(key = "moment_${moment.id}") {
                                MomentFeedCard(moment = moment, persona = persona)
                            }
                        }
                    }
                }
                2 -> {
                    // 经历 tab — all personas' experiences
                    val allExps = personas.flatMap { p -> p.experiences.map { it to p } }
                        .sortedByDescending { it.first.createdAt }
                    if (allExps.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("📖", fontSize = 32.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("还没有经历故事", color = TextGray, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        allExps.forEach { (exp, persona) ->
                            item(key = "exp_${exp.id}") {
                                ExperienceFeedCard(experience = exp, persona = persona)
                            }
                        }
                    }
                }
            }

            // Bottom spacer for nav bar
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// === Top nav bar ===
@Composable
private fun TopNavBar(onNewClick: () -> Unit = {}, onGroupClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("于你", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text(" ♥", fontSize = 18.sp)
            }
            Text("你的专属情感陪伴", fontSize = 12.sp, color = TextLightGray)
        }
        // Group chat button
        IconButton(onClick = onGroupClick, modifier = Modifier.size(38.dp)) {
            Icon(Icons.Default.Groups, "群聊", tint = TextDark.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        }
    }
}

// === Welcome card ===
@Composable
private fun WelcomeCard(illustrationPath: String?) {
    val greeting = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
        in 0..6 -> "夜深了～"
        in 7..9 -> "早呀～"
        in 10..11 -> "上午好～"
        in 12..13 -> "中午好～"
        in 14..17 -> "下午好～"
        in 18..20 -> "傍晚好～"
        else -> "晚上好～"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSemiWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(greeting, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(" ♥", fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("今天想和我聊些什么呢？", fontSize = 15.sp, color = TextDark)
                Spacer(Modifier.height(2.dp))
                Text("无论开心还是难过，我都在听哦", fontSize = 13.sp, color = TextGray)
            }
            if (illustrationPath != null) {
                AsyncImage(
                    model = illustrationPath,
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFE0E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("♥", fontSize = 32.sp, color = AccentPink)
                }
            }
        }
    }
}

// === Tab bar ===
@Composable
private fun TabBar(tabs: List<String>, selectedIndex: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onTabSelected(index) }
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) TextDark else TextGray,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                if (selected) {
                    Canvas(modifier = Modifier.width(24.dp).height(3.dp)) {
                        drawCircle(AccentPink, radius = 1.5.dp.toPx(),
                            center = center.copy(x = size.width / 2))
                    }
                }
            }
        }
    }
}

// === Chat list item ===
@Composable
private fun ChatListItem(
    persona: Persona,
    relationTag: String,
    lastMessage: String,
    time: Long,
    unreadCount: Int,
    onClick: () -> Unit
) {
    val tagColor = relationColors[relationTag] ?: TextGray
    val tagBg = relationBgColors[relationTag] ?: Color(0x33CCCCCC)
    val timeStr = formatTime(time)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE0E5)),
                contentAlignment = Alignment.Center
            ) {
                if (persona.avatarImageUri != null) {
                    AsyncImage(
                        model = persona.avatarImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(persona.name.take(1), fontSize = 20.sp, color = AccentPink, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(14.dp))

            // Middle
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = tagBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            relationTag,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = tagColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    lastMessage.ifBlank { "开始聊天吧～" },
                    fontSize = 14.sp,
                    color = TextGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right
            Column(horizontalAlignment = Alignment.End) {
                Text(timeStr, fontSize = 12.sp, color = TextLightGray)
                if (unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF3B6F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unreadCount > 99) "99+" else "$unreadCount",
                            fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 84.dp, end = 18.dp))
}

@Composable
private fun GroupChatListItem(
    conversation: com.aicompanion.domain.model.Conversation,
    personas: List<Persona>,
    onClick: () -> Unit
) {
    val timeStr = formatTime(conversation.lastMessageAt)
    val memberNames = conversation.groupPersonaIds.mapNotNull { id -> personas.find { it.id == id }?.name }
    val nameText = if (memberNames.size <= 3) memberNames.joinToString("、") else "${memberNames.take(3).joinToString("、")}等${memberNames.size}人"

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFF9BAA), Color(0xFFFFC0CB)))),
                contentAlignment = Alignment.Center
            ) {
                Text("👥", fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(conversation.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(Modifier.width(8.dp))
                    Surface(color = Color(0x33FF9BAA), shape = RoundedCornerShape(6.dp)) {
                        Text("群聊", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp, color = Color(0xFFFF6B9B), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(nameText, fontSize = 12.sp, color = TextLightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conversation.lastMessagePreview.ifBlank { "开始群聊吧～" },
                    fontSize = 14.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(timeStr, fontSize = 12.sp, color = TextLightGray)
        }
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 84.dp, end = 18.dp))
}

// === Moment feed card ===
@Composable
private fun MomentFeedCard(moment: MomentEntry, persona: Persona) {
    val timeStr = formatTime(moment.createdAt)
    val tagColor = relationColors[persona.relationshipType] ?: TextGray

    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFFE0E5)),
                contentAlignment = Alignment.Center
            ) {
                if (persona.avatarImageUri != null) {
                    AsyncImage(
                        model = persona.avatarImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(persona.name.take(1), fontSize = 16.sp, color = AccentPink, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = tagColor)
                    Spacer(Modifier.width(6.dp))
                    moment.mood?.let {
                        Text(it, fontSize = 11.sp, color = TextLightGray)
                    }
                }
                Text(timeStr, fontSize = 11.sp, color = TextLightGray)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(moment.content, fontSize = 15.sp, color = TextDark, lineHeight = 22.sp)
        moment.imageUri?.let { uri ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = uri, contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
        moment.location?.let {
            Spacer(Modifier.height(2.dp))
            Text("📍 $it", fontSize = 12.sp, color = TextLightGray)
        }
    }
    HorizontalDivider(color = DividerColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 66.dp, end = 18.dp))
}

// === Experience feed card ===
@Composable
private fun ExperienceFeedCard(experience: ExperienceEntry, persona: Persona) {
    val dateStr = SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(experience.createdAt))
    val catColors = mapOf(
        "日常" to Color(0xFF81C784), "冒险" to Color(0xFFFFB74D),
        "奇遇" to Color(0xFF64B5F6), "回忆" to Color(0xFFCE93D8), "成长" to Color(0xFF4DD0E1)
    )
    val catColor = catColors[experience.category] ?: AccentPink
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Persona source
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFFFE0E5)),
                    contentAlignment = Alignment.Center
                ) {
                    if (persona.avatarImageUri != null) {
                        AsyncImage(
                            model = persona.avatarImageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(persona.name.take(1), fontSize = 12.sp, color = AccentPink, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(persona.name, fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Surface(color = catColor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                    Text("${experience.category} · $dateStr",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp, color = catColor, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(experience.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(4.dp))
            Text(
                if (expanded || experience.content.length <= 100) experience.content
                else experience.content.take(100) + "…",
                fontSize = 14.sp, color = TextDark.copy(alpha = 0.85f), lineHeight = 21.sp
            )
            if (experience.content.length > 100) {
                Text(
                    if (expanded) "收起" else "展开阅读",
                    fontSize = 13.sp, color = AccentPink, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val msgTime = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == msgTime.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        now.get(Calendar.YEAR) == msgTime.get(Calendar.YEAR) ->
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("yy/MM/dd", Locale.getDefault()).format(Date(timestamp))
    }
}
