package com.svoemesto.karaokeapp.services.songdeletion

/**
 * Состояния прогона уборки (KARAOKE-429, спека 739, FR-034).
 *
 * Цепочка: `PLANNED → APPROVED → RUNNING → {DONE, PARTIAL, FAILED}`.
 */
enum class CleanupRunState {
    PLANNED,
    APPROVED,
    RUNNING,
    DONE,
    PARTIAL,
    FAILED,
    ;

    /** Терминальное состояние — прогон завершён (успешно, частично или нет). */
    val isTerminal: Boolean
        get() = this == DONE || this == PARTIAL || this == FAILED
}

/**
 * Вид прогона уборки (FR-034, FR-041).
 */
enum class CleanupRunKind {
    /** Уборка артефактов удаляемой песни. */
    SONG_DELETE,

    /** Разовая уборка осиротевших артефактов (US4). */
    ONE_TIME_CLEANUP,
}

/**
 * Машина состояний прогона уборки (FR-034, SC-003).
 *
 * **Ключевое правило SC-003: «ложного успеха» не бывает.** Прогон не может перейти в
 * `DONE`, если по нему есть отказы (`files_failed > 0` или `objects_failed > 0`) —
 * только `PARTIAL`. Иначе администратор увидел бы «завершено» при неубранных файлах.
 *
 * Правило вынесено в отдельный объект без БД и сети — его обязано покрывать
 * юнит-тест (`CleanupRunStateMachineTest`).
 */
object CleanupRunStateMachine {
    /**
     * Допустим ли переход из одного состояния в другое?
     *
     * @param from текущее состояние прогона.
     * @param to новое состояние.
     */
    fun canTransition(
        from: CleanupRunState,
        to: CleanupRunState,
    ): Boolean =
        when (from) {
            CleanupRunState.PLANNED ->
                to == CleanupRunState.APPROVED || to == CleanupRunState.FAILED
            CleanupRunState.APPROVED ->
                to == CleanupRunState.RUNNING || to == CleanupRunState.FAILED
            CleanupRunState.RUNNING ->
                to == CleanupRunState.DONE || to == CleanupRunState.PARTIAL || to == CleanupRunState.FAILED
            CleanupRunState.DONE,
            CleanupRunState.PARTIAL,
            CleanupRunState.FAILED,
            -> false
        }

    /**
     * Итоговое состояние прогона по счётчикам отказов (SC-003).
     *
     * @param hasFailures есть ли отказы (файлы/объекты, которые не удалились).
     * @return `DONE`, если отказов нет; иначе — `PARTIAL`.
     */
    fun terminalState(hasFailures: Boolean): CleanupRunState =
        if (hasFailures) CleanupRunState.PARTIAL else CleanupRunState.DONE

    /**
     * Можно ли выставить `DONE` при таких счётчиках?
     *
     * @param filesFailed число неудачных файлов.
     * @param objectsFailed число неудачных объектов хранилищ.
     */
    fun canFinishDone(
        filesFailed: Int,
        objectsFailed: Int,
    ): Boolean = filesFailed == 0 && objectsFailed == 0
}
