package com.aicompanion.core.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Guides users through OEM-specific battery optimization and background
 * keep-alive settings for major Chinese manufacturers.
 */
object OEMGuideHelper {

    enum class Manufacturer {
        XIAOMI, HUAWEI, OPPO, VIVO, SAMSUNG, GOOGLE, UNKNOWN
    }

    data class OEMGuide(
        val manufacturer: Manufacturer,
        val steps: List<String>,
        val settingsIntent: Intent? = null
    )

    fun detectManufacturer(): Manufacturer {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            brand.contains("xiaomi") || manufacturer.contains("xiaomi") -> Manufacturer.XIAOMI
            brand.contains("huawei") || manufacturer.contains("huawei") -> Manufacturer.HUAWEI
            brand.contains("oppo") || manufacturer.contains("oppo") -> Manufacturer.OPPO
            brand.contains("vivo") || manufacturer.contains("vivo") -> Manufacturer.VIVO
            brand.contains("samsung") -> Manufacturer.SAMSUNG
            brand.contains("google") -> Manufacturer.GOOGLE
            else -> Manufacturer.UNKNOWN
        }
    }

    fun getGuide(): OEMGuide {
        return when (detectManufacturer()) {
            Manufacturer.XIAOMI -> OEMGuide(
                Manufacturer.XIAOMI,
                listOf(
                    "1. 进入「设置」→「应用设置」",
                    "2. 找到「AI陪伴」应用",
                    "3. 打开「自启动」开关",
                    "4. 进入「省电策略」→ 选择「无限制」",
                    "5. 进入「近期任务」→ 长按应用 → 点击锁图标"
                ),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.aicompanion.app")
                }
            )
            Manufacturer.HUAWEI -> OEMGuide(
                Manufacturer.HUAWEI,
                listOf(
                    "1. 进入「设置」→「应用」→「应用启动管理」",
                    "2. 找到「AI陪伴」→ 关闭「自动管理」",
                    "3. 打开「自启动」「关联启动」「后台活动」",
                    "4. 进入「设置」→「电池」→ 关闭应用的「省电模式」"
                ),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.aicompanion.app")
                }
            )
            Manufacturer.OPPO -> OEMGuide(
                Manufacturer.OPPO,
                listOf(
                    "1. 进入「设置」→「应用管理」",
                    "2. 找到「AI陪伴」→「耗电保护」",
                    "3. 选择「允许后台运行」",
                    "4. 进入「设置」→「电池」→ 更多 → 关闭「智能省电」"
                ),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.aicompanion.app")
                }
            )
            Manufacturer.VIVO -> OEMGuide(
                Manufacturer.VIVO,
                listOf(
                    "1. 进入「i管家」→「应用管理」→「权限管理」",
                    "2. 找到「AI陪伴」→ 打开「自启动」",
                    "3. 进入「设置」→「电池」→ 后台高耗电 → 允许"
                )
            )
            else -> OEMGuide(
                Manufacturer.UNKNOWN,
                listOf(
                    "1. 进入系统「设置」→「应用」",
                    "2. 找到「AI陪伴」",
                    "3. 选择「电池」→「电池优化」→ 选择「不优化」",
                    "4. 确保通知权限已开启"
                ),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:com.aicompanion.app")
                }
            )
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimization(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun getKeepAliveGuideText(): String {
        val guide = getGuide()
        val sb = StringBuilder()
        sb.appendLine("为确保 AI 陪伴在后台正常运行，请按以下步骤设置：")
        sb.appendLine()
        guide.steps.forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("设置完成后，AI 语音陪伴将持续运行。")
        return sb.toString()
    }
}
