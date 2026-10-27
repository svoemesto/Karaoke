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
- [`frontend/`](frontend/) — 43 документа по `webvue3/`, `karaoke-public/` и `karaoke-web`:
  Vuex-паттерны, store-docs по компонентам, composables, views, экранирование
  текста подтверждений ([`frontend/confirm-escape.md`](frontend/confirm-escape.md)),
  тестовая инфраструктура публичного SPA
  ([`frontend/karaoke-public-tests.md`](frontend/karaoke-public-tests.md)),
  инварианты публичного SPA из аудита #336
  ([`frontend/karaoke-public-audit-invariants.md`](frontend/karaoke-public-audit-invariants.md)),
  инварианты админского SPA `webvue3` из аудита #336
  ([`frontend/webvue3-audit-invariants.md`](frontend/webvue3-audit-invariants.md)),
  контракт ошибок и отказов `karaoke-web` из аудита #336
  ([`frontend/karaoke-web-error-contract.md`](frontend/karaoke-web-error-contract.md)).
- [`infra/`](infra/) — 18 документов по инфраструктуре репозитория: `tools/`,
  `deploy/`, `do.sh`, `registry` (Zot на прод-хосте — зеркало и локальный кэш),
  `harbor-registry` (основной каталог образов), `external-infra` (infra-сервер,
  Forgejo/Plane/Harbor, бэкапы, мониторинг), `ci-tools`, `specs`,
  pre-commit-хуки, governance-документы, SQL-миграции, per-feature-документы.

Реестр доменов и компонентов — в
[`domains/README.md`](../domains/README.md); там же перечислены подкаталоги
`frontend/` и `infra/` построчно.

## Связь с L3

- Каждый контейнер на L2 раскрывается одним или несколькими
  доменами на L3: см. `knowledge/domains/<name>/domain.md`.
- Каждый домен на L3 ссылается на те контейнеры L2, в которых он
  физически реализован.
