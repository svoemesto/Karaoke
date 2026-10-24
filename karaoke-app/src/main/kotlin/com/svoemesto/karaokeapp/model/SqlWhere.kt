package com.svoemesto.karaokeapp.model

import java.sql.PreparedStatement

/**
 * `WHERE`-условие с параметрами (ADR-0001: сырой JDBC, `PreparedStatement`).
 *
 * Заменяет «список готовых SQL-фрагментов», который исторически собирали
 * `getWhereList(...)` в моделях: значения из HTTP-фильтров подставлялись в
 * строку конкатенацией и уходили в `Statement.executeQuery` — это давало
 * SQL-инъекцию (BR-028, BR-100, BR-175, BR-182, BR-237, аудит #336).
 *
 * Теперь каждый фрагмент содержит только `?`-плейсхолдеры, а значения лежат
 * в [params] в том же порядке. Собирать условие — через [SqlWhereBuilder],
 * исполнять — через `KaraokeDbTable.loadList(where = ...)`, который сам
 * выбирает `PreparedStatement`, если параметры есть.
 *
 * Инварианты:
 * - имена таблиц/колонок во фрагментах — только константы кода, никогда не
 *   значения из `args`; динамические имена — только по белому списку;
 * - число `?` во фрагментах равно `params.size` (проверяется в [bind]);
 * - если значение не прошло валидацию (например, `id` не число) — фрагмент
 *   `1=0`, а не «пропустить условие» (иначе фильтр расширил бы выборку).
 *
 * @see SqlWhereBuilder
 * @see KaraokeDbTable.loadList
 */
class SqlWhere internal constructor(
    val fragments: List<String>,
    val params: List<Any?>,
) {
    /** `true` — условий нет (WHERE не добавляется). */
    val isEmpty: Boolean get() = fragments.isEmpty()

    /** Склеить фрагменты для `WHERE ...` (по умолчанию — через ` AND `). */
    fun joinToString(separator: String = " AND "): String = fragments.joinToString(separator)

    /**
     * Привязать [params] к [ps] (нумерация с 1, порядок — как в [fragments]).
     *
     * @throws IllegalArgumentException если число `?` не совпало с числом параметров.
     */
    fun bind(ps: PreparedStatement) {
        val placeholders = fragments.sumOf { fragment -> fragment.count { it == '?' } }
        require(placeholders == params.size) {
            "SqlWhere: ожидалось $placeholders параметров, передано ${params.size}: $fragments"
        }
        params.forEachIndexed { index, value ->
            val position = index + 1
            when (value) {
                null -> ps.setObject(position, null)
                is Int -> ps.setInt(position, value)
                is Long -> ps.setLong(position, value)
                else -> ps.setString(position, value.toString())
            }
        }
    }

    override fun toString(): String = "SqlWhere(${joinToString()}, params=$params)"

    companion object {
        /** Пустое условие — `SELECT ... FROM table` без `WHERE`. */
        val EMPTY = SqlWhere(emptyList(), emptyList())
    }
}

/**
 * Строитель [SqlWhere]: фрагменты и значения добавляются парами, поэтому
 * рассинхронизировать их нельзя.
 *
 * Пример:
 * ```
 * val where = SqlWhereBuilder()
 *     .add("LOWER(picture_name) LIKE ?", "%${name.lowercase()}%")
 *     .add("id = ?", id.toLongOrNull() ?: return builder.none().build())
 *     .build()
 * ```
 */
class SqlWhereBuilder {
    private val fragments: MutableList<String> = mutableListOf()
    private val params: MutableList<Any?> = mutableListOf()

    /** Добавить фрагмент с `?`-плейсхолдерами и значениями в порядке `?`. */
    fun add(
        sql: String,
        vararg values: Any?,
    ): SqlWhereBuilder {
        fragments += sql
        params.addAll(values)
        return this
    }

    /** Фрагмент без параметров (константное условие кода). */
    fun raw(sql: String): SqlWhereBuilder = add(sql)

    /** Условие «ничего не найдено» — безопасный отказ при невалидном значении. */
    fun none(): SqlWhereBuilder = add("1=0")

    fun isEmpty(): Boolean = fragments.isEmpty()

    fun build(): SqlWhere = SqlWhere(fragments.toList(), params.toList())
}
