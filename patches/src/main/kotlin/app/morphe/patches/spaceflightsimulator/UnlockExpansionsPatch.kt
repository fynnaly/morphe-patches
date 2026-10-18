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
 *   - Covers (all four forced to `return true`): part ownership and part allowance in the builder,
 *     `EDRDUTMFILKPRGQP.HGOOLGOEKMK(int)` = the store's per-expansion ownership predicate, and
 *     `SFS.WorldBase.JRMHWFEIUSAIOARX.get_AllowsCheats` = the world flag that gates the cheats menu.
 *   - Does NOT cover: the store's Play Billing receipts (prices still come from BillingClient and a
 *     real purchase still uses the real flow), expansions behind other systems — infinite build area
 *     (`GZRGGLAJZJQJYQWD.GetOwnedGridSize`, returns Vector2), planets
 *     (`IDPXFOVTORFVYNOM.OpenSelectSolarSystemMenu`) and skins (`JDTIIKLUYWPXKAKX.DrawSkinButtons`) —
 *     and no receipt is faked, so nothing is granted server-side.
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

/** `mov w0, #1` - constant `true` from a forced call site (same size as the `bl` it replaces) */
private const val ARM64_MOV_W0_1 = "20008052"

/** `mov r0, #1` - ARM mode, same size as the `bl` it replaces */
private const val ARM32_MOV_R0_1 = "0100a0e3"

private data class WordWrite(
    val libPath: String,
    val fileOffset: Int,
    val expectedWord: String,
    val replacement: String,
    val symbol: String,
)

// File offset = virtual address minus the LOAD segment delta (0x4000 for this .so); the delta was
// read from `llvm-readelf --program-headers` and each word was confirmed with `llvm-objdump -d`.
private val WORD_WRITES = listOf(
    WordWrite(ARM64_LIB, 0x17AF4AC, "fe0f1ef8", ARM64_RETURN_TRUE, "VSBEDHEHBUOHCHWD.get_IsOwned"),
    WordWrite(ARM64_LIB, 0x16EEA34, "fe5fbda9", ARM64_RETURN_TRUE, "GQEZALXKUKZOVQGD.get_IsPremium"),
    // store/pricing ownership predicate: bool HGOOLGOEKMK(int expansionIndex)
    WordWrite(ARM64_LIB, 0x1770C14, "fe57bea9", ARM64_RETURN_TRUE, "EDRDUTMFILKPRGQP.HGOOLGOEKMK"),
    // world flag that gates the cheats menu
    WordWrite(ARM64_LIB, 0x183B234, "fe0f1ff8", ARM64_RETURN_TRUE, "JRMHWFEIUSAIOARX.get_AllowsCheats"),
    WordWrite(ARMV7_LIB, 0xCF13B4, "30482de9", ARM32_RETURN_TRUE, "VSBEDHEHBUOHCHWD.get_IsOwned"),
    WordWrite(ARMV7_LIB, 0xBFFFFC, "f0482de9", ARM32_RETURN_TRUE, "GQEZALXKUKZOVQGD.get_IsPremium"),
    WordWrite(ARMV7_LIB, 0xCA38E0, "f0482de9", ARM32_RETURN_TRUE, "EDRDUTMFILKPRGQP.HGOOLGOEKMK"),
    WordWrite(ARMV7_LIB, 0xDA25D4, "10402de9", ARM32_RETURN_TRUE, "JRMHWFEIUSAIOARX.get_AllowsCheats"),
)

private fun ByteArray.hexWord(offset: Int): String =
    (0 until 4).joinToString("") { "%02x".format(this[offset + it]) }

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()

