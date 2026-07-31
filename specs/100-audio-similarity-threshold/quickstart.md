# Quickstart: Повышение порога аудио-похожести и демотация статуса при импорте из папки

**Branch**: `100-audio-similarity-threshold` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

> Ручная проверка фичи end-to-end. CI-тестов нет (конституция); проверка — на admin-машине 
> (LOCAL Postgres, `karaoke-app`). См. `contracts/behavior-contract.md` для полных постусловий.

## Предпосылки

- admin-машина с запущенным `karaoke-app` (на `dev-pc`/`dev` агент может пересобрать/перезапустить сам; на иной машине — просит пользователя).
- В LOCAL БД есть минимум одна песня `X` с `id_status >= 6` (READY) и непустыми `source_markers`.
- Есть аудиофайл той же композиции (тот же мастер, или копия), который даст `WaveformCompare` ≥ 95% — для сценария 1; и файл, дающий 85–94% — для сценария 2.
- Доступ к `/sm-karaoke/system/...` и MinIO (стандартное окружение admin-машины).
- `git config blame.ignoreRevsFile .git-blame-ignore-revs` настроен (конституция VII.2 — для чистоты `git blame`, не строго для проверки).

## Сборка

```bash
# Бэкенд (на dev-pc/dev — агент сам; иначе — пользователь)
./gradlew karaoke-app:bootJar
# Перезапуск контейнера karaoke-app — только пользователь (или агент на dev-pc/dev)
```

## Сценарий 1: Импорт песни с готовым аудио-родителем ≥95% → статус 5

**Цель**: подтвердить FR-003 — демотация 6→5.

1. Подготовить папку `/tmp/qs-100/` с одним аудиофайлом `Y`, заведомо дающим ≥95% сходства с готовой `X` (например, копия мастер-файла `X` или тот же студийный трек).
2. Вызвать импорт:
   ```bash
   curl -X POST 'http://localhost:8080/api/utils/createfromfolder' \
        -d 'folder=/tmp/qs-100' -H 'Content-type: application/x-www-form-urlencoded'
   ```
   (или через UI админки: «Добавление файлов из папки»).
3. Дождаться SSE-тоста `"Добавлено файлов из папки «/tmp/qs-100»: 1 (пропущено: 0)"`.
4. Найти импортированную `Y` в БД:
   ```sql
   SELECT id, id_status, audio_parent_id, audio_similarity_percent, audio_delta_ms
   FROM tbl_songs WHERE song_name = '<имя Y>' ORDER BY id DESC LIMIT 1;
   ```
5. **Ожидаемый результат**:
   - `id_status = 5` (было бы 6 до фичи),
   - `audio_parent_id` = id `X` (или resolved root `X`, если у `X` есть свой аудио-родитель),
   - `audio_similarity_percent >= 95`,
   - `source_text`/`source_markers` `Y` совпадают с `X` (со сдвигом `audio_delta_ms`).
6. **FAIL-признаки**: `id_status = 6` (демотация не сработала), `audio_parent_id = 0` (аудио-родитель не найден — порог слишком высок или `WaveformCompare` не отработал), текст/маркеры пустые.

## Сценарий 2: Импорт песни с кандидатом 85–94% → аудио-родитель НЕ назначается

**Цель**: подтвердить FR-001 (порог 95) и FR-005 (статус не меняется аудио-путём).

1. Подготовить папку с аудиофайлом, дающим 85–94% по `WaveformCompare` (например, live-версия или ремастер той же композиции — типичный случай ложного срабатывания на старом пороге 85).
2. Импортировать (как в сценарии 1).
3. Проверить в БД:
   ```sql
   SELECT id, id_status, audio_parent_id, audio_similarity_percent FROM tbl_songs WHERE song_name = '<имя>' ORDER BY id DESC LIMIT 1;
   ```
4. **Ожидаемый результат**:
   - `audio_parent_id = 0` (кандидат ниже порога 95),
   - `id_status` — не повышен аудио-путём (0 или 1, если сработал обычный `findDuplicateOriginal`),
   - `audio_similarity_percent` — может быть записан в `audio_compare_history` (кэш), но не в `audio_similarity_percent` (то пишется только при назначении родителя).
5. **FAIL-признаки**: `audio_parent_id <> 0` и `audio_similarity_percent ∈ [85, 94]` — порог не повышен, старое поведение.

## Сценарий 3: Ручной `/song/findaudioparent` — reason содержит «95%»

**Цель**: подтвердить FR-006 (человекочитаемые строки) и контракт 2.

1. Взять любую песню `Z` (можно из сценария 2, где аудио-родитель не назначился).
2. Вызвать:
   ```bash
   curl -X POST 'http://localhost:8080/api/song/findaudioparent' -d 'id=<Z>'
   ```
