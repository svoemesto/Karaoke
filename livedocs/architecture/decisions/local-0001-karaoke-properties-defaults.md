# Local ADR-0001: KaraokeProperties defaults конвенция

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: команда Karaoke

> **English version**: [../../../livedocs-en/decisions/local-0001-karaoke-properties-defaults.md](../../../livedocs-en/decisions/local-0001-karaoke-properties-defaults.md)
>
> **Note**: this is a **local** ADR — describes a specific subsystem
> convention (vs global ADR-0001..0006 which describe project-wide decisions).

## Context

`karaoke-app/.../KaraokeProperties.kt` (~150 настраиваемых параметров для
MLT/melt, ffmpeg, Playwright, Telegram-bot, и т.п.) имеет **сенситивные
дефолты** — на проде они используются как fallback, если `Karaoke.properties`
файл пуст.

Эти дефолты:
- **Должны быть non-secure defaults** (нельзя хардкодить реальные токены).
- **Должны быть consistent** для всех env (admin, dev, prod) с env-overrides.
- **Должны логироваться при использовании** (для отладки).

## Decision

Конвенция для дефолтов в `KaraokeProperties.kt`:

```kotlin
// ✅ ПРАВИЛЬНО
val ollamaEndpoint: String = System.getenv("OLLAMA_ENDPOINT") ?: "http://localhost:11434"
val token: String = System.getenv("TELEGRAM_BOT_TOKEN") ?: ""  // пустая строка если нет
val enabled: Boolean = System.getenv("OLLAMA_ENABLED")?.toBooleanStrictOrNull() ?: false

// ❌ ЗАПРЕЩЕНО
val token: String = "REAL_TOKEN_HERE"  // Секреты в коде!
val endpoint: String = "http://10.0.0.1:1234"  // Хардкод env-значения
```

**Правила**:
1. Все env-через-`getenv(ENV_NAME)`.
2. Дефолт — только `localhost` или пустая строка.
3. Если bool — `toBooleanStrictOrNull() ?: false`.
4. Если число — `?.toIntOrNull() ?: DEFAULT_INT`.
5. Никогда — реальные секреты или prod IP.

### Logging convention

```kotlin
init {
    logger.info("KaraokeProperties loaded: ollama=$ollamaEndpoint, telegram=${if (telegramBotToken.isBlank()) "OFF" else "ON"}")

    // Secrets НЕ ЛОГИРУЕМСЯ (Constitution § VIII.5)
}
```

## Consequences

### Positive
- Безопасно для open-source (секреты не в коде).
- Один и тот же JAR работает на admin/стенд/prod с разными env.
- Легко отлаживать через логи.

### Negative
- Больше boilerplate (`System.getenv(...) ?: ...`).
- Конфигурация через env может быть «магической» для новых разработчиков.

### Neutral
- Document default values в `AGENTS.md` и в LiveDocs.

## Alternatives Considered

- **Hardcoded defaults**: rejected — secret leak risk.
- **Конфиг из `application.yml`**: rejected — Spring Boot config overload,
  проще через env.
- **Config из БД (`tbl_settings`)**: rejected — circular dep с
  KaraokeConnection, env-overrides проще.

## References

- [livedocs/architecture/decisions/0001-raw-jdbc.md](../../decisions/0001-raw-jdbc.md) — global ADR.
- Constitution § VIII.5 — секреты через env.
- [AGENTS.md](../../../../AGENTS.md) — секция «Ограничения агента».