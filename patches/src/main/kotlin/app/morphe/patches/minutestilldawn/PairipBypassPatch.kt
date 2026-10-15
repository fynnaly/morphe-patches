package app.morphe.patches.minutestilldawn

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.minutestilldawn.Constants.COMPATIBILITY_20MTD
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * PairIP tamper gates — prerequisite for any re-sign.
 *
 * 20 Minutes Till Dawn 8.6 / 1093 carries the same PairIP stack as Citampi:
 *   AndroidManifest android:name="com.pairip.application.Application"
 *   extends com.safedk.android.SafeDKApplication
 *   smali_classes2/com/pairip/SignatureCheck.verifyIntegrity(Context)
 *   smali_classes2/com/pairip/licensecheck/LicenseClient.checkLicense(Context)
 *   smali_classes2/com/pairip/VMRunner.<clinit> + invoke + setContext
 *
 * verifyIntegrity computes SHA-256(Base64) of PackageInfo.signatures[0] and
 * throws SignatureTamperedException("Apk signature is invalid.") unless it
 * equals expectedSignature or ALLOWLISTED_SIG. Any re-signed APK dies in
 * Application.attachBaseContext before super — so every downstream patch
 * must depend on this bypass.
 *
 * Guard strings asserted: "Apk signature is invalid." /
 * "Cannot check license with null context." / "pairipcore".
 */

private object SignatureCheckVerifyFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/SignatureCheck;",
    name = "verifyIntegrity",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
    strings = listOf("Apk signature is invalid.")
)

private object LicenseClientCheckFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "checkLicense",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
    // packageName lives in a static field initializer, not in this method —
    // use a body string instead ("Cannot check license with null context.").
    strings = listOf("Cannot check license with null context.")
)

private object VMRunnerClinitFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/VMRunner;",
    name = "<clinit>",
    // No accessFlags/returnType: <clinit> carries STATIC|CONSTRUCTOR and the
    // patcher matches flags exactly, so declaring STATIC alone would miss.
    strings = listOf("pairipcore")
)

private object VMRunnerInvokeFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/VMRunner;",
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Ljava/lang/String;", "[Ljava/lang/Object;"),
)

private object VMRunnerSetContextFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/VMRunner;",
    name = "setContext",
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    parameters = listOf("Landroid/content/Context;"),
)

val pairipBypassPatch = bytecodePatch(
    name = "Bypass PairIP signature & license check",
    description = "Neutralizes PairIP tamper gates (SignatureCheck, LicenseClient, VMRunner) so re-signed APKs no longer crash in Application.attachBaseContext. Required for every Minutes Till Dawn patch."
) {
    compatibleWith(COMPATIBILITY_20MTD)

    execute {
        // Signature gate — return before cert hash comparison.
        // Early return keeps the original body as dead but verifiable code.
        SignatureCheckVerifyFingerprint.method.addInstructions(0, "return-void")
        // License gate — return before service bind.
        LicenseClientCheckFingerprint.method.addInstructions(0, "return-void")
        // Native VM gate — clinit loads libpairipcore.so; invoke bridges ~20 instrumented call sites.
        VMRunnerClinitFingerprint.method.addInstructions(0, "return-void")
        VMRunnerInvokeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """.trimIndent()
        )
        VMRunnerSetContextFingerprint.method.addInstructions(0, "return-void")
    }
}
