package com.aicompanion.feature.settings.presentation

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import com.aicompanion.core.common.ImageGenConfig
import com.aicompanion.core.common.ImageGenSettings
import com.aicompanion.core.common.ImageGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

private val AccentPink = Color(0xFFFF6B9B)
private val BgStart = Color(0xFFFFF0F3)
private val BgEnd = Color(0xFFFFF8F9)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageGenSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val config = remember { ImageGenConfig(context) }
    val settings = remember { mutableStateOf(config.load()) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgStart, BgEnd)))
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextDark, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("生图设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("多模态图片生成配置", fontSize = 13.sp, color = TextGray)
            }
        }

        // Enable toggle
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, null, tint = AccentPink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("启用生图功能", modifier = Modifier.weight(1f), fontSize = 16.sp, color = TextDark)
                Switch(
                    checked = settings.value.enabled,
                    onCheckedChange = { settings.value = settings.value.copy(enabled = it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentPink, checkedTrackColor = AccentPink.copy(alpha = 0.2f))
                )
            }
        }

        if (settings.value.enabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("API 配置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(Modifier.height(8.dp))

                    // Provider selector
                    Text("服务商", fontSize = 13.sp, color = TextGray)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val providers = listOf("openai" to "OpenAI / 硅基流动", "modelscope" to "魔搭 API", "dashscope" to "阿里云百炼")
                        providers.forEach { (key, label) ->
                            FilterChip(
                                selected = settings.value.provider == key,
                                onClick = { settings.value = settings.value.copy(provider = key) },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    // Model quick-select for ModelScope
                    if (settings.value.provider == "modelscope") {
                        Spacer(Modifier.height(8.dp))
                        Text("魔搭模型", fontSize = 13.sp, color = TextGray)
                        Spacer(Modifier.height(4.dp))
                        val msModels = listOf(
                            "Tongyi-MAI/Z-Image" to "通义万相",
                            "Tongyi-MAI/Z-Image-Turbo" to "通义万相 Turbo",
                            "Kwai-Kolors/Kolors-Diffusion" to "Kolors 可图",
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            msModels.forEach { (id, label) ->
                                FilterChip(
                                    selected = settings.value.model == id,
                                    onClick = { settings.value = settings.value.copy(model = id) },
                                    label = { Text("$label", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Model quick-select for DashScope
                    if (settings.value.provider == "dashscope") {
                        Spacer(Modifier.height(8.dp))
                        Text("魔搭模型选择", fontSize = 13.sp, color = TextGray)
                        Spacer(Modifier.height(4.dp))
                        val dashModels = listOf(
                            "wan2.1-t2i-turbo" to "万相 2.1 文生图",
                            "wan2.2-t2i-preview" to "万相 2.2 预览",
                            "flux-schnell" to "FLUX Schnell",
                            "flux-dev" to "FLUX Dev",
                            "sd-turbo" to "Stable Diffusion Turbo"
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dashModels.forEach { (id, label) ->
                                FilterChip(
                                    selected = settings.value.model == id,
                                    onClick = { settings.value = settings.value.copy(model = id) },
                                    label = { Text("$label\n$id", fontSize = 10.sp, maxLines = 2) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(settings.value.apiUrl, { settings.value = settings.value.copy(apiUrl = it) },
                        label = { Text("API URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(when (settings.value.provider) { "dashscope" -> "https://dashscope.aliyuncs.com"; "modelscope" -> "https://api-inference.modelscope.cn"; else -> "https://api.siliconflow.cn" }) })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(settings.value.apiKeyEncrypted, { settings.value = settings.value.copy(apiKeyEncrypted = it) },
                        label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(settings.value.model, { settings.value = settings.value.copy(model = it) },
                        label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(when (settings.value.provider) { "dashscope" -> "wan2.1-t2i-turbo"; "modelscope" -> "Tongyi-MAI/Z-Image"; else -> "black-forest-labs/FLUX.1-dev" }) })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(settings.value.size, { settings.value = settings.value.copy(size = it) },
                        label = { Text("图片尺寸") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("1024x1024") })
                }
            }
        }

        // Save button
        Button(
            onClick = {
                config.save(settings.value)
                Toast.makeText(context, "生图配置已保存", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
            shape = RoundedCornerShape(20.dp)
        ) { Text("保存配置") }

        // Test generation
        if (settings.value.enabled) {
            var testPrompt by remember { mutableStateOf("一只可爱的猫在阳光下打盹") }
            var isGenerating by remember { mutableStateOf(false) }
            var generatedUri by remember { mutableStateOf<String?>(null) }
            var errorMsg by remember { mutableStateOf<String?>(null) }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("测试生成", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(testPrompt, { testPrompt = it },
                        label = { Text("图片描述") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            isGenerating = true; generatedUri = null; errorMsg = null
                            scope.launch {
                                val uri = withContext(Dispatchers.IO) {
                                    ImageGenerator.generate(context, settings.value, testPrompt)
                                }
                                if (uri != null) generatedUri = uri
                                else errorMsg = "生成失败，请检查配置和 API Key"
                                isGenerating = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isGenerating
                    ) {
                        if (isGenerating) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("开始生成")
                    }
                    errorMsg?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, fontSize = 14.sp, color = Color(0xFFE53935))
                    }
                    generatedUri?.let { uri ->
                        Spacer(Modifier.height(12.dp))
                        AsyncImage(
                            model = uri, contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text("✅ 生成成功，已保存", fontSize = 12.sp, color = Color(0xFF4CAF50))
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
