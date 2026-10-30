package com.svoemesto.karaokeweb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// EnableScheduling — нужен для SubscriptionRenewalScheduler (автопродление подписки на сайт).
// Решение "шедулер в karaoke-web, не на admin-машине" (отступление от исходной формулировки плана,
// где рассматривался вариант admin-машины): PaymentService/YooKassa WebClient/yookassa-proxy уже
// живут только в karaoke-web, а именно karaoke-web (не karaoke-app) развёрнут на проде — держать
// автопродление там же, без лишнего HTTP-моста между процессами.

/**
 * Класс Karaoke Web Application.
 *
 * @see archive/docs/features/dual-db-sync.md
 */
@EnableScheduling
@SpringBootApplication
class KaraokeWebApplication

fun main(args: Array<String>) {
    // KARAOKE-338: веб-приложение прода явно остаётся в роли PROD — новости «в коллекции»
    // создаёт админка (karaoke-app) и доставляет синхронизация; на проде работает только
    // путь по применению синхронизации (MainController.doChangeRecords).
    com.svoemesto.karaokeapp.KaraokeRuntimeRole.name = com.svoemesto.karaokeapp.KaraokeRuntimeRole.PROD
    runApplication<KaraokeWebApplication>(*args)
}
