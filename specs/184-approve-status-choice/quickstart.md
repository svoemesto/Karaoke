# Quickstart: ручная валидация «выбор статуса при апруве (5/6)»

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Contracts](./contracts/)

В CI автотестов для admin-фичи нет (AGENTS.md «Тесты»). Проверка — ручная, на
admin-машине (см. `AGENTS.md` «Ограничения агента» — пересборка `karaoke-app`
**только на dev-pc/dev**). На других машинах пересобирает `karaoke-app`
только пользователь; агенту остаётся **код-правки + подсказка сценариев**.

> **Предусловие**: SPEC.md прошла validation 16/16 (см. [checklists/requirements.md](./checklists/requirements.md)).

---

## 0. Подготовка

```bash
# Убедиться, что ветка — наша (только в dev-pc/dev):
git branch --show-current
# Ожидаемо: 184-approve-status-choice

# Сборка backend (на dev-pc/dev):
./gradlew clean karaoke-app:bootJar --parallel
# (webvue3 собирается отдельно — см. шаг 5)

# Перезапуск контейнера (на dev-pc/dev — без согласия, на других машинах — пользователь):
cd deploy && bash do.sh build_app
cd /sm-karaoke/system/deploy && bash do.sh start_karaoke_app
```

---

## 1. Базовые сценарии (обязательные)

### Сценарий 1 — backward-compat: без `idStatus` = как раньше (SC-003)

```bash
# Берём id НЕ одобренного задания (статус = submitted) с idStatus песни < 6
ASSIGN_ID=$(curl -s -X POST http://localhost:8080/api/songeditor/submittedcount \
  -d "target=local" | jq .)
# Упрощённо — заранее знаем, что id нужного задания = N (подставить)
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "target=local"
# Ожидаемый ответ: {"ok":true,"status":"success","idStatus":6}
# (без поля idStatus в запросе — поведение полностью как раньше)
```

**Проверка БД**:
```sql
SELECT id_status FROM tbl_songs WHERE id = (SELECT song_id FROM tbl_song_assignments WHERE id = $N);
-- Ожидаем: 6
SELECT admin_status FROM tbl_song_assignments WHERE id = $N;
-- Ожидаем: 'approved'
```

**Проверка логов** (`docker logs karaoke-app 2>&1 | grep feature-184`):
- строка `idStatus=6 reason=default` присутствует;
- строки `render-demo SKIPPED` / `sync-related SKIPPED` **отсутствуют** (для 6
  они не выводятся, но защитный skip тоже логируется как `render-demo-helper` —
  см. `SongEditorController.kt:997`).

---

### Сценарий 2 — выбор 5: апрув без рендера/sync (US1 acceptance #2)

```bash
# То же задание, но с idStatus=5:
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "idStatus=5" -d "target=local"
# Ожидаемый ответ: {"ok":true,"status":"success","idStatus":5}
```

**Проверка БД**:
```sql
SELECT id_status FROM tbl_songs WHERE id = <songId>;
-- Ожидаем: 5
SELECT admin_status FROM tbl_song_assignments WHERE id = $N;
-- Ожидаем: 'approved'
```

**Проверка логов**:
- `idStatus=5 reason=manual_choice` — присутствует;
- `render-demo SKIPPED for songId=NN reason=idStatus=5` — присутствует;
- `sync-related SKIPPED for songId=NN reason=idStatus=5` — присутствует;
- `news SKIPPED for songId=NN reason=idStatus=5` — присутствует;
- `[approve/timing] push на SERVER: ...` — **присутствует** (push песни не гейтится, research D-3);
- процесс `RENDER_MP4_DEMO` в `tbl_processes` для этой песни — **отсутствует**
  (или есть завершённый ранее, но без новой записи с `created_at` > timestamp сценария).

**Проверка новостей**:
```sql
SELECT * FROM tbl_news WHERE song_id = <songId>;
-- Ожидаем: новость НЕ создана (markNewsAvailableIfReady не сработал: idStatus=5, а не 6)
```

---

### Сценарий 3 — UI: radio-group для песни в idStatus<5 (US2 acceptance #3)

