package dev.pipeline.cli

import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import dev.pipeline.baseline.runBaselineEvaluation
import dev.pipeline.confidence.runDay7Benchmark
import dev.pipeline.inference.runInferenceComparison
import dev.pipeline.routing.runRoutingDemo
import dev.pipeline.tiered.runTieredInference
import dev.pipeline.validation.runValidation
import kotlinx.coroutines.runBlocking

fun main() {
    println("=== LLM Fine-Tuning Pipeline ===")

    while (true) {
        println()
        println("Choose an option:")
        println("  1) Validate dataset (Day 6)")
        println("  2) Run Baseline – Ollama (Day 6)")
        println("  3) Run Day 7 Confidence & Redundancy Benchmark")
        println("  4) Run Day 8 LLM Routing Demo (LangChain4j)")
        println("  5) Run Day 9 Inference Comparison (Monolithic vs Multi-Stage)")
        println("  6) Run Day 10 Tiered Inference (Micro + Fallback)")
        println("  0) Exit")
        print("> ")

        when (readlnOrNull()?.trim()) {
            "1" -> {
                println()
                runValidation()
            }

            "2" -> {
                print("Enter model name [llama3]: ")
                val model = readlnOrNull()?.trim()?.ifBlank { null } ?: "llama3"
                println()
                runBlocking {
                    runBaselineEvaluation(model)
                }
            }

            "3" -> {
                println()
                val config = OpenAIConfig(
                    token = "ollama",
                    host = OpenAIHost(baseUrl = "http://localhost:11434/v1/"),
                    logging = LoggingConfig(logLevel = LogLevel.None),
                )
                val client = OpenAI(config)
                runBlocking {
                    runDay7Benchmark(client)
                }
            }

            "4" -> {
                println()
                runRoutingDemo()
            }

            "5" -> {
                println()
                runInferenceComparison()
            }

            "6" -> {
                println()
                runTieredInference()
            }

            "0" -> {
                println("Goodbye.")
                return
            }

            else -> println("Invalid option. Please enter 0-6.")
        }
    }
}