3. **Ожидаемый результат** (если `best.similarityPercent` ниже 95):
   ```json
   {"audioParentId":0,"audioSimilarityPercent":<NN>,"audioDeltaMs":<...>,"matched":false,
    "reason":"Лучшее совпадение <NN>% (id=<...>) ниже порога 95%"}
   ```
   В `reason` — **«95%»**, не «85%».
4. **FAIL-признаки**: `reason` содержит «85%» — константа не подхвачена / хардкод в строке.

## Сценарий 4: `/songs/autoassignoriginalall` — дефолт 95, параметр работает

**Цель**: подтвердить FR-001a (дефолт 95, параметр остаётся).

1. Подготовить в БД песню `W` с `id_status = 1 AND root_id <> 0` (TEXT_CREATE с привязкой к семье), у которой в семье есть кандидат с маркерами и `WaveformCompare` ≥ 95.
2. Вызвать **без** `?threshold=`:
   ```bash
   curl -X POST 'http://localhost:8080/api/songs/autoassignoriginalall'
   ```
3. Дождаться SSE-итога. Проверить `W`:
   ```sql
   SELECT id_status FROM tbl_songs WHERE id = <W>;
   ```
   **Ожидаемый результат**: `id_status = 2` (TEXT_CHECK — `autoAssignOriginalByWaveform` переводит в 2, **не** в 5/6; демотация к этому механизму НЕ применяется).
4. Повторить с явным низким порогом:
   ```bash
   curl -X POST 'http://localhost:8080/api/songs/autoassignoriginalall?threshold=80'
   ```
   **Ожиданный результат**: параметр `threshold=80` honoured — кандидаты с 80–94% теперь принимаются (если они были в `audio_compare_history` от прежних прогонов и `ok=true`). Это подтверждает, что параметризация сохранена (вариант C из Clarification).
5. **FAIL-признаки**: `?threshold=80` игнорируется (дефолт 95 перебивает) — параметризация сломана.

## Сценарий 5: grep логов и SSE на «95%» / отсутствие «85%»

**Цель**: подтвердить SC-003 (ни одно сообщение не содержит устаревший «85%»).

1. Выполнить сценарии 1–4 (все пути: импорт, findaudioparent, customFunction-эквивалент через кнопку «Custom Function», autoassignoriginalall).
2. Просмотреть логи `karaoke-app` и SSE-тосты:
   ```bash
   docker logs karaoke-app 2>&1 | grep -E 'порог|аудио-родитель|совпадение' | tail -40
   ```
3. **Ожидаемый результат**: во всех mentions порога — «95%». Ни одного «85%» в строках про аудио-родителя/порог.
4. **Допустимое «85»**: цветовые `Color(85, 255, 255, 255)` в `UtilsPictures.kt`/`KaraokeProperties.kt`, `kdenlive:audio_max0=185`, `Mozilla ... Firefox/85.0` в `Constants.kt` — это **не** наши строки (grep должен быть по контексту «порог/аудио-родитель», не по подстроке «85»).

## Сценарий 6: ktlint + KDoc-coverage (CI-эквивалент)

**Цель**: подтвердить Principle VI (FR-006/FR-007).

```bash
./gradlew ktlintCheck
bash tools/check-kdoc-coverage.sh        # должно быть 100%
```
**Ожидаемый результат**: 0 нарушений, KDoc-coverage 100%. Если KDoc-coverage падает — KDoc `applyAudioParentMarkers`/`findAudioParentByWaveform`/`autoAssignOriginalByWaveform`/`AUDIO_PARENT_THRESHOLD` обновлены некорректно (см. research.md R4).

## Сценарий 7: Обратимость (опционально, для регрессии)

**Цель**: подтвердить Assumption (изменение обратимо двумя правками).

1. Временно вернуть `AUDIO_PARENT_THRESHOLD = 85`, `"6"` в `applyAudioParentMarkers`, дефолты `threshold = 85`.
2. Пересобрать, повторить сценарий 1 → `id_status = 6` (старое поведение).
3. Откатить временные правки (вернуть 95/5/95).

Это подтверждает, что фича не ввела скрытых зависимостей. Необязательно для приёмки, но рекомендуется при ревью.

## Чек-лист приёмки

- [ ] Сценарий 1: импорт ≥95% → `id_status = 5` (демотация работает)
- [ ] Сценарий 2: импорт 85–94% → `audio_parent_id = 0` (порог 95 отсекает)
- [ ] Сценарий 3: `findaudioparent` `reason` содержит «95%»
- [ ] Сценарий 4: `autoassignoriginalall` без `?threshold=` → дефолт 95; с `?threshold=80` → honoured
- [ ] Сценарий 5: в логах/SSE — «95%», нет «85%» в строках про аудио-родителя
- [ ] Сценарий 6: ktlint + KDoc-coverage проходят
- [ ] (опц.) Сценарий 7: возврат к 85/6 восстанавливает старое поведение