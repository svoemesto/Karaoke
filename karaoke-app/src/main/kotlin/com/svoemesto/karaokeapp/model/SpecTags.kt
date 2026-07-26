package com.svoemesto.karaokeapp.model

/**
 * Спецтеги в тексте песни («~имя~» / «~имя:значение~», только отдельной строкой) — распознавание и
 * трансляция в маркеры авто-разметки/ручного редактора. Единственный источник правды по синтаксису
 * и реестру: specs/010-lyrics-spec-tags/contracts/tag-registry.md — реализация здесь и зеркальная
 * реализация в SubsEdit.vue ДОЛЖНЫ давать идентичный результат на одном и том же тексте.
 * @see specs/010-lyrics-spec-tags/contracts/tag-registry.md
 */
object SpecTags {
    private val TAG_REGEX = Regex("~(\\p{L}+)(?::([^~]*))?~")

    /** Одно распознанное вхождение тега на строке — имя (нормализовано в нижний регистр) и необязательное значение. */
    data class SpecTag(
        val name: String,
        val value: String?,
    )

    private data class TagRegistryEntry(
        val validate: (String?) -> Boolean,
        val markertype: Markertype,
        val buildLabel: (String?) -> String,
    )

    private data class TagAlias(
        val targetTagName: String,
        val targetValue: String,
    )

    // v1 реестр — см. contracts/tag-registry.md "Реестр v1". Диапазон group 0..4 — существующий
    // диапазон ручных group-маркеров (SubsEdit.vue addMarker('setting', 'GROUP|0'..'GROUP|4')).
    private val registry: Map<String, TagRegistryEntry> =
        mapOf(
            "newline" to
                TagRegistryEntry(
                    validate = { value -> value == null },
                    markertype = Markertype.NEWLINE,
                    buildLabel = { "" },
                ),
            "group" to
                TagRegistryEntry(
                    validate = { value -> value != null && (value.toIntOrNull()?.let { it in 0..4 } ?: false) },
                    markertype = Markertype.SETTING,
                    buildLabel = { value -> "GROUP|$value" },
                ),
            "comment" to
                TagRegistryEntry(
                    validate = { value -> !value.isNullOrBlank() },
                    markertype = Markertype.SETTING,
                    buildLabel = { value -> "COMMENT|$value" },
                ),
        )

    // v1 алиасы — см. contracts/tag-registry.md "Алиасы v1". Набор и нумерация согласованы с
    // пользователем явно; для group:4 алиаса нет. Алиас не принимает значение (resolve() ниже).
    private val aliases: Map<String, TagAlias> =
        mapOf(
            "куплет" to TagAlias("group", "0"),
            "припев" to TagAlias("group", "1"),
            "бридж" to TagAlias("group", "2"),
            "приговор" to TagAlias("group", "3"),
        )

    /**
     * Снимает со строки все синтаксически валидные теги (независимо от того, распознаны ли они в
     * реестре — нераспознанный тег на отдельной строке всё равно даёт "пустую" строку с маркером
     * по умолчанию, см. [resolve] и contracts/tag-registry.md). Возвращает строку без тег-токенов
     * и список найденных тегов в порядке появления.
     */
    fun parseLine(line: String): Pair<String, List<SpecTag>> {
        val tags = mutableListOf<SpecTag>()
        val stripped =
            TAG_REGEX.replace(line) { match ->
                tags.add(SpecTag(match.groupValues[1].lowercase(), match.groups[2]?.value))
                ""
            }
        return stripped to tags
    }

    /** Резолвит тег (включая алиасы) в маркер разметки. null — нераспознан (неизвестное имя или невалидное значение, FR-008). */
    fun resolve(tag: SpecTag): Pair<Markertype, String>? {
        val alias = aliases[tag.name]
        if (alias != null) {
            if (tag.value != null) return null
            return resolve(SpecTag(alias.targetTagName, alias.targetValue))
        }
        val entry = registry[tag.name] ?: return null
        if (!entry.validate(tag.value)) return null
        return entry.markertype to entry.buildLabel(tag.value)
    }
}
