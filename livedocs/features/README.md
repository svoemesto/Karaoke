# features/ — SDD слой (индекс)

> Сводки фич (≤ 2 страницы каждая). Drill-down — в `specs/<NNN>-*/spec.md`.

## Содержимое

| Файл | Что делает | Drill-down |
|------|------------|------------|
| [001-code-standards-docs.md](001-code-standards-docs.md) | Приведение к стандартам + документирование (Phase 001) | [specs/001-.../spec.md](../../specs/001-code-standards-docs/spec.md) |
| [002-ci-lint-enforcement.md](002-ci-lint-enforcement.md) | CI lint enforcement (GitHub Actions) | [specs/002-.../spec.md](../../specs/002-ci-lint-enforcement/spec.md) |
| [003-about-page.md](003-about-page.md) | Страница «О проекте» | [specs/003-.../spec.md](../../specs/003-about-page/spec.md) |
| [004-reasons-to-register.md](004-reasons-to-register.md) | «5 причин зарегистрироваться» на главной | [specs/004-.../spec.md](../../specs/004-reasons-to-register/spec.md) |
| [005-free-vs-premium.md](005-free-vs-premium.md) | Таблица «FREE vs PREMIUM» на /premium | [specs/005-.../spec.md](../../specs/005-free-vs-premium/spec.md) |
| [008-special-orders.md](008-special-orders.md) | «Отдельные песни разных авторов» — виртуальная плашка | [specs/008-.../spec.md](../../specs/008-special-orders/spec.md) |
| [009-listening-history.md](009-listening-history.md) | История прослушиваний для пользователей | [specs/009-.../spec.md](../../specs/009-listening-history/spec.md) |
| [010-lyrics-spec-tags.md](010-lyrics-spec-tags.md) | Спецтеги в тексте для авто-разметки маркеров | [specs/010-.../spec.md](../../specs/010-lyrics-spec-tags/spec.md) |
| [011-album-song-rename.md](011-album-song-rename.md) | Альбом как сущность + переименование Settings→Song | [specs/011-.../spec.md](../../specs/011-album-song-rename/spec.md) |
| [012-entity-description-fields.md](012-entity-description-fields.md) | Доп. поля Author/Album/Song + UI Закромов | [specs/012-.../spec.md](../../specs/012-entity-description-fields/spec.md) |
| [013-song-status-filter.md](013-song-status-filter.md) | Показ на проде только песен со статусом ≥3 | [specs/013-.../spec.md](../../specs/013-song-status-filter/spec.md) |
| [014-album-cell-album-cover-modal.md](014-album-cell-album-cover-modal.md) | Альбомы: клик по ячейке открывает модалку обложки | [specs/014-.../spec.md](../../specs/014-album-cell-album-cover-modal/spec.md) |
| [014-lyrics-search-replacement.md](014-lyrics-search-replacement.md) | Замена поискового движка SearXNG | [specs/014-.../spec.md](../../specs/014-lyrics-search-replacement/spec.md) |
| [015-search-engine-selection.md](015-search-engine-selection.md) | Выбор поискового движка (тексты + обложки) | [specs/015-.../spec.md](../../specs/015-search-engine-selection/spec.md) |
| [018-fix-spec-tag-markers-at-zero.md](018-fix-spec-tag-markers-at-zero.md) | Spec-tag маркеры в нулевой позиции | [specs/018-fix-spec-tag-markers-at-zero/spec.md) |
| [017-editor-status-bypass.md](017-editor-status-bypass.md) | Редактор видит все песни независимо от статуса | [specs/017-.../spec.md](../../specs/017-editor-status-bypass/spec.md) |
| [018-fix-album-name-year-grouping.md](018-fix-album-name-year-grouping.md) | Закрома: альбомы с одинаковым именем и разными годами | [specs/018-.../spec.md](../../specs/018-fix-album-name-year-grouping/spec.md) |
| [019-fix-setcontent-resets-position.md](019-fix-setcontent-resets-position.md) | setContent/setOptions сбрасывают позиции маркеров | [specs/019-.../spec.md](../../specs/019-fix-setcontent-resets-position/spec.md) |
| [020-fix-search-lyrics-autofill.md](020-fix-search-lyrics-autofill.md) | Автоподстановка найденного текста песни | [specs/020-.../spec.md](../../specs/020-fix-search-lyrics-autofill/spec.md) |
| [021-dev-pc-agent-permissions.md](021-dev-pc-agent-permissions.md) | Unrestricted agent permissions on dev-pc | [specs/021-.../spec.md](../../specs/021-dev-pc-agent-permissions/spec.md) |
| [022-song-status-lifecycle.md](022-song-status-lifecycle.md) | Расширенный жизненный цикл статусов готовности | [specs/022-.../spec.md](../../specs/022-song-status-lifecycle/spec.md) |
| [023-songs-audio-root-column.md](023-songs-audio-root-column.md) | Колонка audio_parent_id в таблице песен | [specs/023-.../spec.md](../../specs/023-songs-audio-root-column/spec.md) |
| [029-fix-queue-lane-stall.md](029-fix-queue-lane-stall.md) | Устранение зависания очереди по лейнам | [specs/029-.../spec.md](../../specs/029-fix-queue-lane-stall/spec.md) |
| [030-add-archive-album-type.md](030-add-archive-album-type.md) | Добавить тип альбома «Архивные записи» | [specs/030-.../spec.md](../../specs/030-add-archive-album-type/spec.md) |
| [031-add-tribute-cover-album-type.md](031-add-tribute-cover-album-type.md) | Добавить тип альбома «Трибьют/Кавер» | [specs/031-.../spec.md](../../specs/031-add-tribute-cover-album-type/spec.md) |
| [082-fix-import-folder-oom.md](082-fix-import-folder-oom.md) | Устойчивый импорт из папки без OOM | [specs/082-.../spec.md](../../specs/082-fix-import-folder-oom/spec.md) |
| [083-album-cover-square-cell.md](083-album-cover-square-cell.md) | Альбомы: квадратная ячейка обложки | [specs/083-.../spec.md](../../specs/083-album-cover-square-cell/spec.md) |
| [087-fix-shared-db-connection.md](087-fix-shared-db-connection.md) | Изоляция JDBC + retry очереди при сетевом сбое | [specs/087-.../spec.md](../../specs/087-fix-shared-db-connection/spec.md) |
| [088-fix-queue-swallowed-errors.md](088-fix-queue-swallowed-errors.md) | Единообразная обработка сбоев БД в очереди | [specs/088-.../spec.md](../../specs/088-fix-queue-swallowed-errors/spec.md) |
| [089-auto-news-song-release.md](089-auto-news-song-release.md) | Авто-новости о выходе песни в эфир | [specs/089-.../spec.md](../../specs/089-auto-news-song-release/spec.md) |
| [090-news-pagination.md](090-news-pagination.md) | Пагинация ленты новостей | [specs/090-.../spec.md](../../specs/090-news-pagination/spec.md) |
| [091-fix-connection-leak.md](091-fix-connection-leak.md) | Устранить утечку JDBC от одноразовых потоков | [specs/091-.../spec.md](../../specs/091-fix-connection-leak/spec.md) |
| [092-fix-auto-news-triggers.md](092-fix-auto-news-triggers.md) | Триггеры авто-новостей независимо от sync | [specs/092-.../spec.md](../../specs/092-fix-auto-news-triggers/spec.md) |
| [093-news-pagination-top-35.md](093-news-pagination-top-35.md) | Пагинация НАД таблицей, ≤35 строк | [specs/093-.../spec.md](../../specs/093-news-pagination-top-35/spec.md) |
| [094-fix-approve-news-failure.md](094-fix-approve-news-failure.md) | Approve: ошибка «Ошибка запроса», новость не появляется | [specs/094-.../spec.md](../../specs/094-fix-approve-news-failure/spec.md) |
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
| [017-fix-markers-at-position-zero.md](017-fix-markers-at-position-zero.md) | Spec tags: «залипание» маркеров в нулевой позиции | [specs/017-.../spec.md](../../specs/017-fix-markers-at-position-zero/spec.md) |
| [017-editor-status-bypass.md](017-editor-status-bypass.md) | Редактор видит все песни независимо от статуса | [specs/017-.../spec.md](../../specs/017-editor-status-bypass/spec.md) |
| [154-remove-scheduled-publications-monitoring.md](154-remove-scheduled-publications-monitoring.md) | Убрать мониторинг запланированных публикаций | [specs/154-.../spec.md](../../specs/154-remove-scheduled-publications-monitoring/spec.md) |
| [155-editor-typograph-button.md](155-editor-typograph-button.md) | Кнопка «Типограф» в онлайн-редакторе | [specs/155-.../spec.md](../../specs/155-editor-typograph-button/spec.md) |
| [155-song-state-colors.md](155-song-state-colors.md) | Актуализация статусов и цветов песен | [specs/155-.../spec.md](../../specs/155-song-state-colors/spec.md) |
| [156-remove-songs-table-platform-flags.md](156-remove-songs-table-platform-flags.md) | Удалить 18 столбцов-флагов публикации из «Песни» | [specs/156-.../spec.md](../../specs/156-remove-songs-table-platform-flags/spec.md) |
| [156-publish-slots-range.md](156-publish-slots-range.md) | Расширение диапазона слотов публикации (10→22) | [specs/156-.../spec.md](../../specs/156-publish-slots-range/spec.md) |
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
| [016-fix-spec-tags-marker-loss-on-reopen.md](016-fix-spec-tags-marker-loss-on-reopen.md) | Спецтеги: сохранение маркеров после `Apply → Save → reopen` | [specs/016-.../spec.md](../../specs/016-fix-spec-tags-marker-loss-on-reopen/spec.md) |
| [124-news-flags-backfill.md](124-news-flags-backfill.md) | Backfill флагов публикаций готовых песен без создания новостей | [specs/124-.../spec.md](../../specs/124-news-flags-backfill/spec.md) |
| [125-player-status-gate.md](125-player-status-gate.md) | Доступность плеера в таблице «Песни» при статусе ≥4 | [specs/125-.../spec.md](../../specs/125-player-status-gate/spec.md) |
| [190-playlist-play-button-and-stems-cancel.md](190-playlist-play-button-and-stems-cancel.md) | Плейлисты: запуск с любой песни + превью + фикс вейвформ | [specs/190-.../spec.md](../../specs/190-playlist-play-button-and-stems-cancel/spec.md) |
| [232-admin-song-editor-local-db.md](232-admin-song-editor-local-db.md) | Облегчённый редактор песен в админке пишет в локальную БД admin-машины (mode='song' в SongEditorController всегда Connection.local()) | [specs/232-.../spec.md](../../specs/232-admin-song-editor-local-db/spec.md) |
| [238-import-folder-author-album-cover.md](238-import-folder-author-album-cover.md) | Импорт из папки: поиск «родителя» только у того же автора + автообложка нового альбома из графического файла в `rootFolder` | [specs/238-.../spec.md](../../specs/238-import-folder-author-album-cover/spec.md) |
| [250-unify-site-header.md](250-unify-site-header.md) | Унификация шапки karaoke-public: единый `<AppHeader>`-компонент (логотип справа + кликабельный), 20 view-миграций, slot-based `EditorWorkView` | [specs/250-.../spec.md](../../specs/250-unify-site-header/spec.md) |

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