# Karaoke — Knowledge Base (Living Documentation v2)

> **Статус**: в разработке. Каркас развёрнут в рамках
> livedocs-миграции (Pass 340-385). Спецификация
> `322-knowledge-scaffold` в репозитории отсутствует и в истории git
> не появлялась (проверено в Pass 473). Наполнение доменов/фич/epic'ов
> ведётся в следующих спецификациях.

Эта директория — **Single Source of Truth (SSoT)** для архитектуры проекта
Karaoke. Описывает **текущее состояние** системы, а не историю изменений.

## Структура

| Раздел | Назначение |
| --- | --- |
| [`system/`](system/) | C4 L1 (System Context) и L2 (Containers). Общий взгляд сверху. |
| [`domains/`](domains/) | C4 L3 — Bounded Contexts и компоненты (DDD). |
| [`adr/`](adr/) | Architecture Decision Records — почему мы сделали именно так. |
| [`epics/`](epics/) | Стратегические эпики — крупные переходы, влияющие на несколько доменов. |
| [`guidelines/`](guidelines/) | Стандартные паттерны и ограничения. |
| [`public/`](public/) | Человеко-читаемые проекции внутренней документации. |
| [`templates/`](templates/) | Шаблоны для новых доменов, компонентов, ADR и эпиков. |
| [`product/`](product/) | Продуктовый слой — per-feature документы (профиль B, FR-009). **Структурная категория, НЕ домен.** |

## Домены

Список Bounded Contexts Karaoke (C4 L3). Полный реестр — в
[`domains/README.md`](domains/README.md).

