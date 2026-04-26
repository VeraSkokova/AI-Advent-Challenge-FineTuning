package dev.pipeline.tiered.metrics

import dev.pipeline.tiered.model.HandlerType
import dev.pipeline.tiered.model.InferenceResult
import java.io.File

class MetricsCollector {

    private val results = mutableListOf<InferenceResult>()

    fun add(result: InferenceResult) {
        results.add(result)
    }

    fun printReport() {
        val total = results.size
        val microCount = results.count { it.handledBy == HandlerType.MICRO }
        val fallbackCount = results.count { it.handledBy == HandlerType.FALLBACK }

        println()
        println("=" .repeat(60))
        println("TIERED INFERENCE REPORT")
        println("=".repeat(60))
        println("Total tickets:       $total")
        println("Handled by MICRO:    $microCount (${pct(microCount, total)})")
        println("Handled by FALLBACK: $fallbackCount (${pct(fallbackCount, total)})")
        println()

        println("--- Latency ---")
        println("Avg total latency:    ${avgLong(results.map { it.latencyMs })} ms")
        val microLatencies = results.mapNotNull { it.microLatencyMs }
        if (microLatencies.isNotEmpty()) {
            println("Avg micro latency:    ${avgLong(microLatencies)} ms")
        }
        val fallbackLatencies = results.mapNotNull { it.fallbackLatencyMs }
        if (fallbackLatencies.isNotEmpty()) {
            println("Avg fallback latency: ${avgLong(fallbackLatencies)} ms")
        }
        println()

        println("--- Accuracy (overall) ---")
        printAccuracy(results)

        val categories = results.mapNotNull { it.category }.distinct().sorted()
        for (cat in categories) {
            val subset = results.filter { it.category == cat }
            println("--- Accuracy ($cat, n=${subset.size}) ---")
            printAccuracy(subset)
        }

        println()
        printMistakes()
    }

    private fun printAccuracy(subset: List<InferenceResult>) {
        val withGt = subset.filter { it.isCorrectType != null }
        if (withGt.isEmpty()) {
            println("  No ground truth available")
            return
        }
        val n = withGt.size
        println("  Type correct:     ${withGt.count { it.isCorrectType == true }}/$n (${pct(withGt.count { it.isCorrectType == true }, n)})")
        println("  Priority correct: ${withGt.count { it.isCorrectPriority == true }}/$n (${pct(withGt.count { it.isCorrectPriority == true }, n)})")
        println("  Language correct: ${withGt.count { it.isCorrectLanguage == true }}/$n (${pct(withGt.count { it.isCorrectLanguage == true }, n)})")
        println("  All correct:      ${withGt.count { it.allCorrect == true }}/$n (${pct(withGt.count { it.allCorrect == true }, n)})")
        println()
    }

    fun writeCsv(path: String) {
        val file = File(path)
        file.parentFile?.mkdirs()

        val header = listOf(
            "ticket_id", "category", "handled_by",
            "final_type", "final_priority", "final_language",
            "micro_confidence", "micro_status",
            "total_latency_ms", "micro_latency_ms", "fallback_latency_ms",
            "gt_type", "gt_priority", "gt_language",
            "is_correct_type", "is_correct_priority", "is_correct_language", "all_correct",
        ).joinToString(",")

        val rows = results.map { r ->
            listOf(
                r.ticketId,
                r.category ?: "",
                r.handledBy,
                r.finalType,
                r.finalPriority,
                r.finalLanguage,
                r.microConfidence?.let { "%.3f".format(it) } ?: "",
                r.microStatus ?: "",
                r.latencyMs,
                r.microLatencyMs ?: "",
                r.fallbackLatencyMs ?: "",
                r.isCorrectType?.let { if (it) r.finalType else "GT_MISMATCH" } ?: "",
                r.isCorrectPriority?.let { if (it) r.finalPriority else "GT_MISMATCH" } ?: "",
                r.isCorrectLanguage?.let { if (it) r.finalLanguage else "GT_MISMATCH" } ?: "",
                r.isCorrectType ?: "",
                r.isCorrectPriority ?: "",
                r.isCorrectLanguage ?: "",
                r.allCorrect ?: "",
            ).joinToString(",")
        }

        file.writeText(header + "\n" + rows.joinToString("\n") + "\n")
        println("CSV written to: $path")
    }

    private fun printMistakes() {
        val mistakes = results.filter { it.allCorrect == false }.take(5)
        if (mistakes.isEmpty()) return

        println("--- Sample mistakes (up to 5) ---")
        for (m in mistakes) {
            println("  Ticket #${m.ticketId} [${m.category}] handled by ${m.handledBy}")
            println("    Predicted: type=${m.finalType}, priority=${m.finalPriority}, lang=${m.finalLanguage}")
            val corrections = mutableListOf<String>()
            if (m.isCorrectType == false) corrections.add("type")
            if (m.isCorrectPriority == false) corrections.add("priority")
            if (m.isCorrectLanguage == false) corrections.add("language")
            println("    Wrong fields: ${corrections.joinToString(", ")}")
            println()
        }
    }

    private fun pct(count: Int, total: Int): String =
        if (total == 0) "0.0%" else "%.1f%%".format(count * 100.0 / total)

    private fun avgLong(values: List<Long>): Long =
        if (values.isEmpty()) 0L else values.sum() / values.size
}
