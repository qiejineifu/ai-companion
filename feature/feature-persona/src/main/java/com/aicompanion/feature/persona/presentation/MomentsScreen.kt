package com.aicompanion.feature.persona.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.aicompanion.core.ui.theme.*
import com.aicompanion.domain.model.MomentEntry
import com.aicompanion.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MomentsScreen(
    persona: Persona,
    onBack: () -> Unit,
    onUpdatePersona: (Persona) -> Unit = {},
    onGenerateNow: () -> Unit = {}
) {
    val sorted = remember(persona.moments) { persona.moments.sortedByDescending { it.createdAt } }
    var showSettings by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(topBarGradient))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${persona.name}的朋友圈", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${sorted.size} 条动态", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "设置", tint = Color.White)
            }
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有发过朋友圈", color = TextGray, fontSize = 14.sp)
                    Text("在设置中开启自动生成", color = TextGray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                var lastDateLabel = ""
                sorted.forEachIndexed { index, moment ->
                    val dateFormat = SimpleDateFormat("MM月dd日", Locale.getDefault())
                    val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    val today = todayFormat.format(Date())
                    val dayOfYear = SimpleDateFormat("yyyyDDD", Locale.getDefault()).format(Date(moment.createdAt))
                    val todayDayOfYear = SimpleDateFormat("yyyyDDD", Locale.getDefault()).format(Date())

                    val dateLabel = when {
                        dayOfYear == todayDayOfYear -> "今天"
                        dayOfYear == (todayDayOfYear.toInt() - 1).toString() -> "昨天"
                        else -> dateFormat.format(Date(moment.createdAt))
                    }

                    val showDateDivider = dateLabel != lastDateLabel
                    if (showDateDivider) {
                        lastDateLabel = dateLabel
                        item(key = "date_$dateLabel") {
                            Text(
                                dateLabel, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = TextGray, fontSize = 13.sp
                            )
                        }
                    }

                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(moment.createdAt))

                    item(key = moment.id) {
                        WeChatMomentCard(
                            personaName = persona.name,
                            avatarUri = persona.avatarImageUri,
                            content = moment.content,
                            time = timeStr,
                            location = moment.location,
                            mood = moment.mood,
                            imageUri = moment.imageUri
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    // Settings dialog
    if (showSettings) {
        var enabled by remember { mutableStateOf(persona.momentsEnabled) }
        var randomMode by remember { mutableStateOf(persona.momentsRandomMode) }
        var fixedMinutes by remember { mutableStateOf(persona.momentsIntervalMinutes.toString()) }
        var minMinutes by remember { mutableStateOf(persona.momentsRandomMinMinutes.toString()) }
        var maxMinutes by remember { mutableStateOf(persona.momentsRandomMaxMinutes.toString()) }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("朋友圈设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动生成", modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                    if (enabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("随机间隔", modifier = Modifier.weight(1f))
                            Switch(checked = randomMode, onCheckedChange = { randomMode = it })
                        }
                        if (randomMode) {
                            OutlinedTextField(value = minMinutes, onValueChange = { minMinutes = it },
                                label = { Text("最少(分钟)") }, modifier = Modifier.width(120.dp))
                            OutlinedTextField(value = maxMinutes, onValueChange = { maxMinutes = it },
                                label = { Text("最多(分钟)") }, modifier = Modifier.width(120.dp))
                        } else {
                            OutlinedTextField(value = fixedMinutes, onValueChange = { fixedMinutes = it },
                                label = { Text("间隔(分钟)") }, modifier = Modifier.width(140.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var isGenerating by remember { mutableStateOf(false) }
                    val ctx = LocalContext.current
                    OutlinedButton(
                        onClick = {
                            if (!isGenerating) {
                                isGenerating = true
                                onGenerateNow()
                                android.widget.Toast.makeText(ctx, "已提交生成任务，稍后刷新查看", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Pink500)
                        else Text("立即生成")
                    }
                    TextButton(onClick = {
                    showSettings = false
                    val updated = persona.copy(
                        momentsEnabled = enabled,
                        momentsRandomMode = randomMode,
                        momentsIntervalMinutes = fixedMinutes.toIntOrNull() ?: 60,
                        momentsRandomMinMinutes = minMinutes.toIntOrNull() ?: 30,
                        momentsRandomMaxMinutes = maxMinutes.toIntOrNull() ?: 120
                    )
                    onUpdatePersona(updated)
                    }) { Text("保存") }
                }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun WeChatMomentCard(
    personaName: String,
    avatarUri: String?,
    content: String,
    time: String,
    location: String?,
    mood: String?,
    imageUri: String? = null
) {
    val avatarColors = listOf(
        Pink400, Color(0xFF64B5F6), Color(0xFF81C784),
        Color(0xFFFFB74D), Color(0xFFCE93D8), Color(0xFF4DD0E1)
    )
    val colorIndex = kotlin.math.abs(personaName.hashCode()) % avatarColors.size

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape)
                .background(avatarColors[colorIndex]),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(personaName.take(1), fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name
            Text(personaName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF576B95))

            Spacer(Modifier.height(4.dp))

            // Content
            Text(content, fontSize = 15.sp, color = TextDark, lineHeight = 22.sp)

            // Image (if generated)
            imageUri?.let { uri ->
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = uri, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Mood tag
            if (!mood.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("💬 $mood", fontSize = 11.sp, color = TextGray)
            }

            Spacer(Modifier.height(8.dp))

            // Bottom row: time + location
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time, fontSize = 12.sp, color = TextGray)
                if (!location.isNullOrBlank()) {
                    Spacer(Modifier.width(12.dp))
                    Text("📍 $location", fontSize = 12.sp, color = Pink500)
                }
            }
        }
    }

    HorizontalDivider(color = DividerPink.copy(alpha = 0.2f), thickness = 0.5.dp,
        modifier = Modifier.padding(start = 72.dp, end = 16.dp))
}

@Composable
private fun topBarGradient() = listOf(Pink500, Pink600)
