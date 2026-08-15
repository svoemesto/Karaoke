package com.svoemesto.karaokeapp

/**
 * Перечисление возможных значений для karaoke process statuses.
 *
 * @see archive/archive/docs/features/dual-db-sync.md
 */
enum class KaraokeProcessStatuses {
    CREATING,
    WAITING,
    WORKING,
    DONE,
    ERROR,
}
