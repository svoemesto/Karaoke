# System (C4 L1 + L2)

> **Статус**: в разработке. Каркас развёрнут в рамках
> livedocs-миграции (Pass 340-385). Спецификация
> `322-knowledge-scaffold` в репозитории отсутствует и в истории
> git не появлялась (проверено в Pass 473). Наполнение ведётся в
> следующих спецификациях.

C4-модель верхнего уровня:

- **L1 — System Context** ([`01-context.md`](01-context.md)): место системы
  Karaoke в мире: внешние актёры, внешние системы, граница.
- **L2 — Containers** ([`02-containers.md`](02-containers.md)): технические
  контейнеры внутри границы (Kotlin/Spring backend, Vue 3 SPA, PostgreSQL,
  MinIO, MLT-пайплайн и т.д.).

Детализация L3 находится в `knowledge/domains/<name>/components/*.md`.

## Состав уровня

- [`utilities.md`](utilities.md) — общие утилиты ядра (в т.ч. кеширование
  и определение страны для VPN-проверки).
- [`frontend/`](frontend/) — 38 документов по `webvue3/` и `karaoke-public/`:
  Vuex-паттерны, store-docs по компонентам, composables, views.
- [`infra/`](infra/) — 15 документов по инфраструктуре репозитория: `tools/`,
  `deploy/`, `do.sh`, `specs/`, pre-commit-хуки, governance-документы,
  SQL-миграции, per-feature-документы.

Реестр доменов и компонентов — в
[`domains/README.md`](../domains/README.md); там же перечислены подкаталоги
`frontend/` и `infra/` построчно.

## Связь с L3

- Каждый контейнер на L2 раскрывается одним или несколькими
  доменами на L3: см. `knowledge/domains/<name>/domain.md`.
- Каждый домен на L3 ссылается на те контейнеры L2, в которых он
  физически реализован.
