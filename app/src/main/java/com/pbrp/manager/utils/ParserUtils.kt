package com.pbrp.manager.utils

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.pbrp.manager.ui.theme.*

// Represents a feature or warning (AVB, DM-Verity, etc)
data class DeviceFeature(
    val title: String,
    val description: String,
    val color: Color,
    val borderColor: Color,
    val icon: String
)

enum class InstallType(val title: String, val icon: String) {
    FASTBOOT("Fastboot Install", "⚡"),
    FASTBOOT_AB("Fastboot Install (A/B)", "⚡"),
    FASTBOOT_VB("Fastboot (Vendor Boot)", "💾"),
    FASTBOOT_BOOT("Fastboot (Boot Partition)", "⚠️"),
    FASTBOOT_REC("Fastboot (Recovery)", "⚡"),
    HTC("HTC Install", "📱"),
    ODIN("Odin Install (Samsung)", "🖥️"),
    RECOVERY("Recovery Install (Zip)", "🔄"),
    DD("DD Install (Root)", "#️⃣"),
    FLASH_IMAGE("Flash Image (Root)", "#️⃣"),
    MTK("SP Flash Tool", "M")
}

data class InstallMethod(val type: InstallType, val steps: List<String>, val note: String? = null)

object ParserUtils {
    
    fun parseMarkdownValue(content: String, key: String): String {
        val regex = Regex("$key:\\s*(.*)", RegexOption.IGNORE_CASE)
        return regex.find(content)?.groupValues?.get(1)?.trim() ?: ""
    }

    // Parse Features/Warnings from _includes
    fun parseFeatures(content: String): List<DeviceFeature> {
        val features = mutableListOf<DeviceFeature>()

        if (content.contains("avb.html", ignoreCase = true)) {
            features.add(DeviceFeature(
                "AVB Enabled",
                "This device uses Android Verified Boot. Disable verity to boot custom recovery.\nCommand: fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img",
                Warn_Blue_Bg, Warn_Blue_Border, "🛡️"
            ))
        }

        if (content.contains("dmverity.html", ignoreCase = true)) {
            features.add(DeviceFeature(
                "DM-Verity",
                "System modifications prevent booting. Install a kernel with dm-verity disabled or flash a disabler zip immediately.",
                Warn_Red_Bg, Warn_Red_Border, "🔒"
            ))
        }

        if (content.contains("dynamicpartitions.html", ignoreCase = true)) {
            features.add(DeviceFeature(
                "Dynamic Partitions",
                "Uses logical partitions (Super). PBRP cannot modify system/vendor directly. Use fastbootd.",
                Warn_Purple_Bg, Warn_Purple_Border, "💾"
            ))
        }
        
        if (content.contains("samsungsystemasroot.html", ignoreCase = true)) {
            features.add(DeviceFeature(
                "Samsung System-as-Root",
                "A-only. Magisk and Recovery share partition.\nPower + Vol Up = Recovery.\nPower Only = Stock.",
                Warn_Red_Bg, Warn_Red_Border, "⚠️"
            ))
        }

        if (content.contains("fotakernelnote.html", ignoreCase = true)) {
            features.add(DeviceFeature(
                "Sony FOTAKernel",
                "Recovery resides in the FOTAKernel partition. Your kernel must support 'extract_elf_ramdisk'.",
                Warn_Purple_Bg, Warn_Purple_Border, "ℹ️"
            ))
        }

        return features
    }

