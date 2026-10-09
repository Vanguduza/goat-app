package com.farmos.app

import androidx.compose.runtime.Composable
import com.farmos.domain.ops.CandlePoultryHatch
import com.farmos.domain.ops.CreatePoultryHouse
import com.farmos.domain.ops.EnablePoultryKind
import com.farmos.domain.ops.PlacePoultryFlock
import com.farmos.domain.ops.RecordPoultryBiosecurity
import com.farmos.domain.ops.RecordPoultryFlockDay
import com.farmos.domain.ops.RecordPoultryHatch
import com.farmos.domain.ops.RecordPoultryVaccination
import com.farmos.domain.ops.MovePoultryFlock
import com.farmos.domain.ops.ClosePoultryFlock
import com.farmos.domain.ops.SetPoultryHatch
import com.farmos.feature.ops.PoultryExperienceScreen
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun PoultryModuleContent(state: PoultryModuleState) {
    with(state) {
    PoultryExperienceScreen(
        flockGroups = flockGroups,
        houseOptions = houseOptions,
        poultryFormulary = poultryFormulary,
        setHatches = setHatches,
        candledHatches = candledHatches,
        enabledKinds = enabledKinds,
        houses = houses,
        placements = placements,
        flockDays = flockDays,
        hatches = hatches,
        vaccinations = vaccinations,
        busy = busy,
        error = error,
        onEnableKind = { kind -> run { ops.enablePoultryKind(EnablePoultryKind(kind), newContext()) } },
        onCreateHouse = { code, houseKind, poultryKind ->
            run { ops.createHouse(CreatePoultryHouse(UUID.randomUUID().toString(), code, houseKind, poultryKind), newContext()) }
        },
        onPlaceFlock = { groupId, houseId, poultryKind, heads, day ->
            run {
                ops.placeFlock(
                    PlacePoultryFlock(
                        UUID.randomUUID().toString(), groupId, houseId, poultryKind, heads.toIntOrNull() ?: 0,
                        LocalDate.parse(day).toEpochDay(), UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordFlockDay = { groupId, eggs, dead, culls, feedGrams, day ->
            run {
                ops.recordFlockDay(
                    RecordPoultryFlockDay(
                        UUID.randomUUID().toString(), groupId, eggs.toIntOrNull() ?: 0, dead.toIntOrNull() ?: 0,
                        culls.toIntOrNull() ?: 0, feedGrams.toLongOrNull() ?: 0L, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onSetEggs = { poultryKind, eggs, day, houseId, groupId ->
            run {
                ops.setHatch(
                    SetPoultryHatch(
                        hatchId = UUID.randomUUID().toString(),
                        poultryKindCode = poultryKind,
                        eggsSet = eggs.toIntOrNull() ?: 0,
                        setEpochDay = LocalDate.parse(day).toEpochDay(),
                        houseId = houseId.trim().ifBlank { null },
                        groupId = groupId.trim().ifBlank { null },
                        candleTaskId = UUID.randomUUID().toString(),
                        lockTaskId = UUID.randomUUID().toString(),
                        hatchTaskId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onCandle = { hatchId, fertile, infertile, midDead, day ->
            run {
                ops.candleHatch(
                    CandlePoultryHatch(
                        hatchId,
                        fertile.toIntOrNull() ?: 0,
                        infertile.toIntOrNull() ?: 0,
                        midDead.toIntOrNull() ?: 0,
                        LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordHatch = { hatchId, hatched, culls, day ->
            run {
                ops.recordHatch(
                    RecordPoultryHatch(
                        hatchId,
                        hatched.toIntOrNull() ?: 0,
                        culls.toIntOrNull() ?: 0,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onVaccinate = { groupId, poultryKind, formularyId, day ->
            run {
                ops.recordVaccination(
                    RecordPoultryVaccination(
                        UUID.randomUUID().toString(), groupId, poultryKind, formularyId, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBiosecurity = { houseId, groupId, findings, mixedSpecies, day ->
            run {
                ops.recordBiosecurity(
                    RecordPoultryBiosecurity(
                        UUID.randomUUID().toString(),
                        houseId.trim().ifBlank { null },
                        groupId.trim().ifBlank { null },
                        findings,
                        mixedSpecies,
                        LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onMoveFlock = { groupId, fromHouseId, toHouseId, heads, day ->
            run {
                ops.moveFlock(
                    MovePoultryFlock(
                        UUID.randomUUID().toString(), groupId, fromHouseId, toHouseId,
                        heads.toIntOrNull() ?: 0, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onCloseFlock = { groupId, headOut, reason, day ->
            run {
                ops.closeFlock(
                    ClosePoultryFlock(
                        UUID.randomUUID().toString(), groupId,
                        headOut.toIntOrNull() ?: 0, reason.trim(), LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        records = records,
        loadFlock = loadFlock,
    )

    }
}
