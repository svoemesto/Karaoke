package com.svoemesto.karaokeapp.services.songdeletion

import com.svoemesto.karaokeapp.KaraokeConnection

/**
 * Подключение к LOCAL-БД для интеграционных тестов фичи удаления песни.
 *
 * **Почему не глобальный `WORKING_DATABASE`.** `Connection.local()` — ленивый
 * singleton, который на этапе инициализации класса навсегда захватывает реквизиты из
 * глобалей `DB_LOCAL_POSTGRES_USER` / `DB_LOCAL_POSTGRES_PASSWORD`. Эти глобали
 * меняют другие наборы тестов (например, `WhisperMarkerAlignerSpecTagsTest`
 * выставляет «test»/«test»), поэтому общий singleton в параллельном прогоне
 * может оказаться с чужими реквизитами — и интеграционный тест падал бы с
 * «password authentication failed» в зависимости от порядка классов.
 *
 * Собственный экземпляр [KaraokeConnection] с явными реквизитами делает тест
 * независимым от порядка выполнения. Реквизиты читаются из переменных окружения
 * и в репозиторий не попадают.
 */
object SongDeleteTestDatabase {
    /** JDBC-URL локальной БД admin-машины (порт контейнера `karaoke-db`). */
    private const val LOCAL_URL = "jdbc:postgresql://localhost:8832/karaoke?currentSchema=public"

    /** Заданы ли реквизиты LOCAL-БД в окружении. */
    fun isAvailable(): Boolean =
        !System.getenv("DB_LOCAL_POSTGRES_USER").isNullOrBlank() &&
            !System.getenv("DB_LOCAL_POSTGRES_PASSWORD").isNullOrBlank()

    /**
     * Отдельное подключение к LOCAL-БД.
     *
     * @throws IllegalStateException если реквизиты не заданы в окружении.
     */
    fun local(): KaraokeConnection {
        val user = System.getenv("DB_LOCAL_POSTGRES_USER")
        val password = System.getenv("DB_LOCAL_POSTGRES_PASSWORD")
        require(!user.isNullOrBlank() && !password.isNullOrBlank()) {
            "реквизиты LOCAL-БД не заданы в окружении (DB_LOCAL_POSTGRES_USER / DB_LOCAL_POSTGRES_PASSWORD)"
        }
        return LocalConnection(LOCAL_URL, user, password)
    }

    /** Конкретный экземпляр подключения к локальной БД. */
    private class LocalConnection(
        url: String,
        user: String,
        password: String,
    ) : KaraokeConnection(url, user, password, "LOCAL")
}
