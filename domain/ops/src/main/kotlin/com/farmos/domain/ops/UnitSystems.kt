package com.farmos.domain.ops

import java.math.BigDecimal
import java.math.MathContext

/**
 * FOS-ADMIN-010 — canonical unit systems and display conversions.
 *
 * Canonical STORED units never change: weight is stored in grams, volume in
 * millilitres, length in millimetres. Conversions happen ONLY at the
 * presentation/input boundary through this pure object, using exact
 * BigDecimal arithmetic — never floating point. The validator
 * ([OpsValidator.unitPreference]) and the UI both read the unit sets from
 * here, so there is exactly one source of truth.
 */
object UnitSystems {
    data class UnitDef(val code: String, val label: String, val toCanonical: BigDecimal)
    data class QuantityKind(val kind: String, val label: String, val canonicalUnit: String, val units: List<UnitDef>)

    val KINDS: List<QuantityKind> = listOf(
        QuantityKind(
            "weight", "Weight", "g",
            listOf(
                UnitDef("g", "grams", BigDecimal.ONE),
                UnitDef("kg", "kilograms", BigDecimal("1000")),
                UnitDef("lb", "pounds", BigDecimal("453.59237")),
                UnitDef("oz", "ounces", BigDecimal("28.349523125")),
            ),
        ),
        QuantityKind(
            "volume", "Volume", "ml",
            listOf(
                UnitDef("ml", "millilitres", BigDecimal.ONE),
                UnitDef("L", "litres", BigDecimal("1000")),
                UnitDef("gal", "gallons (US)", BigDecimal("3785.411784")),
                UnitDef("floz", "fluid ounces (US)", BigDecimal("29.5735295625")),
            ),
        ),
        QuantityKind(
            "length", "Length", "mm",
            listOf(
                UnitDef("mm", "millimetres", BigDecimal.ONE),
                UnitDef("cm", "centimetres", BigDecimal("10")),
                UnitDef("m", "metres", BigDecimal("1000")),
                UnitDef("in", "inches", BigDecimal("25.4")),
                UnitDef("ft", "feet", BigDecimal("304.8")),
            ),
        ),
    )

    fun kindOf(kind: String): QuantityKind? = KINDS.firstOrNull { it.kind == kind }

    fun unitsFor(kind: String): Set<String> = kindOf(kind)?.units?.map { it.code }.orEmpty().toSet()

    fun isValid(kind: String, unit: String): Boolean = unit in unitsFor(kind)

    private fun defOf(kind: String, unit: String): UnitDef =
        kindOf(kind)?.units?.firstOrNull { it.code == unit }
            ?: throw IllegalArgumentException("Unknown unit $unit for kind $kind")

    /** Canonical stored value -> display value. */
    fun toDisplay(kind: String, unit: String, canonicalValue: BigDecimal): BigDecimal =
        canonicalValue.divide(defOf(kind, unit).toCanonical, MathContext.DECIMAL128).stripTrailingZeros()

    /** Display input -> canonical stored value. */
    fun toCanonical(kind: String, unit: String, displayValue: BigDecimal): BigDecimal =
        displayValue.multiply(defOf(kind, unit).toCanonical, MathContext.DECIMAL128).stripTrailingZeros()

    /** Canonical stored value -> "value unit" display string. */
    fun format(kind: String, unit: String, canonicalValue: BigDecimal): String =
        "${toDisplay(kind, unit, canonicalValue).toPlainString()} $unit"
}
