package com.svoemesto.karaokeapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Класс Karaoke App Application.
 *
 * @see docs/features/dual-db-sync.md
 */
@SpringBootApplication
@EnableScheduling
class KaraokeAppApplication

fun main(args: Array<String>) {
    runApplication<KaraokeAppApplication>(*args)
}
