package com.svoemesto.karaokeweb.services

import com.svoemesto.karaokeapp.KaraokeConnection
import com.svoemesto.karaokeapp.model.SiteUser
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_REMOTE_HOST
import com.svoemesto.karaokeapp.services.DB_REMOTE_PORT
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import com.svoemesto.karaokeapp.services.KSS_APP
import com.svoemesto.karaokeapp.services.KaraokeStorageService
import com.svoemesto.karaokeapp.services.SAC_APP
import com.svoemesto.karaokeapp.services.StorageApiClient
import com.svoemesto.karaokeapp.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp

/**
 * Регрессионные тесты «до/после» для транзакционного начисления подписки
 * (аудит #336, BR-020; OpenProject #365).
 *
 * **Что было не так.** `PublicPaymentController.webhook` переводил подписку в `PAID` и сохранял
 * это ДО начисления (`sub.save()`, затем `applyFulfillment`), вне транзакции. Следствия:
 *
 * 1. повторная доставка вебхука (ЮKassa повторяет при таймауте) видела «все PAID» и возвращала
 *    200 no-op — оплаченный период не начислялся никогда;
 * 2. два параллельных ретрая оба проходили проверку `all { PAID }` и оба продлевали
 *    `sitePremiumUntil` — двойное продление за один платёж.
 *
 * **После правки** статус и начисление фиксируются одной транзакцией, а захват подписки —
 * условным `UPDATE ... WHERE status <> 'PAID'`: число обновлённых строк решает, кто первый.
 * Второй вызов получает 0 строк и НЕ трогает `sitePremiumUntil`.
 *
 * **До правки** тесты «повторный вебхук не продлевает срок второй раз» и «сбой начисления
 * откатывает и статус, и продление» падали: продление выполнялось повторно, отката не было.
 *
 * @see specs/365-pkg-money-idempotency/report.md
 */
class SubscriptionFulfillmentServiceTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpEnv() {
            KaraokeWebTestEnv.init()
            // Модели karaoke-app (Subscription/SiteUser) в конструкторе по умолчанию тянут
            // lateinit-глобалы karaoke-app: строки подключения (статическая инициализация
            // `Constants.kt` создаёт `Connection.local()`) и хранилище. В тестах — заглушки.
            DB_LOCAL_POSTGRES_USER = "test"
            DB_LOCAL_POSTGRES_PASSWORD = "test"
            DB_SERVER_POSTGRES_USER = "test"
            DB_SERVER_POSTGRES_PASSWORD = "test"
            DB_REMOTE_HOST = "localhost"
            DB_REMOTE_PORT = "5432"
            KSS_APP = Mockito.mock(KaraokeStorageService::class.java)
            SAC_APP = Mockito.mock(StorageApiClient::class.java)
        }
    }

    /** Поддельное соединение: `KaraokeConnection` — шов проекта для тестов без живой БД. */
    private class FakeDb(
        private val conn: Connection,
    ) : KaraokeConnection("jdbc:test", "test", "test", "TEST") {
        override fun getConnection(): Connection = conn
    }

    /**
     * Поддельный JDBC: различает SQL захвата подписки, продления премиума и чтения срока.
     *
     * @param claimRows сколько строк «захватил» условный UPDATE (0 = подписку уже обработали).
     */
    private class FakeJdbc(
        var claimRows: Int = 1,
    ) {
        val connection: Connection = Mockito.mock(Connection::class.java)
        val statements = mutableListOf<String>()

        val claim: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        val premium: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        private val selectUser: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
        private val userRow: ResultSet = Mockito.mock(ResultSet::class.java)

        var premiumUpdateRows: Int = 1
        var premiumFailure: RuntimeException? = null
        var currentPremiumUntil: Timestamp? = null

        init {
            Mockito.`when`(connection.autoCommit).thenReturn(true)
            Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenAnswer { invocation ->
                val sql = invocation.getArgument<String>(0)
                statements.add(sql)
                when {
                    sql.startsWith("UPDATE tbl_subscriptions") -> claim
                    sql.startsWith("UPDATE tbl_site_users") -> premium
                    sql.startsWith("SELECT site_premium_until") -> selectUser
                    else -> claim
                }
            }
            Mockito.`when`(claim.executeUpdate()).thenAnswer { claimRows }
            Mockito.`when`(premium.executeUpdate()).thenAnswer {
                premiumFailure?.let { throw it }
                premiumUpdateRows
            }
            Mockito.`when`(selectUser.executeQuery()).thenReturn(userRow)
            Mockito.`when`(userRow.next()).thenReturn(true)
            Mockito.`when`(userRow.getTimestamp("site_premium_until")).thenAnswer { currentPremiumUntil }
        }

        fun premiumUpdates(): List<String> = statements.filter { it.startsWith("UPDATE tbl_site_users") }

        fun claimUpdates(): List<String> = statements.filter { it.startsWith("UPDATE tbl_subscriptions") }
    }

    private fun subscription(
        jdbc: FakeJdbc,
        id: Long = 10,
        scope: String = Subscription.SCOPE_SITE,
        periodDays: Int = 30,
    ): Subscription =
        Subscription(database = FakeDb(jdbc.connection)).apply {
            this.id = id
            this.scope = scope
            this.siteUserId = 5
            this.periodDays = periodDays
            this.finalPrice = 300.0
        }

    /** Пользователь без приветственного сообщения (иначе тест ушёл бы в чат/БД). */
    private fun user(id: Long = 5): SiteUser =
        SiteUser().apply {
            this.id = id
            this.welcomeMessageSent = true
        }

    private fun fulfill(
        jdbc: FakeJdbc,
        sub: Subscription,
        paymentId: String = "pay-1",
        paymentMethodId: String = "pm-1",
        userProvider: (Subscription) -> SiteUser? = { user() },
    ): Boolean = SubscriptionFulfillmentService().fulfillPaid(sub, paymentId, paymentMethodId, userProvider)

    @Test
    fun `начисление выполняется одной транзакцией с захватом подписки`() {
        val jdbc = FakeJdbc()
        val sub = subscription(jdbc)

        assertTrue(fulfill(jdbc, sub))

        assertEquals(1, jdbc.claimUpdates().size, "захват подписки — ровно один UPDATE: ${jdbc.statements}")
        assertTrue(
            jdbc.claimUpdates().first().contains("status <> ?"),
            "захват обязан быть условным: ${jdbc.claimUpdates().first()}",
        )
        Mockito
            .verify(jdbc.claim, Mockito.atLeastOnce())
            .setString(Mockito.anyInt(), Mockito.eq(Subscription.STATUS_PAID))
        assertEquals(1, jdbc.premiumUpdates().size, "продление премиума: ${jdbc.statements}")
        Mockito.verify(jdbc.connection).commit()
        assertEquals(Subscription.STATUS_PAID, sub.status, "объект подписки согласован с БД")
    }

    @Test
    fun `повторный вебхук не продлевает срок второй раз`() {
        val jdbc = FakeJdbc(claimRows = 0)
        val sub = subscription(jdbc)

        assertFalse(fulfill(jdbc, sub), "второй обработчик не должен считать себя первым")

        assertTrue(jdbc.claimUpdates().isNotEmpty(), "захват выполнялся")
        assertTrue(
            jdbc.premiumUpdates().isEmpty(),
            "повторный вебхук НЕ должен продлевать sitePremiumUntil, выполнено: ${jdbc.statements}",
        )
        assertEquals(Subscription.STATUS_CREATED, sub.status, "статус второго обработчика не меняется")
    }

    @Test
    fun `сбой начисления откатывает и статус, и продление`() {
        val jdbc = FakeJdbc()
        jdbc.premiumFailure = IllegalStateException("нет соединения с tbl_site_users")
        val sub = subscription(jdbc)

        assertThrows(IllegalStateException::class.java) { fulfill(jdbc, sub) }

        Mockito.verify(jdbc.connection).rollback()
        Mockito.verify(jdbc.connection, Mockito.never()).commit()
        assertEquals(Subscription.STATUS_CREATED, sub.status, "статус PAID не должен остаться в памяти")
    }

    @Test
    fun `повторная оплата не теряет ранее оплаченный хвост срока`() {
        val jdbc = FakeJdbc()
        val future = Timestamp(System.currentTimeMillis() + 10L * 24 * 3600_000L)
        jdbc.currentPremiumUntil = future
        val sub = subscription(jdbc)

        assertTrue(fulfill(jdbc, sub))

        val captor = ArgumentCaptor.forClass(Timestamp::class.java)
        Mockito.verify(jdbc.premium).setTimestamp(Mockito.eq(1), captor.capture())
        assertEquals(
            future.time + 30L * 24 * 3600_000L,
            captor.value.time,
            "основа продления — уже оплаченный срок, а не now",
        )
    }

    @Test
    fun `отсутствие пользователя - ошибка, а не молчаливый no-op`() {
        val jdbc = FakeJdbc()
        val sub = subscription(jdbc)

        assertThrows(IllegalStateException::class.java) {
            SubscriptionFulfillmentService().fulfillPaid(sub, "pay-1", "pm-1") { null }
        }
        Mockito.verify(jdbc.connection).rollback()
    }

    @Test
    fun `scope SONG не трогает sitePremiumUntil`() {
        val jdbc = FakeJdbc()
        val sub = subscription(jdbc, id = 21, scope = Subscription.SCOPE_SONG, periodDays = 0)

        assertTrue(fulfill(jdbc, sub))
        assertTrue(jdbc.premiumUpdates().isEmpty(), "подписка на песню не продлевает премиум: ${jdbc.statements}")
    }

    @Test
    fun `autoCommit восстанавливается после транзакции`() {
        val jdbc = FakeJdbc()
        val sub = subscription(jdbc)

        fulfill(jdbc, sub)

        Mockito.verify(jdbc.connection).setAutoCommit(false)
        Mockito.verify(jdbc.connection).setAutoCommit(true)
    }
}
