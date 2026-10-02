package com.local.chargemeter.ui

internal data class BuildContribution(val version: String, val publishedAt: String, val models: List<String>)

// Keep the primary contributor first; update this metadata for each release.
internal val currentBuildModels = listOf("GPT-6 Astra", "GPT-6.1 Sol")
internal const val currentBuildDate = "2026.10.02"
internal val releasedBuilds = listOf(
    BuildContribution("1.0.4", "2026.10.01", listOf("GPT-6.1 Sol")),
    BuildContribution("1.0.3", "2026.10.01", listOf("GPT-6.1 Sol")),
    BuildContribution("1.0.2", "2026.09.30", listOf("GPT-5.6 Sol", "GPT-6 Astra")),
    BuildContribution("1.0.1", "2026.09.29", listOf("GPT-5.6 Sol", "GPT-6 Astra")),
    BuildContribution("1.0.0", "2026.09.29", listOf("GPT-5.6 Sol", "GPT-6 Astra")),
)