- [`identity`](domains/identity/domain.md) — пользователи, авторизация, сессии.
- [`catalog`](domains/catalog/domain.md) — песни, альбомы, исполнители, жанры (18k+ записей).
- [`rendering`](domains/rendering/domain.md) — рендеринг караоке-видео через MLT/melt.
- [`processing`](domains/processing/domain.md) — производство: Demucs, Sheetsage, генерация MP4 через Playwright + ffmpeg.
- [`publishing`](domains/publishing/domain.md) — публичный доступ: эфир, подписка, статистика.
- [`editorial`](domains/editorial/domain.md) — задания редакторов, self-assign, авто-pipeline при апруве.
- [`monitoring`](domains/monitoring/domain.md) — проверки здоровья инфраструктуры и SLF4J-категории.
- [`stats`](domains/stats/domain.md) — аналитика трафика, воронка visitor→registration→premium.
- [`caching`](domains/caching/domain.md) — паттерны кеширования (AtomicInteger, dirty-флаг, денормализация, async cold-start).
- [`storage`](domains/storage/domain.md) — MinIO и локальные файлы, вечный кеш метаданных.
- [`health`](domains/health/domain.md) — HealthReport и auto-repair (прецеденты #65, #69).
- [`monetization`](domains/monetization/domain.md) — тарифы, промо, корзина, подписки (visitor→premium).
- [`persistence`](domains/persistence/domain.md) — `KaraokeDbTable`, reflection-based save, `recordDiff`.
- [`sse`](domains/sse/domain.md) — real-time SSE-уведомления.
- [`integration`](domains/integration/domain.md) — внешние HTTP API (VK, Telegram, LM Studio, Whisper, Alignment, GeoIP).
- [`karaoke-web`](domains/karaoke-web/domain.md) — прод-сервер, public API, YooKassa, share-линки, защита от DDoS.

## Жизненный цикл документации

1. **Ephemeral work** — черновики, задачи, заметки живут **вне** `knowledge/`
   (в `specs/NNN-.../`, в личном scratch-каталоге, в письмах peer-mail).
2. **Mutation** — после реализации состояние `knowledge/` обновляется так,
   чтобы оно отражало **текущий** state системы, а не diff.
3. **Purge** — эфемерные артефакты удаляются. Merge ≠ Done, пока
   Living Documentation не приведён в соответствие с кодом.

## Linking Protocol (L3 → L2 → L1)

Атомарная операция при любом изменении:

- **Новый компонент (L3)** → обязан быть явно перечислен в `domain.md` (L2).
- **Новый домен (L2)** → обязан быть явно перечислен в `domains/README.md` (L1).
- **Удаление** файла на L3 → обязательно удалить ссылку на L2.

Нарушение этого протокола — критический архитектурный регресс.

### Две ветки SSoT

В `knowledge/` живут **две** ветки документации. Они равноправны по статусу
(обе — SSoT), но устроены по-разному:

| | **Профиль A — компонент** | **Профиль B — per-feature документ** |
|---|---|---|
| Где | `knowledge/domains/<домен>/components/<компонент>.md` | `knowledge/product/features/<slug>.md` |
| Что описывает | компонент домена: ответственность, интерфейсы, инварианты | фичу целиком: зачем, как работает, ловушки (FR-009) |
| Шаблон | [`templates/component.md`](templates/component.md) | [`templates/product-feature.md`](templates/product-feature.md) |
| Обязательные секции | по шаблону компонента | 6 секций FR-009: «Что делает», «Зачем», «Как работает», «Инварианты», «Известные ловушки», «Ссылки» |
| Проверяет | `tools/lint-knowledge.py` + `check-knowledge-structure.sh` | `tools/check-feature-doc.sh` (секции, `slug` == имя файла, `status`) |
| Linking Protocol L3 → L2 → L1 | **применяется** | **не применяется** — продуктовый слой не домен |
| Индекс | [`domains/README.md`](domains/README.md) | [`product/README.md`](product/README.md) |

**Продуктовая ветка — категория, а не домен.** Она не раскладывается по
16 доменам: часть per-feature документов относится сразу к нескольким.
Переход `docs/features/` → `knowledge/product/features/` состоялся 2026-10-29
(ADR-0011, спека 675).

## Шаблоны и линтер

- Новые файлы **обязаны** использовать шаблоны из `knowledge/templates/`.
- Перед merge запускается линтер: `python3 tools/lint-knowledge.py`.
- Линтер проверяет: эмодзи, обязательные секции, broken-линки L1↔L2↔L3.

## Для AI-агентов: протокол работы

Префикс `[SSoT-Verified]` в начале ответа — обязателен, если анализ
основан на этой документации.

1. **Initialize**: прочитай `knowledge/README.md` → `domains/README.md` →
   определи домен/эпик/ADR, к которому относится задача. Если задача — про
   фичу целиком, подключи ветку `knowledge/product/README.md`.
2. **Analyze**: прочитай `domain.md` целевого домена, чтобы понять
   Ubiquitous Language, и связанные ADR.
3. **Work in isolation**: инструменты/скрипты/временные файлы — вне `knowledge/`.
4. **Mutate state**: обнови документы `knowledge/` через Linking Protocol.
5. **Verify**: сверься с шаблонами и прогони линтер.
6. **Purge**: удали эфемерные артефакты.

## Karaoke-overrides (явные отклонения от generic-bootstrap)

Эти правила **специфичны для Karaoke** и перекрывают generic-фрагменты
из `bootstrap-living-docs/payload/fragments/`:

1. **CLAUDE.md ≠ AGENTS.md.** В Karaoke `AGENTS.md` — runtime governance
   (изменяется только владельцем через спецпроцедуру, см. AGENTS.md
   «Как обновлять этот файл»), `CLAUDE.md` — рекомендации для Claude Code.
   Они намеренно расходятся. **Ассиметрию не «чинить».**
2. **Агент делает git-цикл.** В Karaoke AI-агент создаёт feature-ветку
   `NNN-slug`, открывает PR и мержит через CI. Запрет на коммиты
   из generic-bootstrap к Karaoke не применяется — см. AGENTS.md
   «Git — CI-gate для master».
3. **ADR-нумерация.** ADR хранятся в `knowledge/adr/`. Существующие ID
   (`0001-raw-jdbc.md` … `0008-…`, `local-0001-…` … `local-0006-…`)
   сохраняются без переименования.
4. **Эпики и спецификации.** Karaoke уже использует `specs/NNN-<slug>/`
   как полный жизненный цикл фичи (spec → plan → tasks → implementation).
   `knowledge/epics/` заводится только для **стратегических** переходов,
   затрагивающих несколько specs; связь `EPIC-XXX → specs/N, M, K`
   указывается явно в секции «Roadmap & Steps».
5. **Слой `docs` слит в `knowledge/` (ADR-0011, 2026-10-29).** Второго
   слоя проектной документации больше нет: `knowledge/` — единственный SSoT,
   каталог `docs` запрещён guard'ом `tools/check-no-docs-dir.sh` (CI обеих
   копий workflow + pre-commit). Продуктовая ветка `knowledge/product/` —
   **структурная категория, а не домен**: Linking Protocol L3 → L2 → L1 на
   неё не применяется, свои 6 секций (FR-009) и свой guard
   (`tools/check-feature-doc.sh`). Старые адреса — в разделе «Бывшие пути»
   ниже.

