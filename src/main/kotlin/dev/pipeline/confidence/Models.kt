package dev.pipeline.confidence

import kotlinx.serialization.Serializable

@Serializable
enum class TicketCategory {
    REFUND,
    TECHNICAL_ISSUE,
    COMPLIMENT,
    UNKNOWN,
}

@Serializable
data class ExtractedTicket(
    val category: TicketCategory,
    val ticketId: String? = null,
    val summary: String,
)

@Serializable
data class DatasetItem(
    val id: String,
    val text: String,
    val expected_difficulty: String,
)