// Each row is one call to `Obs<bool>.ALEDKGMCDCB` in store/UI code, replaced with a constant
// `true`. `bl` and `mov` are both 4 bytes, so nothing needs relocating.
private val CALL_SITE_FORCES = listOf(
    // arm64-v8a - 22 forced call sites in the store/UI code
    WordWrite(ARM64_LIB, 0x164BED0, "20063694", ARM64_MOV_W0_1, "JDTIIKLUYWPXKAKX.DrawSkinButtons"),
    WordWrite(ARM64_LIB, 0x164BF30, "08063694", ARM64_MOV_W0_1, "JDTIIKLUYWPXKAKX.DrawSkinButtons"),
    WordWrite(ARM64_LIB, 0x17F59C4, "635f2f94", ARM64_MOV_W0_1, "IDPXFOVTORFVYNOM.OpenSelectSolarSystemMenu"),
    WordWrite(ARM64_LIB, 0x17F59F0, "585f2f94", ARM64_MOV_W0_1, "IDPXFOVTORFVYNOM.OpenSelectSolarSystemMenu"),
    WordWrite(ARM64_LIB, 0x18060C4, "a31d2f94", ARM64_MOV_W0_1, "JQQECHFPZWKMUEHT.GOBICDMOGLG"),
    WordWrite(ARM64_LIB, 0x177ED5C, "7d3a3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.GAJMENIMBFK"),
    WordWrite(ARM64_LIB, 0x177EF08, "123a3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.HLEHCCHHCAK"),
    WordWrite(ARM64_LIB, 0x177F1C0, "64393194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARM64_LIB, 0x177F204, "53393194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARM64_LIB, 0x177F25C, "3d393194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARM64_LIB, 0x177F28C, "31393194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARM64_LIB, 0x177F54C, "81383194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.ACJAOPPNLBA"),
    WordWrite(ARM64_LIB, 0x177F600, "54383194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.LEHGIEKDDNM"),
    WordWrite(ARM64_LIB, 0x177F6A8, "2a383194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.GJJIEIEHLGE"),
    WordWrite(ARM64_LIB, 0x17819A0, "6c2f3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.JEDDJKJKIMF"),
    WordWrite(ARM64_LIB, 0x1781CA8, "aa2e3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.FPEAGDGPMNK"),
    WordWrite(ARM64_LIB, 0x1781E6C, "392e3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.CPMCLBFFOBE"),
    WordWrite(ARM64_LIB, 0x1781F2C, "092e3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.OMAENGCAILF"),
    WordWrite(ARM64_LIB, 0x1781FEC, "d92d3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.NGHHCMGONBF"),
    WordWrite(ARM64_LIB, 0x1782030, "c82d3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.NGHHCMGONBF"),
    WordWrite(ARM64_LIB, 0x1782660, "3c2c3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.<>c.DBKCEONJLAH"),
    WordWrite(ARM64_LIB, 0x1782A08, "522b3194", ARM64_MOV_W0_1, "PMGOLGVYKUNGOOBD.<>c.FCANIEPODCP"),

    // armeabi-v7a - 22 forced call sites in the store/UI code
    WordWrite(ARMV7_LIB, 0xB32F24, "f7bb3ceb", ARM32_MOV_R0_1, "JDTIIKLUYWPXKAKX.DrawSkinButtons"),
    WordWrite(ARMV7_LIB, 0xB32FAC, "d5bb3ceb", ARM32_MOV_R0_1, "JDTIIKLUYWPXKAKX.DrawSkinButtons"),
    WordWrite(ARMV7_LIB, 0xD4ACFC, "815c34eb", ARM32_MOV_R0_1, "IDPXFOVTORFVYNOM.OpenSelectSolarSystemMenu"),
    WordWrite(ARMV7_LIB, 0xD4AD3C, "715c34eb", ARM32_MOV_R0_1, "IDPXFOVTORFVYNOM.OpenSelectSolarSystemMenu"),
    WordWrite(ARMV7_LIB, 0xD5F940, "700934eb", ARM32_MOV_R0_1, "JQQECHFPZWKMUEHT.GOBICDMOGLG"),
    WordWrite(ARMV7_LIB, 0xCB62D4, "0baf36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.GAJMENIMBFK"),
    WordWrite(ARMV7_LIB, 0xCB6504, "7fae36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.HLEHCCHHCAK"),
    WordWrite(ARMV7_LIB, 0xCB687C, "a1ad36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARMV7_LIB, 0xCB68D4, "8bad36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARMV7_LIB, 0xCB6948, "6ead36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARMV7_LIB, 0xCB6984, "5fad36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.BPCBBDLOFHH"),
    WordWrite(ARMV7_LIB, 0xCB6D20, "78ac36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.ACJAOPPNLBA"),
    WordWrite(ARMV7_LIB, 0xCB6E00, "40ac36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.LEHGIEKDDNM"),
    WordWrite(ARMV7_LIB, 0xCB6EC8, "0eac36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.GJJIEIEHLGE"),
    WordWrite(ARMV7_LIB, 0xCB98D4, "8ba136eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.JEDDJKJKIMF"),
    WordWrite(ARMV7_LIB, 0xCB9C98, "9aa036eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.FPEAGDGPMNK"),
    WordWrite(ARMV7_LIB, 0xCB9ED4, "0ba036eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.CPMCLBFFOBE"),
    WordWrite(ARMV7_LIB, 0xCB9FB8, "d29f36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.OMAENGCAILF"),
    WordWrite(ARMV7_LIB, 0xCBA0A0, "989f36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.NGHHCMGONBF"),
    WordWrite(ARMV7_LIB, 0xCBA104, "7f9f36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.NGHHCMGONBF"),
    WordWrite(ARMV7_LIB, 0xCBA8D0, "8c9d36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.<>c.DBKCEONJLAH"),
    WordWrite(ARMV7_LIB, 0xCBAD10, "7c9c36eb", ARM32_MOV_R0_1, "PMGOLGVYKUNGOOBD.<>c.FCANIEPODCP"),
)

val unlockExpansionsPatch = resourcePatch(
    name = "Unlock expansions",
    description = "Forces four native il2cpp gates in libil2cpp.so to return true (arm64-v8a and armeabi-v7a): part ownership, part allowance, the store's ownership predicate and the cheats flag. Purchase receipts are not faked, so Play Billing results are unaffected."
) {
    compatibleWith(Constants.COMPATIBILITY_SFS)

    execute {
        (WORD_WRITES + CALL_SITE_FORCES).groupBy { it.libPath }.forEach { (libPath, flips) ->
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