## Бывшие пути

Справочник для внешних потребителей (агенты, старые письма, отчёты): **было →
стало** для всех **73** материалов слоя `docs/`, включая удалённые и свёрнутые.
Отдельный файл-редирект не заводится — иначе получился бы второй источник
правды о путях.

| Было | Стало | Что стало с материалом |
|---|---|---|
| `docs/api/README.md` | `knowledge/guidelines/kdoc-jsdoc.md` | перенесён |
| `docs/architecture-notes.md` | `archive/docs/architecture-notes.md` | перенесён в архив |
| `docs/features/admin-home-adaptive-columns.md` | `knowledge/product/features/admin-home-adaptive-columns.md` | перенесён |
| `docs/features/admin-web-client-robustness.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/alignment-ml.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/app-resilience-pkg-s2-11.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/author-filter-datalist.md` | `knowledge/product/features/author-filter-datalist.md` | перенесён |
| `docs/features/client-ip-resolution.md` | `knowledge/product/features/client-ip-resolution.md` | перенесён |
| `docs/features/close-gesture-token-paywall.md` | `knowledge/product/features/close-gesture-token-paywall.md` | перенесён |
| `docs/features/complete-song-rename.md` | `knowledge/product/features/complete-song-rename.md` | перенесён |
| `docs/features/deploy-hardening.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/drop-empty-song-voices.md` | `knowledge/product/features/drop-empty-song-voices.md` | перенесён |
| `docs/features/escape-confirm-user-data.md` | `knowledge/product/features/escape-confirm-user-data.md` | перенесён |
| `docs/features/fix-connection-leak.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/gradle-9-upgrade.md` | `knowledge/guidelines/architecture-conventions.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/idempotent-path-sanitize.md` | `knowledge/product/features/idempotent-path-sanitize.md` | перенесён |
| `docs/features/internal-secret-sync.md` | `knowledge/product/features/internal-secret-sync.md` | перенесён |
| `docs/features/karaoke-db-module-removal.md` | `knowledge/guidelines/architecture-conventions.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/kortege-execution.md` | `knowledge/product/features/kortege-execution.md` | перенесён |
| `docs/features/pagination-filter-admin-tables.md` | `knowledge/product/features/pagination-filter-admin-tables.md` | перенесён |
| `docs/features/pkg-err-stats-zero.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s2-10-data-integrity.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s2-12-audit-fixes.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s2-14-audit-fixes.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-22-audit-fixes.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-23.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-24.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-25.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-26.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-27.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-28.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-29.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-30.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-31.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-32.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-33.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-34.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/pkg-s3-35.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/playlist-membership.md` | `knowledge/product/features/playlist-membership.md` | перенесён |
| `docs/features/private-docker-registry.md` | `knowledge/system/infra/registry.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/process-bulk-actions.md` | `knowledge/product/features/process-bulk-actions.md` | перенесён |
| `docs/features/process-exit-code-check.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/public-api-exposure.md` | `knowledge/product/features/public-api-exposure.md` | перенесён |
| `docs/features/public-settings-perimeter.md` | `knowledge/product/features/public-settings-perimeter.md` | перенесён |
| `docs/features/publications-filters.md` | `knowledge/product/features/publications-filters.md` | перенесён |
| `docs/features/rows-per-page.md` | `knowledge/product/features/rows-per-page.md` | перенесён |
| `docs/features/search-text-extract-btn.md` | `knowledge/product/features/search-text-extract-btn.md` | перенесён |
| `docs/features/security-headers.md` | `knowledge/product/features/security-headers.md` | перенесён |
| `docs/features/song-air-access.md` | `knowledge/product/features/song-air-access.md` | перенесён |
| `docs/features/song-artifact-cleanup.md` | `knowledge/product/features/song-artifact-cleanup.md` | перенесён |
| `docs/features/song-edit-and-censored.md` | `knowledge/system/frontend/webvue3-audit-invariants.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/sql-injection-hardening.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/stemjob-private-bucket.md` | `knowledge/domains/storage/domain.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/storage-metadata-cache.md` | `knowledge/domains/storage/components/storage-api-client.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/subscription-payment-idempotency.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/sync-audio-descendants.md` | `knowledge/domains/catalog/components/audio-descendant-sync.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/sync-resilience.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/target-scoped-admin-requests.md` | `knowledge/system/frontend/target-request-guard.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/telegram-auto-publish.md` | `knowledge/domains/publishing/components/publishing-services.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/tonality-bpm-songs-table.md` | `knowledge/product/features/tonality-bpm-songs-table.md` | перенесён |
| `docs/features/tracker-local-integration.md` | `knowledge/system/infra/tracker.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/vk-id-admin-perimeter.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/web-data-integrity.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/web-render-safety.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/webui-state-integrity.md` | `knowledge/system/infra/audit-336-registry.md` | свёрнут в реестр аудита (BR-коды и карточка пакета) |
| `docs/features/zakroma-albums-by-author.md` | `knowledge/domains/catalog/components/album-entity.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/features/zakroma-tiles-sort-order.md` | `knowledge/domains/catalog/components/dictionaries.md` | свёрнут (содержимое перенесено в компонент-приёмник) |
| `docs/governance/knowledge-first.md` | `knowledge/guidelines/knowledge-first.md` | перенесён |
| `docs/ops/crypto-key-rotation.md` | `knowledge/guidelines/runbooks/crypto-key-rotation.md` | перенесён |
| `docs/ops/forgejo-access.md` | `knowledge/guidelines/runbooks/forgejo-access.md` | перенесён |
| `docs/ops/log-correlation.md` | `knowledge/guidelines/runbooks/log-correlation.md` | перенесён |
| `docs/ops/prod-config-delivery.md` | `knowledge/guidelines/runbooks/prod-config-delivery.md` | перенесён |
| `docs/tracker-setup.md` | `knowledge/guidelines/runbooks/tracker-setup.md` | перенесён |

Полные описания свёрнутых материалов — в git-истории: `git log --follow
knowledge/system/infra/audit-336-registry.md`.

## Связанные документы

- [domains/README.md](domains/README.md) — реестр всех Bounded Contexts.
- [adr/README.md](adr/README.md) — архитектурные решения.
- [templates/README.md](templates/README.md) — шаблоны для новых документов.
- [guidelines/README.md](guidelines/README.md) — руководства и конвенции.
- [public/README.md](public/README.md) — публичные проекции (glossary, onboarding).
- [epics/README.md](epics/README.md) — стратегические эпики.
