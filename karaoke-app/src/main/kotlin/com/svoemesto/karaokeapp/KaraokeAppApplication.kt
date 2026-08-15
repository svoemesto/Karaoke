package com.svoemesto.karaokeapp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.Executors
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler

/**
 * Класс Karaoke App Application.
 *
 * @see archive/archive/docs/features/dual-db-sync.md
 */
@SpringBootApplication
@EnableScheduling
class KaraokeAppApplication : SchedulingConfigurer {
    /**
     * Явная регистрация TaskScheduler-бина (specs/113-telegram-demo-publish диагностика).
     * Без этого бина в Spring Boot 3.5 / Spring Framework 6.2 `@Scheduled`-методы на
     * `@Component`-классах могут молча не регистрироваться, если в classpath нет
     * автоконфигурации TaskScheduler (зависит от spring-boot-autoconfigure). Эксплицитный
     * `ConcurrentTaskScheduler` гарантирует, что все `@Scheduled` тикают.
     */
    @Bean
    fun taskScheduler(): ConcurrentTaskScheduler =
        ConcurrentTaskScheduler(
            Executors.newScheduledThreadPool(4) { r ->
                Thread(r, "karaoke-scheduler").apply { isDaemon = true }
            },
        )

    override fun configureTasks(registrar: ScheduledTaskRegistrar) {
        registrar.setTaskScheduler(taskScheduler())
    }
}

fun main(args: Array<String>) {
    runApplication<KaraokeAppApplication>(*args)
}
