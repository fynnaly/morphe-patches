package app.morphe.patches.minutestilldawn

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_20MTD = Compatibility(
        name = "20 Minutes Till Dawn",
        description = "20 Minutes Till Dawn — roguelike survival shooter (Flanne)",
        packageName = "com.Flanne.MinutesTillDawn.roguelike.shooting.fr.gp",
        apkFileType = ApkFileType.XAPK,
        appIconColor = 0x1A1A2E,
        signatures = setOf(
            // APKPure 8.6 / 1093 — Google Inc. cert
            "5fcb0a7a028ba1eed3a81c9ff5dda97344f2c222c326d568542bc1b5fbd9d16a"
        ),
        targets = listOf(
            AppTarget(
                version = "8.6",
                description = "Verified on APKPure XAPK arm64_v8a (versionCode 1093), Unity il2cpp 6000.3.21f1, metadata v31.1"
            ),
        )
    )
}
