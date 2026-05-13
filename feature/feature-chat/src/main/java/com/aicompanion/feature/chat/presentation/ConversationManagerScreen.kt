package com.aicompanion.feature.chat.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicompanion.domain.model.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationManagerScreen(
    conversations: List<Conversation>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    val filtered = if (searchQuery.isBlank()) conversations
        else conversations.filter { it.title.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话列表") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = onNew) {
                        Icon(Icons.Default.Add, "新建对话")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索对话...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("暂无对话", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { conv ->
                        if (renamingId == conv.id) {
                            // Rename mode
                            ListItem(
                                headlineContent = {
                                    OutlinedTextField(
                                        value = renameText,
                                        onValueChange = { renameText = it },
                                        singleLine = true
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = {
                                            onRename(conv.id, renameText)
                                            renamingId = null
                                        }) {
                                            Icon(Icons.Default.Check, "确认")
                                        }
                                        IconButton(onClick = { renamingId = null }) {
                                            Icon(Icons.Default.Close, "取消")
                                        }
                                    }
                                }
                            )
                        } else {
                            ConversationItem(
                                conversation = conv,
                                isActive = conv.id == activeId,
                                onClick = { onSelect(conv.id) },
                                onRename = {
                                    renamingId = conv.id
                                    renameText = conv.title
                                },
                                onDelete = { onDelete(conv.id) }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    isActive: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (conversation.isPinned) {
                    Icon(Icons.Default.PushPin, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        supportingContent = {
            Text(dateFormat.format(Date(conversation.lastMessageAt)))
        },
        leadingContent = {
            Icon(
                if (isActive) Icons.Default.Chat else Icons.Default.ChatBubbleOutline,
                null,
                tint = if (isActive) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.outline
            )
        },
        modifier = Modifier.clickable { onClick() },
        colors = if (isActive) ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else ListItemDefaults.colors(),
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, "重命名", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(20.dp))
                }
            }
        }
    )
}
