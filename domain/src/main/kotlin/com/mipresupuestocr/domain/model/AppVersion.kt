package com.mipresupuestocr.domain.model

/**
 * A released version such as `1.2.0`, parsed from a tag (`v1.2.0`) or from `BuildConfig.VERSION_NAME`
 * (`1.2.0-debug`). Only the numeric part matters for deciding whether an update exists, so suffixes
 * are ignored.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this,
        other,
        { it.major },
        { it.minor },
        { it.patch },
    )

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
            val match = PATTERN.find(text) ?: return null
            return AppVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: 0,
                patch = match.groupValues[3].toIntOrNull() ?: 0,
            )
        }
    }
}
