package com.rayoai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DescriptionDetailTest {
    @Test
    fun unknownStoredValue_usesExtensiveDefault() {
        assertEquals(DescriptionDetail.EXTENSIVE, DescriptionDetail.fromStored("UNKNOWN"))
        assertEquals(DescriptionDetail.EXTENSIVE, DescriptionDetail.fromStored(null))
    }

    @Test
    fun eachLevel_producesADistinctInstruction() {
        val instructions = DescriptionDetail.entries.map { it.promptInstruction() }

        assertEquals(DescriptionDetail.entries.size, instructions.distinct().size)
        assertNotEquals(
            DescriptionDetail.MINIMUM.promptInstruction(),
            DescriptionDetail.EXTENSIVE.promptInstruction()
        )
    }
}
