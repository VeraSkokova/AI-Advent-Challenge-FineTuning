package dev.pipeline.baseline

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import kotlin.time.Duration.Companion.seconds

data class BaselineResult(
    val userQuery: String,
    val expectedAnswer: String,
    val ollamaAnswer: String,
)

suspend fun runBaselineEvaluation(modelName: String = "llama3") {
    val evalFile = File("eval_tickets.jsonl")
    if (!evalFile.exists()) {
        println("eval_tickets.jsonl not found.")
        return
    }

    val examples = evalFile.useLines { lines ->
        lines.filter { it.isNotBlank() }
            .take(10)
            .toList()
    }

    if (examples.isEmpty()) {
        println("No examples found in eval_tickets.jsonl.")
        return
    }

    val config = OpenAIConfig(
        token = "ollama",
        host = OpenAIHost(baseUrl = "http://localhost:11434/v1/"),
        timeout = Timeout(request = 300.seconds, socket = 300.seconds),
    )
    val client = OpenAI(config)

    val results = mutableListOf<BaselineResult>()

    for ((index, line) in examples.withIndex()) {
        val jsonElement = try {
            Json.parseToJsonElement(line.trim())
        } catch (e: Exception) {
            println("  Skipping line ${index + 1}: invalid JSON")
            continue
        }

        val messages = jsonElement.jsonObject["messages"]?.jsonArray ?: continue

        val systemContent = messages.firstOrNull {
            it.jsonObject["role"]?.jsonPrimitive?.content == "system"
        }?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""

        val userContent = messages.firstOrNull {
            it.jsonObject["role"]?.jsonPrimitive?.content == "user"
        }?.jsonObject?.get("content")?.jsonPrimitive?.content ?: continue

        val expectedContent = messages.firstOrNull {
            it.jsonObject["role"]?.jsonPrimitive?.content == "assistant"
        }?.jsonObject?.get("content")?.jsonPrimitive?.content ?: ""

        println("  Processing example ${index + 1}/${examples.size}...")

        val chatRequest = ChatCompletionRequest(
            model = ModelId(modelName),
            messages = listOf(
                ChatMessage(role = ChatRole.System, content = systemContent),
                ChatMessage(role = ChatRole.User, content = userContent),
            ),
        )

        val ollamaResponse = try {
            val completion = client.chatCompletion(chatRequest)
            completion.choices.firstOrNull()?.message?.content ?: "(no response)"
        } catch (e: Exception) {
            "(error: ${e.message})"
        }

        results.add(
            BaselineResult(
                userQuery = userContent,
                expectedAnswer = expectedContent,
                ollamaAnswer = ollamaResponse,
            ),
        )
    }

    val outputFile = File("baseline_results.txt")
    withContext(Dispatchers.IO) {
        outputFile.writeText(buildString {
            appendLine("=== Baseline Evaluation Results ===")
            appendLine("Model: $modelName")
            appendLine("Examples evaluated: ${results.size}")
            appendLine()

            for ((i, result) in results.withIndex()) {
                appendLine("--- Example ${i + 1} ---")
                appendLine("USER QUERY:")
                appendLine(result.userQuery)
                appendLine()
                appendLine("EXPECTED ANSWER:")
                appendLine(result.expectedAnswer)
                appendLine()
                appendLine("OLLAMA ANSWER:")
                appendLine(result.ollamaAnswer)
                appendLine()
            }
        })
    }

    println("  Results saved to baseline_results.txt")
}
