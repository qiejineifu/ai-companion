package com.aicompanion.feature.chat.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aicompanion.core.ui.theme.*
import com.aicompanion.feature.live2d.Live2DComposeView
import com.aicompanion.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToPersonas: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToMemory: (() -> Unit)? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToConversations: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onNavigateToPersonaSettings: (() -> Unit)? = null,
    userAvatarUri: String? = null,
    personaMap: Map<String, com.aicompanion.domain.model.Persona> = emptyMap(),
    onSpeakMessage: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.streamState) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size)
        }
        if (state.streamState is StreamState.Streaming) {
            listState.animateScrollToItem(state.messages.size + 1)
        }
    }

    val isGroup = state.activeConversation?.isGroupChat == true

    Column(modifier = Modifier.fillMaxSize().background(ChatBg).imePadding()) {
        // === Top bar (plain Row, no Scaffold) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(topBarGradient))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
            } else {
                IconButton(onClick = { viewModel.processIntent(ChatIntent.ToggleSidebar) }) {
                    Icon(Icons.Default.Menu, "列表", tint = Color.White)
                }
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val groupAvatarUri = state.activeConversation?.avatarImageUri

            val groupAvatarPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let {
                    val input = context.contentResolver.openInputStream(it)
                    val ext = context.contentResolver.getType(it)?.let { t ->
                        if (t.contains("png")) ".png" else ".jpg"
                    } ?: ".jpg"
                    val dest = java.io.File(context.filesDir, "group_avatars/${state.activeConversation?.id}$ext")
                    dest.parentFile?.mkdirs()
                    input?.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
                    val newUri = dest.toURI().toString()
                    // Update conversation avatar
                    viewModel.updateConversationAvatar(state.activeConversation?.id ?: "", newUri)
                }
            }

            if (isGroup) {
                // Group chat avatar
                val avatarModifier = Modifier.size(38.dp).clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    .let { mod ->
                        if (groupAvatarUri == null) mod.background(Brush.horizontalGradient(listOf(Pink400, Pink600)))
                        else mod.background(Color.Transparent)
                    }
                    .clickable { groupAvatarPicker.launch("image/*") }
                Box(
                    modifier = avatarModifier,
                    contentAlignment = Alignment.Center
                ) {
                    if (groupAvatarUri != null) {
                        AsyncImage(
                            model = groupAvatarUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text("👥", fontSize = 18.sp)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .background(Pink200),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarUri = state.activePersona?.avatarImageUri
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(state.activePersona?.name?.take(1) ?: "♥", color = Pink600,
                            fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isGroup) {
                    Text(state.activeConversation?.title ?: "群聊", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    // Show group member names
                    val memberNames = state.activeConversation?.groupPersonaIds?.mapNotNull { id ->
                        personaMap[id]?.name
                    } ?: emptyList()
                    if (memberNames.isNotEmpty()) {
                        Text(memberNames.joinToString(" · "), color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(state.activePersona?.name ?: "AI 陪伴", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (state.activeApiProvider != null) {
                        Text("${state.activeApiProvider?.name} · ${state.activeApiProvider?.modelName}",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
            }

            IconButton(onClick = { viewModel.processIntent(ChatIntent.NewConversation) }) {
                Icon(Icons.Default.Add, "新建", tint = Color.White)
            }
            if (isGroup) {
                var showGroupMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showGroupMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多", tint = Color.White)
                    }
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("修改群聊名称") },
                            onClick = {
                                showGroupMenu = false
                                renameText = state.activeConversation?.title ?: "群聊"
                                showRename = true
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("更换群聊头像") },
                            onClick = {
                                showGroupMenu = false
                                groupAvatarPicker.launch("image/*")
                            },
                            leadingIcon = { Icon(Icons.Default.Image, null) }
                        )
                        HorizontalDivider(color = DividerPink)
                        DropdownMenuItem(
                            text = { Text("删除群聊", color = Pink600) },
                            onClick = {
                                showGroupMenu = false
                                viewModel.hardDeleteConversation(state.activeConversation?.id ?: "")
                                onBack?.invoke()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Pink600) }
                        )
                    }
                }
            } else {
                IconButton(onClick = onNavigateToPersonaSettings ?: onNavigateToSettings) {
                    Icon(Icons.Default.MoreVert, "更多", tint = Color.White)
                }
            }
        }

        // === Content ===
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val showWelcome = state.messages.isEmpty() && state.streamState !is StreamState.Streaming
            if (showWelcome && isGroup) {
                // Group chat empty state: show group members
                val groupPersonaIds = state.activeConversation?.groupPersonaIds ?: emptyList()
                val members = groupPersonaIds.mapNotNull { personaMap[it] }
                GroupMembersWelcome(members = members, onStartChat = {})
            } else if (showWelcome) {
                WelcomeWithCharacter(
                    persona = state.activePersona,
                    onSelectPersona = onNavigateToPersonas,
                    onConfigureApi = onNavigateToApiConfig
                )
            }
            if (!showWelcome) {
                    MessageList(
                        messages = state.messages,
                        streamState = state.streamState,
                        listState = listState,
                        personaName = state.activePersona?.name ?: "AI",
                        personaAvatarUri = state.activePersona?.avatarImageUri,
                        userAvatarUri = userAvatarUri,
                        currentStickerUri = state.currentStickerUri,
                        branchSelections = state.branchSelections,
                        isGroupChat = state.activeConversation?.isGroupChat == true,
                        personaMap = personaMap,
                        onRegenerate = { msgId -> viewModel.processIntent(ChatIntent.Regenerate(msgId)) },
                        onSwitchBranch = { key, idx -> viewModel.processIntent(ChatIntent.SwitchBranch(key, idx)) },
                        onStartReply = { msgId -> viewModel.processIntent(ChatIntent.StartReply(msgId)) },
                        onSpeakMessage = onSpeakMessage
                    )
                }
            if (state.error != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    containerColor = Pink600, contentColor = Color.White,
                    action = {
                        TextButton(onClick = { viewModel.processIntent(ChatIntent.DismissError) }) {
                            Text("关闭", color = Color.White)
                        }
                    }
                ) { Text(state.error!!) }
            }
        }

        // Rename dialog (group chat)
        if (showRename) {
            AlertDialog(
                onDismissRequest = { showRename = false },
                title = { Text("修改群聊名称") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("群聊名称") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.renameConversation(state.activeConversation?.id ?: "", renameText)
                        showRename = false
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { showRename = false }) { Text("取消") } }
            )
        }

        // === Character card (compact, when empty, only for single chat) ===
        val persona = state.activePersona
        if (persona != null && state.messages.isEmpty() && state.activeConversation?.isGroupChat != true) {
            CharacterProfileCard(persona = persona)
        }

        // === Reply target bar ===
        val replyTarget = state.replyTarget
        if (replyTarget != null) {
            ReplyBar(
                senderName = if (state.activeConversation?.isGroupChat == true) replyTarget.senderPersonaName else null,
                content = replyTarget.content,
                onCancel = { viewModel.processIntent(ChatIntent.CancelReply) }
            )
        }

        // === Input bar ===
        ChatInputBar(
            text = state.inputText,
            onTextChange = { viewModel.processIntent(ChatIntent.UpdateInput(it)) },
            onSend = { viewModel.processIntent(ChatIntent.SendMessage(it)) },
            onVoice = { viewModel.processIntent(ChatIntent.SendVoice) },
            isRecording = state.isRecording,
            isStreaming = state.streamState is StreamState.Streaming,
            onStop = { viewModel.processIntent(ChatIntent.StopGeneration) },
            onMore = onNavigateToPersonas,
            onCallClick = { viewModel.processIntent(ChatIntent.ToggleCallMode) }
        )
    }

    // === Call mode overlay ===
    if (state.callMode) {
        val persona = state.activePersona
        if (persona != null) {
            CallModeOverlay(
                personaName = persona.name,
                avatarUri = persona.avatarImageUri,
                isRecording = state.isRecording,
                isSpeaking = state.streamState is StreamState.Streaming,
                currentEmotion = state.currentEmotion,
                live2DModel = state.live2DModel,
                onHangUp = { viewModel.processIntent(ChatIntent.ToggleCallMode) },
                onMicToggle = { viewModel.processIntent(ChatIntent.SendVoice) }
            )
        }
    }

    // Sidebar overlay
    if (state.showSidebar) {
        ConversationSidebar(
            conversations = state.conversations,
            activeId = state.activeConversation?.id,
            onSelect = { viewModel.processIntent(ChatIntent.SelectConversation(it)) },
            onNew = { viewModel.processIntent(ChatIntent.NewConversation) },
            onDismiss = { viewModel.processIntent(ChatIntent.ToggleSidebar) }
        )
    }
}

