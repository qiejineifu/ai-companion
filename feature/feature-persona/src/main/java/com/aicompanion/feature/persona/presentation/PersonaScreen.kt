package com.aicompanion.feature.persona.presentation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.util.*
import java.util.concurrent.TimeUnit

// === Colors ===
private val BgStart = Color(0xFFFFF0F3)
private val BgEnd = Color(0xFFFFF8F9)
private val AccentPink = Color(0xFFFF6B9B)
private val TextDark = Color(0xFF2D1B2E)
private val TextMid = Color(0xFF6B5B6E)
private val TextLight = Color(0xFFB0A0B0)
private val ChipBg = Color(0x1AFF6B9B)
private val OnlineGreen = Color(0xFF6BD4A0)

private val statusList = listOf(
    "💭 胡思乱想中", "📚 学习中", "😴 发呆中", "😢 emo中",
    "🎵 听歌中", "🚶 散步中", "📖 阅读中", "🎮 游戏中",
    "☕ 摸鱼中", "💪 运动打卡", "🎬 追剧中", "🍜 干饭中",
    "🌈 元气满满", "🌙 想睡觉", "💼 忙碌中", "🌸 赏花中"
)

private fun getDailyStatus(personaId: String): String {
    val dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    val idx = (personaId.hashCode() + dayOfYear).mod(statusList.size).let { if (it < 0) it + statusList.size else it }
    return statusList[idx]
}

