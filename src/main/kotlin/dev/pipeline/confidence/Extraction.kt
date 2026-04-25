package dev.pipeline.confidence

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

private val SYSTEM_PROMPT = """
You are a structured data extraction assistant. Analyze the customer review and return a JSON object with exactly these fields:
- "category": one of "REFUND", "TECHNICAL_ISSUE", "COMPLIMENT", or "UNKNOWN"
- "ticketId": the ticket or order ID mentioned (e.g. "ORD-1234"), or null if none is clearly identified
- "summary": a one-sentence summary of the customer's issue

Return ONLY valid JSON. No markdown, no explanation.
Example: {"category":"TECHNICAL_ISSUE","ticketId":null,"summary":"User reports crashes during peak usage."}
""".trimIndent()

suspend fun extractData(client: OpenAI, text: String): Pair<ExtractedTicket?, Int> {
    return try {
        val request = ChatCompletionRequest(
            model = ModelId("llama3.1:8b"),
            messages = listOf(
                ChatMessage.System(SYSTEM_PROMPT),
                ChatMessage.User(text),
            ),
            responseFormat = ChatResponseFormat.JsonObject,
        )
        val completion: ChatCompletion = client.chatCompletion(request)
        val content = completion.choices.firstOrNull()?.message?.content.orEmpty()
        val tokens = completion.usage?.totalTokens ?: 0

        val ticket = json.decodeFromString<ExtractedTicket>(content)
        Pair(ticket, tokens)
    } catch (e: SerializationException) {
        println("    [WARN] Serialization error: ${e.message}")
        Pair(null, 0)
    } catch (e: Exception) {
        println("    [WARN] Extraction error: ${e.message}")
        Pair(null, 0)
    }
}

suspend fun extractWithRedundancy(client: OpenAI, text: String): Pair<ExtractedTicket?, Int> =
    coroutineScope {
        val calls = List(3) { async { extractData(client, text) } }
        val results = calls.map { it.await() }
        val totalTokens = results.sumOf { it.second }
        val tickets = results.mapNotNull { it.first }

        // Majority voting: at least 2 out of 3 must agree on category + ticketId
        val majority = tickets
            .groupBy { it.category to it.ticketId }
            .entries
            .firstOrNull { it.value.size >= 2 }

        if (majority != null) {
            Pair(majority.value.first(), totalTokens)
        } else {
            Pair(null, totalTokens)
        }
    }
