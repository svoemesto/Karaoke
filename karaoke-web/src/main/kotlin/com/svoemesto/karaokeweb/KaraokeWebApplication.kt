package com.svoemesto.karaokeweb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

// EnableScheduling — нужен для SubscriptionRenewalScheduler (автопродление подписки на сайт).
// Решение "шедулер в karaoke-web, не на admin-машине" (отступление от исходной формулировки плана,
// где рассматривался вариант admin-машины): PaymentService/YooKassa WebClient/yookassa-proxy уже
// живут только в karaoke-web, а именно karaoke-web (не karaoke-app) развёрнут на проде — держать
// автопродление там же, без лишнего HTTP-моста между процессами.
@EnableScheduling
@SpringBootApplication
class KaraokeWebApplication

fun main(args: Array<String>) {
    runApplication<KaraokeWebApplication>(*args)
}
