package com.aicompanion.feature.voice

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
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.domain.model.VoiceProfile
import com.aicompanion.domain.repository.VoiceRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceProfileScreen(
    voiceRepository: VoiceRepository,
    ttsManager: TTSManager,
    onBack: () -> Unit
) {
    val profiles by voiceRepository.getProfiles().collectAsState(initial = emptyList())
    val activeProfile = profiles.firstOrNull { it.isActive }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音色管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { /* TODO: create new profile */ }) {
                        Icon(Icons.Default.Add, "添加音色")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // Active profile
            if (activeProfile != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("当前音色", style = MaterialTheme.typography.labelSmall)
                            Text(activeProfile.name, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium)
                            Text("引擎: ${activeProfile.engineType}",
                                style = MaterialTheme.typography.bodySmall)
                            Row {
                                Text("音调: ${activeProfile.pitch}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(16.dp))
                                Text("语速: ${activeProfile.speed}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = {
                                ttsManager.speak("你好，这是${activeProfile.name}的语音测试。", activeProfile)
                            }) {
                                Icon(Icons.Default.VolumeUp, null)
                                Spacer(Modifier.width(8.dp))
                                Text("试听")
                            }
                        }
                    }
                }
            }

            // Preset voices
            item {
                Text("预设音色", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary)
            }

            val presetVoices = listOf(
                VoicePreset("温柔女声", "温柔知性的女性声音，适合陪伴聊天", "system", "zh-CN-female-1"),
                VoicePreset("元气少女", "活泼可爱的少女音色", "system", "zh-CN-female-2"),
                VoicePreset("知性女声", "专业清晰的女性声音", "system", "zh-CN-female-3"),
                VoicePreset("沉稳男声", "温暖沉稳的男性声音", "system", "zh-CN-male-1"),
                VoicePreset("清澈女声", "清亮透明的女性声音", "system", "zh-CN-female-4"),
            )

            items(presetVoices) { preset ->
                ListItem(
                    headlineContent = { Text(preset.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(preset.description) },
                    leadingContent = {
                        Icon(Icons.Default.RecordVoiceOver, null,
                            tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        // Create voice profile from preset
                    }
                )
            }

            // Engine settings
            item {
                Text("语音引擎", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.primary)
            }

            item {
                ListItem(
                    headlineContent = { Text("系统 TTS") },
                    supportingContent = { Text("Android 内置语音引擎，离线可用") },
                    leadingContent = { Icon(Icons.Default.Android, null) },
                    trailingContent = { Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("科大讯飞") },
                    supportingContent = { Text("高品质中文语音，需联网") },
                    leadingContent = { Icon(Icons.Default.Cloud, null) },
                    trailingContent = { Text("即将支持", style = MaterialTheme.typography.labelSmall) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("阿里云 CosyVoice") },
                    supportingContent = { Text("AI 语音合成，音色克隆") },
                    leadingContent = { Icon(Icons.Default.AutoAwesome, null) },
                    trailingContent = { Text("即将支持", style = MaterialTheme.typography.labelSmall) }
                )
            }

            // Quick settings
            item {
                Text("快捷调节", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.primary)
            }
            item {
                var pitch by remember { mutableFloatStateOf(1.0f) }
                var speed by remember { mutableFloatStateOf(1.0f) }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("音调: $pitch")
                    Slider(value = pitch, onValueChange = { pitch = it; ttsManager.setPitch(it) },
                        valueRange = 0.5f..2.0f)
                    Text("语速: $speed")
                    Slider(value = speed, onValueChange = { speed = it; ttsManager.setSpeed(it) },
                        valueRange = 0.5f..2.0f)
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private data class VoicePreset(
    val name: String,
    val description: String,
    val engineType: String,
    val voiceId: String
)
