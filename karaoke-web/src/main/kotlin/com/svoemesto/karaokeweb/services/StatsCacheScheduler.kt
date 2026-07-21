package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeweb.StatBySong
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Обновляет кеш счётчиков главной страницы (StatBySong) раз в час. Источник истины — SQL,
 * считается при стандартных фильтрах (id_status>=3, source_markers непустой, без SKIP) — гланая
 * отвечает за миллисекунды без обращения к БД и без блокирующих HEAD-запросов в MinIO на
 * 18k+ записей. Точная проверка стемов (stemsReady в PublicPlayerController) живёт отдельно и
 * используется только для иконки плеера в закромах/поиске (@see usePlayerReadiness в karaoke-public).
 *
 * Почему не Spring @Cacheable:
 *  - в проекте нет Spring Cache (нет @EnableCaching) — проще volatile+AtomicInteger, чем
 *    добавлять стартер и конфиг.
 *  - не нужно кешировать по ключу (один счётчик на весь сайт) и инвалидации по событию —
 *    достаточно периодического «почти свежего» значения.
 */

/**
 * Класс Stats Cache Scheduler.
 *
 * @see docs/features/async-process-queue.md
 */
@Service
class StatsCacheScheduler {
    /**
     * Холодный старт: один синхронный пересчёт сразу после инициализации бина, чтобы первый
     * запрос /api/public/stats после рестарта не вернул нули. Если БД недоступна — refreshCache
     * сам напечатает ошибку и оставит -1; следующий cron-тик через час попробует снова.
     */
    @PostConstruct
    fun warmUp() {
        StatBySong.refreshCache(WORKING_DATABASE)
    }

    /**
     * Каждый час в начале часа: 0 минут 0 секунд. При пиковой нагрузке на главную старт
     * пересчёта в 00:00/13:00/23:00 бывает, но SQL-запросы кеша лёгкие (count с индексом по
     * id_status + фильтр по source_markers через btrim) и блокировок не дают.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun refreshHourly() {
        StatBySong.refreshCache(WORKING_DATABASE)
    }
}
