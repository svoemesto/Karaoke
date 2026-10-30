# Продуктовая ветка SSoT

> **Домен**: `knowledge/product/`
> **Назначение**: решения о **поведении продукта** — то, что видно администратору
> или пользователю. Второй профиль формата (FR-009), рядом с архитектурным
> профилем A (компонент домена, `knowledge/templates/component.md`).
> Канон термина и таблица двух профилей — в [`knowledge/README.md`](../README.md).

Продуктовая ветка — **структурная категория, а не домен**. Реестр доменов
([`knowledge/domains/README.md`](../domains/README.md), 16 доменов) не меняется,
и Linking Protocol L3 → L2 → L1 к `knowledge/product/**` **не применяется**.
Родитель продуктовых файлов — этот файл.

## Правило границы

| Пишем сюда | Пишем в компонент домена |
|---|---|
| Что видит администратор или пользователь | Как это устроено в коде |
| Продуктовое решение и его следствия | Алгоритмы, интерфейсы, контракты, хранилище |
| Инварианты поведения | Инварианты реализации (транзакции, потоки, индексы) |
| Известные ловушки продукта | Ловушки реализации и интеграций |
| Ограничения продукта («что фича не делает») | Технические ограничения и их обоснование |

Если текст описывает **поведение** — он профиля B (здесь). Если **устройство** —
профиля A (компонент). Документ может ссылаться на компонент в разделе «Ссылки»,
но не дублировать его содержание.

## Формат

Per-feature документ — `knowledge/product/features/<slug>.md`, формат FR-009:
6 обязательных секций (`Что делает`, `Зачем`, `Как работает`, `Инварианты`,
`Известные ловушки`, `Ссылки`), `slug` == имя файла, `status` ∈ {`active`,
`deprecated`, `experimental`}. Шаблон — [`knowledge/templates/product-feature.md`](../templates/product-feature.md),
проверка — [`tools/check-feature-doc.sh`](../../tools/check-feature-doc.sh).

## Документы

| # | Документ | Slug |
|---|---|---|
| 1 | [Адаптивная раскладка главной страницы админки](features/admin-home-adaptive-columns.md) | `admin-home-adaptive-columns` |
| 2 | [Feature: Список авторов в фильтрах админки](features/author-filter-datalist.md) | `author-filter-datalist` |
| 3 | [Feature: Определение адреса источника запроса (source address)](features/client-ip-resolution.md) | `client-ip-resolution` |
| 4 | [Close gesture token paywall (закрытие обхода оплаты по «скрытому жесту»)](features/close-gesture-token-paywall.md) | `close-gesture-token-paywall` |
| 5 | [Полное переименование артефактов песни (OpenProject #323, спека #323)](features/complete-song-rename.md) | `complete-song-rename` |
| 6 | [Пустые голоса песни удаляются при сохранении (спека #547)](features/drop-empty-song-voices.md) | `drop-empty-song-voices` |
| 7 | [Feature: Экранирование пользовательских данных в подтверждениях админки](features/escape-confirm-user-data.md) | `escape-confirm-user-data` |
| 8 | [Idempotent Path Sanitize (Санитайзер путей)](features/idempotent-path-sanitize.md) | `idempotent-path-sanitize` |
| 9 | [Feature: Внутренний секрет синхронизации (X-Internal-Secret для POST /changerecords)](features/internal-secret-sync.md) | `internal-secret-sync` |
| 10 | [Кортежное исполнение очереди обработки](features/kortege-execution.md) | `kortege-execution` |
| 11 | [Feature: Корректная пагинация таблиц admin SPA после применения фильтра](features/pagination-filter-admin-tables.md) | `pagination-filter-admin-tables` |
| 12 | [Feature: Playlist Membership (избранное + плейлисты) — bulk-fetch API](features/playlist-membership.md) | `playlist-membership` |
| 13 | [Process Bulk Actions (v2)](features/process-bulk-actions.md) | `process-bulk-actions` |
| 14 | [Публичный API: видимость песен и пределы выдачи](features/public-api-exposure.md) | `public-api-exposure` |
| 15 | [Feature: Периметр публичных настроек и fail-closed проверка капчи](features/public-settings-perimeter.md) | `public-settings-perimeter` |
| 16 | [Publications filters (UNPUBLISH и UNDONE в админке)](features/publications-filters.md) | `publications-filters` |
| 17 | [Feature: Настраиваемое количество строк на странице таблиц в админке](features/rows-per-page.md) | `rows-per-page` |
| 18 | [Feature: Кнопка «Получить текст по ссылке» — обновление UI без закрытия модалки](features/search-text-extract-btn.md) | `search-text-extract-btn` |
| 19 | [Feature: Заголовки безопасности публичного сайта](features/security-headers.md) | `security-headers` |
| 20 | [Song air access (эфир и окно бесплатного доступа)](features/song-air-access.md) | `song-air-access` |
| 21 | [Автоуборка артефактов песни (OpenProject #321, спека #544)](features/song-artifact-cleanup.md) | `song-artifact-cleanup` |
| 22 | [Превентивные меры против OOM в `karaoke-app` (лимит памяти, дампы, мониторинг heap)](features/heap-memory-guardrails.md) | `heap-memory-guardrails` |
| 22 | [Тональность и темп в таблице песен (Ton / BPM)](features/tonality-bpm-songs-table.md) | `tonality-bpm-songs-table` |

## Связанные документы

- [`knowledge/README.md`](../README.md) — главная SSoT-карта, таблица двух профилей.
- [`knowledge/templates/product-feature.md`](../templates/product-feature.md) — шаблон профиля B.
- [`knowledge/guidelines/runbooks/README.md`](../guidelines/runbooks/README.md) — операционные рецепты.
