# Phase 1 Data Model: Список авторов в фильтрах

**Feature**: `450-author-filter-datalist` | **Date**: 2026-09-24

Фича не создаёт и не изменяет серверных сущностей и БД. Ниже —
frontend-модели состояния и передаваемые значения, затронутые изменением.

## 1. Состояние компонента `AuthorsFilterModal.vue`

| Поле | Тип | Дефолт | Назначение |
|------|-----|--------|------------|
| `dictAuthors` | `Array<string>` | `[]` | Локальный справочник имён авторов для `<datalist>` (НОВОЕ) |
| `authorsFilterAuthor` | `string` | из `getWebvueProp('authorsFilterAuthor', '')` | Значение поля «Автор:» (существующее, computed) |

**Инварианты**:
- `dictAuthors` — плоский отсортированный список строк (как отдаёт
  `Song.loadListAuthors` через `POST /api/songs/authors`).
- Ошибка загрузки → `dictAuthors` остаётся `[]`, поле работает как
  текстовый ввод (FR-006).
- `authorsFilterAuthor` не изменяется при загрузке справочника (FR-008).

## 2. Состояние компонента `AlbumsFilterModal.vue`

| Поле | Тип | Дефолт | Назначение |
|------|-----|--------|------------|
| `dictAuthors` | `Array<string>` | `[]` | Локальный справочник имён авторов для `<datalist>` (НОВОЕ) |
| `albumsFilterAuthorName` | `string` | из `getWebvueProp('albumsFilterAuthorName', '')` | Значение поля «Автор:» (существующее, computed) |

**Инварианты**: те же, что для `AuthorsFilterModal`.

## 3. Источник данных (read-only)

| Источник | Тип | Описание |
|----------|-----|----------|
| `getters.songAuthorsPromise` | `Promise<string>` | Vuex-геттер `webvue3/src/components/Songs/store.js:941`; `POST /api/songs/authors`, возвращает JSON-строку `{"authors": [...]}`, из которой берётся `.authors` |
| `POST /api/songs/authors` | HTTP | `ApiController.kt:2095` → `Song.loadListAuthors(WORKING_DATABASE): List<String>` |

**Примечание**: `songAuthorsPromise` возвращает обещание «сырой» JSON-строки;
парсинг (`JSON.parse(data).authors`) делается в компоненте — ровно как в
`SongsFilterModal.vue:839–841`.

## 4. Контракт значений фильтра (не меняется)

| UI-поле | Параметр запроса | Значение | Семантика |
|---------|------------------|----------|-----------|
| Authors «Автор:» | `filterAuthor` | строка | точное совпадение (существующая) |
| Albums «Автор:» | `filterAuthorName` | строка | резолв точного имени → `author_id` (существующая) |

**Инварианты**:
- Пустое значение → параметр не отправляется → показываются все записи
  (FR-005).
- Значение персистируется через `setWebvueProp` (server-side key/value) без
  изменений (FR-008).

## 5. Переходы состояния (загрузка справочника)

```text
[модалка открыта] ──mounted()──▶ dictAuthors=[]  (поле работает как текст)
                                   │
                    songAuthorsPromise.then(data)
                                   │
                    JSON.parse(data).authors
                                   │
                                   ▼
                            dictAuthors=[имена]  (появляются подсказки)
```

Ошибочный путь: при reject/невалидном JSON — `dictAuthors` остаётся `[]`,
исключение не «всплывает» в UI (поведение как в эталоне Songs).

## 6. Что НЕ входит в модель

- Новые БД-таблицы/поля — нет.
- Новые Vuex state/getters/actions — нет (используется существующий
  геттер).
- Изменения `karaoke-public` — нет (Constitution V).
