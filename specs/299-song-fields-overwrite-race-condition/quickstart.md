# Quickstart: 299 — Перезатирание полей песни при фоновой обработке

> **Phase 1 output для `/speckit-plan`.** Описывает запуск на dev-машине + smoke test для проверки, что фикс работает end-to-end.

## Предусловия

- [ ] Ветка `299-song-fields-overwrite-race-condition` (уже создана на стадии `/speckit.specify`).
- [ ] Машина `nsa-i9` под OS-пользователем `nsa` (см. AGENTS.md «Машинно-специфичные исключения (Pass 282)» — разрешено пересобирать `karaoke-app` без согласия).
- [ ] Java/Kotlin toolchain: JDK 17 (см. `gradle/libs.versions.toml`), Gradle wrapper.
- [ ] PostgreSQL 15+ локально (через `deploy/karaoke-db` Docker container, см. `DEVELOPMENT.md`).
- [ ] `karaoke-app` контейнер **остановлен** на время сборки (см. AGENTS.md «Пересобирать/перезапускать `karaoke-app` только по согласию» — на `nsa-i9` под `nsa` разрешено).

## Шаг 1 — Подготовка кодовой базы

```bash
cd /home/nsa/Karaoke
git checkout 299-song-fields-overwrite-race-condition
git pull --ff-only  # если есть remote, иначе skip
git status  # проверяем, что чисто
```

- [ ] Текущая ветка `299-song-fields-overwrite-race-condition`.
- [ ] Нет незакоммиченных изменений.

## Шаг 2 — Реализация

> **Этот шаг выполняется на стадии `/speckit.implement`.** Ниже — краткий обзор того, что должно появиться в результате.

После реализации в коде должны быть:

### 2.1. `Song.kt` — новые методы
- `Song.saveToDbLocked(): Boolean` (~60 строк кода + 60 строк KDoc).
- `Song.Companion.loadFromDbByIdForUpdate(...)` (~30 строк кода + 30 строк KDoc).

### 2.2. `KaraokeProperties.kt` — новое поле
- `songSaveLockedTimeoutMs` с default `5000L`.

