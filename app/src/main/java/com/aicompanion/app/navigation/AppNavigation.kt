package com.aicompanion.app.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.repository.*
import com.aicompanion.feature.apiconfig.presentation.ApiConfigScreen
import com.aicompanion.feature.apiconfig.presentation.ApiConfigViewModel
import com.aicompanion.feature.chat.presentation.*
import com.aicompanion.feature.live2d.Live2DManager
import com.aicompanion.feature.live2d.Live2DModelManagerScreen
import com.aicompanion.feature.memory.presentation.MemoryScreen
import com.aicompanion.feature.memory.presentation.MemoryViewModel
import com.aicompanion.feature.persona.presentation.PersonaScreen
import com.aicompanion.feature.persona.presentation.PersonaViewModel
import com.aicompanion.feature.settings.presentation.*
import com.aicompanion.feature.voice.*
import java.text.SimpleDateFormat
import java.util.*

// Anime pink palette (shared across screens)
val Pink50 = Color(0xFFFFF0F5)
val Pink100 = Color(0xFFFFE0EC)
val Pink200 = Color(0xFFFFC0D8)
val Pink400 = Color(0xFFFF85A2)
val Pink500 = Color(0xFFFF6B8A)
val Pink600 = Color(0xFFF04F7A)
val Pink700 = Color(0xFFE0386A)
val Purple400 = Color(0xFFC4A5E8)
val TextDarkA = Color(0xFF2D1B2E)
val TextGrayA = Color(0xFF9B8EA0)

object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{personaId}"
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

    fun chatRoute(personaId: String) = "chat/$personaId"
}

