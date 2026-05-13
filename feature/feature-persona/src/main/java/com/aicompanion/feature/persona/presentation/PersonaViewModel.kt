package com.aicompanion.feature.persona.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.common.onSuccess
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.model.Trait
import com.aicompanion.domain.repository.PersonaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonaUiState(
    val personas: List<Persona> = emptyList(),
    val isLoading: Boolean = false,
    val editingPersona: Persona? = null,
    val showEditDialog: Boolean = false,
    val newTraitKey: String = "",
    val newTraitValue: String = ""
)

@HiltViewModel
class PersonaViewModel @Inject constructor(
    private val repository: PersonaRepository
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
}
