package com.aicompanion.app.navigation

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.aicompanion.core.ui.theme.*
import com.aicompanion.domain.model.Persona
import com.aicompanion.domain.repository.*
import com.aicompanion.feature.apiconfig.presentation.ApiConfigScreen
import com.aicompanion.feature.apiconfig.presentation.ApiConfigViewModel
import com.aicompanion.feature.chat.presentation.*
import java.io.File
import com.aicompanion.feature.live2d.Live2DManager
import com.aicompanion.feature.live2d.Live2DModelManagerScreen
import com.aicompanion.feature.memory.presentation.MemoryScreen
import com.aicompanion.feature.memory.presentation.MemoryViewModel
import com.aicompanion.app.proactive.ExperienceGeneratorWorker
import com.aicompanion.app.proactive.MomentGeneratorWorker
import com.aicompanion.feature.persona.presentation.ExperiencesScreen
import com.aicompanion.feature.persona.presentation.MomentsScreen
import com.aicompanion.feature.persona.presentation.PersonaGuidedBuilder
import com.aicompanion.feature.persona.presentation.RensheShichangScreen
import com.aicompanion.feature.persona.presentation.PersonaScreen
import com.aicompanion.feature.persona.presentation.PersonaSettingsScreen
import com.aicompanion.feature.persona.presentation.PersonaViewModel
import com.aicompanion.feature.settings.presentation.*
import com.aicompanion.core.common.ImageGenConfig
import com.aicompanion.feature.voice.*
import java.text.SimpleDateFormat
import java.util.*

object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{personaId}?convId={convId}"
    const val API_CONFIG = "api_config"
    const val PERSONAS = "personas"
    const val MEMORY = "memory"
    const val SETTINGS = "settings"
    const val VOICE_SETTINGS = "voice_settings"
    const val IMAGE_GEN_SETTINGS = "image_gen_settings"
    const val IMAGE_GALLERY = "image_gallery"
    const val LIVE2D_MODELS = "live2d_models"
    const val CONVERSATIONS = "conversations"
    const val DATA_EXPORT = "data_export"
    const val PRIVACY_POLICY = "privacy_policy"
    const val USER_AGREEMENT = "user_agreement"
    const val LICENSE = "license"
    const val OEM_GUIDE = "oem_guide"
    const val PERSONA_SETTINGS = "persona_settings/{personaId}"
    const val STICKER_MANAGE = "sticker_manage/{personaId}"
    const val WORLD_BOOK = "world_book/{personaId}/{personaName}"
    const val PROACTIVE_MESSAGES = "proactive_messages"
    const val MOMENTS = "moments/{personaId}"
    const val EXPERIENCES = "experiences/{personaId}"
    const val RENSHE_SHICHANG = "rensheshichang"
    const val GUIDED_BUILDER = "guided_builder"

    fun chatRoute(personaId: String, convId: String? = null) =
        if (convId != null) "chat/$personaId?convId=$convId" else "chat/$personaId"
    fun personaSettingsRoute(personaId: String) = "persona_settings/$personaId"
    fun worldBookRoute(personaId: String, personaName: String) = "world_book/$personaId/$personaName"
    fun momentsRoute(personaId: String) = "moments/$personaId"
    fun experiencesRoute(personaId: String) = "experiences/$personaId"
}

