# Quickstart: проверка рефакторинга `settings` → `song`

Эта фича не меняет поведение (FR-008) — quickstart проверяет, что после
переименования всё продолжает работать так же, как до него, плюс что все
контракты (`contracts/rename-contracts.md`) синхронизированы между backend
и frontend.

## Предусловия

- Локальный docker-стек поднят (`deploy/do.sh`, см. `DEVELOPMENT.md`):
  `karaoke-app`, `karaoke-web`, `webvue3` (dev-режим или собранный),
  локальная PostgreSQL.
- Ветка `102-rename-song-settings-vars` собрана без ошибок:
  `./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`.
- `webvue3` пересобран: `cd webvue3 && npm run build` (или `npm run dev` для
  ручной проверки).

## Сценарий 1 — Компиляция и статический анализ (backend)

```bash
./gradlew ktlintCheck
./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel
```

**Ожидаемо**: 0 ошибок компиляции, 0 новых нарушений ktlint.

## Сценарий 2 — Статический анализ (webvue3)

```bash
cd webvue3
npm run lint:check
npx prettier --check "src/**/*.{vue,js,ts,json}"
cd ..
```

**Ожидаемо**: 0 новых нарушений.

## Сценарий 3 — Health Report (Контракт 1)

1. Открыть админку → раздел Health Report (`webvue3`, `Common/HealthReport`).
2. Убедиться, что таблица со списком проблемных песен отображается (не
   пустая ошибка в консоли браузера про `undefined` ключ).
3. Нажать «repair» на одной из строк.
4. **Ожидаемо**: запрос уходит с новым идентификатором (`songId` в теле/URL,
   см. `contracts/rename-contracts.md` → Контракт 1), UI обновляется по SSE
   без ручной перезагрузки страницы (Контракт 4).

## Сценарий 4 — Смена статуса песни через legacy-эндпоинт (Контракт 2)

1. Открыть legacy-страницу, использующую
   `karaoke-app/src/main/resources/static/settings_context.js`
   (см. `DEVELOPMENT.md` за актуальным URL, если не очевиден из кода).
2. Сменить статус песни через соответствующий UI-элемент.
3. **Ожидаемо**: `POST /changesettingsstatus` возвращает 200 (не 400
   «missing required parameter»), статус песни в БД обновился.

## Сценарий 5 — Legacy-редактирование списка песен (Контракт 3)

1. Открыть `songs.html` (или `songs2.html` — обе используют одни и те же
   form-поля) в браузере.
2. Изменить одно текстовое поле (например, автора) у одной песни.
3. Отправить форму (`POST /songs_update`).
4. Перезагрузить страницу.
5. **Ожидаемо**: изменение сохранилось — форма и контроллер используют
   одинаковые (новые) имена полей `song_*` (см.
   `contracts/rename-contracts.md` → Контракт 3).

## Сценарий 6 — Таблица песен и async-очередь (регрессия без контракта)

1. Открыть `webvue3` → таблица песен (`Songs/SongsTable.vue`).
2. Запустить любое фоновое задание над песней (например, повторный рендер
   демо) и убедиться, что прогресс отображается как раньше (async-очередь,
   `KaraokeProcess`/`KaraokeProcessDTO` — переименование `settingsId` →
   `songId` в `KaraokeProcessDTO` не имеет фронтенд-потребителя, см.
   `research.md`, Решение 1, — проверка чисто отрицательная: ничего не
   должно сломаться).

## Сценарий 7 — grep-регрессия (финальная проверка перед PR)

```bash
# Не должно остаться прямых Song-типизированных `settings`:
grep -rnE '\bsettings\s*:\s*Song\b' --include='*.kt' karaoke-app karaoke-web

# Не должно остаться известных wire-имён:
grep -rn "settingsId\|settingsFileName" \
  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/HealthReportDTO.kt \
  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessDTO.kt \
  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SseNotification.kt \
  karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/MainController.kt \
  karaoke-app/src/main/resources/static/settings_context.js \
  webvue3/src/components/Songs/store.js \
  webvue3/src/components/Common/HealthReport/store.js \
  webvue3/src/components/Common/HealthReport/components/HealthReportTableBody.vue \
  webvue3/src/components/Common/HealthReport/components/HealthReportTableHeader.vue

# Должно остаться (не трогать):
grep -n "settings_id" deploy/karaoke-db/*.sql karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcess.kt
grep -n "settingsField" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokePlatform.kt
grep -n '"settings"' karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/sync/SyncTarget.kt
```

**Ожидаемо**: первые два поиска — 0 совпадений; последние три — совпадения
есть и не изменились (см. `data-model.md`, Категория 6).

## Сценарий 8 — Документация (FR-009)

```bash
grep -n "settingsId\|Settings\b\|tbl_settings" docs/features/premium-stems.md
```

**Ожидаемо**: 0 совпадений — строка обновлена на `songId=0` /
`Song`/`tbl_songs` (см. `plan.md`, Constitution Check → VI).
