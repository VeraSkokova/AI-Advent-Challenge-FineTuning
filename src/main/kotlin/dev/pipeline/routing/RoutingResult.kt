package dev.pipeline.routing

data class RoutingResult(
    val query: String,
    val fastModelName: String,
    val strongModelName: String,
    val escalated: Boolean,
    val complexityScore: Double?,
    val escalationReason: String?,
    val answer: String,
) {
    fun toMarkdown(): String = buildString {
        appendLine("### Query: $query")
        if (escalated) {
            appendLine("- **Attempt**: fast model ($fastModelName) → escalated to strong model ($strongModelName)")
            appendLine("- **Complexity Score**: ${complexityScore ?: "N/A"}")
            appendLine("- **Status**: Escalated (Reason: $escalationReason)")
        } else {
            appendLine("- **Attempt**: fast model ($fastModelName)")
            appendLine("- **Complexity Score**: $complexityScore")
            appendLine("- **Status**: Success")
        }
        appendLine("- **Final Answer**: $answer")
        appendLine()
    }
}
