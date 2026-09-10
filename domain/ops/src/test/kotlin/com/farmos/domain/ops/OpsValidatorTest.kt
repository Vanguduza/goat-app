package com.farmos.domain.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpsValidatorTest {
    @Test
    fun `task title and code are required`() {
        assertNotNull(OpsValidator.task(CreateFarmTask("t1", "goat", "", "Check water", 19_723)))
        assertNull(OpsValidator.task(CreateFarmTask("t1", "goat", "CHECK", "Check water", 19_723)))
    }

    @Test
    fun `health observation rejects blank signs`() {
        assertNotNull(
            OpsValidator.observation(
                RecordHealthObservation(
                    observationId = "o1",
                    speciesCode = "goat",
                    signs = " ",
                    occurredAtEpochMillis = 1,
                ),
            ),
        )
    }

    @Test
    fun `money amount stays in integer minor units`() {
        assertNotNull(OpsValidator.money(RecordMoney("m1", "expense", "feed", 0, occurredEpochDay = 1)))
        assertNotNull(OpsValidator.money(RecordMoney("m1", "gift", "feed", 250, occurredEpochDay = 1)))
        assertNull(OpsValidator.money(RecordMoney("m1", "expense", "feed", 250, occurredEpochDay = 1)))
    }

    @Test
    fun `treatment requires a formulary item`() {
        assertNotNull(
            OpsValidator.treatment(
                RecordHealthTreatment("t1", "goat", "", "pale eyelids", occurredAtEpochMillis = 1),
            ),
        )
    }

    @Test
    fun `lambing counts must add up`() {
        assertNotNull(
            OpsValidator.lambing(
                RecordSheepLambing("l1", "ewe-1", bornCount = 2, liveCount = 2, deadCount = 1, occurredEpochDay = 1),
            ),
        )
        assertNull(
            OpsValidator.lambing(
                RecordSheepLambing("l1", "ewe-1", bornCount = 2, liveCount = 1, deadCount = 1, occurredEpochDay = 1),
            ),
        )
    }

    @Test
    fun `foster needs two waves`() {
        assertNotNull(
            OpsValidator.foster(
                RecordRabbitFoster("f1", "w1", "w1", kitCount = 2, occurredEpochDay = 1),
            ),
        )
        assertNull(
            OpsValidator.foster(
                RecordRabbitFoster("f1", "w1", "w2", kitCount = 2, occurredEpochDay = 1),
            ),
        )
    }

    @Test
    fun `cattle bcs stays on the named scale`() {
        assertNotNull(
            OpsValidator.cattleBcs(RecordCattleBcs("b1", "c1", "1_5", scoreTenths = 60, occurredEpochDay = 1)),
        )
        assertNull(
            OpsValidator.cattleBcs(RecordCattleBcs("b1", "c1", "1_5", scoreTenths = 25, occurredEpochDay = 1)),
        )
    }

    @Test
    fun `purchase needs quantity and amount`() {
        assertNotNull(
            OpsValidator.purchase(
                RecordPurchase("p1", "s1", "i1", quantityMilli = 0, amountMinor = 100, occurredEpochDay = 1),
            ),
        )
        assertNull(
            OpsValidator.purchase(
                RecordPurchase("p1", "s1", "i1", quantityMilli = 1000, amountMinor = 250, occurredEpochDay = 1),
            ),
        )
    }

    @Test
    fun `locomotion stays on 1 to 5`() {
        assertNotNull(OpsValidator.locomotion(RecordCattleLocomotion("l1", "c1", 0, 1)))
        assertNull(OpsValidator.locomotion(RecordCattleLocomotion("l1", "c1", 3, 1)))
    }

    @Test
    fun `scc needs cells per millilitre`() {
        assertNotNull(OpsValidator.scc(RecordCattleScc("s1", "c1", 0, occurredEpochDay = 1)))
        assertNull(OpsValidator.scc(RecordCattleScc("s1", "c1", 180_000, occurredEpochDay = 1)))
    }

    @Test
    fun `shearing kind is listed`() {
        assertNotNull(OpsValidator.shearing(RecordSheepShearing("e1", kind = "clip", occurredEpochDay = 1)))
        assertNull(OpsValidator.shearing(RecordSheepShearing("e1", animalId = "s1", kind = "crutching", occurredEpochDay = 1)))
    }

    @Test
    fun `flystrike stays on 0 to 5`() {
        assertNotNull(OpsValidator.flystrike(RecordSheepFlystrike("f1", "s1", 6, occurredEpochDay = 1)))
        assertNull(OpsValidator.flystrike(RecordSheepFlystrike("f1", "s1", 2, occurredEpochDay = 1)))
    }

    @Test
    fun `chicken hatch uses 21 day incubation`() {
        assertEquals(21, PoultryKindIncubation.days("chicken"))
        assertEquals(35, PoultryKindIncubation.days("muscovy"))
        assertEquals(17, PoultryKindIncubation.days("quail"))
        assertEquals(null, PoultryKindIncubation.days("farm_defined"))
        assertEquals(28, PoultryKindIncubation.days("farm_defined", farmDefinedDays = 28))
    }

    @Test
    fun `flock placement needs a kind and head count`() {
        assertNotNull(
            OpsValidator.flockPlace(
                PlacePoultryFlock("p1", "g1", "h1", "lizard", 0, 1, "t1", "t2"),
            ),
        )
        assertNull(
            OpsValidator.flockPlace(
                PlacePoultryFlock("p1", "g1", "h1", "chicken", 40, 1, "t1", "t2"),
            ),
        )
    }

    @Test
    fun `poultry vaccination needs a flock and kind`() {
        assertNotNull(
            OpsValidator.vaccination(
                RecordPoultryVaccination("v1", "", "chicken", "f1", 1),
            ),
        )
        assertNull(
            OpsValidator.vaccination(
                RecordPoultryVaccination("v1", "g1", "chicken", "f1", 1),
            ),
        )
    }

    @Test
    fun `hatch set needs eggs and a kind`() {
        assertNotNull(
            OpsValidator.hatchSet(
                SetPoultryHatch(
                    hatchId = "h1",
                    poultryKindCode = "lizard",
                    eggsSet = 12,
                    setEpochDay = 1,
                    candleTaskId = "t1",
                    lockTaskId = "t2",
                    hatchTaskId = "t3",
                ),
            ),
        )
        assertNull(
            OpsValidator.hatchSet(
                SetPoultryHatch(
                    hatchId = "h1",
                    poultryKindCode = "chicken",
                    eggsSet = 12,
                    setEpochDay = 1,
                    candleTaskId = "t1",
                    lockTaskId = "t2",
                    hatchTaskId = "t3",
                ),
            ),
        )
    }

    @Test
    fun `inventory issue cannot overdraw`() {
        val move = MoveInventory("mv1", "item-1", "issue", 500, 1)
        assertNotNull(OpsValidator.inventoryMove(move, onHandMilli = 400))
        assertNull(OpsValidator.inventoryMove(move, onHandMilli = 500))
    }

    @Test
    fun `identifier needs a listed type`() {
        assertNotNull(OpsValidator.identifier(AssignAnimalIdentifier("i1", "a1", "barcode", "NALA-01", 1)))
        assertNull(OpsValidator.identifier(AssignAnimalIdentifier("i1", "a1", "ear_tag", "NALA-01", 1)))
    }

    @Test
    fun `lot receive needs a code and quantity`() {
        assertNotNull(OpsValidator.lotReceive(ReceiveInventoryLot("l1", "item-1", "", 1, 1000)))
        assertNull(OpsValidator.lotReceive(ReceiveInventoryLot("l1", "item-1", "LOT-A", 1, 1000)))
    }

    @Test
    fun `micron stays in tenths from 80 to 500`() {
        assertNotNull(OpsValidator.micron(RecordSheepMicron("t1", "a1", null, 79, 1)))
        assertNull(OpsValidator.micron(RecordSheepMicron("t1", "a1", null, 185, 1)))
    }

    @Test
    fun `pedigree rejects a self link`() {
        assertNotNull(OpsValidator.pedigree(LinkPedigree("p1", "a1", "a1", "sire")))
        assertNull(OpsValidator.pedigree(LinkPedigree("p1", "a1", "a2", "genetic_dam")))
    }

    @Test
    fun `pack apply needs an animal or group`() {
        assertNotNull(OpsValidator.packApply(ApplyHealthPack("a1", "p1", anchorEpochDay = 1)))
        assertNull(OpsValidator.packApply(ApplyHealthPack("a1", "p1", animalId = "n1", anchorEpochDay = 1)))
    }

    @Test
    fun `days on feed cannot be negative`() {
        assertNotNull(OpsValidator.daysOnFeed(RecordCattleDaysOnFeed("d1", "g1", -1, 1)))
        assertNull(OpsValidator.daysOnFeed(RecordCattleDaysOnFeed("d1", "g1", 42, 1)))
    }

    @Test
    fun `census rejects a negative head count`() {
        assertNotNull(OpsValidator.census(RecordGroupCensus("c1", "g1", -1, 1)))
        assertNull(OpsValidator.census(RecordGroupCensus("c1", "g1", 8, 1)))
    }
}
