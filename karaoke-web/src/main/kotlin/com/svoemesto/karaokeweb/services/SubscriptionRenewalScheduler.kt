package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Автопродление подписки на сайт (scope=SITE, autoRenew=true). Живёт в karaoke-web (не на
 * admin-машине) — PaymentService/YooKassa WebClient/yookassa-proxy уже здесь, а именно karaoke-web
 * развёрнут на проде (karaoke-app там не разворачивается вовсе, см. DEVELOPMENT.md).
 *
 * "Активная цепочка" пользователя = его САМАЯ ПОЗДНЯЯ оплаченная SITE-подписка (по paidAt). Именно
 * её autoRenew/yookassaPaymentMethodId используются для решения о продлении — POST .../cancel
 * (PublicSubscriptionController) отключает autoRenew ровно у неё (личный кабинет всегда управляет
 * актуальной записью, не историческими).
 *
 * Каждый цикл продления создаёт НОВУЮ строку tbl_subscriptions (а не переиспользует старую) —
 * согласуется с моделью "одна строка = один платёж". Idempotence-Key завязан на (userId, дата) —
 * повторный прогон шедулера в тот же день не задвоит списание, даже если предыдущий вызов уже
 * создал PENDING-запись (см. newIdempotenceKeyFor).
 *
 * Промо-акции НЕ переприменяются при продлении — платится та же цена, что была оплачена в
 * последний раз (sub.finalPrice). Так проще и предсказуемее для пользователя; специальные акции
 * (NEW_USER_PERCENT/HAPPY_HOUR/NTH_FREE) по смыслу разовые/новым пользователям, не для ренты.
 */

/**
 * Класс Subscription Renewal Scheduler.
 *
 * @see archive/docs/features/async-process-queue.md
 */
