package com.aicompanion.feature.chat.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aicompanion.domain.model.StickerItem
import com.aicompanion.domain.repository.StickerRepository
import kotlinx.coroutines.launch
import java.io.File

private val Pink50 = Color(0xFFFFF0F5)
private val Pink100 = Color(0xFFFFE0EC)
private val Pink400 = Color(0xFFFF85A2)
private val Pink500 = Color(0xFFFF6B8A)
private val Pink600 = Color(0xFFF04F7A)
private val Pink700 = Color(0xFFE0386A)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)
private val ChatBg = Color(0xFFFFF5F7)

val EmotionLabels = mapOf(
    "happy" to "😊 开心",
    "sad" to "😢 难过",
    "angry" to "😠 生气",
    "surprised" to "😲 惊讶",
    "shy" to "😳 害羞",
    "thinking" to "🤔 思考",
    "neutral" to "😶 平静"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerManageScreen(
    personaId: String,
    repository: StickerRepository,
    onBack: () -> Unit
) {
    val stickers by repository.getByPersona(personaId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val dest = File(context.filesDir, "stickers/${System.currentTimeMillis()}.png")
            dest.parentFile?.mkdirs()
            context.contentResolver.openInputStream(it)?.use { src ->
                dest.outputStream().use { out -> src.copyTo(out) }
            }
            pendingImagePath = dest.absolutePath
            showAddDialog = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(ChatBg)) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Pink500, Pink600, Pink700)))
                .statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = Color.White) }
            Text("表情包管理", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Default.Add, "导入", tint = Color.White) }
        }

        // Emotion filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = selectedFilter == null, onClick = { selectedFilter = null },
                label = { Text("全部", fontSize = 12.sp) })
            EmotionLabels.forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = if (selectedFilter == key) null else key },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pink100)
                )
            }
        }

        val display = if (selectedFilter == null) stickers else stickers.filter { it.emotion == selectedFilter }

        if (display.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😊", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有表情包", color = TextGray)
                    Text("点击右上角 + 导入图片", color = TextGray, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(display, key = { it.id }) { sticker ->
                    Box(modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Pink100)
                    ) {
                        AsyncImage(model = sticker.imagePath, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        // Emotion label
                        Text(
                            EmotionLabels[sticker.emotion] ?: sticker.emotion,
                            fontSize = 10.sp, color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        // Delete
                        IconButton(
                            onClick = { scope.launch { repository.delete(sticker.id) } },
                            modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Default.Close, "删除", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // Add dialog: choose emotion
    if (showAddDialog && pendingImagePath != null) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingImagePath = null },
            title = { Text("选择表情情绪", fontWeight = FontWeight.Bold, color = TextDark) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EmotionLabels.forEach { (key, label) ->
                        ListItem(
                            headlineContent = { Text(label, color = TextDark, fontSize = 16.sp) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    repository.addSticker(personaId, key, pendingImagePath!!)
                                    showAddDialog = false
                                    pendingImagePath = null
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddDialog = false; pendingImagePath = null }) { Text("取消") } }
        )
    }
}
