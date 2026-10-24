package com.svoemesto.karaokeweb.controllers

import com.svoemesto.karaokeweb.WORKING_DATABASE

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.PriceTariff
import com.svoemesto.karaokeapp.model.Song
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeweb.config.SiteAuthInterceptor
import com.svoemesto.karaokeweb.services.PaymentOrderReuse
import com.svoemesto.karaokeweb.services.PaymentService
import com.svoemesto.karaokeweb.services.PriceService
import com.svoemesto.karaokeweb.services.ReuseOutcome
import com.svoemesto.karaokeweb.services.SubscriptionFulfillmentService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Оформление подписки — единая точка для обоих видов (термин «покупка» нигде не используется,
 * см. план монетизации): scope=SONG (бессрочная подписка на песню) и scope=SITE (периодическая
 * подписка на сайт, по умолчанию с автопродлением). Весь класс под /api/public/account — проходит
 * через SiteAuthInterceptor (WebMvcConfig), доступно только вошедшим.
 *
 * Цена всегда пересчитывается на сервере (PriceService) — клиент не может её подменить, см. план,
 * Verification. Fulfillment (реальная разблокировка/продление) происходит в PublicPaymentController
 * по вебхуку ЮKassa, а не здесь — до оплаты запись остаётся в CREATED/PENDING.
 *
 * Идемпотентность оформления (аудит #336, BR-021): незавершённый (PENDING) заказ
 * пользователя на тот же тариф (scope=SITE) или на ту же песню (scope=SONG) **переиспользуется**
 * при любом неотменённом статусе платежа:
 *
 * - `pending` / `waiting_for_capture` — отдаём тот же `confirmationUrl`, новый платёж не создаём;
 * - `succeeded` — платёт уже оплачен (вебхук мог не дойти): начисляем подписку и НЕ создаём
 *   второй платёж (раньше здесь создавался новый платёж → двойное списание);
 * - `canceled` — заказ мёртвый, помечаем FAILED и создаём новый;
 * - ЮKassa недоступна (verify вернул null) — новый платёж НЕ создаём (503), повтор позже.
 */

/**
 * Контроллер (HTTP/WebSocket endpoints) для public subscription .
 *
 * @see AGENTS.md
 */
@RestController
@RequestMapping("/api/public/account/subscription")
class PublicSubscriptionController(
    private val storageService: KaraokeStorageService,
    private val storageApiClient: StorageApiClient,
    private val priceService: PriceService,
    private val paymentService: PaymentService,
    /**
     * Начисление оплаченной подписки (транзакция «статус + продление»). Шов для тестов.
     */
    private val fulfillmentService: SubscriptionFulfillmentService = SubscriptionFulfillmentService(),
    @Value("\${app.public-site-url}") private val publicSiteUrl: String,
) {
    private val db get() = WORKING_DATABASE

    private fun currentUser(request: HttpServletRequest): SiteUser = request.getAttribute(SiteAuthInterceptor.SITE_USER_ATTR) as SiteUser

    private fun tariffFor(
        scope: String,
        songId: Long?,
        tariffId: Long?,
        database: KaraokeConnection = WORKING_DATABASE,
    ): PriceTariff? {
        if (tariffId != null) {
            PriceTariff
                .getById(tariffId, database, storageService, storageApiClient)
                ?.takeIf { it.scope == scope && it.isActive }
                ?.let { return it }
        }
        return PriceTariff.getDefault(scope, database, storageService, storageApiClient)
    }

    // ---- Список активных тарифов (для выбора на /premium) --------------------------------------

    // paymentsEnabled — заданы ли ключи ЮKassa (PaymentService.hasCredentials()). Пока их нет, фронт
    // должен отключать кнопку "Оплатить" вместо того, чтобы пользователь ловил payment_unavailable
    // после клика. Бесплатных (акция 100%) подписок это не касается — они не ходят в ЮKassa вовсе.
    @GetMapping("/tariffs")
    fun tariffs(
        @RequestParam scope: String,
    ): List<Map<String, Any?>> {
        val paymentsEnabled = paymentService.hasCredentials()
        return PriceTariff
            .loadAll(db, storageService, storageApiClient)
            .filter { it.scope == scope && it.isActive }
            .map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name,
                    "priceRub" to it.priceRub,
                    "periodDays" to it.periodDays,
                    "isDefault" to it.isDefault,
                    "paymentsEnabled" to paymentsEnabled,
                )
            }
    }

    // ---- Цена (для показа в CheckoutModal ДО оплаты) -------------------------------------------

    @GetMapping("/price")
    fun price(
        @RequestParam scope: String,
        @RequestParam(required = false) songId: Long?,
        @RequestParam(required = false) tariffId: Long?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        val tariff =
            tariffFor(scope, songId, tariffId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))
        val result = priceService.computePrice(tariff, user, db, storageService, storageApiClient)
        return ResponseEntity.ok(
            mapOf(
                "tariffId" to tariff.id,
                "tariffName" to tariff.name,
                "periodDays" to tariff.periodDays,
                "base" to result.base,
                "discount" to result.discount,
                "final" to result.final,
                "promoApplied" to result.promoApplied,
                "personalDiscountPercent" to result.personalDiscountPercent,
                // Отключаем "Оплатить" на фронте, пока не заданы ключи ЮKassa — бесплатных (final=0)
                // подписок это не касается, они не ходят в ЮKassa вовсе.
                "paymentsEnabled" to paymentService.hasCredentials(),
            ),
        )
    }

    // ---- Оформление ------------------------------------------------------------------------------

    @PostMapping("/create")
    fun create(
        @RequestParam scope: String,
        @RequestParam(required = false) songId: Long?,
        @RequestParam(required = false) tariffId: Long?,
        @RequestParam disclaimerAccepted: Boolean,
        @RequestParam(required = false) autoRenew: Boolean?,
        request: HttpServletRequest,
    ): ResponseEntity<Any> =
        handleCreate(
            scope = scope,
            songId = songId,
            tariffId = tariffId,
            disclaimerAccepted = disclaimerAccepted,
            autoRenew = autoRenew,
            request = request,
            database = WORKING_DATABASE,
        )

    /**
     * Оформление подписки. Шов для тестов: [database] подменяется поддельным соединением.
     */
    internal fun handleCreate(
        scope: String,
        songId: Long?,
        tariffId: Long?,
        disclaimerAccepted: Boolean,
        autoRenew: Boolean?,
        request: HttpServletRequest,
        database: KaraokeConnection,
    ): ResponseEntity<Any> {
        if (!disclaimerAccepted) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "disclaimer_required"))
        val user = currentUser(request)

        if (scope == Subscription.SCOPE_SONG) {
            if (songId == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "song_id_required"))
            val song =
                Song.loadFromDbById(songId, database, storageService = storageService, storageApiClient = storageApiClient)
                    ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "song_not_found"))
            // id_tariff: 0 (дефолт новой песни) = подписка разрешена тарифом по умолчанию; -1 = автор
            // явно запретил подписку на эту песню. Любое другое значение зарезервировано на будущее.
            if (song.idTariff <
                0
            ) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "song_not_for_subscription"))
            }
            if (Subscription.isSubscribedToSong(user.id, songId, database, storageService, storageApiClient)) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "already_subscribed"))
            }
        } else if (scope != Subscription.SCOPE_SITE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "invalid_scope"))
        }

        val tariff =
            tariffFor(scope, songId, tariffId, database)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "no_tariff"))

        // Повторный клик "Оформить" до того, как первый заказ обработан (см. инцидент 2026-07-09 —
        // двойной клик создал две отдельные PENDING-подписки и два отдельных платежа в ЮKassa):
        // если у пользователя уже есть незавершённый заказ на этот же тариф сайта (scope=SITE) или
        // на ту же песню (scope=SONG), новый платёж НЕ создаём — переиспользуем существующий.
        val pending =
            if (scope == Subscription.SCOPE_SITE) {
                Subscription.findPendingSite(user.id, tariff.id, database, storageService, storageApiClient)
            } else {
                Subscription.findPendingSong(user.id, songId!!, database, storageService, storageApiClient)
            }
        if (pending != null) {
            // Платёж прошлого запроса мог не создаться (503) — строка осталась CREATED без
            // yookassa_payment_id. Досоздаём платёж для НЕЁ ЖЕ (Idempotence-Key "sub-<id>-<createdAt>"
            // тот же), а не заводим вторую строку: иначе пользователь получил бы два счёта.
            val reused =
                if (pending.yookassaPaymentId.isBlank()) {
                    createPaymentForExisting(pending, scope, songId, tariff, user)
                } else {
                    reuseExistingOrder(pending)
                }
            if (reused != null) return reused
        }

        // Аудит BR-005 (OpenProject #378): цена и создание подписки считаются под блокировкой
        // (пользователь, scope). Раньше `countPaid(...) + 1` для акции «каждая N-я бесплатно»
        // читался без блокировки: залп параллельных `POST /create` на границе акции давал всем
        // один и тот же номер, кратный N, и каждый получал 100% скидку (ветка нулевой цены
        // ставит PAID без платежа). Теперь порядковый номер и его «занятие» (запись PAID)
        // атомарны: второй запрос читает уже увеличенный countPaid.
        var freeByPromo = false
        val sub =
            priceService.withOrdinalLock(user.id, scope) {
                val price = priceService.computePrice(tariff, user, database, storageService, storageApiClient)
                val created =
                    Subscription.createNew(
                        siteUserId = user.id,
                        scope = scope,
                        idSong = if (scope == Subscription.SCOPE_SONG) songId else null,
                        tariffId = tariff.id,
                        periodDays = tariff.periodDays,
                        basePrice = price.base,
                        discount = price.discount,
                        finalPrice = price.final,
                        promoApplied = price.promoApplied ?: "",
                        // По умолчанию включено для подписки на сайт (см. план: "автопродление по умолчанию"),
                        // для подписки на песню значение не имеет смысла (бессрочная, продлевать нечего).
                        autoRenew = if (scope == Subscription.SCOPE_SITE) (autoRenew ?: true) else false,
                        database = database,
                        storageService = storageService,
                        storageApiClient = storageApiClient,
                    )
                // Акция довела цену до нуля — фиксируем как оплаченную сразу, без похода в ЮKassa
                // (там ненулевой минимум суммы платежа). Запись PAID — ВНУТРИ блокировки: именно
                // она «занимает» порядковый номер для следующего запроса (BR-005). Статус и
                // начисление фиксируются ОДНОЙ транзакцией [SubscriptionFulfillmentService]
                // (#365, BR-020/BR-021): «оплачено, но не начислено» не остаётся.
                if (created != null && price.final <= 0.0) {
                    fulfillmentService.fulfillPaid(created, "", "")
                    freeByPromo = true
                }
                created
            } ?: return ResponseEntity.internalServerError().body(mapOf("error" to "create_failed"))

        if (freeByPromo) {
            return ResponseEntity.ok(mapOf("subscriptionId" to sub.id, "status" to Subscription.STATUS_PAID, "confirmationUrl" to null))
        }

        val description = if (scope == Subscription.SCOPE_SONG) "Подписка на песню (id $songId)" else "Подписка на сайт (${tariff.name})"
        val returnUrl = "$publicSiteUrl/subscription/return?subId=${sub.id}"
        val payment =
            paymentService.createPayment(
                sub = sub,
                description = description,
                email = user.email,
                returnUrl = returnUrl,
                // save_payment_method=true требует, чтобы менеджер ЮKassa включил рекуррентные платежи
                // для магазина (это отдельное разрешение, не совпадает с базовым одобрением "На своём
                // сайте", и не переключается тумблером в кабинете самостоятельно). Пока это не включено,
                // ЮKassa отвечает 403 — PaymentService.createPayment сам перехватывает это и повторяет
                // платёж без save_payment_method, чтобы оформление подписки не ломалось (см. её kdoc).
                saveMethod = scope == Subscription.SCOPE_SITE && sub.autoRenew,
            )
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf(
                    "error" to "payment_unavailable",
                    "subscriptionId" to sub.id,
                ),
            )
        }
        sub.yookassaPaymentId = payment.id
        sub.status = Subscription.STATUS_PENDING
        sub.save()
        return ResponseEntity.ok(
            mapOf(
                "subscriptionId" to sub.id,
                "status" to sub.status,
                "confirmationUrl" to payment.confirmation?.confirmation_url,
            ),
        )
    }

    /**
     * Переиспользование незавершённого заказа вместо создания нового (аудит #336, BR-021).
     *
     * Решение о статусе принимает [PaymentOrderReuse] — общий с корзиной (BR-014): переиспользуем
     * ЛЮБОЙ не-`canceled` статус, а не только `pending`. Иначе задержавшийся вебхук (`succeeded`)
     * приводил к созданию второго платежа и второму списанию.
     *
     * @return ответ, который надо отдать клиенту, либо `null` — «заказ мёртвый, создавай новый».
     */
    private fun reuseExistingOrder(pending: Subscription): ResponseEntity<Any>? {
        val verified = paymentService.verifyAndFetch(pending.yookassaPaymentId)
        return when (PaymentOrderReuse.decide(verified?.status)) {
            ReuseOutcome.REUSE_PAYMENT ->
                ResponseEntity.ok(
                    mapOf(
                        "subscriptionId" to pending.id,
                        "status" to pending.status,
                        "confirmationUrl" to verified?.confirmation?.confirmation_url,
                    ),
                )
            ReuseOutcome.ALREADY_PAID -> {
                // Платёж уже оплачен (вебхук мог задержаться или не дойти) — начисляем подписку
                // сами и НЕ создаём второй платёж.
                fulfillmentService.fulfillPaid(pending, pending.yookassaPaymentId, verified?.payment_method?.id ?: "")
                ResponseEntity.ok(
                    mapOf(
                        "subscriptionId" to pending.id,
                        "status" to Subscription.STATUS_PAID,
                        "confirmationUrl" to null,
                    ),
                )
            }
            ReuseOutcome.CREATE_NEW -> {
                // Мёртвый заказ — помечаем FAILED (чтобы он больше не находился как незавершённый) и
                // создаём новый.
                pending.status = Subscription.STATUS_FAILED
                pending.save()
                null
            }
            ReuseOutcome.UNAVAILABLE ->
                // ЮKassa недоступна или статус неизвестен — новый платёж НЕ создаём: иначе
                // пользователь оплатит дважды.
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                    mapOf("error" to "payment_unavailable", "subscriptionId" to pending.id),
                )
        }
    }

    /**
     * Досоздание платежа для уже существующей строки, оставшейся без `yookassa_payment_id`
     * (сбой прошлого запроса) — вместо создания второй строки и второго платежа (BR-021).
     *
     * `Idempotence-Key` в `PaymentService.createPayment` привязан к `sub-<id>-<createdAt>`, то есть
     * повтор для той же строки не создаёт дубликат платежа в ЮKassa.
     *
     * Платёж не создался — 503 (fail-closed): вторую строку и второй счёт не заводим.
     */
    private fun createPaymentForExisting(
        pending: Subscription,
        scope: String,
        songId: Long?,
        tariff: PriceTariff,
        user: SiteUser,
    ): ResponseEntity<Any> {
        val description = if (scope == Subscription.SCOPE_SONG) "Подписка на песню (id $songId)" else "Подписка на сайт (${tariff.name})"
        val payment =
            paymentService.createPayment(
                sub = pending,
                description = description,
                email = user.email,
                returnUrl = "$publicSiteUrl/subscription/return?subId=${pending.id}",
                saveMethod = scope == Subscription.SCOPE_SITE && pending.autoRenew,
            )
        if (payment == null || payment.id.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                mapOf("error" to "payment_unavailable", "subscriptionId" to pending.id),
            )
        }
        pending.yookassaPaymentId = payment.id
        pending.status = Subscription.STATUS_PENDING
        pending.save()
        return ResponseEntity.ok(
            mapOf(
                "subscriptionId" to pending.id,
                "status" to pending.status,
                "confirmationUrl" to payment.confirmation?.confirmation_url,
            ),
        )
    }

    // ---- Список (личный кабинет) -----------------------------------------------------------------

    @GetMapping("/list")
    fun list(request: HttpServletRequest): List<Map<String, Any?>> {
        val user = currentUser(request)
        return Subscription.loadByUser(user.id, db, storageService, storageApiClient).map { sub ->
            val songName =
                sub.idSong?.let {
                    Song.loadFromDbById(it, db, storageService = storageService, storageApiClient = storageApiClient)?.songName
                }
            mapOf(
                "id" to sub.id,
                "scope" to sub.scope,
                "idSong" to sub.idSong,
                "songName" to songName,
                "finalPrice" to sub.finalPrice,
                "status" to sub.status,
                "autoRenew" to sub.autoRenew,
                "createdAt" to sub.createdAt.toString(),
                "paidAt" to sub.paidAt?.toString(),
                "orderId" to sub.orderId,
            )
        }
    }

    // Отключает автопродление подписки на сайт — доступ доживает до текущего sitePremiumUntil,
    // повторного списания больше не будет (см. план: POST /cancel). Также отвязывает сохранённый
    // способ оплаты (yookassaPaymentMethodId) — самостоятельный, без обращения в поддержку,
    // интерфейс отвязки карты обязателен по условиям подключения автоплатежей ЮKassa. Отвязываем
    // именно здесь (а не отдельным эндпоинтом), т.к. карта без autoRenew всё равно бесполезна —
    // раздельные действия только создавали бы противоречивое состояние (autoRenew=true без карты).
    @PostMapping("/cancel")
    fun cancel(
        @RequestParam id: Long,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val user = currentUser(request)
        val sub =
            Subscription
                .getById(id, db, storageService, storageApiClient)
                ?.takeIf { it.siteUserId == user.id && it.scope == Subscription.SCOPE_SITE }
                ?: return ResponseEntity.notFound().build()
        sub.autoRenew = false
        sub.yookassaPaymentMethodId = ""
        sub.save()
        return ResponseEntity.ok(mapOf("ok" to true))
    }
}
