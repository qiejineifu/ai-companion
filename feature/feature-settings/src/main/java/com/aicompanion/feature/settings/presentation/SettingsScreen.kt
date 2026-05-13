package com.aicompanion.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pink500 = Color(0xFFFF6B8A)
private val Pink600 = Color(0xFFF04F7A)
private val Pink700 = Color(0xFFE0386A)
private val ChatBg = Color(0xFFFFF5F7)
private val TextDark = Color(0xFF2D1B2E)

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
    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        TopAppBar(
            title = { Text("设置", color = Color.White) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) } },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.background(Brush.horizontalGradient(listOf(Pink500, Pink600, Pink700)))
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionTitle("AI 设置") }
            item { SettingsItem("API 配置", "管理 AI 模型接口和密钥", Icons.Default.Api, onNavigateToApiConfig) }
            item { SettingsItem("人设管理", "创建和编辑 AI 角色人设", Icons.Default.Person, onNavigateToPersonas) }
            if (onNavigateToMemory != null) {
                item { SettingsItem("记忆管理", "查看和编辑 AI 记忆库", Icons.Default.Psychology, onNavigateToMemory) }
            }

            item { SectionTitle("语音设置") }
            item { SettingsItem("语音配置", "TTS 引擎、语速、音调", Icons.Default.Tune, onNavigateToVoiceSettings) }
            if (onNavigateToVoiceProfiles != null) {
                item { SettingsItem("音色管理", "选择和切换 AI 音色", Icons.Default.RecordVoiceOver, onNavigateToVoiceProfiles) }
            }

            if (onNavigateToLive2DModels != null) {
                item { SectionTitle("虚拟形象") }
                item { SettingsItem("模型管理", "导入和管理 Live2D 模型", Icons.Default.TagFaces, onNavigateToLive2DModels) }
            }

            if (onNavigateToConversations != null) {
                item { SectionTitle("对话") }
                item { SettingsItem("对话管理", "查看和管理历史对话", Icons.Default.Forum, onNavigateToConversations) }
            }

            item { SectionTitle("数据管理") }
            if (onNavigateToDataExport != null) {
                item { SettingsItem("导出/导入", "备份或恢复全部数据", Icons.Default.SwapHoriz, onNavigateToDataExport) }
            }

            item { SectionTitle("关于") }
            item { SettingsItem("AI 陪伴", "版本 0.3.0", Icons.Default.Info) {} }
            if (onNavigateToOEMGuide != null) {
                item { SettingsItem("后台运行设置", "保持 AI 陪伴在后台", Icons.Default.BatterySaver, onNavigateToOEMGuide) }
            }
            if (onNavigateToPrivacyPolicy != null) {
                item { SettingsItem("隐私政策", "", Icons.Default.Policy, onNavigateToPrivacyPolicy) }
            }
            if (onNavigateToUserAgreement != null) {
                item { SettingsItem("用户协议", "", Icons.Default.Description, onNavigateToUserAgreement) }
            }
            if (onNavigateToLicense != null) {
                item { SettingsItem("开源许可", "", Icons.Default.Code, onNavigateToLicense) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Pink500,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
    )
}

@Composable
private fun SettingsItem(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, color = TextDark, fontSize = 15.sp) },
        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFF9B8EA0), fontSize = 13.sp) },
        leadingContent = { Icon(icon, null, tint = Pink500) },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
