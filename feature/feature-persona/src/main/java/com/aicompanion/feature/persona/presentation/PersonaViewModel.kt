package com.aicompanion.feature.persona.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.TavernCardParser
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.common.onSuccess
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.model.Trait
import com.aicompanion.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PersonaUiState(
    val personas: List<Persona> = emptyList(),
    val isLoading: Boolean = false,
    val editingPersona: Persona? = null,
    val showEditDialog: Boolean = false,
    val newTraitKey: String = "",
    val newTraitValue: String = "",
    val newExampleChat: String = "",
    val newTag: String = "",
    val importMessage: String? = null
)

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val repository: PersonaRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PersonaUiState())
    val state: StateFlow<PersonaUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.createPresetTemplates()
            repository.getAll().collect { personas ->
                _state.update { it.copy(personas = personas) }
            }
        }
    }

    fun createPersona(persona: Persona) {
        viewModelScope.launch {
            repository.create(persona).onSuccess {
                _state.update { it.copy(importMessage = "角色「${persona.name}」已创建！") }
            }
        }
    }

    fun showCreateDialog() {
        _state.update {
            it.copy(
                editingPersona = Persona(
                    id = newId(), name = "", description = "",
                    systemPrompt = "", createdAt = now()
                ),
                showEditDialog = true
            )
        }
    }

    fun showEditDialog(persona: Persona) {
        _state.update { it.copy(editingPersona = persona, showEditDialog = true) }
    }

    fun dismissDialog() {
        _state.update { it.copy(editingPersona = null, showEditDialog = false) }
    }

    fun updateEditingPersona(persona: Persona) {
        _state.update { it.copy(editingPersona = persona) }
    }

    fun addTrait() {
        val persona = _state.value.editingPersona ?: return
        val key = _state.value.newTraitKey.trim()
        val value = _state.value.newTraitValue.trim()
        if (key.isBlank() || value.isBlank()) return

        val newTrait = Trait(key = key, value = value)
        _state.update {
            it.copy(
                editingPersona = persona.copy(traits = persona.traits + newTrait),
                newTraitKey = "", newTraitValue = ""
            )
        }
    }

    fun removeTrait(index: Int) {
        val persona = _state.value.editingPersona ?: return
        _state.update {
            it.copy(editingPersona = persona.copy(traits = persona.traits.toMutableList().also { l -> l.removeAt(index) }))
        }
    }

    fun setTraitKey(value: String) { _state.update { it.copy(newTraitKey = value) } }
    fun setTraitValue(value: String) { _state.update { it.copy(newTraitValue = value) } }

    // Example chats
    fun addExampleChat() {
        val chat = _state.value.newExampleChat.trim()
        if (chat.isBlank()) return
        val persona = _state.value.editingPersona ?: return
        _state.update {
            it.copy(
                editingPersona = persona.copy(exampleChats = persona.exampleChats + chat),
                newExampleChat = ""
            )
        }
    }
    fun removeExampleChat(index: Int) {
        val persona = _state.value.editingPersona ?: return
        _state.update {
            it.copy(editingPersona = persona.copy(
                exampleChats = persona.exampleChats.toMutableList().also { l -> l.removeAt(index) }
            ))
        }
    }
    fun setNewExampleChat(value: String) { _state.update { it.copy(newExampleChat = value) } }

    // Tags
    fun addTag() {
        val tag = _state.value.newTag.trim()
        if (tag.isBlank()) return
        val persona = _state.value.editingPersona ?: return
        _state.update {
            it.copy(
                editingPersona = persona.copy(tags = persona.tags + tag),
                newTag = ""
            )
        }
    }
    fun removeTag(index: Int) {
        val persona = _state.value.editingPersona ?: return
        _state.update {
            it.copy(editingPersona = persona.copy(
                tags = persona.tags.toMutableList().also { l -> l.removeAt(index) }
            ))
        }
    }
    fun setNewTag(value: String) { _state.update { it.copy(newTag = value) } }

    fun savePersona() {
        val persona = _state.value.editingPersona ?: return
        if (persona.name.isBlank() || persona.systemPrompt.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = if (_state.value.personas.any { it.id == persona.id }) {
                repository.update(persona)
            } else {
                repository.create(persona)
            }
            result.onSuccess { _state.update { it.copy(showEditDialog = false, editingPersona = null) } }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun deletePersona(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun duplicatePersona(persona: Persona) {
        viewModelScope.launch {
            repository.create(persona.copy(id = newId(), name = "${persona.name}(副本)", isPreset = false, createdAt = now()))
        }
    }

    fun importFromTavernCard(uri: Uri, context: Context) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, importMessage = null) }
            val parsed = TavernCardParser.parse(context, uri)
            if (parsed == null) {
                _state.update { it.copy(isLoading = false, importMessage = "无法解析角色卡，请确认文件格式正确") }
                return@launch
            }

            val persona = Persona(
                id = newId(),
                name = parsed.name,
                description = parsed.description,
                systemPrompt = parsed.systemPrompt.ifBlank {
                    parsed.personality.ifBlank { "你是${parsed.name}。" }
                },
                scenario = parsed.scenario,
                firstMessage = parsed.firstMessage,
                exampleChats = parsed.exampleChats,
                tags = parsed.tags,
                specVersion = parsed.specVersion,
                creator = parsed.creator,
                createdAt = now()
            )

            repository.create(persona).onSuccess {
                _state.update { it.copy(isLoading = false, importMessage = "成功导入: ${parsed.name}") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun importParsedCard(parsed: TavernCardParser.ParsedCard, imageBytes: ByteArray? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, importMessage = null) }

            // Save PNG as persona avatar
            var avatarUri: String? = null
            if (imageBytes != null) {
                try {
                    val dir = File(appContext.filesDir, "persona_avatars")
                    dir.mkdirs()
                    val file = File(dir, "${newId()}.png")
                    file.writeBytes(imageBytes)
                    avatarUri = file.toURI().toString()
                } catch (_: Exception) {}
            }

            val persona = Persona(
                id = newId(), name = parsed.name, description = parsed.description,
                systemPrompt = parsed.systemPrompt.ifBlank {
                    parsed.personality.ifBlank { "你是${parsed.name}。" }
                },
                scenario = parsed.scenario,
                firstMessage = parsed.firstMessage,
                exampleChats = parsed.exampleChats,
                tags = parsed.tags,
                specVersion = parsed.specVersion,
                creator = parsed.creator,
                avatarImageUri = avatarUri,
                createdAt = now()
            )
            repository.create(persona).onSuccess {
                _state.update { it.copy(isLoading = false, importMessage = "成功导入: ${parsed.name}") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun clearImportMessage() {
        _state.update { it.copy(importMessage = null) }
    }
}
