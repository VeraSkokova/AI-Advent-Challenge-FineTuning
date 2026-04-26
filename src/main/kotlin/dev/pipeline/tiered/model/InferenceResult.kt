package dev.pipeline.tiered.model

data class InferenceResult(
    val ticketId: Int,
    val handledBy: HandlerType,
    val finalType: TicketType,
    val finalPriority: Priority,
    val finalLanguage: String,
    val microConfidence: Double?,
    val microStatus: String?,
    val latencyMs: Long,
    val microLatencyMs: Long?,
    val fallbackLatencyMs: Long?,
    val category: String?,
    val isCorrectType: Boolean?,
    val isCorrectPriority: Boolean?,
    val isCorrectLanguage: Boolean?,
    val allCorrect: Boolean?,
)