1. Открыть `webvue3` → «Задания редактора» → строка задания с песней
   `id_status = 4` (найти через фильтр по автору).
2. Кликнуть на строку → открывается `ReviewModal`.
3. В блоке `.se-meta` (под заголовком) ищите новое поле `idStatus песни: 4`.
4. Над кнопками `Одобрить`/`Отклонить`/`Отозвать` появился radio-group
   `Финальный статус песни:` с двумя опциями, по умолчанию выбрана «6 — Готово».
5. ~~Серого баннера-подсказки над radio нет~~ (FR-010 [REMOVED], Pass 51-3.2).

**Проверка**:
- [ ] radio видим
- [ ] дефолт = 6
- [ ] при выборе 5 и клике «Одобрить» — сообщение в модалке
      `Одобрено в статусе 5` (FR-009)
- [ ] после закрытия модалки и `Ctrl+R` (повторное открытие) —
      в `.se-meta` остаётся `idStatus: 5` (информационный бейдж, radio ВСЕГДА виден — Pass 51-3.1)

---

### Сценарий 4 — UI: radio ВСЕГДА виден, когда статус песни известен (US2.1, Pass 51-3.1)

1. Открыть задание в `ReviewModal` для песни с **любым известным `idStatus`** (0..6).
2. В блоке `.se-meta` — информационный бейдж «idStatus: N (...)» с текущим значением.
3. **radio-group ВИДЕН ВСЕГДА** (Pass 51-3.1: больше нет гейта `< 5`), обе опции доступны.
4. **НЕ должно быть** read-only блока «Текущий статус песни: ... Для перевода используйте SongEdit» — он удалён в Pass 51-3.1.

**Проверка**:
- [ ] radio виден для `idStatus = 0`, `4`, `5`, `6` (все варианты)
- [ ] выбор 5 + одобрить для песни в 6 → бэкенд логирует `idStatus downgrade IGNORED ...`, статус остаётся 6 (FR-012)
- [ ] выбор 6 + одобрить для песни в 6 → no-op (уже 6, idempotent)
- [ ] `.se-meta` бейдж показывает текущее значение, не меняется после одобрения

---

### Сценарий 5 — повторный апрув уже одобренного задания (US1 acceptance #5)

```bash
# После сценария 1 (id=$N уже approved):
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "target=local"
# Ожидаемый ответ: {"ok":true,"status":"already_approved"}

# Проверка: idStatus НЕ изменился
curl -s -X POST http://localhost:8080/api/songeditor/byId \
  -d "id=$N" -d "target=local" | jq .idStatus
# Ожидаем: 6 (или 5, если сценарий 2 был последним)
```

**Проверка логов**:
- [ ] новая строка `[approve/feature-184]` НЕ появилась (только `status=already_approved`
      от старой логики specs/094).

---

### Сценарий 6 — downgrade-ignore: запрос 5 при песне в 6 (Edge Cases)

```bash
# Найти/создать задание, где ПЕСНЯ уже в id_status=6 (например, предыдущим сценарием 1),
# а admin_status = 'in_progress' (например, новое задание, а не одобренное).
# Прямым curl: песня в 6 → в сценарии 1 она перешла в 6. Создадим ситуацию:
#   - сначала одобрим (id=$N, no idStatus) → песня в 6, задание approved;
#   - руками создадим новое задание через /assign на ту же песню (status=in_progress);
#   - отправим на ревью и попробуем одобрить с idStatus=5.
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$NEW_N" -d "idStatus=5" -d "target=local"
# Ожидаемый ответ: {"ok":true,"status":"success","idStatus":6}  ← фактический = 6, не 5
```

**Проверка логов**:
- [ ] `[approve/feature-184] idStatus downgrade IGNORED songId=NN current=6 requested=5` —
      присутствует;
- [ ] render-demo и sync-related НЕ пропускаются (статус фактически 6).

**Проверка БД**:
- [ ] `tbl_songs.id_status` остался `6` (не понизился, INV-1).

---

### Сценарий 7 — невалидный idStatus (FR-001 / Edge Cases)

