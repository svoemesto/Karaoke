# Local ADR-0001: KaraokeProperties defaults convention

* **Status**: Accepted
* **Date**: 2026-08-14
* **Deciders**: Karaoke team

> **Russian version**: [../../../livedocs/architecture/decisions/local-0001-karaoke-properties-defaults.md](../../../livedocs/architecture/decisions/local-0001-karaoke-properties-defaults.md)
>
> **Note**: this is a **local** ADR — describes a specific subsystem
> convention (vs global ADR-0001..0006 which describe project-wide decisions).

## Context

`karaoke-app/.../KaraokeProperties.kt` (~150 configurable parameters for
MLT/melt, ffmpeg, Playwright, Telegram-bot, etc.) has **sensitive defaults** —
on prod they're used as fallback when `Karaoke.properties` file is empty.

These defaults:
- **Must be non-secure defaults** (cannot hardcode real tokens).
- **Must be consistent** across all envs (admin, dev, prod) with env-overrides.
- **Must be logged when used** (for debugging).

## Decision

Convention for defaults in `KaraokeProperties.kt`:

```kotlin
// ✅ CORRECT
val ollamaEndpoint: String = System.getenv("OLLAMA_ENDPOINT") ?: "http://localhost:11434"
val token: String = System.getenv("TELEGRAM_BOT_TOKEN") ?: ""  // empty string if not
val enabled: Boolean = System.getenv("OLLAMA_ENABLED")?.toBooleanStrictOrNull() ?: false

// ❌ FORBIDDEN
val token: String = "REAL_TOKEN_HERE"  // Secrets in code!
val endpoint: String = "http://10.0.0.1:1234"  // Hardcoded env-value
```

**Rules**:
1. All envs via `getenv(ENV_NAME)`.
2. Default — only `localhost` or empty string.
3. If bool — `toBooleanStrictOrNull() ?: false`.
4. If number — `?.toIntOrNull() ?: DEFAULT_INT`.
5. Never — real secrets or prod IP.

### Logging convention

```kotlin
init {
    logger.info("KaraokeProperties loaded: ollama=$ollamaEndpoint, telegram=${if (telegramBotToken.isBlank()) "OFF" else "ON"}")

    // Secrets NOT LOGGED (Constitution § VIII.5)
}
```

## Consequences

### Positive
- Safe for open-source (no secrets in code).
- Same JAR runs on admin/stand/prod with different env.
- Easy to debug via logs.

### Negative
- More boilerplate (`System.getenv(...) ?: ...`).
- Config via env may be "magic" for new developers.

### Neutral
- Document default values in `AGENTS.md` and LiveDocs.

## Alternatives Considered

- **Hardcoded defaults**: rejected — secret leak risk.
- **Config from `application.yml`**: rejected — Spring Boot config overload,
  simpler through env.
- **Config from DB (`tbl_settings`)**: rejected — circular dep with
  KaraokeConnection, env-overrides simpler.

## References

- [livedocs/architecture/decisions/0001-raw-jdbc.md](../../decisions/0001-raw-jdbc.md) — global ADR.
- Constitution § VIII.5 — secrets through env.
- [AGENTS.md](../../../../AGENTS.md) — "Agent restrictions" section.