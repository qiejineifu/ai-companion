package com.aicompanion.feature.persona.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    viewModel: PersonaViewModel,
    onBack: () -> Unit,
    onSelectPersona: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar (plain Row)
        TopAppBar(
            title = { Text("人设管理") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            },
            actions = {
                IconButton(onClick = { viewModel.showCreateDialog() }) {
                    Icon(Icons.Default.Add, "创建")
                }
            }
        )
        LazyColumn {
            item {
                Text(
                    "预设模板", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.personas.filter { it.isPreset }, key = { it.id }) { persona ->
                PersonaCard(
                    persona = persona,
                    onSelect = { onSelectPersona(persona.id) },
                    onEdit = { viewModel.showEditDialog(persona) },
                    onDuplicate = { viewModel.duplicatePersona(persona) }
                )
            }
            item {
                Text(
                    "自定义人设", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.personas.filter { !it.isPreset }, key = { it.id }) { persona ->
                PersonaCard(
                    persona = persona,
                    onSelect = { onSelectPersona(persona.id) },
                    onEdit = { viewModel.showEditDialog(persona) },
                    onDuplicate = { viewModel.duplicatePersona(persona) },
                    onDelete = { viewModel.deletePersona(persona.id) }
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

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
    } // End Column
}

@Composable
private fun PersonaCard(
    persona: com.aicompanion.domain.model.Persona,
    onSelect: () -> Unit = {},
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(persona.name, fontWeight = FontWeight.Bold)
                        if (persona.isPreset) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(onClick = {}, label = { Text("预设") })
                        }
                    }
                    Text(persona.description, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("语气: ${persona.speakingStyle}", style = MaterialTheme.typography.labelSmall)
                        Text("关系: ${persona.relationshipType}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FilledTonalButton(onClick = onSelect, modifier = Modifier.padding(end = 4.dp)) {
                    Text("选择")
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
                IconButton(onClick = onDuplicate) { Icon(Icons.Default.Add, "复制") }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
                }
            }
        }
    }
}

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
                // Basic info
                OutlinedTextField(
                    value = persona.name, onValueChange = { onPersonaChange(persona.copy(name = it)) },
                    label = { Text("角色名称") }, singleLine = true
                )
                OutlinedTextField(
                    value = persona.description, onValueChange = { onPersonaChange(persona.copy(description = it)) },
                    label = { Text("一句话简介") }, singleLine = true,
                    placeholder = { Text("她是一个...") }
                )

                // Scenario (世界观)
                OutlinedTextField(
                    value = persona.scenario, onValueChange = { onPersonaChange(persona.copy(scenario = it)) },
                    label = { Text("场景/世界观") }, minLines = 2,
                    placeholder = { Text("故事发生的背景，例如：深夜的酒吧，你是唯一的顾客...") }
                )

                // First message
                OutlinedTextField(
                    value = persona.firstMessage, onValueChange = { onPersonaChange(persona.copy(firstMessage = it)) },
                    label = { Text("开场白") }, minLines = 1,
                    placeholder = { Text("角色在对话开始时说的第一句话") },
                    supportingText = { Text("新对话时自动发送，让角色主动开口") }
                )

                // System prompt
                OutlinedTextField(
                    value = persona.systemPrompt,
                    onValueChange = { onPersonaChange(persona.copy(systemPrompt = it)) },
                    label = { Text("角色定义 (System Prompt)") }, minLines = 2,
                    placeholder = { Text("你是谁、你的性格、说话方式、限制条件...") }
                )

                // User display name
                OutlinedTextField(
                    value = persona.userDisplayName,
                    onValueChange = { onPersonaChange(persona.copy(userDisplayName = it)) },
                    label = { Text("用户称呼") }, singleLine = true,
                    placeholder = { Text("AI 怎么叫你？如：小明、主人") }
                )

                // Style & relationship
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = persona.speakingStyle,
                        onValueChange = { onPersonaChange(persona.copy(speakingStyle = it)) },
                        label = { Text("说话风格") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = persona.relationshipType,
                        onValueChange = { onPersonaChange(persona.copy(relationshipType = it)) },
                        label = { Text("关系定位") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Example chats
                Text("示例对话", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newExampleChat, onValueChange = onNewExampleChatChange,
                        label = { Text("添加示例") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("用户: 你好呀 / 角色: 你来啦~") }
                    )
                    IconButton(onClick = onAddExampleChat, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
                persona.exampleChats.forEachIndexed { index, chat ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chat, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2)
                        IconButton(onClick = { onRemoveExampleChat(index) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "删除", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Tags
                Text("标签", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTag, onValueChange = onNewTagChange,
                        label = { Text("添加标签") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onAddTag, modifier = Modifier.align(Alignment.CenterVertically)) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
                if (persona.tags.isNotEmpty()) {
                    FlowRow {
                        persona.tags.forEachIndexed { index, tag ->
                            InputChip(
                                selected = false,
                                onClick = { onRemoveTag(index) },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, "移除", Modifier.size(16.dp)) }
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }

                // Traits
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
                            selected = false,
                            onClick = { onRemoveTrait(index) },
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
