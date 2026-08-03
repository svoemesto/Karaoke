---

description: "Spec 125 — backfill флагов публикации готовых песен + kill-switch на tbl_public_settings (архитектурный fix фичи 124)"
---

# Spec 125: Backfill флагов публикации готовых песен (kill-switch через `tbl_public_settings`)

> **Связь с фичей 124**. Spec 124 содержал архитектурную ошибку — kill-switch хранился в
> `KaraokeProperties` (файл `/sm-karaoke/system/Karaoke.properties`, существует только на
> admin-машине). На проде `karaoke-app` не разворачивается, Spring сканирует только
> `com.svoemesto.karaokeweb.*`, и `KaraokeProperties.getBoolean(...)` на проде молча возвращал
> default `false`. То есть фича 124 после merge в master **НЕ работала на проде** — kill-switch
> всегда выключен, лавина auto-новостей возможна. Эта спека 125 — тот же сценарий
> (backfill на LOCAL → sync LOCAL→PROD → 0 новостей в `tbl_news`), но с **правильным** слоем
> для kill-switch (`tbl_public_settings` в Postgres), который ЕДИНСТВЕННО работает и на admin,
> и на проде (см. `CaptchaConfigService.kt:35` — там же читаются настройки Yandex SmartCaptcha
> на проде). Backfill-часть фичи 124 (`SongReleaseAnnouncementService.backfillPublishFlags`,
> endpoint `/api/utils/backfillpublishflags`, UI кнопка) **остаётся без изменений** — она
> работает только на admin-машине и там проблем нет. Меняется только: 1) источник kill-switch
> (KaraokeProperties → tbl_public_settings), 2) endpoint для on/off (`karaoke-app/ApiController`
> → `karaoke-web/.../controllers/PublicSettingsWebController`).

**Input**: Design documents in `/specs/125-news-flags-backfix/` и `/specs/124-news-flags-backfill/` (история решений, data-model, контракты актуальные для backfill).

**Scope of this fix**: только kill-switch и его endpoint. Endpoint `backfillpublishflags`,
сервис `SongReleaseAnnouncementService.backfillPublishFlags`, UI-кнопка и миграции backfill
НЕ пересматриваются.

## Что меняется vs 124

| Компонент | 124 (баг) | 125 (fix) |
|-----------|-----------|-----------|
| Где хранится kill-switch | `KaraokeProperties.newsAutoPublishKillSwitch` (файл `/sm-karaoke/system/Karaoke.properties`) | `tbl_public_settings.key='newsAutoPublishKillSwitch'` (Postgres) |
| Endpoint для on/off | `POST /api/properties/setproperty` в `karaoke-app/ApiController.kt` (404 на проде) | `POST /api/properties/setproperty` в новом `karaoke-web/.../controllers/PublicSettingsWebController.kt` |
| Чтение kill-switch в `News.createAutoAnnouncement` | `KaraokeProperties.getBoolean(...)` — статический singleton, читает файл (не работает на проде) | Прямой JDBC к `tbl_public_settings` (как `CaptchaConfigService` на проде) |
| Миграция | Не было | INSERT ключа в `tbl_public_settings` (`deploy/karaoke-db/37_news_auto_publish_kill_switch.sql`, ручная) |
| Sync LOCAL↔PROD | Не нужен (KaraokeProperties — локальный файл) | Не нужен (kill-switch ставится напрямую на прод через прод-endpoint, см. сценарий ниже) |
| Backfill endpoint/service/UI | Без изменений | Без изменений |

## User Story (одна — kill-switch-фикс)

**US1 (P1, MVP)** — kill-switch через `tbl_public_settings` доступен на проде.

**Independent test**:
1. После применения миграции `37_*.sql` на обеих БД (LOCAL и PROD): `SELECT key, value FROM tbl_public_settings WHERE key='newsAutoPublishKillSwitch'` возвращает 1 строку с `value=''`.
2. На проде (curl `http://localhost:8897/api/properties/setproperty -d "key=newsAutoPublishKillSwitch&stringValue=true"` → HTTP 200, JSON body `true`).
3. Проверить в БД на проде: `SELECT value FROM tbl_public_settings WHERE key='newsAutoPublishKillSwitch'` → `value='true'`.
4. Триггернуть air-новость (любую готовую песню со ёще не-air): `SongReleaseAnnouncementService.checkOnAirWindow` → `News.createAutoAnnouncement` → `isNewsAutoPublishKillSwitchActive` читает `'true'` → возвращает `null` → в `tbl_news` 0 новых строк.
5. Снять kill-switch: `stringValue=false` → следующий триггер создаст новость нормально.

## Acceptance Criteria

