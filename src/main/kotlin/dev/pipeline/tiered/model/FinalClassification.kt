package dev.pipeline.tiered.model

data class FinalClassification(
    val type: TicketType,
    val priority: Priority,
    val language: String,
)
