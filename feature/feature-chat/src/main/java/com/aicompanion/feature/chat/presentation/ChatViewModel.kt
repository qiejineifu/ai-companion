package com.aicompanion.feature.chat.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.core.common.*
import com.aicompanion.domain.model.*
import com.aicompanion.domain.model.StickerItem
import com.aicompanion.domain.repository.*
import com.aicompanion.domain.usecase.*
import com.aicompanion.feature.memory.MemoryExtractor
import com.aicompanion.feature.voice.STTEvent
import com.aicompanion.feature.voice.SherpaOnnxSTT
import com.aicompanion.feature.voice.STTResult
import android.content.Context
import com.aicompanion.core.common.UnreadTracker
import com.aicompanion.feature.live2d.Live2DManager
import com.aicompanion.feature.voice.STTManager
import com.aicompanion.feature.voice.TTSManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val apiProviderRepository: ApiProviderRepository,
    private val memoryRepository: MemoryRepository,
    private val stickerRepository: StickerRepository,
    private val worldBookRepository: WorldBookRepository,
    private val sttManager: STTManager,
    private val onnxSTT: SherpaOnnxSTT,
    private val ttsManager: TTSManager,
    private val voiceRepository: VoiceRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val stopGenerationUseCase: StopGenerationUseCase,
    private val createConversationUseCase: CreateConversationUseCase,
    private val live2DManager: Live2DManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private val memoryExtractor = MemoryExtractor()
    private var messagesJob: Job? = null

    init {
        Log.d("AIC", "ChatViewModel init START")
        sttManager.initialize()
        ttsManager.initialize()
        // Collect conversations
        viewModelScope.launch {
            getConversationsUseCase().collect { convs ->
                _state.update { it.copy(conversations = convs) }
            }
        }
        // Load active persona & provider
        viewModelScope.launch {
            personaRepository.getAll().collect { personas ->
                Log.d("AIC", "Personas loaded: ${personas.size}")
                if (personas.isNotEmpty() && _state.value.activePersona == null) {
                    Log.d("AIC", "Auto-selecting persona: ${personas.first().name}")
                    _state.update { it.copy(activePersona = personas.first()) }
                }
            }
        }
        viewModelScope.launch {
            apiProviderRepository.getAll().collect { providers ->
                Log.d("AIC", "Providers loaded: ${providers.size}, activeCount=${providers.count { it.isActive }}")
                val active = providers.firstOrNull { it.isActive }
                    ?: providers.firstOrNull()
                if (active != null) {
                    Log.d("AIC", "Using provider: ${active.name}, apiKeyLen=${active.apiKeyEncrypted.length}")
                    _state.update { it.copy(activeApiProvider = active) }
                } else {
                    Log.d("AIC", "NO providers available!")
                }
            }
        }
        // Observe STT results
        viewModelScope.launch {
            sttManager.events.collect { event ->
                when (event) {
                    is STTEvent.PartialResult -> {
                        _state.update { it.copy(inputText = event.text) }
                    }
                    is STTEvent.FinalResult -> {
                        _state.update { it.copy(inputText = event.text, isRecording = false) }
                        if (_state.value.callMode && event.text.isNotBlank()) {
                            sendMessage(event.text)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun setPendingConversation(id: String) {
        _state.update { it.copy(pendingConversationId = id) }
    }

    /** Synchronously load a conversation from DB and set it as active. Used before user can interact. */
    suspend fun loadConversationSync(convId: String) {
        val conv = chatRepository.getConversationById(convId) ?: return
        _state.update {
            it.copy(
                activeConversation = conv,
                pendingConversationId = convId,
                conversations = if (it.conversations.none { c -> c.id == conv.id })
                    it.conversations + conv else it.conversations
            )
        }
    }

    fun processIntent(intent: ChatIntent) {
        Log.d("AIC", "processIntent: ${intent.javaClass.simpleName}")
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.SendVoice -> toggleVoiceInput()
            is ChatIntent.StopGeneration -> stopGeneration()
            is ChatIntent.DeleteMessage -> deleteMessage(intent.messageId)
            is ChatIntent.SelectConversation -> selectConversation(intent.id)
            is ChatIntent.NewConversation -> createNewConversation()
            is ChatIntent.ShowGroupPicker -> showGroupPicker()
            is ChatIntent.StartGroupChat -> startGroupChat(intent.personaIds, intent.conversationId)
            is ChatIntent.SelectPersona -> selectPersona(intent.personaId)
            is ChatIntent.ToggleVoiceMode -> toggleVoiceMode()
            is ChatIntent.ToggleCallMode -> toggleCallMode()
            is ChatIntent.Regenerate -> regenerate(intent.messageId)
            is ChatIntent.SwitchBranch -> switchBranch(intent.branchKey, intent.index)
            is ChatIntent.ToggleSidebar -> toggleSidebar()
            is ChatIntent.UpdateInput -> updateInput(intent.text)
            is ChatIntent.DismissError -> dismissError()
            is ChatIntent.StartReply -> startReply(intent.messageId)
            is ChatIntent.CancelReply -> cancelReply()
        }
    }

    private fun sendMessage(text: String) {
        val currentState = _state.value
        val conversation = currentState.activeConversation
        val persona = currentState.activePersona
        val provider = currentState.activeApiProvider

        if (text.isBlank()) return
        if (persona == null) {
            _state.update { it.copy(error = "请先选择一个人设") }
            return
        }
        if (provider == null) {
            _state.update { it.copy(error = "请先配置 API Key") }
            return
        }
        if (provider.apiKeyEncrypted.isBlank()) {
            _state.update { it.copy(error = "API Key 不能为空，请在设置中配置") }
            return
        }

        Log.d("AIC", "Validation passed, launching coroutine...")
        viewModelScope.launch {
            ttsManager.stop()
            _state.update { it.copy(inputText = "", streamState = StreamState.Connecting, currentStickerUri = null, replyTarget = null) }

            // Ensure conversation exists
            var conv = conversation
            if (conv == null) {
                // Try pending conversation (group chat from navigation)
                // Retry with delay: startGroupChat runs in a coroutine, may not have finished yet
                val pendingId = _state.value.pendingConversationId
                if (pendingId != null) {
                    repeat(5) { attempt ->
                        conv = chatRepository.getConversationById(pendingId)
                        if (conv != null) {
                            Log.d("AIC", "Loaded pending conversation (attempt ${attempt+1}): ${conv!!.id} group=${conv!!.isGroupChat}")
                            _state.update { it.copy(activeConversation = conv, pendingConversationId = null) }
                            return@repeat
                        }
                        if (attempt < 4) kotlinx.coroutines.delay(100)
                    }
                }
                // If still null, create new ordinary conversation
                if (conv == null) {
                    Log.d("AIC", "Creating new conversation...")
                    val result = chatRepository.createConversation(
                        personaId = persona.id,
                        apiProviderId = provider.id,
                        title = text.take(30)
                    )
                    result.onSuccess { conv = it }
                    result.onError { _state.update { s -> s.copy(streamState = StreamState.Error(it)) } }
                    _state.update { it.copy(pendingConversationId = null) }
                }
                if (conv == null) { Log.d("AIC", "FAILED to create conversation"); return@launch }
                _state.update { it.copy(activeConversation = conv) }
                Log.d("AIC", "Conversation active: ${conv!!.id} group=${conv!!.isGroupChat}")
            }

            val memories = memoryRepository.searchRelevant(persona.id, text, Constants.DEFAULT_MEMORY_TOP_K)
            memories.forEach { memoryRepository.markAccessed(it.id) }
            val recentMessages = getMessagesUseCase(conv!!.id).first()
            Log.d("AIC", "Got messages=${recentMessages.size}, memories=${memories.size}")

            // Inject firstMessage as opening if this is a fresh conversation
            val isNewConversation = recentMessages.isEmpty()
            if (isNewConversation && persona.firstMessage.isNotBlank() && conv?.isGroupChat != true) {
                // Only for non-group chats - group chats rely on AI response
                val openingMsg = MessageUi(
                    id = newId(), role = "assistant", content = persona.firstMessage,
                    createdAt = now() - 1
                )
                _state.update { it.copy(messages = it.messages + openingMsg) }
            }

            val worldBookEntries = worldBookRepository.getEnabledByPersona(persona.id)
            // Load group personas for group chats
            val groupPersonas = if (conv?.isGroupChat == true && conv.groupPersonaIds.isNotEmpty()) {
                conv.groupPersonaIds.mapNotNull { personaRepository.getById(it) }
                    .filter { it.id != persona.id }
            } else emptyList()
            val replyTarget = currentState.replyTarget
            val mood = analyzeMood(recentMessages)
            val effectiveProvider = provider.copy(modelName = persona.modelName ?: provider.modelName)
            Log.d("AIC", "sendMessage using model=${effectiveProvider.modelName}, persona.modelName=${persona.modelName}, provider.modelName=${provider.modelName}")
            val context = ChatContext(
                persona = persona, messages = recentMessages,
                memories = memories, provider = effectiveProvider,
                worldBookEntries = worldBookEntries,
                groupPersonas = groupPersonas,
                replyTargetContent = replyTarget?.content,
                replyTargetSenderName = replyTarget?.senderPersonaName,
                conversationMood = mood
            )
            val userMsg = MessageUi(id = newId(), role = "user", content = text, createdAt = now())
            _state.update { it.copy(messages = it.messages + userMsg) }

            if (conv?.isGroupChat == true) {
                // === GROUP CHAT: Swap-card round-robin ===
                val allPersonaIds = conv.groupPersonaIds
                Log.d("AIC", "Group chat personaIds from conv: $allPersonaIds")
                val allPersonas = allPersonaIds.mapNotNull { id ->
                    val p = personaRepository.getById(id)
                    if (p == null) Log.w("AIC", "Group chat: persona id=$id NOT FOUND")
                    p
                }
                Log.d("AIC", "Group chat with ${allPersonas.size} personas: ${allPersonas.map { it.name }}")

                // Save user message to DB
                chatRepository.saveMessage(Message(
                    id = userMsg.id, conversationId = conv.id,
                    role = "user", content = text, createdAt = userMsg.createdAt
                ))

                speakInGroupRoundRobin(conv, effectiveProvider, userMsg, allPersonas, recentMessages, memories, worldBookEntries, text)
            } else {
                // === SINGLE CHAT: Original flow ===
                // Stream response
                Log.d("AIC", "Starting SSE stream to ${provider.baseUrl}")
                sendMessageUseCase(conv!!.id, text, context).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val emotion = detectEmotion(result.data)
                            if (_state.value.currentStickerUri == null && emotion != null) {
                                val sticker = stickerRepository.getRandomByEmotion(persona.id, emotion.name.lowercase())
                                if (sticker != null) _state.update { it.copy(currentStickerUri = sticker.imagePath) }
                            }
                            _state.update { it.copy(streamState = StreamState.Streaming(result.data, emotion), currentEmotion = emotion ?: Emotion.NEUTRAL) }
                        }
                        is Result.Error -> {
                            Log.e("AIC", "SSE error: ${result.message}")
                            _state.update { it.copy(streamState = StreamState.Error(result.message)) }
                        }
                    }
                }
                // Complete
                val s = _state.value
                when {
                    s.streamState is StreamState.Streaming -> {
                        val finalText = (s.streamState as StreamState.Streaming).partialText

                        if (persona.waifuMode) {
                            _state.update { it.copy(streamState = StreamState.Idle) }
                            processWaifuResponse(finalText, persona, conv!!.id)
                        } else {
                            val assistantMsg = MessageUi(id = newId(), role = "assistant", content = finalText, emotion = detectEmotion(finalText)?.label, createdAt = now())
                            _state.update { it.copy(streamState = StreamState.Idle, messages = it.messages + assistantMsg) }
                            speakWithTts(finalText)
                        }
                        // Regex extraction (sync)
                        val extractedMemories = memoryExtractor.extractFromText(text + " " + finalText, persona.id)
                        extractedMemories.forEach { memoryRepository.save(it) }
                        // LLM extraction (async, non-blocking)
                        launch(Dispatchers.IO) {
                            val llmMemories = memoryExtractor.extractWithLLM(
                                listOf(text), listOf(finalText), persona.id, effectiveProvider
                            )
                            llmMemories.forEach { memoryRepository.save(it) }
                        }
                    }
                    s.streamState is StreamState.Error -> {}
                    else -> _state.update { it.copy(streamState = StreamState.Idle, error = "未收到 AI 回复") }
                }
            }
        }
    }

    private fun stopGeneration() {
        viewModelScope.launch {
            stopGenerationUseCase()
            _state.update { it.copy(streamState = StreamState.Idle) }
        }
    }

    private fun selectConversation(id: String) {
        messagesJob?.cancel()
        val conv = _state.value.conversations.find { c -> c.id == id }
        if (conv != null) UnreadTracker.clear(appContext, conv.personaId)
        messagesJob = viewModelScope.launch {
            // Load conversation from DB if not in memory yet
            var conv = _state.value.conversations.find { c -> c.id == id }
            if (conv == null) {
                conv = chatRepository.getConversationById(id)
                if (conv != null) {
                    _state.update { it.copy(conversations = it.conversations + conv) }
                }
            }
            if (conv != null) {
                _state.update { it.copy(activeConversation = conv) }
            }

            // Pre-load group persona info for enriching messages
            val groupPersonas = if (conv?.isGroupChat == true && conv.groupPersonaIds.isNotEmpty()) {
                conv.groupPersonaIds.mapNotNull { personaRepository.getById(it) }
            } else emptyList()

            getMessagesUseCase(id).collect { messages ->
                _state.update {
                    it.copy(
                        activeConversation = it.conversations.find { c -> c.id == id },
                        messages = messages.map { m ->
                            val ui = m.toUi()
                            // Enrich with persona name/avatar for group chat messages
                            if (ui.senderPersonaId != null && ui.senderPersonaName == null) {
                                val p = groupPersonas.find { gp -> gp.id == ui.senderPersonaId }
                                if (p != null) ui.copy(senderPersonaName = p.name, senderAvatarUri = p.avatarImageUri)
                                else ui
                            } else ui
                        }
                    )
                }
            }
        }
    }

    private fun createNewConversation() {
        messagesJob?.cancel()
        _state.update {
            it.copy(
                activeConversation = null,
                messages = emptyList(),
                streamState = StreamState.Idle,
                currentEmotion = Emotion.NEUTRAL,
                retrievedMemories = emptyList(),
                currentStickerUri = null
            )
        }
    }

    private fun selectPersona(personaId: String) {
        UnreadTracker.clear(appContext, personaId)
        viewModelScope.launch {
            personaRepository.getById(personaId)?.let { persona ->
                _state.update { it.copy(activePersona = persona, showPersonaPicker = false) }
                // Only auto-resume if no conversation is already selected (e.g. group chat)
                if (_state.value.activeConversation != null) return@launch
                val latestConv = _state.value.conversations
                    .filter { it.personaId == personaId && !it.isArchived && !it.isGroupChat }
                    .maxByOrNull { it.lastMessageAt }
                if (latestConv != null) {
                    selectConversation(latestConv.id)
                } else {
                    messagesJob?.cancel()
                    _state.update { it.copy(activeConversation = null, messages = emptyList()) }
                }
            }
        }
    }

    private fun deleteMessage(messageId: String) {
        _state.update { it.copy(messages = it.messages.filter { m -> m.id != messageId }) }
    }

    private fun toggleVoiceMode() {
        _state.update { it.copy(voiceMode = !it.voiceMode) }
    }

    private fun toggleCallMode() {
        val entering = !_state.value.callMode
        _state.update { it.copy(callMode = entering, voiceMode = entering) }
        if (entering) {
            viewModelScope.launch(Dispatchers.IO) {
                try { live2DManager.initialize() } catch (_: Exception) {}
                _state.update { it.copy(live2DModel = live2DManager.activeModel) }
                // Load STT on demand when mic is tapped, not here.
                // TTS is loaded on demand per-sentence via speakLazyLoad.
                // This keeps peak native memory low (one model at a time).
            }
        } else {
            silenceTimer?.cancel()
            accumulatedVoiceText = ""
            onnxSTT.stop()
            sttJob?.cancel()
            _state.update { it.copy(isRecording = false) }
            ttsManager.stop()
            ttsManager.disableSherpaOnnx()
            onnxSTT.release()
        }
    }

    /** Speak AI response through TTS if voice mode is on. Cloud TTS when online, offline otherwise. */
    private fun speakWithTts(text: String) {
        if (!_state.value.voiceMode) return
        viewModelScope.launch {
            // Re-check voice mode inside coroutine to avoid stale state
            if (!_state.value.voiceMode) return@launch

            // Release STT before TTS to keep peak memory low
            onnxSTT.release()

            // Try cloud TTS if enabled, online, and DashScope key is set
            val vp = com.aicompanion.core.common.VoicePrefs(appContext)
            if (vp.isCloudEnabled()) {
                val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val online = cm.activeNetwork != null
                if (online) {
                    val apiKey = vp.getApiKey()
                    if (apiKey.isNotBlank()) {
                        ttsManager.speakCloud(text, apiKey, vp.getVoice())
                        return@launch
                    }
                }
            }

            // Fallback to offline AISHELL-3
            val sid = _state.value.activePersona?.voiceSid ?: 0
            val voicePrefs = com.aicompanion.core.common.VoicePrefs(appContext)
            val prefs = voicePrefs.load()
            val models = listOf(
                "sherpa_models/tts/aishell3" to "vits-aishell3.int8.onnx"
            )
            val (dir, file) = models[prefs.selectedModelIndex.coerceIn(0, models.size - 1)]
            ttsManager.speakLazyLoad(text, sid, dir, file)
        }
    }

    private var sttJob: Job? = null

    private var silenceTimer: Job? = null
    private var accumulatedVoiceText = ""

    private fun toggleVoiceInput() {
        if (_state.value.isRecording) {
            silenceTimer?.cancel()
            onnxSTT.stop()
            sttJob?.cancel()
            // In call mode, manual stop also sends accumulated text
            if (_state.value.callMode && accumulatedVoiceText.isNotBlank()) {
                val text = accumulatedVoiceText
                accumulatedVoiceText = ""
                sendMessage(text)
            }
            _state.update { it.copy(isRecording = false) }
        } else {
            val inCallMode = _state.value.callMode
            accumulatedVoiceText = ""
            _state.update { it.copy(isRecording = true, error = null) }
            sttJob = viewModelScope.launch(Dispatchers.IO) {
                if (onnxSTT.initialize() is com.aicompanion.core.common.Result.Error) {
                    _state.update { it.copy(error = "语音引擎未就绪", isRecording = false) }
                    return@launch
                }
                onnxSTT.startStreaming().collect {
                    when (it) {
                        is STTResult.Partial -> {
                            accumulatedVoiceText = it.text
                            _state.update { s -> s.copy(inputText = it.text) }
                            // Call mode: reset 5-second silence timer, auto-send when timer fires
                            if (inCallMode) {
                                silenceTimer?.cancel()
                                silenceTimer = launch {
                                    delay(5000)
                                    if (accumulatedVoiceText.isNotBlank()) {
                                        val text = accumulatedVoiceText
                                        accumulatedVoiceText = ""
                                        _state.update { s -> s.copy(inputText = "", isRecording = false) }
                                        onnxSTT.stop()
                                        sttJob?.cancel()
                                        sendMessage(text)
                                    }
                                }
                            }
                        }
                        is STTResult.Final -> {
                            _state.update { s -> s.copy(inputText = it.text, isRecording = false) }
                            silenceTimer?.cancel()
                            accumulatedVoiceText = ""
                            if (inCallMode && it.text.isNotBlank()) {
                                sendMessage(it.text)
                            }
                        }
                        is STTResult.Error -> {
                            _state.update { s -> s.copy(error = it.message, isRecording = false) }
                            silenceTimer?.cancel()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun showGroupPicker() {
        _state.update { it.copy(showGroupPicker = true) }
    }

    private fun startGroupChat(personaIds: List<String>, convId: String) {
        if (personaIds.size < 2) {
            _state.update { it.copy(error = "请至少选择 2 个角色") }
            return
        }
        viewModelScope.launch {
            // Check if a group chat with the same participants already exists
            val sorted = personaIds.sorted()
            val existing = _state.value.conversations.firstOrNull { conv ->
                conv.isGroupChat && conv.groupPersonaIds.sorted() == sorted && !conv.isArchived
            }
            if (existing != null) {
                _state.update { it.copy(activeConversation = existing, showGroupPicker = false) }
                Log.d("AIC", "Reusing existing group chat: ${existing.id}")
                return@launch
            }

            val personas = personaIds.mapNotNull { personaRepository.getById(it) }
            if (personas.size < 2) return@launch

            val primaryPersona = personas.first()
            val title = "群聊: ${personas.joinToString("、") { it.name }}"
            val provider = _state.value.activeApiProvider ?: return@launch

            val conv = Conversation(
                id = convId, personaId = primaryPersona.id,
                apiProviderId = provider.id, title = title,
                createdAt = now(), lastMessageAt = now(),
                isGroupChat = true, groupPersonaIds = personaIds
            )

            // Save full conversation including group fields
            chatRepository.createFullConversation(conv)
            _state.update { s ->
                s.copy(
                    activeConversation = conv,
                    activePersona = primaryPersona,
                    showGroupPicker = false,
                    messages = emptyList()
                )
            }
        }
    }

    private fun switchBranch(branchKey: String, index: Int) {
        _state.update {
            it.copy(branchSelections = it.branchSelections + (branchKey to index))
        }
    }

    private fun regenerate(messageId: String) {
        val currentState = _state.value
        val conv = currentState.activeConversation ?: return
        val persona = currentState.activePersona ?: return
        val provider = (currentState.activeApiProvider ?: return).let { p ->
            currentState.activePersona?.let { persona -> p.copy(modelName = persona.modelName ?: p.modelName) } ?: p
        }

        // Find the message being regenerated
        val msgIndex = currentState.messages.indexOfFirst { it.id == messageId }
        if (msgIndex < 0) return

        val targetMsg = currentState.messages[msgIndex]
        // Find the preceding user message
        val userMsg = currentState.messages.take(msgIndex).findLast { it.role == "user" } ?: return

        // Find existing branches for this message group
        val branchKey = targetMsg.branchKey
        val existingBranches = currentState.messages.filter { it.branchKey == branchKey }
        val newBranchIndex = existingBranches.size

        viewModelScope.launch {
            _state.update { it.copy(streamState = StreamState.Connecting) }

            val memories = memoryRepository.searchRelevant(persona.id, userMsg.content, Constants.DEFAULT_MEMORY_TOP_K)
            memories.forEach { memoryRepository.markAccessed(it.id) }
            val recentMessages = currentState.messages.take(msgIndex).map {
                Message(id = it.id, conversationId = conv.id, role = it.role,
                    content = it.content, createdAt = it.createdAt)
            }

            val worldBookEntries = worldBookRepository.getEnabledByPersona(persona.id)
            val groupPersonas = if (conv.isGroupChat && conv.groupPersonaIds.isNotEmpty()) {
                conv.groupPersonaIds.mapNotNull { personaRepository.getById(it) }
                    .filter { it.id != persona.id }
            } else emptyList()

            val mood = analyzeMood(recentMessages)
            val context = ChatContext(
                persona = persona, messages = recentMessages,
                memories = memories, provider = provider,
                worldBookEntries = worldBookEntries,
                groupPersonas = groupPersonas,
                replyTargetContent = currentState.replyTarget?.content,
                replyTargetSenderName = currentState.replyTarget?.senderPersonaName,
                conversationMood = mood
            )

            // Remove the old AI message being replaced (all branches of it)
            _state.update { s ->
                s.copy(messages = s.messages.filter { it.branchKey != branchKey })
            }

            // Stream new response
            sendMessageUseCase(conv.id, userMsg.content, context).collect { result ->
                when (result) {
                    is Result.Success -> {
                        _state.update { it.copy(streamState = StreamState.Streaming(result.data)) }
                    }
                    is Result.Error -> {
                        _state.update { it.copy(streamState = StreamState.Error(result.message)) }
                    }
                }
            }

            val s = _state.value
            if (s.streamState is StreamState.Streaming) {
                val finalText = (s.streamState as StreamState.Streaming).partialText

                if (conv.isGroupChat) {
                    val groupPersonas = conv.groupPersonaIds.mapNotNull { personaRepository.getById(it) }
                    val allGroupPersonas = listOf(currentState.activePersona!!) + groupPersonas
                    val splitMsgs = splitGroupResponse(finalText, allGroupPersonas)
                    _state.update {
                        it.copy(
                            streamState = StreamState.Idle,
                            messages = it.messages + splitMsgs
                        )
                    }
                } else {
                    val emotion = detectEmotion(finalText)
                    val newMsg = MessageUi(
                        id = newId(), role = "assistant", content = finalText,
                        emotion = emotion?.label, createdAt = now(),
                        branchParentId = branchKey.takeIf { newBranchIndex > 0 },
                        branchIndex = newBranchIndex
                    )
                    _state.update {
                        it.copy(
                            streamState = StreamState.Idle,
                            messages = it.messages + newMsg,
                            branchSelections = it.branchSelections + (branchKey to newBranchIndex)
                        )
                    }
                    speakWithTts(finalText)
                }
            } else {
                _state.update { it.copy(streamState = StreamState.Idle) }
            }
        }
    }

    private fun toggleSidebar() {
        _state.update { it.copy(showSidebar = !it.showSidebar) }
    }

    private fun updateInput(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    private fun dismissError() {
        _state.update { it.copy(error = null, showGroupPicker = false) }
    }

    private fun startReply(messageId: String) {
        val target = _state.value.messages.find { it.id == messageId } ?: return
        _state.update { it.copy(replyTarget = target) }
    }

    private fun cancelReply() {
        _state.update { it.copy(replyTarget = null) }
    }

    fun updateConversationAvatar(id: String, avatarUri: String) {
        viewModelScope.launch {
            var conv = _state.value.conversations.find { it.id == id }
            if (conv == null && _state.value.activeConversation?.id == id) {
                conv = _state.value.activeConversation
            }
            conv?.let {
                val updated = it.copy(avatarImageUri = avatarUri)
                chatRepository.createFullConversation(updated)
                _state.update { s ->
                    s.copy(
                        conversations = s.conversations.map { c -> if (c.id == id) updated else c },
                        activeConversation = if (s.activeConversation?.id == id) updated else s.activeConversation
                    )
                }
            }
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            // Find in list or activeConversation
            var conv = _state.value.conversations.find { it.id == id }
            if (conv == null && _state.value.activeConversation?.id == id) {
                conv = _state.value.activeConversation
            }
            conv?.let {
                val updated = it.copy(title = newTitle)
                chatRepository.createFullConversation(updated)
                _state.update { s ->
                    s.copy(
                        conversations = s.conversations.map { c ->
                            if (c.id == id) updated else c
                        },
                        activeConversation = if (s.activeConversation?.id == id) updated else s.activeConversation
                    )
                }
            }
        }
    }

    fun hardDeleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.hardDeleteConversation(id)
            _state.update {
                it.copy(
                    conversations = it.conversations.filter { c -> c.id != id },
                    activeConversation = if (it.activeConversation?.id == id) null else it.activeConversation,
                    messages = if (it.activeConversation?.id == id) emptyList() else it.messages
                )
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_state.value.activeConversation?.id == id) {
                _state.update { it.copy(activeConversation = null, messages = emptyList()) }
            }
        }
    }

    /** Group chat: each character speaks in turn with their own persona card. */
    private suspend fun speakInGroupRoundRobin(
        conv: Conversation,
        provider: ApiProvider,
        userMsg: MessageUi,
        allPersonas: List<Persona>,
        initialMessages: List<Message>,
        memories: List<MemoryEntry>,
        worldBookEntries: List<WorldBookEntry>,
        userText: String
    ) {
        if (allPersonas.isEmpty()) {
            Log.e("AIC", "Group round-robin: allPersonas is empty!")
            _state.update { it.copy(streamState = StreamState.Idle, error = "群聊角色列表为空") }
            return
        }
        Log.d("AIC", "Group round-robin START: ${allPersonas.size} speakers — ${allPersonas.map { it.name }}")

        var sharedMessages = initialMessages.toMutableList()
        var roundIdx = _state.value.groupRoundIndex % allPersonas.size

        for (i in allPersonas.indices) {
            val speaker = allPersonas[roundIdx]
            Log.d("AIC", "Group round-robin [$i/${allPersonas.size}]: ${speaker.name} (id=${speaker.id}) speaking, roundIdx=$roundIdx")

            _state.update { it.copy(currentSpeakerPersonaId = speaker.id, streamState = StreamState.Connecting) }

            // Build context for THIS speaker only
            val mood = analyzeMood(sharedMessages.toList())
            val speakerProvider = provider.copy(modelName = speaker.modelName ?: provider.modelName)
            val speakerContext = ChatContext(
                persona = speaker,
                messages = sharedMessages.toList(),
                memories = memories,
                provider = speakerProvider,
                worldBookEntries = worldBookEntries,
                isGroupSpeakerTurn = true,
                replyTargetContent = _state.value.replyTarget?.content,
                replyTargetSenderName = _state.value.replyTarget?.senderPersonaName,
                conversationMood = mood
            )

            // Stream response from this speaker
            var responseText = ""
            try {
                sendMessageUseCase(conv.id, userText, speakerContext).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            responseText = result.data
                            _state.update { it.copy(streamState = StreamState.Streaming(result.data), currentSpeakerPersonaId = speaker.id) }
                        }
                        is Result.Error -> {
                            Log.e("AIC", "Group speaker ${speaker.name} error: ${result.message}")
                        }
                    }
                }
                Log.d("AIC", "Group speaker ${speaker.name} stream ended, responseLen=${responseText.length}")
            } catch (e: Exception) {
                Log.e("AIC", "Group speaker ${speaker.name} exception: ${e.javaClass.simpleName}: ${e.message}", e)
            }

            // Save this speaker's message
            if (responseText.isNotBlank()) {
                Log.d("AIC", "Group speaker ${speaker.name} saving message, len=${responseText.length}")
                if (speaker.waifuMode) {
                    // Waifu mode: split into sentence bubbles with stickers
                    _state.update { it.copy(streamState = StreamState.Idle) }
                    processWaifuResponse(responseText, speaker, conv.id, sharedMessages)
                } else {
                    val msgId = newId()
                    val emotion = detectEmotion(responseText)?.label
                    val msgUi = MessageUi(
                        id = msgId, role = "assistant", content = responseText,
                        emotion = emotion, createdAt = now(),
                        senderPersonaId = speaker.id
                    )
                    // Save to DB
                    chatRepository.saveMessage(Message(
                        id = msgId, conversationId = conv.id,
                        role = "assistant", content = responseText,
                        emotion = emotion, createdAt = now(),
                        senderPersonaId = speaker.id
                    ))
                    // Add to UI state
                    _state.update { it.copy(messages = it.messages + msgUi) }
                    speakWithTts(responseText)
                    // Add to shared history for next speaker
                    sharedMessages.add(Message(
                        id = msgId, conversationId = conv.id,
                        role = "assistant", content = responseText,
                        emotion = emotion, createdAt = now(),
                        senderPersonaId = speaker.id
                    ))
                }
            } else {
                Log.w("AIC", "Group speaker ${speaker.name} had EMPTY response, skipping message save")
            }

            roundIdx = (roundIdx + 1) % allPersonas.size
        }

        Log.d("AIC", "Group round-robin DONE, final roundIdx=$roundIdx")
        // Save round index for next turn
        _state.update { it.copy(
            streamState = StreamState.Idle,
            groupRoundIndex = roundIdx,
            currentSpeakerPersonaId = null
        )}
    }

    /** Waifu mode: split AI response into sentences, each in its own bubble with smart delay and sticker matching. */
    private suspend fun processWaifuResponse(
        text: String, persona: Persona, conversationId: String,
        sharedMessages: MutableList<Message>? = null
    ) {
        // Clear streaming sticker so it doesn't leak onto waifu bubbles
        _state.update { it.copy(currentStickerUri = null) }

        // Strip action expressions: *text*, (text), 【text】
        val cleaned = text
            .replace(Regex("\\*[^*]+\\*"), "")
            .replace(Regex("（[^）]+）"), "")
            .replace(Regex("\\([^)]+\\)"), "")
            .replace(Regex("【[^】]+】"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Split into sentences (。！？!?…\n)
        val sentences = cleaned.split(Regex("(?<=[。！？!?…\\n])"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }

        if (sentences.isEmpty()) {
            // Fallback: single message
            val emotion = detectEmotion(cleaned)
            val sticker = stickerRepository.getRandomByEmotion(persona.id, emotion?.name?.lowercase() ?: "neutral")
            val msg = MessageUi(
                id = newId(), role = "assistant", content = cleaned,
                emotion = emotion?.label, createdAt = now(),
                senderPersonaId = persona.id,
                stickerUri = sticker?.imagePath
            )
            chatRepository.saveMessage(Message(
                id = msg.id, conversationId = conversationId,
                role = "assistant", content = cleaned,
                emotion = msg.emotion, createdAt = msg.createdAt,
                senderPersonaId = persona.id
            ))
            sharedMessages?.add(Message(
                id = msg.id, conversationId = conversationId,
                role = "assistant", content = cleaned,
                emotion = msg.emotion, createdAt = msg.createdAt,
                senderPersonaId = persona.id
            ))
            _state.update { it.copy(messages = it.messages + msg) }
            return
        }

        // Put sticker only on the last sentence — one per full response
        val lastIdx = sentences.size - 1

        for ((i, sentence) in sentences.withIndex()) {
            val emotion = detectEmotion(sentence)
            val stickerUri = if (i == lastIdx && emotion != null && emotion != Emotion.NEUTRAL) {
                stickerRepository.getRandomByEmotion(persona.id, emotion.name.lowercase())?.imagePath
            } else null

            // Smart delay: 400ms base + 50ms per character, max 2s
            val delayMs = 400L + (sentence.length * 50L).coerceAtMost(2000L)

            val msg = MessageUi(
                id = newId(), role = "assistant", content = sentence,
                emotion = emotion?.label, createdAt = now() + i * delayMs,
                senderPersonaId = persona.id,
                stickerUri = stickerUri
            )
            // Save to DB
            chatRepository.saveMessage(Message(
                id = msg.id, conversationId = conversationId,
                role = "assistant", content = sentence,
                emotion = msg.emotion, createdAt = msg.createdAt,
                senderPersonaId = persona.id
            ))
            sharedMessages?.add(Message(
                id = msg.id, conversationId = conversationId,
                role = "assistant", content = sentence,
                emotion = msg.emotion, createdAt = msg.createdAt,
                senderPersonaId = persona.id
            ))

            _state.update { s -> s.copy(messages = s.messages + msg) }
            speakWithTts(sentence)
            if (i < sentences.size - 1) {
                kotlinx.coroutines.delay(delayMs)
            }
        }
    }

    private fun splitGroupResponse(text: String, groupPersonas: List<Persona>): List<MessageUi> {
        val messages = mutableListOf<MessageUi>()
        // Try to parse "角色名：内容" format, one per line
        val lines = text.trim().split("\n").filter { it.isNotBlank() }
        for (line in lines) {
            // Match "角色名：内容" or "【角色名】内容" or "角色名:内容"
            val match = Regex("^【(.+?)】(.+)|^(.+?)[：:](.+)").find(line.trim())
            if (match != null) {
                // Alt1: 【name】content → groups 1,2; Alt2: name：content → groups 3,4
                val name: String
                val content: String
                if (match.groupValues[1].isNotBlank()) {
                    name = match.groupValues[1].trim()
                    content = match.groupValues[2].trim()
                } else {
                    name = match.groupValues[3].trim()
                    content = match.groupValues[4].trim()
                }
                // Find matching persona by name
                val matchedPersona = groupPersonas.find { it.name == name }
                messages.add(MessageUi(
                    id = newId(), role = "assistant", content = content,
                    emotion = detectEmotion(content)?.label,
                    createdAt = now() + messages.size.toLong(),
                    senderPersonaId = matchedPersona?.id,
                    senderPersonaName = matchedPersona?.name ?: name,
                    senderAvatarUri = matchedPersona?.avatarImageUri
                ))
            } else if (line.isNotBlank()) {
                // Line without prefix - attribute to primary persona
                val primary = groupPersonas.firstOrNull()
                messages.add(MessageUi(
                    id = newId(), role = "assistant", content = line,
                    emotion = detectEmotion(line)?.label,
                    createdAt = now() + messages.size.toLong(),
                    senderPersonaId = primary?.id,
                    senderPersonaName = primary?.name,
                    senderAvatarUri = primary?.avatarImageUri
                ))
            }
        }
        // Fallback: if no split occurred, create single message
        if (messages.isEmpty()) {
            val primary = groupPersonas.firstOrNull()
            messages.add(MessageUi(
                id = newId(), role = "assistant", content = text,
                createdAt = now(),
                senderPersonaId = primary?.id,
                senderPersonaName = primary?.name,
                senderAvatarUri = primary?.avatarImageUri
            ))
        }
        return messages
    }

    private fun detectEmotion(text: String): Emotion? {
        val lower = text.lowercase()
        // Score each emotion by keyword matches, pick the highest scorer
        data class Score(val emotion: Emotion, val count: Int)
        val scores = listOf(
            Score(Emotion.HAPPY, countMatches(lower,
                "哈哈", "嘻嘻", "嘿嘿", "呵呵", "开心", "高兴", "快乐", "幸福", "太好了", "真好",
                "喜欢", "爱你", "爱", "想你", "想你了", "亲爱的", "好想你",
                "棒", "厉害", "赞", "酷", "太棒了", "好厉害", "绝了", "nice",
                "😊", "😄", "☺", "😁", "😆", "😂", "🤣", "❤", "💕", "💗", "💖", "😍", "🥰", "😘", "^_^")),
            Score(Emotion.SAD, countMatches(lower,
                "难过", "伤心", "哭泣", "哭了", "呜呜", "唉", "叹气", "遗憾", "可惜",
                "不开心", "不高兴", "郁闷", "低落", "悲伤",
                "😢", "😭", "💔", "😞", "😔", "😟", "😿")),
            Score(Emotion.ANGRY, countMatches(lower,
                "生气", "可恶", "讨厌", "混蛋", "过分", "烦", "烦死了", "滚", "闭嘴",
                "气死", "受不了", "恶心", "不要脸",
                "哼", "切",
                "😠", "😡", "🤬", "💢", "😤")),
            Score(Emotion.SURPRISED, countMatches(lower,
                "哇", "天哪", "真的假的", "不会吧", "什么", "竟然", "居然",
                "不可思议", "吓", "震惊", "惊呆了",
                "😲", "😮", "😯", "😳", "🤯")),
            Score(Emotion.SHY, countMatches(lower,
                "害羞", "⁄", "不好意思", "脸红", "⁄⁄", "扭捏", "羞涩",
                "别这样", "不要啦", "人家",
                "😳", "☺️", "👉👈", "🥺")),
            Score(Emotion.THINKING, countMatches(lower,
                "嗯", "唔", "呃", "这个嘛", "让我想想", "我想想",
                "等等", "等一下", "稍等",
                "🤔", "💭"))
        )
        val best = scores.maxByOrNull { it.count } ?: return null
        return if (best.count > 0) best.emotion else null
    }

    private fun countMatches(text: String, vararg keywords: String): Int =
        keywords.count { text.contains(it) }

    /** Analyze the emotional mood of recent conversation turns. */
    private fun analyzeMood(recentMessages: List<com.aicompanion.domain.model.Message>): String {
        if (recentMessages.isEmpty()) return "neutral"
        val recent = recentMessages.takeLast(6)
        val userMessages = recent.filter { it.role == "user" }
        val combined = userMessages.joinToString(" ") { it.content.lowercase() }
        return when {
            combined.contains("哈哈") || combined.contains("开心") || combined.contains("笑") -> "happy"
            combined.contains("难过") || combined.contains("伤心") || combined.contains("哭") || combined.contains("累") -> "sad"
            combined.contains("生气") || combined.contains("烦") || combined.contains("可恶") -> "angry"
            combined.contains("哇") || combined.contains("天哪") || combined.contains("惊喜") -> "excited"
            combined.length < 30 -> "flat"
            else -> "neutral"
        }
    }

    /** Higher = stronger emotion, for picking the best sentence to attach a sticker to. */
    private fun emotionStrength(emotion: Emotion): Int = when (emotion) {
        Emotion.HAPPY -> 3
        Emotion.SURPRISED -> 3
        Emotion.ANGRY -> 2
        Emotion.SAD -> 2
        Emotion.SHY -> 1
        Emotion.THINKING -> 1
        Emotion.NEUTRAL -> 0
    }
}
