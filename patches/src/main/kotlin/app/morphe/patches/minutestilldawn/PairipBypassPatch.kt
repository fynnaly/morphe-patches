package app.morphe.patches.minutestilldawn

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.patches.minutestilldawn.Constants.COMPATIBILITY_20MTD
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.value.ImmutableStringEncodedValue
import com.android.tools.smali.dexlib2.util.FieldUtil

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

// ---- Layer 4: verbose PairIP UI paths that survive the null-return of checkLicense --
//
// checkLicense returning early leaves a bound Play licensing service that still
// reports "no license". Its responses then flow into these two private methods.
// Neutralising the gate at its entry (checkLicense) plus both presentation sinks
// makes every license outcome invisible. Verified on-device 2026-10-17: with all
// three nop'd the game reached main menu with zero dialogs and no System.exit(0).

private object LicenseClientStartPaywallFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "startPaywallActivity",
    returnType = "V",
    parameters = listOf("Landroid/app/PendingIntent;"),
    strings = listOf("paywallintent")
)

private object LicenseClientStartErrorDialogFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
    name = "startErrorDialogActivity",
    returnType = "V",
    strings = listOf("activitytype")
)

// ---- Layer 5: the VM choke point ----------------------------------------------------
//
// Besides StartupLauncher, ~22 more PairIP-virtualised stubs across SDKs(Facebook
// auth, Firebase messaging, WorkManager, GameAnalytics, ...) call
// VMRunner.invoke(String, Object[]) directly, days after launch. Every one of
// them funnels into the single invoke() entry, so no-oping invoke() closes the
// native VM for good. Verified on-device: SIGSEGV in libpairipcore.so gone (was
// deterministic pc 0x5a684 FNV-1a check), JIT /engine still runs at 30 FPS.

private object VMRunnerInvokeFingerprint : Fingerprint(
    definingClass = "Lcom/pairip/VMRunner;",
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    strings = listOf("Executing ")
)

// ---- Layer 6: stubs that must return a value, not null ------------------------------
//
// invoke() returning null is fine for V- and Z-shaped stubs but WorkManager's
// CombineContinuationsWorker chain and GMS's OfflinePingSender NPE on
// check-cast ListenableWorker$Result. Return Result.success() instead.

private object CombineContinuationsWorkerFingerprint : Fingerprint(
    definingClass = "Landroidx/work/impl/workers/CombineContinuationsWorker;",
    name = "doWork",
    // Kotlin string templates: escape `\$` so the real class name survives.
    returnType = "Landroidx/work/ListenableWorker\$Result;"
)

private object OfflinePingSenderFingerprint : Fingerprint(
    definingClass = "Lcom/google/android/gms/ads/internal/offline/buffering/OfflinePingSender;",
    name = "doWork",
    returnType = "Landroidx/work/ListenableWorker\$Result;"
)

// ---- Layer 7: string-pool consumers that NPE on the null holders ---------------------

private object TenjinStartupRunnableFingerprint : Fingerprint(
    definingClass = "Lcom/tenjin/android/utils/TenjinStartup$1;",
    name = "run",
    returnType = "V"
)

// ---- Layer 8: AppLovin duplicate-key assertion --------------------------------------
//
// With all pool strings set to "", the 388 AppLovin settings all register the
// same key "" and sj.a() throws IllegalArgumentException("Setting has already
// been used") in its clinit, crashing at the login screen. Branching over the
// assert keeps duplicate settings overwriting the previous value, the semantics
// a settings registry normally has.

private object AppLovinSettingRegisterFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/impl/sj;",
    name = "a",
    returnType = "Lcom/applovin/impl/sj;",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/Object;"),
    strings = listOf("Setting has already been used: ")
)

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

        // ---- Layer 4 ------------------------------------------------------------------
        // Both presentation sinks nop'd — no paywall, no error dialog, ever.
        LicenseClientStartPaywallFingerprint.method.addInstructions(0, "return-void")
        LicenseClientStartErrorDialogFingerprint.method.addInstructions(0, "return-void")

        // ---- Layer 5 ------------------------------------------------------------------
        // Close the native VM for every virtualised stub at the single entry point.
        // The original body (log, readByteCode, executeVM) becomes dead code.
        VMRunnerInvokeFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """.trimIndent()
        )

        // ---- Layer 6 ------------------------------------------------------------------
        // Stub bodies for WorkManager / GMS workers return Result.success() so their
        // callers never see a null ListenableWorker$Result.
        //
        // Built through the dexlib2 API rather than inline smali: the smali lexer
        // chokes on the `$` in `ListenableWorker$Result` ("Invalid directive").
        val successRef = ImmutableMethodReference(
            "Landroidx/work/ListenableWorker\$Result;",
            "success",
            emptyList(),
            "Landroidx/work/ListenableWorker\$Result;"
        )
        val successResult = listOf(
            BuilderInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, successRef),
            BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            BuilderInstruction11x(Opcode.RETURN_OBJECT, 0)
        )
        CombineContinuationsWorkerFingerprint.method.addInstructions(0, successResult)
        OfflinePingSenderFingerprint.method.addInstructions(0, successResult)

        // ---- Layer 7 ------------------------------------------------------------------
        // Tenjin's startup thread reads a pool holder field (SvuapeuVOPOnAk string
        // pool) and dies with StringBuilder.<init>(null). Skip it entirely — it is
        // only an attribution SDK entry, nothing gameplay depends on.
        TenjinStartupRunnableFingerprint.method.addInstructions(0, "return-void")

        // ---- Layer 8 ------------------------------------------------------------------
        // Replace the duplicate-key throw with a direct put + return. The original
        // instructions after it (StringBuilder, throw IllegalArgumentException) are
        // unreachable dead code — the verifier never inspects them.
        val appLovinMethod = AppLovinSettingRegisterFingerprint.method
        // Index 12 is `if-nez v1, :cond_0` (the duplicate-key branch). NOP it so
        // both new and duplicate keys fall through to the existing put+return at
        // indices 13,14. The throw block at :cond_0 becomes dead code.
        appLovinMethod.replaceInstruction(
            12,
            "nop".toInstruction(appLovinMethod)
        )

        // ---- Layer 9: the string pool itself -----------------------------------------
        //
        // Every string-pool holder class carries static String fields that the native
        // VM was supposed to fill at runtime (it can't — its bytecode is encrypted
        // and keyed to the original signature). Consumers then die on
        // StringBuilder.<init>(null) or String.length() == 0 NPEs, and the SDK init
        // chains that read them stall the game at the loading screen.
        //
        // Fill them with "" so consumers get a valid (if empty) string and fall
        // through to the SDK's own defaults. Setting them to "" rather than the
        // real values is deliberate: the actual values are unrecoverable, and any
        // logic keyed on the string's content (e.g. equals("...")) just evaluates
        // to false and takes the neutral branch.
        //
        // Fields that were already assigned a literal (initialValue != null) are
        // left untouched — only the PairIP-generated pool holders match.
        val staticStringPool = { cls: ClassDef ->
            cls.methods.count() == 0 &&
                cls.fields.count() >= 10 &&
                cls.fields.all { FieldUtil.isStatic(it) && it.type == "Ljava/lang/String;" }
        }
        classDefForEach { cls ->
            if (!staticStringPool(cls)) return@classDefForEach
            mutableClassDefBy(cls).staticFields.forEach { field ->
                field.setInitialValue(
                    MutableStringEncodedValue(ImmutableStringEncodedValue(""))
                )
            }
        }
    }
}