// Bottom nav tabs
private enum class BottomTab(val label: String, val icon: @Composable () -> Unit) {
    CHATS("对话", { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(24.dp)) }),
    PERSONAS("人设", { Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp)) }),
    SETTINGS("设置", { Icon(Icons.Default.Settings, null, modifier = Modifier.size(24.dp)) })
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
    context: Context
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only show bottom nav on HOME, PERSONAS, SETTINGS (not on CHAT or sub-screens)
    val showBottomNav = currentRoute in listOf(Routes.HOME, Routes.PERSONAS, Routes.SETTINGS)

    val dataExportImport = DataExportImport(
        context = context,
        personaRepository = personaRepository,
        apiProviderRepository = apiProviderRepository,
        memoryRepository = memoryRepository,
        chatRepository = chatRepository,
        voiceRepository = voiceRepository
    )

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp
                ) {
                    BottomTab.entries.forEach { tab ->
                        val selected = when (tab) {
                            BottomTab.CHATS -> currentRoute == Routes.HOME
                            BottomTab.PERSONAS -> currentRoute == Routes.PERSONAS
                            BottomTab.SETTINGS -> currentRoute == Routes.SETTINGS
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                val target = when (tab) {
                                    BottomTab.CHATS -> Routes.HOME
                                    BottomTab.PERSONAS -> Routes.PERSONAS
                                    BottomTab.SETTINGS -> Routes.SETTINGS
                                }
                                if (currentRoute != target) {
                                    navController.navigate(target) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { tab.icon() },
                            label = { Text(tab.label, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Pink500,
                                selectedTextColor = Pink500,
                                indicatorColor = Pink50,
                                unselectedIconColor = TextGrayA,
                                unselectedTextColor = TextGrayA
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            // === HOME: Conversation list ===
            composable(Routes.HOME) {
                val chatVm: ChatViewModel = hiltViewModel()
                val chatState by chatVm.state.collectAsState()
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()

                // Build conversation info per persona
                val personaConvs = remember(chatState.conversations, personaState.personas) {
                    personaState.personas.associateWith { p ->
                        chatState.conversations.find { it.personaId == p.id }
                    }
                }

                ConversationListScreen(
                    personas = personaState.personas,
                    conversations = personaConvs.mapKeys { it.key.id }.mapValues { (pId, conv) ->
                        PersonaConversation(
                            persona = personaConvs.entries.first { it.key.id == pId }.key,
                            lastMessage = conv?.title ?: "",
                            lastMessageTime = conv?.lastMessageAt ?: 0L
                        )
                    },
                    activePersonaId = chatState.activePersona?.id,
                    onPersonaClick = { personaId ->
                        chatVm.processIntent(ChatIntent.SelectPersona(personaId))
                        navController.navigate(Routes.chatRoute(personaId))
                    },
                    onNewConversation = {
                        chatVm.processIntent(ChatIntent.NewConversation)
                        if (personaState.personas.isNotEmpty()) {
                            val firstId = personaState.personas.first().id
                            chatVm.processIntent(ChatIntent.SelectPersona(firstId))
                            navController.navigate(Routes.chatRoute(firstId))
                        }
                    },
                    onNavigateToPersonas = {
                        navController.navigate(Routes.PERSONAS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // === CHAT: Conversation detail ===
            composable(
                route = Routes.CHAT,
                arguments = listOf(navArgument("personaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                val vm: ChatViewModel = hiltViewModel()
                val chatState by vm.state.collectAsState()

                // Load persona if needed
                LaunchedEffect(personaId) {
                    if (chatState.activePersona?.id != personaId) {
                        vm.processIntent(ChatIntent.SelectPersona(personaId))
                    }
                }

                ChatScreen(
                    viewModel = vm,
                    onNavigateToPersonas = {
                        navController.navigate(Routes.PERSONAS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToApiConfig = { navController.navigate(Routes.API_CONFIG) },
                    onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                    onNavigateToSettings = {
                        navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToConversations = null, // No longer needed — use back to go to list
                    onBack = { navController.popBackStack() }
                )
            }

            // === PERSONAS management ===
            composable(Routes.PERSONAS) {
                val vm: PersonaViewModel = hiltViewModel()
                PersonaScreen(
                    viewModel = vm,
                    onBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onSelectPersona = { personaId ->
                        navController.navigate(Routes.chatRoute(personaId))
                    }
                )
            }

            // === SETTINGS ===
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
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

            // Sub-screens (same as before)
            composable(Routes.API_CONFIG) {
                val vm: ApiConfigViewModel = hiltViewModel()
                ApiConfigScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.MEMORY) {
                val vm: MemoryViewModel = hiltViewModel()
                MemoryScreen(viewModel = vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.VOICE_SETTINGS) {
                VoiceSettingsScreen(ttsManager = ttsManager, onBack = { navController.popBackStack() })
            }
            composable(Routes.VOICE_PROFILES) {
                VoiceProfileScreen(voiceRepository = voiceRepository, ttsManager = ttsManager, onBack = { navController.popBackStack() })
            }
            composable(Routes.LIVE2D_MODELS) {
                Live2DModelManagerScreen(manager = live2DManager, onBack = { navController.popBackStack() })
            }
            composable(Routes.CONVERSATIONS) {
                val vm: ChatViewModel = hiltViewModel()
                val chatState by vm.state.collectAsState()
                ConversationManagerScreen(
                    conversations = chatState.conversations,
                    activeId = chatState.activeConversation?.id,
                    onSelect = { id ->
                        vm.processIntent(ChatIntent.SelectConversation(id))
                        // Navigate to chat with this conversation's persona
                        val pId = chatState.conversations.find { it.id == id }?.personaId
                        if (pId != null) navController.navigate(Routes.chatRoute(pId))
                    },
                    onNew = {
                        vm.processIntent(ChatIntent.NewConversation)
                        navController.popBackStack()
                    },
                    onDelete = { vm.deleteConversation(it) },
                    onRename = { id, name -> vm.renameConversation(id, name) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.DATA_EXPORT) {
                DataExportScreen(exportImport = dataExportImport, onBack = { navController.popBackStack() })
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
}
