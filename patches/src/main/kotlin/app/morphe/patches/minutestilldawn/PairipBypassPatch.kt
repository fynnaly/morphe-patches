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
 *
 * verifyIntegrity computes SHA-256(Base64) of PackageInfo.signatures[0] and
 * throws SignatureTamperedException("Apk signature is invalid.") unless it
 * equals expectedSignature or ALLOWLISTED_SIG. Any re-signed APK dies in
 * Application.attachBaseContext before super — so every downstream patch
 * must depend on this bypass.
 *
 * Guard strings asserted: "Apk signature is invalid." /
 * "Cannot check license with null context.".
 *
 * Deliberately does NOT touch VMRunner (clinit / invoke / setContext):
 * neutralizing invoke to return null kills the PairIP VM side effects that
 * the app depends on — QONHfEt Method fields stay null and
 * FacebookContentProvider.onCreate dies with NPE on Method.invoke
 * (logcat 2026-10-15, 8 identical FC, zero SignatureTampered). The native
 * libpairipcore.so ships in the ABI split and loads normally; no SIGSEGV
 * observed on this title.
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

val pairipBypassPatch = bytecodePatch(
    name = "Bypass PairIP signature & license check",
    description = "Neutralizes PairIP tamper gates (SignatureCheck, LicenseClient) so re-signed APKs no longer crash in Application.attachBaseContext. Required for every Minutes Till Dawn patch."
) {
    compatibleWith(COMPATIBILITY_20MTD)

    execute {
        // Signature gate — return before cert hash comparison.
        // Early return keeps the original body as dead but verifiable code.
        SignatureCheckVerifyFingerprint.method.addInstructions(0, "return-void")
        // License gate — return before service bind.
        LicenseClientCheckFingerprint.method.addInstructions(0, "return-void")
    }
}
