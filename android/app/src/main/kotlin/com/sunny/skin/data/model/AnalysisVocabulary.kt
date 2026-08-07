package com.sunny.skin.data.model

/**
 * Converts free-form model output into a small, non-diagnostic vocabulary.
 *
 * The raw model response remains stored for audit. Everything shown to people is
 * normalized here so cloud and offline inference use the same wording, legacy
 * observations improve automatically, and contradictory surface descriptions
 * fail safely to "unclear" instead of being presented as facts.
 */
object AnalysisVocabulary {
    fun normalize(raw: Analysis): Analysis {
        val lesionType = lesionType(raw.lesionType)
        val colour = colour(raw.colour)
        val symmetry = symmetry(raw.symmetry)
        val borders = borders(raw.borders)
        val texture = texture(raw.lesionType, raw.texture)
        return Analysis(
            lesionType = lesionType,
            colour = colour,
            symmetry = symmetry,
            borders = borders,
            texture = texture,
            summary = summary(lesionType, colour, symmetry, borders, texture),
        )
    }

    private fun lesionType(value: String): String {
        val text = value.clean()
        return when {
            text == "visible skin mark" -> "Visible skin mark"
            text.hasAny("blister", "vesicle", "fluid-filled", "fluid filled") ->
                "Fluid-filled bump"
            text.hasAny("papule", "nodule", "bump", "raised", "elevated") ->
                "Raised spot"
            text.hasAny("patch", "plaque", "area") -> "Patch"
            text.hasAny("macule", "spot", "freckle", "dot", "mark") -> "Flat spot"
            else -> "Visible skin mark"
        }
    }

    private fun colour(value: String): String {
        val text = value.clean()
        val colours = buildList {
            if (text.hasAny("skin-toned", "skin toned", "skin-coloured", "skin colored")) {
                add("Skin-coloured")
            }
            if (text.hasAny("light and dark brown", "dark and light brown")) {
                add("Light brown")
                add("Dark brown")
            } else {
                if ("light brown" in text) add("Light brown")
                if ("dark brown" in text) add("Dark brown")
            }
            if ("brown" in text && "light brown" !in text && "dark brown" !in text) add("Brown")
            if ("tan" in text) add("Tan")
            if (text.hasPattern("\\bpink(?:ish)?\\b")) add("Pink")
            if (text.hasPattern("\\bred(?:dish)?\\b")) add("Red")
            if (text.hasPattern("\\b(?:purple|violet)\\b")) add("Purple")
            if (text.hasPattern("\\b(?:blue|bluish)\\b")) add("Blue")
            if (text.hasPattern("\\bblack(?:ish)?\\b")) add("Black")
            if (text.hasPattern("\\b(?:white|whitish)\\b")) add("White")
            if (text.hasPattern("\\byellow(?:ish)?\\b")) add("Yellow")
        }.distinct()
        return when (colours.size) {
            0 -> "Colour unclear"
            1 -> colours.single()
            2 -> colours.joinToString(" and ")
            else -> colours.take(3).dropLast(1).joinToString(", ") + ", and " + colours.take(3).last()
        }
    }

    private fun symmetry(value: String): String {
        val text = value.clean()
        val negatedSymmetry = text.hasAny("not symmetric", "not symmetrical")
        val asymmetric = text.hasPattern("\\b(?:asymmetric|asymmetrical)\\b") ||
            negatedSymmetry || text.hasAny("uneven shape", "irregular shape")
        val symmetric = (!negatedSymmetry && text.hasPattern("\\b(?:symmetric|symmetrical)\\b")) ||
            text.hasAny("roughly even", "even shape", "balanced")
        return when {
            asymmetric && symmetric -> "Symmetry unclear"
            asymmetric -> "Asymmetric"
            symmetric -> "Symmetric"
            else -> "Symmetry unclear"
        }
    }

