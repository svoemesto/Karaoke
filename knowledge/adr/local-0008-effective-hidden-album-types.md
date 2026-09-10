# Local ADR-0008: Transient auto-reset `effectiveHiddenAlbumTypes` для `?albumId=`

* **Status**: Accepted
* **Date**: 2026-09-10
* **Deciders**: команда Karaoke
* **Issue**: OpenProject #78 (spec 363)

> **English version**: TBD (см. шаблон в `0001-raw-jdbc.md` если потребуется)
>
> **Note**: this is **local** ADR — описывает конкретный frontend-паттерн в
> `karaoke-public/src/views/ZakromaView.vue` (а не глобальное архитектурное
> решение).

## Context

Спека 356 (Pass 359) ввела промежуточный этап «Альбомы автора» в
навигации `/zakroma/{authorId}/albums` → клик по плашке альбома →
`/zakroma/{authorId}?albumId={id}`. На странице песен работает
быстрый фильтр категорий альбомов (`hiddenAlbumTypes: Set<string>` в
`localStorage.km-zakroma-hidden-album-types`), скрывающий целые типы
(например, «singl»).

## Проблема

Прецедент (Pass 362, issue #78): пользователь скрывает тип «singl»
через UI-фильтр на странице `/zakroma/{id}`. Переходит на
`/zakroma/{id}/albums`, кликает на плашку альбома-сингла. Открывается
`/zakroma/{id}?albumId=Y` (Y = id этого альбома), но
`ZakromaView.visibleAlbums(zak)` исключает Y потому что его `albumType`
есть в `hiddenAlbumTypes`. Страница показывает пустоту — пользователь
не понимает почему.

Альтернатива «заставить пользователя вернуться и включить тип» —
anti-pattern: фильтр по типу не должен блокировать просмотр конкретного
альбома, на который пользователь явно кликнул.

## Decision

Ввести computed/method `effectiveHiddenAlbumTypes(zak)` в
`karaoke-public/src/views/ZakromaView.vue`, который возвращает
`hiddenAlbumTypes` за вычетом `albumType` открытого альбома (если
он там есть). `visibleAlbums(zak)` использует результат.

```js
effectiveHiddenAlbumTypes(zak) {
  const albumId = this.selectedAlbumId
  if (albumId == null) return this.hiddenAlbumTypes
  const opened = (zak?.albums || []).find(
    (alb) => Number(alb.albumId) === Number(albumId),
  )
  if (!opened || !this.hiddenAlbumTypes.has(opened.albumType)) {
    return this.hiddenAlbumTypes
  }
  const next = new Set(this.hiddenAlbumTypes)
  next.delete(opened.albumType)
  return next
}
```

Параллельно **вся** панель `.km-album-controls-bar` (переключатель
«Сквозной/По типам альбомов» + фильтр категорий) скрывается через
`v-if="zakromaAlbumTypeCounts.length > 0 && !selectedAlbumId"` —
при просмотре одного альбома оба элемента неактуальны (нет смысла
выбирать режим отображения, когда альбом один, а фильтр по типу
auto-reset'нут).

## Альтернативы

- **Мутировать `hiddenAlbumTypes` Set + откатывать при уходе**: rejected
  — требует хранения «снапшота оригинала», добавляет state, race-condition
  при возврате до окончания reset; риск рассинхронизации с `localStorage`.
- **Писать в `localStorage`**: rejected — нарушает FR-003 (transient state),
  портит оригинальное значение фильтра (юзер не ожидает что возврат
  изменил его выбор).
- **Backend фильтрация по `?albumType=`**: rejected — anti-pattern
  (дублирование состояния между query и localStorage), лишний round-trip,
  не решает проблему UX-шумности фильтр-бара на странице одного альбома.
- **Очистить `hiddenAlbumTypes` целиком при входе на `?albumId=`**: rejected
  — слишком агрессивно, сбрасывает фильтры по другим типам, которые юзер
  хотел скрыть (например, скрыть `archive` + открыть `single` → `archive`
  тоже снимется).

## Consequences

### Positive

- Пользователь, явно кликнувший на альбом, всегда видит его песни —
  даже если тип скрыт в фильтре.
- `localStorage.km-zakroma-hidden-album-types` остаётся байт-в-байт
  идентичным до и после навигации (FR-003). Никаких сюрпризов для
  пользователя.
- `hiddenAlbumTypes` Set не мутируется — реактивность сохраняется,
  watcher'ы не нужен.
- Чисто реактивный computed (Vue 2) — пересчитается автоматически при
  изменении `hiddenAlbumTypes`, `selectedAlbumId` или `zakroma`.

### Negative

- Дополнительный метод (~10 строк) в компоненте, который уже не маленький
  (1500 строк). Можно вынести в composable, но scope фичи минимален —
  premature extraction.
- `effectiveHiddenAlbumTypes(zak)` — метод с параметром (а не чистый
  computed). Это намеренно: для каждого автора (`zak`) фильтр
  рассчитывается отдельно в зависимости от того, открыт ли его альбом.

### Neutral

- Вся панель `.km-album-controls-bar` скрывается на странице одного
  альбома — небольшое изменение UX (раньше фильтр-бар показывался, теперь
  нет).
- Поведение `toggleAlbumType(dbValue)` (FR-025 спеки #012) не затрагивается.

## References

- `specs/363-album-type-filter-reset-on-album-open/spec.md` — Issue #78
- `specs/356-zakroma-albums-by-author/spec.md` — донорский паттерн URL-фильтрации
- `knowledge/adr/local-0007-zakroma-album-id-in-stream-dto.md` — прецедент фильтрации в ZakromaView (albumId в стриме)
- `knowledge/domains/catalog/components/album-entity.md` — определение `AlbumType` enum (dbValue)
- `karaoke-public/src/views/ZakromaView.vue:730-763` — реализация `effectiveHiddenAlbumTypes` + изменённый `visibleAlbums`

## История

- 2026-09-10: создан (Pass 362) после issue #78 от пользователя: открытие
  альбома-сингла со скрытым в фильтре типом `single` показывает пустую
  страницу. Решение — transient computed без побочных эффектов, плюс
  скрытие всей панели `.km-album-controls-bar` на странице одного альбома
  (уточнение владельца после первой реализации).