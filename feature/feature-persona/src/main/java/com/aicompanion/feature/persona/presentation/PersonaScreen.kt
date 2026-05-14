package com.aicompanion.feature.persona.presentation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aicompanion.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    viewModel: PersonaViewModel,
    onBack: () -> Unit,
    onSelectPersona: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importFromTavernCard(it, context) } }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearImportMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        // Top bar with pink gradient
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(topBarGradient))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
            Text("人设管理", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { importLauncher.launch("image/*") }) {
                Icon(Icons.Default.FileUpload, "导入角色卡", tint = Color.White)
            }
            IconButton(onClick = { viewModel.showCreateDialog() }) {
                Icon(Icons.Default.Add, "创建", tint = Color.White)
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
            item {
                SectionHeader("预设模板", Icons.Default.Star)
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
                SectionHeader("自定义人设", Icons.Default.Face)
            }
            if (state.personas.none { !it.isPreset }) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("点击右上角 + 创建自定义角色", color = TextGray, fontSize = 14.sp)
                    }
                }
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
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Pink500, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Pink500)
    }
}

@Composable
private fun PersonaCard(
    persona: com.aicompanion.domain.model.Persona,
    onSelect: () -> Unit = {},
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape)
                    .background(if (persona.avatarImageUri == null) Pink100 else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (persona.avatarImageUri != null) {
                    AsyncImage(
                        model = persona.avatarImageUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(persona.name.take(1), fontSize = 22.sp, color = Pink600, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(persona.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                    if (persona.isPreset) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = Pink100, shape = RoundedCornerShape(4.dp)) {
                            Text("预设", fontSize = 10.sp, color = Pink500,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                if (persona.description.isNotBlank()) {
                    Text(persona.description, fontSize = 13.sp, color = TextGray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Pink50, shape = RoundedCornerShape(4.dp)) {
                        Text(persona.speakingStyle, fontSize = 11.sp, color = Pink500,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Surface(color = Purple100, shape = RoundedCornerShape(4.dp)) {
                        Text(persona.relationshipType, fontSize = 11.sp, color = Purple400,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            // Actions
            FilledTonalButton(
                onClick = onSelect,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Pink100,
                    contentColor = Pink600
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) { Text("选择", fontSize = 13.sp) }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, "编辑", tint = TextGray, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, "复制", tint = TextGray, modifier = Modifier.size(18.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = Pink400, modifier = Modifier.size(18.dp))
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
