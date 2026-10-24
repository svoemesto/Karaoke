package com.svoemesto.karaokeapp.model

import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

/** Классический payload обхода условия для тестов пакета PKG-SEC-SQLI (#383). */
internal const val SQLI_TAUTOLOGY = "x' OR '1'='1"

/**
 * `true`, если вне строковых литералов в SQL встретился оператор/ключевое
 * слово, которого там быть не должно (`OR`, `UNION`). Ровно это делает payload
 * `x' OR '1'='1`: закрывает литерал и добавляет своё условие.
 */
internal fun hasOperatorOutsideLiterals(sql: String): Boolean {
    var inLiteral = false
    var index = 0
    while (index < sql.length) {
        val char = sql[index]
        when {
            char == '\'' && inLiteral && index + 1 < sql.length && sql[index + 1] == '\'' -> index++
            char == '\'' -> inLiteral = !inLiteral
            !inLiteral && char.isLetter() -> {
                val start = index
                while (index < sql.length && sql[index].isLetter()) index++
                val word = sql.substring(start, index).uppercase()
                if (word == "OR" || word == "UNION") return true
                continue
            }
        }
        index++
    }
    return false
}

/**
 * Мок JDBC-слоя: соединение, `Statement`/`PreparedStatement` и пустой `ResultSet`.
 * Позволяет проверить сформированный SQL и привязанные параметры без живой БД.
 */
internal class MockJdbc {
    val connection: Connection = Mockito.mock(Connection::class.java)
    val statement: Statement = Mockito.mock(Statement::class.java)
    val prepared: PreparedStatement = Mockito.mock(PreparedStatement::class.java)
    val resultSet: ResultSet = Mockito.mock(ResultSet::class.java)

    init {
        Mockito.`when`(connection.createStatement()).thenReturn(statement)
        Mockito.`when`(connection.prepareStatement(Mockito.anyString())).thenReturn(prepared)
        Mockito.`when`(statement.executeQuery(Mockito.anyString())).thenReturn(resultSet)
        Mockito.`when`(prepared.executeQuery()).thenReturn(resultSet)
        Mockito.`when`(resultSet.next()).thenReturn(false)
    }

    fun preparedSql(): String {
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(connection).prepareStatement(captor.capture())
        return captor.value
    }

    fun statementSql(): String {
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(statement).executeQuery(captor.capture())
        return captor.value
    }

    companion object {
        fun storageService(): com.svoemesto.karaokeapp.services.KaraokeStorageService =
            Mockito.mock(com.svoemesto.karaokeapp.services.KaraokeStorageService::class.java)

        fun storageApiClient(): com.svoemesto.karaokeapp.services.StorageApiClient =
            Mockito.mock(com.svoemesto.karaokeapp.services.StorageApiClient::class.java)
    }
}
