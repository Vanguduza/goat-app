package com.farmos.app

import java.math.BigDecimal

private val plainDecimal = Regex("""[+-]?(?:\d+(?:\.\d*)?|\.\d+)""")

/**
 * Converts human decimal input into the exact integer storage unit.
 *
 * This deliberately avoids Double/Float so values such as 18.10 cannot be rounded
 * or truncated while crossing a money, quantity, weight, feed, or water boundary.
 */
internal fun String.toScaledLongExact(scale: Int, fieldName: String): Long {
    require(scale >= 0) { "Scale must be non-negative" }
    val normalized = trim().replace(',', '.')
    require(normalized.isNotEmpty()) { "$fieldName is required" }
    require(plainDecimal.matches(normalized)) { "$fieldName must be a plain decimal number" }
    return try {
        BigDecimal(normalized)
            .movePointRight(scale)
            .longValueExact()
    } catch (failure: ArithmeticException) {
        throw IllegalArgumentException(
            "$fieldName has too many decimal places or is outside the supported range",
            failure,
        )
    } catch (failure: NumberFormatException) {
        throw IllegalArgumentException("$fieldName must be a valid decimal number", failure)
    }
}
