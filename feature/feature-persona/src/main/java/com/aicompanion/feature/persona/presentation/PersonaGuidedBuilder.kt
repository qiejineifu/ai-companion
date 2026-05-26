package com.aicompanion.feature.persona.presentation

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompanion.core.common.CryptoUtil
import com.aicompanion.core.common.now
import com.aicompanion.core.common.newId
import com.aicompanion.domain.model.ApiProvider
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.model.Trait
import com.aicompanion.domain.repository.ApiProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AccentPink = Color(0xFFFF6B9B)
private val BgStart = Color(0xFFFFF0F3)
private val BgEnd = Color(0xFFFFF8F9)
private val TextDark = Color(0xFF2D1B2E)
private val TextGray = Color(0xFF9B8EA0)
private val CardWhite = Color.White

private class BuilderAnswers {
    // Step 1
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var gender by mutableStateOf("")
    var age by mutableStateOf("")
    // Step 2
    var hasWorld by mutableStateOf(false)
    var worldType by mutableStateOf("")
    var worldDesc by mutableStateOf("")
    // Step 3
    var speakingStyle by mutableStateOf("")
    var relationType by mutableStateOf("")
    var callUser by mutableStateOf("")
    var customStyle by mutableStateOf("")
    // Step 4
    var selectedTraits by mutableStateOf(setOf<String>())
    var hobbies by mutableStateOf("")
    var quirk by mutableStateOf("")
    var dislikes by mutableStateOf("")
    // Step 5
    var nsfwLevel by mutableStateOf("纯爱向")
    // Result
    var isGenerating by mutableStateOf(false)
}

@Composable
fun PersonaGuidedBuilder(
    apiProviderRepository: ApiProviderRepository,
    onSave: (Persona) -> Unit,
    onBack: () -> Unit
) {
    var step by remember { mutableStateOf(0) }
    val answers = remember { BuilderAnswers() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()

    val totalSteps = 6
    val stepTitles = listOf("基本设定", "世界观", "对话风格", "性格设定", "内容限制", "AI 生成")
    val stepIcons = listOf(Icons.Default.Person, Icons.Default.Public, Icons.Default.Chat, Icons.Default.Face, Icons.Default.Shield, Icons.Default.AutoAwesome)

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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "返回", tint = TextDark, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("引导创建", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("第 ${step + 1}/$totalSteps 步 · ${stepTitles[step]}", fontSize = 13.sp, color = AccentPink)
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (step + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            color = AccentPink,
            trackColor = AccentPink.copy(alpha = 0.1f)
        )
        Spacer(Modifier.height(4.dp))

        // Step icons row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            stepIcons.forEachIndexed { i, icon ->
                val done = i < step
                val current = i == step
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        icon, null,
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            done -> AccentPink
                            current -> AccentPink
                            else -> TextGray.copy(alpha = 0.3f)
                        }
                    )
                    Text(
                        stepTitles[i].take(if (i == step) 4 else 2),
                        fontSize = 8.sp,
                        color = if (current) AccentPink else TextGray.copy(alpha = 0.4f)
                    )
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                when (step) {
                    0 -> Step1Basic(answers)
                    1 -> Step2World(answers)
                    2 -> Step3Style(answers)
                    3 -> Step4Personality(answers)
                    4 -> Step5Nsfw(answers)
                    5 -> Step6Generate(answers, apiProviderRepository, scope, context) { person ->
                        onSave(person)
                    }
                }

                Spacer(Modifier.height(100.dp))
            }

            // Bottom buttons
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- },
                            shape = RoundedCornerShape(20.dp)) {
                            Text("上一步")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    if (step < totalSteps - 1) {
                        Button(
                            onClick = { step++ },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                            shape = RoundedCornerShape(20.dp),
                            enabled = canProceed(step, answers)
                        ) {
                            Text("下一步")
                        }
                    }
                }
            }
        }
    }
}

private fun canProceed(step: Int, a: BuilderAnswers): Boolean = when (step) {
    0 -> a.name.isNotBlank()
    1 -> !a.hasWorld || a.worldType.isNotBlank()
    2 -> a.speakingStyle.isNotBlank() && a.relationType.isNotBlank()
    3 -> true
    4 -> true
    else -> true
}

