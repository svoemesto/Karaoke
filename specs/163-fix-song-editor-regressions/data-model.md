# Phase 1 Data Model: Исправление регрессий редакторов текста песни после внедрения спецтегов

**Дата**: 2026-08-09
**Спека**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

Фича не вводит новых сущностей БД, таблиц, колонок или миграций (см. Assumptions spec.md,
Technical Context plan.md). Ниже — существующие in-memory структуры данных `webvue3`, чья форма и
инварианты непосредственно затрагиваются исправлением, зафиксированные как контекст для реализации
и тестов.

## Marker (in-memory, JS)

Единица разметки на вейвформе. Формат идентичен между обоими редакторами (независимые копии,
`useKaraokeEditor.js` и `SubsEdit.vue`) и совместим с backend `SourceMarker` (`SourceMarker.kt`)
после сериализации через `markersToSave()`.

| Поле | Тип | Обязательность | Примечание |
|---|---|---|---|
| `uid` | string | только in-memory, только облегчённый редактор | Не отправляется на сервер (`markersToSave()` его отбрасывает); связывает JS-объект с WaveSurfer-регионом. |
| `time` | number (сек) | да | Позиция на вейвформе. Для маркеров, синхронизированных из спецтегов — вычисляется по формуле `gap >= 1.0 ? nextStartTime - 1.0 : prevEndTime + gap / 2`, см. research.md §2. |
| `label` | string | да (может быть `''`) | Для `markertype: 'setting'` — `"GROUP|<0-4>"` / `"COMMENT|<текст>"`; для остальных типов — слог/пусто. |
| `color` | string (hex) | да | Устанавливается по `markertype` при создании; не участвует в дедупликации. |
| `position` | `'top' \| 'bottom'` | да | UI-подсказка положения ярлыка, не участвует в дедупликации. |
| `markertype` | string (см. `Markertype.kt` значения) | да | Участвует и в дедупликации (§ниже), и в сортировке (`sortMarkers`). |

**Инвариант дедупликации (уже существующий, FR-007 spec.md)**: для одного и того же
`(markertype, label)` в пределах "окна" между двумя соседними слоговыми (`syllables`) маркерами не
должно быть больше одной записи, добавленной через `syncMarkersFromSpecTags`.

**Инвариант, который эта фича добавляет явно (см. research.md §2)**: повторный вызов
`syncMarkersFromSpecTags(markers, sourceText)` на **неизменных** `markers`/`sourceText` НЕ должен
менять длину `markers` (идемпотентность). Ранее это подразумевалось контрактом
(`specs/010-lyrics-spec-tags/contracts/tag-registry.md`, инвариант 3 "дубликат не создаётся"), но
не было устойчивым при совпадении времени вставки с временем соседнего маркера — см.
`contracts/sync-idempotency-invariant.md`.

## SpecTagAnchor (in-memory, промежуточный результат `specTagAnchors()`)

| Поле | Тип | Примечание |
|---|---|---|
| `syllableIndex` | number | Порядковый индекс слогового маркера, ПОСЛЕ которого должен стоять якорь тега. |
| `markertype` | string | Из `SPEC_TAG_REGISTRY`/алиасов (`'newline'` \| `'setting'`). |
| `label` | string | Из `resolveSpecTag()` (например, `'GROUP|1'`). |

Не персистится — пересчитывается из `sourceText` при каждом вызове `syncMarkersFromSpecTags`.
Изменений формы в рамках этой фичи не требуется — правится только то, КАК якорь превращается во
вставку в `markers` (см. Marker выше).

## Voice (облегчённый редактор, `SongKaraokeEditorView.vue`/`Modal.vue`)

| Поле | Тип | Примечание |
|---|---|---|
| `sourceText` | string | Текст голоса, может содержать строки-спецтеги. |
| `markers` | `Marker[]` | См. выше. |
| `syllables` | string[] | Результат `splitSyllables(sourceText)`, пересчитывается на каждый `onTextInput()`. |

`SongKaraokeEditorModal.vue` при сохранении сериализует **все** голоса разом:
`sourceTexts = voices.map(v => v.sourceText)`, `markersPerVoice = voices.map(v =>
markersToSave(v.markers))` — форма запроса `/api/songeditor/edit/save` не меняется этой фичей.

## SaveState (облегчённый редактор, `SongKaraokeEditorModal.vue`)

Существующее поле `saveState: 'idle' | 'saving' | 'saved' | 'error'` — расширяется по смыслу (не
по набору значений): переход в `'saved'` теперь обязан зависеть от поля `ok` в теле ответа
`/api/songeditor/edit/save`, а не только от успешного HTTP-статуса (FR-003). Новое поведение,
FR-004: пока последнее значение — `'error'`, попытка закрыть модалку (`$emit('close')`) должна
быть перехвачена явным предупреждением перед фактическим закрытием — форма представления этого
предупреждения (confirm-диалог/кастомный компонент) выбирается при реализации по существующим в
проекте паттернам (например, `CustomConfirm`, уже используемый в `SubsEdit.vue`).
