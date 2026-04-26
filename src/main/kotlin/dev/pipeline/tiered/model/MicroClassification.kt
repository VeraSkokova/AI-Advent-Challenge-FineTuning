package dev.pipeline.tiered.model

data class MicroClassification(
    val type: TicketType,
    val priority: Priority,
    val language: String,
    val confidence: Double,
    val status: MicroStatus,
)
