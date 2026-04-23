package dev.pipeline.cli

import dev.pipeline.baseline.runBaselineEvaluation
import dev.pipeline.validation.runValidation
import kotlinx.coroutines.runBlocking

fun main() {
    println("=== LLM Fine-Tuning Pipeline ===")

    while (true) {
        println()
        println("Choose an option:")
        println("  1) Validate dataset")
        println("  2) Run Baseline (Ollama)")
        println("  3) OpenAI Fine-Tuning (mock run)")
        println("  4) Exit")
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
                println("  [Mock] OpenAI Fine-Tuning client initialized.")
                println("  [Mock] Would upload train_tickets.jsonl, create job, and poll status.")
                println("  No real API call was made.")
            }

            "4" -> {
                println("Goodbye.")
                return
            }

            else -> println("Invalid option. Please enter 1-4.")
        }
    }
}