// --- Character profile card ---
@Composable
private fun CharacterProfileCard(persona: Persona) {
    Surface(color = Pink50, modifier = Modifier.fillMaxWidth(), shadowElevation = 1.dp) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Pink200), contentAlignment = Alignment.Center) {
                Text(persona.name.take(1), fontSize = 24.sp, color = Pink600, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(persona.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                if (persona.description.isNotBlank()) {
                    Text(persona.description, fontSize = 13.sp, color = TextGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (persona.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        persona.tags.take(3).forEach { tag ->
                            Text("#$tag", fontSize = 11.sp, color = Pink500,
                                modifier = Modifier.background(Pink100, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- Group members welcome ---
@Composable
private fun GroupMembersWelcome(members: List<Persona>, onStartChat: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(20.dp))
                Text("👥", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text("群聊", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("${members.size} 位成员", color = TextGray, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                // Member avatars in a row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    members.forEach { member ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(Pink400, Pink600))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val avatarUri = member.avatarImageUri
                                if (avatarUri != null) {
                                    AsyncImage(
                                        model = avatarUri, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(member.name.take(1), fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(member.name, fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                            Text(member.speakingStyle, fontSize = 11.sp, color = TextGray)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                DividerLine()
                Spacer(Modifier.height(12.dp))
                Text("在下方输入消息开始群聊", color = TextGray, fontSize = 14.sp)
            }
        }
    }
}

// --- Welcome ---
@Composable
private fun WelcomeWithCharacter(persona: Persona?, onSelectPersona: () -> Unit, onConfigureApi: () -> Unit) {
    if (persona == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("♥", fontSize = 48.sp, color = Pink400)
                Spacer(Modifier.height(16.dp))
                Text("选择一个角色开始对话吧", color = TextGray)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onSelectPersona, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("选择人设") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onConfigureApi) { Text("配置 API") }
            }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Pink400, Pink600))), contentAlignment = Alignment.Center) {
                        Text(persona.name.take(2), fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(persona.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    if (persona.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(persona.description, fontSize = 14.sp, color = TextGray)
                    }
                    if (persona.scenario.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("「${persona.scenario}」", fontSize = 13.sp, color = Pink500,
                            modifier = Modifier.background(Pink100, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                    if (persona.tags.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            persona.tags.forEach { tag ->
                                Text("#$tag", color = Pink500, fontSize = 13.sp,
                                    modifier = Modifier.background(Pink50, RoundedCornerShape(6.dp))
                                        .border(1.dp, Pink200, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        }
                    }
                    if (persona.firstMessage.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        DividerLine()
                        Spacer(Modifier.height(12.dp))
                        Text(persona.firstMessage, fontSize = 14.sp, color = TextGray,
                            modifier = Modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(16.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(DividerPink))
        Text(" ✦ ", color = Pink400, fontSize = 12.sp)
        Box(Modifier.weight(1f).height(1.dp).background(DividerPink))
    }
}

// --- Message grouping ---
private fun shouldShowAvatar(index: Int, messages: List<MessageUi>, isGroupChat: Boolean): Boolean {
    if (index == 0) return true
    val cur = messages[index]
    val prev = messages[index - 1]
    if (cur.role != prev.role) return true
    if (isGroupChat && cur.senderPersonaId != prev.senderPersonaId) return true
    if (cur.createdAt - prev.createdAt > 2 * 60 * 1000) return true
    return false
}

// --- Bubble stem triangle ---
@Composable
private fun StemTriangle(isUser: Boolean, color: Color, modifier: Modifier = Modifier) {
    val stemColor = color
    Canvas(modifier = modifier.size(width = 7.dp, height = 14.dp)) {
        val path = if (isUser) {
            Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2)
                lineTo(size.width, size.height)
                close()
            }
        } else {
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2)
                lineTo(0f, size.height)
                close()
            }
        }
        drawPath(path, color = stemColor)
    }
}

// --- Message list ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageList(
    messages: List<MessageUi>,
    streamState: StreamState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    personaName: String,
    personaAvatarUri: String? = null,
    userAvatarUri: String? = null,
    currentStickerUri: String? = null,
    branchSelections: Map<String, Int> = emptyMap(),
    isGroupChat: Boolean = false,
    personaMap: Map<String, Persona> = emptyMap(),
    onRegenerate: (String) -> Unit = {},
    onSwitchBranch: (String, Int) -> Unit = { _, _ -> },
    onStartReply: (String) -> Unit = {},
    onSpeakMessage: (String) -> Unit = {}
) {
    // Deduplicate branches
    val displayMessages = remember(messages, branchSelections) {
        val groups = LinkedHashMap<String, MutableList<MessageUi>>()
        for (msg in messages) {
            groups.getOrPut(msg.branchKey) { mutableListOf() }.add(msg)
        }
        groups.mapNotNull { (key, variants) ->
            val selectedIdx = branchSelections[key] ?: variants.maxByOrNull { it.branchIndex }?.branchIndex ?: 0
            variants.find { it.branchIndex == selectedIdx } ?: variants.firstOrNull()
        }
    }

    val branchCounts = remember(messages) {
        messages.groupBy { it.branchKey }.mapValues { it.value.size }
    }
    val timeFormat = remember { SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()) }
    var lastTimeShown by remember { mutableStateOf(0L) }
    var lastDate by remember { mutableStateOf("") }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        displayMessages.forEachIndexed { index, message ->
            val msgTime = message.createdAt
            val dateStr = SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(msgTime))

            if (index == 0 || dateStr != lastDate) {
                item(key = "date_$dateStr") {
                    Text(dateStr, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = TextGray, fontSize = 13.sp)
                }
                lastDate = dateStr
                lastTimeShown = 0L
            }
            if (index > 0 && msgTime - lastTimeShown > 30 * 60 * 1000) {
                item(key = "time_${msgTime}") {
                    Text(timeFormat.format(Date(msgTime)), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = TextGray, fontSize = 12.sp)
                }
            }
            lastTimeShown = msgTime

            val isUser = message.role == "user"
            val showAvatar = shouldShowAvatar(index, displayMessages, isGroupChat)

            item(key = message.id, contentType = if (isUser) "user" else "ai") {
                var showContextMenu by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (!isUser) Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { showContextMenu = true }
                            ) else Modifier),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        // --- AI message: avatar + stem + bubble ---
                        if (!isUser) {
                            if (showAvatar) {
                                val isGroupMsg = isGroupChat && message.senderPersonaId != null
                                val senderPersona = if (isGroupMsg) personaMap[message.senderPersonaId] else null
                                val senderName = senderPersona?.name
                                val senderAvatar = senderPersona?.avatarImageUri
                                val displayName = senderName ?: personaName

                                val avatarColors = listOf(
                                    Pink400, Color(0xFF64B5F6), Color(0xFF81C784),
                                    Color(0xFFFFB74D), Purple400, Color(0xFF4DD0E1)
                                )
                                val colorIndex = kotlin.math.abs(displayName.hashCode()) % avatarColors.size

                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape)
                                            .border(1.5.dp, Pink200, CircleShape)
                                            .background(if (isGroupMsg) avatarColors[colorIndex] else Pink200),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (senderAvatar != null) {
                                            AsyncImage(model = senderAvatar, contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        } else if (!isGroupMsg && personaAvatarUri != null) {
                                            AsyncImage(model = personaAvatarUri, contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                        } else {
                                            Text(displayName.take(1), fontSize = 13.sp,
                                                color = if (isGroupMsg) Color.White else Pink600,
                                                fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (isGroupMsg && senderName != null) {
                                        Text(senderName, fontSize = 10.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                            } else {
                                // Indent for grouped messages (same width as avatar column)
                                Spacer(Modifier.width(42.dp))
                            }

                            // Stem pointing right
                            StemTriangle(isUser = false, color = Color.White, modifier = Modifier.padding(top = 12.dp))

                            // Bubble
                            Column(modifier = Modifier.widthIn(max = 260.dp)) {
                                MessageBubble(message = message, isUser = false, currentStickerUri = currentStickerUri, onSpeakMessage = onSpeakMessage)
                                MessageFooter(
                                    message = message, isUser = false,
                                    branchCount = branchCounts[message.branchKey] ?: 1,
                                    currentIdx = branchSelections[message.branchKey] ?: message.branchIndex,
                                    isGroupChat = isGroupChat, personaMap = personaMap,
                                    onRegenerate = onRegenerate,
                                    onSwitchBranch = onSwitchBranch
                                )
                            }
                        }

                        // --- User message: bubble + stem + avatar ---
                        if (isUser) {
                            Column(modifier = Modifier.widthIn(max = 260.dp), horizontalAlignment = Alignment.End) {
                                MessageBubble(message = message, isUser = true)
                            }

                            // Stem pointing left
                            StemTriangle(isUser = true, color = Pink400, modifier = Modifier.padding(top = 12.dp))

                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                                    .border(1.5.dp, Pink200, CircleShape)
                                    .background(Purple100),
                                contentAlignment = Alignment.Center
                            ) {
                                if (userAvatarUri != null) {
                                    AsyncImage(model = userAvatarUri, contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                } else {
                                    Text("我", fontSize = 11.sp, color = Purple400, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (!isUser) {
                        DropdownMenu(expanded = showContextMenu, onDismissRequest = { showContextMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("引用回复") },
                                onClick = {
                                    showContextMenu = false
                                    onStartReply(message.id)
                                },
                                leadingIcon = { Icon(Icons.Default.Reply, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("朗读此句 🔊") },
                                onClick = {
                                    showContextMenu = false
                                    onSpeakMessage(message.content)
                                },
                                leadingIcon = { Icon(Icons.Default.VolumeUp, null) }
                            )
                        }
                    }
                }
            }
        }

        // Streaming bubble
        if (streamState is StreamState.Streaming) {
            item(key = "streaming") {
                Row(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape)
                        .border(1.5.dp, Pink200, CircleShape)
                        .background(Pink200),
                        contentAlignment = Alignment.Center) {
                        if (personaAvatarUri != null) {
                            AsyncImage(model = personaAvatarUri, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Text(personaName.take(1), fontSize = 14.sp, color = Pink600, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    StemTriangle(isUser = false, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    Column(modifier = Modifier.widthIn(max = 260.dp)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                            color = Color.White, shadowElevation = 0.5.dp
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text("${streamState.partialText}▊", fontSize = 15.sp, color = TextDark, lineHeight = 22.sp)
                            }
                        }
                    }
                }
            }
        }
        item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
    }
}

// --- Single message bubble ---
@Composable
private fun MessageBubble(message: MessageUi, isUser: Boolean, currentStickerUri: String? = null, onSpeakMessage: (String) -> Unit = {}) {
    val bubbleShape = if (isUser) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    val bubbleColor = if (isUser) Pink400 else Color.White

    // Sticker
    val stickerToShow = if (!isUser) (message.stickerUri ?: currentStickerUri) else null
    if (stickerToShow != null) {
        AsyncImage(
            model = stickerToShow, contentDescription = "表情",
            modifier = Modifier.width(120.dp).aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)).padding(bottom = 4.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }

    Surface(shape = bubbleShape, color = bubbleColor, shadowElevation = if (isUser) 2.dp else 0.5.dp) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (isUser) {
                Text(message.content, color = Color.White, fontSize = 15.sp, lineHeight = 22.sp)
            } else {
                MarkdownMsg(text = message.content + if (message.isStreaming) "▊" else "")
            }
        }
    }
}

// --- Branch & regenerate controls ---
@Composable
private fun MessageFooter(
    message: MessageUi,
    isUser: Boolean,
    branchCount: Int,
    currentIdx: Int,
    isGroupChat: Boolean,
    personaMap: Map<String, Persona>,
    onRegenerate: (String) -> Unit,
    onSwitchBranch: (String, Int) -> Unit
) {
    if (isUser || message.isStreaming) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onRegenerate(message.id) }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Refresh, "重新生成", tint = TextGray, modifier = Modifier.size(16.dp))
        }
        if (branchCount > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Pink50)
                    .clickable { onSwitchBranch(message.branchKey, (currentIdx + 1) % branchCount) }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("${currentIdx + 1}/$branchCount", fontSize = 11.sp, color = Pink500)
                Icon(Icons.Default.ChevronRight, null, tint = Pink400, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// --- Reply bar (above input, shows quoted message) ---
@Composable
private fun ReplyBar(senderName: String?, content: String, onCancel: () -> Unit) {
    Surface(color = Pink50, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pink left border indicator
            Box(modifier = Modifier.width(3.dp).height(36.dp).background(Pink400, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (senderName != null) {
                    Text(senderName, fontSize = 11.sp, color = Pink500, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = if (senderName != null) "「$content」" else content,
                    fontSize = 13.sp, color = TextDark,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "取消引用", tint = TextGray, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// --- Call mode overlay ---
@Composable
private fun CallModeOverlay(
    personaName: String,
    avatarUri: String?,
    isRecording: Boolean,
    isSpeaking: Boolean,
    currentEmotion: com.aicompanion.core.common.Emotion = com.aicompanion.core.common.Emotion.NEUTRAL,
    live2DModel: com.aicompanion.feature.live2d.Live2DModel? = null,
    onHangUp: () -> Unit,
    onMicToggle: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Pink600.copy(alpha = 0.95f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // Name and status
            Text(personaName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSpeaking) "对方正在说话..." else if (isRecording) "正在聆听..." else "通话中...",
                color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp
            )

            Spacer(Modifier.height(20.dp))

            // Static avatar (Live2D disabled for crash debugging)
            Box(
                modifier = Modifier.size(160.dp).clip(CircleShape)
                    .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .background(Pink200),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    AsyncImage(model = avatarUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Text(personaName.take(1), fontSize = 48.sp, color = Pink600, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(20.dp))

            // Status indicator (no animation - avoids Honor hwui crash)
            if (isSpeaking || isRecording) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }

            Spacer(Modifier.height(24.dp))

            // Bottom controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                modifier = Modifier.padding(bottom = 60.dp)
            ) {
                FloatingActionButton(
                    onClick = onMicToggle,
                    containerColor = if (isRecording) Pink400 else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)
                    )
                }
                FloatingActionButton(
                    onClick = onHangUp,
                    containerColor = Color(0xFFE53935),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

// --- Markdown ---
@Composable
private fun MarkdownMsg(text: String) {
    val annotated = remember(text) { parseMarkdown(text) }
    Text(text = annotated, fontSize = 15.sp, lineHeight = 22.sp)
}

private fun parseMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val chars = text.toCharArray()
    while (i < chars.size) {
        when {
            chars.size > i + 3 && String(chars, i, 3) == "```" -> {
                val end = text.indexOf("```", i + 3)
                val code = if (end > i) text.substring(i + 3, end).trimStart('\n') else text.substring(i + 3)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = Pink50, color = Pink700)) { append(code) }
                i = if (end > i) end + 3 else chars.size
            }
            chars.size > i + 2 && String(chars, i, 2) == "**" -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextDark)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else { append(chars[i]); i++ }
            }
            chars[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, background = Pink100)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else { append(chars[i]); i++ }
            }
            else -> { withStyle(SpanStyle(color = TextDark)) { append(chars[i]) }; i++ }
        }
    }
}

// --- Input bar ---
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
    isRecording: Boolean,
    isStreaming: Boolean,
    onStop: () -> Unit,
    onMore: () -> Unit,
    onCallClick: (() -> Unit)? = null
) {
    Surface(color = Color.White, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onCallClick != null) {
                IconButton(onClick = onCallClick, modifier = Modifier.size(42.dp).clip(CircleShape).background(Pink100)) {
                    Icon(Icons.Default.Call, "电话", tint = Pink500, modifier = Modifier.size(22.dp))
                }
            } else {
                IconButton(onClick = onMore, modifier = Modifier.size(42.dp).clip(CircleShape).background(Pink100)) {
                    Icon(Icons.Default.Add, "更多", tint = Pink500, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Pink50)
                    .then(if (text.isNotEmpty()) Modifier.border(1.5.dp, Pink200, RoundedCornerShape(22.dp)) else Modifier)
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                if (text.isEmpty()) Text("说点什么吧...", color = TextGray.copy(alpha = 0.6f), fontSize = 15.sp)
                BasicTextField(value = text, onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextDark, fontSize = 15.sp),
                    cursorBrush = SolidColor(Pink500), modifier = Modifier.fillMaxWidth(), maxLines = 4, singleLine = false)
            }
            Spacer(Modifier.width(8.dp))
            if (isStreaming) {
                IconButton(onClick = onStop, modifier = Modifier.size(42.dp).clip(CircleShape).background(Pink600)) {
                    Icon(Icons.Default.Stop, "停止", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            } else {
                IconButton(onClick = onVoice, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Mic, "语音", tint = if (isRecording) Pink600 else TextGray, modifier = Modifier.size(22.dp))
                }
                IconButton(
                    onClick = { if (text.isNotBlank()) { onSend(text.trim()); onTextChange("") } },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                        .then(if (text.isNotBlank()) Modifier.background(Pink500) else Modifier)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "发送",
                        tint = if (text.isNotBlank()) Color.White else Pink200, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// --- Sidebar ---
@Composable
fun ConversationSidebar(
    conversations: List<com.aicompanion.domain.model.Conversation>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize().clickable { onDismiss() }, color = Color.Black.copy(alpha = 0.4f)) {}
        Surface(modifier = Modifier.fillMaxWidth(0.78f).fillMaxHeight(), color = Color.White) {
            Column {
                Surface(color = Pink500) {
                    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("对话列表", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onNew(); onDismiss() }) { Icon(Icons.Default.Add, "新对话", tint = Color.White) }
                    }
                }
                if (conversations.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无对话", color = TextGray) }
                } else {
                    LazyColumn {
                        itemsIndexed(conversations, key = { _, c -> c.id }) { _, conv ->
                            ListItem(
                                headlineContent = { Text(conv.title, maxLines = 1, fontSize = 15.sp, color = TextDark) },
                                supportingContent = {
                                    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
                                    Text(fmt.format(Date(conv.lastMessageAt)), fontSize = 12.sp, color = TextGray)
                                },
                                modifier = Modifier.clickable { onSelect(conv.id); onDismiss() },
                                colors = if (conv.id == activeId) ListItemDefaults.colors(containerColor = Pink50) else ListItemDefaults.colors()
                            )
                        }
                    }
                }
            }
        }
    }
}
