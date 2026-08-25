# Contract: `Utils.findDuplicateOriginal` (урезание fallback)

**Дата**: 2026-08-25
**Спека**: [../spec.md](../spec.md)
**Research**: [../research.md](../research.md)

## Назначение

Точечное изменение существующей функции `findDuplicateOriginal` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` (строка 4256) — убрать fallback на поиск «родителя» среди песен **других авторов**. Поиск должен ограничиваться **только** песнями того же автора, что и импортируемая.

## Текущее поведение (ДО фичи)

```kotlin
fun findDuplicateOriginal(
    newSong: Song,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Song? {
    ...
    fun findId(sameAuthorOnly: Boolean): Long? { ... }

    val id = findId(sameAuthorOnly = true) ?: findId(sameAuthorOnly = false) ?: return null
    return Song.loadFromDbById(id = id, ...)
}
```

**Эффект**: при ненахождении «родителя» у того же автора делается **второй** запрос по всей БД с `sameAuthorOnly = false` — ищет среди всех авторов. Это и есть источник ложных привязок (одинаковые по названию песни у разных исполнителей получают чужой текст).

## Новое поведение (ПОСЛЕ фичи)

```kotlin
fun findDuplicateOriginal(...): Song? {
    ...
    fun findId(sameAuthorOnly: Boolean): Long? { ... }

    val id = findId(sameAuthorOnly = true) ?: return null
    return Song.loadFromDbById(id = id, ...)
}
```

**Эффект**: если у того же автора «родитель» не найден — функция возвращает `null`, никакого fallback'а нет.

## Изменения в коде

| Файл | Строка | Изменение |
|------|--------|-----------|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` | 4296 | Заменить `val id = findId(sameAuthorOnly = true) ?: findId(sameAuthorOnly = false) ?: return null` на `val id = findId(sameAuthorOnly = true) ?: return null` |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt` | 4262-4267 | Обновить KDoc: убрать упоминание про fallback на «среди всех авторов» |

**Минимальное изменение**: ~3-5 строк в одном файле.

## Поведенческие гарантии

- **FR-001**: поиск ведётся только среди песен того же автора.
- **FR-002**: при ненахождении у того же автора запись остаётся без `root_id`, после чего отрабатывает существующая фоновая логика поиска текста через SearXNG (`ApiController.kt:5280-5298`).
- **FR-003**: правила выбора среди песен того же автора (нормализация имени, регистр, исключение скобок, приоритет по `id ASC`, фильтр `TRIM(source_text) <> ''`) сохранены без изменений.

## Что НЕ делает

- НЕ изменяет `findParentCandidateId` (`Utils.kt:4314`) — это другая функция, используемая в `customFunction` (пакетный поиск родителей); пользователь явно просил ограничить поиск только в импорте из папки, не в `customFunction`.
- НЕ изменяет поведение `markDublicatesPromise` / кнопки «Найти и обработать дубликаты песен автора» (FR-004).

## Совместимость

- Все вызывающие `findDuplicateOriginal` (включая `ApiController.doCreateFromFolder`) автоматически получают новое поведение без изменения вызывающего кода.
- `Song.createFromPath` (через `applyDuplicateOriginal`) — без изменений в сигнатуре, переиспользует ту же функцию.