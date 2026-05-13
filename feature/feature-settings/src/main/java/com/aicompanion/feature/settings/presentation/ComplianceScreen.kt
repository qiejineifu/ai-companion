package com.aicompanion.feature.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ComplianceDoc { PRIVACY_POLICY, USER_AGREEMENT, OPEN_SOURCE_LICENSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    ComplianceDocScreen(
        title = "隐私政策",
        onBack = onBack,
        lastUpdated = "2026-05-01",
        content = """
隐私政策

最后更新日期：2026年5月1日

1. 信息收集
AI陪伴（以下简称"本应用"）可能收集以下信息：
• 您主动提供的 API 密钥和配置信息
• 聊天对话记录（存储在本地设备）
• 语音输入数据（用于语音识别功能）
• 设备信息（系统版本、设备型号，仅用于性能优化）

2. 信息存储
• 所有对话记录、人设配置、记忆数据均存储在您的设备本地
• API 密钥使用 Android KeyStore AES-256 加密存储
• 本应用不上传任何个人数据到第三方服务器

3. 信息使用
• API 密钥仅用于您指定的 AI 服务提供商进行身份验证
• 语音数据仅用于本地或您选择的云服务进行语音识别
• 不会将您的数据用于广告推送或用户画像

4. 第三方服务
本应用可能接入以下第三方服务：
• AI 模型 API（由您自行配置的服务商）
• 语音识别/合成服务（科大讯飞、系统内置引擎）
使用这些服务时，数据将直接发送至对应的服务商。

5. 权限说明
• 录音权限：用于语音输入功能
• 通知权限：用于后台语音服务状态提示
• 存储权限：用于导入 Live2D 模型和数据备份

6. 数据安全
• 采用 SQLCipher 加密本地数据库
• 网络传输使用 HTTPS/TLS 加密
• SSL Certificate Pinning 防止中间人攻击

7. 用户权利
您可以在设置中：
• 导出全部数据
• 清除所有本地数据
• 随时卸载应用，数据将被完全删除

8. 联系我们
如有隐私相关问题，请联系我们。

9. 政策更新
我们可能会更新本隐私政策，更新后的政策将在应用内公布。
        """.trimIndent()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgreementScreen(onBack: () -> Unit) {
    ComplianceDocScreen(
        title = "用户协议",
        onBack = onBack,
        lastUpdated = "2026-05-01",
        content = """
用户协议

最后更新日期：2026年5月1日

1. 服务说明
AI陪伴是一款AI虚拟角色陪伴应用，提供文字/语音对话、Live2D虚拟形象展示等功能。

2. 用户责任
• 您需要对自行配置的 API 服务的使用负责
• 不得利用本应用生成违法、违规内容
• 不得利用本应用进行骚扰、诈骗等行为
• 不得修改、逆向工程本应用的核心代码

3. 知识产权
• 本应用的代码、界面设计归开发者所有
• Live2D 模型文件的版权归模型原作者所有
• 用户导入的模型文件由用户自行负责版权合规

4. 免责声明
• 本应用提供的 AI 回复由第三方 AI 模型生成，不代表开发者观点
• AI 生成内容仅供参考，不构成任何专业建议
• 因 API 服务不可用导致的功能异常，开发者不承担责任

5. 服务终止
• 我们保留在必要时修改或终止服务的权利
• 用户可随时停止使用本应用

6. 准据法
本协议适用中华人民共和国法律。
        """.trimIndent()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicenseScreen(onBack: () -> Unit) {
    ComplianceDocScreen(
        title = "开源许可",
        onBack = onBack,
        lastUpdated = "2026-05-01",
        content = """
开源软件许可声明

本应用使用了以下开源软件：

1. Jetpack Compose (Apache 2.0)
   Copyright Google LLC
   https://developer.android.com/jetpack/compose

2. Kotlin Coroutines (Apache 2.0)
   Copyright JetBrains s.r.o.
   https://github.com/Kotlin/kotlinx.coroutines

3. OkHttp (Apache 2.0)
   Copyright Square, Inc.
   https://github.com/square/okhttp

4. Ktor (Apache 2.0)
   Copyright JetBrains s.r.o.
   https://github.com/ktorio/ktor

5. Coil (Apache 2.0)
   Copyright Coil Contributors
   https://github.com/coil-kt/coil

6. Room (Apache 2.0)
   Copyright Google LLC

7. Hilt/Dagger (Apache 2.0)
   Copyright Google LLC

8. SQLCipher (BSD)
   Copyright Zetetic LLC

9. Gson (Apache 2.0)
   Copyright Google LLC

10. Live2D Cubism SDK
    Copyright Live2D Inc.

11. Sherpa-ONNX (Apache 2.0)
    Copyright k2-fsa
    https://github.com/k2-fsa/sherpa-onnx

12. AndroidX (Apache 2.0)
    Copyright Google LLC

完整的许可证文本请访问 Apache License 2.0:
http://www.apache.org/licenses/LICENSE-2.0
        """.trimIndent()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplianceDocScreen(
    title: String,
    onBack: () -> Unit,
    lastUpdated: String,
    content: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(
                "最后更新: $lastUpdated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(16.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(32.dp))
        }
    }
}
