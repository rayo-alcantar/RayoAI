package com.rayoai.domain.model

enum class UpdateChannel {
    STABLE,
    BETA,
    // Persisted by older versions. Keep the name readable, but use BETA semantics.
    ALL;

    val canonical: UpdateChannel
        get() = if (this == ALL) BETA else this

    companion object {
        fun fromStored(value: String?): UpdateChannel = entries.firstOrNull { it.name == value }
            ?.canonical ?: STABLE
    }
}
