package com.aicompanion.feature.live2d

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
import androidx.compose.ui.unit.dp
import com.aicompanion.domain.model.Live2DModelInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Live2DModelManagerScreen(
    manager: Live2DManager,
    onBack: () -> Unit
) {
    val state by manager.state.collectAsState()
    val models by manager.getModels().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Text("当前模型", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
            }

            // Current model status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    state.currentModel?.name ?: "未加载",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "表情: ${state.currentExpression}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    if (state.isPlayingMotion) "动作: ${state.motionName}" else "状态: 待机",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (state.isLoaded) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { manager.setEmotion(com.aicompanion.core.common.Emotion.HAPPY) },
                                label = { Text("😊 开心") }
                            )
                            AssistChip(
                                onClick = { manager.setEmotion(com.aicompanion.core.common.Emotion.SAD) },
                                label = { Text("😢 伤心") }
                            )
                            AssistChip(
                                onClick = { manager.setEmotion(com.aicompanion.core.common.Emotion.SURPRISED) },
                                label = { Text("😲 惊讶") }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { manager.setEmotion(com.aicompanion.core.common.Emotion.SHY) },
                                label = { Text("😳 害羞") }
                            )
                            AssistChip(
                                onClick = { manager.setEmotion(com.aicompanion.core.common.Emotion.THINKING) },
                                label = { Text("🤔 思考") }
                            )
                            AssistChip(
                                onClick = { manager.setEmotion(com.aicompanion.core.common.Emotion.NEUTRAL) },
                                label = { Text("😶 默认") }
                            )
                        }
                    }
                }
            }

            item {
                Text("已导入模型", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 24.dp),
                    color = MaterialTheme.colorScheme.primary)
            }

            items(models, key = { it.id }) { model ->
                ModelCard(
                    model = model,
                    isActive = model.id == state.currentModel?.id,
                    onActivate = {
                        scope.launch { manager.setActive(model.id) }
                    },
                    onDelete = {
                        scope.launch { manager.deleteModel(model.id) }
                    }
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
                OutlinedButton(
                    onClick = { /* Launch file picker for model import */ },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Create, null)
                    Spacer(Modifier.width(8.dp))
                    Text("导入 Live2D 模型")
                }
                Text(
                    "支持的格式: .model3.json + .moc3 + 纹理文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: Live2DModelInfo,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = if (isActive) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(model.name, fontWeight = FontWeight.Bold)
                    if (model.isBuiltIn) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text("内置") })
                    }
                }
                Text(
                    if (isActive) "正在使用" else "未激活",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.outline
                )
            }
            if (!isActive) {
                TextButton(onClick = onActivate) { Text("启用") }
            }
            if (!model.isBuiltIn) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
