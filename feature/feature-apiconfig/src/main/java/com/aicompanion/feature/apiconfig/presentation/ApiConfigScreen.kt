package com.aicompanion.feature.apiconfig.presentation

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiConfigScreen(
    viewModel: ApiConfigViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 配置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showAddDialog() }) {
                        Icon(Icons.Default.Add, "添加")
                    }
                }
            )
        }
    ) { padding ->
        if (state.providers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚未配置 API", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.showTemplatePicker() }) {
                        Text("添加 API 提供商")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(state.providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        isActive = provider.isActive,
                        onEdit = { viewModel.showEditDialog(provider) },
                        onDelete = { viewModel.deleteProvider(provider.id) },
                        onSetActive = { viewModel.setActive(provider.id) }
                    )
                }
                item {
                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        // Add/Edit Dialog
        if (state.showEditDialog && state.editingProvider != null) {
            ProviderEditDialog(
                provider = state.editingProvider!!,
                onProviderChange = { viewModel.updateEditingProvider(it) },
                onSave = { viewModel.saveProvider() },
                onTest = { viewModel.testConnection() },
                onDismiss = { viewModel.dismissDialog() },
                isTesting = state.isTesting,
                testResult = state.testResult
            )
        }

        // Template Picker
        if (state.showTemplatePicker) {
            TemplatePickerDialog(
                templates = state.templates,
                onSelect = { viewModel.showAddDialog(it) },
                onDismiss = { viewModel.hideTemplatePicker() }
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: com.aicompanion.domain.model.ApiProvider,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
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
                    Text(provider.name, fontWeight = FontWeight.Bold)
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text("当前使用") })
                    }
                }
                Text(provider.modelName, style = MaterialTheme.typography.bodySmall)
                Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (!isActive) {
                TextButton(onClick = onSetActive) { Text("启用") }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditDialog(
    provider: com.aicompanion.domain.model.ApiProvider,
    onProviderChange: (com.aicompanion.domain.model.ApiProvider) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
    isTesting: Boolean,
    testResult: String?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (provider.name.isBlank()) "添加 API" else "编辑 ${provider.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = provider.name, onValueChange = { onProviderChange(provider.copy(name = it)) },
                    label = { Text("名称") }, singleLine = true
                )
                OutlinedTextField(
                    value = provider.baseUrl, onValueChange = { onProviderChange(provider.copy(baseUrl = it)) },
                    label = { Text("API 地址") }, singleLine = true,
                    placeholder = { Text("https://api.openai.com/v1/chat/completions") }
                )
                OutlinedTextField(
                    value = provider.apiKeyEncrypted, onValueChange = { onProviderChange(provider.copy(apiKeyEncrypted = it)) },
                    label = { Text("API Key") }, singleLine = true
                )
                OutlinedTextField(
                    value = provider.modelName, onValueChange = { onProviderChange(provider.copy(modelName = it)) },
                    label = { Text("模型名称") }, singleLine = true,
                    placeholder = { Text("gpt-4o / deepseek-chat") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = provider.temperature.toString(),
                        onValueChange = { it.toFloatOrNull()?.let { t -> onProviderChange(provider.copy(temperature = t.coerceIn(0f, 2f))) } },
                        label = { Text("Temperature") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = provider.maxTokens.toString(),
                        onValueChange = { it.toIntOrNull()?.let { t -> onProviderChange(provider.copy(maxTokens = t)) } },
                        label = { Text("Max Tokens") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isTesting && provider.apiKeyEncrypted.isNotBlank()
                    ) {
                        Text(if (isTesting) "测试中..." else "测试连接")
                    }
                    if (testResult != null) {
                        Text(
                            testResult,
                            color = if (testResult == "连接成功") MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
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

@Composable
private fun TemplatePickerDialog(
    templates: List<ApiConfigTemplate>,
    onSelect: (ApiConfigTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 API 模板") },
        text = {
            LazyColumn {
                items(templates) { template ->
                    ListItem(
                        headlineContent = { Text(template.name) },
                        supportingContent = { Text(template.baseUrl.ifBlank { "自定义配置" }) },
                        modifier = Modifier.clickable { onSelect(template) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
