package com.farmos.data.herd

import androidx.room.withTransaction
import com.farmos.core.database.FarmOsDatabase
import com.farmos.core.database.UnitPreferenceEntity
import com.farmos.core.database.BudgetEntity
import com.farmos.core.model.LocalCommandContext
import com.farmos.core.model.LocalCommandResult
import com.farmos.domain.ops.OpsValidator
import com.farmos.domain.ops.RecordUnitPreference
import com.farmos.domain.ops.RecordBudget
import com.farmos.domain.ops.ReviseBudget
import kotlinx.serialization.json.Json

/** Command handlers extracted from the operations facade; validation and writes share one transaction. */
internal class FarmConfigurationCommands(
    private val database: FarmOsDatabase,
    private val farmId: String,
    private val json: Json,
    private val journal: OpsCommandJournal,
) {
    suspend fun recordUnitPreference(command: RecordUnitPreference, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.unitPreference(command)?.let { error(it) }
        require(command.farmId == farmId) { "Farm context mismatch" }
        val preferenceId = farmId + ":" + command.quantityKind
        journal.enqueueCommand(json, context, "farm.record_unit_preference.v1", "unit_preference", preferenceId, journal.observedVersion("unit_preference", preferenceId), command) {
            database.unitPreferences().upsert(
                UnitPreferenceEntity(farmId, command.quantityKind, command.displayUnit, context.occurredAtEpochMillis, context.actorId),
            )
        }
        LocalCommandResult(context.mutationId, "$farmId:${command.quantityKind}", true)
    }

    suspend fun recordBudget(command: RecordBudget, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.budget(command)?.let { error(it) }
        journal.enqueueCommand(json, context, "finance.record_budget.v1", "budget", command.budgetKey, 0, command) {
            val duplicate = database.budgets().active(farmId).any {
                it.kind == command.kind.trim() &&
                    it.categoryCode == command.categoryCode.trim() &&
                    it.currency == command.currency &&
                    it.periodStartYearMonth == command.periodStartYearMonth &&
                    it.periodEndYearMonth == command.periodEndYearMonth
            }
            if (duplicate) error("A budget already exists for this kind, category, currency and period")
            database.budgets().insert(
                BudgetEntity(
                    id = command.budgetId,
                    farmId = farmId,
                    budgetKey = command.budgetKey,
                    version = 1L,
                    name = command.name.trim(),
                    kind = command.kind.trim(),
                    categoryCode = command.categoryCode.trim(),
                    periodStartYearMonth = command.periodStartYearMonth,
                    periodEndYearMonth = command.periodEndYearMonth,
                    amountMinor = command.amountMinor,
                    currency = command.currency,
                    superseded = false,
                    createdAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.budgetId, true)
    }

    suspend fun reviseBudget(command: ReviseBudget, context: LocalCommandContext): LocalCommandResult = database.withTransaction {
        OpsValidator.reviseBudget(command)?.let { error(it) }
        val locallyObserved = database.budgets().revisions(farmId, command.budgetKey).firstOrNull()?.version ?: 0L
        val expected = journal.originalBaseVersion(context, locallyObserved)
        journal.enqueueCommand(json, context, "finance.revise_budget.v1", "budget", command.budgetKey, expected, command) {
            val latest = database.budgets().revisions(farmId, command.budgetKey).firstOrNull()
                ?: error("Budget not found")
            require(latest.version == expected) {
                "Conflict: budget changed from version $expected to " + latest.version + "; review the original revision"
            }
            val nextVersion = Math.addExact(expected, 1L)
            database.budgets().supersedePrior(farmId, command.budgetKey)
            database.budgets().insert(
                BudgetEntity(
                    id = command.budgetId,
                    farmId = farmId,
                    budgetKey = command.budgetKey,
                    version = nextVersion,
                    name = command.name.trim(),
                    kind = latest.kind,
                    categoryCode = latest.categoryCode,
                    periodStartYearMonth = latest.periodStartYearMonth,
                    periodEndYearMonth = latest.periodEndYearMonth,
                    amountMinor = command.amountMinor,
                    currency = latest.currency,
                    superseded = false,
                    createdAtEpochMillis = context.occurredAtEpochMillis,
                ),
            )
        }
        LocalCommandResult(context.mutationId, command.budgetId, true)
    }
}
