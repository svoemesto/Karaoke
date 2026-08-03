# Модель данных: аудиоданные при выборе похожей версии

**Feature**: `129-copy-family-audio`

## Сущности

### `Song` — текущая песня

Существующая запись `tbl_songs`, редактируемая в `SongEdit`. Для этой фичи используются уже существующие non-null-поля:

| Поле модели | Колонка | Тип | Sentinel | Назначение |
|---|---|---:|---:|---|
| `audioParentId` | `audio_parent_id` | `Long` / `integer` | `0` | ID строки, явно выбранной как аудио-родитель |
| `audioSimilarityPercent` | `audio_similarity_percent` | `Int` / `integer` | `0` | Процент последней успешной сверки; без сверки — 0 |
| `audioDeltaMs` | `audio_delta_ms` | `Long` / `bigint` | `0` | Signed-сдвиг результата сверки в миллисекундах; без сверки — 0 |

Дополнительно в той же операции изменяются существующие поля family selection: `sourceText`, `resultText`, `sourceMarkers`, `formattedTextSong`, `formattedTextTabs`, `formattedTextChords`, `rootId` и условно `idStatus`. Их семантика этой фичей не меняется.

Источник typed-свойств и их default-преобразование: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:800-837`.

### `SelectedFamilySong` — выбранная похожая версия

Существующая строка `FamilySongsModal` с полями `id`, отображаемыми метаданными и признаком `current`. При успешном выборе её `id` становится значением `Song.audioParentId` текущей песни.

Если выбранная версия сама имеет `audioParentId`, ручной flow всё равно сохраняет ID фактически выбранной строки. Это связывает сохранённые метрики с той же парой, для которой выполнена сверка, и не применяет автоматический flattening.

### `WaveformComparison` — результат акустической сверки

Локальный результат, хранимый в `FamilySongsModal.compareResults[selectedId]`:

| Поле | Тип | Семантика |
|---|---:|---|
| `status` | enum-like string | `idle`, `loading`, `done`, `error` |
| `similarityPercent` | `Int` | 0–100, включая валидный 0 |
| `deltaMs` | `Long` | Signed-сдвиг, включая валидный 0 |
| `stemUsed` | string | Информационное описание использованного стема |
| `error` | nullable string | Причина неуспешной сверки |

Результат считается доступным для переноса только при `status == 'done'`. Числовые нули сами по себе не определяют наличие результата.

Источник результата: `webvue3/src/components/Songs/edit/FamilySongsModal.vue:124-179`.

### `FamilySongSelectionResult` — ответ применения выбора

Расширенное существующее DTO ответа `/api/song/selectfamilysong`:

| Поле | Тип | Источник |
|---|---:|---|
| `rootId` | `Long` | фактически сохранённый `Song.rootId` |
| `idStatus` | `Long` | фактически сохранённый статус |
| `audioParentId` | `Long` | фактически сохранённый ID выбранной строки |
| `audioSimilarityPercent` | `Int` | нормализованный процент или 0 |
| `audioDeltaMs` | `Long` | нормализованный signed-сдвиг или 0 |

Ответ не создаёт новую сущность и не заменяет полный `SongDTO`; он сообщает только значения, которые нужны открытому редактору для немедленной синхронизации.

## Отношения

```text
Текущая Song 1 ── selects ──> 1 SelectedFamilySong
Текущая Song 1 ── has ───────> 0..1 audioParentId (ID выбранной строки)
Текущая Song + SelectedFamilySong ── produces ──> 0..1 WaveformComparison
WaveformComparison ── maps to ──> audioSimilarityPercent + audioDeltaMs
```

`rootId` остаётся отдельной кураторской family-связью. `audioParentId` не заменяет `rootId` и не участвует в выборе обычного текстового корня.

## Состояния и переходы

| Состояние до выбора | Вход | Состояние после успешного выбора |
|---|---|---|
| Любые аудиополя, кандидат `B`, сверка `done` с `P/D` | выбор `B` | `audioParentId=B.id`, `audioSimilarityPercent=P`, `audioDeltaMs=D` |
| Старые аудиополя кандидата `A`, для `B` сверки нет | выбор `B` | `audioParentId=B.id`, `audioSimilarityPercent=0`, `audioDeltaMs=0` |
| Старые аудиополя, результат `error`/`loading`/`idle` | выбор `B` | то же, что при отсутствии сверки: `B.id`, `0`, `0` |
| Любое состояние | клик по `current=true` | состояние не меняется |
| Любое состояние | невалидный запрос или ошибка сохранения | состояние БД и редактора не получает новый частичный набор |

После успешного ответа frontend обновляет открытый объект и snapshot одними и теми же значениями. Это предотвращает повторную отправку тех же трёх полей debounce-autosave.

## Правила валидации

1. `id` и `idAnother` обязательны и не могут совпадать.
2. `idAnother` должен соответствовать реально загруженной песне; `audioParentId` не принимается как независимое клиентское значение.
3. `audioSimilarityPercent` отсутствует вместе с `deltaMs` — это выбор без сверки, результат `0/0`.
4. Если присутствует одна из двух метрик, должна присутствовать и вторая; частичная пара отклоняется до изменения записи.
5. Переданный процент находится в диапазоне `0..100`, границы включаются.
6. `deltaMs` сохраняется как signed `Long`; знак и нулевое значение не меняются.
7. Успешный ручной выбор не обновляет `audioCompareHistory`; отсутствие истории не влияет на сохранённые три поля.

## Персистентность и синхронизация

- Новых колонок и миграций нет.
- `Song.getDiff()` уже создаёт `RecordDiff` для всех трёх полей (`Song.kt:6885-6900`).
- Три поля устанавливаются до существующего единственного `Song.saveToDb()`, поэтому текст, маркеры, root/status и аудиоданные попадают в один SQL `UPDATE`.
- Recordhash и существующий LOCAL↔SERVER sync автоматически учитывают изменения; `SyncRegistry` и SQL-триггеры не изменяются.
- После сохранения backend перечитывает запись для проверки ожидаемых аудиозначений; при несоответствии не возвращает успешный результат.

## Вне модели этой фичи

- `SongDTOdigest` и отображение процента/дельты в `SongsTable`.
- Автоматический `findAudioParentByWaveform` и `autoAssignOriginalByWaveform`.
- Публичный сайт/плеер.
- Схема `audio_compare_history` и кэширование сверок.
