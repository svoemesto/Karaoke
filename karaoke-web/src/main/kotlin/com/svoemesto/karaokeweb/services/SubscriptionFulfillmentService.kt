package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.model.Subscription
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.Timestamp

/**
 * Начисление оплаченной подписки (fulfillment) — **единая точка** для всех путей, где деньги
 * УЖЕ списаны:
 *
 * - вебхук ЮKassa (`PublicPaymentController.webhook`, аудит #336 BR-020);
 * - оформление подписки, платёж которой уже `succeeded` (`PublicSubscriptionController.create`,
 *   BR-021 — вебхук мог не дойти, но платёж оплачен);
 * - автопродление (`SubscriptionRenewalScheduler.tryRenew`, BR-514);
 * - бесплатная (акционная) подписка — цена 0, «оплата» без ЮKassa.
 *
 * Ключевой инвариант: **смена статуса подписки и начисление (продление
 * `SiteUser.sitePremiumUntil`) фиксируются ОДНОЙ транзакцией**, а захват подписки делается
 * условным `UPDATE ... WHERE status <> 'PAID'` — число обновлённых строк и есть признак
 * «мы первые». Отсюда:
 *
 * - повторный вебхук или параллельный ретрай **не продлевают срок дважды** (раньше оба
 *   проходили проверку «все PAID» и оба продлевали `sitePremiumUntil`);
 * - падение между «статус = PAID» и «продление» больше **не оставляет оплатившего без
 *   доступа**: транзакция откатывается целиком, вызывающий возвращает ошибку, и ЮKassa
 *   повторяет доставку вебхука (раньше: статус PAID сохранялся, начисление терялось, повторный
 *   вебхук видел «все PAID» и отвечал 200 no-op → «платёж прошёл, подписки нет»).
 *
 * Признак «уже обработано» реализован **БЕЗ новой колонки** (миграция БД не требуется):
 * условный UPDATE по `status` — атомарная операция СУБД.
 *
 * @see knowledge/domains/monetization/domain.md § Domain Invariants
 */
@Service
class SubscriptionFulfillmentService {
    private val log = LoggerFactory.getLogger(SubscriptionFulfillmentService::class.java)

    /**
     * Переводит подписку в PAID и начисляет её (продление `sitePremiumUntil` для `scope=SITE`)
     * В ОДНОЙ транзакции.
     *
     * @param sub подписка (несёт свои `database`/`storageService`/`storageApiClient`).
     * @param yookassaPaymentId id платежа ЮKassa (пусто — не перезаписывать; для автопродления
     *   это id СВЕЖЕГО списания, для вебхука — уже сохранённый).
     * @param paymentMethodId id способа оплаты ЮKassa (пусто — не перезаписывать).
     * @return `true` — начисление выполнено этим вызовом; `false` — подписку уже обработал
     *   другой вызов (параллельный вебхук/ретрай) — это НЕ ошибка.
     * @throws Exception транзакция откатывается; вызывающий обязан вернуть ошибку, чтобы
     *   ЮKassa повторила доставку вебхука (иначе потерянное начисление не восстановится).
     */
    fun fulfillPaid(
        sub: Subscription,
        yookassaPaymentId: String = "",
        paymentMethodId: String = "",
    ): Boolean =
        fulfillPaid(sub, yookassaPaymentId, paymentMethodId) { subscription ->
            SiteUser.getSiteUserById(
                subscription.siteUserId,
                subscription.database,
                subscription.storageService,
                subscription.storageApiClient,
            )
        }

