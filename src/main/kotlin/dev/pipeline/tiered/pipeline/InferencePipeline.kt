package dev.pipeline.tiered.pipeline

import dev.pipeline.tiered.model.HandlerType
import dev.pipeline.tiered.model.InferenceResult
import dev.pipeline.tiered.model.MicroStatus
import dev.pipeline.tiered.model.TicketInput

class InferencePipeline(
    private val microLayer: MicroModelLayer,
    private val fallbackLayer: FallbackLayer,
    private val confidenceThreshold: Double = 0.75,
) {
    fun process(ticket: TicketInput): InferenceResult {
        val totalStart = System.currentTimeMillis()

        var microConfidence: Double? = null
        var microStatus: String? = null
        var microLatencyMs: Long? = null
        var fallbackLatencyMs: Long? = null
        var useFallback = false
        var fallbackReason: String? = null

        // --- Micro layer ---
        val microStart = System.currentTimeMillis()
        try {
            val micro = microLayer.classify(ticket.body)
            microLatencyMs = System.currentTimeMillis() - microStart
            microConfidence = micro.confidence
            microStatus = micro.status.name

            if (micro.status == MicroStatus.UNSURE) {
                useFallback = true
                fallbackReason = "status=UNSURE"
            } else if (micro.confidence < confidenceThreshold) {
                useFallback = true
                fallbackReason = "confidence=${micro.confidence} < $confidenceThreshold"
            }

            if (!useFallback) {
                val latencyMs = System.currentTimeMillis() - totalStart
                return buildResult(
                    ticket = ticket,
                    handledBy = HandlerType.MICRO,
                    type = micro.type,
                    priority = micro.priority,
                    language = micro.language,
                    microConfidence = microConfidence,
                    microStatus = microStatus,
                    latencyMs = latencyMs,
                    microLatencyMs = microLatencyMs,
                    fallbackLatencyMs = null,
                )
            }
        } catch (e: Exception) {
            microLatencyMs = System.currentTimeMillis() - microStart
            useFallback = true
            fallbackReason = "micro error: ${e.message}"
        }

        // --- Fallback layer ---
        println("    -> Fallback triggered: $fallbackReason")
        val fallbackStart = System.currentTimeMillis()
        val fallback = try {
            fallbackLayer.classify(ticket.body)
        } catch (e: Exception) {
            throw IllegalStateException("Both micro and fallback failed for ticket ${ticket.id}: $fallbackReason / fallback: ${e.message}", e)
        }
        fallbackLatencyMs = System.currentTimeMillis() - fallbackStart

        val latencyMs = System.currentTimeMillis() - totalStart
        return buildResult(
            ticket = ticket,
            handledBy = HandlerType.FALLBACK,
            type = fallback.type,
            priority = fallback.priority,
            language = fallback.language,
            microConfidence = microConfidence,
            microStatus = microStatus,
            latencyMs = latencyMs,
            microLatencyMs = microLatencyMs,
            fallbackLatencyMs = fallbackLatencyMs,
        )
    }

    private fun buildResult(
        ticket: TicketInput,
        handledBy: HandlerType,
        type: dev.pipeline.tiered.model.TicketType,
        priority: dev.pipeline.tiered.model.Priority,
        language: String,
        microConfidence: Double?,
        microStatus: String?,
        latencyMs: Long,
        microLatencyMs: Long?,
        fallbackLatencyMs: Long?,
    ): InferenceResult {
        val gtType = ticket.groundTruthType
        val gtPriority = ticket.groundTruthPriority
        val gtLang = ticket.gtLanguage

        val isCorrectType = gtType?.let { it == type }
        val isCorrectPriority = gtPriority?.let { it == priority }
        val isCorrectLanguage = gtLang?.let { it.lowercase() == language }
        val allCorrect = if (isCorrectType != null && isCorrectPriority != null && isCorrectLanguage != null) {
            isCorrectType && isCorrectPriority && isCorrectLanguage
        } else {
            null
        }

        return InferenceResult(
            ticketId = ticket.id,
            handledBy = handledBy,
            finalType = type,
            finalPriority = priority,
            finalLanguage = language,
            microConfidence = microConfidence,
            microStatus = microStatus,
            latencyMs = latencyMs,
            microLatencyMs = microLatencyMs,
            fallbackLatencyMs = fallbackLatencyMs,
            category = ticket.category,
            isCorrectType = isCorrectType,
            isCorrectPriority = isCorrectPriority,
            isCorrectLanguage = isCorrectLanguage,
            allCorrect = allCorrect,
        )
    }
}
