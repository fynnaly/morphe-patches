package app.morphe.patches.minutestilldawn

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.minutestilldawn.Constants.COMPATIBILITY_20MTD
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * PairIP bypass — prerequisite for every other Minutes Till Dawn patch.
 *
 * 20 Minutes Till Dawn 8.6 / 1093 ships Google's PairIP ("app protection"), the
 * same stack documented at github.com/Solaree/pairipcore:
 *
 *   AndroidManifest android:name="com.pairip.application.Application"
 *       extends com.safedk.android.SafeDKApplication
 *   com/pairip/SignatureCheck.verifyIntegrity(Context)   -> re-sign gate
 *   com/pairip/licensecheck/LicenseClient.checkLicense   -> Play licence gate
 *   com/pairip/VMRunner                                  -> native VM (libpairipcore.so)
 *   com/pairip/StartupLauncher.launch()                  -> runs the startup VM program
 *
 * Three distinct layers, verified on-device 2026-10-15 (TECNO LJ6, Android 16):
 *
 * 1. Signature gate. verifyIntegrity SHA-256(Base64)-hashes
 *    PackageInfo.signatures[0] and throws SignatureTamperedException
 *    ("Apk signature is invalid.") unless it matches expectedSignature or
 *    ALLOWLISTED_SIG. It runs from Application.attachBaseContext before super,
 *    so a re-signed APK dies instantly.
 *
 * 2. License gate. checkLicense("Cannot check license with null context.")
 *    bounces sideloaded installs to Play.
 *
 * 3. Virtualised methods. PairIP rewrites selected methods into its own bytecode:
 *    the DEX body becomes a reflective Method.invoke on a static Method field.
 *    libpairipcore.so decrypts and executes that bytecode, and the key is bound to
 *    the install signature — so on a re-signed APK the VM faults natively
 *    (SIGSEGV in libpairipcore.so, deterministic, pc 0x4e64c) and, even when the
 *    fault is avoided, the holder fields stay null and the call sites NPE.
 *
 *    Verified experimentally: installing the untouched, Google-signed APK works
 *    (ActivityManager "Displayed", zero crashes), while re-signing that exact same
 *    APK without modifying any DEX crashes on launch. The native fault is therefore
 *    caused by re-signing, not by anything this patch writes.
 *
 * Since layer 3 cannot be repaired from DEX, this patch replaces the invoke stubs
 * with real bodies and skips the startup VM program so the fault is never reached:
 *
 *   UnityPlayerActivity  onStart/onStop/onPause/onResume/onDestroy -> delegate to
 *                        UnityPlayer, matching the untampered Unity template
 *                        (smali_classes5 UnityPlayer still exposes public
 *                        onStart/onStop/onPause/onResume/destroy)
 *   FacebookContentProvider.onCreate -> return true (no Facebook integration here)
 *   SafeDKApplication.onCreate       -> call super
 *   StartupLauncher.launch()         -> skip the VM program that faults natively
 *
 * The virtualised call sites are recognised by a field access on the PairIP VM
 * holder, e.g.
 *   sget-object v0, Lcom/google/android/datatransport/runtime/dagger/internal/Uoq/QONHfEt;->UsgJX:Ljava/lang/reflect/Method;
 * The holder class is obfuscated per build, so the filter matches the field type
 * and opcode plus the exact method name instead of a fixed holder name.
 *
 * Remaining caveat: the app still registers the PairIP Application/provider and
 * still runs Play integrity checks, so this is a best-effort client-side bypass for
 * a re-signed build, not a full de-PairIP.
 */

// ---- Layer 1 + 2 -----------------------------------------------------------------

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
    // use a body string instead.
    strings = listOf("Cannot check license with null context.")
)

// ---- Layer 3: skip the startup VM program ----------------------------------------

private object StartupLauncherFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/StartupLauncher;",
    name = "launch",
    returnType = "V",
    // No accessFlags: the patcher compares them exactly, and launch() is declared
    // `public static declared-synchronized`, so listing PUBLIC|STATIC alone never
    // matches. Name + definingClass is unambiguous here.
    //
    // startupProgramName is a *static field* (`= "8jsM3WGcz3HmEwH0"`), not a
    // const-string inside the body, so a `strings` filter cannot match either.
    // Anchor on the field read instead — see vmHolderField() for the same reason.
    filters = listOf(
        fieldAccess(
            name = "startupProgramName",
            type = "Ljava/lang/String;",
            opcode = Opcode.SGET_OBJECT
        )
    )
)

// ---- Layer 3: virtualised call sites the app cannot start without ----------------
//
// `type = "Ljava/lang/reflect/Method;"` with SGET_OBJECT is true only for a class
// that holds a static java.lang.reflect.Method field. The app itself never does
// that — PairIP generated those holders for the methods it virtualised — and the
// combination with the exact method name pins the call site without depending on
// the obfuscated holder class name (which changes per build).

private fun vmHolderField() = fieldAccess(
    type = "Ljava/lang/reflect/Method;",
    opcode = Opcode.SGET_OBJECT
)

private object UnityPlayerActivityOnStartFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/player/UnityPlayerActivity;",
    name = "onStart",
    returnType = "V",
    filters = listOf(vmHolderField())
)

private object UnityPlayerActivityOnStopFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/player/UnityPlayerActivity;",
    name = "onStop",
    returnType = "V",
    filters = listOf(vmHolderField())
)

private object UnityPlayerActivityOnPauseFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/player/UnityPlayerActivity;",
    name = "onPause",
    returnType = "V",
    filters = listOf(vmHolderField())
)

private object UnityPlayerActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/player/UnityPlayerActivity;",
    name = "onResume",
    returnType = "V",
    filters = listOf(vmHolderField())
)

private object UnityPlayerActivityOnDestroyFingerprint : Fingerprint(
    definingClass = "Lcom/unity3d/player/UnityPlayerActivity;",
    name = "onDestroy",
    returnType = "V",
    filters = listOf(vmHolderField())
)

/** Facebook provider installs early and blocks process start. */
private object FacebookContentProviderOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/FacebookContentProvider;",
    name = "onCreate",
    returnType = "Z",
    filters = listOf(vmHolderField())
)

/** SafeDK hook — its virtualised onCreate is the next NPE after the provider. */
private object SafeDKApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/safedk/android/SafeDKApplication;",
    name = "onCreate",
    returnType = "V",
    filters = listOf(vmHolderField())
)

val pairipBypassPatch = bytecodePatch(
    name = "Bypass PairIP signature, license & VM gate",
    description = "Neutralises the PairIP signature and license gates, restores the virtualised lifecycle methods with real Unity delegating bodies, and skips the startup VM program. Required for every Minutes Till Dawn patch."
) {
    compatibleWith(COMPATIBILITY_20MTD)

    execute {
        // Layer 1: signature gate — return before the cert hash comparison.
        SignatureCheckVerifyFingerprint.method.addInstructions(0, "return-void")
        // Layer 2: license gate — return before the Play licence bind.
        LicenseClientCheckFingerprint.method.addInstructions(0, "return-void")

        // Layer 3a: do not run the startup VM program — it faults natively
        // (SIGSEGV in libpairipcore.so) on a re-signed install.
        StartupLauncherFingerprint.method.addInstructions(0, "return-void")

        // Layer 3b: restore the lifecycle methods PairIP virtualised. Bodies mirror
        // the untampered Unity template: super first, then UnityPlayer.
        UnityPlayerActivityOnStartFingerprint.method.addInstructions(
            0,
            """
                invoke-super {p0}, Landroid/app/Activity;->onStart()V
                iget-object v0, p0, Lcom/unity3d/player/UnityPlayerActivity;->mUnityPlayer:Lcom/unity3d/player/UnityPlayer;
                invoke-virtual {v0}, Lcom/unity3d/player/UnityPlayer;->onStart()V
                return-void
            """.trimIndent()
        )
        UnityPlayerActivityOnStopFingerprint.method.addInstructions(
            0,
            """
                invoke-super {p0}, Landroid/app/Activity;->onStop()V
                iget-object v0, p0, Lcom/unity3d/player/UnityPlayerActivity;->mUnityPlayer:Lcom/unity3d/player/UnityPlayer;
                invoke-virtual {v0}, Lcom/unity3d/player/UnityPlayer;->onStop()V
                return-void
            """.trimIndent()
        )
        UnityPlayerActivityOnPauseFingerprint.method.addInstructions(
            0,
            """
                invoke-super {p0}, Landroid/app/Activity;->onPause()V
                iget-object v0, p0, Lcom/unity3d/player/UnityPlayerActivity;->mUnityPlayer:Lcom/unity3d/player/UnityPlayer;
                invoke-virtual {v0}, Lcom/unity3d/player/UnityPlayer;->onPause()V
                return-void
            """.trimIndent()
        )
        UnityPlayerActivityOnResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-super {p0}, Landroid/app/Activity;->onResume()V
                iget-object v0, p0, Lcom/unity3d/player/UnityPlayerActivity;->mUnityPlayer:Lcom/unity3d/player/UnityPlayer;
                invoke-virtual {v0}, Lcom/unity3d/player/UnityPlayer;->onResume()V
                return-void
            """.trimIndent()
        )
        // onDestroy: tear the player down first, then let the superclass run.
        UnityPlayerActivityOnDestroyFingerprint.method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/unity3d/player/UnityPlayerActivity;->mUnityPlayer:Lcom/unity3d/player/UnityPlayer;
                invoke-virtual {v0}, Lcom/unity3d/player/UnityPlayer;->destroy()V
                invoke-super {p0}, Landroid/app/Activity;->onDestroy()V
                return-void
            """.trimIndent()
        )

        // Layer 3c: the Facebook provider must report success (no Facebook
        // integration in this title) instead of invoking its dead VM reference.
        FacebookContentProviderOnCreateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )

        // Layer 3d: SafeDK's onCreate wraps the real Application.onCreate; calling
        // super restores tracing/attribution without the dead VM reference.
        SafeDKApplicationOnCreateFingerprint.method.addInstructions(
            0,
            """
                invoke-super {p0}, Landroid/app/Application;->onCreate()V
                return-void
            """.trimIndent()
        )
    }
}