@Service
class SubscriptionRenewalScheduler(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val paymentService: PaymentService,
) {
    private val db get() = WORKING_DATABASE

    private val log = LoggerFactory.getLogger(SubscriptionRenewalScheduler::class.java)

    /**
     * Метка времени последнего подробного сообщения об ошибке по конкретному пользователю.
     *
     * Аудит BR-515 (OpenProject #378): один сбойный пользователь не должен забивать лог на
     * каждом тике шедулера — поэтому подробная запись об ошибке пишется не чаще одного раза в
     * [FAILURE_LOG_INTERVAL_MS] на пользователя, а остальные случаи попадают в сводку.
     */
    private val failureLoggedAt = ConcurrentHashMap<Long, Long>()

    // Ежедневно в 03:00 — вне пиковой нагрузки. Захватывает и уже истёкшие подписки (грейс-период
    // для ретрая после сбойного списания в прошлый раз — см. класс-комментарий).
    @Scheduled(cron = "0 0 3 * * *")
    fun renewExpiringSiteSubscriptions() {
        val horizon = Timestamp(System.currentTimeMillis() + 24L * 3600_000L)
        val users = SiteUser.loadSitePremiumExpiringBefore(horizon, db, storageService, storageApiClient)
        if (users.isEmpty()) return
        log.info("subscription:renew: кандидатов на автопродление: {}", users.size)
        renewEach(users) { user -> tryRenew(user) }
    }

    /**
     * Обрабатывает кандидатов на автопродление ПО ОЧЕРЕДИ, изолируя сбой одного пользователя.
     *
     * Аудит BR-515 (OpenProject #378): раньше `users.forEach { tryRenew(user) }` шёл без
     * `try/catch` — исключение от ПЕРВОГО пользователя (обрыв соединения, таймаут, конфликт
     * IDENTITY) выходило из цикла, и все остальные кандидаты в этом тике НЕ продлевались: их
     * премиум истекал в течение суток, автосписания не было. Теперь сбой одного пользователя
     * логируется и цикл продолжается.
     *
     * @param users кандидаты на автопродление.
     * @param renew операция продления для одного пользователя (в проде — [tryRenew]).
     */
    internal fun renewEach(
        users: List<SiteUser>,
        renew: (SiteUser) -> Unit,
    ) {
        var failed = 0
        val failedIds = mutableListOf<Long>()
        users.forEach { user ->
            try {
                renew(user)
            } catch (e: Exception) {
                failed++
                failedIds.add(user.id)
                logRenewFailure(user.id, e)
            }
        }
        if (failed > 0) {
            log.warn(
                "subscription:renew: не удалось продлить {} из {} подписок (siteUserIds={})",
                failed,
                users.size,
                failedIds.take(MAX_FAILED_IDS_IN_SUMMARY),
            )
        }
    }

    /**
     * Подробная запись об ошибке продления — не чаще одного раза в [FAILURE_LOG_INTERVAL_MS] на
     * пользователя (защита от «забивания» лога одним сбойным пользователем на каждом тике).
     */
    private fun logRenewFailure(
        siteUserId: Long,
        e: Exception,
    ) {
        val now = System.currentTimeMillis()
        val last = failureLoggedAt[siteUserId] ?: 0L
        if (now - last < FAILURE_LOG_INTERVAL_MS) {
            log.debug(
                "subscription:renew:failed (повтор) siteUserId={} cause={}",
                siteUserId,
                e.message ?: e::class.simpleName,
            )
            return
        }
        failureLoggedAt[siteUserId] = now
        log.warn(
            "subscription:renew:failed siteUserId={} cause={}",
            siteUserId,
            e.message ?: e::class.simpleName,
            e,
        )
    }

    private fun tryRenew(user: SiteUser) {
        val latest =
            Subscription
                .loadByUser(user.id, db, storageService, storageApiClient)
                .filter { it.scope == Subscription.SCOPE_SITE && it.status == Subscription.STATUS_PAID }
                .maxByOrNull { it.paidAt?.time ?: it.createdAt.time }
                ?: return
        if (!latest.autoRenew || latest.yookassaPaymentMethodId.isBlank()) return

        val idempotenceKey = "renew-${user.id}-${DAY_FORMAT.format(java.util.Date())}"
        val renewal =
            Subscription.createNew(
                siteUserId = user.id,
                scope = Subscription.SCOPE_SITE,
                idSong = null,
                tariffId = latest.tariffId,
                periodDays = latest.periodDays,
                basePrice = latest.finalPrice,
                discount = 0.0,
                finalPrice = latest.finalPrice,
                promoApplied = "",
                autoRenew = true,
                database = db,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return
        renewal.yookassaPaymentMethodId = latest.yookassaPaymentMethodId
        renewal.status = Subscription.STATUS_PENDING
        renewal.save()

        val description = "Автопродление подписки на сайт"
        val result = paymentService.chargeRecurring(renewal, description, user.email, idempotenceKey)
        if (result == null || result.status != "succeeded") {
            renewal.status = Subscription.STATUS_FAILED
            renewal.save()
            log.warn(
                "subscription:renew:charge:failed siteUserId={} subId={}",
                user.id,
                renewal.id,
            )
            return
        }
        renewal.status = Subscription.STATUS_PAID
        renewal.paidAt = Timestamp(System.currentTimeMillis())
        renewal.save()

        val now = System.currentTimeMillis()
        val currentUntil = user.sitePremiumUntil?.time?.takeIf { it > now } ?: now
        user.sitePremiumUntil = Timestamp(currentUntil + renewal.periodDays * 24L * 3600_000L)
        user.save()
        log.info(
            "subscription:renew:ok siteUserId={} sitePremiumUntil={}",
            user.id,
            user.sitePremiumUntil,
        )
    }

    companion object {
        private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd")

        /** Минимальный интервал между подробными сообщениями об ошибке по одному пользователю. */
        private const val FAILURE_LOG_INTERVAL_MS = 6L * 3600_000L

        /** Сколько id неудачных пользователей перечислять в сводке за тик. */
        private const val MAX_FAILED_IDS_IN_SUMMARY = 10
    }
}
