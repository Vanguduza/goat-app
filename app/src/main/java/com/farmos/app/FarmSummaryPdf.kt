package com.farmos.app

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.farmos.domain.ops.MetricResult
import java.io.OutputStream
import java.time.LocalDate

/** One printed line of the farm summary; a heading is set bold. */
internal data class SummaryLine(val text: String, val heading: Boolean = false)

/**
 * The farm summary (D-026) as printed lines: every metric shown on Reports, each with its value, how it is
 * worked out, its period and scope, and its completeness, so a printed figure carries the same truth as the
 * screen. A metric's lines are kept together on one page.
 */
internal fun farmSummaryPages(metrics: List<MetricResult>, linesPerPage: Int = SUMMARY_LINES_PER_PAGE, width: Int = SUMMARY_CHARS_PER_LINE): List<List<SummaryLine>> {
    val blocks = if (metrics.isEmpty()) {
        listOf(listOf(SummaryLine("No animals are recorded on this device yet.")))
    } else {
        metrics.map { result ->
            listOf(SummaryLine(result.definition.name, heading = true)) + listOf(
                metricValueText(result),
                "How: ${result.definition.formula}",
                "Period: ${result.definition.period} · Scope: ${result.definition.scope}",
                result.completeness,
            ).flatMap { wrapSummaryText(it, width) }.map { SummaryLine(it) }
        }
    }
    val pages = mutableListOf<MutableList<SummaryLine>>(mutableListOf())
    blocks.forEach { block ->
        val page = pages.last()
        val needed = block.size + if (page.isEmpty()) 0 else 1
        if (page.isNotEmpty() && page.size + needed > linesPerPage) pages += mutableListOf<SummaryLine>()
        val current = pages.last()
        if (current.isNotEmpty()) current += SummaryLine("")
        current += block
    }
    return pages
}

/** Word-wraps [text] to [width] characters; a word longer than a line is broken across lines. */
internal fun wrapSummaryText(text: String, width: Int): List<String> {
    val lines = mutableListOf<String>()
    var line = StringBuilder()
    text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
        var rest = word
        while (rest.isNotEmpty()) {
            val room = if (line.isEmpty()) width else width - line.length - 1
            when {
                rest.length <= room -> {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(rest)
                    rest = ""
                }
                line.isNotEmpty() -> {
                    lines += line.toString()
                    line = StringBuilder()
                }
                else -> {
                    lines += rest.take(width)
                    rest = rest.drop(width)
                }
            }
        }
    }
    if (line.isNotEmpty() || lines.isEmpty()) lines += line.toString()
    return lines
}

/** Writes the farm summary as an A4 PDF set in Inter, each page headed with the date and page count. */
internal fun writeFarmSummaryPdf(context: Context, metrics: List<MetricResult>, generatedOn: LocalDate, out: OutputStream) {
    val inter = runCatching { ResourcesCompat.getFont(context, com.farmos.core.design.R.font.inter_variable) }.getOrNull() ?: Typeface.SANS_SERIF
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = inter; textSize = BODY_SIZE; color = Color.BLACK }
    val bold = Paint(body).apply { typeface = Typeface.create(inter, Typeface.BOLD) }
    val pages = farmSummaryPages(metrics)
    val document = PdfDocument()
    try {
        pages.forEachIndexed { index, lines ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create())
            var y = MARGIN + BODY_SIZE
            page.canvas.drawText("Farm summary · $generatedOn · Page ${index + 1} of ${pages.size}", MARGIN, y, bold)
            y += LINE_HEIGHT * 2
            lines.forEach { line ->
                if (line.text.isNotEmpty()) page.canvas.drawText(line.text, MARGIN, y, if (line.heading) bold else body)
                y += LINE_HEIGHT
            }
            document.finishPage(page)
        }
        document.writeTo(out)
    } finally {
        document.close()
    }
}

private const val A4_WIDTH = 595
private const val A4_HEIGHT = 842
private const val MARGIN = 48f
private const val BODY_SIZE = 10f
private const val LINE_HEIGHT = 14f

/** Lines below the page header within the margins: (842 - 2 × 48 - 3 × 14) / 14. */
internal const val SUMMARY_LINES_PER_PAGE = 50

/** Characters that fit the 499 pt text width at 10 pt Inter, with room for wide glyphs. */
internal const val SUMMARY_CHARS_PER_LINE = 80
