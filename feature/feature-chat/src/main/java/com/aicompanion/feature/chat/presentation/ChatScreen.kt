package com.aicompanion.feature.chat.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicompanion.core.common.Emotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToPersonas: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToMemory: (() -> Unit)? = null,
    onNavigateToSettings: () -> Unit,
    onNavigateToConversations: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll on new messages
    LaunchedEffect(state.messages.size, state.streamState) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.activePersona?.name ?: "AI 陪伴",
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (state.activeApiProvider != null) {
                            Text(
                                "${state.activeApiProvider?.name} / ${state.activeApiProvider?.modelName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.processIntent(ChatIntent.ToggleSidebar) }) {
                        Icon(Icons.Default.Menu, "会话列表")
                    }
                },
                actions = {
                    // New conversation
                    IconButton(onClick = {
                        viewModel.processIntent(ChatIntent.NewConversation)
                    }) {
                        Icon(Icons.Default.Add, "新建对话")
                    }
                    // Current emotion indicator
                    if (state.currentEmotion != Emotion.NEUTRAL) {
                        Text(
                            state.currentEmotion.emoji,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(onClick = {
                        viewModel.processIntent(ChatIntent.ToggleVoiceMode)
                    }) {
                        Icon(
                            if (state.voiceMode) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            "语音模式"
                        )
                    }
                    if (onNavigateToMemory != null) {
                        IconButton(onClick = onNavigateToMemory) {
                            Icon(Icons.Default.Psychology, "记忆")
                        }
                    }
                    if (onNavigateToConversations != null) {
                        IconButton(onClick = onNavigateToConversations) {
                            Icon(Icons.Default.ChatBubbleOutline, "对话")
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = state.inputText,
                onTextChange = { viewModel.processIntent(ChatIntent.UpdateInput(it)) },
                onSend = { viewModel.processIntent(ChatIntent.SendMessage(it)) },
                onVoice = { viewModel.processIntent(ChatIntent.SendVoice) },
                isRecording = state.isRecording,
                isStreaming = state.streamState is StreamState.Streaming,
                onStop = { viewModel.processIntent(ChatIntent.StopGeneration) }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.conversations.isEmpty() && state.messages.isEmpty() -> {
                    // Welcome screen
                    WelcomeContent(
                        personaName = state.activePersona?.name,
                        onSelectPersona = onNavigateToPersonas,
                        onConfigureApi = onNavigateToApiConfig
                    )
                }
                state.messages.isEmpty() -> {
                    // No messages yet
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("开始和 ${state.activePersona?.name ?: "AI"} 聊天吧",
                                style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = onNavigateToPersonas) {
                                Text("切换人设")
                            }
                        }
                    }
                }
                else -> {
                    MessageList(
                        messages = state.messages,
                        streamState = state.streamState,
                        listState = listState
                    )
                }
            }

            // Error snackbar
            if (state.error != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.processIntent(ChatIntent.DismissError) }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(state.error!!)
                }
            }
        }
    }

    // Sidebar drawer
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

@Composable
private fun WelcomeContent(
    personaName: String?,
    onSelectPersona: () -> Unit,
    onConfigureApi: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("欢迎使用 AI 陪伴", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            Text(
                "选择一个 AI 角色，配置 API，开始对话",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onSelectPersona) {
                Icon(Icons.Default.Person, null)
                Spacer(Modifier.width(8.dp))
                Text("选择人设")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onConfigureApi) {
                Icon(Icons.Default.Api, null)
                Spacer(Modifier.width(8.dp))
                Text("配置 API")
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<MessageUi>,
    streamState: StreamState,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }

        // Streaming message
        if (streamState is StreamState.Streaming) {
            item {
                MessageBubble(
                    message = MessageUi(
                        id = "streaming", role = "assistant",
                        content = streamState.partialText,
                        emotion = streamState.emotion?.label,
                        isStreaming = true, createdAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // Error state
        if (streamState is StreamState.Error) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        streamState.message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageUi) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!isUser) {
            Text(
                text = if (message.emotion != null) {
                    val emoji = Emotion.entries.find { it.label == message.emotion }?.emoji ?: ""
                    "AI $emoji"
                } else "AI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Surface(
            shape = shape,
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = message.content + if (message.isStreaming) "▊" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onVoice: () -> Unit,
    isRecording: Boolean,
    isStreaming: Boolean,
    onStop: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onVoice) {
                Icon(
                    if (isRecording) Icons.Default.Mic else Icons.Default.Mic,
                    contentDescription = "语音",
                    tint = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("输入消息...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            if (isStreaming) {
                FilledIconButton(
                    onClick = onStop,
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Icon(Icons.Default.Stop, "停止生成")
                }
            } else {
                FilledIconButton(
                    onClick = { if (text.isNotBlank()) { onSend(text); onTextChange("") } },
                    modifier = Modifier.clip(CircleShape)
                ) {
                    Icon(Icons.Default.Send, "发送")
                }
            }
        }
    }
}

@Composable
fun ConversationSidebar(
    conversations: List<com.aicompanion.domain.model.Conversation>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // Backdrop click
        Surface(
            modifier = Modifier.fillMaxSize().clickable { onDismiss() },
            color = Color.Black.copy(alpha = 0.5f)
        ) {}

        // Sidebar
        Surface(
            modifier = Modifier.fillMaxWidth(0.75f).fillMaxHeight(),
            tonalElevation = 8.dp
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("会话列表", fontWeight = FontWeight.Bold) },
                    trailingContent = {
                        IconButton(onClick = {
                            onNew()
                            onDismiss()
                        }) {
                            Icon(Icons.Default.Add, "新对话")
                        }
                    }
                )
                Divider()
                LazyColumn {
                    items(conversations, key = { it.id }) { conv ->
                        ListItem(
                            headlineContent = { Text(conv.title, maxLines = 1) },
                            modifier = Modifier.clickable {
                                onSelect(conv.id)
                                onDismiss()
                            },
                            colors = if (conv.id == activeId) ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else ListItemDefaults.colors()
                        )
                    }
                }
            }
        }
    }
}
