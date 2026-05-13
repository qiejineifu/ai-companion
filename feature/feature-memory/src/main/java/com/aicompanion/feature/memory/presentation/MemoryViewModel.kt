package com.aicompanion.feature.memory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.domain.model.MemoryEntry
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.repository.MemoryRepository
import com.aicompanion.domain.repository.PersonaRepository
import com.aicompanion.feature.memory.MemoryExtractor
import com.aicompanion.feature.memory.MemorySearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<MemoryEntry> = emptyList(),
    val searchResults: List<MemorySearchEngine.ScoredMemory> = emptyList(),
    val searchQuery: String = "",
    val selectedPersona: Persona? = null,
    val personas: List<Persona> = emptyList(),
    val isSearching: Boolean = false,
    val editingMemory: MemoryEntry? = null,
    val showEditDialog: Boolean = false,
    val stats: MemoryStats = MemoryStats()
)

data class MemoryStats(
    val totalCount: Int = 0,
    val personalCount: Int = 0,
    val preferenceCount: Int = 0,
    val lifeEventCount: Int = 0,
    val emotionalCount: Int = 0,
    val averageConfidence: Float = 0f
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val personaRepository: PersonaRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()
    private val searchEngine = MemorySearchEngine()
    private val extractor = MemoryExtractor()

    init {
        viewModelScope.launch {
            personaRepository.getAll().collect { personas ->
                val active = personas.firstOrNull()
                _state.update { it.copy(personas = personas, selectedPersona = active ?: it.selectedPersona) }
                active?.let { loadMemories(it.id) }
            }
        }
    }

    fun selectPersona(personaId: String) {
        viewModelScope.launch {
            personaRepository.getById(personaId)?.let { persona ->
                _state.update { it.copy(selectedPersona = persona) }
                loadMemories(persona.id)
            }
        }
    }

    private suspend fun loadMemories(personaId: String) {
        memoryRepository.getByPersona(personaId).collect { memories ->
            val stats = computeStats(memories)
            _state.update { it.copy(memories = memories, stats = stats) }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        val results = searchEngine.search(query, _state.value.memories)
        _state.update { it.copy(searchResults = results, isSearching = false) }
    }

    fun extractFromConversation(messages: List<com.aicompanion.domain.model.Message>) {
        val persona = _state.value.selectedPersona ?: return
        viewModelScope.launch {
            val extracted = extractor.extractFromMessages(messages, persona.id)
            extracted.forEach { memoryRepository.save(it) }
        }
    }

    fun addManualMemory(content: String) {
        val persona = _state.value.selectedPersona ?: return
        viewModelScope.launch {
            val memory = MemoryEntry(
                id = newId(), personaId = persona.id, content = content,
                confidence = 1.0f, importance = 0.8f,
                createdAt = now(), lastAccessedAt = now()
            )
            memoryRepository.save(memory)
        }
    }

    fun showEditDialog(memory: MemoryEntry? = null) {
        val edit = memory ?: MemoryEntry(
            id = newId(), personaId = _state.value.selectedPersona?.id ?: "",
            content = "", confidence = 0.8f, importance = 0.5f,
            createdAt = now(), lastAccessedAt = now()
        )
        _state.update { it.copy(editingMemory = edit, showEditDialog = true) }
    }

    fun dismissEditDialog() {
        _state.update { it.copy(editingMemory = null, showEditDialog = false) }
    }

    fun updateEditingMemory(memory: MemoryEntry) {
        _state.update { it.copy(editingMemory = memory) }
    }

    fun saveMemory() {
        val memory = _state.value.editingMemory ?: return
        viewModelScope.launch {
            memoryRepository.save(memory)
            _state.update { it.copy(editingMemory = null, showEditDialog = false) }
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { memoryRepository.delete(id) }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            val persona = _state.value.selectedPersona ?: return@launch
            _state.value.memories.forEach { memoryRepository.delete(it.id) }
        }
    }

    private fun computeStats(memories: List<MemoryEntry>): MemoryStats {
        return MemoryStats(
            totalCount = memories.size,
            personalCount = memories.count { it.content.startsWith("[personal]") },
            preferenceCount = memories.count { it.content.startsWith("[preference]") },
            lifeEventCount = memories.count { it.content.startsWith("[life_event]") },
            emotionalCount = memories.count { it.content.startsWith("[emotional]") },
            averageConfidence = if (memories.isNotEmpty()) memories.map { it.confidence }.average().toFloat() else 0f
        )
    }
}
