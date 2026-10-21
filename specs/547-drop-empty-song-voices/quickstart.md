# Quickstart: проверка фичи «Пустые голоса удаляются при сохранении»

**Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md) | **Контракты**: [contracts/](contracts/)

Пошаговая проверка после реализации. Все шаги — на машине `nsa-i9` (admin),
локальная БД в контейнере `karaoke-db`.

## 0. Предусловия

```bash
cd /home/nsa/Karaoke
git branch --show-current          # 547-drop-empty-song-voices
docker ps --format '{{.Names}}' | grep -c karaoke-db   # 1
```

- Ветка — `547-drop-empty-song-voices`.
- `GRADLE_USER_HOME` — `/home/nsa/Karaoke/.gradle` (обязательно, иначе read-only FS).

## 1. Юнит-тесты чистой функции (основная проверка правила)

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test \
  --tests 'com.svoemesto.karaokeapp.model.VoiceNormalizationTest'
```

**Ожидание**: BUILD SUCCESSFUL, все кейсы из таблицы
[contracts/voice-normalization.md](contracts/voice-normalization.md#4-примеры-обязательные-тест-кейсы)
(12+) зелёные.

## 2. Компиляция и линтеры

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew \
  :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:ktlintCheck :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && npm run format:check && cd ..
```

**Ожидание**: без ошибок; новых нарушений ktlint/ESLint нет.

## 3. Снимок данных «до» (песня 15696)

```bash
docker exec karaoke-db psql -U postgres -d karaoke -tAc \
 "select id, jsonb_array_length(source_markers::jsonb) as voices,
         (select string_agg(jsonb_array_length(value)::text, ',')
            from jsonb_array_elements(source_markers::jsonb)) as marker_counts
    from tbl_songs where id = 15696;"
```

**Ожидание сейчас**: `15696 | 2 | 621,0`.

Вспомогательный запрос — все песни с пустым не-первым голосом:

```bash
docker exec karaoke-db psql -U postgres -d karaoke -tAc "
with p as (select id, source_markers::jsonb m from tbl_songs where left(source_markers,1)='[')
select id from p
 where jsonb_array_length(m) > 1
   and exists (select 1 from generate_series(1, jsonb_array_length(m)-1) i
                where jsonb_array_length(m->i) = 0);"
```

**Ожидание сейчас**: 2278, 3182, 3197, 15696, 19905, 22041 (из них строго пустой —
только 15696; у остальных во втором голосе есть текст).

## 4. Проверка через API (нужен пересобранный `karaoke-app`)

Пересборка и перезапуск `karaoke-app` на `nsa-i9` — **только с согласия владельца**
и только после остановки очереди (AGENTS.md, Pass 380/381):

```bash
# 1) нет активных заданий?
curl -s -X POST http://localhost:7906/api/processesdigests \
  -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import json,sys; ps=json.load(sys.stdin)['processesDigests']; \
    print([(p['id'], p.get('name','')[:40]) for p in ps if p.get('status') in ('WORKING','CREATING')])"
# 2) остановить очередь (MainController без префикса /api)
curl -s http://localhost:8898/process/stop
# 3) дождаться пустого списка из шага 1, затем:
cd deploy && DOCKER_CONFIG=/home/nsa/Karaoke/.docker bash do.sh start_app
# 4) после проверки вернуть очередь, если была включена:
curl -s http://localhost:8898/process/start
```

Проверка сценария US1/US2 на копии данных:

```bash
# сохранить пустой второй голос (как «Добавить» в редакторе)
curl -s -X POST 'http://localhost:7906/api/song/savesourcetextmarkers' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'id=15696' --data-urlencode 'voice=1' \
  --data-urlencode 'sourceText=' --data-urlencode 'sourceMarkers=[]' \
  --data-urlencode 'indexTabsVariant=0'
```

**Ожидание**: `{"ok":true,"voice":-1,"countVoices":1}`.

Снимок «после» (повторить запрос из шага 3):

**Ожидание**: `15696 | 1 | 621`; `countVoices` в карточке песни — 1; текст первого
голоса не изменился (сравнить `md5(source_text)` до/после — совпадает, т.к. первый
элемент не меняется).

## 5. Проверка сценария US3 (пустой в середине)

На тестовой копии песни (не на проде): выставить
`source_markers = [[<M>], [], [<M2>]]`, `source_text = ["A","","B"]`, затем сохранить
любое поле через `POST /api/song/update` и проверить:

- `jsonb_array_length(source_markers) = 2`;
- второй голос по содержимому равен бывшему третьему (`M2`);
- `/api/song/voices` возвращает 2 голоса, маркеры второго — `M2`.

## 6. Проверка UI (US1, US4)

```bash
cd webvue3 && npm run dev     # разрешено конституцией (build/dev)
```

1. Открыть карточку любой песни с 2+ голосами → «Разметка».
2. Выбрать «Добавить» (голос N+1), ничего не вводить, нажать Save.
3. **Ожидание**: редактор переключается на последний реальный голос, список голосов
   снова содержит N пунктов, в карточке `countVoices = N`.
4. Повторить Save дважды — число голосов не растёт, правки не дублируются (US4).

## 7. Регрессия: аудио-потомки и синхронизация

На песне с `audio_parent_id` (потомок) и на песне с потомками (родитель, статус ≥ 5):

1. Сохранить карточку песни (без правок разметки).
2. **Ожидание**: в очереди появляется не более одной задачи синхронизации потомков,
   она завершается; повторных постановок в цикле нет (research R7).
3. Проверить, что у потомка разметка не опустела (`source_markers` не `[[]]`).

## 8. Идемпотентность (SC-004)

Сохранить песню два раза подряд и сравнить `last_update`/`source_markers`/`source_text`:

