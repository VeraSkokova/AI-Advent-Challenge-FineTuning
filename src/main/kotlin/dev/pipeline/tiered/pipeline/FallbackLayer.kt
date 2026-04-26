package dev.pipeline.tiered.pipeline

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.pipeline.tiered.model.FinalClassification
import dev.pipeline.tiered.model.Priority
import dev.pipeline.tiered.model.TicketType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class FallbackLayer(
    private val model: ChatLanguageModel = OllamaChatModel.builder()
        .baseUrl("http://localhost:11434")
        .modelName("llama3.1:8b")
        .format("json")
        .temperature(0.0)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun classify(ticketBody: String): FinalClassification {
        val raw = model.chat(buildPrompt(ticketBody))
        val cleaned = cleanJsonResponse(raw)
        return parseAndValidate(cleaned)
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

Return exactly this JSON shape:
{"type": "...", "priority": "...", "language": "..."}

Ticket:
$ticketBody
""".trimIndent()

    private fun parseAndValidate(raw: String): FinalClassification {
        val obj = json.decodeFromString(JsonObject.serializer(), raw.trim())

        val typeStr = obj["type"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Fallback: missing 'type'")
        val type = TicketType.fromStringOrNull(typeStr)
            ?: throw IllegalArgumentException("Fallback: invalid type: $typeStr")

        val priorityStr = obj["priority"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Fallback: missing 'priority'")
        val priority = Priority.fromStringOrNull(priorityStr)
            ?: throw IllegalArgumentException("Fallback: invalid priority: $priorityStr")

        val language = obj["language"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Fallback: missing 'language'")

        return FinalClassification(
            type = type,
            priority = priority,
            language = language.lowercase(),
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
