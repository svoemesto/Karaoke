package com.svoemesto.karaokeweb.config

import com.svoemesto.karaokeweb.controllers.MainController
import com.svoemesto.karaokeweb.controllers.PublicSettingsWebController
import com.svoemesto.karaokeweb.services.CaptchaConfigService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value

/**
 * Регрессионный тест на инъекцию Spring-плейсхолдеров в `@Value`.
 *
 * **Дефект (найден при развёртывании волны A, 2026-10-24).** В трёх местах
 * аннотация была записана как `@Value($$"{свойство}")` — без `$` внутри кавычек.
 * В Kotlin такая запись компилируется в ЛИТЕРАЛ `{свойство}` (без `$`), и Spring
 * не находит в строке плейсхолдер — внедряется сам текст `{captcha.server-key:}`.
 * Итог на проде: сравнение секретов всегда давало `invalid_secret`, проверка капчи
 * сравнивала токен с мусором, регистрация отклонялась (fail-closed, но фича не работала).
 *
 * **Почему это не ловили юнит-тесты.** Контроллеры в тестах создаются напрямую с
 * явным значением секрета, минуя Spring-контекст, — сломанная аннотация проявляется
 * только в рантайме.
 */
class ValuePlaceholderInjectionTest {
    private fun valueAnnotation(
        clazz: Class<*>,
        parameterName: String,
    ): String {
        val constructor =
            clazz.declaredConstructors.first { c ->
                c.parameters.any { p -> p.name == parameterName }
            }
        val parameter = constructor.parameters.first { p -> p.name == parameterName }
        val annotation =
            parameter.annotations.filterIsInstance<Value>().firstOrNull()
                ?: error("У $parameterName нет аннотации @Value")
        return annotation.value
    }

    private fun assertPlaceholder(
        clazz: Class<*>,
        parameterName: String,
        expected: String,
    ) {
        val actual = valueAnnotation(clazz, parameterName)
        assertEquals(
            expected,
            actual,
            "$clazz.$parameterName: аннотация должна содержать настоящий плейсхолдер " +
                "(иначе Spring внедрит литерал и сравнение секретов всегда будет ложным)",
        )
        assertTrue(
            actual.startsWith("\${") && actual.endsWith("}"),
            "$clazz.$parameterName: плейсхолдер должен начинаться с доллара и заканчиваться фигурной скобкой",
        )
    }

    @Test
    fun `секрет синхронизации внедряется как плейсхолдер`() {
        assertPlaceholder(
            MainController::class.java,
            "syncInternalSecret",
            "\${sync.internal-secret:}",
        )
    }

    @Test
    fun `секрет публичных настроек внедряется как плейсхолдер`() {
        assertPlaceholder(
            PublicSettingsWebController::class.java,
            "internalSecret",
            "\${publicsettings.internal-secret:}",
        )
    }

    @Test
    fun `ключ капчи внедряется как плейсхолдер`() {
        assertPlaceholder(
            CaptchaConfigService::class.java,
            "serverKey",
            "\${captcha.server-key:}",
        )
    }

    @Test
    fun `в исходниках нет аннотаций без плейсхолдера`() {
        val offenders = mutableListOf<String>()
        val dir = java.io.File("src/main/kotlin")
        if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("@Value(\$\$") && !line.contains("\${")) {
                        offenders += "${file.path}:${index + 1}"
                    }
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Аннотации @Value без плейсхолдера (значение внедрится литералом): ${offenders.joinToString()}",
        )
    }
}
