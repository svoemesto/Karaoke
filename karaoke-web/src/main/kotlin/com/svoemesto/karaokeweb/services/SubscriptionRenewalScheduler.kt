package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.WORKING_DATABASE
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.text.SimpleDateFormat

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
@Service
class SubscriptionRenewalScheduler(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val paymentService: PaymentService,
) {
    private val db get() = WORKING_DATABASE

    // Ежедневно в 03:00 — вне пиковой нагрузки. Захватывает и уже истёкшие подписки (грейс-период
    // для ретрая после сбойного списания в прошлый раз — см. класс-комментарий).
    @Scheduled(cron = "0 0 3 * * *")
    fun renewExpiringSiteSubscriptions() {
        val horizon = Timestamp(System.currentTimeMillis() + 24L * 3600_000L)
        val users = SiteUser.loadSitePremiumExpiringBefore(horizon, db, storageService, storageApiClient)
        if (users.isEmpty()) return
        println("SubscriptionRenewalScheduler: кандидатов на автопродление: ${users.size}")
        users.forEach { user -> tryRenew(user) }
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
            println("SubscriptionRenewalScheduler: автосписание не удалось для site_user_id=${user.id} (sub=${renewal.id})")
            return
        }
        renewal.status = Subscription.STATUS_PAID
        renewal.paidAt = Timestamp(System.currentTimeMillis())
        renewal.save()

        val now = System.currentTimeMillis()
        val currentUntil = user.sitePremiumUntil?.time?.takeIf { it > now } ?: now
        user.sitePremiumUntil = Timestamp(currentUntil + renewal.periodDays * 24L * 3600_000L)
        user.save()
        println(
            "SubscriptionRenewalScheduler: автопродление успешно для site_user_id=${user.id}, новый sitePremiumUntil=${user.sitePremiumUntil}",
        )
    }

    companion object {
        private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd")
    }
}
