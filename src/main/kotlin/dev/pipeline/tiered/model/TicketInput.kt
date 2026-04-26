package dev.pipeline.tiered.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TicketInput(
    val id: Int,
    val body: String,
    val category: String? = null,
    @SerialName("gt_type") val gtType: String? = null,
    @SerialName("gt_priority") val gtPriority: String? = null,
    @SerialName("gt_language") val gtLanguage: String? = null,
    @SerialName("labeling_reason") val labelingReason: String? = null,
) {
    val groundTruthType: TicketType? get() = gtType?.let { TicketType.fromStringOrNull(it) }
    val groundTruthPriority: Priority? get() = gtPriority?.let { Priority.fromStringOrNull(it) }
}
