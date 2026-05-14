package com.aicompanion.feature.settings.presentation

import android.widget.Toast
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.core.common.ProactiveSettings
import com.aicompanion.core.ui.theme.*
import com.aicompanion.domain.model.Persona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProactiveMessageSettingsScreen(
    onBack: () -> Unit,
    onReschedule: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    personas: List<Persona> = emptyList()
) {
    val context = LocalContext.current
    val settings = remember { ProactiveSettings(context) }

    var enabled by remember { mutableStateOf(settings.enabled) }
    var mode by remember { mutableStateOf(settings.mode) }
    var fixedInterval by remember { mutableStateOf(settings.fixedIntervalMinutes.toString()) }
    var randomMin by remember { mutableStateOf(settings.randomMinMinutes.toString()) }
    var randomMax by remember { mutableStateOf(settings.randomMaxMinutes.toString()) }
    var selectedIds by remember { mutableStateOf(settings.selectedPersonaIds) }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(topBarGradient))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
            Text("AI主动消息", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enable switch
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.NotificationsActive, null, tint = Pink500, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("开启主动消息", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                        Text("AI会在后台随机发消息给你，像真人一样主动联系", fontSize = 13.sp, color = TextGray)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            settings.enabled = it
                            if (it) onReschedule?.invoke() else onCancel?.invoke()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Pink500, checkedTrackColor = Pink100)
                    )
                }
            }

            if (enabled) {
                // Mode selection
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("发送模式", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDark)
                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { mode = "fixed" }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = mode == "fixed",
                                onClick = { mode = "fixed" },
                                colors = RadioButtonDefaults.colors(selectedColor = Pink500)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("固定间隔", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextDark)
                                Text("每隔固定时间发送一条消息", fontSize = 12.sp, color = TextGray)
                            }
                        }

                        if (mode == "fixed") {
                            OutlinedTextField(
                                value = fixedInterval,
                                onValueChange = {
                                    fixedInterval = it
                                    it.toIntOrNull()?.let { v -> settings.fixedIntervalMinutes = v.coerceIn(5, 1440) }
                                },
                                label = { Text("间隔时间（分钟）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                                suffix = { Text("分钟") }
                            )
                        }

                        HorizontalDivider(color = DividerPink, modifier = Modifier.padding(vertical = 4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { mode = "random" }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = mode == "random",
                                onClick = { mode = "random" },
                                colors = RadioButtonDefaults.colors(selectedColor = Pink500)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("随机时间", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextDark)
                                Text("在一个时间范围内随机选择，更有真人感", fontSize = 12.sp, color = TextGray)
                            }
                        }

                        if (mode == "random") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = randomMin,
                                    onValueChange = {
                                        randomMin = it
                                        it.toIntOrNull()?.let { v -> settings.randomMinMinutes = v.coerceIn(5, 1440) }
                                    },
                                    label = { Text("最小") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    suffix = { Text("分钟") }
                                )
                                Text("~", modifier = Modifier.align(Alignment.CenterVertically), color = TextGray)
                                OutlinedTextField(
                                    value = randomMax,
                                    onValueChange = {
                                        randomMax = it
                                        it.toIntOrNull()?.let { v -> settings.randomMaxMinutes = v.coerceIn(5, 1440) }
                                    },
                                    label = { Text("最大") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    suffix = { Text("分钟") }
                                )
                            }
                        }
                    }
                }

                // Persona selection
                if (personas.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Face, null, tint = Pink500, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("选择可主动发消息的角色", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDark)
                            }
                            Text("未选择则所有角色都会参与", fontSize = 12.sp, color = TextGray,
                                modifier = Modifier.padding(start = 28.dp, bottom = 8.dp))
                            personas.forEach { persona ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            selectedIds = if (persona.id in selectedIds)
                                                selectedIds - persona.id
                                            else selectedIds + persona.id
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = persona.id in selectedIds,
                                        onCheckedChange = {
                                            selectedIds = if (it) selectedIds + persona.id
                                            else selectedIds - persona.id
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Pink500)
                                    )
                                    Text(persona.name, fontSize = 14.sp, color = TextDark)
                                    Text(" · ${persona.speakingStyle}", fontSize = 12.sp, color = TextGray)
                                }
                            }
                        }
                    }
                }

                // Save button
                Button(
                    onClick = {
                        settings.mode = mode
                        settings.fixedIntervalMinutes = fixedInterval.toIntOrNull() ?: 60
                        settings.randomMinMinutes = randomMin.toIntOrNull() ?: 30
                        settings.randomMaxMinutes = randomMax.toIntOrNull() ?: 120
                        settings.selectedPersonaIds = selectedIds
                        onReschedule?.invoke()
                        val min = settings.getNextDelayMinutes()
                        Toast.makeText(context, "已设置，约 ${min} 分钟后收到第一条消息", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("应用并重新调度", fontSize = 16.sp)
                }

                // Info card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Pink50)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Pink400, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("工作原理", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Pink600)
                            Spacer(Modifier.height(4.dp))
                            Text("• AI会随机选择一个角色\n• 生成一条简短的日常消息\n• 通过系统通知栏推送到你的手机\n• 点击通知可打开对应角色的对话",
                                fontSize = 13.sp, color = TextGray, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
