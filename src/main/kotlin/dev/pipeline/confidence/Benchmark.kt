package dev.pipeline.confidence

import com.aallam.openai.client.OpenAI
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.measureTimeMillis

private val json = Json { ignoreUnknownKeys = true }

suspend fun runDay7Benchmark(client: OpenAI) {
    val datasetFile = File("day7_dataset.jsonl")
    if (!datasetFile.exists()) {
        println("ERROR: day7_dataset.jsonl not found in ${datasetFile.absolutePath}")
        return
    }

    val items = datasetFile.readLines()
        .filter { it.isNotBlank() }
        .map { json.decodeFromString<DatasetItem>(it) }

    File("day7_benchmark_results.txt").printWriter().use { writer ->
        fun log(message: String) {
            println(message)
            writer.println(message)
        }

        log("=== Day 7: Confidence & Redundancy Benchmark ===")
        log("Loaded ${items.size} items from dataset.")
        log("")

        var rejectedCount = 0
        var totalTokens = 0
        val latencies = mutableListOf<Long>()

        for (item in items) {
            print("Processing ${item.id} [${item.expected_difficulty}]... ")
            writer.print("Processing ${item.id} [${item.expected_difficulty}]... ")

            var result: Pair<ExtractedTicket?, Int>
            val elapsed = measureTimeMillis {
                result = extractWithRedundancy(client, item.text)
            }

            val (ticket, tokens) = result
            totalTokens += tokens
            latencies += elapsed

            if (ticket == null || ticket.category == TicketCategory.UNKNOWN) {
                rejectedCount++
            }

            val categoryStr = ticket?.category?.name ?: "REJECTED"
            val ticketIdStr = ticket?.ticketId ?: "none"
            val stats = "Category=$categoryStr | TicketId=$ticketIdStr | ${elapsed}ms | ${tokens} tokens"
            println(stats)
            writer.println(stats)
        }

        log("")
        log("=== Summary ===")
        log("Total Processed : ${items.size}")
        log("Total Rejected  : $rejectedCount")
        log("Total Tokens    : $totalTokens")
        log("Avg Latency     : ${if (latencies.isNotEmpty()) latencies.average().toLong() else 0}ms")
    }

    println("Results saved to day7_benchmark_results.txt")
}
