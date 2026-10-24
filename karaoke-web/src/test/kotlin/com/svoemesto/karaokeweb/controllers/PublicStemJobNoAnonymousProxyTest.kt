package com.svoemesto.karaokeweb.controllers

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Регрессионный тест «до/после» (BR-258, спека 561, OpenProject #361) — **компилируется и на
 * коде до правки**, поэтому показывает разницу поведения, а не только отсутствие новых символов.
 *
 * **До правки** контроллер выдачи результата держал адрес анонимного прокси MinIO
 * (`@Value("\${storage.proxy-url}") minioProxyUrl`) и читал приватный объект по публичному
 * адресу `/minio/karaoke/<key>` — то есть отдавал анонимно доступный объект. **После правки**
 * такого поля нет: приватные объекты лежат в приватном каталоге результатов, а отдаёт их
 * только владелец задания.
 *
 * @see specs/561-impl-stem-bucket/research.md
 */
class PublicStemJobNoAnonymousProxyTest {
    @Test
    fun `контроллер выдачи результата не обращается к MinIO через анонимный прокси`() {
        val fieldNames = PublicStemJobController::class.java.declaredFields.map { it.name }

        assertFalse(
            fieldNames.any { it.contains("minio", ignoreCase = true) },
            "контроллер не должен обращаться к MinIO напрямую/через анонимный прокси: $fieldNames",
        )
    }
}
