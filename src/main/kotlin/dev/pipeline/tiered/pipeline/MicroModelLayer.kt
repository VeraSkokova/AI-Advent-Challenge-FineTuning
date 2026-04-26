package dev.pipeline.tiered.pipeline

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.pipeline.tiered.model.MicroClassification
import dev.pipeline.tiered.model.MicroStatus
import dev.pipeline.tiered.model.Priority
import dev.pipeline.tiered.model.TicketType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

class MicroModelLayer(
    private val model: ChatLanguageModel = OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("qwen2:1.5b")
        .format("json")
        .temperature(0.0)
        .build(),
    private val maxRetries: Int = 1,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun classify(ticketBody: String): MicroClassification {
        var lastException: Exception? = null

        repeat(maxRetries + 1) {
            try {
                val raw = model.chat(buildPrompt(ticketBody))
                val cleaned = cleanJsonResponse(raw)
                return parseAndValidate(cleaned)
            } catch (e: Exception) {
                lastException = e
            }
        }

        throw lastException ?: IllegalStateException("Micro model failed with no exception")
    }

    private fun buildPrompt(ticketBody: String): String = """
You are a support ticket classifier. Analyze the ticket below and return ONLY valid JSON with no extra text.

Classification rules:
- type: one of [Incident, Request, Problem, Change]
  * Incident = active malfunction, outage, breach, failure, disruption
  * Request = asking for information, documentation, instructions, compatibility, features
  * Problem = issue needing diagnosis, unclear root cause, repeated unsuccessful troubleshooting
  * Change = request to modify setup, strategy, configuration, integration, or environment
- priority: one of [high, medium, low]
  * high = security breach, data loss, severe outage, urgent keywords
  * medium = service degradation, moderate impact, standard issues
  * low = informational queries, no urgency
- language: ISO 639-1 code (e.g. "en", "de")
- confidence: float between 0.0 and 1.0 — how certain you are about your classification
- status: "OK" if you are confident, "UNSURE" if any field is uncertain

Confidence and status rules:
- For standard, detailed tickets with clear intent, you MUST return status="OK" and confidence=0.9 or 1.0. Do not artificially lower your confidence.
- If the ticket is shorter than 2 sentences (e.g. "Problem leading to access difficulties"), return status="UNSURE" and confidence=0.5.
- If the ticket is highly ambiguous (e.g. could be both Incident and Problem, or priority is impossible to guess), return status="UNSURE" and confidence=0.6.

Return exactly this JSON shape:
{"type": "...", "priority": "...", "language": "...", "confidence": 0.0, "status": "OK"}

Ticket:
$ticketBody
""".trimIndent()

    private fun parseAndValidate(raw: String): MicroClassification {
        val obj = json.decodeFromString(JsonObject.serializer(), raw.trim())

        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing 'type' field")
        val type = TicketType.fromStringOrNull(typeStr)
            ?: throw IllegalArgumentException("Invalid type: $typeStr")

        val priorityStr = obj["priority"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing 'priority' field")
        val priority = Priority.fromStringOrNull(priorityStr)
            ?: throw IllegalArgumentException("Invalid priority: $priorityStr")

        val language = obj["language"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing 'language' field")

        val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull
            ?: throw IllegalArgumentException("Missing or invalid 'confidence' field")
        if (confidence < 0.0 || confidence > 1.0) {
            throw IllegalArgumentException("Confidence out of range: $confidence")
        }

        val statusStr = obj["status"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Missing 'status' field")
        val status = MicroStatus.fromStringOrNull(statusStr)
            ?: throw IllegalArgumentException("Invalid status: $statusStr")

        return MicroClassification(
            type = type,
            priority = priority,
            language = language.lowercase(),
            confidence = confidence,
            status = status,
        )
    }
}

private fun cleanJsonResponse(raw: String): String {
    var cleaned = raw.trim()
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.removePrefix("```json")
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.removePrefix("```")
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.removeSuffix("```")
    }
    return cleaned.trim()
}
