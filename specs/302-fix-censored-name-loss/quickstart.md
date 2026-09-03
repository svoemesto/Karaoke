# Quickstart: Verification Guide для spec 302

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md) | **Data Model**: [data-model.md](data-model.md) | **Plan**: [plan.md](plan.md)

## Цель

Пошаговая инструкция для **ручной верификации** SC-001..SC-011 после
завершения implementation. Каждый шаг соответствует одному или
нескольким Success Criteria.

## Prerequisites

- Рабочая машина с Docker + docker-compose.
- Karaoke-проект склонирован, `karaoke-app` пересобран после merge
  feature-ветки `302-fix-censored-name-loss`.
- LOCAL-БД запущена (контейнеры Karaoke через `deploy/do.sh build_start_*`).
- OpenProject запущен (issue #52 существует).
- `.env.local-tracker` с валидным `TRACKER_API_TOKEN`.

## Setup (Pre-Verification)

### 1. Проверить наличие файлов

```bash
cd /home/nsa/Karaoke && git pull
git branch --show-current  # должен быть master (после merge)
ls -la specs/302-fix-censored-name-loss/
# Ожидаем: plan.md, spec.md, research.md, data-model.md, quickstart.md, contracts/, checklists/

ls -la tools/check-*coverage* tools/endpoint-pairs.yml tools/cleanup-test-songs.sql
# Ожидаем: 5+ файлов из FR-005/006/007/008/NFR-006

ls karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongUpdateMapper.kt
# Ожидаем: файл существует
```

### 2. Проверить рефактор `songs2Update`

```bash
grep -c "@RequestParam" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt
# Ожидаем: ~0 (после рефактора — 1 Map<String, String> all)

grep -A1 "fun songs2Update" karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt | head -3
# Ожидаем: @RequestParam all: Map<String, String>
```

### 3. Пересобрать и перезапустить `karaoke-app`

```bash
cd /home/nsa/Karaoke && ./gradlew clean karaoke-app:bootJar --parallel
# (на nsa-i9 — разрешено без согласия, см. AGENTS.md Pass 282)

cd /home/nsa/Karaoke/deploy && bash do.sh build_start_karaoke-app
# (на nsa-i9 — разрешено перезапускать без согласия)
```

### 4. Подготовить тестовые данные (NFR-006)

Перед SC-001 нужно зафиксировать исходные значения 10 песен
для последующего cleanup.

```bash
# Создать список 10 случайных песен (или взять первые 10)
PGPASSWORD=$(grep WORKING_DATABASE_PASS .env.local 2>/dev/null | cut -d= -f2 || \
             grep KARAOKE_DB_PASSWORD deploy/.env 2>/dev/null | cut -d= -f2) \
  psql -h localhost -U karaoke -d karaoke -c \
  "SELECT id, song_name, song_name_censored FROM tbl_songs ORDER BY id LIMIT 10;" \
  > .report-tracker-52-cleanup-originals.txt

cat .report-tracker-52-cleanup-originals.txt
# Ожидаем: 10 строк (id, song_name, song_name_censored)
```

Сохранить этот файл в `.report-tracker-52.md` (секция «Cleanup»,
см. NFR-005) **до** начала теста.

## Verification — Success Criteria

### SC-001: 10 ручных правок `song_name_censored` сохраняются в БД

**Setup**: открыть браузер → `http://localhost:8897/` → Songs → SongEdit.

**Procedure** (для каждой из 10 песен):

1. Открыть песню в SongEdit.
2. Стереть поле «Censored», ввести «Тест-NN» (где NN = 01..10).
3. Дождаться тоста «Изменения сохранены» (≤2 сек).
4. Обновить страницу (F5).
5. Убедиться, что поле «Censored» всё ещё содержит «Тест-NN».
6. Проверить через БД:

```bash
PGPASSWORD=... psql -h localhost -U karaoke -d karaoke -c \
  "SELECT id, song_name_censored FROM tbl_songs WHERE id = <ID>;"
# Ожидаем: song_name_censored = 'Тест-NN'
```

7. Проверить публичный API:

```bash
curl -s "http://localhost:8897/api/public/song?id=<ID>" | jq '.songNameCensored'
# Ожидаем: "Тест-NN"
```

**Pass criteria**: 10/10 песен прошли проверку.

### SC-002: Latency ≤2 секунды

**Procedure**:

1. В SongEdit ввести значение в поле «Censored».
2. Засечь время от ввода до появления тоста «Изменения сохранения».
3. Повторить 5 раз, вычислить среднее.

**Pass criteria**: среднее ≤2 сек.

### SC-003: Аудит 95 полей SongEdit — 0 потерянных

```bash
bash tools/check-songedit-field-coverage.sh
# Ожидаемый вывод:
# OK: 95/95 полей покрыты (или 95 - whitelist_size)
# exit 0
```

Если есть ошибки:
```bash
# MISSING: <field>
# Проверить, что <field> есть в whitelist (если non-standard setter)
# Или добавить @RequestParam <field> в songs2Update
```

### SC-004: Чек ≤1 секунды

```bash
time bash tools/check-songedit-field-coverage.sh
# Ожидаем: real ≤1s
```

### SC-005: CI блокирует PR без `@RequestParam`

Этот SC проверяется на стороне CI, не локально. Для локальной
проверки — добавить в SongEdit пробное поле `v-model="song.testProbe"`,
запустить чек:

```bash
# Временно добавить в SongEdit.vue: <input v-model="song.testProbe" />
bash tools/check-songedit-field-coverage.sh
# Ожидаем: MISSING: testProbe, exit 1

# Убрать пробное поле, запустить снова — должен выдать OK
```

### SC-006: Аудит-чек для всех пар ≤5 секунд

```bash
time bash tools/check-endpoint-field-coverage.sh
# Ожидаем: real ≤5s, exit 0
```

Вывод должен включать строки вида:
```
[PASS] SongEdit.vue ↔ /api/song/update (95/95)
[INFO] Только одна пара покрыта (MVP scope)
```

### SC-007: Документация `docs/features/song-edit-and-censored.md` создана

```bash
ls -la docs/features/song-edit-and-censored.md
# Ожидаем: файл существует, ≥50 строк, содержит секции:
# - Контракт UI↔backend
# - Ссылка на tools/check-songedit-field-coverage.sh
# - Краткое описание фикса songNameCensored
```

### SC-008: Никаких регрессий в сценариях specs/277

**Sub-test 1**: CustomFunction реckan работает.

```bash
# Запустить CustomFunction «Пересканировать цензурированные названия песен»
# Дождаться завершения, проверить тост «Обработано N песен за M секунд»
# SELECT COUNT(*) FROM tbl_songs WHERE song_name_censored = '';
# Ожидаем: значение, близкое к 0 (или к числу песен с пустым song_name)
```

**Sub-test 2**: Шаблоны VK/Telegram/News используют `songNameCensored`.

```bash
# Открыть песню, проверить превью VK-поста, Telegram-поста, News-поста
# Все три должны содержать значение из songNameCensored, не из songName
```

**Sub-test 3**: Публичный API возвращает `songNameCensored`.

```bash
curl -s "http://localhost:8897/api/public/song?id=42" | jq '.songNameCensored'
# Ожидаем: непустое значение (или пустая строка, если песня без цензуры)
```

### SC-009: Рефактор — поведение 1:1

**Integration-test procedure** (требует unit-test или manual golden-requests):

```bash
# Сгенерировать golden-request со ВСЕМИ 95 параметрами
# Запустить ДО merge: SELECT * FROM tbl_songs WHERE id = 42 → snapshot_before.json
# Запустить golden-request с полным набором параметров
# Запустить ПОСЛЕ merge: SELECT * FROM tbl_songs WHERE id = 42 → snapshot_after.json
# diff snapshot_before.json snapshot_after.json
# Ожидаем: 0 различий (или только разрешённые, например albumId если менялся)
```

Это требует написания интеграционного теста. Out of scope MVP
ручной проверки, но обязательно для CI в будущем.

### SC-010: Обратная совместимость

**Procedure**:

```bash
# Найти существующий скрипт, использующий /api/song/update
grep -r "/api/song/update" deploy/ tools/ 2>/dev/null | head -5

# Запустить его (например, scripts/update_song_metadata.sh)
bash <found_script>
# Ожидаем: exit 0, HTTP 200, корректный SongUpdateResultDto
```

Если таких скриптов нет — это нормально, можно пропустить SC.

### SC-011: OpenProject issue #52 в статусе `In review`

```bash
cd /home/nsa/Karaoke && source .env.local-tracker
bash tools/tracker.sh get-issue 52 | jq '.status, .updatedAt'
# Ожидаем: status="In review", updatedAt > время начала работы

bash tools/tracker.sh get-issue 52 | jq '.activities[-1].comment.raw' | head -50
# Ожидаем: markdown-отчёт со секциями "Что сделано", "Изменённые файлы",
# "Прогон проверок", "Известные ограничения"
```

## Cleanup (NFR-006)

После успешной верификации SC-001 (10 ручных правок) выполнить
cleanup:

```bash
# Способ 1: через .report-tracker-52-cleanup-originals.txt + UPDATE
# (требует парсинга txt-файла в SQL — выполняется вручную или через psql)
PGPASSWORD=... psql -h localhost -U karaoke -d karaoke <<EOF
UPDATE tbl_songs SET song_name_censored = '<original_value>' WHERE id = <ID>;
-- ... повторить для всех 10 песен
EOF

# Способ 2: через tools/cleanup-test-songs.sql (если реализован)
bash tools/check-endpoint-field-coverage.sh  # sanity check после cleanup
PGPASSWORD=... psql -h localhost -U karaoke -d karaoke \
  -f tools/cleanup-test-songs.sql
```

**Pass criteria**:
```bash
PGPASSWORD=... psql -h localhost -U karaoke -d karaoke -c \
  "SELECT id, song_name_censored FROM tbl_songs ORDER BY id LIMIT 10;"
# Ожидаем: те же значения, что в .report-tracker-52-cleanup-originals.txt
```

## Reporting (NFR-005)

После всех проверок SC-001..SC-011:

```bash
cd /home/nsa/Karaoke && source .env.local-tracker
bash tools/tracker.sh add-comment 52 --file .report-tracker-52.md
bash tools/tracker.sh mark-review 52
# НЕ делать close-issue — это делает пользователь после одобрения
```

**Структура `.report-tracker-52.md`** (по NFR-005 + FR-008 specs/295):

```markdown
# Отчёт по задаче #52: Не сохраняется цензурированное имя

## Что сделано
- Рефактор songs2Update (FR-011): 95 @RequestParam → 1 Map<String, String> all
- SongUpdateMapper.apply: централизованный маппер с сохранением special-case логики
- Чек-скрипты: tools/check-songedit-field-coverage.sh, tools/check-endpoint-field-coverage.sh
- Whitelist yml-файлы с предзаполненными исключениями
- Endpoint-pairs.yml (MVP: SongEdit ↔ /api/song/update)
- Pre-commit + CI интеграция (FR-006)
- Документация docs/features/song-edit-and-censored.md (FR-009)
- Обновлён specs/277-song-name-censored/spec.md US-2 (FR-010)

## Изменённые файлы
- karaoke-app/src/main/kotlin/.../controllers/ApiController.kt (рефактор)
- karaoke-app/src/main/kotlin/.../controllers/SongUpdateMapper.kt (new)
- tools/check-songedit-field-coverage.sh (new)
- tools/check-songedit-field-coverage.whitelist.yml (new)
- tools/check-endpoint-field-coverage.sh (new)
- tools/check-endpoint-field-coverage.whitelist.yml (new)
- tools/endpoint-pairs.yml (new)
- tools/cleanup-test-songs.sql (new, для NFR-006)
- docs/features/song-edit-and-censored.md (new)
- specs/277-song-name-censored/spec.md (updated)
- specs/302-fix-censored-name-loss/{spec,plan,research,data-model,quickstart}.md
- .pre-commit-config.yaml (2 hooks added)
- .github/workflows/lint.yml (1 job added)

## Прогон проверок
- SC-001: PASS (10/10 ручных правок сохранены)
- SC-002: PASS (latency = X.XX сек)
- SC-003: PASS (95/95 полей покрыты)
- SC-004: PASS (чек за X.XX сек)
- SC-005: PASS (CI конфиг обновлён)
- SC-006: PASS (аудит-чек за X.XX сек, exit 0)
- SC-007: PASS (docs/features/song-edit-and-censored.md существует)
- SC-008: PASS (no regressions)
- SC-009: PASS (integration-тест 1:1)
- SC-010: PASS (golden-requests совместимы)
- SC-011: PASS (issue в In review)

## Cleanup
10 тестовых песен возвращены в исходное состояние:
см. .report-tracker-52-cleanup-originals.txt (исходные значения)
и tools/cleanup-test-songs.sql (UPDATE скрипт).

## Известные ограничения
- Whitelist содержит 18 полей (включая все не-String типы + special-case).
  Если в будущем вырастет >25 — нужен редизайн (AST-анализ).
- Endpoint-pairs.yml содержит только одну пару (SongEdit ↔ /api/song/update).
  Расширение на Album/Author/SiteUser/Dictionary — следующий раунд.
```

## Rollback Plan (если что-то пошло не так)

1. **Revert merge**: `git revert -m 1 <merge-commit>` + push.
2. **Пересобрать и перезапустить** `karaoke-app` (старая версия).
3. **Проверить**: ручное сохранение через SongEdit работает
   (баг #52 вернётся, но всё остальное не сломается).
4. **Сообщить пользователю**: что нужно переоткрыть issue #52.

## Done When

- [ ] Setup выполнен (все 4 шага).
- [ ] SC-001..SC-011 — PASS (или явно SKIPPED с обоснованием).
- [ ] Cleanup выполнен (10 песен в исходном состоянии).
- [ ] `.report-tracker-52.md` сформирован и опубликован.
- [ ] `mark-review 52` выполнен, `close-issue 52` — НЕ выполнен
      (это делает пользователь).
