package com.farmos.domain.ops

import kotlinx.serialization.Serializable

@Serializable
data class CreateFarmTask(
    val taskId: String,
    val moduleCode: String,
    val taskCode: String,
    val title: String,
    val dueEpochDay: Long,
    val animalId: String? = null,
    val cageId: String? = null,
    val waveId: String? = null,
)

@Serializable
data class CompleteFarmTask(
    val taskId: String,
)

@Serializable
data class RecordHealthObservation(
    val observationId: String,
    val speciesCode: String,
    val animalId: String? = null,
    val signs: String,
    val firstAidApplied: String? = null,
    val redFlag: Boolean = false,
    val occurredAtEpochMillis: Long,
)

@Serializable
data class RecordMoney(
    val recordId: String,
    val kind: String,
    val categoryCode: String,
    val amountMinor: Long,
    val currency: String = "USD",
    val occurredEpochDay: Long,
    val note: String? = null,
)

@Serializable
data class CreateInventoryItem(
    val itemId: String,
    val sku: String,
    val name: String,
    val unit: String = "kg",
)

@Serializable
data class CreateAnimalGroup(
    val groupId: String,
    val speciesCode: String,
    val name: String,
    val headCount: Int,
)

@Serializable
data class CreatePaddock(
    val paddockId: String,
    val code: String,
    val displayName: String,
    val areaM2: Int? = null,
    val waterSource: String = "none",
    val shade: Boolean = false,
)

@Serializable
data class StartGrazing(
    val sessionId: String,
    val paddockId: String,
    val groupId: String,
    val headCount: Int,
    val enteredEpochDay: Long,
)

@Serializable
data class EndGrazing(
    val sessionId: String,
    val exitedEpochDay: Long,
)

@Serializable
data class RecordLabour(
    val entryId: String,
    val workerName: String,
    val taskCode: String,
    val minutes: Int,
    val occurredEpochDay: Long,
    val note: String? = null,
)

@Serializable
data class CreateFarmAsset(
    val assetId: String,
    val code: String,
    val name: String,
    val kind: String = "equipment",
)

@Serializable
data class RecordMaintenance(
    val eventId: String,
    val assetId: String,
    val title: String,
    val occurredEpochDay: Long,
    val note: String? = null,
)

