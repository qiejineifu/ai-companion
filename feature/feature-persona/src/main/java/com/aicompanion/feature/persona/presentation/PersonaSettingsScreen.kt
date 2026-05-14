package com.aicompanion.feature.persona.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.aicompanion.domain.model.ApiProvider
import com.aicompanion.domain.model.Conversation
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.model.VoiceProfile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.aicompanion.core.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaSettingsScreen(
    persona: Persona,
    voiceProfiles: List<VoiceProfile>,
    apiProviders: List<ApiProvider>,
    conversations: List<Conversation>,
    onUpdatePersona: (Persona) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onBack: () -> Unit,
    onEditPersona: () -> Unit,
    onManageStickers: () -> Unit,
    onManageWorldBook: () -> Unit = {}
) {
    val context = LocalContext.current
    var avatarUri by remember { mutableStateOf(persona.avatarImageUri) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copy to internal storage
            val input = context.contentResolver.openInputStream(it)
            val ext = context.contentResolver.getType(it)?.let { t ->
                if (t.contains("png")) ".png" else ".jpg"
            } ?: ".jpg"
            val dest = File(context.filesDir, "avatars/${persona.id}$ext")
            dest.parentFile?.mkdirs()
            input?.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
            val newUri = dest.toURI().toString()
            avatarUri = newUri
            onUpdatePersona(persona.copy(avatarImageUri = newUri))
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(topBarGradient))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
            Text("${persona.name} 设置", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onEditPersona) { Icon(Icons.Default.Edit, "编辑", tint = Color.White) }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            // Avatar
            item {
                SectionHeader("头像")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(72.dp).clip(CircleShape)
                            .background(Pink200).clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val uri = avatarUri
                        if (uri != null) {
                            AsyncImage(
                                model = uri, contentDescription = "头像",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(persona.name.take(1), fontSize = 28.sp, color = Pink600, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text("点击更换头像", color = Pink500, fontWeight = FontWeight.Medium)
                        Text("支持 PNG/JPG 格式", color = TextGray, fontSize = 13.sp)
                    }
                }
            }

            // Voice
            item { SectionHeader("音色设置") }
            if (voiceProfiles.isEmpty()) {
                item { SettingsItem("尚未配置音色", "前往语音设置添加", Icons.Default.RecordVoiceOver) {} }
            } else {
                items(voiceProfiles) { vp ->
                    val isSelected = vp.id == persona.voiceProfileId
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onUpdatePersona(persona.copy(voiceProfileId = if (isSelected) null else vp.id)) },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Pink50 else Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, null, tint = if (isSelected) Pink500 else TextGray)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vp.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = TextDark)
                                Text("引擎: ${vp.engineType}", color = TextGray, fontSize = 13.sp)
                            }
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Pink500)
                        }
                    }
                }
            }

            // Model
            item { SectionHeader("模型设置") }
            if (apiProviders.isEmpty()) {
                item { SettingsItem("尚未配置 API", "前往 API 设置添加", Icons.Default.Api) {} }
            } else {
                items(apiProviders) { provider ->
                    val isSelected = provider.id == persona.apiProviderId
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onUpdatePersona(persona.copy(apiProviderId = if (isSelected) null else provider.id)) },
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Pink50 else Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Api, null, tint = if (isSelected) Pink500 else TextGray)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = TextDark)
                                Text(provider.modelName, color = TextGray, fontSize = 13.sp)
                            }
                            if (isSelected) Icon(Icons.Default.Check, null, tint = Pink500)
                        }
                    }
                }
            }

            // Stickers
            item { SectionHeader("表情包") }
            item {
                SettingsItem("表情包管理", "导入和管理该角色的表情包，AI 会根据情绪自动匹配", Icons.Default.EmojiEmotions, onClick = onManageStickers)
            }

            // Waifu Mode
            item { SectionHeader("对话模式") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, null, tint = Pink500, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Waifu 模式", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                            Text("AI 回复拆分为逐句气泡，智能延迟模拟真人打字节奏，用表情包代替动作描写",
                                fontSize = 12.sp, color = TextGray)
                        }
                        Switch(
                            checked = persona.waifuMode,
                            onCheckedChange = { onUpdatePersona(persona.copy(waifuMode = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Pink500, checkedTrackColor = Pink100)
                        )
                    }
                }
            }

            // World Book
            item { SectionHeader("世界书") }
            item {
                SettingsItem("世界书管理", "添加世界观设定，触发关键词后自动注入上下文", Icons.Default.MenuBook, onClick = onManageWorldBook)
            }

            // Author's Note
            item { SectionHeader("Author's Note") }
            item {
                var note by remember { mutableStateOf(persona.authorsNote) }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("作者注记", fontWeight = FontWeight.Medium, color = TextDark, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("会在每次对话中引导 AI 的写作方向，类似 SillyTavern 的 Author's Note",
                            color = TextGray, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("[写作风格: 描述性散文, 使用感官细节]") },
                            minLines = 2
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onUpdatePersona(persona.copy(authorsNote = note)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Pink500),
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("保存") }
                    }
                }
            }

            // Conversations for this persona
            item { SectionHeader("对话记录") }
            val personaConvs = conversations.filter { it.personaId == persona.id }
            if (personaConvs.isEmpty()) {
                item { SettingsItem("暂无对话记录", "", Icons.Default.Forum) {} }
            } else {
                items(personaConvs) { conv ->
                    val timeStr = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(conv.lastMessageAt))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable { onOpenConversation(conv.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(conv.title, fontWeight = FontWeight.Medium, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (conv.lastMessagePreview.isNotBlank()) {
                                    Text(conv.lastMessagePreview, color = TextGray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text(timeStr, color = TextGray, fontSize = 12.sp)
                            }
                            IconButton(onClick = { onDeleteConversation(conv.id) }) {
                                Icon(Icons.Default.Delete, "删除", tint = TextGray, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Pink500,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
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
