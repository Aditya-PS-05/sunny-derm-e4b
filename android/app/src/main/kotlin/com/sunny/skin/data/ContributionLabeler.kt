package com.sunny.skin.data

import com.sunny.skin.data.model.Analysis
import com.sunny.skin.inference.SchemaParser

data class ContributionLabels(
    val modelOutput: Analysis,
    val correctedOutput: Analysis?,
)

/** Keeps contributed model output paired with the exact photo/raw response. */
object ContributionLabeler {
    fun from(rawOutput: String, savedOutput: Analysis): ContributionLabels {
        val modelOutput = SchemaParser.parse(rawOutput) ?: savedOutput
        return ContributionLabels(
            modelOutput = modelOutput,
            correctedOutput = savedOutput.takeIf { it != modelOutput },
        )
    }
}
