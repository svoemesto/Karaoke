# features/ — SDD слой (индекс)

> Сводки фич (≤ 2 страницы каждая). Drill-down — в `specs/<NNN>-*/spec.md`.

## Содержимое

| Файл | Что делает | Drill-down |
|------|------------|------------|
| [100-audio-similarity-threshold.md](100-audio-similarity-threshold.md) | Повышение порога аудио-похожести (85→95) | [specs/100-.../spec.md](../../specs/100-audio-similarity-threshold/spec.md) |
| [101-audio-transpose-player.md](101-audio-transpose-player.md) | Транспонирование аудио в онлайн-плеере (админка) | [specs/101-.../spec.md](../../specs/101-audio-transpose-player/spec.md) |
| [101-song-news-flag.md](101-song-news-flag.md) | Флаг «песня доступна» + очистка ленты | [specs/101-.../spec.md](../../specs/101-song-news-flag/spec.md) |
| [102-rename-song-settings-vars.md](102-rename-song-settings-vars.md) | Переименование settings → song (полный охват) | [specs/102-.../spec.md](../../specs/102-rename-song-settings-vars/spec.md) |
| [113-telegram-demo-publish.md](113-telegram-demo-publish.md) | Автопубликация DEMO в Telegram по расписанию | [specs/113-.../spec.md](../../specs/113-telegram-demo-publish/spec.md) |
| [121-vk-news-auto-publish.md](121-vk-news-auto-publish.md) | Автопубликация новостей в группу ВКонтакте | [specs/121-.../spec.md](../../specs/121-vk-news-auto-publish/spec.md) |
| [122-premium-auto-publish.md](122-premium-auto-publish.md) | Премиум-автопубликация Telegram + ВК | [specs/122-.../spec.md](../../specs/122-premium-auto-publish/spec.md) |
| [123-vk-og-preview-fix.md](123-vk-og-preview-fix.md) | Премиум-публикация ВК: превью через attachments=photo | [specs/123-.../spec.md](../../specs/123-vk-og-preview-fix/spec.md) |
| [124-filename-sanitization-rename.md](124-filename-sanitization-rename.md) | Санитайзинг имён файлов + переименование | [specs/124-.../spec.md](../../specs/124-filename-sanitization-rename/spec.md) |
| [125-news-flags-backfix.md](125-news-flags-backfix.md) | Backfill флагов публикации (kill-switch tbl_public_settings) | [specs/125-.../spec.md](../../specs/125-news-flags-backfix/spec.md) |
| [128-news-publish-templates.md](128-news-publish-templates.md) | Шаблоны автоматических новостей сайта | [specs/128-.../spec.md](../../specs/128-news-publish-templates/spec.md) |
| [129-copy-family-audio.md](129-copy-family-audio.md) | Копирование аудиосвязи при выборе похожей версии | [specs/129-.../spec.md](../../specs/129-copy-family-audio/spec.md) |
| [130-vk-preview-generation.md](130-vk-preview-generation.md) | Предварительная подготовка PNG-кэша перед ВК | [specs/130-.../spec.md](../../specs/130-vk-preview-generation/spec.md) |
| [131-fix-approve-demo-render-telegram-sync.md](131-fix-approve-demo-render-telegram-sync.md) | Авто-пайплайн публикации после approve | [specs/131-.../spec.md](../../specs/131-fix-approve-demo-render-telegram-sync/spec.md) |
| [138-vk-photo-preview-attachment.md](138-vk-photo-preview-attachment.md) | Превью ВК через прикрепление обложки фото | [specs/138-.../spec.md](../../specs/138-vk-photo-preview-attachment/spec.md) |
| [139-fix-censored-dictionary.md](139-fix-censored-dictionary.md) | Цензурирование {songNameCensored} на проде | [specs/139-.../spec.md](../../specs/139-fix-censored-dictionary/spec.md) |
| [140-fix-zakroma-censored-database.md](140-fix-zakroma-censored-database.md) | Падение /api/public/zakroma (Property APP_WORK_ON_SERVER) | [specs/140-.../spec.md](../../specs/140-fix-zakroma-censored-database/spec.md) |
| [141-fix-censored-web-storage-globals.md](141-fix-censored-web-storage-globals.md) | Цензурирование на karaoke-web: глобалы + Unicode regex | [specs/141-.../spec.md](../../specs/141-fix-censored-web-storage-globals/spec.md) |
| [142-remove-watch-links-block.md](142-remove-watch-links-block.md) | Удалить блок «Ссылки на просмотр» со страницы песни | [specs/142-.../spec.md](../../specs/142-remove-watch-links-block/spec.md) |
| [143-song-free-access-window.md](143-song-free-access-window.md) | Временное окно бесплатного доступа к песням | [specs/143-.../spec.md](../../specs/143-song-free-access-window/spec.md) |
| [144-homepage-latest-news.md](144-homepage-latest-news.md) | Компактная таблица «последние 5 новостей» на главной | [specs/144-.../spec.md](../../specs/144-homepage-latest-news/spec.md) |
| [151-vk-id-personal-token.md](151-vk-id-personal-token.md) | Миграция на VK ID для персонального токена | [specs/151-.../spec.md](../../specs/151-vk-id-personal-token/spec.md) |
| [152-fix-false-collection-news.md](152-fix-false-collection-news.md) | Ложное срабатывание новости «в коллекции» после sync | [specs/152-.../spec.md](../../specs/152-fix-false-collection-news/spec.md) |
| [154-editor-tasks-manage.md](154-editor-tasks-manage.md) | Управление заданиями в ЛК + админке | [specs/154-.../spec.md](../../specs/154-editor-tasks-manage/spec.md) |
| [155-editor-typograph-button.md](155-editor-typograph-button.md) | Кнопка «Типограф» в онлайн-редакторе | [specs/155-.../spec.md](../../specs/155-editor-typograph-button/spec.md) |
| [156-remove-songs-table-platform-flags.md](156-remove-songs-table-platform-flags.md) | Удалить 18 столбцов-флагов публикации из «Песни» | [specs/156-.../spec.md](../../specs/156-remove-songs-table-platform-flags/spec.md) |
| [160-publish-body-td-remove-six-columns.md](160-publish-body-td-remove-six-columns.md) | Чистка PublishTableBodyTd + удаление processColor* из DTO | [specs/160-.../spec.md](../../specs/160-publish-body-td-remove-six-columns/spec.md) |
| [162-fix-header-stale-premium-status.md](162-fix-header-stale-premium-status.md) | Устаревший premium-статус в шапке | [specs/162-.../spec.md](../../specs/162-fix-header-stale-premium-status/spec.md) |
| [163-fix-song-editor-regressions.md](163-fix-song-editor-regressions.md) | Регрессии редакторов после спецтегов | [specs/163-.../spec.md](../../specs/163-fix-song-editor-regressions/spec.md) |
| [164-complete-guest-share-link.md](164-complete-guest-share-link.md) | Завершение share-link: плеер гостя + heartbeat + sweep | [specs/164-.../spec.md](../../specs/164-complete-guest-share-link/spec.md) |
| [166-fix-share-link-timezone.md](166-fix-share-link-timezone.md) | Единая TZ share-ссылок: МСК в БД, deviceTZ на UI | [specs/166-.../spec.md](../../specs/166-fix-share-link-timezone/spec.md) |
| [167-fix-share-claim-500.md](167-fix-share-claim-500.md) | Починить 500 на POST /api/public/share/claim | [specs/167-.../spec.md](../../specs/167-fix-share-claim-500/spec.md) |
| [169-share-link-in-premium-compare.md](169-share-link-in-premium-compare.md) | Строка «Временная ссылка» в FREE vs PREMIUM | [specs/169-.../spec.md](../../specs/169-share-link-in-premium-compare/spec.md) |
| [171-admin-subscriptions-history.md](171-admin-subscriptions-history.md) | Админ-таблицы: Подписки, История прослушиваний, Временные ссылки | [specs/171-.../spec.md](../../specs/171-admin-subscriptions-history/spec.md) |
| [172-db-sync-temporary-links.md](172-db-sync-temporary-links.md) | Share-ссылки в LOCAL↔SERVER sync | [specs/172-.../spec.md](../../specs/172-db-sync-temporary-links/spec.md) |
| [174-fix-stats-connection-leak.md](174-fix-stats-connection-leak.md) | Починить flood JDBC в «Статистике» | [specs/174-.../spec.md](../../specs/174-fix-stats-connection-leak/spec.md) |
| [176-authors-new-albums-badge.md](176-authors-new-albums-badge.md) | Бейдж «новые альбомы» в пункте меню «Авторы» | [specs/176-.../spec.md](../../specs/176-authors-new-albums-badge/spec.md) |
| [177-fix-process-count-waiting-spam.md](177-fix-process-count-waiting-spam.md) | Устранение спама PROCESS_COUNT_WAITING в SSE | [specs/177-.../spec.md](../../specs/177-fix-process-count-waiting-spam/spec.md) |
| [180-og-seo-html.md](180-og-seo-html.md) | SEO-HTML (Schema.org JSON-LD + OG + Twitter) вместо PNG | [specs/180-.../spec.md](../../specs/180-og-seo-html/spec.md) |
| [181-zakroma-author-load-progress.md](181-zakroma-author-load-progress.md) | Real-time прогресс через NDJSON-стрим | [specs/181-.../spec.md](../../specs/181-zakroma-author-load-progress/spec.md) |
| [182-editor-self-assign-tasks.md](182-editor-self-assign-tasks.md) | Self-assign заданий редакторами (atomic SELECT FOR UPDATE + idempotency) | [specs/182-.../spec.md](../../specs/182-editor-self-assign-tasks/spec.md) |
| [184-approve-status-choice.md](184-approve-status-choice.md) | Условный запуск конвейера при выборе idStatus 5/6 | [specs/184-.../spec.md](../../specs/184-approve-status-choice/spec.md) |
| [185-song-dto-audit-sponsr-remove.md](185-song-dto-audit-sponsr-remove.md) | Аудит Song DTO, удаление спонсорских полей | [specs/185-.../spec.md](../../specs/185-song-dto-audit-sponsr-remove/spec.md) |
| [186-zakroma-songs-fast-load.md](186-zakroma-songs-fast-load.md) | Оптимизация загрузки песен в Закромах | [specs/186-.../spec.md](../../specs/186-zakroma-songs-fast-load/spec.md) |
| [187-site-traffic-anomaly-investigation.md](187-site-traffic-anomaly-investigation.md) | Расследование аномалии трафика сайта | [specs/187-.../spec.md](../../specs/187-site-traffic-anomaly-investigation/spec.md) |

## Конвенции

- Имя файла: `<NNN-slug>.md`, где NNN — номер спеки.
- Размер: ≤ 2 страницы (≤ 80 строк).
- Шаблон: [`templates/feature-summary.md`](../templates/feature-summary.md).
- Frontmatter: обязательные `status`, `slug` + опциональный `related` (связь с bounded contexts и C4 уровнями).

## Связь с другими слоями

- **Drill-down**: `specs/<NNN>-*/spec.md` (полная спека с US/FR/SC).
- **Cross-references**: `domain/<context>.md` (какие bounded context'ы затрагивает).
- **Архитектура**: `architecture/L<n>-*.md` (где реализован).

## Когда добавлять новую сводку

1. Создана новая фича (Pass N+1) → мигрировать сводку в features/.
2. Существующая фича протухла → обновить сводку (не удалять).
3. Сводка устарела безвозвратно → перевести `status: Archived` (не удалять из git).