// === Category filter values ===
private val categories = listOf("全部", "恋人", "知己", "闺蜜", "学长", "妹妹", "青梅", "同事", "网友", "朋友")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    viewModel: PersonaViewModel,
    onBack: () -> Unit,
    onSelectPersona: (String) -> Unit = {},
    onNavigateToMarket: () -> Unit = {},
    onNavigateToGuidedBuilder: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("全部") }
    var isGridView by remember { mutableStateOf(true) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importFromTavernCard(it, context) } }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearImportMessage()
        }
    }

    val allPersonas = state.personas
    val filtered = if (selectedCategory == "全部") allPersonas
    else allPersonas.filter { it.relationshipType == selectedCategory }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgStart, BgEnd)))
    ) {
        // === Top nav bar ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextDark, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(2.dp))

            // Title area
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("角色宇宙", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(" ✨✨", fontSize = 13.sp)
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = TextLight)) { append("你已拥有 ") }
                        withStyle(SpanStyle(color = AccentPink, fontWeight = FontWeight.Bold)) {
                            append("${allPersonas.size}")
                        }
                        withStyle(SpanStyle(color = TextLight)) { append(" 位陪伴角色") }
                    },
                    fontSize = 13.sp
                )
            }

            // Market icon
            IconButton(onClick = onNavigateToMarket, modifier = Modifier.size(38.dp)) {
                Text("🏪", fontSize = 20.sp)
            }
            // Bunny icon (import tavern card)
            IconButton(onClick = { importLauncher.launch("image/*") }, modifier = Modifier.size(38.dp)) {
                Text("🐰", fontSize = 20.sp)
            }
            // + Create button
            Button(
                onClick = { viewModel.showCreateDialog() },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("创建角色", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            // Guided builder button
            IconButton(onClick = onNavigateToGuidedBuilder, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.AutoAwesome, "引导创建", tint = AccentPink, modifier = Modifier.size(20.dp))
            }
        }

        // === Category chips ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show only categories that have personas or are "全部"
            val activeCategories = categories.filter { cat ->
                cat == "全部" || allPersonas.any { it.relationshipType == cat }
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeCategories.size) { index ->
                    val cat = activeCategories[index]
                    val selected = cat == selectedCategory
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                cat,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) Color.White else TextDark
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPink,
                            containerColor = Color.White.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = null
                    )
                }
            }

            // Grid/list toggle
            IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isGridView) Icons.Default.GridView else Icons.Default.ViewList,
                    "切换视图", tint = TextLight, modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // === Persona grid/list ===
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🐰", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有角色", color = TextLight, fontSize = 15.sp)
                    Text("点击右上角「创建角色」开始吧", color = TextLight, fontSize = 13.sp)
                }
            }
        } else if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { persona ->
                    PersonaGridCard(
                        persona = persona,
                        onClick = { onSelectPersona(persona.id) },
                        onEdit = { viewModel.showEditDialog(persona) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered.size, key = { filtered[it].id }) { index ->
                    val persona = filtered[index]
                    PersonaListCard(
                        persona = persona,
                        onClick = { onSelectPersona(persona.id) },
                        onEdit = { viewModel.showEditDialog(persona) },
                        onDuplicate = { viewModel.duplicatePersona(persona) },
                        onDelete = { viewModel.deletePersona(persona.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // === Edit dialog ===
    if (state.showEditDialog && state.editingPersona != null) {
        PersonaEditDialog(
            persona = state.editingPersona!!,
            onPersonaChange = { viewModel.updateEditingPersona(it) },
            onSave = { viewModel.savePersona() },
            onDismiss = { viewModel.dismissDialog() },
            newTraitKey = state.newTraitKey,
            newTraitValue = state.newTraitValue,
            onTraitKeyChange = { viewModel.setTraitKey(it) },
            onTraitValueChange = { viewModel.setTraitValue(it) },
            onAddTrait = { viewModel.addTrait() },
            onRemoveTrait = { viewModel.removeTrait(it) },
            newExampleChat = state.newExampleChat,
            newTag = state.newTag,
            onNewExampleChatChange = { viewModel.setNewExampleChat(it) },
            onAddExampleChat = { viewModel.addExampleChat() },
            onRemoveExampleChat = { viewModel.removeExampleChat(it) },
            onNewTagChange = { viewModel.setNewTag(it) },
            onAddTag = { viewModel.addTag() },
            onRemoveTag = { viewModel.removeTag(it) }
        )
    }
}

@Composable
private fun PersonaGridCard(
    persona: com.aicompanion.domain.model.Persona,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val days = computeCompanionDays(persona.createdAt)
    val tagText = "${persona.speakingStyle} · ${persona.relationshipType}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background image
            if (persona.avatarImageUri != null) {
                AsyncImage(
                    model = persona.avatarImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Placeholder gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFE0E8), Color(0xFFFFD0DC))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(persona.name.take(1), fontSize = 48.sp,
                        color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                }
            }

            // Gradient overlay (dark at bottom for text readability)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x00000000),
                                Color(0x33000000),
                                Color(0xAA000000)
                            )
                        )
                    )
            )

            // Top-left: daily status badge
            val dailyStatus = remember(persona.id) { getDailyStatus(persona.id) }
            Surface(
                color = Color.White.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    dailyStatus,
                    fontSize = 10.sp,
                    color = TextDark,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Top-right: edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.MoreHoriz,
                    "编辑",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Bottom area: name + tags + days
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    persona.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tagText,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "我们已相伴 $days 天",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun PersonaListCard(
    persona: com.aicompanion.domain.model.Persona,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val days = computeCompanionDays(persona.createdAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with image or initial
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFFE0E8)),
                contentAlignment = Alignment.Center
            ) {
                if (persona.avatarImageUri != null) {
                    AsyncImage(
                        model = persona.avatarImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        persona.name.take(1),
                        fontSize = 24.sp,
                        color = AccentPink.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${persona.speakingStyle} · ${persona.relationshipType}",
                    fontSize = 13.sp,
                    color = TextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val dailyStatus = remember(persona.id) { getDailyStatus(persona.id) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "我们已相伴 $days 天",
                        fontSize = 12.sp,
                        color = TextLight
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        dailyStatus,
                        fontSize = 11.sp,
                        color = AccentPink.copy(alpha = 0.7f)
                    )
                }
            }

            // Actions
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "编辑", tint = TextLight, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, "复制", tint = TextLight, modifier = Modifier.size(18.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = AccentPink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun computeCompanionDays(createdAt: Long): Int {
    val diff = System.currentTimeMillis() - createdAt
    return (TimeUnit.MILLISECONDS.toDays(diff) + 1).toInt().coerceAtLeast(1)
}

// === Keeping existing PersonaEditDialog ===
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PersonaEditDialog(
    persona: com.aicompanion.domain.model.Persona,
    onPersonaChange: (com.aicompanion.domain.model.Persona) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    newTraitKey: String, newTraitValue: String,
    onTraitKeyChange: (String) -> Unit, onTraitValueChange: (String) -> Unit,
    onAddTrait: () -> Unit, onRemoveTrait: (Int) -> Unit,
    newExampleChat: String, newTag: String,
    onNewExampleChatChange: (String) -> Unit, onAddExampleChat: () -> Unit, onRemoveExampleChat: (Int) -> Unit,
    onNewTagChange: (String) -> Unit, onAddTag: () -> Unit, onRemoveTag: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (persona.name.isBlank()) "创建角色卡" else "编辑 ${persona.name}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = persona.name, onValueChange = { onPersonaChange(persona.copy(name = it)) },
                    label = { Text("角色名称") }, singleLine = true
                )
                OutlinedTextField(
                    value = persona.description, onValueChange = { onPersonaChange(persona.copy(description = it)) },
                    label = { Text("一句话简介") }, singleLine = true,
                    placeholder = { Text("她是一个...") }
                )
                OutlinedTextField(
                    value = persona.scenario, onValueChange = { onPersonaChange(persona.copy(scenario = it)) },
                    label = { Text("场景/世界观") }, minLines = 2,
                    placeholder = { Text("故事发生的背景...") }
                )
                OutlinedTextField(
                    value = persona.firstMessage, onValueChange = { onPersonaChange(persona.copy(firstMessage = it)) },
                    label = { Text("开场白") }, minLines = 1,
                    placeholder = { Text("角色在对话开始时说的第一句话") }
                )
                OutlinedTextField(
                    value = persona.systemPrompt,
                    onValueChange = { onPersonaChange(persona.copy(systemPrompt = it)) },
                    label = { Text("角色定义 (System Prompt)") }, minLines = 2,
                    placeholder = { Text("你是谁、你的性格、说话方式、限制条件...") }
                )
                OutlinedTextField(
                    value = persona.userDisplayName,
                    onValueChange = { onPersonaChange(persona.copy(userDisplayName = it)) },
                    label = { Text("用户称呼") }, singleLine = true,
                    placeholder = { Text("AI 怎么叫你？如：小明、主人") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = persona.speakingStyle,
                        onValueChange = { onPersonaChange(persona.copy(speakingStyle = it)) },
                        label = { Text("说话风格") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = persona.relationshipType,
                        onValueChange = { onPersonaChange(persona.copy(relationshipType = it)) },
                        label = { Text("关系定位") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                }

                Text("示例对话", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newExampleChat, onValueChange = onNewExampleChatChange,
                        label = { Text("添加示例") }, singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text("用户: 你好呀 / 角色: 你来啦~") }
                    )
                    IconButton(onClick = onAddExampleChat, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
                persona.exampleChats.forEachIndexed { index, chat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chat, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        IconButton(onClick = { onRemoveExampleChat(index) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "删除", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Text("标签", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTag, onValueChange = onNewTagChange,
                        label = { Text("添加标签") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAddTag, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
                if (persona.tags.isNotEmpty()) {
                    FlowRow {
                        persona.tags.forEachIndexed { index, tag ->
                            InputChip(
                                selected = false, onClick = { onRemoveTag(index) },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, "移除", Modifier.size(16.dp)) }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }

                Text("性格特征", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTraitKey, onValueChange = onTraitKeyChange,
                        label = { Text("特征") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = newTraitValue, onValueChange = onTraitValueChange,
                        label = { Text("描述") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAddTrait, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
                FlowRow {
                    persona.traits.forEachIndexed { index, trait ->
                        InputChip(
                            selected = false, onClick = { onRemoveTrait(index) },
                            label = { Text("${trait.key}: ${trait.value}") },
                            trailingIcon = { Icon(Icons.Default.Close, "移除", Modifier.size(16.dp)) }
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
