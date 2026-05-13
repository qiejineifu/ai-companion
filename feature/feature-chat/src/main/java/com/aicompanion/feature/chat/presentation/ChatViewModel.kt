package com.aicompanion.feature.chat.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.core.common.*
import com.aicompanion.domain.model.*
import com.aicompanion.domain.repository.*
import com.aicompanion.domain.usecase.*
import com.aicompanion.feature.memory.MemoryExtractor
import com.aicompanion.feature.voice.STTEvent
import com.aicompanion.feature.voice.STTManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val personaRepository: PersonaRepository,
    private val apiProviderRepository: ApiProviderRepository,
    private val memoryRepository: MemoryRepository,
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

    fun processIntent(intent: ChatIntent) {
        Log.d("AIC", "processIntent: ${intent.javaClass.simpleName}")
        when (intent) {
            is ChatIntent.SendMessage -> sendMessage(intent.text)
            is ChatIntent.SendVoice -> toggleVoiceInput()
            is ChatIntent.StopGeneration -> stopGeneration()
            is ChatIntent.DeleteMessage -> deleteMessage(intent.messageId)
            is ChatIntent.SelectConversation -> selectConversation(intent.id)
            is ChatIntent.NewConversation -> createNewConversation()
            is ChatIntent.SelectPersona -> selectPersona(intent.personaId)
            is ChatIntent.ToggleVoiceMode -> toggleVoiceMode()
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

        Log.d("AIC", "=== sendMessage called ===")
        Log.d("AIC", "text=$text, persona=${persona?.name}, provider=${provider?.name}, conversationId=${conversation?.id}")

        if (text.isBlank()) { Log.d("AIC", "BLOCKED: empty text"); return }
        if (persona == null) {
            Log.d("AIC", "BLOCKED: no persona")
            _state.update { it.copy(error = "请先选择一个人设") }
            return
        }
        if (provider == null) {
            Log.d("AIC", "BLOCKED: no provider")
            _state.update { it.copy(error = "请先配置 API Key") }
            return
        }
        if (provider.apiKeyEncrypted.isBlank()) {
            Log.d("AIC", "BLOCKED: blank API key")
            _state.update { it.copy(error = "API Key 不能为空，请在设置中配置") }
            return
        }

        Log.d("AIC", "Validation passed, launching coroutine...")
        viewModelScope.launch {
            _state.update { it.copy(inputText = "", streamState = StreamState.Connecting) }

            // Ensure conversation exists
            var conv = conversation
            if (conv == null) {
                Log.d("AIC", "Creating new conversation...")
                val result = chatRepository.createConversation(
                    personaId = persona.id,
                    apiProviderId = provider.id,
                    title = text.take(30)
                )
                result.onSuccess { conv = it }
                result.onError { _state.update { s -> s.copy(streamState = StreamState.Error(it)) } }
                if (conv == null) { Log.d("AIC", "FAILED to create conversation"); return@launch }
                _state.update { it.copy(activeConversation = conv) }
                Log.d("AIC", "Conversation created: ${conv!!.id}")
            }

            val memories = memoryRepository.searchRelevant(persona.id, text, Constants.DEFAULT_MEMORY_TOP_K)
            val recentMessages = getMessagesUseCase(conv!!.id).first()
            Log.d("AIC", "Got messages=${recentMessages.size}, memories=${memories.size}")

            // Inject firstMessage as opening if this is a fresh conversation
            val isNewConversation = recentMessages.isEmpty()
            if (isNewConversation && persona.firstMessage.isNotBlank()) {
                val openingMsg = MessageUi(
                    id = newId(), role = "assistant", content = persona.firstMessage,
                    createdAt = now() - 1
                )
                _state.update { it.copy(messages = it.messages + openingMsg) }
            }

            val context = ChatContext(persona = persona, messages = recentMessages, memories = memories, provider = provider)
            val userMsg = MessageUi(id = newId(), role = "user", content = text, createdAt = now())
            _state.update { it.copy(messages = it.messages + userMsg) }

            // Stream response
            Log.d("AIC", "Starting SSE stream to ${provider.baseUrl}")
            sendMessageUseCase(conv!!.id, text, context).collect { result ->
                when (result) {
                    is Result.Success -> {
                        Log.d("AIC", "SSE chunk received: ${result.data.take(50)}...")
                        _state.update { it.copy(
                            streamState = StreamState.Streaming(result.data, detectEmotion(result.data)),
                            currentEmotion = detectEmotion(result.data) ?: Emotion.NEUTRAL
                        )}
                    }
                    is Result.Error -> {
                        Log.e("AIC", "SSE error: ${result.message}")
                        _state.update { it.copy(streamState = StreamState.Error(result.message)) }
                    }
                }
            }

            // Complete
            val s = _state.value
            Log.d("AIC", "Stream ended. streamState=${s.streamState}")
            when {
                s.streamState is StreamState.Streaming -> {
                    val finalText = (s.streamState as StreamState.Streaming).partialText
                    val assistantMsg = MessageUi(
                        id = newId(), role = "assistant", content = finalText,
                        emotion = detectEmotion(finalText)?.label,
                        createdAt = now()
                    )
                    _state.update { it.copy(
                        streamState = StreamState.Idle,
                        messages = it.messages + assistantMsg
                    )}
                    val extractedMemories = memoryExtractor.extractFromText(text + " " + finalText, persona.id)
                    extractedMemories.forEach { memoryRepository.save(it) }
                }
                s.streamState is StreamState.Error -> { /* error already displayed */ }
                else -> {
                    _state.update { it.copy(
                        streamState = StreamState.Idle,
                        error = "未收到 AI 回复，请检查 API 地址和 Key 是否正确"
                    )}
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
        viewModelScope.launch {
            getMessagesUseCase(id).collect { messages ->
                _state.update {
                    it.copy(
                        activeConversation = it.conversations.find { c -> c.id == id },
                        messages = messages.map { m -> m.toUi() }
                    )
                }
            }
        }
    }

    private fun createNewConversation() {
        _state.update {
            it.copy(
                activeConversation = null,
                messages = emptyList(),
                streamState = StreamState.Idle,
                currentEmotion = Emotion.NEUTRAL,
                retrievedMemories = emptyList()
            )
        }
    }

    private fun selectPersona(personaId: String) {
        viewModelScope.launch {
            personaRepository.getById(personaId)?.let { persona ->
                _state.update { it.copy(activePersona = persona, showPersonaPicker = false) }
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
            Log.d("AIC", "STT: stop listening")
            sttManager.stopListening()
            _state.update { it.copy(isRecording = false) }
        } else {
            Log.d("AIC", "STT: start listening")
            try {
                sttManager.startListening()
                _state.update { it.copy(isRecording = true) }
            } catch (e: SecurityException) {
                Log.e("AIC", "STT: no audio permission", e)
                _state.update { it.copy(error = "需要录音权限才能使用语音输入") }
            } catch (e: Exception) {
                Log.e("AIC", "STT: start failed", e)
                _state.update { it.copy(error = "语音输入启动失败: ${e.message}") }
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
        _state.update { it.copy(error = null) }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.createConversation(
                _state.value.activePersona?.id ?: "", "", newTitle
            )
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
