package dev.pipeline.routing

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.service.AiServices
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LlmRouter(
    private val ollamaBaseUrl: String = "http://localhost:11434",
    private val fastModelName: String = "qwen2:1.5b",
    private val strongModelName: String = "llama3.1:8b",
    private val complexityThreshold: Double = 0.8,
) {
    private val fastModel: ChatLanguageModel = OllamaChatModel.builder()
        .baseUrl(ollamaBaseUrl)
        .modelName(fastModelName)
        .format("json")
        .temperature(0.0)
        .build()

    private val strongModel: ChatLanguageModel = OllamaChatModel.builder()
        .baseUrl(ollamaBaseUrl)
        .modelName(strongModelName)
        .temperature(0.2)
        .build()

    private val fastAssistant: FastModelAssistant = AiServices.builder(FastModelAssistant::class.java)
        .chatLanguageModel(fastModel)
        .build()

    fun routeQuery(query: String): RoutingResult {
        println("[Router] Sending query to fast model ($fastModelName)...")

        val fastResponse = tryFastModel(query)

        // Condition C: JSON parsing failed
        if (fastResponse == null) {
            println("[Router] JSON parsing failed. Escalating to strong model ($strongModelName).")
            return RoutingResult(
                query = query,
                fastModelName = fastModelName,
                strongModelName = strongModelName,
                escalated = true,
                complexityScore = null,
                escalationReason = "C: JSON parsing failed",
                answer = askStrongModel(query),
            )
        }

        // Condition A: Complexity score too high
        if (fastResponse.complexityScore >= complexityThreshold) {
            println("[Router] Condition A: Complexity ${fastResponse.complexityScore} >= $complexityThreshold. Escalating to $strongModelName.")
            return RoutingResult(
                query = query,
                fastModelName = fastModelName,
                strongModelName = strongModelName,
                escalated = true,
                complexityScore = fastResponse.complexityScore,
                escalationReason = "A: Complexity ${fastResponse.complexityScore} >= $complexityThreshold",
                answer = askStrongModel(query),
            )
        }

        // Condition B: Lazy answer fallback
        if (query.length > MIN_QUERY_LENGTH && fastResponse.answer.length < MIN_ANSWER_LENGTH) {
            println("[Router] Condition B: Lazy answer (query ${query.length} chars > $MIN_QUERY_LENGTH, answer ${fastResponse.answer.length} chars < $MIN_ANSWER_LENGTH). Escalating to $strongModelName.")
            return RoutingResult(
                query = query,
                fastModelName = fastModelName,
                strongModelName = strongModelName,
                escalated = true,
                complexityScore = fastResponse.complexityScore,
                escalationReason = "B: Lazy Answer Fallback (Answer length ${fastResponse.answer.length} < $MIN_ANSWER_LENGTH)",
                answer = askStrongModel(query),
            )
        }

        println("[Router] Complexity ${fastResponse.complexityScore} < $complexityThreshold, answer length ${fastResponse.answer.length} chars. Using fast model answer.")
        return RoutingResult(
            query = query,
            fastModelName = fastModelName,
            strongModelName = strongModelName,
            escalated = false,
            complexityScore = fastResponse.complexityScore,
            escalationReason = null,
            answer = fastResponse.answer,
        )
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 40
        private const val MIN_ANSWER_LENGTH = 250
    }

    private fun tryFastModel(query: String): FastModelResponse? =
        try {
            val raw = fastAssistant.answer(query)
            parseResponse(raw)
        } catch (e: Exception) {
            println("[Router] Fast model error: ${e.message}")
            null
        }

    private fun parseResponse(raw: String): FastModelResponse? =
        try {
            val json = Json.parseToJsonElement(raw).jsonObject
            val answer = json["answer"]?.jsonPrimitive?.content ?: return null
            val complexityScore = json["complexityScore"]?.jsonPrimitive?.double ?: return null
            FastModelResponse(answer = answer, complexityScore = complexityScore)
        } catch (e: Exception) {
            println("[Router] JSON parse error: ${e.message}")
            null
        }

    private fun askStrongModel(query: String): String =
        strongModel.chat(query)
}
