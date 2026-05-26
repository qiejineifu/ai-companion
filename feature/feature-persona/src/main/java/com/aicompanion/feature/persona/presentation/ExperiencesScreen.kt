package com.aicompanion.feature.persona.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.aicompanion.core.ui.theme.*
import com.aicompanion.domain.model.ExperienceEntry
import com.aicompanion.domain.model.Persona
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExperiencesScreen(
    persona: Persona,
    onBack: () -> Unit,
    onUpdatePersona: (Persona) -> Unit = {},
    onGenerateNow: () -> Unit = {}
) {
    val sorted = remember(persona.experiences) { persona.experiences.sortedByDescending { it.createdAt } }
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
                Text("${persona.name}的最近经历", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${sorted.size} 篇经历", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "设置", tint = Color.White)
            }
        }

        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📖", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有记录经历", color = TextGray, fontSize = 14.sp)
                    Text("在设置中开启自动生成", color = TextGray, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                sorted.forEachIndexed { index, exp ->
                    val dateStr = SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(exp.createdAt))
                    val categoryColors = mapOf(
                        "日常" to Color(0xFF81C784),
                        "冒险" to Color(0xFFFFB74D),
                        "奇遇" to Color(0xFF64B5F6),
                        "回忆" to Color(0xFFCE93D8),
                        "成长" to Color(0xFF4DD0E1)
                    )

                    item(key = exp.id) {
                        ExperienceCard(
                            title = exp.title,
                            content = exp.content,
                            category = exp.category,
                            date = dateStr,
                            categoryColor = categoryColors[exp.category] ?: Pink400
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showSettings) {
        var enabled by remember { mutableStateOf(persona.experiencesEnabled) }
        var randomMode by remember { mutableStateOf(persona.experiencesRandomMode) }
        var fixedHours by remember {
            mutableStateOf((persona.experiencesIntervalMinutes / 60).toString())
        }
        var minHours by remember {
            mutableStateOf((persona.experiencesRandomMinMinutes / 60).toString())
        }
        var maxHours by remember {
            mutableStateOf((persona.experiencesRandomMaxMinutes / 60).toString())
        }

        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("最近经历设置") },
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
                            OutlinedTextField(value = minHours, onValueChange = { minHours = it },
                                label = { Text("最少(小时)") }, modifier = Modifier.width(120.dp))
                            OutlinedTextField(value = maxHours, onValueChange = { maxHours = it },
                                label = { Text("最多(小时)") }, modifier = Modifier.width(120.dp))
                        } else {
                            OutlinedTextField(value = fixedHours, onValueChange = { fixedHours = it },
                                label = { Text("间隔(小时)") }, modifier = Modifier.width(140.dp))
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
                        if (isGenerating) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Pink400)
                        else Text("立即生成")
                    }
                    TextButton(onClick = {
                        showSettings = false
                        val updated = persona.copy(
                            experiencesEnabled = enabled,
                            experiencesRandomMode = randomMode,
                            experiencesIntervalMinutes = (fixedHours.toIntOrNull() ?: 12) * 60,
                            experiencesRandomMinMinutes = (minHours.toIntOrNull() ?: 6) * 60,
                            experiencesRandomMaxMinutes = (maxHours.toIntOrNull() ?: 24) * 60
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
private fun ExperienceCard(
    title: String,
    content: String,
    category: String,
    date: String,
    categoryColor: Color
) {
    var expanded by remember { mutableStateOf(false) }
    val displayContent = if (expanded) content else content.take(120)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Category chip + date
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = categoryColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "$category · $date",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // Title
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)

            Spacer(Modifier.height(8.dp))

            // Content
            Text(
                if (expanded || content.length <= 120) content else displayContent + "…",
                fontSize = 14.sp, color = TextDark.copy(alpha = 0.85f), lineHeight = 22.sp
            )

            if (content.length > 120) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (expanded) "收起" else "展开阅读",
                    fontSize = 13.sp, color = Pink500, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            }

            // Timeline indicator
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(6.dp).background(Pink200, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    date,
                    fontSize = 12.sp, color = TextGray
                )
            }
        }
    }
}

@Composable
private fun topBarGradient() = listOf(Pink600, Pink400)
