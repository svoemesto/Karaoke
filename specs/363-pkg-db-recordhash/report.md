# OpenProject #363 — PKG-DB-RECORDHASH (волна C): дрейф recordhash-триггеров и sync-таблиц

- **Ветка**: `582-pkg-db-recordhash` (worktree `/home/nsa/Karaoke/.worktrees/waveC-recordhash`, от master `79b74b0a`)
- **Пакет**: `PKG-DB-RECORDHASH`, порции P-71 / P-72 / P-73 аудита #336
- **Записи**: BR-540 (S2), BR-541 (S2), BR-553 (S2), BR-533 (S2) — короткий цикл, ТЗ = карточки аудита
- **Дата**: 2026-10-24
- **Миграции БД не применялись** (запрет задачи): все SQL-файлы проверены статически, см. § 4.

---

## 1. Итог

| BR | Файл | Было | Стало |
|---|---|---|---|
| **BR-553** | `deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql:342–372` | в `update_tbl_songs_sync_recordhash()` последние **31 колонка** md5-цепочки без префикса `NEW.` (скопированы из `UPDATE`-выражения). `CREATE FUNCTION` проходит (тело plpgsql проверяется только синтаксически), а триггер падает на **первой** записи: `ERROR: column "rate" does not exist`. `tbl_songs_sync` — очередь sync-пайплайна LOCAL→SERVER ⇒ на прод не уезжает ни одна песня | префикс `NEW.` восстановлен во всех 95 колонках (эталон — `43_song_name_censored_sync.sql`); в миграцию добавлен `DO $$`-контроль: считает `COALESCE(` и `COALESCE(NEW.` в `pg_proc.prosrc` и падает при расхождении. Регресс-тест — `tools/check-recordhash-consistency.py` (check 1) |
| **BR-540** | `deploy/recordhash_processes.sql:19` | канонический файл ссылался на **`NEW.settings_id`** — колонка переименована в `song_id` миграцией 35, а 36 починила только функцию **в БД**. Файл назван в `knowledge` каноническим местом для этой функции ⇒ его применение ломало каждую запись `tbl_processes` (`record "new" has no field "settings_id"`): создание задания и смена статуса `WORKING→DONE`. В md5 не входили `process_envs`, `process_chain_id`, `process_deleted_at` (поля модели `KaraokeProcess`) | `NEW.song_id`; набор колонок доведён до **18** (все `@KaraokeDbTableField` модели); для трёх недостающих колонок — `ADD COLUMN IF NOT EXISTS` (иначе файл неприменим к БД без миграции 47); перед созданием функции — явная проверка наличия `song_id` (молча подставлять пустой id нельзя); бэкфилл повторяет формулу |
| **BR-541** | `deploy/recordhash_authors.sql:10–21` | в md5 не входили **7 колонок**: `description`, `short_description`, `warning` (31), `is_special_order` (27), `sort_order` (46), `ready_songs_count`, `total_songs_count` (44); при этом файл **требовал** `aliases`, которой нет в базовой схеме (появляется только после 18). Итог: либо падение на каждой записи автора, либо тихая потеря синхронизации — diff сравнивает строки только по `recordhash`, поэтому правки описания / предупреждения / «по спецзаказу» / порядка / счётчиков не уезжали на прод, а публичные «Закрома» показывали устаревшие данные, и автор мог не попасть в сетку (отбор по `ready_songs_count > 0 OR sort_order != 0`) | набор колонок **10 → 17**; все нужные колонки добавляются самим файлом (`ADD COLUMN IF NOT EXISTS` с определениями из 18/27/31/44/46) — файл применим к любой БД; бэкфилл повторяет формулу один-в-один. Порядок: новые колонки — только в конец цепочки |
| **BR-533** | `deploy/karaoke-db/31_entity_description_fields.sql:72–74` (фикс — новой миграцией) | `Song.getSqlToInsert(sync = true)` пишет **91** колонку в `tbl_songs_sync`, а пяти в таблице не было: `status_process_demo`, `id_tariff`, `description`, `short_description`, `warning`. Любой push песни в SYNC-таблицу падал на первой же песне батча (`column "status_process_demo" of relation "tbl_songs_sync" does not exist`) ⇒ ни одна песня не уезжала на прод | новая миграция `deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql`: пять колонок (определения как в `tbl_songs`), пересборка `update_tbl_songs_sync_recordhash()` (функция **95 → 99** колонок; `status_process_demo` — колонка есть, в md5 не входит, как в `tbl_songs`), `DO $$`-контроль `NEW.`, перенос значений из `tbl_songs` (ветка ленивая — только если там есть все пять колонок) и пересчёт `recordhash` существующих строк. Порядок применения: **50 → 52**, после 52 миграцию 50 не переприменять |

