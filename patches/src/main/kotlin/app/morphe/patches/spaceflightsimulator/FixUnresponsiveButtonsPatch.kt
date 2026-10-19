package app.morphe.patches.spaceflightsimulator

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import java.io.File

/**
 * Fixes buttons that do not react in Spaceflight Simulator.
 *
 * Symptom: the game shows everything as owned, but pressing a button (for example "View Details")
 * does nothing. The cause is not the store logic: the button's onClick handler plays a click sound
 * first, and the audio subsystem throws a NullReferenceException while doing so:
 *
 *     SFS.Input.UVCLLMRTIBCXEUFP.EAKBAKOINNA   (input event)
 *     SFS.UI.XXWNLMTGUDEVDYSL.ONMNGEFMKHD     (button onClick)
 *     SFS.Audio.IHSFEEHYCORNSWBL.PlaySound_2D  (click sound)  -> NullReferenceException
 *
 * The exception unwinds the handler before the rest of it runs, so the action never happens. The
 * same helper (`SFS.Audio.PSFOCCDIGMIZKCMN.HFLHLHIHOEG`) is also called from the audio manager's
 * per-frame Update, which turns the broken state into a storm of ~60 exceptions per second and
 * leaves the whole UI unresponsive.
 *
 * This patch replaces the three audio entry points with `ret` / `bx lr`, so no exception can be
 * raised from them. The trade-off is deliberate: sound effects stop working, but every button
 * reacts again. Offsets are build specific (versionCode 897) and every write asserts the original
 * instruction bytes first.
 */
private const val ARM64_LIB = "lib/arm64-v8a/libil2cpp.so"
private const val ARMV7_LIB = "lib/armeabi-v7a/libil2cpp.so"

/** `ret` + `nop` */
private const val ARM64_NOOP = "c0035fd61f2003d5"

/** `bx lr` + `nop` */
private const val ARM32_NOOP = "1eff2fe10000a0e1"

private data class Noop(
    val libPath: String,
    val fileOffset: Int,
    val expectedWord: String,
    val symbol: String,
)

private val NOOP_METHODS = listOf(
    // SFS.Audio.IHSFEEHYCORNSWBL.PlaySound_2D
    Noop(ARM64_LIB, 0x17B0270, "e80f19fcfd7b01a9", "SFS.Audio.IHSFEEHYCORNSWBL.PlaySound_2D"),
    Noop(ARMV7_LIB, 0xCF24C4, "f04f2de904d04de2", "SFS.Audio.IHSFEEHYCORNSWBL.PlaySound_2D"),
    // SFS.Audio.PSFOCCDIGMIZKCMN.Update
    Noop(ARM64_LIB, 0x1801078, "e923bd6dfe0b00f9", "SFS.Audio.PSFOCCDIGMIZKCMN.Update"),
    Noop(ARMV7_LIB, 0xD590DC, "f0482de9048b2ded", "SFS.Audio.PSFOCCDIGMIZKCMN.Update"),
    // SFS.Audio.PSFOCCDIGMIZKCMN.StartPlaying
    Noop(ARM64_LIB, 0x1800F44, "e80f1efcfe4f01a9", "SFS.Audio.PSFOCCDIGMIZKCMN.StartPlaying"),
    Noop(ARMV7_LIB, 0xD58F30, "30482de9028b2ded", "SFS.Audio.PSFOCCDIGMIZKCMN.StartPlaying"),
)

val fixUnresponsiveButtonsPatch = resourcePatch(
    name = "Fix unresponsive buttons",
    description = "Stops the broken audio subsystem from throwing NullReferenceException, which aborted every button's click handler and left the UI unresponsive. Sound effects stop working, buttons work again."
) {
    compatibleWith(Constants.COMPATIBILITY_SFS)

    execute {
        NOOP_METHODS.groupBy { it.libPath }.forEach { (libPath, noops) ->
            val lib: File = get(libPath)
            val bytes = lib.readBytes()

            noops.forEach { noop ->
                val expected = noop.expectedWord.hexToBytes()
                val found = bytes.copyOfRange(noop.fileOffset, noop.fileOffset + expected.size)
                if (!found.contentEquals(expected)) {
                    throw PatchException(
                        "$libPath @0x${noop.fileOffset.toString(16)} (${noop.symbol}): " +
                            "expected ${noop.expectedWord}, found ${found.toHexString()} - unsupported build"
                    )
                }

                val replacement = if (libPath == ARM64_LIB) ARM64_NOOP else ARM32_NOOP
                replacement.hexToBytes().forEachIndexed { index, byte ->
                    bytes[noop.fileOffset + index] = byte
                }
            }

            lib.writeBytes(bytes)
        }
    }
}

private fun String.hexToBytes(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
