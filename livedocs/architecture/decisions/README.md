# Architecture Decision Records (ADR)

> ADR-процесс для проекта Karaoke. Используем формат MADR-lite:
> **Context → Decision → Consequences → Alternatives**.

## Назначение

ADR фиксируют **значимые архитектурные решения**, которые:
- влияют на долгосрочную поддержку;
- имеют несколько альтернатив;
- требуют обоснования для будущих разработчиков/AI-агентов.

Каждый ADR — короткий документ (1-2 страницы). **Immutable** после принятия —
изменения делаются через новый ADR, заменяющий старый.

## Index

| ADR | Title | Status | Date |
|-----|-------|--------|------|
| [0001](0001-raw-jdbc.md) | Сырой JDBC без JPA/Hibernate для доступа к БД | Accepted | 2026-07-20 |
| [0002](0002-mlt-instead-of-ffmpeg.md) | MLT/melt как основной стек для генерации караоке-видео | Accepted | 2026-07-20 |
| [0003](0003-livedocs-markdown-yaml-mermaid.md) | LiveDocs = Markdown + YAML frontmatter + Mermaid (не MkDocs/Docusaurus) | Accepted | 2026-08-14 |
| [0004](0004-karaoke-app-admin-only.md) | KaraokeApp — только на admin-машине, не на проде | Accepted | 2026-07-20 |
| [0005](0005-self-hosted-ml.md) | Self-hosted ML (Ollama + SearXNG + Sheetsage + Demucs) вместо SaaS | Accepted | 2026-07-20 |
| [0006](0006-processbuilder-redirect-errorstream.md) | ProcessBuilder + redirectErrorStream(true) для async-задач | Accepted | 2026-08-13 |

## Conventions

- Нумерация: `NNNN-kebab-case-slug.md` (4 цифры — позволяет до 9999 ADR).
- Шаблон — см. `0001-raw-jdbc.md`.
- Frontmatter — НЕ требуется (ADR не являются LiveDocs в смысле «про фичу»
  или «про домен»; это **проектная** документация).
- Index (этот файл) обновляется при создании ADR.
- PASS 16+ этот процесс формализован (см. Constitution § VII.4 — TODO).

## Почему не docs/adr/

Все архитектурные документы живут в `livedocs/architecture/` — это обеспечивает
единый source of truth для AI-агента (см. `AGENTS.md` «С чего начать сессию»).
ADR — часть архитектурного слоя, поэтому тоже здесь.

## См. также

- [Constitution § VII.4](../decisions/README.md) — governance cross-machine
  setup, включая TODO по ADR.
- [livedocs/README.md](../../README.md) — корневой манифест LiveDocs.