**Общий корень** (как и указано в карточках): канонические `deploy/recordhash_<table>.sql` жили отдельно от миграций и от модели. Миграция добавляла колонку в таблицу и в **свою** копию функции — канонический файл при этом отставал (BR-540/541), а следующая миграция, наоборот, копировала тело `UPDATE` в функцию без `NEW.` (BR-553). Обе аварии «немые»: функция создаётся без ошибок, падает только триггер.

---

## 2. Разбор по коду

### 2.1. Почему «голое» имя колонки — это падение, а не ошибка создания (BR-553)

В PL/pgSQL идентификатор, не найденный среди переменных функции, передаётся SQL-парсеру как ссылка на колонку. У триггерной функции нет `FROM`, поэтому `COALESCE(rate::TEXT, '')` не разрешается:

```
ERROR:  column "rate" does not exist
```

`CREATE OR REPLACE FUNCTION` при этом успешен: тело проверяется только синтаксически. Это и есть причина, по которой дефект дожил до аудита — «файл применился без ошибок».

### 2.2. Почему пропущенная колонка = тихая потеря синхронизации (BR-540/541)

Diff LOCAL↔SERVER (`KaraokeDbTable.getListHashes` → `to.recordhash != from.recordhash`) сравнивает строки **только по `recordhash`**. Поле, не вошедшее в md5, не считается расхождением: `getDiff` для него не вызывается, `UPDATE` на вторую БД не формируется.

### 2.3. Почему падал перенос песен (BR-533 = BR-553 по последствиям)

`Song.getSqlToInsert(sync = true)` формирует фиксированный список из 91 колонки и шлёт его зашифрованным SQL на `POST /changerecords`; прод-БД выполняет его как есть. Список строится из модели, а не из фактического набора колонок целевой таблицы — поэтому каждая новая колонка `tbl_songs` повторяет дефект, пока её не добавят в `tbl_songs_sync` отдельной миграцией.

### 2.4. Что изменено

```
M deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql   (NEW-префикс в 31 колонке + DO-контроль)
A deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql (5 колонок + функция 99 колонок + DO-контроль + бэкфилл)
M deploy/recordhash_processes.sql                        (song_id, +3 колонки, предусловия, бэкфилл)
M deploy/recordhash_authors.sql                          (10 → 17 колонок, ADD COLUMN IF NOT EXISTS, бэкфилл)
A tools/check-recordhash-consistency.py                  (guard: 3 проверки, без БД)
M tools/README.md, .pre-commit-config.yaml, .github/workflows/lint.yml (регистрация guard'а)
M karaoke-app/.../model/WebEvent.kt                      (useInHash = false — контракт стал машиночитаемым)
M karaoke-app/.../model/KaraokeDbTableField.kt           (KDoc: что читает guard)
M knowledge/system/infra/sql-migrations.md               (Решение 3: канонический файл, NEW., guard, 50 → 52)
M knowledge/domains/persistence/domain.md                (§ recordhash: 6 инвариантов формулы)
M docs/features/song-air-access.md, docs/features/zakroma-tiles-sort-order.md (FR-009)
A specs/363-pkg-db-recordhash/report.md                  (этот отчёт)
```

`specs/336-code-audit/registry.csv` **не тронут** (запрет задачи) — статусы четырёх записей переводит лид.

---

## 3. Регрессионный тест «до/после»

Тест — `tools/check-recordhash-consistency.py`: три проверки, полностью статические (БД не нужна, контейнер `karaoke-db` не используется). «До» — состояние `HEAD` базового коммита, собранное `git archive` (guard скопирован в дерево, потому что он новый):

