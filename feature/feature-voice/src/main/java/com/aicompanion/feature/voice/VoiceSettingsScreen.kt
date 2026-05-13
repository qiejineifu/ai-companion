package com.aicompanion.feature.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    ttsManager: TTSManager,
    onBack: () -> Unit
) {
    val ttsState = ttsManager.state
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("语音设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("TTS 引擎", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("当前引擎") },
                supportingContent = { Text(ttsState.engine.name) }
            )
            ListItem(
                headlineContent = { Text("朗读状态") },
                supportingContent = { Text(if (ttsState.isSpeaking) "正在朗读..." else "空闲") }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text("音调", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = pitch, onValueChange = {
                    pitch = it
                    ttsManager.setPitch(it)
                },
                valueRange = 0.5f..2.0f
            )

            Text("语速", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = speed, onValueChange = {
                    speed = it
                    ttsManager.setSpeed(it)
                },
                valueRange = 0.5f..2.0f
            )

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { ttsManager.speak("你好，这是一条测试语音。") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("测试朗读")
            }
        }
    }
}
