package app.morphe.patches.minutestilldawn

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.minutestilldawn.Constants.COMPATIBILITY_20MTD
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Skip rewarded ads — grants the reward without ever playing the video.
 *
 * 20 Minutes Till Dawn is a Sauce SDK (Flanne) Unity title. Every
 * "watch an ad to unlock X" gate — heroes, guns, daily gift, trial coupons,
 * rebirth coupons, ad-free coupons — goes through the AppLovin MAX Unity bridge:
 *
 *   C# AdsManager -> MaxUnityPlugin.showRewardedAd(adUnitId, placement, customData)
 *                 -> MaxUnityAdManager.showRewardedAd  -> MaxRewardedAd.showAd (video)
 *                 -> onUserRewarded / onAdHidden       -> forwardUnityEvent(JSON)
 *                 -> UnitySendMessage back to C#       -> reward granted
 *
 * The C# side only ever sees the forwarded JSON events (name + optional
 * adUnitId/reward fields), never the video — so emitting the events directly
 * and never calling showAd makes the game grant the reward immediately.
 *
 * The hook lives on MaxUnityPlugin, the static JNI wrapper C# calls through:
 * that is also where the fresh-install failure comes from — its
 * `initializeSdkCalled` guard returns false before the pool-holder fix has any
 * say, and the adUnitId passed by the config pool is null at that point, so
 * both isRewardedAdReady and showRewardedAd must bypass the guard and the
 * trim() NPE, not just the manager.
 *
 * Registers: p0 (adUnitId) is read several times before the first overwrite;
 * p1/p2 (placement/customData) are unused, p3 becomes scratch. forwardUnityEvent
 * is a private static on MaxUnityAdManager — legal to invoke from anywhere after
 * the patch promotes it to public static below.
 */

private object PluginShowRewardedAdFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/unity/MaxUnityPlugin;",
    name = "showRewardedAd",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Ljava/lang/String;")
)

private object PluginIsRewardedAdReadyFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/unity/MaxUnityPlugin;",
    name = "isRewardedAdReady",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;")
)

private object PluginIsRewardedInterstitialAdReadyFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/unity/MaxUnityPlugin;",
    name = "isRewardedInterstitialAdReady",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;")
)

// The C# OnRewardedAdHiddenEvent handler reloads the next ad before invoking
// the game's completion callback. With the adUnitId coming from the string
// pool as "", LoadRewardedAd throws IllegalArgumentException and the callback
// is never reached — so the unlock is silently dropped. No real ad is ever
// shown anymore, so the reload is dead weight: neutralize it.
private object PluginLoadRewardedAdFingerprint : Fingerprint(
    definingClass = "Lcom/applovin/mediation/unity/MaxUnityPlugin;",
    name = "loadRewardedAd",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;")
)

// v0 = JSONObject, p1/p2 = scratch, p0 = adUnitId (preserved)
private const val GRANT_REWARD = """
    new-instance v0, Lorg/json/JSONObject;
    invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V

    const-string p1, "name"
    const-string p2, "OnRewardedAdDisplayedEvent"
    invoke-static {v0, p1, p2}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "adUnitId"
    invoke-static {v0, p1, p0}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {v0}, Lcom/applovin/mediation/unity/MaxUnityAdManager;->forwardUnityEvent(Lorg/json/JSONObject;)V

    new-instance v0, Lorg/json/JSONObject;
    invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V

    const-string p1, "name"
    const-string p2, "OnRewardedAdReceivedRewardEvent"
    invoke-static {v0, p1, p2}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "adUnitId"
    invoke-static {v0, p1, p0}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "rewardLabel"
    const-string p2, ""
    invoke-static {v0, p1, p2}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "rewardAmount"
    const-string p2, "1"
    invoke-static {v0, p1, p2}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {v0}, Lcom/applovin/mediation/unity/MaxUnityAdManager;->forwardUnityEvent(Lorg/json/JSONObject;)V

    new-instance v0, Lorg/json/JSONObject;
    invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V

    const-string p1, "name"
    const-string p2, "OnRewardedAdHiddenEvent"
    invoke-static {v0, p1, p2}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V

    const-string p1, "adUnitId"
    invoke-static {v0, p1, p0}, Lcom/applovin/impl/sdk/utils/JsonUtils;->putString(Lorg/json/JSONObject;Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {v0}, Lcom/applovin/mediation/unity/MaxUnityAdManager;->forwardUnityEvent(Lorg/json/JSONObject;)V

    return-void
"""

val skipRewardedAdsPatch = bytecodePatch(
    name = "Skip rewarded ads",
    description = "Grants the rewarded-ad reward without playing the video, so every \"watch an ad to unlock\" gate (heroes, guns, coupons, daily gift) resolves instantly."
) {
    compatibleWith(COMPATIBILITY_20MTD)

    dependsOn(pairipBypassPatch)

    execute {
        // forwardUnityEvent is private static on the manager; the injected
        // block in MaxUnityPlugin invokes it cross-class, so it must be public.
        mutableClassDefBy("Lcom/applovin/mediation/unity/MaxUnityAdManager;")
            .methods.first { it.name == "forwardUnityEvent" }
            .setAccessFlags(AccessFlags.PUBLIC.value or AccessFlags.STATIC.value)

        PluginShowRewardedAdFingerprint.method.addInstructions(0, GRANT_REWARD)
        PluginIsRewardedAdReadyFingerprint.method.addInstructions(0, "const/4 p0, 0x1\nreturn p0")
        PluginIsRewardedInterstitialAdReadyFingerprint.method.addInstructions(0, "const/4 p0, 0x1\nreturn p0")
        PluginLoadRewardedAdFingerprint.method.addInstructions(0, "return-void")
    }
}