### 3.1. «До» — все четыре дефекта

```
$ D=.guard-before && mkdir -p "$D" \
  && git archive HEAD deploy tools karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp | tar -x -C "$D" \
  && cp tools/check-recordhash-consistency.py "$D/tools/" \
  && python3 "$D/tools/check-recordhash-consistency.py" --root "$D"
FAIL deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql:273: [check 1] функция public.update_tbl_songs_sync_recordhash(): без префикса NEW. ссылки на колонки ['audio_compare_history', 'audio_delta_ms', 'audio_parent_id', 'audio_similarity_percent', 'exclusive', 'formatted_text_chords', 'formatted_text_song', 'formatted_text_tabs', 'free', 'free_after_on_air', 'id_dzen_demo', 'id_max_chords', 'id_max_demo', 'id_max_karaoke', 'id_max_lyrics', 'id_max_melody', 'id_telegram_demo', 'id_vk_demo', 'player_readiness_flags', 'rate', 'root_id', 'song_name_censored', 'song_type', 'version_dzen_demo', 'version_max_chords', 'version_max_demo', 'version_max_karaoke', 'version_max_lyrics', 'version_max_melody', 'version_telegram_demo', 'version_vk_demo'] — триггер упадёт на первой записи (ERROR: column "audio_compare_history" does not exist)
FAIL deploy/recordhash_authors.sql: [check 2] в md5 НЕ входят колонки модели Author.kt: ['description', 'is_special_order', 'ready_songs_count', 'short_description', 'sort_order', 'total_songs_count', 'warning'] — их изменения не попадут в diff LOCAL↔SERVER
FAIL deploy/recordhash_processes.sql: [check 2] в md5 НЕ входят колонки модели KaraokeProcess.kt: ['process_chain_id', 'process_deleted_at', 'process_envs', 'song_id'] — их изменения не попадут в diff LOCAL↔SERVER
FAIL deploy/recordhash_processes.sql: [check 2] в md5 входят колонки, которых нет в модели KaraokeProcess.kt и в списке extras: ['settings_id'] — устаревшее имя колонки или дрейф набора колонок
FAIL deploy/recordhash_events.sql: [check 2] в md5 НЕ входят колонки модели WebEvent.kt: ['last_update'] — их изменения не попадут в diff LOCAL↔SERVER
FAIL karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt → tbl_songs_sync: [check 3] INSERT пишет колонки, которых нет в tbl_songs_sync: ['status_process_demo', 'id_tariff', 'description', 'short_description', 'warning'] — любой push песни в sync упадёт на первой записи (нет миграции, добавляющей их)

6 нарушение(й) recordhash-контракта (BR-533/540/541/553).
exit=1
```

Соответствие записям: строка `50_…:273` → **BR-553** (31 колонка — ровно как в карточке); две строки `recordhash_authors` → **BR-541**; две строки `recordhash_processes` → **BR-540**; строка `Song.kt → tbl_songs_sync` → **BR-533**. Строка про `recordhash_events.sql` — побочный дрейф того же корня (см. § 5.3).

### 3.2. «После» — рабочее дерево

```
$ python3 tools/check-recordhash-consistency.py
OK recordhash-контракт: NEW-префикс в триггерах, канонические файлы == модели (+бэкфилл), tbl_songs_sync содержит все колонки INSERT.
exit=0

$ python3 tools/check-recordhash-consistency.py --quiet    # как его зовёт pre-commit
exit=0
```

### 3.3. Мутационная проверка guard'а (что он ловит по-настоящему)

```
(a) удалена одна колонка из функции recordhash_authors.sql:
FAIL deploy/recordhash_authors.sql: [check 2] в md5 НЕ входят колонки модели Author.kt: ['sort_order'] — их изменения не попадут в diff LOCAL↔SERVER
FAIL deploy/recordhash_authors.sql: [check 2] формула бэкфилла не совпадает с формулой функции (порядок/состав колонок) — существующие строки останутся со старым recordhash
exit=1

(b) удалён ALTER TABLE ... ADD COLUMN sort_order (класс BR-541 «aliases»):
FAIL deploy/recordhash_authors.sql: [check 2] md5 ссылается на колонки, которых нет в tbl_authors и которые не добавляет сам файл: ['sort_order'] — триггер упадёт при первой записи
exit=1
```

