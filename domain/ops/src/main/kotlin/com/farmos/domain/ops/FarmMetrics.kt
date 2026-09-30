package com.farmos.domain.ops

/**
 * What a metric means (owner decision D-026, resolution R10): how it is computed, in what unit, over which
 * period and which records. A metric runs on an exhaustive local query, never on a capped list.
 */
data class MetricDefinition(
    val id: String,
    val name: String,
    val formula: String,
    val unit: String,
    val period: String,
    val scope: String,
)

/**
 * A metric's value with its completeness: [included] records had what the formula needs and [missing]
 * did not. A missing value is never counted as zero (R3), so a partial figure says so.
 */
data class MetricResult(val definition: MetricDefinition, val value: Long, val included: Int, val missing: Int) {
    init {
        require(included >= 0 && missing >= 0) { "Record counts cannot be negative" }
    }

    val complete: Boolean get() = missing == 0

    /** "Complete", or how many records the figure leaves out. */
    val completeness: String get() = if (complete) "Complete" else "Partial: $missing of ${included + missing} record(s) have no value"

    companion object {
        /** Sums the known values; unknown ones are counted as missing, not as zero. */
        fun sumOfKnown(definition: MetricDefinition, values: List<Long?>): MetricResult =
            MetricResult(definition, values.filterNotNull().sum(), values.count { it != null }, values.count { it == null })

        /** Counts records; a count over an exhaustive query is always complete. */
        fun count(definition: MetricDefinition, count: Int): MetricResult = MetricResult(definition, count.toLong(), count, 0)
    }
}

/**
 * RFC 4180 CSV for device exports. A cell that a spreadsheet would run as a formula (starting with =, +, -, @,
 * tab or carriage return) is prefixed with an apostrophe so an exported record can never execute.
 */
object FarmCsv {
    fun write(header: List<String>, rows: List<List<String?>>): String {
        require(header.isNotEmpty()) { "A CSV export needs columns" }
        rows.forEachIndexed { index, row -> require(row.size == header.size) { "Row ${index + 1} has ${row.size} cells for ${header.size} columns" } }
        return buildString {
            (listOf(header) + rows).forEach { row ->
                append(row.joinToString(",") { cell(it) })
                append("\r\n")
            }
        }
    }

    fun cell(value: String?): String {
        if (value == null) return ""
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