- **SC-1**: Endpoint `POST /api/properties/setproperty` на проде возвращает 200/JSON `{result:true}` (не 404).
- **SC-2**: После `setproperty stringValue=true` на проде последующие вызовы `News.createAutoAnnouncement` возвращают `null` без INSERT в `tbl_news`.
- **SC-3**: После `setproperty stringValue=false` (или default) `News.createAutoAnnouncement` работает нормально.
- **SC-4**: `News.createNew` (source="manual") НЕ проверяет kill-switch в обоих состояниях.
- **SC-5**: Миграция `37_*.sql` идемпотентна (повторное применение не падает).
- **SC-6**: Чтение kill-switch через прямой JDBC НЕ ломает существующий scheduler `SongReleaseAnnouncementScheduler` (на проде) — все 7 проверок CI зелёные, при деплое контейнер стартует без ошибок.

## Implementation (Tasks summary)

- **T1** — миграция `deploy/karaoke-db/37_news_auto_publish_kill_switch.sql` (INSERT ключа, идемпотентный `ON CONFLICT DO NOTHING`).
- **T2** — новый контроллер `karaoke-web/.../controllers/PublicSettingsWebController.kt` (`@PostMapping("/api/properties/setproperty")` → UPDATE/INSERT в `tbl_public_settings` через прямой JDBC).
- **T3** — `News.kt`: заменить `KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")` на `isNewsAutoPublishKillSwitchActive(database)` (новый приватный helper в companion object).
- **T4** — `KaraokeProperties.kt`: убрать строку с `newsAutoPublishKillSwitch` из `listKaraokeProperties` (бесполезна после миграции в `tbl_public_settings`).
- **T5** — обновить `docs/features/news-publish-backfill.md` (новый подраздел «Архитектурный fix 125» с ссылкой на эту спеку).
- **T6** — обновить `docs/architecture-notes.md` (Pass 35: «kill-switch через tbl_public_settings вместо KaraokeProperties»).
- **T7** — CI-gate + PR.

## Clarifications (вместо запрошенных на этапе 124)

Нет открытых [NEEDS CLARIFICATION] — архитектурная ошибка 124 описана вверху, направление исправления однозначно.

## Edge cases

1. **`tbl_public_settings` ещё не содержит ключа `newsAutoPublishKillSwitch`** (миграция не применена) — `SELECT` вернёт `rs.next()=false` → `isNewsAutoPublishKillSwitchActive` возвращает `false`. Админ должен применить миграцию. Документировано в quickstart.
2. **Endpoint `/api/properties/setproperty` вызывается на admin-машине** (там где Spring сканирует ОБА пакета) — там же есть старый endpoint `karaoke-app/.../ApiController.doSetProperty` (на master, не трогаем). Будет 2 endpoint'а с одинаковым путём → Spring выберет ОДИН (обычно более специфичный из `@RequestMapping`). На admin-машине НЕ сломается. На проде работает только наш новый (нет конфликта).
3. **Конкурентный UPDATE** (несколько admin одновременно) — последняя запись выигрывает. Для kill-switch это OK (1 строка, eventual consistency через `last_update` timestamp).
4. **Connection leak**: контроллер закрывает соединение в finally (по образцу `PublicSettingsController.kt:42`).
5. **`tbl_public_settings` нет на БД** (миграция `07_*.sql` ещё не применена) — SQL упадёт → endpoint вернёт `false`, kill-switch не активен (default). Лавина возможна, но это уже операционный риск на admin, а не баг кода.

## Assumptions

- На проде уже применена миграция `07_public_settings.sql` (есть на всех БД с 2021+). Verify: `SELECT 1 FROM tbl_public_settings LIMIT 1`.
- Админ имеет прямой SSH-доступ к проде и может выполнить `psql -h ... -c "\i deploy/karaoke-db/37_news_auto_publish_kill_switch.sql"` (или применит скриптом из `do.sh` при деплое).
- `SongReleaseAnnouncementService.backfillPublishFlags` из фичи 124 НЕ пересматривается — он работает только на admin-машине (karaoke-app), где Spring сканирует karaoke-app пакет.

## Связанные документы

- `specs/124-news-flags-backfill/spec.md` — оригинальная спека (kill-switch через KaraokeProperties — БАГ, см. шапку 125).
- `specs/124-news-flags-backfill/research.md`, `data-model.md`, `contracts/api.md`, `quickstart.md` — актуальны для backfill-части (она не меняется).
- `docs/features/news-publish-backfill.md` — per-feature документ (обновлён в Т5).
- `docs/architecture-notes.md`, Pass 35 — запись о PR.
- `karaoke-web/services/CaptchaConfigService.kt:35` — образец прямого JDBC к `tbl_public_settings`.
- `karaoke-app/controllers/PublicSettingsController.kt` — образец endpoint'а для UPDATE (но на проде 404, поэтому мы делаем дубль в karaoke-web).
- `deploy/karaoke-db/07_public_settings.sql` — определение таблицы (комментарий в шапке файла явно указывает: «karaoke-app на сервере не разворачивается, эта таблица доступна и локально, и на сервере»).
