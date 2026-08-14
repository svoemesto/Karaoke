# ADR-0005: Self-hosted ML (Ollama + SearXNG + Sheetsage + Demucs) вместо SaaS

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, NON-NEGOTIABLE — см. Constitution § I)
* **Deciders**: команда Karaoke

## Context

Караоке-проект использует несколько **ML-сервисов**:

1. **Ollama** — локальная LLM (Mistral, Llama) для AI-поиска текстов песен,
   генерации summaries.
2. **SearXNG** — локальный мета-поисковик для поиска текстов песен.
3. **Sheetsage** — локальная ML-модель для key / BPM / chords.
4. **Demucs** — локальная модель (Facebook Research) для стем-сепарации.

Альтернативы:
- OpenAI / Claude API (SaaS).
- Google Speech-to-Text / MusicBrainz (SaaS).
- Лицензия ASR / ML сервисов дорогая.

## Decision

**Self-hosted ML**: установить и запускать **локально** все 4 ML-сервиса.

- Ollama — бинарь на admin-машине.
- SearXNG — Docker container на admin-машине.
- Sheetsage — Docker container на admin-машине (Python).
- Demucs — Docker image (Python).

**Работает ТОЛЬКО на admin-машине** (где `karaoke-app`), не на проде.
См. [ADR-0004](0004-karaoke-app-admin-only.md).

## Почему self-hosted

1. **Независимость от интернета**. Admin-машина работает оффлайн (исторически
   — медленный/нестабильный интернет). SaaS не работает в этом случае.

2. **Стоимость**. SaaS (OpenAI, MusicBrainz) платный. Self-hosted:
   - Ollama — бесплатный (Mistral 7B ~$0).
   - SearXNG — бесплатный (open-source).
   - Sheetsage — open-source.
   - Demucs — open-source (MIT).

3. **Privacy**. Тексты песен — лицензионный материал (русский рок, not public domain).
   SaaS отправляет наши тексты на чужие серверы. Self-hosted — нет.

4. **Reproducibility**. Один раз настроил — работает без изменений годами.

5. **Производительность**. Локальные модели быстрее (нет сетевой задержки).

6. **Constitution § I NON-NEGOTIABLE** — фундаментальный принцип проекта:
   - «Па йплайн производства караоке-видео выполняется на admin-машине без
     зависимости от внешних SaaS».
   - «Допускаются локально развёрнутые ML-модели (Ollama, Silero TTS, Sheetsage)».

## Что работает локально

| ML | Endpoint | Контейнер/процесс | Использование |
|----|----------|-------------------|---------------|
| **Ollama** | `http://localhost:11434` | Бинарь на admin-машине | AI-поиск текстов, summaries |
| **SearXNG** | `http://localhost:8888` | Docker container | Мета-поиск текстов |
| **Sheetsage** | `http://localhost:8000` | Docker container (Python) | key/BPM/chords |
| **Demucs** | Docker image (`python:3.10-slim`) | subprocess через ProcessBuilder | Стем-сепарация |

Все четыре — **только** на admin-машине. На проде их нет (но также не
нужны — там только публичный HTTP).

## Endpoints в коде

```kotlin
// karaoke-app/.../KaraokeProperties.kt
val ollamaEndpoint = System.getenv("OLLAMA_ENDPOINT") ?: "http://localhost:11434"
val searxngEndpoint = System.getenv("SEARXNG_ENDPOINT") ?: "http://localhost:8888"
val sheetsageEndpoint = System.getenv("SHEETSAGE_ENDPOINT") ?: "http://localhost:8000"
const val demucsImage = "demucs-python:3.10-slim"
```

Через env-переменные (на admin-машине — defaults localhost, на проде —
N/A, но и не нужны).

## Альтернатива SaaS — почему отклонена

| SaaS | Risk | Стоимость | Privacy |
|------|------|-----------|---------|
| OpenAI API | Internet required, шумный | $$$ | Тексты уходят в OpenAI |
| Claude API | Internet required | $$$ | Тексты уходят в Anthropic |
| Google STT | Internet, quota | $$ | Аудио уходит в Google |
| MusicBrainz | API limits | Бесплатно (но quota) | Метаданные уходят |
| HuggingFace Inference | Internet, latency | $$ | Модели HF SaaS |

Все SaaS требуют **стабильного интернета** (историческая проблема admin-машины).

## Минусы self-hosted

- ❌ **Hardware**: нужен GPU для Demucs/LLM (CPU fallback медленный).
- ❌ **Maintenance**: обновления моделей, Docker-compose, мониторинг.
- ❌ **Cold-start**: Ollama грузит модель ~3 сек при первом запросе (но
  в дальнейшем — instant).
- ❌ **Качество**: открытые модели могут быть хуже закрытых (но для нашей
  задачи — sufficient).

## Когда может измениться

Если в будущем появится облачный бюджет, можно перейти на SaaS для
некоторых компонентов. Constitution § I требует только, что **караоке-видео
продакшн не зависел от SaaS** — это значит fallback на self-hosted ОК,
но гибрид (SearXNG self + OpenAI для LLM) тоже приемлем.

## Alternatives Considered

- **Demucs SaaS** (Facebook hosted): не существует в виде SaaS.
- **OpenAI Whisper API** для текстов: нет, мы используем LLM для summary,
  не для STT (Sheetsage — для анализа аудио).
- **HuggingFace Inference (SaaS)**: рассмотрено, отвергнуто из-за
  зависимости от интернета.

## Ссылки

- [Constitution § I](.specify/memory/constitution.md) — NON-NEGOTIABLE.
- [L1-system-context.md](../L1-system-context.md) — где Ollama/SearXNG/Sheetsage
  упоминаются как внешние системы (на самом деле — локальные).
- [L3-components.md](../L3-components.md) — LLM Integration компонент.
- [ADR-0004](0004-karaoke-app-admin-only.md) — где живёт ML-инфраструктура.
- [architecture/dual-db-access.md](dual-db-access.md) — как karaoke-web
  доступ к БД (read-only).