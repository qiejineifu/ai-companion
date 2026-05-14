package com.aicompanion.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.ui.theme.*
import com.aicompanion.domain.model.WorldBookEntry
import com.aicompanion.domain.repository.WorldBookRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookScreen(
    personaId: String,
    personaName: String,
    repository: WorldBookRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val entries by repository.getByPersona(personaId).collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WorldBookEntry?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Pink500, Pink600, Pink700)))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
            Text("${personaName} 世界书", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = {
                editingEntry = WorldBookEntry(
                    id = newId(), personaId = personaId, key = "", content = "",
                    createdAt = now()
                )
                showDialog = true
            }) { Icon(Icons.Default.Add, "添加", tint = Color.White) }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MenuBook, null, tint = TextGray, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有世界书条目", color = TextGray, fontSize = 15.sp)
                    Text("点击右上角 + 添加世界观设定", color = TextGray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { editingEntry = entry; showDialog = true },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.key, fontWeight = FontWeight.Bold, color = TextDark)
                                    if (entry.constant) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = Pink100, shape = RoundedCornerShape(4.dp)) {
                                            Text("常驻", fontSize = 10.sp, color = Pink600,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    if (!entry.enabled) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = Color(0xFFEEEEEE), shape = RoundedCornerShape(4.dp)) {
                                            Text("禁用", fontSize = 10.sp, color = TextGray,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text("优先级 ${entry.priority}", fontSize = 11.sp, color = TextGray)
                                }
                                Text(entry.content.take(80), fontSize = 13.sp, color = TextGray, maxLines = 2)
                                if (entry.keywords.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("触发词: ${entry.keywords.joinToString(", ")}", fontSize = 11.sp, color = Pink400)
                                }
                            }
                            IconButton(onClick = {
                                scope.launch { repository.delete(entry.id) }
                            }) { Icon(Icons.Default.Delete, "删除", tint = TextGray, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showDialog && editingEntry != null) {
        WorldBookEditDialog(
            entry = editingEntry!!,
            onUpdate = { editingEntry = it },
            onSave = {
                scope.launch {
                    if (editingEntry?.let { entries.any { e -> e.id == it.id } } == true) {
                        repository.update(editingEntry!!)
                    } else {
                        repository.create(editingEntry!!)
                    }
                    showDialog = false
                    editingEntry = null
                }
            },
            onDismiss = { showDialog = false; editingEntry = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorldBookEditDialog(
    entry: WorldBookEntry,
    onUpdate: (WorldBookEntry) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    var keywordsText by remember { mutableStateOf(entry.keywords.joinToString(", ")) }
    var secondaryText by remember { mutableStateOf(entry.secondaryKeywords.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry.key.isBlank()) "新建条目" else "编辑 ${entry.key}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = entry.key, onValueChange = { onUpdate(entry.copy(key = it)) },
                    label = { Text("标题") }, singleLine = true,
                    placeholder = { Text("例如：魔法系统、世界历史") }
                )
                OutlinedTextField(
                    value = entry.content, onValueChange = { onUpdate(entry.copy(content = it)) },
                    label = { Text("内容") }, minLines = 3,
                    placeholder = { Text("触发后注入到 AI 上下文的世界观设定...") }
                )
                OutlinedTextField(
                    value = keywordsText, onValueChange = {
                        keywordsText = it
                        onUpdate(entry.copy(keywords = it.split(",").map { kw -> kw.trim() }.filter { kw -> kw.isNotBlank() }))
                    },
                    label = { Text("触发词（逗号分隔）") }, singleLine = true,
                    placeholder = { Text("魔法, 咒语, 魔力") }
                )
                OutlinedTextField(
                    value = secondaryText, onValueChange = {
                        secondaryText = it
                        onUpdate(entry.copy(secondaryKeywords = it.split(",").map { kw -> kw.trim() }.filter { kw -> kw.isNotBlank() }))
                    },
                    label = { Text("次要触发词") }, singleLine = true,
                    placeholder = { Text("可选，匹配优先级较低的相关词") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = entry.priority.toString(),
                        onValueChange = { it.toIntOrNull()?.let { p -> onUpdate(entry.copy(priority = p)) } },
                        label = { Text("优先级") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("10") }
                    )
                    OutlinedTextField(
                        value = entry.depth.toString(),
                        onValueChange = { it.toIntOrNull()?.let { d -> onUpdate(entry.copy(depth = d)) } },
                        label = { Text("深度") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("4") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = if (entry.position == "before") "对话前" else "对话后",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("注入位置") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { expanded = true }) { Icon(Icons.Default.ArrowDropDown, "") } }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("对话记录前（高优先）") },
                                onClick = { onUpdate(entry.copy(position = "before")); expanded = false })
                            DropdownMenuItem(text = { Text("对话记录后（低优先）") },
                                onClick = { onUpdate(entry.copy(position = "after")); expanded = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = entry.enabled, onCheckedChange = { onUpdate(entry.copy(enabled = it)) })
                    Text("启用", fontSize = 14.sp)
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = entry.constant, onCheckedChange = { onUpdate(entry.copy(constant = it)) })
                    Text("常驻（始终注入）", fontSize = 14.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
