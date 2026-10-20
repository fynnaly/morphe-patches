package app.morphe.patches.spaceflightsimulator

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.io.File

/**
 * Makes the Spaceflight Simulator store show the expansions and bundles as owned.
 *
 * Why a `resourcePatch` and not a `bytecodePatch`: the gate is C# compiled to native code, so it
 * only exists in `global-metadata.dat` and `libil2cpp.so` (zero hits in DEX). Morphe cannot rewrite
 * the IL, but `ResourcePatchContext.get(path)` reaches raw APK entries, so the gate is neutralized
 * by rewriting instruction words in `libil2cpp.so` for every ABI in the APK.
 *
 * What is patched, and why only this:
 *
 *   The store renders a card in `SFS.UI.JQQECHFPZWKMUEHT.GOBICDMOGLG` (native range
 *   0x1809E44..0x180A688). The card decides "owned or price" with one call to the shared
 *   `Obs<bool>` getter:
 *
 *     180a0c4: bl  0x23d1750           ; the shared Obs<bool> ownership getter
 *     180a0c8: tbz w0, #0, 0x180a154   ; false -> the price path
 *     180a0cc: ...                     ; true  -> the owned path (get_Owned)
 *
 *   Forcing that single call site to return true makes the card read "(Owned)" with no price and
 *   no Buy button. It is the only call site of that getter inside the store class, so nothing else
 *   in the game changes.
 *
 * What is deliberately NOT patched, and why (all measured on device, one variable per build):
 *
 *   - The per-SKU ownership predicate `LMEMKLJJJOC.<>c.ECNHDFDEIGP` and `get_IsOwned` forced to
 *     true: that also hides the price, but it breaks the game's own state. The audio subsystem
 *     then dereferences a null (buttons stop responding) and the ship builder starts throwing
 *     ~120 exceptions per second.
 *   - `get_IsPremium` forced to true: the game takes the premium editor path, whose scene objects
 *     are not in the free APK, so `SFS.Career.IIDKWSHOVBLCXTEE.EnterBuild` throws and the
 *     "Build New Rocket" button only animates.
 *   - `Bool_Local(true)` in `NFPAPMNDEML..ctor` (the ownership *state*): same story, the builder
 *     degrades about a minute after the builder is opened.
 *
 * The premium content itself is simply not part of the free APK, so the parts of the expansion
 * packs cannot be unlocked by flipping a flag - only the store display can be made to say
 * "(Owned)".
 */
private const val ARM64_LIB = "lib/arm64-v8a/libil2cpp.so"
private const val ARMV7_LIB = "lib/armeabi-v7a/libil2cpp.so"

/** `mov w0, #1` + `nop` */
private const val ARM64_MOV_W0_1 = "20008052"

/** `mov r0, #1` + `nop` */
private const val ARM32_MOV_R0_1 = "0100a0e3"

private data class WordWrite(
    val libPath: String,
    val fileOffset: Int,
    val expectedWord: String,
    val replacement: String,
    val symbol: String,
)

private val CALL_SITE_FORCES = listOf<WordWrite>(
    // arm64-v8a - the store's owned-or-price decision
    WordWrite(ARM64_LIB, 0x18060C4, "a31d2f94", ARM64_MOV_W0_1, "JQQECHFPZWKMUEHT.GOBICDMOGLG"),
    // armeabi-v7a - the store's owned-or-price decision
    WordWrite(ARMV7_LIB, 0xD5F940, "700934eb", ARM32_MOV_R0_1, "JQQECHFPZWKMUEHT.GOBICDMOGLG"),
)

val unlockExpansionsPatch = resourcePatch(
    name = "Unlock expansions",
    description = "Makes the store show the expansion packs and bundles as owned, without touching the game's ownership state: the card's own owned-or-price decision in libil2cpp.so is forced to the owned branch (arm64-v8a and armeabi-v7a). No receipt is faked, so Play Billing itself is untouched."
) {
    compatibleWith(Constants.COMPATIBILITY_SFS)

    execute {
        CALL_SITE_FORCES.groupBy { it.libPath }.forEach { (libPath, writes) ->
            val lib: File = get(libPath)
            val bytes = lib.readBytes()

            writes.forEach { write ->
                val expected = write.expectedWord.hexToBytes()
                val found = bytes.copyOfRange(write.fileOffset, write.fileOffset + expected.size)
                if (!found.contentEquals(expected)) {
                    throw PatchException(
                        "$libPath @0x${write.fileOffset.toString(16)} (${write.symbol}): " +
                            "expected ${write.expectedWord}, found ${found.toHexString()} - unsupported build"
                    )
                }

                write.replacement.hexToBytes().forEachIndexed { index, byte ->
                    bytes[write.fileOffset + index] = byte
                }
            }

            lib.writeBytes(bytes)
        }
    }
}

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