    /**
     * Та же транзакция с явным источником пользователя — шов для тестов (в проде всегда
     * [SiteUser.getSiteUserById]).
     */
    internal fun fulfillPaid(
        sub: Subscription,
        yookassaPaymentId: String,
        paymentMethodId: String,
        userProvider: (Subscription) -> SiteUser?,
    ): Boolean {
        val connection =
            sub.database.getConnection()
                ?: throw IllegalStateException("Нет соединения с БД — начисление подписки ${sub.id} невозможно")
        val wasAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val claimed = claimPaid(connection, sub.id, yookassaPaymentId, paymentMethodId)
            if (!claimed) {
                // Подписку уже перевёл в PAID другой обработчик (параллельный вебхук/ретрай) —
                // начисление повторять нельзя.
                connection.commit()
                log.debug("Подписка {} уже обработана (status=PAID) — начисление пропущено", sub.id)
                return false
            }
            applyFulfillment(sub, connection, userProvider)
            connection.commit()
            // Держим объект в согласии с БД: вызывающий может строить на нём ответ клиенту.
            sub.status = Subscription.STATUS_PAID
            sub.paidAt = Timestamp(System.currentTimeMillis())
            if (yookassaPaymentId.isNotBlank()) sub.yookassaPaymentId = yookassaPaymentId
            if (paymentMethodId.isNotBlank()) sub.yookassaPaymentMethodId = paymentMethodId
            return true
        } catch (e: Exception) {
            runCatching { connection.rollback() }
            log.error("Начисление подписки {} не выполнено — транзакция откатывается: {}", sub.id, e.message, e)
            throw e
        } finally {
            runCatching { connection.autoCommit = wasAutoCommit }
        }
    }

    /**
     * Атомарный захват подписки: `UPDATE ... WHERE id = ? AND status <> 'PAID'`.
     * Ноль обновлённых строк = «подписку уже обработал другой вызов».
     */
    private fun claimPaid(
        connection: Connection,
        subId: Long,
        yookassaPaymentId: String,
        paymentMethodId: String,
    ): Boolean {
        val extraColumns =
            buildList {
                if (yookassaPaymentId.isNotBlank()) add("yookassa_payment_id = ?")
                if (paymentMethodId.isNotBlank()) add("yookassa_payment_method_id = ?")
            }
        val sql =
            "UPDATE tbl_subscriptions SET status = ?, paid_at = ?" +
                (if (extraColumns.isEmpty()) "" else ", " + extraColumns.joinToString(", ")) +
                " WHERE id = ? AND status <> ?"
        connection.prepareStatement(sql).use { ps ->
            var idx = 1
            ps.setString(idx++, Subscription.STATUS_PAID)
            ps.setTimestamp(idx++, Timestamp(System.currentTimeMillis()))
            if (yookassaPaymentId.isNotBlank()) ps.setString(idx++, yookassaPaymentId)
            if (paymentMethodId.isNotBlank()) ps.setString(idx++, paymentMethodId)
            ps.setLong(idx++, subId)
            ps.setString(idx, Subscription.STATUS_PAID)
            return ps.executeUpdate() == 1
        }
    }

    /**
     * Начисление для `scope=SITE` — продление `sitePremiumUntil` от `max(now, текущая дата)`:
     * повторная оплата НЕ теряет ранее оплаченный хвост срока.
     *
     * Строка пользователя блокируется (`SELECT ... FOR UPDATE`), чтобы два одновременных
     * начисления разных подписок не вычитали одну и ту же базу (иначе одна оплата терялась бы).
     */
    private fun applyFulfillment(
        sub: Subscription,
        connection: Connection,
        userProvider: (Subscription) -> SiteUser?,
    ) {
        if (sub.scope != Subscription.SCOPE_SITE) return
        val user =
            userProvider(sub)
                ?: throw IllegalStateException("Пользователь ${sub.siteUserId} не найден — начисление подписки ${sub.id} невозможно")
        val currentUntil = readPremiumUntil(connection, sub.siteUserId)
        val now = System.currentTimeMillis()
        val base = currentUntil?.time?.takeIf { it > now } ?: now
        val newUntil = Timestamp(base + sub.periodDays * 24L * 3600_000L)
        val updated =
            connection.prepareStatement("UPDATE tbl_site_users SET site_premium_until = ? WHERE id = ?").use { ps ->
                ps.setTimestamp(1, newUntil)
                ps.setLong(2, sub.siteUserId)
                ps.executeUpdate()
            }
        if (updated != 1) {
            throw IllegalStateException("Продление премиума не зафиксировано: site_user_id=${sub.siteUserId}, подписка ${sub.id}")
        }
        user.sitePremiumUntil = newUntil
        // Приветственное сообщение — только после фиксации продления (иначе «премиум» ещё не выдан).
        user.sendWelcomePremiumMessageIfNeeded()
    }

    /** Текущий срок премиума пользователя под блокировкой строки (`FOR UPDATE`). */
    private fun readPremiumUntil(
        connection: Connection,
        siteUserId: Long,
    ): Timestamp? =
        connection.prepareStatement("SELECT site_premium_until FROM tbl_site_users WHERE id = ? FOR UPDATE").use { ps ->
            ps.setLong(1, siteUserId)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getTimestamp("site_premium_until") else null
            }
        }
}