    private fun borders(value: String): String {
        val text = value.clean()
        if (text.hasAny("unclear", "not clear", "cannot tell", "can't tell")) {
            return "Border detail unclear"
        }
        val irregular = text.hasPattern("\\b(?:irregular|ragged|uneven|notched|scalloped)\\b")
        val smooth = text.hasPattern("\\b(?:smooth|regular|even)\\b")
        val poorlyDefined = text.hasAny(
            "poorly-defined", "poorly defined", "ill-defined", "ill defined",
            "blurred", "blurry", "fuzzy", "indistinct", "diffuse",
        )
        val wellDefined = text.hasAny("well-defined", "well defined") ||
            text.hasPattern("\\b(?:clear|sharp|distinct)\\b")
        if ((irregular && smooth) || (poorlyDefined && wellDefined)) {
            return "Border detail unclear"
        }
        val shape = when {
            irregular -> "Irregular"
            smooth -> "Smooth"
            else -> null
        }
        val definition = when {
            poorlyDefined -> "poorly defined"
            wellDefined -> "well defined"
            else -> null
        }
        return listOfNotNull(shape, definition).joinToString(" and ")
            .ifEmpty { "Border detail unclear" }
    }

    private fun texture(typeValue: String, textureValue: String): String {
        val text = "${typeValue.clean()} ${textureValue.clean()}"
        val scaly = text.hasAny("scaly", "scale", "flaky", "flaking", "crust", "crusted")
        val rough = text.hasAny("rough", "coarse")
        val smooth = text.hasAny("smooth", "even surface")
        val raised = text.hasAny("raised", "elevated")

        // Do not choose a side when different fields disagree about the surface.
        if (smooth && (rough || scaly)) return "Texture unclear"
        return when {
            scaly && rough -> "Rough and scaly"
            scaly -> "Scaly"
            rough -> "Rough"
            smooth -> "Smooth"
            raised -> "Raised"
            else -> "Texture unclear"
        }
    }

    private fun summary(
        lesionType: String,
        colour: String,
        symmetry: String,
        borders: String,
        texture: String,
    ): String {
        val opening = if (colour == "Colour unclear") {
            "This image shows a ${lesionType.lowercase()}; its colour is unclear."
        } else {
            "This image shows a ${colour.lowercase()} ${lesionType.lowercase()}."
        }
        val shape = when (symmetry) {
            "Symmetric" -> "Its shape appears symmetric."
            "Asymmetric" -> "Its shape appears asymmetric."
            else -> "Its symmetry is unclear."
        }
        val border = if (borders == "Border detail unclear") {
            "The border detail is unclear."
        } else {
            "The border appears ${borders.lowercase()}."
        }
        val surface = if (texture == "Texture unclear") {
            "The surface texture is unclear."
        } else {
            "The surface appears ${texture.lowercase()}."
        }
        return "$opening $shape $border $surface"
    }

    private fun String.clean(): String = lowercase()
        .replace('–', '-')
        .replace('—', '-')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun String.hasAny(vararg terms: String): Boolean = terms.any(::contains)
    private fun String.hasPattern(pattern: String): Boolean = Regex(pattern).containsMatchIn(this)
}

fun Analysis.normalized(): Analysis = AnalysisVocabulary.normalize(this)

data class AnalysisChange(
    val label: String,
    val previous: String,
    val current: String,
)

/** Literal differences between controlled observable fields; never a risk score. */
object AnalysisComparison {
    fun changes(previous: Analysis, current: Analysis): List<AnalysisChange> {
        val before = previous.normalized().rows().filterNot { it.first == "Summary" }.toMap()
        val after = current.normalized().rows().filterNot { it.first == "Summary" }.toMap()
        return Analysis.FIELDS.filterNot { it == "Summary" }.mapNotNull { label ->
            val old = before.getValue(label)
            val new = after.getValue(label)
            AnalysisChange(label, old, new).takeIf { !old.equals(new, ignoreCase = true) }
        }
    }
}
