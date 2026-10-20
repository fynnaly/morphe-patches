package app.morphe.patches.minutestilldawn

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.minutestilldawn.Constants.COMPATIBILITY_20MTD
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

/**
 * Repairs the Room column names that PairIP moved into the encrypted string pool.
 *
 * PairIP rewrites string literals into reads of static String fields that the native
 * VM fills at runtime. The PairIP bypass cannot run that VM (its bytecode is keyed to
 * the original signature), so the pool holders are blanked to "" instead. That is
 * fine for the game's own SDK strings, but Room's generated DAO also got one of its
 * column names moved into the pool — and a blank column name is fatal:
 *
 *   java.lang.IllegalArgumentException: column '' does not exist. Available columns: ...
 *     at androidx.room.util.CursorUtil.getColumnIndexOrThrow(CursorUtil.kt:99)
 *     at androidx.work.impl.model.WorkSpecDao_Impl.getAllEligibleWorkSpecsForScheduling
 *     at androidx.work.impl.Schedulers.schedule(Schedulers.java:78)
 *     at androidx.work.impl.WorkManagerImpl.rescheduleEligibleWork(WorkManagerImpl.java:701)
 *     at androidx.work.impl.utils.ForceStopRunnable.forceStopRunnable(ForceStopRunnable.java:223)
 *
 * The exception is raised on WorkManager's own worker thread, so the whole game
 * process dies with it. That is the "force close" 20 Minutes Till Dawn shows after
 * the app has been killed once (the ROM kills background apps; the next launch then
 * runs ForceStopRunnable and hits the broken query).
 *
 * The fix restores the literal where the DAO reads the column name. The instruction
 * that reads the pool field is a `sget-object` (format 21c) and the replacement is a
 * `const-string` (also format 21c), so the method keeps its exact size and no offsets
 * move.
 *
 * The column is `input_merger_class_name` — a stable WorkManager column, and the one
 * the generated DAO reads between `worker_class_name` and `input`. The other ~24
 * column names in the same method are still plain literals.
 */
private object WorkSpecDaoSchedulingFingerprint : Fingerprint(
    definingClass = "Landroidx/work/impl/model/WorkSpecDao_Impl;",
    name = "getAllEligibleWorkSpecsForScheduling",
    returnType = "Ljava/util/List;"
)

val fixWorkManagerColumnNamesPatch = bytecodePatch(
    name = "Fix WorkManager column names",
    description = "Restores the Room column name that PairIP moved into the encrypted string pool, so WorkManager no longer kills the game with \"column '' does not exist\" on the next launch after the app was killed."
) {
    compatibleWith(COMPATIBILITY_20MTD)

    dependsOn(pairipBypassPatch)

    execute {
        val method = WorkSpecDaoSchedulingFingerprint.method
        val instructions = method.instructions.toList()

        // The pool read is the only sget-object in this method that pulls a String
        // from outside androidx — the DAO's own fields are all androidx types.
        val poolReadIndex = instructions.indexOfFirst { instruction ->
            if (instruction.opcode != Opcode.SGET_OBJECT) return@indexOfFirst false
            val reference = (instruction as? BuilderInstruction21c)?.reference
            val field = reference as? FieldReference ?: return@indexOfFirst false
            field.type == "Ljava/lang/String;" && !field.definingClass.startsWith("Landroidx/")
        }

        if (poolReadIndex >= 0) {
            val poolRead = instructions[poolReadIndex] as BuilderInstruction21c
            val register = poolRead.registerA
            method.replaceInstruction(
                poolReadIndex,
                BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    register,
                    ImmutableStringReference("input_merger_class_name")
                )
            )
        }
    }
}
