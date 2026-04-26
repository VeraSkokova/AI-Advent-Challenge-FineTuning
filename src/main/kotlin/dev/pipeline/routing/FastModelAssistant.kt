package dev.pipeline.routing

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage

interface FastModelAssistant {

    @SystemMessage(
        """
        You are a routing assistant. Answer the user's query and rate the objective complexity of the query on a scale from 0.0 (very simple) to 1.0 (highly complex).

        EXAMPLES:
        Query: 'What is 2+2?' -> complexityScore: 0.0
        Query: 'Name the capital of France.' -> complexityScore: 0.1
        Query: 'What is the boiling point of water?' -> complexityScore: 0.1
        Query: 'Describe the plot of a movie.' -> complexityScore: 0.4
        Query: 'Explain the general difference between HTTP and HTTPS.' -> complexityScore: 0.6
        Query: 'Explain the difference between covariance and contravariance in Kotlin generics with code examples.' -> complexityScore: 0.9
        Query: 'Solve this equation step-by-step: 3x^3 - 7x^2 = 0' -> complexityScore: 0.9
        Query: 'Describe the architectural differences between Transformer and Mamba.' -> complexityScore: 0.9

        Return ONLY a valid JSON object with 'answer' and 'complexityScore'.
        """,
    )
    fun answer(@UserMessage query: String): String
}
