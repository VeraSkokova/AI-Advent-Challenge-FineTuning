package dev.pipeline.validation

import kotlinx.serialization.json.*
import java.io.File

private val REQUIRED_ROLES = setOf("system", "user", "assistant")

fun validateJsonlFile(file: File): List<String> {
    if (!file.exists()) {
        return listOf("File not found: ${file.name}")
    }

    val errors = mutableListOf<String>()

    file.useLines { lines ->
        lines.forEachIndexed { index, line ->
            val lineNum = index + 1
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val jsonElement = try {
                Json.parseToJsonElement(trimmed)
            } catch (e: Exception) {
                errors.add("Line $lineNum: Invalid JSON — ${e.message}")
                return@forEachIndexed
            }

            val obj = jsonElement as? JsonObject
            if (obj == null) {
                errors.add("Line $lineNum: Top-level element is not a JSON object")
                return@forEachIndexed
            }

            val messages = obj["messages"]?.let { it as? JsonArray }
            if (messages == null) {
                errors.add("Line $lineNum: Missing or invalid 'messages' array")
                return@forEachIndexed
            }

            val foundRoles = mutableSetOf<String>()

            for ((msgIndex, msgElement) in messages.withIndex()) {
                val msgObj = msgElement as? JsonObject
                if (msgObj == null) {
                    errors.add("Line $lineNum: messages[$msgIndex] is not a JSON object")
                    continue
                }

                val role = msgObj["role"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (role == null) {
                    errors.add("Line $lineNum: messages[$msgIndex] missing 'role'")
                    continue
                }

                foundRoles.add(role)

                val content = msgObj["content"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (content.isNullOrBlank()) {
                    errors.add("Line $lineNum: messages[$msgIndex] (role='$role') has empty or missing 'content'")
                }
            }

            val missingRoles = REQUIRED_ROLES - foundRoles
            if (missingRoles.isNotEmpty()) {
                errors.add("Line $lineNum: Missing required roles: $missingRoles")
            }
        }
    }

    return errors
}

fun runValidation() {
    val files = listOf("train_tickets.jsonl", "eval_tickets.jsonl")

    var hasErrors = false

    for (fileName in files) {
        val file = File(fileName)
        println("=== Validating: $fileName ===")

        val errors = validateJsonlFile(file)
        if (errors.isEmpty()) {
            println("  No errors found.")
        } else {
            hasErrors = true
            errors.forEach { println("  ERROR: $it") }
        }
        println()
    }

    if (hasErrors) {
        println("Validation completed with errors.")
    } else {
        println("All files passed validation.")
    }
}
