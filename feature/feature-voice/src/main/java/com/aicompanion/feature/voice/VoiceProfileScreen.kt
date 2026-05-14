package com.aicompanion.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.domain.model.VoiceProfile
import com.aicompanion.domain.repository.VoiceRepository

private val Pink50 = Color(0xFFFFF0F5)
private val Pink100 = Color(0xFFFFE0EC)
private val Pink400 = Color(0xFFFF85A2)
private val Pink500 = Color(0xFFFF6B8A)
private val Pink600 = Color(0xFFF04F7A)
private val Pink700 = Color(0xFFE0386A)
private val ChatBg = Color(0xFFFFF5F7)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceProfileScreen(
    voiceRepository: VoiceRepository,
    ttsManager: TTSManager,
    onBack: () -> Unit
) {
    val profiles by voiceRepository.getProfiles().collectAsState(initial = emptyList())
    val activeProfile = profiles.firstOrNull { it.isActive }
    val scope = rememberCoroutineScope()
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        TopAppBar(
            title = { Text("音色管理", color = Color.White) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            modifier = Modifier.background(Brush.horizontalGradient(listOf(Pink500, Pink600, Pink700)))
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            // Active
            if (activeProfile != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Pink50)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("当前音色", fontSize = 12.sp, color = Pink500)
                            Text(activeProfile.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                            Text("引擎: ${activeProfile.engineType} · 音调: ${activeProfile.pitch} · 语速: ${activeProfile.speed}",
                                color = TextGray, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                ttsManager.speak("你好，这是${activeProfile.name}的语音测试。", activeProfile)
                            }) {
                                Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("试听")
                            }
                        }
                    }
                }
            }

            // Preset voices
            item {
                Text("预设音色", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Pink500,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
            }

            val presets = listOf(
                VoicePresetData("温柔女声", "知性温柔的女性声音", "system", "zh-CN-female-1"),
                VoicePresetData("元气少女", "活泼可爱的少女音色", "system", "zh-CN-female-2"),
                VoicePresetData("沉稳男声", "温暖沉稳的男性声音", "system", "zh-CN-male-1"),
                VoicePresetData("清澈女声", "清亮透明的女性声音", "system", "zh-CN-female-4"),
            )

            items(presets) { preset ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            scope.launch {
                                val profile = VoiceProfile(
                                    id = newId(), name = preset.name,
                                    engineType = preset.engineType, voiceId = preset.voiceId,
                                    pitch = pitch, speed = speed,
                                    isActive = true, createdAt = now()
                                )
                                voiceRepository.createProfile(profile)
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RecordVoiceOver, null, tint = Pink400)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.Medium, color = TextDark)
                            Text(preset.description, fontSize = 13.sp, color = TextGray)
                        }
                        Text("点击使用", fontSize = 12.sp, color = Pink500)
                    }
                }
            }

            // Custom profiles
            val custom = profiles.filter { !presets.any { p -> p.name == it.name } }
            if (custom.isNotEmpty()) {
                item {
                    Text("已保存", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Pink500,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
                }
                items(custom) { profile ->
                    val isActive = profile.id == activeProfile?.id
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                scope.launch { voiceRepository.setActive(profile.id) }
                            },
                        colors = CardDefaults.cardColors(containerColor = if (isActive) Pink50 else Color.White)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.name, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, color = TextDark)
                                Text("引擎: ${profile.engineType}", fontSize = 13.sp, color = TextGray)
                            }
                            if (isActive) Icon(Icons.Default.Check, null, tint = Pink500)
                            IconButton(onClick = {
                                scope.launch { voiceRepository.deleteProfile(profile.id) }
                            }) { Icon(Icons.Default.Delete, "删除", tint = TextGray, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }

            // Pitch/speed
            item {
                Text("快捷调节", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Pink500,
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("音调: ${"%.1f".format(pitch)}", color = TextDark, fontSize = 14.sp)
                    Slider(value = pitch, onValueChange = { pitch = it; ttsManager.setPitch(it) },
                        valueRange = 0.5f..2.0f, colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500))
                    Text("语速: ${"%.1f".format(speed)}", color = TextDark, fontSize = 14.sp)
                    Slider(value = speed, onValueChange = { speed = it; ttsManager.setSpeed(it) },
                        valueRange = 0.5f..2.0f, colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500))
                }
            }
        }
    }
}

private data class VoicePresetData(
    val name: String, val description: String, val engineType: String, val voiceId: String
)
