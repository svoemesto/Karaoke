package com.svoemesto.karaokeweb.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Регрессионный тест «до/после» для аудита BR-005 «nth-free-race:price» (OpenProject #378).
 *
 * **ДО правки** порядковый номер покупки для акции «каждая N-я бесплатно» считался как
 * `Subscription.countPaid(...) + 1` без блокировки и без транзакции. Параллельные
 * `POST /account/subscription/create` одного пользователя читали один и тот же `countPaid`,
 * все получали номер, кратный `n`, и 100% скидку; ветка нулевой цены сразу ставила `PAID` без
 * платежа. Один залп из K запросов на границе акции давал K бесплатных подписок вместо одной.
 *
 * **ПОСЛЕ правки** расчёт номера и его «занятие» (создание подписки + запись `PAID`) выполняются
 * под [PriceService.withOrdinalLock] для пары (пользователь, scope): второй запрос читает уже
 * увеличенный `countPaid` и бесплатным не становится.
 *
 * **За владельцем:** окончательная атомарность при горизонтальном масштабировании требует
 * уникального индекса в БД `(site_user_id, scope, ordinal)` либо строки-счётчика с
 * `SELECT … FOR UPDATE` — это меняет схему БД. Сейчас `karaoke-web` развёрнут одним инстансом,
 * поэтому внутрипроцессной блокировки достаточно.
 */
class PriceServiceOrdinalLockTest {
    private val service = PriceService()

    @Test
    fun `параллельные покупки на границе акции получают разные порядковые номера`() {
        // countPaid имитируется счётчиком: под блокировкой каждый «платёж» увеличивает его на 1,
        // как запись PAID в БД. Скидка — каждая 5-я покупка.
        val paid = AtomicInteger(0)
        val free = AtomicInteger(0)
        val ordinals = mutableListOf<Int>()
        val threads = 20
        val start = CountDownLatch(1)

        val workers =
            (1..threads).map {
                thread {
                    start.await()
                    service.withOrdinalLock(userId = 42L, scope = "SITE") {
                        val ordinal = paid.get() + 1
                        if (ordinal % 5 == 0) free.incrementAndGet()
                        // «Занятие» номера: подписка становится PAID.
                        paid.incrementAndGet()
                        synchronized(ordinals) { ordinals.add(ordinal) }
                    }
                }
            }
        start.countDown()
        workers.forEach { it.join(TimeUnit.SECONDS.toMillis(10)) }

        assertEquals(threads, ordinals.size)
        assertEquals(threads, ordinals.toSet().size, "порядковые номера не должны повторяться: $ordinals")
        assertEquals(threads / 5, free.get(), "бесплатной должна быть ровно каждая 5-я покупка: $ordinals")
    }

    @Test
    fun `разные пользователи не блокируют друг друга`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)

        // Полосы блокировки — 64; выбираем двух пользователей, чьи ключи гарантированно попадают
        // в РАЗНЫЕ полосы (иначе тест проверял бы не то).
        val first = 1L
        val firstStripe = Math.floorMod("$first:SITE".hashCode(), 64)
        val second = generateSequence(2L) { it + 1 }.first { Math.floorMod("$it:SITE".hashCode(), 64) != firstStripe }

        // Два потока под РАЗНЫМИ ключами должны одновременно войти в критическую секцию; если бы
        // блокировка была глобальной, release не дождался бы второго входа и тест упал бы по таймауту.
        val a =
            thread {
                service.withOrdinalLock(userId = first, scope = "SITE") {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS), "второй пользователь должен войти параллельно")
                }
            }
        val b =
            thread {
                service.withOrdinalLock(userId = second, scope = "SITE") {
                    entered.countDown()
                    assertTrue(release.await(5, TimeUnit.SECONDS), "второй пользователь должен войти параллельно")
                }
            }
        assertTrue(entered.await(5, TimeUnit.SECONDS), "оба пользователя должны войти без ожидания друг друга")
        release.countDown()
        a.join(TimeUnit.SECONDS.toMillis(10))
        b.join(TimeUnit.SECONDS.toMillis(10))
    }

    @Test
    fun `блокировка реентрантна в пределах одного потока`() {
        val result =
            service.withOrdinalLock(userId = 42L, scope = "SITE") {
                service.withOrdinalLock(userId = 42L, scope = "SITE") { 7 }
            }

        assertEquals(7, result)
    }
}
