package com.aicompanion.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.core.common.VoicePrefData
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AccentPink = Color(0xFFFF6B9B)
private val BgStart = Color(0xFFFFF0F3)
private val BgEnd = Color(0xFFFFF8F9)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)

private data class ModelOption(val name: String, val dir: String, val file: String, val voices: String)

private val availableModels = listOf(
    ModelOption("AISHELL-3 (174音色)·默认", "sherpa_models/tts/aishell3", "vits-aishell3.int8.onnx", "稳定，覆盖各年龄层"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    ttsManager: TTSManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val ttsState = ttsManager.state
    val voicePrefs = remember { com.aicompanion.core.common.VoicePrefs(context) }
    val savedPrefs = remember { voicePrefs.load() }
    var pitch by remember { mutableFloatStateOf(savedPrefs.pitch) }
    var speed by remember { mutableFloatStateOf(savedPrefs.speed) }
    var selectedModel by remember { mutableIntStateOf(savedPrefs.selectedModelIndex.coerceIn(0, availableModels.lastIndex)) }
    var testText by remember { mutableStateOf("你好，这是一条测试语音。") }
    var sid by remember { mutableIntStateOf(savedPrefs.sid) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgStart, BgEnd)))
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextDark, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("语音设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text(ttsState.engine.name, fontSize = 13.sp, color = AccentPink)
            }
        }

        // Engine status card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, null, tint = AccentPink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TTS 引擎", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        Text(
                            when (ttsState.engine) {
                                TTSEngineType.SHERPA_ONNX -> "离线 VITS · 已就绪"
                                TTSEngineType.IFlyTEK -> "云端 CosyVoice · 已连接"
                                TTSEngineType.SYSTEM -> "系统 TTS · 备用模式"
                                else -> ttsState.engine.name
                            },
                            fontSize = 13.sp,
                            color = when {
                                ttsState.engine == TTSEngineType.SHERPA_ONNX -> Color(0xFF4CAF50)
                                ttsState.engine == TTSEngineType.IFlyTEK -> Color(0xFF2196F3)
                                else -> TextGray
                            }
                        )
                    }
                    Surface(
                        color = when (ttsState.engine) {
                            TTSEngineType.SHERPA_ONNX -> Color(0xFFE8F5E9)
                            TTSEngineType.IFlyTEK -> Color(0xFFE3F2FD)
                            else -> Color(0xFFFFF3E0)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            when (ttsState.engine) {
                                TTSEngineType.SHERPA_ONNX -> "离线"
                                TTSEngineType.IFlyTEK -> "云端"
                                else -> "在线"
                            },
                            fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            color = when (ttsState.engine) {
                                TTSEngineType.SHERPA_ONNX -> Color(0xFF4CAF50)
                                TTSEngineType.IFlyTEK -> Color(0xFF2196F3)
                                else -> Color(0xFFFF9800)
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                if (ttsState.isSpeaking) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentPink)
                    Text("正在朗读...", fontSize = 12.sp, color = AccentPink)
                }
            }
        }

        // DashScope API Key
        var apiKey by remember { mutableStateOf(voicePrefs.getApiKey()) }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("DashScope TTS 云端", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(Modifier.weight(1f))
                    var cloudEnabled by remember { mutableStateOf(voicePrefs.isCloudEnabled()) }
                    Switch(
                        checked = cloudEnabled,
                        onCheckedChange = {
                            cloudEnabled = it
                            voicePrefs.setCloudEnabled(it)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentPink)
                    )
                }
                Text("Chat 用 DeepSeek，TTS 用 DashScope，互不影响", fontSize = 12.sp, color = TextGray)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; voicePrefs.saveApiKey(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("DashScope API Key") },
                    placeholder = { Text("sk-...") },
                    trailingIcon = {
                        if (apiKey.isNotBlank()) {
                            IconButton(onClick = { apiKey = ""; voicePrefs.saveApiKey("") }) {
                                Icon(Icons.Default.Close, "清除", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                var voice by remember { mutableStateOf(voicePrefs.getVoice()) }
                var voiceDropdown by remember { mutableStateOf(false) }
                val voiceOptions = listOf(
                    "cosyvoice-v1:zhitian_emo" to "CosyVoice · 知甜 (活泼女声)",
                    "cosyvoice-v1:zhiyan_emo" to "CosyVoice · 知燕 (知性女声)",
                    "cosyvoice-v1:zhixiang_emo" to "CosyVoice · 知祥 (温暖男声)",
                    "cosyvoice-v1:zhichu_emo" to "CosyVoice · 知楚 (沉稳女声)",
                    "cosyvoice-v1:zhifei_emo" to "CosyVoice · 知菲 (温柔女声)",
                    "qwen-tts:zhitian_emo" to "Qwen-TTS · 知甜",
                    "sambert-zhide-v1" to "Sambert · 知德",
                    "sambert-zhitian-v1" to "Sambert · 知甜",
                )
                ExposedDropdownMenuBox(
                    expanded = voiceDropdown,
                    onExpandedChange = { voiceDropdown = it }
                ) {
                    OutlinedTextField(
                        value = voiceOptions.firstOrNull { it.first == voice }?.second ?: voice,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        label = { Text("发音人") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceDropdown) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = voiceDropdown,
                        onDismissRequest = { voiceDropdown = false }
                    ) {
                        voiceOptions.forEach { (model, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text("$label\n$model", fontSize = 13.sp, color = if (voice == model) AccentPink else TextDark)
                                },
                                onClick = {
                                    voice = model
                                    voiceDropdown = false
                                    voicePrefs.save(VoicePrefData(selectedModel, sid, pitch, speed, apiKey, model))
                                },
                                leadingIcon = if (voice == model) {
                                    { Icon(Icons.Default.Check, "选中", tint = AccentPink, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                }
                Text(
                    if (apiKey.isNotBlank()) "已配置 · 有网自动用云端" else "未配置 · 始终用离线 VITS",
                    fontSize = 11.sp,
                    color = if (apiKey.isNotBlank()) Color(0xFF4CAF50) else TextGray
                )
            }
        }

        // Model info (模型在进入电话模式时自动加载)
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("离线模型", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Text("模型在进入电话模式时自动加载，选择后退出重进电话模式生效", fontSize = 12.sp, color = TextGray)
                Spacer(Modifier.height(8.dp))
                availableModels.forEachIndexed { i, model ->
                    val sel = selectedModel == i
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (sel) AccentPink.copy(alpha = 0.08f) else Color.White
                        ),
                        border = if (sel) androidx.compose.foundation.BorderStroke(1.5.dp, AccentPink) else null,
                        onClick = {
                            selectedModel = i
                            voicePrefs.save(VoicePrefData(i, sid, pitch, speed, apiKey))
                            Toast.makeText(context, "已选择 ${model.name}，退出并重新进入电话模式生效", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(sel, {
                                selectedModel = i
                                voicePrefs.save(VoicePrefData(i, sid, pitch, speed, apiKey))
                                Toast.makeText(context, "已选择 ${model.name}，退出并重新进入电话模式生效", Toast.LENGTH_SHORT).show()
                            }, colors = RadioButtonDefaults.colors(selectedColor = AccentPink))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(model.name, fontSize = 14.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) AccentPink else TextDark)
                                Text("${model.voices}", fontSize = 12.sp, color = TextGray)
                            }
                        }
                    }
                }
            }
        }

        // Speaker ID selector
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("音色选择 (Speaker ID)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Text("AISHELL-3: 0-173，不同编号对应不同声线", fontSize = 12.sp, color = TextGray)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (sid > 0) sid-- }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Remove, null, tint = AccentPink)
                    }
                    Text(
                        "$sid", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AccentPink,
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { if (sid < 173) sid++ }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Add, null, tint = AccentPink)
                    }
                }
                Slider(
                    value = sid.toFloat(), onValueChange = { sid = it.toInt() },
                    valueRange = 0f..173f, steps = 172,
                    colors = SliderDefaults.colors(thumbColor = AccentPink, activeTrackColor = AccentPink)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val model = availableModels.getOrElse(selectedModel) { availableModels[0] }
                    OutlinedButton(
                        onClick = {
                            val s = sid; val d = model.dir; val f = model.file
                            if (voicePrefs.isCloudEnabled() && apiKey.isNotBlank())
                                ttsManager.speakCloud("你好，今天天气真不错呢", apiKey, voicePrefs.getVoice())
                            else
                                ttsManager.speakLazyLoad("你好，今天天气真不错呢", s, d, f)
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                        enabled = !ttsState.isSpeaking
                    ) { Text("试听", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = {
                            val s = sid; val d = model.dir; val f = model.file
                            if (voicePrefs.isCloudEnabled() && apiKey.isNotBlank())
                                ttsManager.speakCloud("我今天特别开心，因为你来看我了", apiKey, voicePrefs.getVoice())
                            else
                                ttsManager.speakLazyLoad("我今天特别开心，因为你来看我了", s, d, f)
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                        enabled = !ttsState.isSpeaking
                    ) { Text("试听2", fontSize = 13.sp) }
                }
            }
        }

        // Pitch & speed
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("音调", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Slider(
                    value = pitch, onValueChange = { pitch = it; ttsManager.setPitch(it); voicePrefs.save(VoicePrefData(selectedModel, sid, pitch, speed, apiKey)) },
                    valueRange = 0.5f..2.0f, colors = SliderDefaults.colors(thumbColor = AccentPink, activeTrackColor = AccentPink)
                )
                Text("语速", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Slider(
                    value = speed, onValueChange = { speed = it; ttsManager.setSpeed(it); voicePrefs.save(VoicePrefData(selectedModel, sid, pitch, speed, apiKey)) },
                    valueRange = 0.5f..2.0f, colors = SliderDefaults.colors(thumbColor = AccentPink, activeTrackColor = AccentPink)
                )
            }
        }

        // Test
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("测试朗读", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = testText, onValueChange = { testText = it },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("测试文本") }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val s = sid; val m = availableModels.getOrElse(selectedModel) { availableModels[0] }
                        if (voicePrefs.isCloudEnabled() && apiKey.isNotBlank())
                            ttsManager.speakCloud(testText, apiKey, voicePrefs.getVoice())
                        else
                            ttsManager.speakLazyLoad(testText, s, m.dir, m.file)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !ttsState.isSpeaking
                ) { Text(if (ttsState.isSpeaking) "朗读中..." else "测试朗读 🔊") }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