@Serializable
data class IssueFeed(
    val issueId: String,
    val itemId: String,
    val groupId: String? = null,
    val quantityMilli: Long,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordWater(
    val recordId: String,
    val source: String,
    val litresMilli: Long,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSale(
    val saleId: String,
    val itemKind: String,
    val quantityMilli: Long,
    val amountMinor: Long,
    val currency: String = "USD",
    val occurredEpochDay: Long,
)

@Serializable
data class CreateFormularyItem(
    val itemId: String,
    val productName: String,
    val speciesCode: String,
    val vetClass: String,
    val meatWithdrawalDays: Int? = null,
    val milkWithdrawalDays: Int? = null,
    val eggWithdrawalDays: Int? = null,
    val vetApproved: Boolean = true,
)

@Serializable
data class RecordHealthTreatment(
    val treatmentId: String,
    val speciesCode: String,
    val formularyItemId: String,
    val reason: String,
    val animalId: String? = null,
    val occurredAtEpochMillis: Long,
)

@Serializable
data class AssignAnimalIdentifier(
    val identifierId: String,
    val animalId: String,
    val type: String,
    val value: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordOfficialMovement(
    val movementId: String,
    val animalId: String,
    val direction: String,
    val fromPlace: String? = null,
    val toPlace: String? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class ReceiveInventoryLot(
    val lotId: String,
    val itemId: String,
    val lotCode: String,
    val expiresEpochDay: Long,
    val quantityMilli: Long,
)

@Serializable
data class IssueInventoryLot(
    val issueId: String,
    val itemId: String,
    val quantityMilli: Long,
)

@Serializable
data class RecordVetVisit(
    val visitId: String,
    val speciesCode: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val reason: String,
    val attendingVet: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordLabResult(
    val resultId: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val testName: String,
    val resultText: String,
    val cellsPerMl: Int? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleWeaning(
    val weaningId: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val weightGrams: Long? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepMicron(
    val testId: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val micronTenths: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class EnablePoultryKind(
    val poultryKindCode: String,
)

@Serializable
data class LinkPedigree(
    val linkId: String,
    val animalId: String,
    val parentId: String,
    val relationType: String,
)

@Serializable
data class RecordFamacha(
    val scoreId: String,
    val animalId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class AddHealthPackSlot(
    val slotId: String,
    val packId: String,
    val slotCode: String,
    val title: String,
    val offsetDays: Int,
    val fromEvent: String = "apply_date",
    val isCore: Boolean = true,
)

@Serializable
data class ApplyHealthPack(
    val applyId: String,
    val packId: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val anchorEpochDay: Long,
)

@Serializable
data class PlaceCattleLot(
    val placementId: String,
    val groupId: String,
    val headCount: Int,
    val placedEpochDay: Long,
)

@Serializable
data class RecordCattleDaysOnFeed(
    val recordId: String,
    val groupId: String,
    val daysOnFeed: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class CloseCattleLot(
    val closeoutId: String,
    val groupId: String,
    val headOut: Int,
    val weightGrams: Long? = null,
    val daysOnFeed: Int? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class SetInventoryReorder(
    val itemId: String,
    val reorderMilli: Long,
)

@Serializable
data class RecordReorderAlert(
    val alertId: String,
    val itemId: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordGroupCensus(
    val censusId: String,
    val groupId: String,
    val headCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordPoultryFlockDay(
    val dayId: String,
    val groupId: String,
    val eggs: Int,
    val dead: Int,
    val culls: Int,
    val feedGrams: Long,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepJoining(
    val joiningId: String,
    val groupId: String,
    val startedEpochDay: Long,
    val scanTaskId: String,
    val preLambTaskId: String,
    val paddockTaskId: String,
    val lambingTaskId: String,
)

@Serializable
data class RecordSheepScan(
    val scanId: String,
    val animalId: String,
    val result: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepLambing(
    val lambingId: String,
    val damAnimalId: String,
    val bornCount: Int,
    val liveCount: Int,
    val deadCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleService(
    val serviceId: String,
    val animalId: String,
    val method: String,
    val occurredEpochDay: Long,
    val pdTaskId: String,
    val paddockTaskId: String,
    val calvingTaskId: String,
)

@Serializable
data class RecordCattlePd(
    val pdId: String,
    val animalId: String,
    val result: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleCalving(
    val calvingId: String,
    val damAnimalId: String,
    val bornCount: Int,
    val liveCount: Int,
    val deadCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordRabbitPalpation(
    val palpationId: String,
    val waveId: String,
    val result: String,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordRabbitKindling(
    val kindlingId: String,
    val waveId: String,
    val liveCount: Int,
    val deadCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordRabbitFoster(
    val fosterId: String,
    val fromWaveId: String,
    val toWaveId: String,
    val kitCount: Int,
    val occurredEpochDay: Long,
    val ackOutsideWindow: Boolean = false,
)

@Serializable
data class CreateSupplier(
    val supplierId: String,
    val name: String,
    val leadTimeDays: Int = 0,
)

@Serializable
data class RecordPurchase(
    val purchaseId: String,
    val supplierId: String,
    val itemId: String,
    val quantityMilli: Long,
    val amountMinor: Long,
    val currency: String = "USD",
    val occurredEpochDay: Long,
)

@Serializable
data class AcceptHealthPack(
    val packId: String,
    val speciesCode: String,
    val name: String,
    val acceptedByVet: String,
)

@Serializable
data class RecordSheepMarking(
    val markingId: String,
    val groupId: String? = null,
    val animalId: String? = null,
    val markedCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepWeaning(
    val weaningId: String,
    val groupId: String? = null,
    val animalId: String? = null,
    val weanedCount: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleBcs(
    val scoreId: String,
    val animalId: String,
    val scale: String,
    val scoreTenths: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepWool(
    val clipId: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val greasyGrams: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleMilk(
    val milkId: String,
    val animalId: String,
    val litresMilli: Long,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepDag(
    val scoreId: String,
    val animalId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepFootrot(
    val scoreId: String,
    val animalId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleLocomotion(
    val scoreId: String,
    val animalId: String,
    val score: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleScc(
    val recordId: String,
    val animalId: String,
    val cellsPerMl: Int,
    val dimDays: Int? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepShearing(
    val eventId: String,
    val animalId: String? = null,
    val groupId: String? = null,
    val kind: String,
    val greasyGrams: Int? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordSheepFlystrike(
    val scoreId: String,
    val animalId: String,
    val score: Int,
    val region: String? = null,
    val occurredEpochDay: Long,
)

@Serializable
data class CreatePoultryHouse(
    val houseId: String,
    val code: String,
    val kind: String,
    val poultryKindCode: String,
)

@Serializable
data class SetPoultryHatch(
    val hatchId: String,
    val poultryKindCode: String,
    val eggsSet: Int,
    val setEpochDay: Long,
    val houseId: String? = null,
    val groupId: String? = null,
    val incubationDays: Int? = null,
    val candleTaskId: String,
    val lockTaskId: String,
    val hatchTaskId: String,
)

@Serializable
data class CandlePoultryHatch(
    val hatchId: String,
    val fertile: Int,
    val infertile: Int,
    val midDead: Int,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordPoultryVaccination(
    val vaccinationId: String,
    val groupId: String,
    val poultryKindCode: String,
    val formularyItemId: String,
    val occurredEpochDay: Long,
)

@Serializable
data class PlacePoultryFlock(
    val placementId: String,
    val groupId: String,
    val houseId: String,
    val poultryKindCode: String,
    val headCount: Int,
    val occurredEpochDay: Long,
    val inspectTaskId: String,
    val vaxTaskId: String,
)

@Serializable
data class RecordPoultryBiosecurity(
    val walkId: String,
    val houseId: String? = null,
    val groupId: String? = null,
    val findings: String,
    val mixedSpecies: Boolean = false,
    val occurredEpochDay: Long,
)

@Serializable
data class RecordCattleDryOff(
    val dryOffId: String,
    val animalId: String,
    val occurredEpochDay: Long,
    val expectedCalvingEpochDay: Long? = null,
)

@Serializable
data class RecordPoultryHatch(
    val hatchId: String,
    val hatched: Int,
    val culls: Int = 0,
    val placementGroupId: String? = null,
    val occurredEpochDay: Long,
)

object PoultryKindIncubation {
    val KINDS = setOf(
        "chicken", "duck", "muscovy", "guinea_fowl", "turkey", "goose", "quail", "pigeon", "farm_defined",
    )
    val HOUSE_KINDS = setOf("house", "coop", "range", "hatchery", "brooder", "pond", "loft")
    const val LOCKDOWN_LEAD_DAYS = 3

    fun days(kind: String, farmDefinedDays: Int? = null): Int? = when (kind) {
        "chicken" -> 21
        "duck", "guinea_fowl", "turkey" -> 28
        "muscovy" -> 35
        "goose" -> 30
        "quail", "pigeon" -> 17
        "farm_defined" -> farmDefinedDays?.takeIf { it in 1..60 }
        else -> null
    }

    fun candlingLeadDays(kind: String): Int = when (kind) {
        "duck", "muscovy", "guinea_fowl", "turkey", "goose" -> 10
        else -> 7
    }
}

@Serializable
data class MoveInventory(
    val movementId: String,
    val itemId: String,
    val direction: String,
    val quantityMilli: Long,
    val occurredAtEpochMillis: Long,
)

object OpsValidator {
    fun task(command: CreateFarmTask): String? =
        if (command.title.isBlank() || command.taskCode.isBlank()) "Task title and code are required" else null

    fun observation(command: RecordHealthObservation): String? {
        if (command.signs.isBlank()) return "Signs are required"
        return null
    }

    fun money(command: RecordMoney): String? {
        if (command.kind !in setOf("expense", "income")) return "Money kind must be expense or income"
        if (command.amountMinor <= 0L) return "Amount must be greater than zero"
        if (command.currency.length != 3) return "Currency must be a 3-letter code"
        return null
    }

    fun inventoryItem(command: CreateInventoryItem): String? =
        if (command.sku.isBlank() || command.name.isBlank()) "Inventory sku and name are required" else null

    fun group(command: CreateAnimalGroup): String? =
        if (command.name.isBlank() || command.headCount <= 0) "Group needs a species, name, and head count" else null

    fun paddock(command: CreatePaddock): String? =
        if (command.code.isBlank()) "Paddock needs a code and a water source" else null

    fun labour(command: RecordLabour): String? =
        if (command.workerName.isBlank() || command.taskCode.isBlank() || command.minutes <= 0) {
            "Labour needs a worker, task code, and minutes"
        } else {
            null
        }

    fun asset(command: CreateFarmAsset): String? =
        if (command.code.isBlank() || command.name.isBlank()) "Asset needs a code and name" else null

    fun feed(command: IssueFeed, onHandMilli: Long): String? {
        if (command.quantityMilli <= 0L) return "Feed quantity must be greater than zero"
        if (onHandMilli < command.quantityMilli) return "Not enough feed on this farm"
        return null
    }

    fun water(command: RecordWater): String? =
        if (command.source.isBlank() || command.litresMilli <= 0L) "Water record needs a source and litres" else null

    fun sale(command: RecordSale): String? {
        if (command.itemKind.isBlank() || command.quantityMilli <= 0L || command.amountMinor <= 0L) {
            return "Sale needs an item, quantity, and amount"
        }
        return null
    }

    fun formulary(command: CreateFormularyItem): String? {
        if (command.productName.isBlank() || command.speciesCode.isBlank() || command.vetClass.isBlank()) {
            return "Formulary item needs a product, species, and vet class"
        }
        if (!command.vetApproved) return "Formulary items must be marked vet-approved before they can be used"
        return null
    }

    fun treatment(command: RecordHealthTreatment): String? {
        if (command.formularyItemId.isBlank() || command.reason.isBlank()) {
            return "Treatment needs a vet-approved formulary item and reason"
        }
        return null
    }

    fun famacha(command: RecordFamacha): String? =
        if (command.score !in 1..5) "FAMACHA score must be 1 to 5" else null

    fun flockDay(command: RecordPoultryFlockDay): String? =
        if (command.eggs < 0 || command.dead < 0 || command.culls < 0 || command.feedGrams < 0) {
            "Flock day counts cannot be negative"
        } else {
            null
        }

    fun joining(command: RecordSheepJoining): String? =
        if (command.groupId.isBlank()) "Joining needs a sheep mob" else null

    fun scan(command: RecordSheepScan): String? =
        if (command.result !in setOf("dry", "single", "twin", "triplet")) {
            "Scan result must be dry, single, twin, or triplet"
        } else {
            null
        }

    fun lambing(command: RecordSheepLambing): String? =
        if (command.bornCount <= 0 || command.liveCount + command.deadCount != command.bornCount) {
            "Lambing counts must add up and be greater than zero"
        } else {
            null
        }

    fun cattleService(command: RecordCattleService): String? =
        if (command.method !in setOf("ai", "natural", "et")) "Cattle service must be ai, natural, or et" else null

    fun cattlePd(command: RecordCattlePd): String? =
        if (command.result !in setOf("pregnant", "open")) "PD result must be pregnant or open" else null

    fun calving(command: RecordCattleCalving): String? =
        if (command.bornCount <= 0 || command.liveCount + command.deadCount != command.bornCount) {
            "Calving counts must add up and be greater than zero"
        } else {
            null
        }

    fun palpation(command: RecordRabbitPalpation): String? =
        if (command.result !in setOf("pregnant", "open")) "Palpation needs pregnant or open" else null

    fun kindling(command: RecordRabbitKindling): String? =
        if (command.liveCount < 0 || command.deadCount < 0) "Kindling needs live and dead counts" else null

    fun foster(command: RecordRabbitFoster): String? {
        if (command.kitCount <= 0 || command.fromWaveId == command.toWaveId) {
            return "Foster needs two waves and a kit count"
        }
        return null
    }

    fun supplier(command: CreateSupplier): String? =
        if (command.name.isBlank()) "Supplier name is required" else null

    fun purchase(command: RecordPurchase): String? =
        if (command.quantityMilli <= 0L || command.amountMinor <= 0L) "Purchase needs a quantity and amount" else null

    fun marking(command: RecordSheepMarking): String? =
        if (command.markedCount <= 0 || (command.groupId.isNullOrBlank() && command.animalId.isNullOrBlank())) {
            "Marking needs a mob or lamb and a count"
        } else {
            null
        }

    fun sheepWeaning(command: RecordSheepWeaning): String? =
        if (command.weanedCount <= 0 || (command.groupId.isNullOrBlank() && command.animalId.isNullOrBlank())) {
            "Weaning needs a mob or lamb and a count"
        } else {
            null
        }

    fun cattleBcs(command: RecordCattleBcs): String? {
        val ok = when (command.scale) {
            "1_5" -> command.scoreTenths in 10..50
            "1_9" -> command.scoreTenths in 10..90
            else -> false
        }
        return if (ok) null else "BCS must use the 1-5 or 1-9 scale"
    }

    fun cattleMilk(command: RecordCattleMilk): String? =
        if (command.litresMilli <= 0L) "Milk record needs litres" else null

    fun dag(command: RecordSheepDag): String? =
        if (command.score !in 0..5) "Dag score must be 0 to 5" else null

    fun footrot(command: RecordSheepFootrot): String? =
        if (command.score !in 0..5) "Footrot score must be 0 to 5" else null

    fun locomotion(command: RecordCattleLocomotion): String? =
        if (command.score !in 1..5) "Locomotion score must be 1 to 5" else null

    fun scc(command: RecordCattleScc): String? =
        if (command.cellsPerMl <= 0) "SCC needs cells per millilitre" else null

    fun shearing(command: RecordSheepShearing): String? =
        if (command.kind !in setOf("shearing", "crutching", "classing") ||
            (command.animalId.isNullOrBlank() && command.groupId.isNullOrBlank())
        ) {
            "Shearing needs a kind and a sheep or mob"
        } else {
            null
        }

    fun flystrike(command: RecordSheepFlystrike): String? =
        if (command.score !in 0..5) "Flystrike score must be 0 to 5" else null

    fun house(command: CreatePoultryHouse): String? =
        if (command.code.isBlank() ||
            command.kind !in PoultryKindIncubation.HOUSE_KINDS ||
            command.poultryKindCode !in PoultryKindIncubation.KINDS
        ) {
            "House needs a code, listed housing kind, and poultry kind"
        } else {
            null
        }

    fun hatchSet(command: SetPoultryHatch): String? {
        val days = PoultryKindIncubation.days(command.poultryKindCode, command.incubationDays)
        if (command.eggsSet <= 0 || days == null) {
            return "Hatch set needs a poultry kind and an egg count"
        }
        return null
    }

    fun hatchCandle(command: CandlePoultryHatch): String? =
        if (command.fertile < 0 || command.infertile < 0 || command.midDead < 0) {
            "Candling counts cannot be negative"
        } else {
            null
        }

    fun hatchRecord(command: RecordPoultryHatch): String? =
        if (command.hatched < 0 || command.culls < 0) "Hatch needs hatched and cull counts" else null

    fun vaccination(command: RecordPoultryVaccination): String? =
        if (command.groupId.isBlank() ||
            command.formularyItemId.isBlank() ||
            command.poultryKindCode !in PoultryKindIncubation.KINDS
        ) {
            "Vaccination needs a flock, kind, and vet-approved formulary item"
        } else {
            null
        }

    fun dryOff(command: RecordCattleDryOff): String? =
        if (command.animalId.isBlank()) "Dry-off needs a cow" else null

    fun flockPlace(command: PlacePoultryFlock): String? =
        if (command.headCount <= 0 || command.poultryKindCode !in PoultryKindIncubation.KINDS) {
            "Placement needs a poultry kind and head count"
        } else {
            null
        }

    fun biosecurity(command: RecordPoultryBiosecurity): String? =
        if (command.findings.isBlank() || (command.houseId.isNullOrBlank() && command.groupId.isNullOrBlank())) {
            "Biosecurity walk needs findings and a house or flock"
        } else {
            null
        }

    fun identifier(command: AssignAnimalIdentifier): String? =
        if (command.value.isBlank() || command.type !in IDENTIFIER_TYPES) {
            "Identifier needs a listed type and a value"
        } else {
            null
        }

    fun movement(command: RecordOfficialMovement): String? =
        if (command.direction !in setOf("on", "off", "transfer")) "Movement must be on, off, or transfer" else null

    fun lotReceive(command: ReceiveInventoryLot): String? =
        if (command.lotCode.isBlank() || command.quantityMilli <= 0L) {
            "Lot receive needs a lot code, expiry, and quantity"
        } else {
            null
        }

    fun lotIssue(command: IssueInventoryLot): String? =
        if (command.quantityMilli <= 0L) "Lot issue needs a quantity" else null

    fun vetVisit(command: RecordVetVisit): String? =
        if (command.reason.isBlank() || command.attendingVet.isBlank()) {
            "Vet visit needs a species, reason, and attending vet"
        } else {
            null
        }

    fun lab(command: RecordLabResult): String? =
        if (command.testName.isBlank() || command.resultText.isBlank() ||
            (command.animalId.isNullOrBlank() && command.groupId.isNullOrBlank())
        ) {
            "Lab result needs a test, result, and an animal or group"
        } else {
            null
        }

    fun cattleWeaning(command: RecordCattleWeaning): String? =
        if (command.animalId.isNullOrBlank() && command.groupId.isNullOrBlank()) {
            "Cattle weaning needs a calf or lot"
        } else {
            null
        }

    fun micron(command: RecordSheepMicron): String? =
        if (command.micronTenths !in 80..500 || (command.animalId.isNullOrBlank() && command.groupId.isNullOrBlank())) {
            "Micron needs a sheep or mob and tenths from 80 to 500"
        } else {
            null
        }

    fun enableKind(command: EnablePoultryKind): String? =
        if (command.poultryKindCode !in PoultryKindIncubation.KINDS) "Enable needs a listed poultry kind" else null

    fun pedigree(command: LinkPedigree): String? =
        if (command.relationType !in setOf("sire", "dam", "genetic_dam", "recipient_dam") ||
            command.animalId == command.parentId
        ) {
            "Pedigree link needs a listed relation and two different animals"
        } else {
            null
        }

    fun packSlot(command: AddHealthPackSlot): String? =
        if (command.slotCode.isBlank() || command.title.isBlank() ||
            command.fromEvent !in setOf("apply_date", "expected_birth", "kidding", "kindling", "placement")
        ) {
            "Pack slot needs a code, title, offset, and listed anchor"
        } else {
            null
        }

    fun packApply(command: ApplyHealthPack): String? =
        if (command.animalId.isNullOrBlank() && command.groupId.isNullOrBlank()) {
            "Pack apply needs an animal or group"
        } else {
            null
        }

    fun lotPlace(command: PlaceCattleLot): String? =
        if (command.headCount <= 0) "Lot place needs a cattle lot and head count" else null

    fun daysOnFeed(command: RecordCattleDaysOnFeed): String? =
        if (command.daysOnFeed < 0) "Days on feed needs a cattle lot and a non-negative day count" else null

    fun lotClose(command: CloseCattleLot): String? =
        if (command.headOut <= 0) "Lot close-out needs a cattle lot and head-out count" else null

    fun setReorder(command: SetInventoryReorder): String? =
        if (command.reorderMilli < 0L) "Reorder point needs a non-negative milli quantity" else null

    fun reorderAlert(command: RecordReorderAlert): String? =
        if (command.itemId.isBlank()) "Reorder alert needs an item" else null

    fun census(command: RecordGroupCensus): String? =
        if (command.headCount < 0) "Census needs a group and a head count" else null

    fun sheepFamacha(command: RecordFamacha): String? =
        if (command.score !in 1..5) "FAMACHA score must be 1 to 5" else null

    fun wool(command: RecordSheepWool): String? =
        if (command.greasyGrams <= 0 || (command.animalId.isNullOrBlank() && command.groupId.isNullOrBlank())) {
            "Wool clip needs greasy grams and a sheep or mob"
        } else {
            null
        }

    fun pack(command: AcceptHealthPack): String? =
        if (command.name.isBlank() || command.acceptedByVet.isBlank()) {
            "A protocol pack needs a species, name, and attending vet"
        } else {
            null
        }

    fun inventoryMove(command: MoveInventory, onHandMilli: Long): String? {
        if (command.direction !in setOf("receive", "issue")) return "Inventory movement needs a direction"
        if (command.quantityMilli <= 0L) return "Quantity must be greater than zero"
        if (command.direction == "issue" && onHandMilli < command.quantityMilli) {
            return "Not enough stock on this farm"
        }
        return null
    }
}

private val IDENTIFIER_TYPES = setOf(
    "farm_id", "official_id", "rfid", "eid", "ear_tag", "tattoo", "registration", "name",
    "wing_band", "leg_band", "nlis", "nait", "freeze_brand", "herd_book",
)
