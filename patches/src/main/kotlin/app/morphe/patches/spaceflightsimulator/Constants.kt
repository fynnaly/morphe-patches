package app.morphe.patches.spaceflightsimulator

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_SFS = Compatibility(
        name = "Spaceflight Simulator",
        description = "Spaceflight Simulator — rocket building and orbital sandbox (Stefo Mai Morojna)",
        packageName = "com.StefMorojna.SpaceflightSimulator",
        apkFileType = ApkFileType.APK,
        appIconColor = 0x0B1026,
        signatures = setOf(
            // APKPure 1.6.00.22 / 897 — original signer (C=SI, O=Stefo Mai Morojna)
            "0ec8f3e8e6ddf74cc2b4b9808bef30dfef3157ffce710fa4d971b780690cd84b"
        ),
        targets = listOf(
            AppTarget(
                version = "1.6.00.22",
                description = "Verified anchors on APKPure APK (versionCode 897), Unity il2cpp 6000.1.17f1, metadata v31.1"
            ),
        )
    )
}