// Bottom nav tabs
private enum class BottomTab(
    val label: String,
    val outlineIcon: @Composable () -> Unit,
    val filledIcon: @Composable () -> Unit
) {
    CHATS("对话",
        { Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(24.dp)) },
        { Icon(Icons.Default.ChatBubble, null, modifier = Modifier.size(24.dp)) }
    ),
    PERSONAS("人设",
        { Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(24.dp)) },
        { Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp)) }
    ),
    SETTINGS("设置",
        { Icon(Icons.Default.Settings, null, modifier = Modifier.size(24.dp)) },
        { Icon(Icons.Default.Settings, null, modifier = Modifier.size(24.dp)) }
    )
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
    stickerRepository: StickerRepository,
    worldBookRepository: WorldBookRepository,
    context: Context
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // User avatar (persisted to SharedPreferences)
    var userAvatarUri by remember {
        val saved = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).getString("avatar_uri", null)
        mutableStateOf(saved)
    }

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
                Surface(
                    shadowElevation = 4.dp,
                    color = Color(0xF5FFFFFF)
                ) {
                NavigationBar(
                    containerColor = Color(0xF5FFFFFF),
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
                            icon = { if (selected) tab.filledIcon() else tab.outlineIcon() },
                            label = { Text(tab.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Pink500,
                                selectedTextColor = Pink500,
                                indicatorColor = Pink100,
                                unselectedIconColor = TextGray,
                                unselectedTextColor = TextGray
                            )
                        )
                    }
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
            // === HOME: Conversation list + Moments + Experiences ===
            composable(Routes.HOME) {
                val chatVm: ChatViewModel = hiltViewModel()
                val chatState by chatVm.state.collectAsState()
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()

                // Group chat picker dialog
                if (chatState.showGroupPicker) {
                    var selectedIds by remember { mutableStateOf(setOf<String>()) }
                    AlertDialog(
                        onDismissRequest = { chatVm.processIntent(ChatIntent.DismissError) },
                        title = { Text("选择群聊角色", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("请选择 2 个或以上的角色加入群聊", color = TextGray, fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                personaState.personas.forEach { persona ->
                                    val isSelected = persona.id in selectedIds
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                selectedIds = if (isSelected) selectedIds - persona.id
                                                else selectedIds + persona.id
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(checked = isSelected, onCheckedChange = {
                                            selectedIds = if (it) selectedIds + persona.id
                                            else selectedIds - persona.id
                                        })
                                        Spacer(Modifier.width(8.dp))
                                        Text(persona.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        Text(" · ${persona.speakingStyle}", color = TextGray, fontSize = 13.sp)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (selectedIds.size >= 2) {
                                        val sorted = selectedIds.sorted()
                                        val existing = chatState.conversations.firstOrNull {
                                            it.isGroupChat && it.groupPersonaIds.sorted() == sorted && !it.isArchived
                                        }
                                        if (existing != null) {
                                            navController.navigate(Routes.chatRoute(existing.personaId, existing.id))
                                        } else {
                                            val groupConvId = com.aicompanion.core.common.newId()
                                            chatVm.processIntent(ChatIntent.StartGroupChat(selectedIds.toList(), groupConvId))
                                            navController.navigate(Routes.chatRoute(selectedIds.first(), groupConvId))
                                        }
                                    }
                                },
                                enabled = selectedIds.size >= 2
                            ) { Text("开始群聊 (${selectedIds.size})") }
                        },
                        dismissButton = {
                            TextButton(onClick = { chatVm.processIntent(ChatIntent.DismissError) }) { Text("取消") }
                        }
                    )
                }

                val charIllustPath = remember {
                    val f = File(context.filesDir, "char_illust.png")
                    if (f.exists()) f.absolutePath
                    else "file:///android_asset/char_illust.png"
                }

                HomeScreen(
                    personas = personaState.personas,
                    conversations = buildMap {
                        // Include personas with existing conversations
                        chatState.conversations.filter { !it.isGroupChat }.forEach { conv ->
                            val p = personaState.personas.firstOrNull { it.id == conv.personaId }
                            if (p != null) put(conv.personaId, PersonaConversation(
                                persona = p,
                                lastMessage = conv.lastMessagePreview.ifBlank { conv.title },
                                lastMessageTime = conv.lastMessageAt
                            ))
                        }
                        // Also include personas without conversations (show them with firstMessage)
                        personaState.personas.filter { it.id !in this }.forEach { p ->
                            put(p.id, PersonaConversation(
                                persona = p,
                                lastMessage = p.firstMessage.ifBlank { "开始一段新的故事吧～" },
                                lastMessageTime = p.createdAt
                            ))
                        }
                    },
                    groupConversations = chatState.conversations.filter { it.isGroupChat },
                    characterIllustrationPath = charIllustPath,
                    context = context,
                    onPersonaClick = { personaId ->
                        chatVm.processIntent(ChatIntent.SelectPersona(personaId))
                        navController.navigate(Routes.chatRoute(personaId))
                    },
                    onGroupChatClick = { convId ->
                        val conv = chatState.conversations.find { it.id == convId }
                        chatVm.processIntent(ChatIntent.SelectConversation(convId))
                        navController.navigate(Routes.chatRoute(conv?.personaId ?: "", convId))
                    },
                    onNewConversation = {
                        chatVm.processIntent(ChatIntent.NewConversation)
                        if (personaState.personas.isNotEmpty()) {
                            val firstId = personaState.personas.first().id
                            chatVm.processIntent(ChatIntent.SelectPersona(firstId))
                            navController.navigate(Routes.chatRoute(firstId))
                        }
                    },
                    onNewGroupChat = { chatVm.processIntent(ChatIntent.ShowGroupPicker) },
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
                arguments = listOf(
                    navArgument("personaId") { type = NavType.StringType },
                    navArgument("convId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                val convId = backStackEntry.arguments?.getString("convId")
                val vm: ChatViewModel = hiltViewModel()
                val chatState by vm.state.collectAsState()
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()

                LaunchedEffect(personaId, convId) {
                    if (convId != null) {
                        // 1. Load conversation synchronously FIRST
                        vm.setPendingConversation(convId)
                        vm.loadConversationSync(convId)
                        // 2. Then select persona (won't auto-resume because activeConversation is set)
                        vm.processIntent(ChatIntent.SelectPersona(personaId))
                    } else {
                        vm.processIntent(ChatIntent.SelectPersona(personaId))
                    }
                    // 3. Finally load messages
                    if (convId != null) vm.processIntent(ChatIntent.SelectConversation(convId))
                }

                // Build persona map for group chat avatar lookup
                val personaMapForChat = remember(personaState.personas) {
                    personaState.personas.associateBy { it.id }
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
                    onNavigateToConversations = null,
                    onBack = { navController.popBackStack() },
                    onNavigateToPersonaSettings = {
                        navController.navigate(Routes.personaSettingsRoute(personaId))
                    },
                    userAvatarUri = userAvatarUri,
                    personaMap = personaMapForChat,
                    onSpeakMessage = { text ->
                        val vp = com.aicompanion.core.common.VoicePrefs(context)
                        if (vp.isCloudEnabled() && vp.getApiKey().isNotBlank()) {
                            ttsManager.speakCloud(text, vp.getApiKey(), vp.getVoice())
                        } else {
                            val prefs = vp.load()
                            val models = listOf(
                                "sherpa_models/tts/aishell3" to "vits-aishell3.int8.onnx"
                            )
                            val (dir, file) = models[prefs.selectedModelIndex.coerceIn(0, models.size - 1)]
                            ttsManager.speakLazyLoad(text, 0, dir, file)
                        }
                    }
                )
            }

            // === PER-PERSONA SETTINGS ===
            composable(
                route = Routes.PERSONA_SETTINGS,
                arguments = listOf(navArgument("personaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()
                val persona = personaState.personas.find { it.id == personaId }
                val chatVm: ChatViewModel = hiltViewModel()
                val chatState by chatVm.state.collectAsState()

                // Load voice profiles and API providers
                val voiceProfiles by voiceRepository.getProfiles().collectAsState(initial = emptyList())
                val apiProviders by apiProviderRepository.getAll().collectAsState(initial = emptyList())

                if (persona != null) {
                    PersonaSettingsScreen(
                        persona = persona,
                        voiceProfiles = voiceProfiles,
                        apiProviders = apiProviders,
                        conversations = chatState.conversations,
                        onUpdatePersona = { updated ->
                            personaVm.updateEditingPersona(updated)
                            personaVm.savePersona()
                            MomentGeneratorWorker.schedule(context, updated)
                            ExperienceGeneratorWorker.schedule(context, updated)
                        },
                        onDeleteConversation = { convId -> chatVm.deleteConversation(convId) },
                        onOpenConversation = { convId ->
                            val pId = chatState.conversations.find { it.id == convId }?.personaId
                            if (pId != null) {
                                chatVm.processIntent(
                                    com.aicompanion.feature.chat.presentation.ChatIntent.SelectConversation(convId)
                                )
                                navController.navigate(Routes.chatRoute(pId)) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onEditPersona = {
                            navController.navigate(Routes.PERSONAS) {
                                popUpTo(Routes.HOME) { saveState = true }
                            }
                        },
                        onManageStickers = {
                            navController.navigate("sticker_manage/$personaId")
                        },
                        onManageWorldBook = {
                            navController.navigate(Routes.worldBookRoute(persona.id, persona.name))
                        },
                        onManageMoments = {
                            navController.navigate(Routes.momentsRoute(persona.id))
                        },
                        onManageExperiences = {
                            navController.navigate(Routes.experiencesRoute(persona.id))
                        }
                    )
                }
            }

            // === Moments (朋友圈) ===
            composable(
                route = Routes.MOMENTS,
                arguments = listOf(navArgument("personaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()
                val persona = personaState.personas.find { it.id == personaId }

                if (persona != null) {
                    MomentsScreen(
                        persona = persona,
                        onBack = { navController.popBackStack() },
                        onUpdatePersona = { updated ->
                            personaVm.updateEditingPersona(updated)
                            personaVm.savePersona()
                            MomentGeneratorWorker.schedule(context, updated)
                        },
                        onGenerateNow = {
                            MomentGeneratorWorker.generateNow(context, persona.id)
                        }
                    )
                }
            }

            // === Experiences (最近经历) ===
            composable(
                route = Routes.EXPERIENCES,
                arguments = listOf(navArgument("personaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()
                val persona = personaState.personas.find { it.id == personaId }

                if (persona != null) {
                    ExperiencesScreen(
                        persona = persona,
                        onBack = { navController.popBackStack() },
                        onUpdatePersona = { updated ->
                            personaVm.updateEditingPersona(updated)
                            personaVm.savePersona()
                            ExperienceGeneratorWorker.schedule(context, updated)
                        },
                        onGenerateNow = {
                            ExperienceGeneratorWorker.generateNow(context, persona.id)
                        }
                    )
                }
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
                    },
                    onNavigateToMarket = {
                        navController.navigate(Routes.RENSHE_SHICHANG)
                    },
                    onNavigateToGuidedBuilder = {
                        navController.navigate(Routes.GUIDED_BUILDER)
                    }
                )
            }

            // === Renshe Shichang (Card Market) ===
            composable(Routes.RENSHE_SHICHANG) {
                val vm: PersonaViewModel = hiltViewModel()
                RensheShichangScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() }
                )
            }

            // === Guided Builder ===
            composable(Routes.GUIDED_BUILDER) {
                val vm: PersonaViewModel = hiltViewModel()
                PersonaGuidedBuilder(
                    apiProviderRepository = apiProviderRepository,
                    onSave = { persona ->
                        vm.createPersona(persona)
                        navController.navigate(Routes.PERSONAS) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // === SETTINGS ===
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    userAvatarUri = userAvatarUri,
                    onUserAvatarChanged = {
                        userAvatarUri = it
                        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().putString("avatar_uri", it).apply()
                    },
                    onBack = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                    onNavigateToApiConfig = { navController.navigate(Routes.API_CONFIG) },
                    onNavigateToPersonas = { navController.navigate(Routes.PERSONAS) },
                    onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                    onNavigateToProactiveMessages = { navController.navigate(Routes.PROACTIVE_MESSAGES) },
                    onNavigateToVoiceSettings = { navController.navigate(Routes.VOICE_SETTINGS) },
                    onNavigateToImageGen = { navController.navigate(Routes.IMAGE_GEN_SETTINGS) },
                    onNavigateToImageGallery = { navController.navigate(Routes.IMAGE_GALLERY) },
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
            composable(Routes.IMAGE_GEN_SETTINGS) {
                ImageGenSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.IMAGE_GALLERY) {
                ImageGalleryScreen(onBack = { navController.popBackStack() })
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
                        val pId = chatState.conversations.find { it.id == id }?.personaId
                        if (pId != null) {
                            vm.processIntent(ChatIntent.SelectConversation(id))
                            navController.navigate(Routes.chatRoute(pId)) {
                                popUpTo(Routes.HOME) { saveState = true }
                            }
                        }
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
            composable(Routes.PROACTIVE_MESSAGES) {
                val personaVm: PersonaViewModel = hiltViewModel()
                val personaState by personaVm.state.collectAsState()
                ProactiveMessageSettingsScreen(
                    onBack = { navController.popBackStack() },
                    personas = personaState.personas,
                    onReschedule = {
                        val settings = com.aicompanion.core.common.ProactiveSettings(context)
                        com.aicompanion.app.proactive.ProactiveMessageWorker.schedule(context, settings)
                    },
                    onCancel = {
                        com.aicompanion.app.proactive.ProactiveMessageWorker.cancel(context)
                    }
                )
            }
            composable(
                route = Routes.STICKER_MANAGE,
                arguments = listOf(navArgument("personaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                StickerManageScreen(
                    personaId = personaId,
                    repository = stickerRepository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.WORLD_BOOK,
                arguments = listOf(
                    navArgument("personaId") { type = NavType.StringType },
                    navArgument("personaName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val personaId = backStackEntry.arguments?.getString("personaId") ?: ""
                val personaName = backStackEntry.arguments?.getString("personaName") ?: ""
                WorldBookScreen(
                    personaId = personaId,
                    personaName = personaName,
                    repository = worldBookRepository,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
