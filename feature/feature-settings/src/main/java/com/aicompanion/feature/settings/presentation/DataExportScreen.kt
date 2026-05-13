package com.aicompanion.feature.settings.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aicompanion.core.common.Result
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataExportScreen(
    exportImport: DataExportImport,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showImportPreview by remember { mutableStateOf(false) }
    var importSummary by remember { mutableStateOf<ImportSummary?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            scope.launch {
                isExporting = true
                val result = exportImport.exportToZip(it)
                resultMessage = when (result) {
                    is Result.Success -> "导出成功 (${result.data} bytes)"
                    is Result.Error -> result.message
                }
                isExporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isImporting = true
                val result = exportImport.importFromZip(it)
                when (result) {
                    is Result.Success -> {
                        importSummary = result.data
                        showImportPreview = true
                    }
                    is Result.Error -> resultMessage = result.message
                }
                isImporting = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Export section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("导出数据", style = MaterialTheme.typography.titleMedium)
                            Text("将所有对话、人设、记忆导出为 ZIP 文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("包含内容:", style = MaterialTheme.typography.labelSmall)
                    Text("• 对话记录和消息\n• 人设配置\n• 记忆数据\n• 音色设置",
                        style = MaterialTheme.typography.bodySmall)

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            exportLauncher.launch("ai_companion_backup_${System.currentTimeMillis()}.zip")
                        },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isExporting) "导出中..." else "导出到文件")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Import section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("导入数据", style = MaterialTheme.typography.titleMedium)
                            Text("从之前导出的 ZIP 文件恢复数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/json")) },
                        enabled = !isImporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isImporting) "导入中..." else "选择文件导入")
                    }
                }
            }

            // Result
            if (resultMessage != null) {
                Spacer(Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (resultMessage!!.startsWith("导出成功") || resultMessage!!.startsWith("导入成功"))
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(resultMessage!!, modifier = Modifier.weight(1f))
                        TextButton(onClick = { resultMessage = null }) { Text("关闭") }
                    }
                }
            }

            // Data management
            Text("数据管理", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text("清除所有对话") },
                        leadingContent = { Icon(Icons.Default.DeleteSweep, null) }
                    )
                }
            }
        }

        // Import preview dialog
        if (showImportPreview && importSummary != null) {
            AlertDialog(
                onDismissRequest = { showImportPreview = false },
                title = { Text("导入完成") },
                text = {
                    Column {
                        Text("人设: ${importSummary!!.personaCount} 个")
                        Text("对话: ${importSummary!!.conversationCount} 个")
                        Text("记忆: ${importSummary!!.memoryCount} 个")
                        if (importSummary!!.errors.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text("警告:", color = MaterialTheme.colorScheme.error)
                            importSummary!!.errors.forEach {
                                Text("• $it", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showImportPreview = false }) { Text("确定") } }
            )
        }
    }
}