```bash
# Любое значение, не 5 и не 6:
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "idStatus=4" -d "target=local"
# Ожидаемый HTTP-код: 400
# Тело: {"ok":false,"status":"error","error":"invalid_idstatus: must be 5 or 6"}

# Граничные значения:
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "idStatus=0" -d "target=local"
# Ожидаемый: 400 invalid_idstatus

curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "idStatus=abc" -d "target=local"
# Ожидаемый: 400 (Spring парсит как null → ловим по нашей проверке)

curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "idStatus=-1" -d "target=local"
# Ожидаемый: 400 invalid_idstatus
```

**Проверка БД**: `tbl_song_assignments.admin_status` НЕ стал `approved`
(задание осталось в `submitted`/`in_progress`).

---

### Сценарий 8 — `/byId` содержит `idStatus` (FR-011, INV-B1)

```bash
# После сценария 1 (id_status=6) или 2 (id_status=5):
curl -s -X POST http://localhost:8080/api/songeditor/byId \
  -d "id=$N" -d "target=local" | jq .idStatus
# Ожидаем: 6 (или 5 — в зависимости от предыдущего сценария)

# Для target=remote — то же самое, но читается из REMOTE-БД:
curl -s -X POST http://localhost:8080/api/songeditor/byId \
  -d "id=$N" -d "target=remote" | jq .idStatus
# Ожидаем: значение из REMOTE-БД (после синка должно совпадать с LOCAL)
```

**Проверка через прямой SQL**:
```sql
SELECT id_status FROM tbl_songs WHERE id = <songId>;
-- Должно совпадать с .idStatus из ответа /byId
```

---

### Сценарий 9 — remote-target: апрув в 5 (FR-004 + target)

```bash
# Задание читается из REMOTE-БД (target=remote), но применение разметки
# идёт в LOCAL (как и раньше — см. комментарий на SongEditorController.approve:312-314):
curl -s -X POST http://localhost:8080/api/songeditor/approve \
  -d "id=$N" -d "idStatus=5" -d "target=remote"
# Ожидаемый ответ: {"ok":true,"status":"success","idStatus":5}
```

**Проверка**:
- [ ] в LOCAL `tbl_songs.id_status` = 5 (где живёт разметка);
- [ ] admin_status задания обновился в REMOTE-БД;
- [ ] `updateRemoteSongFromLocalDatabase` НЕ сработал автоматически (это
      отдельный ручной `doUpdateRemoteSettingFromLocalDatabase`, см.
      спеку 022). Если он был запущен — статус на REMOTE станет 5, и
      публичный плеер песню не покажет (idStatus<6).

---

### Сценарий 10 — UI из 3 точек входа (Key Entities: `ReviewModal` общий)

1. **«Задания редактора»** (`SongEditorTable` → `ReviewModal`):
   пройти сценарий 3.
2. **Таблица песен** (`SongsTable` → `ReviewModal`):
   открыть ту же песню через «Песни» → фильтр по автору → контекстное
   «Review» на строке с активным заданием.
3. **Карточка песни** (`SongEdit` → `ReviewModal`):
   открыть `SongEdit` для той же песни → кнопка/ссылка «Review» (или
   «Open in Review»).
4. Во всех трёх местах — **тот же** radio-group с теми же значениями.
   Никаких правок в вызывающих компонентах не было — это проверка того,
   что `ReviewModal` действительно общий.

---

## 2. Линт- и coverage-проверки (обязательны перед PR)

```bash
# Backend
./gradlew ktlintCheck
./gradlew :karaoke-app:compileKotlin
bash tools/check-kdoc-coverage.sh

# Frontend
cd webvue3 && npm run lint:check
cd webvue3 && npm run build
bash tools/check-jsdoc-coverage.sh webvue3
```

Все должны быть зелёными (или baseline не увеличился).

## 3. Что не покрыто (для следующих фич)

- Автоматические UI-тесты radio-group (нет инфраструктуры Vue-тестов в проекте —
  см. AGENTS.md «Тесты»).
- Аудит-история выбора статуса (отдельная колонка `approved_id_status` +
  миграция + пересоздание `recordhash`-триггера). Вне scope.
- Массовый «одобрить все в 5» (UI-batch в `SongsTable`) — вне scope.
