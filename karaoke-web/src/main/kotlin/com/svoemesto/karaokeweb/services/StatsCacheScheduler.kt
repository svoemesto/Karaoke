package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeweb.StatBySong
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Обновляет кеш счётчиков главной страницы (StatBySong). Источник истины — SQL, считается при
 * стандартных фильтрах (id_status>=6, source_markers непустой, без SKIP) — главная отвечает за
 * миллисекунды без обращения к БД и без блокирующих HEAD-запросов в MinIO на 18k+ записей. Точная
 * проверка стемов (stemsReady в PublicPlayerController) живёт отдельно и используется только для
 * иконки плеера в закромах/поиске (@see usePlayerReadiness в karaoke-public).
 *
 * Два независимых триггера пересчёта (specs/143-song-free-access-window):
 *  - **Часовой** ([refreshHourly]) — единственный источник истины для перехода "наступил эфир":
 *    для него ничего не "взводится", время просто проходит, поэтому нужен периодический опрос.
 *  - **По изменению** ([refreshIfDirty], раз в минуту) — реагирует на изменение free-статуса песни
 *    при сохранении/синхронизации: karaoke-app взводит флаг через
 *    `InternalStatsController.markDirty` (см. [StatBySong.markDirty]), здесь он лишь проверяется —
 *    сам пересчёт (SQL) происходит только если флаг взведён, минутный тик почти всегда no-op.
 *
 * Почему не Spring @Cacheable/событийная инвалидация "в лоб": karaoke-app и karaoke-web — разные
 * процессы (часто разные машины, LOCAL admin vs SERVER prod), общей шины/брокера нет — простой
 * best-effort HTTP-пуш + флаг дешевле, чем добавлять message queue ради одного счётчика.
 */

/**
 * Класс Stats Cache Scheduler.
 *
 * @see docs/features/async-process-queue.md
 * @see docs/features/song-free-access.md
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
     * id_status + фильтр по source_markers через btrim) и блокировок не дают. Единственный
     * триггер для перехода "наступил эфир"/"истекло окно бесплатного доступа" — эти события не
     * сопровождаются явным сохранением записи, поэтому dirty-флаг их не ловит.
     */
    @Scheduled(cron = "0 0 * * * *")
    fun refreshHourly() {
        StatBySong.refreshCache(WORKING_DATABASE)
    }

    /**
     * Раз в минуту — почти всегда no-op (`consumeDirty()` читает и сбрасывает
     * [AtomicBoolean][java.util.concurrent.atomic.AtomicBoolean] за одну операцию). Пересчёт
     * запускается только если флаг был взведён `InternalStatsController.markDirty` — при
     * сохранении в karaoke-app песни с изменённым `free`, либо при синхронизации LOCAL→SERVER,
     * доставившей такое изменение. Таким образом обычный пользователь видит обновлённые счётчики
     * в пределах минуты после действия администратора, а не часа.
     */
    @Scheduled(fixedRate = 60_000)
    fun refreshIfDirty() {
        if (StatBySong.consumeDirty()) {
            StatBySong.refreshCache(WORKING_DATABASE)
        }
    }
}
