package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.sql.Timestamp

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
 * согласуется с моделью "одна строка = один платёж". Idempotence-Key завязан на СТРОКУ продления
 * (`renew-<userId>-sub<id>`): повторный прогон после сбоя переиспользует строку и возвращает тот же
 * платёж ЮKassa, а не списывает второй раз (см. `idempotenceKeyFor`).
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
    /**
     * Начисление оплаченной подписки (транзакция «статус + продление»). Общая точка с вебхуком
     * ЮKassa и оформлением подписки — см. [SubscriptionFulfillmentService].
     */
    private val fulfillmentService: SubscriptionFulfillmentService = SubscriptionFulfillmentService(),
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
        users.forEach { user -> tryRenew(user, db) }
    }

    /**
     * Автопродление одного пользователя (аудит #336, BR-514 — «списание до фиксации»).
     *
     * Порядок:
     *
     * 1. Строка продления создаётся и КОММИТИТСЯ **до** списания (ей же принадлежит
     *    `Idempotence-Key`), поэтому после сбоя следующий тик находит её и переиспользует.
     * 2. `Idempotence-Key` привязан к СТРОКЕ продления (`renew-<userId>-sub<id>`), а не к дате
     *    запуска шедулера. Раньше ключ был `renew-<userId>-<yyyy-MM-dd>`: сбой после списания
     *    приводил к новой строке на следующий день → новый ключ → **второе списание за тот же
     *    период**. Теперь повторный тик возвращает тот же платёж ЮKassa.
     * 3. `result.id` сохраняется в `renewal.yookassaPaymentId` — без него вебхук ЮKassa не мог
     *    найти подписку (`unknown_subscription`), и оплаченный период терялся навсегда.
     * 4. Смена статуса на PAID и продление `sitePremiumUntil` выполняются ОДНОЙ транзакцией
     *    ([SubscriptionFulfillmentService.fulfillPaid]) с условным `UPDATE ... WHERE status <> 'PAID'`:
     *    повторное начисление за один платёж невозможно, а сбой откатывает оба изменения.
     *
     * @param database шов для тестов: подменяется поддельным соединением.
     */
    internal fun tryRenew(
        user: SiteUser,
        database: KaraokeConnection = db,
    ) {
        val renewals = Subscription.loadByUser(user.id, database, storageService, storageApiClient)
        val latest =
            renewals
                .filter { it.scope == Subscription.SCOPE_SITE && it.status == Subscription.STATUS_PAID }
                .maxByOrNull { it.paidAt?.time ?: it.createdAt.time }
                ?: return
        if (!latest.autoRenew || latest.yookassaPaymentMethodId.isBlank()) return

        val renewal =
            renewals.firstOrNull { it.isUnfinishedRenewal(latest) }
                ?: createRenewal(user, latest, database)
                ?: return

        val description = "Автопродление подписки на сайт"

        // У незавершённой строки уже есть платёж (прошлый тик дошёл до ЮKassa) — НЕ списываем
        // повторно, а выясняем его судьбу у ЮKassa.
        if (renewal.yookassaPaymentId.isNotBlank()) {
            when (PaymentOrderReuse.decide(paymentService.verifyAndFetch(renewal.yookassaPaymentId)?.status)) {
                ReuseOutcome.ALREADY_PAID -> recordPaid(renewal, renewal.yookassaPaymentId, renewal.yookassaPaymentMethodId)
                ReuseOutcome.REUSE_PAYMENT, ReuseOutcome.UNAVAILABLE -> return
                ReuseOutcome.CREATE_NEW -> {
                    // Платёж отменён — деньги за этот период не списаны; строку закрываем, новое
                    // списание будет отдельной попыткой (новая строка → новый Idempotence-Key).
                    renewal.status = Subscription.STATUS_FAILED
                    renewal.save()
                    return
                }
            }
            return
        }

        val result = paymentService.chargeRecurring(renewal, description, user.email, idempotenceKeyFor(user.id, renewal.id))
        if (result == null || result.id.isBlank()) {
            // Списание не подтверждено (таймаут/сеть/4xx). Строку НЕ закрываем и НЕ создаём новую:
            // ЮKassa могла платёж уже создать, а ответ потеряться. Следующий тик переиспользует ЭТУ
            // ЖЕ строку и повторит ТОТ ЖЕ Idempotence-Key — ЮKassa вернёт существующий платёж,
            // второго списания не будет. Окончательный отказ карты придёт как status="canceled"
            // (ветка verify выше) и закроет строку.
            println("SubscriptionRenewalScheduler: автосписание не подтверждено для site_user_id=${user.id} (sub=${renewal.id}) — повтор на следующем тике с тем же ключом")
            return
        }

        // Деньги уже списаны — сначала фиксируем id платежа (иначе вебхук его не найдёт), затем
        // начисляем транзакцией. Сбой между этими шагами безопасен: следующий тик увидит id платежа
        // и доведёт начисление, не списывая повторно.
        renewal.yookassaPaymentId = result.id
        val chargedPaymentMethodId = result.payment_method?.id
        if (!chargedPaymentMethodId.isNullOrBlank()) renewal.yookassaPaymentMethodId = chargedPaymentMethodId
        renewal.save()
        if (result.status != "succeeded") {
            // Платёж создан, но ещё не подтверждён (pending) — начислим по вебхуку или следующему тику.
            return
        }
        recordPaid(renewal, result.id, renewal.yookassaPaymentMethodId)
    }

    /** Начисление продления транзакцией «статус PAID + sitePremiumUntil» (BR-514/BR-020). */
    private fun recordPaid(
        renewal: Subscription,
        paymentId: String,
        paymentMethodId: String,
    ) {
        val fulfilled = fulfillmentService.fulfillPaid(renewal, paymentId, paymentMethodId)
        if (fulfilled) {
            println("SubscriptionRenewalScheduler: автопродление успешно для site_user_id=${renewal.siteUserId}, sub=${renewal.id}")
        }
    }

    private fun createRenewal(
        user: SiteUser,
        latest: Subscription,
        database: KaraokeConnection,
    ): Subscription? {
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
                database = database,
                storageService = storageService,
                storageApiClient = storageApiClient,
            ) ?: return null
        renewal.yookassaPaymentMethodId = latest.yookassaPaymentMethodId
        renewal.status = Subscription.STATUS_PENDING
        // Строка коммитится ДО списания — она же несёт Idempotence-Key.
        renewal.save()
        return renewal
    }

    /**
     * Строка автопродления, по которой ещё не зафиксирован результат (сбой прошлого тика).
     *
     * Признак «это продление, а не ручная покупка» — непустой `yookassaPaymentMethodId`: он
     * копируется из оплаченной подписки при создании строки продления, а ручная PENDING-покупка
     * его не имеет (появляется только после успешного платежа, когда строка уже PAID). Без этого
     * различия шедулер мог бы «продлить» чужую ручную заявку, создав для неё ВТОРОЙ платёж
     * с другим Idempotence-Key.
     */
    private fun Subscription.isUnfinishedRenewal(latest: Subscription): Boolean =
        autoRenew &&
            scope == Subscription.SCOPE_SITE &&
            tariffId == latest.tariffId &&
            yookassaPaymentMethodId.isNotBlank() &&
            (status == Subscription.STATUS_PENDING || status == Subscription.STATUS_CREATED)

    companion object {
        /**
         * `Idempotence-Key` автопродления — стабилен для конкретной строки продления.
         *
         * Раньше ключ включал ДАТУ ЗАПУСКА (`renew-<userId>-<yyyy-MM-dd>`): сбой после списания
         * приводил к новой строке на следующий день, к новому ключу и ко ВТОРОМУ списанию за тот же
         * период. Ключ от строки (`renew-<userId>-sub<id>`): повторный тик по той же строке
         * возвращает уже созданный платёж ЮKassa, а новая попытка (новая строка) — новый ключ.
         */
        internal fun idempotenceKeyFor(
            userId: Long,
            renewalId: Long,
        ): String = "renew-$userId-sub$renewalId"
    }
}
