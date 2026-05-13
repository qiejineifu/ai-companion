package com.aicompanion.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    onNavigateToMemory: (() -> Unit)? = null,
    onNavigateToVoiceSettings: () -> Unit,
    onNavigateToVoiceProfiles: (() -> Unit)? = null,
    onNavigateToLive2DModels: (() -> Unit)? = null,
    onNavigateToConversations: (() -> Unit)? = null,
    onNavigateToDataExport: (() -> Unit)? = null,
    onNavigateToPrivacyPolicy: (() -> Unit)? = null,
    onNavigateToUserAgreement: (() -> Unit)? = null,
    onNavigateToLicense: (() -> Unit)? = null,
    onNavigateToOEMGuide: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // AI Settings
            item {
                Text("AI 设置", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
            item {
                ListItem(headlineContent = { Text("API 配置") },
                    supportingContent = { Text("管理 AI 模型接口和密钥") },
                    leadingContent = { Icon(Icons.Default.Api, null) },
                    modifier = Modifier.clickable { onNavigateToApiConfig() })
            }
            item {
                ListItem(headlineContent = { Text("人设管理") },
                    supportingContent = { Text("创建和编辑 AI 角色人设") },
                    leadingContent = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.clickable { onNavigateToPersonas() })
            }
            if (onNavigateToMemory != null) {
                item {
                    ListItem(headlineContent = { Text("记忆管理") },
                        supportingContent = { Text("查看和编辑 AI 记忆库") },
                        leadingContent = { Icon(Icons.Default.Psychology, null) },
                        modifier = Modifier.clickable { onNavigateToMemory() })
                }
            }

            // Voice Settings
            item {
                Text("语音设置", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
            item {
                ListItem(headlineContent = { Text("语音配置") },
                    supportingContent = { Text("TTS 引擎、语速、音调") },
                    leadingContent = { Icon(Icons.Default.Tune, null) },
                    modifier = Modifier.clickable { onNavigateToVoiceSettings() })
            }
            if (onNavigateToVoiceProfiles != null) {
                item {
                    ListItem(headlineContent = { Text("音色管理") },
                        supportingContent = { Text("选择和切换 AI 音色") },
                        leadingContent = { Icon(Icons.Default.RecordVoiceOver, null) },
                        modifier = Modifier.clickable { onNavigateToVoiceProfiles() })
                }
            }

            // Live2D
            if (onNavigateToLive2DModels != null) {
                item {
                    Text("虚拟形象", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary)
                }
                item {
                    ListItem(headlineContent = { Text("模型管理") },
                        supportingContent = { Text("导入和管理 Live2D 模型") },
                        leadingContent = { Icon(Icons.Default.TagFaces, null) },
                        modifier = Modifier.clickable { onNavigateToLive2DModels() })
                }
            }

            // Conversations
            if (onNavigateToConversations != null) {
                item {
                    Text("对话", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary)
                }
                item {
                    ListItem(headlineContent = { Text("对话管理") },
                        supportingContent = { Text("查看、搜索和管理历史对话") },
                        leadingContent = { Icon(Icons.Default.Forum, null) },
                        modifier = Modifier.clickable { onNavigateToConversations() })
                }
            }

            // Data
            item {
                Text("数据管理", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
            if (onNavigateToDataExport != null) {
                item {
                    ListItem(headlineContent = { Text("导出/导入数据") },
                        supportingContent = { Text("备份或恢复全部数据") },
                        leadingContent = { Icon(Icons.Default.SwapHoriz, null) },
                        modifier = Modifier.clickable { onNavigateToDataExport() })
                }
            }
            item {
                ListItem(headlineContent = { Text("导出数据") },
                    supportingContent = { Text("导出对话记录和配置") },
                    leadingContent = { Icon(Icons.Default.CloudDownload, null) })
            }
            item {
                ListItem(headlineContent = { Text("清除数据") },
                    supportingContent = { Text("清除所有对话和缓存") },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, null) })
            }

            // About
            item {
                Text("关于", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
            item {
                ListItem(headlineContent = { Text("AI 陪伴") },
                    supportingContent = { Text("版本 0.3.0") },
                    leadingContent = { Icon(Icons.Default.Info, null) })
            }
            if (onNavigateToOEMGuide != null) {
                item {
                    ListItem(headlineContent = { Text("后台运行设置") },
                        supportingContent = { Text("保持AI陪伴在后台运行") },
                        leadingContent = { Icon(Icons.Default.BatterySaver, null) },
                        modifier = Modifier.clickable { onNavigateToOEMGuide() })
                }
            }
            if (onNavigateToPrivacyPolicy != null) {
                item {
                    ListItem(headlineContent = { Text("隐私政策") },
                        leadingContent = { Icon(Icons.Default.Policy, null) },
                        modifier = Modifier.clickable { onNavigateToPrivacyPolicy() })
                }
            }
            if (onNavigateToUserAgreement != null) {
                item {
                    ListItem(headlineContent = { Text("用户协议") },
                        leadingContent = { Icon(Icons.Default.Description, null) },
                        modifier = Modifier.clickable { onNavigateToUserAgreement() })
                }
            }
            if (onNavigateToLicense != null) {
                item {
                    ListItem(headlineContent = { Text("开源许可") },
                        leadingContent = { Icon(Icons.Default.Code, null) },
                        modifier = Modifier.clickable { onNavigateToLicense() })
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
