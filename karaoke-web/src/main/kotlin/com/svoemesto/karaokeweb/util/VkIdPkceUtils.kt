package com.svoemesto.karaokeweb.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Утилиты для реализации PKCE (Proof Key for Code Exchange, RFC 7636)
 * в OAuth 2.0 Authorization Code Flow через VK ID (id.vk.ru).
 *
 * PKCE защищает Authorization Code Flow от перехвата кода авторизации
 * злоумышленником. Без PKCE перехваченный code можно обменять на
 * access_token. С PKCE для обмена нужен `code_verifier`, который
 * известен только нашему серверу.
 *
 * Применяется в [com.svoemesto.karaokeweb.controllers.PublicVkIdAuthController]
 * — генерация `code_verifier`/`code_challenge` для `/authorize` и хранение
 * `code_verifier` между `/authorize` и `/callback`.
 *
 * @see specs/151-vk-id-personal-token/spec.md (FR-001)
 * @see specs/151-vk-id-personal-token/research.md (раздел 3)
 */
object VkIdPkceUtils {
    private const val VERIFIER_LENGTH = 64

    // RFC 7636, секция 4.1: code_verifier — случайная строка из 43-128 символов,
    // каждый символ из набора [A-Z][a-z][0-9]-._~
    private const val CODE_VERIFIER_CHARSET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private val secureRandom = SecureRandom()

    /**
     * Генерирует `code_verifier` — криптографически стойкую случайную строку
     * длиной 64 символа из набора `[A-Z][a-z][0-9]-._~`.
     *
     * @return строка-верификатор (например, `dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk`).
     */
    fun generateCodeVerifier(): String {
        val out = StringBuilder(VERIFIER_LENGTH)
        repeat(VERIFIER_LENGTH) {
            out.append(CODE_VERIFIER_CHARSET[secureRandom.nextInt(CODE_VERIFIER_CHARSET.length)])
        }
        return out.toString()
    }

    /**
     * Вычисляет `code_challenge` по `code_verifier` согласно RFC 7636, секция 4.2:
     * `base64url(SHA-256(code_verifier))`.
     *
     * VK ID ожидает метод `S256`. Параметр `code_challenge_method=plain` (без хеша)
     * допустим по RFC, но VK ID может его не поддерживать — используем S256 для
     * максимальной совместимости и безопасности.
     *
     * @param verifier `code_verifier`, сгенерированный [generateCodeVerifier].
     * @return строка-челлендж (например, `E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM`).
     */
    fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha)
    }

    /**
     * Генерирует `state` — случайную строку для CSRF-защиты (RFC 6749, секция 10.12).
     *
     * VK ID возвращает `state` в callback; наш сервер сравнивает с сохранённым.
     * Если не совпадает — потенциальная CSRF-атака, отклоняем запрос.
     *
     * @return строка из 32 случайных символов (например, `aB3dEf7hIjK9mNoP2qRsT5uVwX8yZ0`).
     */
    fun generateState(): String = generateCodeVerifier().take(32)
}
