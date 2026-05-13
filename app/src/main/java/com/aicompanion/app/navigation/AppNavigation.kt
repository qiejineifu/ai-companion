package com.aicompanion.app.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aicompanion.domain.repository.*
import com.aicompanion.feature.apiconfig.presentation.ApiConfigScreen
import com.aicompanion.feature.apiconfig.presentation.ApiConfigViewModel
import com.aicompanion.feature.chat.presentation.ChatIntent
import com.aicompanion.feature.chat.presentation.ChatScreen
import com.aicompanion.feature.chat.presentation.ChatViewModel
import com.aicompanion.feature.chat.presentation.ConversationManagerScreen
import com.aicompanion.feature.live2d.Live2DManager
import com.aicompanion.feature.live2d.Live2DModelManagerScreen
import com.aicompanion.feature.memory.presentation.MemoryScreen
import com.aicompanion.feature.memory.presentation.MemoryViewModel
import com.aicompanion.feature.persona.presentation.PersonaScreen
import com.aicompanion.feature.persona.presentation.PersonaViewModel
import com.aicompanion.feature.settings.presentation.ComplianceDoc
import com.aicompanion.feature.settings.presentation.DataExportImport
import com.aicompanion.feature.settings.presentation.DataExportScreen
import com.aicompanion.feature.settings.presentation.OpenSourceLicenseScreen
import com.aicompanion.feature.settings.presentation.PrivacyPolicyScreen
import com.aicompanion.feature.settings.presentation.OEMGuideScreen
import com.aicompanion.feature.settings.presentation.SettingsScreen
import com.aicompanion.feature.settings.presentation.UserAgreementScreen
import com.aicompanion.feature.voice.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

object Routes {
    const val CHAT = "chat"
    const val API_CONFIG = "api_config"
    const val PERSONAS = "personas"
    const val MEMORY = "memory"
    const val SETTINGS = "settings"
    const val VOICE_SETTINGS = "voice_settings"
    const val VOICE_PROFILES = "voice_profiles"
    const val LIVE2D_MODELS = "live2d_models"
    const val CONVERSATIONS = "conversations"
    const val DATA_EXPORT = "data_export"
    const val PRIVACY_POLICY = "privacy_policy"
    const val USER_AGREEMENT = "user_agreement"
    const val LICENSE = "license"
    const val OEM_GUIDE = "oem_guide"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    ttsManager: TTSManager,
    live2DManager: Live2DManager,
    voiceRepository: VoiceRepository,
    personaRepository: PersonaRepository,
    apiProviderRepository: ApiProviderRepository,
    memoryRepository: MemoryRepository,
    chatRepository: ChatRepository,
    context: android.content.Context
) {
    val dataExportImport = DataExportImport(
        context = context,
        personaRepository = personaRepository,
        apiProviderRepository = apiProviderRepository,
        memoryRepository = memoryRepository,
        chatRepository = chatRepository,
        voiceRepository = voiceRepository
    )

    NavHost(navController = navController, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) { backStackEntry ->
            val vm: ChatViewModel = hiltViewModel()
            // Observe persona selection result from PersonaScreen
            val selectedPersonaId = backStackEntry.savedStateHandle.get<String>("selectedPersonaId")
            LaunchedEffect(selectedPersonaId) {
                selectedPersonaId?.let {
                    vm.processIntent(ChatIntent.SelectPersona(it))
                    backStackEntry.savedStateHandle.remove<String>("selectedPersonaId")
                }
            }
            ChatScreen(
                viewModel = vm,
                onNavigateToPersonas = { navController.navigate(Routes.PERSONAS) },
                onNavigateToApiConfig = { navController.navigate(Routes.API_CONFIG) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToConversations = { navController.navigate(Routes.CONVERSATIONS) }
            )
        }
        composable(Routes.API_CONFIG) {
            val vm: ApiConfigViewModel = hiltViewModel()
            ApiConfigScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.PERSONAS) {
            val vm: PersonaViewModel = hiltViewModel()
            PersonaScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onSelectPersona = { personaId ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("selectedPersonaId", personaId)
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.MEMORY) {
            val vm: MemoryViewModel = hiltViewModel()
            MemoryScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToApiConfig = { navController.navigate(Routes.API_CONFIG) },
                onNavigateToPersonas = { navController.navigate(Routes.PERSONAS) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                onNavigateToVoiceSettings = { navController.navigate(Routes.VOICE_SETTINGS) },
                onNavigateToVoiceProfiles = { navController.navigate(Routes.VOICE_PROFILES) },
                onNavigateToLive2DModels = { navController.navigate(Routes.LIVE2D_MODELS) },
                onNavigateToConversations = { navController.navigate(Routes.CONVERSATIONS) },
                onNavigateToDataExport = { navController.navigate(Routes.DATA_EXPORT) },
                onNavigateToPrivacyPolicy = { navController.navigate(Routes.PRIVACY_POLICY) },
                onNavigateToUserAgreement = { navController.navigate(Routes.USER_AGREEMENT) },
                onNavigateToLicense = { navController.navigate(Routes.LICENSE) },
                onNavigateToOEMGuide = { navController.navigate(Routes.OEM_GUIDE) }
            )
        }
        composable(Routes.VOICE_SETTINGS) {
            VoiceSettingsScreen(ttsManager = ttsManager, onBack = { navController.popBackStack() })
        }
        composable(Routes.VOICE_PROFILES) {
            VoiceProfileScreen(
                voiceRepository = voiceRepository,
                ttsManager = ttsManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.LIVE2D_MODELS) {
            Live2DModelManagerScreen(
                manager = live2DManager,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CONVERSATIONS) {
            val vm: ChatViewModel = hiltViewModel()
            val chatState by vm.state.collectAsState()
            ConversationManagerScreen(
                conversations = chatState.conversations,
                activeId = chatState.activeConversation?.id,
                onSelect = { id ->
                    vm.processIntent(com.aicompanion.feature.chat.presentation.ChatIntent.SelectConversation(id))
                    navController.popBackStack()
                },
                onNew = {
                    vm.processIntent(com.aicompanion.feature.chat.presentation.ChatIntent.NewConversation)
                    navController.popBackStack()
                },
                onDelete = { vm.deleteConversation(it) },
                onRename = { id, name -> vm.renameConversation(id, name) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DATA_EXPORT) {
            DataExportScreen(
                exportImport = dataExportImport,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.USER_AGREEMENT) {
            UserAgreementScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LICENSE) {
            OpenSourceLicenseScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.OEM_GUIDE) {
            OEMGuideScreen(onBack = { navController.popBackStack() })
        }
    }
}
