# Quickstart: Шаблоны автоматических новостей сайта

**Branch**: `128-news-publish-templates` | **Date**: 2026-08-03

Phase 1 output. Runnable validation scenarios, доказывающие что фича
работает end-to-end. Не включает реализацию (см. `tasks.md` в Phase 2) —
только проверочные шаги.

## Предпосылки

- `karaoke-app` собран и запущен на admin-машине (контейнер или IDE).
- `karaoke-web` запущен на проде (или локально, имитируя прод).
- `webvue3` dev-сервер (`npm run dev`) на admin-машине, открыт
  `/publish-templates` в браузере.
- В БД есть хотя бы одна песня с `id_status >= 6`, `publish_date`/`publish_time`
  в ближайшие 10 минут (для проверки `air`) и `newsAvailableAnnounced=true`
  (для проверки `premium`).
- Kill-switch `newsAutoPublishKillSwitch` в `tbl_public_settings` = `"false"`
  (или строка отсутствует) — иначе auto-новости не создаются (см.
  `News.isNewsAutoPublishKillSwitchActive`).

## Сценарий 1: Дефолты byte-идентичны хардкоду (FR-010, FR-015, SC-002)

Цель: после релиза (без правки шаблонов) вид auto-новостей не меняется.

**Шаги**:
1. Не открывать UI шаблонов новостей (не сохранять значения).
2. Создать тестовую песню, запустить `checkOnAirWindow` (дождаться
   тика `SongReleaseAnnouncementScheduler` или вызвать метод вручную).
3. Сравнить `title`/`body` созданной `tbl_news`-записи с прежними
   хардкод-строками:
   - `air`: `"Новая песня: ${author} — ${songName}${albumYearSuffix}"`
   - `air`: `"Песня «${songName}» (${bodyDetails}) вышла в эфир."`

**Ожидаемый результат**: `title` и `body` byte-идентичны строкам, которые
генерировал бы `SongReleaseAnnouncementService` до фичи (сравнение с
git-историей `SongReleaseAnnouncementService.kt` до правки).

**Где смотреть**:
- `SELECT title, body FROM tbl_news WHERE source='auto' AND category='air' ORDER BY id DESC LIMIT 1` на prod-БД.

## Сценарий 2: Правка шаблона `air` без перезапуска (FR-008, SC-001, SC-005)

Цель: изменённый шаблон применяется к следующей auto-новости без
перезапуска контейнеров.

**Шаги**:
1. Открыть `webvue3` → `/publish-templates` → вкладка «Новости сайта».
2. Тип «В эфире (air)» → поле `body` → добавить эмодзи 🎵 в конец:
   `Песня «{songName}» ({bodyDetails}) вышла в эфир. 🎵`
3. Выбрать `target=remote` (prod-БД), нажать «Сохранить».
4. НЕ перезапускать `karaoke-web`.
5. Смоделировать выход тестовой песни в эфир (плановый тик
   `SongReleaseAnnouncementScheduler` или ручной вызов
   `checkOnAirWindow`).

**Ожидаемый результат**: новая запись `tbl_news` с `category='air'`
содержит `... вышла в эфир. 🎵` (с эмодзи). Старые новости НЕ
переписаны (FR-011).

**Где смотреть**:
- `SELECT title, body FROM tbl_news WHERE source='auto' AND category='air' ORDER BY id DESC LIMIT 1` на prod-БД.
- `SELECT value FROM tbl_public_settings WHERE key='newsTemplateAirBody'` на prod-БД — должно быть `Песня «{songName}» ({bodyDetails}) вышла в эфир. 🎵`.

## Сценарий 3: Правка шаблона `premium` (FR-012, User Story 2)

Цель: premium-категория аналогично применяет новый шаблон.

**Шаги**:
1. В UI выбрать тип «В коллекции (premium)» → поле `title` →
   заменить на `{author} — {songName} (премиум)`.
