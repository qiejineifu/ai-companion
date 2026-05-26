package com.aicompanion.feature.persona.presentation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aicompanion.core.common.TavernCardParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgStart = Color(0xFFFFF0F3)
private val BgEnd = Color(0xFFFFF8F9)
private val AccentPink = Color(0xFFFF6B9B)
private val TextDark = Color(0xFF2D1B2E)
private val TextLight = Color(0xFFB0A0B0)

data class CardPreview(
    val fileName: String,
    val parsed: TavernCardParser.ParsedCard?,
    val bytes: ByteArray
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RensheShichangScreen(
    viewModel: PersonaViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<CardPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        cards = withContext(Dispatchers.IO) {
            try {
                val files = context.assets.list("rensheshichang")
                    ?.filter { it.endsWith(".png") } ?: emptyList()
                files.map { fileName ->
                    val bytes = context.assets.open("rensheshichang/$fileName").use { it.readBytes() }
                    val parsed = TavernCardParser.parseFromBytes(bytes)
                    CardPreview(fileName = fileName, parsed = parsed, bytes = bytes)
                }
            } catch (e: Exception) {
                loadError = "加载失败: ${e.message}"
                emptyList()
            }
        }
        isLoading = false
    }

    val existingNames = state.personas.map { it.name }

    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearImportMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgStart, BgEnd)))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextDark, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("人设市场", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("${cards.size} 张角色卡", fontSize = 13.sp, color = TextLight)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPink)
            }
        } else if (loadError != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(loadError!!, color = TextLight, fontSize = 14.sp)
            }
        } else if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🐰", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("没有找到角色卡", color = TextLight, fontSize = 15.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(cards, key = { it.fileName }) { card ->
                    val alreadyImported = card.parsed?.name?.let { it in existingNames } == true
                    CardMarketCard(
                        card = card,
                        alreadyImported = alreadyImported,
                        onClick = {
                            if (!alreadyImported && card.parsed != null) {
                                viewModel.importParsedCard(card.parsed, card.bytes)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun CardMarketCard(
    card: CardPreview,
    alreadyImported: Boolean,
    onClick: () -> Unit
) {
    val parsed = card.parsed
    val displayName = parsed?.name ?: card.fileName.removeSuffix(".png").take(20)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background: the PNG itself
            AsyncImage(
                model = "file:///android_asset/rensheshichang/${card.fileName}",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x00000000), Color(0x44000000), Color(0xBB000000))
                        )
                    )
            )

            // Already imported badge
            if (alreadyImported) {
                Surface(
                    color = Color(0xFF6BD4A0).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text("已导入", fontSize = 10.sp, color = Color.White,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            // Bottom info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (parsed != null) {
                    if (parsed.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(parsed.description, fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (parsed.tags.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(parsed.tags.take(3).joinToString(" · "), fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
