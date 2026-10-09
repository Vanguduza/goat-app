package com.farmos.app

import androidx.compose.runtime.Composable
import com.farmos.domain.ops.RecordRabbitFoster
import com.farmos.domain.ops.RecordRabbitKindling
import com.farmos.domain.ops.RecordRabbitPalpation
import com.farmos.domain.rabbit.CreateRabbitCage
import com.farmos.domain.rabbit.CreateRabbitNestBox
import com.farmos.domain.rabbit.CreateRabbitWave
import com.farmos.domain.rabbit.DecideRabbitRetention
import com.farmos.domain.rabbit.RecordRabbitGiStasis
import com.farmos.domain.rabbit.RecordRabbitMarketPlan
import com.farmos.feature.rabbit.RabbitProgrammeScreen
import java.time.LocalDate
import java.util.UUID

@Composable
internal fun RabbitModuleContent(state: RabbitModuleState) {
    with(state) {
    RabbitProgrammeScreen(
        rabbits = rabbits,
        entryProfile = entryProfile,
        loading = loading,
        rabbitExit = { rabbit -> exitFor(rabbit) { run { } } },
        rabbitAttachments = attachmentsFor,
        pedigree = pedigree,
        waveOptions = waveOptions,
        nestBoxChoices = nestBoxChoices,
        searchRabbits = searchRabbits,
        cages = cages,
        waves = waves,
        availableBoxes = boxes,
        busy = busy,
        error = error,
        does = rabbitRows,
        onRegisterDoe = { tag, name, sex ->
            run { rabbitHerd.register(UUID.randomUUID().toString(), tag, name, sex, null, newContext()) }
        },
        onCreateCage = { code ->
            run { ops.createCage(CreateRabbitCage(UUID.randomUUID().toString(), code), newContext()) }
        },
        onCreateNestBox = { cageCode, boxCode ->
            run {
                val cageId = ops.cages().firstOrNull { it.code == cageCode }?.id ?: error("Cage not found")
                selectedCageId = cageId
                ops.createNestBox(CreateRabbitNestBox(UUID.randomUUID().toString(), cageId, boxCode), newContext())
            }
        },
        onCreateWave = { cageCode, doeCount, matingDay ->
            run {
                val cageId = ops.cages().firstOrNull { it.code == cageCode }?.id ?: error("Cage not found")
                selectedCageId = cageId
                ops.createWave(
                    CreateRabbitWave(
                        waveId = UUID.randomUUID().toString(),
                        cageId = cageId,
                        doeCount = doeCount,
                        matingEpochDay = LocalDate.parse(matingDay).toEpochDay(),
                        placeTaskId = UUID.randomUUID().toString(),
                        kindlingTaskId = UUID.randomUUID().toString(),
                        removeTaskId = UUID.randomUUID().toString(),
                        rebreedTaskId = UUID.randomUUID().toString(),
                        weanTaskId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onPalpate = { waveId, result, day ->
            run {
                ops.recordPalpation(
                    RecordRabbitPalpation(UUID.randomUUID().toString(), waveId, result, LocalDate.parse(day).toEpochDay()),
                    newContext(),
                )
            }
        },
        onKindle = { waveId, live, dead, day ->
            run {
                ops.recordKindling(
                    RecordRabbitKindling(
                        UUID.randomUUID().toString(),
                        waveId,
                        live.toIntOrNull() ?: 0,
                        dead.toIntOrNull() ?: 0,
                        LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onFoster = { fromWave, toWave, kits, day, ack ->
            run {
                ops.recordFoster(
                    RecordRabbitFoster(
                        fosterId = UUID.randomUUID().toString(),
                        fromWaveId = fromWave,
                        toWaveId = toWave,
                        kitCount = kits.toIntOrNull() ?: 0,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        ackOutsideWindow = ack,
                    ),
                    newContext(),
                )
            }
        },
        nestBoxes = nestBoxRows,
        onSetNestStatus = { boxId, status ->
            run { ops.setNestBoxStatus(com.farmos.domain.rabbit.SetRabbitNestBoxStatus(boxId, status), newContext()) }
        },
        onWean = { waveId, count, day ->
            run {
                ops.recordWean(
                    com.farmos.domain.rabbit.RecordRabbitWean(
                        UUID.randomUUID().toString(), waveId, count.toIntOrNull() ?: 0, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordOutcome = { waveId, outcome, day ->
            run {
                ops.recordMatingOutcome(
                    com.farmos.domain.rabbit.RecordRabbitMatingOutcome(
                        UUID.randomUUID().toString(), waveId, outcome, LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordGiStasis = { animalId, signs, day ->
            run {
                ops.recordGiStasis(
                    RecordRabbitGiStasis(
                        flagId = UUID.randomUUID().toString(),
                        animalId = animalId,
                        signs = signs,
                        occurredEpochDay = LocalDate.parse(day).toEpochDay(),
                        taskId = UUID.randomUUID().toString(),
                    ),
                    newContext(),
                )
            }
        },
        onDecideRetention = { kitId, decision, day ->
            run {
                ops.decideRetention(
                    DecideRabbitRetention(
                        UUID.randomUUID().toString(), kitId, decision,
                        LocalDate.parse(day).toEpochDay(),
                    ),
                    newContext(),
                )
            }
        },
        onRecordWeight = { animalId, weightKg, day, notes ->
            run {
                ops.recordRabbitWeight(
                    com.farmos.domain.rabbit.RecordRabbitWeight(
                        weightId = UUID.randomUUID().toString(),
                        animalId = animalId,
                        weightKg = weightKg.toDoubleOrNull() ?: 0.0,
                        weighedAtEpochMillis = LocalDate.parse(day).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        notes = notes,
                    ),
                    newContext(),
                )
            }
        },
        onAllocateSale = { kitId, targetWeightGrams, targetDay, purpose ->
            run {
                ops.recordPlan(
                    RecordRabbitMarketPlan(
                        UUID.randomUUID().toString(), kitId, null,
                        targetWeightGrams.toIntOrNull() ?: 0,
                        LocalDate.parse(targetDay).toEpochDay(), purpose,
                    ),
                    newContext(),
                )
            }
        },
        onBack = onBack,
        records = records,
        rabbitCount = rabbitCount,
    )

    }
}
