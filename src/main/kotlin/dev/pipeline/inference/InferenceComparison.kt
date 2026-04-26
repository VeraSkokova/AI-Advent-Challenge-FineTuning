package dev.pipeline.inference

import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.system.measureTimeMillis

// ── Data classes ────────────────────────────────────────────────────────────────

@Serializable
data class MonolithicResult(
    val summary: String = "",
    val type: String = "",
    @SerialName("draft_response") val draftResponse: String = "",
)

@Serializable
data class ClassificationResult(
    val type: String = "",
    val tags: List<String> = emptyList(),
)

data class ApproachMetrics(
    val name: String,
    val latencies: MutableList<Long> = mutableListOf(),
    var successes: Int = 0,
    var parseErrors: Int = 0,
) {
    val total get() = successes + parseErrors
    val avgLatency get() = if (latencies.isEmpty()) 0L else latencies.average().toLong()
    val successRate get() = if (total == 0) 0.0 else successes.toDouble() / total * 100
}

// ── Dual logger ─────────────────────────────────────────────────────────────────

class DualLogger(logFileName: String) {
    private val logFile = File(logFileName)

    init {
        logFile.writeText("") // clear previous run
    }

    fun log(message: String) {
        println(message)
        logFile.appendText(message + "\n")
    }
}

// ── Prompts ─────────────────────────────────────────────────────────────────────

private fun monolithicPrompt(ticketBody: String): String = """
You are a support ticket analyzer. Analyze the following ticket and respond with ONLY valid JSON.

Ticket:
\"\"\"
$ticketBody
\"\"\"

Respond with this exact JSON structure (no markdown, no extra text):
{"summary": "<brief summary of the issue>", "type": "<one of: Incident, Request, Problem>", "draft_response": "<professional support response>"}
""".trimIndent()

private fun stage1ExtractionPrompt(ticketBody: String): String = """
You are a support ticket analyst. Read the ticket below and extract exactly 3 bullet points in plain text:
- Core Issue: <what the user needs>
- System: <system or product mentioned>
- Sentiment: <positive, neutral, or negative>

Ticket:
\"\"\"
$ticketBody
\"\"\"

Respond with ONLY the 3 bullet points, no other text.
""".trimIndent()

private fun stage2ClassificationPrompt(bulletPoints: String): String = """
You are a classifier. Based on the extracted information below, respond with ONLY valid JSON.

Extracted info:
$bulletPoints

Classify the issue type as exactly one of: "Incident", "Request", or "Problem".
Also extract up to 3 relevant tags as short keywords.

Respond with this exact JSON structure (no markdown, no extra text):
{"type": "<Incident|Request|Problem>", "tags": ["tag1", "tag2"]}
""".trimIndent()

private fun stage3DraftPrompt(ticketBody: String, classification: ClassificationResult): String = """
You are a professional support agent. Draft a polite, concise response to the customer ticket below.

Ticket:
\"\"\"
$ticketBody
\"\"\"

Context:
- Issue type: ${classification.type}
- Tags: ${classification.tags.joinToString(", ")}

Respond with ONLY the draft response text, no JSON, no markdown.
""".trimIndent()