### 3.4. Что этим НЕ проверено (честно)

- **Сам SQL на живом PostgreSQL не исполнялся** — ни на LOCAL, ни на контейнере `karaoke-db` (запрет задачи), а `psql`/`postgres` на машине отсутствуют (`which psql postgres initdb` → пусто). Синтаксис проверен только структурно (сбалансированные `$$`/скобки, разбор выражений guard'ом), поэтому `DO $$`-блоки контроля, `ADD COLUMN`, `UPDATE`-бэкфилл и перенос значений из `tbl_songs` нужно прогнать на копии БД перед продом (runbook внутри каждой миграции).
- **Фактический эффект на прод-данные** (какие именно авторы/песни поедут после пересчёта хэшей) не измерялся: нет доступа к БД.

---

## 4. Проверки (до коммита, факты с диска)

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck
BUILD SUCCESSFUL in 44s

$ ... :karaoke-app:test
BUILD SUCCESSFUL in 1m 28s
tests=459 skipped=0 failures=0 errors=0 (56 suites)

$ ... :karaoke-web:test
BUILD SUCCESSFUL in 19s
tests=101 skipped=0 failures=0 errors=0 (18 suites)

$ bash tools/check-knowledge-structure.sh      → OK: knowledge/ structure valid (9/9 checks passed)
$ python3 tools/check-doc-references.py        → OK: ссылок 4139, путей 2281, эндпоинтов 313; битых нет
$ bash tools/check-subagent-isolation.sh       → ✅ все commits ветки уникальны
$ bash tools/check-no-legacy-tbl-settings.sh   → OK: 1624 файла, нарушений нет
$ python3 tools/lint-knowledge.py              → All documentation passes architectural style and structural checks.
$ python3 -c "import yaml; yaml.safe_load(...)" → yaml ok (lint.yml, .pre-commit-config.yaml)
```

`gtk`/`karaoke-app:test` один раз упал в Kotlin daemon (`Using fallback strategy: Compile without Kotlin daemon`) — пересборка прошла, тесты зелёные.

---

## 5. Совместимость со старыми значениями `recordhash`

1. **Пересчёт, а не миграция данных.** Во всех трёх файлах (50/52, authors, processes) в конце стоит `UPDATE … SET recordhash = md5(…)` с той же формулой, что в функции, — строки со старым хэшем пересчитываются на месте, `NULL` не остаётся, «вечных расхождений» не образуется. `WHERE id > 0` — унаследованный предохранитель, служебные строки не трогаются.
2. **Хэши меняются на обеих сторонах.** Формула authors/processes изменилась (10 → 17 и 15 → 18 колонок), поэтому канонический файл нужно применить на **LOCAL и PROD в одном окне обслуживания**. Если применить только на LOCAL, первый прогон sync покажет все строки изменёнными: направление authors — `LOCAL_TO_SERVER` (`AuthorsSyncTarget.oneClickDirection`), то есть это безопасный полный re-push, а не затирание локальных данных; после применения на PROD хэши сходятся.
3. **`useInHash = false` вместо «молчаливого» пропуска.** `WebEvent.last_update` авто-обновляется триггером в каждой БД и намеренно не входит в md5 (это было написано словами в комментарии). Теперь это объявлено в аннотации, и guard читает флаг — новый дрейф набора колонок не пройдёт молча.
4. **Файл authors применим к «старой» БД.** Раньше он требовал `aliases` и падал на БД без миграции 18; теперь все 17 колонок добавляются `IF NOT EXISTS` с определениями из исходных миграций (18/27/31/44/46) — для БД, где они уже есть, это no-op.
5. **Файл processes применим к «старой» БД** (без 35/47): явная проверка `song_id` с указанием нужной миграции вместо молчаливой подстановки пустого id; `process_envs` / `process_chain_id` / `process_deleted_at` добавляются `IF NOT EXISTS` с определениями из прод-схемы и миграции 47.
6. **`tbl_songs_sync` (52)**: значения пяти колонок переносятся из `tbl_songs` только если там есть все пять (ветка `DO` ленивая) — на БД без миграции 31 перенос просто не выполняется, а не падает. Хэш пересчитывается для всех строк, чтобы первый diff не показывал ложных расхождений.
7. **`tbl_processes` в `SyncRegistry` не входит** (комментарий в миграции 35), поэтому пересчёт его хэшей на межБД-синхронизацию не влияет; хэш читается только админкой (`KaraokeProcessAdminService`).

---

## 6. Knowledge (SSoT) и per-feature документ

- `knowledge/system/infra/sql-migrations.md`, Решение 3 — контракт канонических файлов, правило `NEW.`-префикса, guard, порядок «50 → 52».
- `knowledge/domains/persistence/domain.md`, § «`recordhash`-колонка» — шесть инвариантов формулы и чем каждый проверяется.
- `docs/features/song-air-access.md` — прецедент BR-553 и миграция 52 в разделе recordhash; инвариант про «голые» имена и полноту колонок INSERT; новая ловушка «переприменение 50 после 52».
- `docs/features/zakroma-tiles-sort-order.md` — `sort_order` в каноническом `recordhash_authors.sql` (BR-541) и инвариант «набор колонок = модель Author + счётчики».
- `tools/README.md`, `.pre-commit-config.yaml`, `.github/workflows/lint.yml` — guard зарегистрирован (CI + pre-commit, `always_run`).

---

## 7. Что осталось за владельцем

1. **Применить миграции (я не применял — запрет).** Порядок:
   - `deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql` (если ещё не применена) → затем
     `deploy/karaoke-db/52_tbl_songs_sync_description_fields.sql` на LOCAL и PROD;
   - `deploy/recordhash_authors.sql` на LOCAL и PROD **в одном окне** (формула md5 должна совпасть);
   - `deploy/recordhash_processes.sql` — на каждую БД, где есть `tbl_processes`.
   Runbook с проверками — в конце каждого файла. Перед продом — прогнать на копии БД: блоки `DO $$` не исполнялись ни разу (см. § 3.4).
2. **Решение по `album_id`** (новая находка, вне четырёх записей): `tbl_songs_sync` не имеет колонки `album_id`, которая есть в `tbl_songs` и входит в его `recordhash` (29_albums.sql:175). `INSERT` её не пишет, падения нет, но `UPDATE … SET album_id` на sync-таблице упадёт, а хэши двух таблиц расходятся ещё на одну колонку. Нужна отдельная миграция (и решение, едет ли альбом в sync вообще).
3. **`Song.getSqlToInsert`** по-прежнему строит список колонок из модели, а не из фактического набора колонок целевой таблицы (поправка BR-533, п. 1). Guard ловит расхождение на CI, но структурно дефект остаётся: следующая новая колонка `tbl_songs` потребует такой же миграции для `tbl_songs_sync`.
4. **Пункт 3 поправки BR-533 закрыт ранее**: `MainController.doChangeRecords` с спеки 554 не отдаёт текст ошибки БД как успех — логирует и возвращает 500 с нейтральным телом (`karaoke-web/.../MainController.kt`, `catch` в конце `handleChangeRecords`). Отдельной правки не потребовалось.
5. **Статусы в `specs/336-code-audit/registry.csv`** не менял (запрет задачи) — BR-533/540/541/553 остаются `TODO` до решения лида.
6. **Ветка не смержена, push/PR не делались** (запрет). Master ушёл вперёд (`d4109801`, PR #675) — rebase не делал.

---

## 8. Коммиты

```
cafc8a58 fix(deploy): вернуть префикс NEW. в триггере tbl_songs_sync (аудит #336, BR-553)
fe345fe7 fix(deploy): полный набор колонок в канонических recordhash-триггерах (аудит #336, BR-540/541)
41c3f05f feat(deploy): миграция 52 — недостающие колонки tbl_songs_sync (аудит #336, BR-533)
4b4fdc06 feat(tools): guard recordhash-контракта + CI/pre-commit (аудит #336, BR-533/540/541/553)
7c8ccf10 docs(knowledge): recordhash-контракт в SSoT и per-feature (аудит #336, #363)
```

`git diff --stat 79b74b0a..HEAD` → 14 файлов, +1305/−53.
