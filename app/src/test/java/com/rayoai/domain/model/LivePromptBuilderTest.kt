package com.rayoai.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

class LivePromptBuilderTest {

    @Test
    fun buildSystemPrompt_containsIdentityGreetingAndConversationalRules() {
        val promptEs = LivePromptBuilder.buildSystemPrompt("es")
        assertTrue(promptEs.contains("Always respond in es"))
        assertTrue(promptEs.contains("You are now operating in RayoAI Live mode"))
        assertTrue(promptEs.contains("Hola, soy RayoAI, ¿en qué te puedo ayudar hoy?"))
        assertTrue(promptEs.contains("Google Search is available in this session"))
        assertTrue(promptEs.contains("Never claim that you can see a current image when none is available"))

        val promptEn = LivePromptBuilder.buildSystemPrompt("en")
        assertTrue(promptEn.contains("Always respond in en"))
        assertTrue(promptEn.contains("RayoAI Live mode"))
    }

    @Test
    fun liveGreetingTrigger_isDefined() {
        assertTrue(LivePromptBuilder.LIVE_GREETING_TRIGGER.isNotBlank())
        assertTrue(LivePromptBuilder.LIVE_GREETING_TRIGGER.contains("RayoAI Live session has just started"))
    }
}