**Ожидание**: данные голосов не меняются; второго UPDATE по голосам нет
(`recordhash`/`source_markers` стабильны).

## 9. Документация и SSOT

```bash
python3 tools/check-ssot-impact.py
python3 tools/lint-knowledge.py
python3 tools/check-spec-issue-link.py specs/547-drop-empty-song-voices
```

**Ожидание**: SSOT-гейт зелёный — изменены `knowledge/domains/catalog/components/
dictionaries.md` и `song-entity.md`; knowledge-линтер без новых нарушений;
спека принимается (Issue ID = none).

## 10. Замер латентности сохранения (SC-007)

Цель: время сохранения голоса не растёт более чем на 10 % относительно текущего поведения.

**Базовая линия (до правок, на текущем контейнере)** — 10 замеров сохранения карточки:

```bash
for i in $(seq 10); do
  curl -s -o /dev/null -w '%{time_total}\n' -X POST \
    'http://localhost:7906/api/song/update' \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'id=15696' --data-urlencode 'songTone=Am'
done | sort -n | awk '{a[NR]=$1} END {printf "median=%.4f p90=%.4f\n", a[int(NR/2)+1], a[int(NR*0.9)]}'
```

**После правок** — тот же прогон на пересобранном контейнере; дополнительно сохранить голос
(`/api/song/savesourcetextmarkers`, тот же цикл) и сравнить медианы.

**Ожидание**: медиана и p90 после правок ≤ 1.10 × базовой линии. Записать оба числа в
`docs/features/drop-empty-song-voices.md` § «Латентность».

## 11. Выборка и сравнение голосов (SC-002, SC-005)

Фиксированная выборка: 15696 + первые 19 песен с 2+ голосами по возрастанию `id`
(всего 20). Снимок «до» — в файл, чтобы сравнение было машинным, а не «на глаз»:

```bash
docker exec karaoke-db psql -U postgres -d karaoke -tAc "
select id, source_text, source_markers from tbl_songs
 where id in (15696, 2278, 3182, 3197, 19905, 22041)
 order by id;" > /tmp/voices_before.tsv
# затем: сохранить каждую песню через API (карточка или голос) и снять снимок «после»
docker exec karaoke-db psql -U postgres -d karaoke -tAc "
select id, source_text, source_markers from tbl_songs
 where id in (15696, 2278, 3182, 3197, 19905, 22041)
 order by id;" > /tmp/voices_after.tsv
```

**Ожидание**:

- у каждой песни число голосов ≤ числа «до», пустых голосов кроме первого — 0 (SC-002);
- у каждой песни `jsonb_array_length(source_text) = jsonb_array_length(source_markers)`;
- непустые голоса совпадают с исходными по тексту и маркерам (SC-005): сравнить
  нормализованные `jsonb`-фрагменты, а не всю строку (порядок ключей маркеров может отличаться).

## 12. Регрессии сохранения песни (SC-008)

Прогнать четыре существующих сценария — по одному прогону каждый:

| Спека | Что проверяем | Шаг |
|---|---|---|
| `544-song-artifact-cleanup` | сохранение/апрув не создаёт `.srt`-файлов; разметка живёт в БД | сохранить голос → в `done_files/` нового `.srt` не появилось |
| `546-lyrics-search-preserve-song-fields` | подстановка найденного текста не затирает поля песни | сценарий US1 спеки 546 (импорт папки + правка названия/тональности во время поиска) |
| `299-song-fields-overwrite-race-condition` | `saveToDbLocked` не теряет параллельные правки | сохранить голос и параллельно `POST /api/song/update` (тональность) → обе правки в БД |
| `357-folder-import-overwrite` | импорт из папки не перезатирает ручные правки | импорт папки при открытой карточке песни с правкой |

**Ожидание**: 4/4 зелёные; результат записать в `docs/features/drop-empty-song-voices.md`
§ «Регрессии».

## 13. Задания и черновики не меняются (FR-015)

```bash
docker exec karaoke-db psql -U postgres -d karaoke -tAc "
select id, song_id, voice, status from tbl_song_assignments where song_id = 15696 order by id;" > /tmp/assignments_before.tsv
docker exec karaoke-db psql -U postgres -d karaoke -tAc "
select assignment_id, edited_markers, edited_texts from tbl_song_assignment_drafts
 where assignment_id in (select id from tbl_song_assignments where song_id = 15696) order by assignment_id;" > /tmp/drafts_before.tsv
```

Сохранить песню (любой путь), повторить оба запроса в `*_after.tsv` и сравнить.

**Ожидание**: 0 отличий (`diff` пуст).

## Чек-лист приёмки

- [ ] Юнит-тесты `VoiceNormalizationTest` — зелёные (12+ кейсов)
- [ ] Компиляция + ktlint + ESLint + формат — без ошибок
- [ ] 15696 после сохранения: 1 голос, 621 маркер, текст не изменён (SC-001)
- [ ] Пустой не-первый голос отсутствует после сохранения на 10 песнях (SC-002, §11)
- [ ] `countVoices` в карточке/фильтре/API совпадает с данными (SC-003)
- [ ] Повторное сохранение не меняет данные голосов (SC-004, §8)
- [ ] На 20 песнях с 2+ голосами непустые голоса сохранены побайтово (SC-005, §11)
- [ ] US4: 10 сохранений подряд — 0 дубликатов, 0 потерь (SC-006)
- [ ] Латентность сохранения голоса +≤10 % (SC-007, §10)
- [ ] Регрессии 544/546/299/357 — 0 (SC-008, §12)
- [ ] Задания и черновики не изменились (FR-015, §13)
- [ ] Аудио-потомки: без цикла синхронизации (research R7, §7)
- [ ] `docs/features/drop-empty-song-voices.md` обновлён (FR-009 конституции)
