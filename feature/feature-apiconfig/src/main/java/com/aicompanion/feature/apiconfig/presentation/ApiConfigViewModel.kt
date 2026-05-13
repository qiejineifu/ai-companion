package com.aicompanion.feature.apiconfig.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicompanion.core.common.Result
import com.aicompanion.core.common.newId
import com.aicompanion.core.common.now
import com.aicompanion.core.common.onSuccess
import com.aicompanion.domain.model.ApiProvider
import com.aicompanion.domain.repository.ApiProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiConfigUiState(
    val providers: List<ApiProvider> = emptyList(),
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val editingProvider: ApiProvider? = null,
    val showEditDialog: Boolean = false,
    val showTemplatePicker: Boolean = false,
    val templates: List<ApiConfigTemplate> = ApiConfigTemplate.presets
)

data class ApiConfigTemplate(
    val name: String, val baseUrl: String, val modelName: String, val key: String
) {
    companion object {
        val presets = listOf(
            ApiConfigTemplate("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o", "openai"),
            ApiConfigTemplate("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", "deepseek"),
            ApiConfigTemplate("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus", "qwen"),
            ApiConfigTemplate("硅基流动", "https://api.siliconflow.cn/v1/chat/completions", "deepseek-ai/DeepSeek-V3", "siliconflow"),
            ApiConfigTemplate("自定义", "", "", "custom")
        )
    }
}

@HiltViewModel
class ApiConfigViewModel @Inject constructor(
    private val repository: ApiProviderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ApiConfigUiState())
    val state: StateFlow<ApiConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { providers ->
                _state.update { it.copy(providers = providers) }
            }
        }
    }

    fun showAddDialog(template: ApiConfigTemplate? = null) {
        val provider = if (template != null && template.key != "custom") {
            ApiProvider(
                id = newId(), name = template.name, baseUrl = template.baseUrl,
                apiKeyEncrypted = "", modelName = template.modelName,
                providerTemplate = template.key, createdAt = now()
            )
        } else {
            ApiProvider(
                id = newId(), name = "", baseUrl = "", apiKeyEncrypted = "",
                modelName = "", createdAt = now()
            )
        }
        _state.update { it.copy(editingProvider = provider, showEditDialog = true, showTemplatePicker = false) }
    }

    fun showEditDialog(provider: ApiProvider) {
        _state.update { it.copy(editingProvider = provider, showEditDialog = true) }
    }

    fun dismissDialog() {
        _state.update { it.copy(editingProvider = null, showEditDialog = false, testResult = null) }
    }

    fun showTemplatePicker() {
        _state.update { it.copy(showTemplatePicker = true) }
    }

    fun hideTemplatePicker() {
        _state.update { it.copy(showTemplatePicker = false) }
    }

    fun updateEditingProvider(provider: ApiProvider) {
        _state.update { it.copy(editingProvider = provider) }
    }

    fun saveProvider() {
        val provider = _state.value.editingProvider ?: return
        if (provider.name.isBlank() || provider.apiKeyEncrypted.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val isNew = _state.value.providers.none { it.id == provider.id }
            val result = if (isNew) {
                repository.create(provider)
            } else {
                repository.update(provider)
            }
            result.onSuccess {
                // Auto-activate when saving the first provider
                if (isNew) repository.setActive(provider.id)
                _state.update { it.copy(showEditDialog = false, editingProvider = null) }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun setActive(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }

    fun testConnection() {
        val provider = _state.value.editingProvider ?: return
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null) }
            val result = repository.testConnection(provider)
            _state.update {
                it.copy(
                    isTesting = false,
                    testResult = when (result) {
                        is Result.Success -> "连接成功"
                        is Result.Error -> result.message
                    }
                )
            }
        }
    }
}
