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
import com.aicompanion.feature.voice.STTManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val sendMessageUseCase: SendMessageUseCase,
    private val getConversationsUseCase: GetConversationsUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val stopGenerationUseCase: StopGenerationUseCase,
    private val createConversationUseCase: CreateConversationUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private val memoryExtractor = MemoryExtractor()
    private var messagesJob: Job? = null

    init {
        Log.d("AIC", "ChatViewModel init START")
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
                        if (event.text.isNotBlank()) {
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
            is ChatIntent.Regenerate -> regenerate(intent.messageId)
            is ChatIntent.SwitchBranch -> switchBranch(intent.branchKey, intent.index)
            is ChatIntent.ToggleSidebar -> toggleSidebar()
            is ChatIntent.UpdateInput -> updateInput(intent.text)
            is ChatIntent.DismissError -> dismissError()
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
            _state.update { it.copy(inputText = "", streamState = StreamState.Connecting) }

            // Ensure conversation exists
            var conv = conversation
            if (conv == null) {
                // Try pending conversation (group chat from navigation)
                val pendingId = _state.value.pendingConversationId
                if (pendingId != null) {
                    conv = chatRepository.getConversationById(pendingId)
                    if (conv != null) {
                        Log.d("AIC", "Loaded pending conversation: ${conv!!.id} group=${conv!!.isGroupChat}")
                        _state.update { it.copy(activeConversation = conv, pendingConversationId = null) }
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
            val context = ChatContext(
                persona = persona, messages = recentMessages,
                memories = memories, provider = provider,
                worldBookEntries = worldBookEntries,
                groupPersonas = groupPersonas
            )
            val userMsg = MessageUi(id = newId(), role = "user", content = text, createdAt = now())
            _state.update { it.copy(messages = it.messages + userMsg) }

            if (conv?.isGroupChat == true) {
                // === GROUP CHAT: Swap-card round-robin ===
                val allPersonaIds = conv.groupPersonaIds
                val allPersonas = allPersonaIds.mapNotNull { personaRepository.getById(it) }
                Log.d("AIC", "Group chat with ${allPersonas.size} personas: ${allPersonas.map { it.name }}")

                // Save user message to DB
                chatRepository.saveMessage(Message(
                    id = userMsg.id, conversationId = conv.id,
                    role = "user", content = text, createdAt = userMsg.createdAt
                ))

                speakInGroupRoundRobin(conv, provider, userMsg, allPersonas, recentMessages, memories, worldBookEntries, text)
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
                            val waifuMsgs = processWaifuResponse(finalText, persona, conv!!.id)
                            _state.update { it.copy(streamState = StreamState.Idle, messages = it.messages + waifuMsgs) }
                        } else {
                            val assistantMsg = MessageUi(id = newId(), role = "assistant", content = finalText, emotion = detectEmotion(finalText)?.label, createdAt = now())
                            _state.update { it.copy(streamState = StreamState.Idle, messages = it.messages + assistantMsg) }
                        }
                        val extractedMemories = memoryExtractor.extractFromText(text + " " + finalText, persona.id)
                        extractedMemories.forEach { memoryRepository.save(it) }
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
        viewModelScope.launch {
            personaRepository.getById(personaId)?.let { persona ->
                _state.update { it.copy(activePersona = persona, showPersonaPicker = false) }
                // Only auto-resume if no conversation is already selected (e.g. group chat)
                if (_state.value.activeConversation != null) return@launch
                val latestConv = _state.value.conversations
                    .filter { it.personaId == personaId && !it.isArchived }
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

    private fun toggleVoiceInput() {
        if (_state.value.isRecording) {
            sttManager.stopListening()
            _state.update { it.copy(isRecording = false) }
        } else {
            // Check if any speech recognition service is installed
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val activities = sttManager.context.packageManager.queryIntentActivities(intent, 0)
            if (activities.isEmpty()) {
                _state.update { it.copy(error = "未检测到语音识别服务，需要安装 Google 或系统语音输入") }
                return
            }
            try {
                sttManager.startListening()
                _state.update { it.copy(isRecording = true, error = null) }
            } catch (e: SecurityException) {
                _state.update { it.copy(error = "需要录音权限才能使用语音输入") }
            } catch (e: Exception) {
                _state.update { it.copy(error = "语音启动失败: ${e.message}") }
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
        val provider = currentState.activeApiProvider ?: return

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
            val recentMessages = currentState.messages.take(msgIndex).map {
                Message(id = it.id, conversationId = conv.id, role = it.role,
                    content = it.content, createdAt = it.createdAt)
            }

            val worldBookEntries = worldBookRepository.getEnabledByPersona(persona.id)
            val groupPersonas = if (conv.isGroupChat && conv.groupPersonaIds.isNotEmpty()) {
                conv.groupPersonaIds.mapNotNull { personaRepository.getById(it) }
                    .filter { it.id != persona.id }
            } else emptyList()

            val context = ChatContext(
                persona = persona, messages = recentMessages,
                memories = memories, provider = provider,
                worldBookEntries = worldBookEntries,
                groupPersonas = groupPersonas
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
        var sharedMessages = initialMessages.toMutableList()
        // Start from round index
        var roundIdx = _state.value.groupRoundIndex % allPersonas.size

        for (i in allPersonas.indices) {
            val speaker = allPersonas[roundIdx]
            Log.d("AIC", "Group round-robin: ${speaker.name} speaking (index $roundIdx)")

            _state.update { it.copy(currentSpeakerPersonaId = speaker.id, streamState = StreamState.Connecting) }

            // Build context for THIS speaker only
            val speakerContext = ChatContext(
                persona = speaker,
                messages = sharedMessages.toList(),
                memories = memories,
                provider = provider,
                worldBookEntries = worldBookEntries,
                isGroupSpeakerTurn = true
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
            } catch (e: Exception) {
                Log.e("AIC", "Group speaker ${speaker.name} exception: ${e.message}")
            }

            // Save this speaker's message
            if (responseText.isNotBlank()) {
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
                // Add to shared history for next speaker
                sharedMessages.add(Message(
                    id = msgId, conversationId = conv.id,
                    role = "assistant", content = responseText,
                    emotion = emotion, createdAt = now(),
                    senderPersonaId = speaker.id
                ))
            }

            roundIdx = (roundIdx + 1) % allPersonas.size
        }

        // Save round index for next turn
        _state.update { it.copy(
            streamState = StreamState.Idle,
            groupRoundIndex = roundIdx,
            currentSpeakerPersonaId = null
        )}
    }

    /** Waifu mode: split AI response into sentences, each in its own bubble with smart delay and sticker matching. */
    private suspend fun processWaifuResponse(
        text: String, persona: Persona, conversationId: String
    ): List<MessageUi> {
        val messages = mutableListOf<MessageUi>()
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
            messages.add(MessageUi(
                id = newId(), role = "assistant", content = cleaned,
                emotion = emotion?.label, createdAt = now(),
                senderPersonaId = persona.id
            ))
            if (sticker != null) {
                _state.update { it.copy(currentStickerUri = sticker.imagePath) }
            }
            return messages
        }

        for ((i, sentence) in sentences.withIndex()) {
            val emotion = detectEmotion(sentence)
            val sticker = stickerRepository.getRandomByEmotion(persona.id, emotion?.name?.lowercase() ?: "neutral")
            // Smart delay: 400ms base + 50ms per character
            val delayMs = 400L + (sentence.length * 50L).coerceAtMost(2000L)

            val msg = MessageUi(
                id = newId(), role = "assistant", content = sentence,
                emotion = emotion?.label, createdAt = now() + i * delayMs,
                senderPersonaId = persona.id
            )
            // Save to DB
            chatRepository.saveMessage(Message(
                id = msg.id, conversationId = conversationId,
                role = "assistant", content = sentence,
                emotion = msg.emotion, createdAt = msg.createdAt,
                senderPersonaId = persona.id
            ))
            // Set sticker for this message
            if (sticker != null) {
                _state.update { it.copy(currentStickerUri = sticker.imagePath) }
            }

            messages.add(msg)
            // Add to UI gradually to simulate typing delay
            if (i < sentences.size - 1) {
                _state.update { s -> s.copy(messages = s.messages + msg) }
                kotlinx.coroutines.delay(delayMs)
            }
        }
        return messages
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
        return when {
            lower.contains("哈哈") || lower.contains("开心") || lower.contains("😊") -> Emotion.HAPPY
            lower.contains("难过") || lower.contains("伤心") || lower.contains("😢") -> Emotion.SAD
            lower.contains("生气") || lower.contains("可恶") || lower.contains("😠") -> Emotion.ANGRY
            lower.contains("哇") || lower.contains("天哪") || lower.contains("😲") -> Emotion.SURPRISED
            lower.contains("害羞") || lower.contains("⁄") || lower.contains("😳") -> Emotion.SHY
            lower.contains("嗯") && lower.length < 10 -> Emotion.THINKING
            else -> null
        }
    }
}
