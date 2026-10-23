package com.rayoai.domain.model

import java.math.BigInteger

/** Numeric release identity; rebuilding the same version is not an update. */
internal data class AppVersion private constructor(private val parts: List<BigInteger>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val comparison = parts.getOrElse(index) { BigInteger.ZERO }
                .compareTo(other.parts.getOrElse(index) { BigInteger.ZERO })
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        private val pattern = Regex("^[vV]?(\\d+(?:\\.\\d+){1,3})(?:-(?:stable|beta))?$")

        fun parse(value: String): AppVersion? = pattern.matchEntire(value)?.groupValues?.get(1)
            ?.split('.')?.map { it.toBigInteger() }?.let { AppVersion(it) }
    }
}
