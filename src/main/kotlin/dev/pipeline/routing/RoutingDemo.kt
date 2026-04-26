package dev.pipeline.routing

import java.io.File

private val DEFAULT_PROMPTS = listOf(
    "What is 2+2?",
    "Name the capital of France.",
    "What is the boiling point of water in Celsius?",
    "Who wrote the play Romeo and Juliet?",
    "Explain the math behind quantum entanglement.",
    "Write a production-ready Kubernetes deployment YAML for a highly available Redis cluster with persistent volumes, pod anti-affinity, and strict memory limits.",
    "Solve this equation step-by-step and provide the exact roots: 3x^3 - 7x^2 + 2x = 0. Do not skip any mathematical steps.",
    "Write a detailed 300-word explanation of how a combustion engine works, but every single sentence must end with the letter 's'.",
    "Explain the difference between covariance and contravariance in Kotlin generics with code examples.",
    "Describe the architectural differences between Transformer and Mamba (State Space Models).",
)

fun runRoutingDemo() {
    val promptsFile = File("prompts.txt")
    if (!promptsFile.exists()) {
        promptsFile.writeText(DEFAULT_PROMPTS.joinToString(separator = "\n"))
        println("[Demo] Created prompts.txt with ${DEFAULT_PROMPTS.size} default queries.")
    }

    val queries = promptsFile.readLines().filter { it.isNotBlank() }
    println("[Demo] Loaded ${queries.size} queries from prompts.txt")

    val outputFile = File("routing_results.md")
    outputFile.writeText("# LLM Routing Results\n\n")
    println("[Demo] Writing results to ${outputFile.name}")

    val router = LlmRouter()

    for ((index, query) in queries.withIndex()) {
        println()
        println("=".repeat(60))
        println("Query ${index + 1}/${queries.size}: $query")
        println("=".repeat(60))
        val result = router.routeQuery(query)
        println("[Result] ${result.answer}")
        outputFile.appendText(result.toMarkdown())
    }

    println()
    println("[Demo] Results saved to ${outputFile.absolutePath}")
}