    // Parse Installation Methods from _includes
    fun parseInstallMethods(context: Context, content: String): List<InstallMethod> {
        val methods = mutableListOf<InstallMethod>()
        
        // 1. Fastboot Standard (fastbootinstall.html)
        if (content.contains("fastbootinstall.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.FASTBOOT, listOf(
                "Reboot to Bootloader: adb reboot bootloader",
                "Preferred: fastboot boot pbrp.img (Then flash zip in recovery)",
                "Alternative (A-only): fastboot flash recovery pbrp.img",
                "Reboot: fastboot reboot (Hold Vol Up + Power)"
            ), "Bootloader must be unlocked."))
        }

        // 2. Fastboot A/B (fastbootabinstall.html)
        if (content.contains("fastbootabinstall.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.FASTBOOT_AB, listOf(
                "Reboot to Bootloader: adb reboot bootloader",
                "Boot PBRP: fastboot boot pbrp.img",
                "Copy PBRP zip to device.",
                "Install > Select Zip > Swipe (Patches both slots)."
            ), "Caution: Do not use 'fastboot flash recovery' on A/B devices."))
        }

        // 3. Fastboot Recovery Partition (fastbootinstallrecoveryab.html)
        if (content.contains("fastbootinstallrecoveryab.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.FASTBOOT_REC, listOf(
                "Reboot to Bootloader: adb reboot bootloader",
                "Flash: fastboot flash recovery pbrp.img",
                "Reboot immediately to Recovery to prevent overwrite."
            )))
        }

        // 4. Vendor Boot (fastbootvendorbootxiaomiabmtk.html)
        if (content.contains("fastbootvendorbootxiaomiabmtk.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.FASTBOOT_VB, listOf(
                "Reboot to Bootloader: adb reboot bootloader",
                "Flash: fastboot flash vendor_boot pbrp.img",
                "Reboot to Recovery: fastboot reboot recovery"
            ), "For Android 12+ GKI devices."))
        }

        // 5. Boot Partition (fastbootxiaomiabmtk.html)
        if (content.contains("fastbootxiaomiabmtk.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.FASTBOOT_BOOT, listOf(
                "Reboot to Bootloader: adb reboot bootloader",
                "Flash: fastboot flash boot pbrp.img",
                "Reboot to Recovery: fastboot reboot recovery"
            ), "Warning: Ensure device uses 'Recovery in Boot'."))
        }

        // 6. HTC (fastbootinstallhtc.html)
        if (content.contains("fastbootinstallhtc.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.HTC, listOf(
                "Reboot to Download Mode: adb reboot download",
                "Flash: fastboot flash recovery pbrp.img",
                "Reboot: fastboot reboot"
            )))
        }

        // 7. Odin (odininstall.html)
        if (content.contains("odininstall.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.ODIN, listOf(
                "Boot to Download Mode.",
                "Open Odin. Click AP/PDA.",
                "Select .tar.md5 file.",
                "Uncheck 'Auto Reboot'.",
                "Click Start. Force reboot to Recovery manually."
            )))
        }

        // 8. DD Install (ddinstall.html)
        if (content.contains("ddinstall.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.DD, listOf(
                "Requires Root. Place pbrp.img in /sdcard/",
                "Terminal: su",
                "Command: dd if=/sdcard/pbrp.img of=/dev/block/bootdevice/by-name/recovery"
            )))
        }

        // 9. Flash Image (flashimageinstall.html)
        if (content.contains("flashimageinstall.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.FLASH_IMAGE, listOf(
                "Requires Root. Place pbrp.img in /sdcard/",
                "Terminal: su",
                "Command: flash_image recovery /sdcard/pbrp.img"
            )))
        }

        // 10. MTK (mtkinstall.html)
        if (content.contains("mtkinstall.html", ignoreCase = true)) {
            methods.add(InstallMethod(InstallType.MTK, listOf(
                "Open SP Flash Tool.",
                "Load Scatter file.",
                "Uncheck all except RECOVERY.",
                "Select pbrp.img.",
                "Click Download, then connect powered-off device."
            )))
        }

        // 11. Recovery Install (pbrpinstall.html) - DEFAULT FALLBACK
        // Added if explicitly included OR if no other methods found (e.g. SourceForge fallback)
        if (content.contains("pbrpinstall.html", ignoreCase = true) || methods.isEmpty()) {
            methods.add(InstallMethod(InstallType.RECOVERY, listOf(
                "Download the latest PBRP zip.",
                "Boot into current custom recovery.",
                "Install > Select Zip > Swipe.",
                "Reboot to Recovery."
            )))
        }

        return methods
    }
}
