package com.aicompanion.feature.memory.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicompanion.domain.model.MemoryEntry

private val Pink500 = Color(0xFFFF6B8A)
private val Pink600 = Color(0xFFF04F7A)
private val Pink700 = Color(0xFFE0386A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF5F7))) {
        TopAppBar(
            title = { Text("记忆管理", color = Color.White) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) } },
            actions = {
                IconButton(onClick = { viewModel.showEditDialog() }) { Icon(Icons.Default.Add, "添加记忆", tint = Color.White) }
                IconButton(onClick = { viewModel.clearAllMemories() }) { Icon(Icons.Default.Delete, "清除全部", tint = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.background(Brush.horizontalGradient(listOf(Pink500, Pink600, Pink700)))
        )
        Column {
            // Persona selector
            if (state.personas.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = state.personas.indexOf(state.selectedPersona).coerceAtLeast(0)
                ) {
                    state.personas.forEach { persona ->
                        Tab(
                            selected = persona.id == state.selectedPersona?.id,
                            onClick = { viewModel.selectPersona(persona.id) },
                            text = { Text(persona.name) }
                        )
                    }
                }
            }

            // Stats cards
            if (state.stats.totalCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatChip("总计", "${state.stats.totalCount}", Modifier.weight(1f))
                    StatChip("个人", "${state.stats.personalCount}", Modifier.weight(1f))
                    StatChip("偏好", "${state.stats.preferenceCount}", Modifier.weight(1f))
                    StatChip("生活", "${state.stats.lifeEventCount}", Modifier.weight(1f))
                }
            }

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("搜索记忆...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Clear, "清除")
                        }
                    }
                },
                singleLine = true
            )

            // Memory list
            val displayList = if (state.searchQuery.isNotBlank()) {
                state.searchResults.map { it.memory }
            } else {
                state.memories
            }

            if (displayList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("暂无记忆数据", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline)
                        Text("AI 会在对话中自动提取重要信息",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn {
                    items(displayList, key = { it.id }) { memory ->
                        MemoryCard(
                            memory = memory,
                            searchQuery = state.searchQuery,
                            onEdit = { viewModel.showEditDialog(memory) },
                            onDelete = { viewModel.deleteMemory(memory.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // Edit dialog
        if (state.showEditDialog && state.editingMemory != null) {
            MemoryEditDialog(
                memory = state.editingMemory!!,
                onMemoryChange = { viewModel.updateEditingMemory(it) },
                onSave = { viewModel.saveMemory() },
                onDismiss = { viewModel.dismissEditDialog() }
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntry,
    searchQuery: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category icon
            val icon = when {
                memory.content.startsWith("[personal]") -> Icons.Default.Person
                memory.content.startsWith("[preference]") -> Icons.Default.Favorite
                memory.content.startsWith("[life_event]") -> Icons.Default.DateRange
                memory.content.startsWith("[emotional]") -> Icons.Default.Face
                else -> Icons.Default.Info
            }
            Icon(icon, null, modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Highlight matching text
                val displayContent = memory.content.removePrefix("[personal] ")
                    .removePrefix("[preference] ")
                    .removePrefix("[life_event] ")
                    .removePrefix("[emotional] ")
                Text(displayContent, style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("置信度: ${(memory.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("重要度: ${(memory.importance * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("访问: ${memory.accessCount}次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MemoryEditDialog(
    memory: MemoryEntry,
    onMemoryChange: (MemoryEntry) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (memory.content.isBlank()) "添加记忆" else "编辑记忆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = memory.content,
                    onValueChange = { onMemoryChange(memory.copy(content = it)) },
                    label = { Text("记忆内容") },
                    minLines = 3,
                    placeholder = { Text("输入重要的信息，AI 会记住并在后续对话中使用") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重要度:", Modifier.align(Alignment.CenterVertically))
                    Slider(
                        value = memory.importance,
                        onValueChange = { onMemoryChange(memory.copy(importance = it)) },
                        modifier = Modifier.weight(1f),
                        valueRange = 0f..1f
                    )
                    Text("${(memory.importance * 100).toInt()}%")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("置信度:", Modifier.align(Alignment.CenterVertically))
                    Slider(
                        value = memory.confidence,
                        onValueChange = { onMemoryChange(memory.copy(confidence = it)) },
                        modifier = Modifier.weight(1f),
                        valueRange = 0f..1f
                    )
                    Text("${(memory.confidence * 100).toInt()}%")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
