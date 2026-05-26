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
    onManageWorldBook: () -> Unit = {},
    onManageMoments: () -> Unit = {},
    onManageExperiences: () -> Unit = {}
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, null, tint = Pink500, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("VITS 离线音色 · 编号 ${persona.voiceSid}",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("0", fontSize = 12.sp, color = TextGray)
                            Slider(
                                value = persona.voiceSid.toFloat(),
                                onValueChange = { onUpdatePersona(persona.copy(voiceSid = it.toInt())) },
                                valueRange = 0f..173f, steps = 172,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500)
                            )
                            Text("173", fontSize = 12.sp, color = TextGray)
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
                    var modelExpanded by remember { mutableStateOf(false) }
                    var showCustomDialog by remember { mutableStateOf(false) }
                    val models = knownModels(provider.providerTemplate) + provider.modelName
                    val currentModel = persona.modelName ?: provider.modelName

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        // Provider card — click to select
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onUpdatePersona(persona.copy(apiProviderId = if (isSelected) null else provider.id)) },
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Pink50 else Color.White)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Api, null, tint = if (isSelected) Pink500 else TextGray)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(provider.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = TextDark)
                                    Text(currentModel, color = TextGray, fontSize = 13.sp)
                                }
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Pink500)
                            }
                        }
                        // Model dropdown — shown only when this provider is selected
                        if (isSelected) {
                            Spacer(Modifier.height(6.dp))
                            Box {
                                OutlinedCard(
                                    modifier = Modifier.fillMaxWidth().clickable { modelExpanded = true },
                                    colors = CardDefaults.outlinedCardColors(containerColor = Pink50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Dns, null, tint = Pink500, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("模型: $currentModel", modifier = Modifier.weight(1f), color = TextDark, fontSize = 14.sp)
                                        Icon(Icons.Default.ArrowDropDown, null, tint = TextGray)
                                    }
                                }
                                DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                                    models.distinct().forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model, fontSize = 14.sp) },
                                            onClick = {
                                                modelExpanded = false
                                                val override = if (model == provider.modelName) null else model
                                                onUpdatePersona(persona.copy(modelName = override))
                                            },
                                            trailingIcon = {
                                                if (model == currentModel) Icon(Icons.Default.Check, null, tint = Pink500)
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = DividerPink)
                                    DropdownMenuItem(
                                        text = { Text("+ 自定义模型…", color = Pink500, fontSize = 14.sp) },
                                        onClick = { modelExpanded = false; showCustomDialog = true },
                                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Pink500) }
                                    )
                                }
                            }
                            if (showCustomDialog) {
                                var customText by remember { mutableStateOf("") }
                                AlertDialog(
                                    onDismissRequest = { showCustomDialog = false },
                                    title = { Text("自定义模型名称") },
                                    text = {
                                        OutlinedTextField(
                                            value = customText,
                                            onValueChange = { customText = it },
                                            singleLine = true,
                                            placeholder = { Text("输入模型名称，如 deepseek-v4-flash") }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showCustomDialog = false
                                            if (customText.isNotBlank()) {
                                                onUpdatePersona(persona.copy(modelName = customText))
                                            }
                                        }) { Text("确定") }
                                    },
                                    dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("取消") } }
                                )
                            }
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
                            onCheckedChange = {
                                onUpdatePersona(persona.copy(
                                    waifuMode = it,
                                    chuanYueMode = if (it) false else persona.chuanYueMode
                                ))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Pink500, checkedTrackColor = Pink100)
                        )
                    }
                }
            }

            // 穿越 Mode
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Explore, null, tint = Purple400, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("穿越模式", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                            Text("沉浸式叙事互动，允许动作描写和场景交互。与 Waifu 模式互斥",
                                fontSize = 12.sp, color = TextGray)
                        }
                        Switch(
                            checked = persona.chuanYueMode,
                            onCheckedChange = {
                                onUpdatePersona(persona.copy(
                                    chuanYueMode = it,
                                    waifuMode = if (it) false else persona.waifuMode
                                ))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Purple400, checkedTrackColor = Purple100)
                        )
                    }
                }
            }

            // Appearance description for image generation
            item { SectionHeader("外貌描述") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("用于 AI 生图的人物外貌",
                            fontSize = 13.sp, color = TextGray)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = persona.appearanceDesc,
                            onValueChange = { onUpdatePersona(persona.copy(appearanceDesc = it)) },
                            label = { Text("外貌描述") },
                            placeholder = { Text("黑色长发，棕色眼睛，身高165cm，常穿白色连衣裙...") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("朋友圈配图", modifier = Modifier.weight(1f), fontSize = 14.sp, color = TextDark)
                            Switch(
                                checked = persona.imageGenEnabled,
                                onCheckedChange = { onUpdatePersona(persona.copy(imageGenEnabled = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Pink500, checkedTrackColor = Pink100)
                            )
                        }
                    }
                }
            }

            // World Book
            item { SectionHeader("世界书") }
            item {
                SettingsItem("世界书管理", "添加世界观设定，触发关键词后自动注入上下文", Icons.Default.MenuBook, onClick = onManageWorldBook)
            }

            // Moments (朋友圈)
            item { SectionHeader("朋友圈") }
            item {
                val count = persona.moments.size
                val status = if (persona.momentsEnabled) {
                    if (persona.momentsRandomMode) "${persona.momentsRandomMinMinutes}-${persona.momentsRandomMaxMinutes}分钟随机"
                    else "每${persona.momentsIntervalMinutes}分钟"
                } else "已关闭"
                SettingsItem(
                    "朋友圈动态", "${if (count > 0) "$count 条 · " else ""}$status",
                    Icons.Default.CameraAlt, onClick = onManageMoments
                )
            }

            // Experiences (最近经历)
            item { SectionHeader("最近经历") }
            item {
                val count = persona.experiences.size
                val status = if (persona.experiencesEnabled) {
                    if (persona.experiencesRandomMode) "${persona.experiencesRandomMinMinutes / 60}-${persona.experiencesRandomMaxMinutes / 60}小时随机"
                    else "每${persona.experiencesIntervalMinutes / 60}小时"
                } else "已关闭"
                SettingsItem(
                    "最近经历", "${if (count > 0) "$count 篇 · " else ""}$status",
                    Icons.Default.AutoStories, onClick = onManageExperiences
                )
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

private fun knownModels(providerTemplate: String): List<String> = when (providerTemplate) {
    "openai" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "o3-mini")
    "deepseek" -> listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash")
    "qwen" -> listOf("qwen-plus", "qwen-max", "qwen-turbo", "qwen-long")
    "siliconflow" -> listOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-72B-Instruct")
    else -> emptyList()
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