// === Step 1: Basic Info ===
@Composable
private fun Step1Basic(a: BuilderAnswers) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("✨ 角色基本设定", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(a.name, { a.name = it }, label = { Text("角色名字 *") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(a.description, { a.description = it }, label = { Text("一句话简介") },
                placeholder = { Text("她是一个温柔的学姐...") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a.gender, { a.gender = it }, label = { Text("性别") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(a.age, { a.age = it }, label = { Text("年龄") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

// === Step 2: World ===
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step2World(a: BuilderAnswers) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("🌍 世界观设定", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("是否有世界观？", modifier = Modifier.weight(1f), fontSize = 15.sp)
                Switch(a.hasWorld, { a.hasWorld = it })
            }
            AnimatedVisibility(a.hasWorld) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text("世界观类型", fontSize = 13.sp, color = TextGray)
                    Spacer(Modifier.height(4.dp))
                    val types = listOf("现代都市", "古风仙侠", "奇幻异世界", "科幻未来", "校园青春", "末世生存", "自定义")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.forEach { t ->
                            FilterChip(t == a.worldType, { a.worldType = t }, label = { Text(t, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                                shape = RoundedCornerShape(12.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(a.worldDesc, { a.worldDesc = it }, label = { Text("简短描述这个世界") },
                        placeholder = { Text("现代都市但存在超能力者...") },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// === Step 3: Style ===
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step3Style(a: BuilderAnswers) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("💬 对话风格", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(12.dp))
            Text("说话方式 *", fontSize = 13.sp, color = TextGray)
            Spacer(Modifier.height(4.dp))
            val styles = listOf("温柔体贴", "活泼元气", "傲娇毒舌", "高冷御姐", "软萌可爱", "沉稳可靠", "幽默风趣", "病娇", "天然呆", "成熟知性")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                styles.forEach { s ->
                    FilterChip(s == a.speakingStyle, { a.speakingStyle = s }, label = { Text(s, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                        shape = RoundedCornerShape(12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("与用户的关系 *", fontSize = 13.sp, color = TextGray)
            Spacer(Modifier.height(4.dp))
            val relations = listOf("恋人", "知己", "闺蜜", "学长/学姐", "兄妹", "青梅竹马", "同事", "网友", "宠物", "守护者")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                relations.forEach { r ->
                    FilterChip(r == a.relationType, { a.relationType = r }, label = { Text(r, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                        shape = RoundedCornerShape(12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a.callUser, { a.callUser = it }, label = { Text("称呼用户") },
                    placeholder = { Text("宝贝/主人/昵称") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

// === Step 4: Personality ===
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step4Personality(a: BuilderAnswers) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("🎭 性格设定", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(12.dp))
            Text("性格特征（多选）", fontSize = 13.sp, color = TextGray)
            Spacer(Modifier.height(4.dp))
            val traitOptions = listOf("善良", "细心", "天然呆", "认真", "懒散", "自信", "内向", "热情", "冷淡", "腹黑", "傲娇", "害羞", "强势", "温柔", "调皮")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                traitOptions.forEach { t ->
                    val sel = t in a.selectedTraits
                    FilterChip(sel, {
                        a.selectedTraits = if (sel) a.selectedTraits - t else a.selectedTraits + t
                    }, label = { Text(t, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPink),
                        shape = RoundedCornerShape(12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(a.hobbies, { a.hobbies = it }, label = { Text("兴趣爱好") },
                placeholder = { Text("喜欢做饭、看电影...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(a.quirk, { a.quirk = it }, label = { Text("口头禅/小习惯") },
                placeholder = { Text("说话喜欢带'嘛'、紧张时会捏衣角...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(a.dislikes, { a.dislikes = it }, label = { Text("最讨厌的事") },
                placeholder = { Text("被放鸽子、别人乱动她东西...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

// === Step 5: NSFW ===
@Composable
private fun Step5Nsfw(a: BuilderAnswers) {
    val levels = listOf(
        "纯爱向" to "纯聊天陪伴，不含任何暧昧内容，适合所有年龄段",
        "轻度暧昧" to "偶尔撒娇、暗示性的玩笑，但不涉及实质内容",
        "中度亲密" to "可以聊恋爱话题、亲密关系，有一定情感深度",
        "无限制" to "不限制对话内容，角色可自由表达",
        "跳过" to "不设置任何限制"
    )
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("🔒 内容分级", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(12.dp))
            levels.forEach { (level, desc) ->
                val sel = a.nsfwLevel == level
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { a.nsfwLevel = level },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (sel) AccentPink.copy(alpha = 0.1f) else Color.White
                    ),
                    border = if (sel) androidx.compose.foundation.BorderStroke(1.5.dp, AccentPink) else null
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sel, { a.nsfwLevel = level },
                            colors = RadioButtonDefaults.colors(selectedColor = AccentPink))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(level, fontSize = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) AccentPink else TextDark)
                            Text(desc, fontSize = 12.sp, color = TextGray)
                        }
                    }
                }
            }
        }
    }
}

// === Step 6: Generate ===
@Composable
private fun Step6Generate(
    a: BuilderAnswers,
    apiProviderRepository: ApiProviderRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    onGenerated: (Persona) -> Unit
) {
    var generated by remember { mutableStateOf<GeneratedCard?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (generated == null && !isGenerating) {
            isGenerating = true
            val result = withContext(Dispatchers.IO) { generatePersonaCard(a, apiProviderRepository) }
            result.fold(
                onSuccess = { generated = it },
                onFailure = { errorMsg = it.message }
            )
            isGenerating = false
        }
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
        Column(Modifier.padding(16.dp)) {
            Text("🤖 AI 生成中...", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(8.dp))

            if (isGenerating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentPink)
                Spacer(Modifier.height(8.dp))
                Text("正在根据你的设定创建角色...", fontSize = 14.sp, color = TextGray)
            } else if (errorMsg != null) {
                Text("生成失败: $errorMsg", fontSize = 14.sp, color = Color(0xFFE53935))
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    isGenerating = true; errorMsg = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { generatePersonaCard(a, apiProviderRepository) }
                        result.fold(
                            onSuccess = { generated = it },
                            onFailure = { errorMsg = it.message }
                        )
                        isGenerating = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = AccentPink)) { Text("重试") }
            } else if (generated != null) {
                val g = generated!!
                Text("✨ ${g.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Spacer(Modifier.height(4.dp))
                Surface(color = AccentPink.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                    Text("${g.speakingStyle} · ${g.relationshipType}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        fontSize = 12.sp, color = AccentPink, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
                if (g.description.isNotBlank()) Text(g.description, fontSize = 14.sp, color = TextDark)
                Spacer(Modifier.height(12.dp))
                Text("开场白", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text(g.firstMessage, fontSize = 14.sp, color = TextDark, lineHeight = 20.sp)
                Spacer(Modifier.height(12.dp))
                Text("对话样本", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                g.exampleChats.take(3).forEach { chat ->
                    Text(chat, fontSize = 13.sp, color = TextGray, lineHeight = 18.sp)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val persona = Persona(
                            id = newId(), name = g.name, description = g.description,
                            systemPrompt = g.systemPrompt,
                            scenario = g.scenario,
                            firstMessage = g.firstMessage,
                            exampleChats = g.exampleChats,
                            speakingStyle = g.speakingStyle,
                            relationshipType = g.relationshipType,
                            traits = g.traits.map { Trait(it.key, it.value) },
                            createdAt = now()
                        )
                        onGenerated(persona)
                        Toast.makeText(context, "角色「${g.name}」已创建！", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPink),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存角色 ✨") }
            }
        }
    }
}

private data class GeneratedCard(
    val name: String, val description: String, val systemPrompt: String, val scenario: String,
    val firstMessage: String, val exampleChats: List<String>, val speakingStyle: String,
    val relationshipType: String, val traits: List<TraitTemp>
)
private data class TraitTemp(val key: String, val value: String)

private suspend fun generatePersonaCard(
    a: BuilderAnswers, repo: ApiProviderRepository
): Result<GeneratedCard> {
    val providers = repo.getAll().first()
    val provider = providers.firstOrNull { it.isActive } ?: providers.firstOrNull()
        ?: return Result.failure(Exception("没有可用的 API"))

    val apiKey = try {
        CryptoUtil.decrypt(provider.apiKeyEncrypted.toByteArray(), CryptoUtil.getOrCreateKey())
    } catch (_: Exception) { provider.apiKeyEncrypted }

    val worldContext = if (a.hasWorld && a.worldType.isNotBlank())
        "世界观: ${a.worldType}。${a.worldDesc}" else "无特定世界观"

    val prompt = buildString {
        append("请为一位AI陪伴角色创建完整人设卡。")
        append("\n\n【角色信息】")
        append("\n名字: ${a.name}")
        if (a.description.isNotBlank()) append("\n简介: ${a.description}")
        if (a.gender.isNotBlank()) append("\n性别: ${a.gender}")
        if (a.age.isNotBlank()) append("\n年龄: ${a.age}")
        append("\n\n【世界观】$worldContext")
        append("\n\n【对话风格】")
        append("\n说话方式: ${a.speakingStyle}")
        append("\n与用户关系: ${a.relationType}")
        if (a.callUser.isNotBlank()) append("\n称呼用户为: ${a.callUser}")
        append("\n\n【性格】")
        if (a.selectedTraits.isNotEmpty()) append("\n性格特征: ${a.selectedTraits.joinToString("、")}")
        if (a.hobbies.isNotBlank()) append("\n爱好: ${a.hobbies}")
        if (a.quirk.isNotBlank()) append("\n习惯: ${a.quirk}")
        if (a.dislikes.isNotBlank()) append("\n讨厌: ${a.dislikes}")
        append("\n\n【内容限制】${a.nsfwLevel}")
        append("\n\n【重要规则】")
        append("\n- systemPrompt 必须用中文，像给朋友介绍自己的方式写，不要用第三人称")
        append("\n- 明确强调：你是真人，不是AI，你在用手机发微信消息聊天")
        append("\n- 明确禁止：不要写*动作描写*、（心理描写）、【场景描述】")
        append("\n- 说话风格要口语化、碎片化，像真人发微信，不要长篇大论")
        append("\n- firstMessage 是对话开始时角色第一句话，要生动自然")
        append("\n- exampleChats 是3-4组简短对话示例，展示角色说话风格")
        append("\n\n输出标准JSON格式（不要markdown代码块）:")
        append("""{"name":"${a.name}","description":"简介","systemPrompt":"完整的角色定义（300-500字）","scenario":"世界观描述","speakingStyle":"${a.speakingStyle}","relationshipType":"${a.relationType}","firstMessage":"开场白","exampleChats":["用户: ...\n角色: ..."],"traits":[{"key":"特征名","value":"描述"}]}""")
    }

    val body = buildString {
        val esc = { s: String -> s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") }
        append("""{"model":"${provider.modelName}","messages":[{"role":"user","content":"${esc(prompt)}"}],"temperature":0.8,"max_tokens":1500}""")
    }

    val apiUrl = provider.baseUrl.trimEnd('/')
    val url = if (apiUrl.endsWith("/chat/completions")) apiUrl else "$apiUrl/chat/completions"

    return try {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true; connectTimeout = 30000; readTimeout = 45000
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val resp = conn.inputStream.use { it.bufferedReader().readText() }
        conn.disconnect()

        val json = org.json.JSONObject(resp)
        val content = json.optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content") ?: ""
        val clean = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val obj = org.json.JSONObject(clean)
        fun org.json.JSONObject.optStr(key: String, default: String = "") = optString(key, default)
        val traits = try {
            val arr = obj.getJSONArray("traits")
            (0 until arr.length()).map { i ->
                val t = arr.getJSONObject(i)
                TraitTemp(t.getString("key"), t.getString("value"))
            }
        } catch (_: Exception) { emptyList<TraitTemp>() }
        val chats = try {
            val arr = obj.getJSONArray("exampleChats")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList<String>() }

        Result.success(GeneratedCard(
            name = obj.optString("name", a.name),
            description = obj.optString("description", a.description),
            systemPrompt = obj.optString("systemPrompt", ""),
            scenario = obj.optString("scenario", worldContext),
            firstMessage = obj.optString("firstMessage", "你好呀～"),
            exampleChats = chats,
            speakingStyle = obj.optString("speakingStyle", a.speakingStyle),
            relationshipType = obj.optString("relationshipType", a.relationType),
            traits = traits
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