### 2.3. 8 горячих путей Pass 281 → `saveToDbLocked()`
- `UtilsAI.kt:applyFoundLyricsIfMissing`
- `Utils.kt:applyDuplicateOriginal`
- `Utils.kt:applyAudioParentMarkers`
- `Utils.kt:applyFamilySongSelection`
- `Utils.kt:autoAssignOriginalByWaveform`
- `Utils.kt:findAudioParentByWaveform` (4 reload'а)
- `Song.kt:setSourceMarkers` (2 reload'а)
- `Song.kt:setSourceText` (2 reload'а)

### 2.4. 25+ мест FR-020 → `saveToDbLocked()` или KDoc-обоснование
Полный список — в `tasks.md`. Для каждого места — вердикт hot/not-hot.

### 2.5. Документация
- `docs/ops/log-correlation.md` — добавить секцию про specs/299 маркеры.

## Шаг 3 — Сборка

```bash
# 1. Backend compile
./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Linters (см. AGENTS.md § "Перед каждым git commit")
./gradlew :karaoke-web:ktlintCheck

# 3. Backend bootJar (т.к. меняем karaoke-app — нужно пересобрать, см. AGENTS.md «Машинно-специфичные исключения (Pass 282)»)
./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel

# 4. KDoc coverage (CI gate)
bash tools/check-kdoc-coverage.sh --strict
```

- [ ] Все шаги завершились без ошибок.
- [ ] KDoc coverage ≥ 50%.
- [ ] ktlint baseline не вырос.

## Шаг 4 — Деплой на dev-машину

> **Важно:** на `nsa-i9` под `nsa` разрешено пересобирать/перезапускать `karaoke-app` без явного согласия (AGENTS.md, Pass 282).

```bash
# Остановить текущий контейнер
docker stop karaoke-app

# Пересобрать образ (если есть Dockerfile для karaoke-app)
cd deploy && bash do.sh build_karaoke_app  # или эквивалентная команда

# Запустить новый контейнер
docker start karaoke-app
sleep 5  # дать время на старт

# Проверить, что поднялся
docker logs karaoke-app --tail 20
curl http://localhost:8897/actuator/health  # или эквивалентный healthcheck
```

- [ ] `karaoke-app` запустился без ошибок в логе.
- [ ] Healthcheck возвращает `UP`.

## Шаг 5 — Smoke test (базовая проверка)

### 5.1. Создать тестовую песню

Через UI (`SongEdit.vue`) или прямой SQL:
```sql
INSERT INTO tbl_songs (song_name, song_author, source_text, id_status, ...)
VALUES ('TEST-LOCK-SONG', 'Test Author', '', 0, ...);
```

### 5.2. Запустить фоновый поиск текстов

Через UI (`SongsTable.vue` → «Найти текст» для одной песни). Или прямой вызов:
```bash
curl -X POST "http://localhost:8897/song/searchsongtext?id=<song_id>&lyricsSearchEngine=FOURGET"
```

### 5.3. Сразу после старта — изменить `songName` через SongEdit

```bash
# Через UI или прямой SQL:
UPDATE tbl_songs SET song_name = 'TEST-LOCK-SONG-EDITED' WHERE id = <song_id>;
```

### 5.4. Дождаться завершения поиска текстов (10-60 сек)

### 5.5. Проверить финальное состояние

```sql
SELECT id, song_name, song_author, source_text, id_status
FROM tbl_songs
WHERE id = <song_id>;
```

**Ожидаемый результат:**
- `song_name = 'TEST-LOCK-SONG-EDITED'` (ручная правка сохранена, НЕ перезатёрта поиском текстов).
- `source_text` непустой (текст найден и применён).
- `id_status >= 1` (TEXT_CREATE).

- [ ] Smoke test прошёл — `song_name` сохранён, `source_text` обновлён.

### 5.6. Проверить логи на WARN-маркеры

```bash
docker logs karaoke-app 2>&1 | grep -E 'WARN.*song\.(locked_save|lock_timeout)' | head -20
```

- [ ] Нет `song.locked_save_fallback` (признак удаления песни во время фоновой обработки — НЕ должно быть в smoke test).
- [ ] Нет `song.locked_save_failed`.
- [ ] Нет `song.lock_timeout`.

## Шаг 6 — Полный manual test

После успешного smoke test выполнить **полный manual test checklist**:
- [`contracts/manual-test-checklist.md`](./contracts/manual-test-checklist.md) — 5 шагов (компиляция + unit + end-to-end + SQL/логи/SSE + откат).

## Шаг 7 — Pre-commit + публикация PR

См. AGENTS.md «Перед каждым `git commit`»:
```bash
# 1. Линтеры
./gradlew ktlintCheck
cd webvue3       && npm run lint:check && cd ..
cd karaoke-public && npm run lint:check && cd ..

# 2. Покрытие документацией
bash tools/check-kdoc-coverage.sh
bash tools/check-jsdoc-coverage.sh webvue3
bash tools/check-jsdoc-coverage.sh karaoke-public

# 3. Prettier --check
cd webvue3       && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..
cd karaoke-public && npx prettier --check "src/**/*.{vue,js,ts,json}" && cd ..

# 4. Pre-commit
pre-commit run --all-files
```

Если всё OK:
```bash
git push -u origin 299-song-fields-overwrite-race-condition
gh pr create --base master
gh pr checks
gh pr merge --merge
```

(см. также AGENTS.md «CI-gate для master»).

## Шаг 8 — После merge на проде

1. **Мониторинг 24-48 часов**:
   - Лог `infra.prod.ping` (см. `docs/ops/log-correlation.md`) — нет ли WARN/ERROR.
   - Метрики lock-wait (`song.lock_timeout`) — должно быть < 1 / час (SC-007).
2. **Если WARN/ERROR превышают ожидания** — откат через `git revert` (см. `contracts/manual-test-checklist.md` шаг 5).
3. **OpenProject WP #49** — после успешного деплоя `tracker.sh mark-review 49` + `close-issue 49`.

## Done When

- [ ] Код реализован (`Song.saveToDbLocked`, `Song.loadFromDbByIdForUpdate`, `KaraokeProperties.songSaveLockedTimeoutMs`, 8 мест Pass 281, 25+ мест FR-020).
- [ ] Все 5 шагов manual test checklist выполнены с `pass`.
- [ ] Все 7 проверок pre-commit прошли (lint, kdoc, prettier, baseline).
- [ ] PR смержен в `master` через `gh pr merge --merge`.
- [ ] На проде 24-48 часов нет WARN/ERROR lock-wait > ожидаемого.
- [ ] OpenProject WP #49 закрыт.

## См. также

- [`spec.md`](./spec.md) — спецификация.
- [`plan.md`](./plan.md) — Implementation Plan.
- [`research.md`](./research.md) — Phase 0 research.
- [`data-model.md`](./data-model.md) — Phase 1 data-model.
- [`contracts/manual-test-checklist.md`](./contracts/manual-test-checklist.md) — manual test checklist.
- [`../../../AGENTS.md`](../../../AGENTS.md) — governance, lint gates, machine rules.
- [`../../../DEVELOPMENT.md`](../../../DEVELOPMENT.md) — архитектура + команды.
