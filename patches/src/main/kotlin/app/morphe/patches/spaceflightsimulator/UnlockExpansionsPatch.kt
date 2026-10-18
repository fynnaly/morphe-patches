package app.morphe.patches.spaceflightsimulator

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.io.File

/**
 * Unlocks the expansion part packs of Spaceflight Simulator.
 *
 * Why a `resourcePatch` and not a `bytecodePatch`: the gate is C# compiled to native code, so it
 * only exists in `global-metadata.dat` and `libil2cpp.so` (zero hits in DEX). Morphe cannot rewrite
 * the IL, but `ResourcePatchContext.get(path)` reaches raw APK entries, so the gate is neutralized by
 * rewriting instruction words in `libil2cpp.so` for every ABI in the APK.
 *
 * The entitlement model (derived from `global-metadata.dat` + `script.json` + the disassembly):
 *   - `SFS.Parts.Modules.VSBEDHEHBUOHCHWD.get_IsOwned()` reads `this.pack` (`GNDLADOKFKN`:
 *     `BigParts`=0, `Redstone_Atlas`=1, `Full_Version`=3) and, for the paid packs, reads bool fields
 *     (`+0x10` BigParts, `+0x50` Redstone_Atlas) off the purchase-manager singleton
 *     (`LMEMKLJJJOC.KHHFOAEAONN`, 0x1737604).
 *   - `SFS.Parts.Modules.GQEZALXKUKZOVQGD.get_IsPremium()` is the size/allowance check:
 *     `Enumerable.All(allowed, allowed => ...)` (its obfuscated name is `IBKAGMEOFCO`).
 *   Both are forced to `return true`, which removes the ownership/allowance gate for every part.
 *
 * Scope — read before promising anything:
 *   - Covers: the part packs (Parts Expansion / Big Parts, Redstone Atlas) and any module that
 *     consults these two getters.
 *   - Does NOT cover: the store's Play Billing receipts (the Expansions screen keeps showing prices —
 *     that layer is `Unity.Purchasing`/BillingClient, not these getters), nor expansions gated by
 *     other systems (skins, infinite build area, planets) which read their own state.
 *   - Unverifiable in effect without playing: the byte patch is confirmed present in the installed
 *     artifact and the game boots and runs with it, but the in-game unlock was never observed
 *     (device-side verification kept failing on unrelated issues: locked screen, ROM kills at low
 *     battery, taps desynced from the UI). Treat the effect as unproven, not as verified.
 *
 * Offsets are build specific (versionCode 897). Every write asserts the original instruction word
 * first, so an unsupported build fails loudly instead of corrupting the library.
 */
private const val ARM64_LIB = "lib/arm64-v8a/libil2cpp.so"
private const val ARMV7_LIB = "lib/armeabi-v7a/libil2cpp.so"

/** `mov w0, #1` + `ret` */
private const val ARM64_RETURN_TRUE = "20008052c0035fd6"

/** `mov r0, #1` + `bx lr` */
private const val ARM32_RETURN_TRUE = "0100a0e31eff2fe1"

private data class GetterFlip(
    val libPath: String,
    val fileOffset: Int,
    val expectedWord: String,
    val replacement: String,
    val symbol: String,
)

// File offset = virtual address minus the LOAD segment delta (0x4000 for this .so); the delta was
// read from `llvm-readelf --program-headers` and each word was confirmed with `llvm-objdump -d`.
private val GETTER_FLIPS = listOf(
    GetterFlip(ARM64_LIB, 0x17AF4AC, "fe0f1ef8", ARM64_RETURN_TRUE, "VSBEDHEHBUOHCHWD.get_IsOwned"),
    GetterFlip(ARM64_LIB, 0x16EEA34, "fe5fbda9", ARM64_RETURN_TRUE, "GQEZALXKUKZOVQGD.get_IsPremium"),
    // store/pricing ownership predicate: bool HGOOLGOEKMK(int expansionIndex)
    GetterFlip(ARM64_LIB, 0x1770C14, "fe57bea9", ARM64_RETURN_TRUE, "EDRDUTMFILKPRGQP.HGOOLGOEKMK"),
    // world flag that gates the cheats menu
    GetterFlip(ARM64_LIB, 0x183B234, "fe0f1ff8", ARM64_RETURN_TRUE, "JRMHWFEIUSAIOARX.get_AllowsCheats"),
    GetterFlip(ARMV7_LIB, 0xCF13B4, "30482de9", ARM32_RETURN_TRUE, "VSBEDHEHBUOHCHWD.get_IsOwned"),
    GetterFlip(ARMV7_LIB, 0xBFFFFC, "f0482de9", ARM32_RETURN_TRUE, "GQEZALXKUKZOVQGD.get_IsPremium"),
    GetterFlip(ARMV7_LIB, 0xCA38E0, "f0482de9", ARM32_RETURN_TRUE, "EDRDUTMFILKPRGQP.HGOOLGOEKMK"),
    GetterFlip(ARMV7_LIB, 0xDA25D4, "10402de9", ARM32_RETURN_TRUE, "JRMHWFEIUSAIOARX.get_AllowsCheats"),
)

private fun ByteArray.hexWord(offset: Int): String =
    (0 until 4).joinToString("") { "%02x".format(this[offset + it]) }

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

val unlockExpansionsPatch = resourcePatch(
    name = "Unlock expansions",
    description = "Forces the il2cpp part-ownership and allowance getters in libil2cpp.so to return true (arm64-v8a and armeabi-v7a). Covers the part packs; the store's purchase receipts are unaffected."
) {
    compatibleWith(Constants.COMPATIBILITY_SFS)

    execute {
        GETTER_FLIPS.groupBy { it.libPath }.forEach { (libPath, flips) ->
            val lib: File = get(libPath)
            val bytes = lib.readBytes()

            flips.forEach { flip ->
                val found = bytes.hexWord(flip.fileOffset)
                if (!found.equals(flip.expectedWord, ignoreCase = true)) {
                    throw PatchException(
                        "$libPath @0x${flip.fileOffset.toString(16)} (${flip.symbol}): " +
                            "expected ${flip.expectedWord}, found $found — unsupported build"
                    )
                }

                flip.replacement.hexToBytes().forEachIndexed { index, byte ->
                    bytes[flip.fileOffset + index] = byte
                }
            }

            lib.writeBytes(bytes)
        }
    }
}
