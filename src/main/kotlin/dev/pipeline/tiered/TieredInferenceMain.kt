package dev.pipeline.tiered

import dev.pipeline.tiered.metrics.MetricsCollector
import dev.pipeline.tiered.model.TicketInput
import dev.pipeline.tiered.pipeline.FallbackLayer
import dev.pipeline.tiered.pipeline.InferencePipeline
import dev.pipeline.tiered.pipeline.MicroModelLayer
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true }

fun loadDataset(path: String): List<TicketInput> {
    val file = File(path)
    require(file.exists()) { "Dataset not found: $path" }
    return file.readLines()
        .filter { it.isNotBlank() }
        .map { json.decodeFromString(TicketInput.serializer(), it) }
}

fun runTieredInference() {
    val datasetPath = "test_dataset.jsonl"
    val csvPath = "output/inference_results.csv"

    val threshold = System.getenv("CONFIDENCE_THRESHOLD")?.toDoubleOrNull() ?: 0.75
    println("Confidence threshold: $threshold")

    val tickets = loadDataset(datasetPath)
    println("Loaded ${tickets.size} tickets from $datasetPath")

    val microLayer = MicroModelLayer()
    val fallbackLayer = FallbackLayer()
    val pipeline = InferencePipeline(microLayer, fallbackLayer, threshold)
    val metrics = MetricsCollector()

    for ((index, ticket) in tickets.withIndex()) {
        print("[${index + 1}/${tickets.size}] Ticket #${ticket.id} (${ticket.category ?: "?"})... ")
        try {
            val result = pipeline.process(ticket)
            metrics.add(result)
            println("${result.handledBy} -> ${result.finalType}, ${result.finalPriority}, ${result.finalLanguage} (${result.latencyMs}ms)")
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
        }
    }

    metrics.printReport()
    metrics.writeCsv(csvPath)
}
