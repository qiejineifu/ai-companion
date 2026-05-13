package com.aicompanion.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.aicompanion.app.navigation.AppNavigation
import com.aicompanion.core.ui.theme.AICompanionTheme
import com.aicompanion.domain.repository.*
import com.aicompanion.feature.live2d.Live2DManager
import com.aicompanion.feature.voice.TTSManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var live2DManager: Live2DManager
    @Inject lateinit var voiceRepository: VoiceRepository
    @Inject lateinit var personaRepository: PersonaRepository
    @Inject lateinit var apiProviderRepository: ApiProviderRepository
    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var chatRepository: ChatRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            AICompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        ttsManager = ttsManager,
                        live2DManager = live2DManager,
                        voiceRepository = voiceRepository,
                        personaRepository = personaRepository,
                        apiProviderRepository = apiProviderRepository,
                        memoryRepository = memoryRepository,
                        chatRepository = chatRepository,
                        context = this
                    )
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
