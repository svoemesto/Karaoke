# SQL migrations (detailed)

> **Домен**: system (infrastructure)
> **Компонента**: детальный каталог 54 SQL миграций `deploy/karaoke-db/`.

## Назначение

`deploy/karaoke-db/*.sql` — **54 миграции** в формате `NN_<slug>.sql`.
Идемпотентны через `IF NOT EXISTS` / `DROP IF EXISTS`.

## Каталог (по NNN)

| NNN | Slug | Что создаёт/изменяет | Связанный Knowledge |
|---|---|---|---|
| **01** | `initdb` | Начальная схема (`tbl_pictures`, `tbl_processes`, и др.) | [pictures.md](../../domains/catalog/components/pictures.md), [async-process-queue.md](../../domains/processing/components/async-process-queue.md) |
| **02** | `result_text_and_index` | `tbl_settings.result_text` + GIN-индекс для full-text search | [utilities.md](../../system/utilities.md) |
| **03** | `events` | `tbl_events` (web-аналитика) | [entities-catalog.md#webevent](../../domains/catalog/components/entities-catalog.md#webevent) |
| **04** | `id_boosty_files_and_index` | `tbl_settings.id_boosty_files` | [publishing-services.md](../../domains/publishing/components/publishing-services.md) |
| **04** | `melody` | `id_youtube_melody`, `id_vk_melody`, `id_telegram_melody`, `id_pl_melody` + индексы | (Pass 343+ для деталей) |
| **05** | `index_tabs_variant` | Индексы для табов | (Pass 343+) |
| **06** | `site_users` | `tbl_site_users` | [identity domain](../../domains/identity/domain.md) |
| **07** | `public_settings` | `tbl_public_settings` | |
| **08** | `ip_country` | `tbl_ip_country` (GeoIp кэш) | [external-api-clients.md#geoipservice](../../domains/integration/components/external-api-clients.md) |
| **09** | `playlists` | `tbl_site_playlists`, `tbl_site_playlist_items` | [store-site-playlists.md](../frontend/store-site-playlists.md) |
| **10** | `song_assignments` | `tbl_song_assignments`, `tbl_song_assignment_drafts` | [store-song-editor.md](../frontend/store-song-editor.md) |
| **11** | `pictures_unique_name` | UNIQUE constraint на `picture_name` | [pictures.md](../../domains/catalog/components/pictures.md) |
| **12** | `site_user_limits` | `tbl_site_user_limits` | [identity domain](../../domains/identity/domain.md) |
| **13** | `site_user_editor_role` | Editor role для `SiteUser` | [store-song-editor.md](../frontend/store-song-editor.md) |
| **14** | `site_user_premium_sources` | Источники premium-доступа | [monetization domain](../../domains/monetization/domain.md) |
| **15** | `monetization` | Таблицы монетизации | [monetization domain](../../domains/monetization/domain.md) |
| **16** | `cart_and_orders` | `tbl_cart_items`, `tbl_orders` | [composable-use-cart.md](../frontend/composable-use-cart.md) |
| **17** | `dictionaries` | `tbl_dictionaries` | [dictionaries.md](../../domains/catalog/components/dictionaries.md) |
| **17** | `site_user_personal_discount` | Персональные скидки `SiteUser` | [monetization domain](../../domains/monetization/domain.md) |
| **18** | `author_aliases` | `tbl_author_aliases` (псевдонимы) | [entities-catalog.md#author](../../domains/catalog/components/entities-catalog.md#author) |
| **19** | `site_chat_messages` | `tbl_site_chat_messages` | [entities-catalog.md#sitechatmessage](../../domains/catalog/components/entities-catalog.md#sitechatmessage) |
| **20** | `news` | `tbl_news` | [entities-catalog.md#news](../../domains/catalog/components/entities-catalog.md#news) |
| **21** | `site_user_welcome_message` | Welcome message для `SiteUser` | [identity domain](../../domains/identity/domain.md) |
| **22** | `stem_jobs` | `tbl_stem_jobs` | [remaining-models.md#stemjob](../../domains/catalog/components/remaining-models.md#stemjob) |
| **23** | `demo_publish_links` | Demo-публикации | (Pass 343+) |
| **24** | `song_type` | `SongType` enum | [entities-catalog.md#song](../../domains/catalog/components/entities-catalog.md#song) |
| **25** | `audio_parent` | Audio parent (parent_id для audio) | (Pass 343+) |
| **26** | `player_readiness_flags` | `*_ready` колонки в `tbl_songs` | [health-report.md#reconcileplayerreadinessflags](../../domains/health/components/health-report.md) |
| **27** | `author_special_order` | Author special order | (Pass 343+) |
| **27** | `listening_history` | `tbl_listening_history` | [entities-catalog.md#listeninghistory](../../domains/catalog/components/entities-catalog.md#listeninghistory) |
| ... (ещё ~25, см. `ls deploy/karaoke-db/*.sql`) | | | |

## Известные TODO

- [ ] **Каждая миграция** — детальное описание (Pass 343+).
- [ ] **Schema diagram** — er-diagram всех таблиц.
- [ ] **Зависимости между миграциями** — какие от каких зависят.

## Changelog

- **Pass 403** (2026-09-09): Initial detailed. Автор: agent (Karaoke).