// ── Core logic ──────────────────────────────────────────────────────────────────

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun runInferenceComparison() {
    val logger = DualLogger("inference_comparison.log")
    logger.log("=" .repeat(70))
    logger.log("  Monolithic vs Multi-Stage Inference Comparison")
    logger.log("=".repeat(70))

    // Load data
    val dataFile = File("train_tickets.jsonl")
    if (!dataFile.exists()) {
        logger.log("[ERROR] train_tickets.jsonl not found in project root.")
        return
    }

    val ticketBodies = dataFile.useLines { lines ->
        lines.take(10).mapNotNull { line ->
            try {
                val root = Json.parseToJsonElement(line).jsonObject
                val messages = root["messages"]?.jsonArray ?: return@mapNotNull null
                messages.firstOrNull { msg ->
                    msg.jsonObject["role"]?.jsonPrimitive?.content == "user"
                }?.jsonObject?.get("content")?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }
        }.toList()
    }

    logger.log("[Data] Loaded ${ticketBodies.size} tickets from train_tickets.jsonl")
    if (ticketBodies.isEmpty()) {
        logger.log("[ERROR] No valid tickets found. Aborting.")
        return
    }

    // Initialize model
    logger.log("[Model] Initializing OllamaChatModel (llama3.1:8b)...")
    val model = OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("llama3.1:8b")
        .temperature(0.2)
        .timeout(java.time.Duration.ofSeconds(120))
        .build()

    val jsonModel = OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("llama3.1:8b")
        .format("json")
        .temperature(0.2)
        .timeout(java.time.Duration.ofSeconds(120))
        .build()

    val metricsA = ApproachMetrics("Monolithic")
    val metricsB = ApproachMetrics("Multi-Stage")

    // Process each ticket
    for ((index, ticketBody) in ticketBodies.withIndex()) {
        val ticketNum = index + 1
        logger.log("")
        logger.log("-".repeat(70))
        logger.log("Ticket $ticketNum / ${ticketBodies.size}")
        logger.log("-".repeat(70))
        logger.log("[Ticket] ${ticketBody.take(100)}...")

        // ── Approach A: Monolithic ──────────────────────────────────────────
        logger.log("")
        logger.log("[A] Monolithic Inference...")
        var monolithicLatency: Long
        val monolithicRaw: String
        monolithicLatency = measureTimeMillis {
            monolithicRaw = jsonModel.chat(monolithicPrompt(ticketBody))
        }
        metricsA.latencies.add(monolithicLatency)

        try {
            val result = lenientJson.decodeFromString<MonolithicResult>(monolithicRaw)
            metricsA.successes++
            logger.log("[A] Summary  : ${result.summary.take(80)}")
            logger.log("[A] Type     : ${result.type}")
            logger.log("[A] Draft    : ${result.draftResponse.take(80)}...")
        } catch (e: Exception) {
            metricsA.parseErrors++
            logger.log("[A] JSON PARSE ERROR: ${e.message}")
            logger.log("[A] Raw output: ${monolithicRaw.take(200)}")
        }
        logger.log("[A] Latency  : ${monolithicLatency}ms")

        // ── Approach B: Multi-Stage ─────────────────────────────────────────
        logger.log("")
        logger.log("[B] Multi-Stage Inference...")
        var multiStageLatency: Long
        multiStageLatency = measureTimeMillis {
            try {
                // Stage 1: Extraction (plain text)
                logger.log("[B][Stage 1] Extracting bullet points...")
                val bulletPoints = model.chat(stage1ExtractionPrompt(ticketBody))
                logger.log("[B][Stage 1] $bulletPoints")

                // Stage 2: Classification (JSON)
                logger.log("[B][Stage 2] Classifying...")
                val classificationRaw = jsonModel.chat(stage2ClassificationPrompt(bulletPoints))
                val classification = lenientJson.decodeFromString<ClassificationResult>(classificationRaw)
                logger.log("[B][Stage 2] Type: ${classification.type}, Tags: ${classification.tags}")

                // Stage 3: Drafting (plain text)
                logger.log("[B][Stage 3] Drafting response...")
                val draft = model.chat(stage3DraftPrompt(ticketBody, classification))
                logger.log("[B][Stage 3] Draft: ${draft.take(80)}...")

                metricsB.successes++
            } catch (e: Exception) {
                metricsB.parseErrors++
                logger.log("[B] PARSE ERROR at Stage 2: ${e.message}")
            }
        }
        metricsB.latencies.add(multiStageLatency)
        logger.log("[B] Latency  : ${multiStageLatency}ms")
    }

    // ── Summary Report ──────────────────────────────────────────────────────
    logger.log("")
    logger.log("=".repeat(70))
    logger.log("  SUMMARY REPORT")
    logger.log("=".repeat(70))
    logger.log("Total tickets processed: ${ticketBodies.size}")
    logger.log("")
    logger.log("Approach A (Monolithic):")
    logger.log("  Successes    : ${metricsA.successes}")
    logger.log("  Parse errors : ${metricsA.parseErrors}")
    logger.log("  Success rate : ${"%.1f".format(metricsA.successRate)}%")
    logger.log("  Avg latency  : ${metricsA.avgLatency}ms")
    logger.log("")
    logger.log("Approach B (Multi-Stage):")
    logger.log("  Successes    : ${metricsB.successes}")
    logger.log("  Parse errors : ${metricsB.parseErrors}")
    logger.log("  Success rate : ${"%.1f".format(metricsB.successRate)}%")
    logger.log("  Avg latency  : ${metricsB.avgLatency}ms")
    logger.log("=".repeat(70))
    logger.log("[Done] Full log saved to inference_comparison.log")
}
