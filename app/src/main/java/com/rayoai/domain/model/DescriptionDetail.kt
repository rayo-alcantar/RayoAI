package com.rayoai.domain.model

enum class DescriptionDetail {
    EXTENSIVE,
    MEDIUM,
    MINIMUM;

    fun promptInstruction(): String = when (this) {
        EXTENSIVE ->
            "For this description, be comprehensive and include all relevant visible details, " +
                "spatial relationships, text, actions and changes that help the user understand the content."
        MEDIUM ->
            "For this description, prioritize the main subject and the information needed to understand it. " +
                "Summarize secondary elements without omitting important visible text or actions."
        MINIMUM ->
            "For this description, state only the essential visible information needed to understand the content. " +
                "Keep the response brief and omit secondary details unless they change the meaning."
    }

    companion object {
        fun fromStored(value: String?): DescriptionDetail =
            entries.firstOrNull { it.name == value } ?: EXTENSIVE
    }
}
