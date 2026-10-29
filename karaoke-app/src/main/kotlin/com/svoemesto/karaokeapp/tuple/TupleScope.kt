package com.svoemesto.karaokeapp.tuple

import java.io.File

/**
 * Идентичность кортежа и подстановка scope в argv.
 *
 * `scopeId = process_chain_id ?: id`: у головы кортежа `process_chain_id = id`, у одиночного
 * задания цепочки нет — его scope равен собственному id. Строители argv кладут литерал
 * [TOKEN], потому что id головы известен только после INSERT; подстановка выполняется
 * непосредственно перед запуском шага.
 *
 * @see specs/674-kortege-execution/contracts/tuple-cli.md
 * @see specs/674-kortege-execution/spec.md FR-004, FR-006
 */
object TupleScope {
    /** Токен в `process_args`, разворачиваемый в `scopeId` перед запуском шага. */
    const val TOKEN: String = "{scope}"

    /** Идентификатор владельца временных ресурсов кортежа. */
    fun scopeId(
        chainId: Long?,
        stepId: Long,
    ): Long = chainId ?: stepId

    /** Разворачивает [TOKEN] во всех аргументах одного шага (включая `runFunctionWithArgs`). */
    fun expand(
        argv: List<String>,
        scopeId: Long,
    ): List<String> = argv.map { it.replace(TOKEN, scopeId.toString()) }

    /** Каталог временных ресурсов кортежа внутри общего корня (корень не создаётся/не удаляется). */
    fun scopeDir(
        root: String,
        scopeId: Long,
    ): File = File(root, scopeId.toString())
}
