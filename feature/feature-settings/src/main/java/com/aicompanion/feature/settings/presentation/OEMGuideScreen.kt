package com.aicompanion.feature.settings.presentation

import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicompanion.core.common.OEMGuideHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OEMGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val guide = remember { OEMGuideHelper.getGuide() }
    val isIgnored = remember { OEMGuideHelper.isBatteryOptimizationIgnored(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("后台运行设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Battery optimization status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isIgnored)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                        null,
                        tint = if (isIgnored) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (isIgnored) "电池优化已关闭，后台运行正常"
                        else "需要关闭电池优化以保持后台运行",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (!isIgnored) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = OEMGuideHelper.requestIgnoreBatteryOptimization(context)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BatterySaver, null)
                    Spacer(Modifier.width(8.dp))
                    Text("关闭电池优化")
                }
            }

            Spacer(Modifier.height(24.dp))

            // OEM-specific guide
            Text(
                "设备专属设置指南",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${guide.manufacturer.name} 设备设置步骤：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    guide.steps.forEachIndexed { index, step ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(step.replaceFirst("${index + 1}. ", ""))
                        }
                    }
                }
            }

            // Quick action
            if (guide.settingsIntent != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { context.startActivity(guide.settingsIntent) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开应用设置")
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("打开系统电池设置")
            }

            Spacer(Modifier.height(32.dp))

            // Tips
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("温馨提示", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• 语音对话模式需要后台持续运行\n" +
                        "• 建议在 Wi-Fi 环境下使用语音功能\n" +
                        "• 长时间使用可能增加电量消耗\n" +
                        "• 您可以在通知栏快速控制语音对话",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
