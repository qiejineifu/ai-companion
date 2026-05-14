package com.aicompanion.feature.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aicompanion.core.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userAvatarUri: String?,
    onUserAvatarChanged: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToApiConfig: () -> Unit,
    onNavigateToPersonas: () -> Unit,
    onNavigateToMemory: (() -> Unit)? = null,
    onNavigateToProactiveMessages: (() -> Unit)? = null,
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
            modifier = Modifier.background(Brush.horizontalGradient(topBarGradient))
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // User profile
            item {
                val context = LocalContext.current
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                    uri?.let {
                        val dest = File(context.filesDir, "user_avatar.jpg")
                        context.contentResolver.openInputStream(it)?.use { src ->
                            dest.outputStream().use { out -> src.copyTo(out) }
                        }
                        onUserAvatarChanged(dest.toURI().toString())
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { picker.launch("image/*") }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Pink500.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        if (userAvatarUri != null) {
                            AsyncImage(model = userAvatarUri, contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Person, null, tint = Pink500, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("你的头像", fontWeight = FontWeight.SemiBold, color = TextDark, fontSize = 16.sp)
                        Text("点击更换头像", color = TextGray, fontSize = 13.sp)
                    }
                }
            }
            item { HorizontalDivider(color = DividerPink) }

            item { SectionTitle("AI 设置") }
            item { SettingsItem("API 配置", "管理 AI 模型接口和密钥", Icons.Default.Api, onNavigateToApiConfig) }
            item { SettingsItem("人设管理", "创建和编辑 AI 角色人设", Icons.Default.Person, onNavigateToPersonas) }
            if (onNavigateToMemory != null) {
                item { SettingsItem("记忆管理", "查看和编辑 AI 记忆库", Icons.Default.Psychology, onNavigateToMemory) }
            }
            if (onNavigateToProactiveMessages != null) {
                item { SettingsItem("AI主动消息", "设置AI在后台随机主动给你发消息，像真人一样", Icons.Default.NotificationsActive, onNavigateToProactiveMessages) }
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
        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle, color = TextGray, fontSize = 13.sp) },
        leadingContent = { Icon(icon, null, tint = Pink500) },
        modifier = Modifier.clickable { onClick() },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
