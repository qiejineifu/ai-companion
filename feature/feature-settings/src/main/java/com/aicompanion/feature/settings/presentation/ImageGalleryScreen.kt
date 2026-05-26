package com.aicompanion.feature.settings.presentation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import java.io.File

private val AccentPink = Color(0xFFFF6B9B)
private val BgStart = Color(0xFFFFF0F3)
private val BgEnd = Color(0xFFFFF8F9)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)

data class GalleryImage(val file: File, val uri: String, val isFavorite: Boolean)

@Composable
fun ImageGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var images by remember { mutableStateOf(loadImages(context)) }
    var selectedImage by remember { mutableStateOf<GalleryImage?>(null) }
    var confirmDelete by remember { mutableStateOf<GalleryImage?>(null) }

    // Refresh on return
    LaunchedEffect(Unit) { images = loadImages(context) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgStart, BgEnd)))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextDark, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("生成相册", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("${images.size} 张图片", fontSize = 13.sp, color = TextGray)
            }
        }

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🖼️", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有生成的图片", color = TextGray)
                    Text("生图后会自动出现在这里", color = TextGray, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(images, key = { it.file.name }) { img ->
                    Box(
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .clickable { selectedImage = img }
                    ) {
                        AsyncImage(
                            model = img.uri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Favorite indicator
                        if (img.isFavorite) {
                            Icon(
                                Icons.Default.Favorite, null,
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(16.dp).align(Alignment.TopEnd).padding(4.dp)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Full-screen preview
    selectedImage?.let { img ->
        AlertDialog(
            onDismissRequest = { selectedImage = null },
            title = { Text(img.file.name, maxLines = 1) },
            text = {
                AsyncImage(
                    model = img.uri, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        toggleFavorite(context, img.file.name)
                        images = loadImages(context)
                        selectedImage = null
                    }) {
                        Icon(
                            if (img.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null, modifier = Modifier.size(18.dp),
                            tint = if (img.isFavorite) Color(0xFFFF4081) else TextGray
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (img.isFavorite) "取消收藏" else "收藏")
                    }
                    TextButton(onClick = { confirmDelete = img; selectedImage = null }) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = Color(0xFFE53935))
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = Color(0xFFE53935))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selectedImage = null }) { Text("关闭") } }
        )
    }

    // Delete confirmation
    confirmDelete?.let { img ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除图片") },
            text = { Text("确定要删除这张图片吗？") },
            confirmButton = {
                TextButton(onClick = {
                    img.file.delete()
                    images = loadImages(context)
                    confirmDelete = null
                }) { Text("删除", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
}

private fun loadImages(context: Context): List<GalleryImage> {
    val dir = File(context.filesDir, "generated_images")
    if (!dir.exists()) return emptyList()
    val favs = context.getSharedPreferences("img_favs", Context.MODE_PRIVATE)
    return dir.listFiles()?.filter { it.extension == "png" }?.sortedByDescending { it.lastModified() }
        ?.map { GalleryImage(it, it.toURI().toString(), favs.getBoolean(it.name, false)) }
        ?: emptyList()
}

private fun toggleFavorite(context: Context, filename: String) {
    val prefs = context.getSharedPreferences("img_favs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(filename, !prefs.getBoolean(filename, false)).apply()
}
