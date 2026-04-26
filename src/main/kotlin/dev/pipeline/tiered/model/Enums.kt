package dev.pipeline.tiered.model

enum class TicketType {
    Incident, Request, Problem, Change;

    companion object {
        fun fromStringOrNull(value: String): TicketType? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class Priority {
    high, medium, low;

    companion object {
        fun fromStringOrNull(value: String): Priority? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class MicroStatus {
    OK, UNSURE;

    companion object {
        fun fromStringOrNull(value: String): MicroStatus? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

enum class HandlerType {
    MICRO, FALLBACK
}