2. `target=remote`, сохранить.
3. На admin-машине: найти тестовую песню с
   `newsAvailableAnnounced=false`, выставить `=true`,
   `Song.saveToDb()`, запустить sync (или вручную вызвать
   `detectAndAnnounceAvailability` на prod).

**Ожидаемый результат**: новая `tbl_news` `category='premium'` с
`title = "Группа — Песня (премиум)"`.

## Сценарий 4: Превью шаблона (FR-009, SC-003)

Цель: превью возвращает `title`+`body` отдельно, ≤3 сек, без записи в БД.

**Шаги**:
1. В UI «Новости сайта» → ввести id тестовой песни в поле превью.
2. Нажать «Превью».

**Ожидаемый результат**:
- Ответ ≤ 3 сек.
- Показаны два поля: отрендеренный `title` и `body` с подставленными
  значениями.
- `SELECT count(*) FROM tbl_news WHERE source='auto' AND created_at > now() - interval '1 minute'` — не увеличился (превью не создаёт запись).

**Граничный случай**: в шаблон ввести `{nonexistent}` → превью
показывает literal-текст `{nonexistent}` (FR-005).

## Сценарий 5: Сброс к дефолту (FR-013, User Story 4)

Цель: кнопка возвращает заводское значение в поле (без автосохранения).

**Шаги**:
1. Изменить поле `body` типа `air`.
2. Нажать «Сбросить к дефолту».

**Ожидаемый результат**:
- Поле заполнилось `Песня «{songName}» ({bodyDetails}) вышла в эфир.`
- В БД НЕТ записи (сброс только заполняет поле, не сохраняет —
  нужно нажать «Сохранить» отдельно, FR User Story 4 Acceptance 1).

**Где смотреть**:
- `SELECT value FROM tbl_public_settings WHERE key='newsTemplateAirBody'` — значение НЕ изменилось (только UI-состояние).

## Сценарий 6: Unknown key отклоняется (FR-012 валидация)

Цель: POST с неразрешённым key не создаёт мусор в БД.

**Шаги**:
1. `curl -X POST 'http://admin:8897/api/news/templates' -d 'key=newsTemplateFoo&value=test&target=remote'`

**Ожидаемый результат**:
- `{ "success": false, "error": "unknown key: newsTemplateFoo (allowed: ...)" }`.
- `SELECT * FROM tbl_public_settings WHERE key='newsTemplateFoo'` — пусто.

## Сценарий 7: Превышение лимита title (Edge Case)

Цель: title > 500 символов усекается с `…`.

**Шаги**:
1. В UI установить шаблон `airTitle` = `{author}{author}{author}...`
   (повторять `{author}` до превышения 500 после рендера).
2. Превью на песне с длинным автором.

**Ожидаемый результат**:
- `titleLength <= 500`, `titleTruncated = true`.
- `title` заканчивается `…`.
- `body` не затронут (body — TEXT без лимита).

## Сценарий 8: Lint-гейты CI

Цель: фича проходит CI 7/7 (NON-NEGOTIABLE, AGENTS.md CI-gate).

**Шаги** (в feature-ветке `128-news-publish-templates`):
```bash
./gradlew ktlintCheck
cd webvue3 && npm run lint:check && cd ..
cd karaoke-public && npm run lint:check && cd ..
bash tools/check-kdoc-coverage.sh
bash tools/check-jsdoc-coverage.sh webvue3
pre-commit run --all-files
./gradlew :karaoke-app:compileKotlin
```

**Ожидаемый результат**: все зелёные, baseline не растёт. Per-feature
документ `docs/features/news-templates.md` создан (FR-009
constitution).

## Ссылки

- Контракт endpoints: [contracts/api.md](./contracts/api.md)
- Сущности и лимиты: [data-model.md](./data-model.md)
- Архитектурные решения: [research.md](./research.md)
- Спецификация: [spec.md](./spec.md)