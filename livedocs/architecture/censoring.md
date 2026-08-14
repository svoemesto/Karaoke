---
status: Active
slug: censoring
type: topic
related:
  - ../features/139-fix-censored-dictionary.md
  - ../features/140-fix-zakroma-censored-database.md
  - ../features/141-fix-censored-web-storage-globals.md
  - ../domain/publishing.md
  - ../domain/processing.md
---

# Censoring — паттерн цензурирования текста (topic)

> Drill-down для всего паттерна цензурирования матерных слов в публикациях.
> Конкретные баг-фиксы — в [`../features/`](../features/README.md).

## Назначение

**Censoring** — паттерн замены символов внутри матерных слов на `*` при
публикации в публичных каналах (сайт, Telegram, VK). Применяется к:
- названиям песен (`{songNameCensored}` placeholder в шаблонах новостей),
- именам исполнителей (опционально),
- тексту новостей (опционально).

## Компоненты паттерна

### Файл словаря

- **`deploy/new_comp/sm-karaoke-system/censored.txt`** — список слов с
  маской замены. Формат — `слово[маска]часть`. Например:
  ```
  бл[я]              →  бл*
  долбо[ё]б          →  долбо*б
  п[и]зда            →  п*зда
  ```
  Символ в `[]` маскируется на `*`. **Не** цензурируется полностью слово —
  только внутренняя буква, чтобы сохранить узнаваемость.
- 51 слово в файле (на 2026-08-14).

### Backend

- **`CensoredWordsDictionary.kt`** — реализация `TextFileDictionary`,
  читает из БД (`tbl_dictionaries.dictValues`). Слова хранятся в БД
  для возможности оперативного обновления без передеплоя.
- **`TextFileDictionary`** — базовый класс, общий для всех словарей
  (Censored, Yo, SyncIds, Test). Реализует загрузку и применение regex.
- **`String.censored(database)`** — extension-функция для применения
  цензурирования. **Требует явный `database` параметр** (см. спеку 139)
  — иначе падает с `IllegalStateException: Property APP_WORK_ON_SERVER
  should be initialized`.

### Frontend

- Не используется на frontend — цензурирование применяется ТОЛЬКО на
  backend перед публикацией в каналы.

### Database

- **`tbl_dictionaries`** — общая таблица для всех словарей. Поля:
  `dictName`, `dictValues` (text[]).
- Загрузка — `TextFileDictionary.dict:60`, ленивая (по первому
  обращению).

## Применение в публикациях

### Шаблон новости

В шаблонах новостей (`tbl_news` или конфиг в `KaraokeProperties`)
используется placeholder `{songNameCensored}`:

```kotlin
val template = "Новая песня: {songNameCensored} в эфире!"
val result = template.replace("{songNameCensored}", song.name.censored(database))
// "Новая песня: п*зда в эфире!"  (если название содержало "пизда")
```

### Где применяется

- **Telegram auto-publish** (спека 113): в шаблоне поста.
- **VK auto-publish** (спека 121): в шаблоне поста.
- **News** (спека 089 + 124): в шаблоне новости сайта.
- **Premium auto-publish** (спека 122): в шаблоне премиум-публикации.

### Где НЕ применяется

- В интерфейсе админки — админ видит оригинальные названия.
- В Закромах — там оригинал нужен для редактирования.
- В `KaraokePlayer` — текст песни показывается as-is.

## Regex и Unicode

- Стандартный regex `\p{L}\p{N}` — поддержка русских (кириллица) и
  латинских букв + цифр. **Не** поддерживает emoji и спецсимволы
  (но они и не нужны в матерных словах).
- Регистр — учитывается (case-sensitive). Для проверки uppercase
  версий — отдельный набор записей или предобработка.

## Глобалы и DI

- **`KSS_APP`**, **`SAC_APP`**, **`APP_WORK_*`** — глобалы `karaoke-app`,
  которые должны быть инициализированы в `karaoke-web` (спека 141).
  Без инициализации — `String.censored()` падает с
  `UninitializedPropertyAccessException`.
- **`WebKaraokeStorageServiceImpl`** — заглушка storage для `karaoke-web`.
  Бросает `UnsupportedOperationException` на любой вызов storage —
  это безопасно, потому что `CensoredWordsDictionary` не пользуется
  storage в runtime (только в инициализации `WORKING_DATABASE`).

## Известные баги (фичи)

- **Спека 139**: `{songNameCensored}` не работал на проде → название
  появлялось as-is. Фикс: явный `database` параметр + Unicode regex.
- **Спека 140**: Закрома падали на загрузке `Censored` словаря →
  `IllegalStateException`. Фикс: `database` параметр в
  `TextFileDictionary.dict()`.
- **Спека 141**: `KSS_APP` не инициализирован в `karaoke-web` →
  `lateinit property KSS_APP has not been initialized`. Фикс: явная
  инициализация через DI в `KaraokeWebService`.

## Тестирование

- **`TextFileDictionaryTest`** (юнит-тест): проверка regex,
  case-sensitivity, Unicode.
- **Интеграционный тест**: добавление слова в словарь → проверка, что
  публикация содержит цензурированную форму (спека 139, FR-003).

## Добавление нового слова

1. Добавить слово в `deploy/new_comp/sm-karaoke-system/censored.txt`
   с маской `[маскируемая буква]`.
2. Передеплоить `censored.txt` на сервер.
3. Запустить миграцию (если есть DDL) для `tbl_dictionaries`.
4. Перезапустить `karaoke-web` (для перечитки файла в БД).
5. Проверить в UI админки, что слово появилось в словаре
   (Tools → Dictionaries).
6. Проверить интеграционный тест.

## См. также

- [`../features/139-fix-censored-dictionary.md`](../features/139-fix-censored-dictionary.md) —
  основная фича.
- [`../features/140-fix-zakroma-censored-database.md`](../features/140-fix-zakroma-censored-database.md) —
  интеграция с Закромами.
- [`../features/141-fix-censored-web-storage-globals.md`](../features/141-fix-censored-web-storage-globals.md) —
  инициализация глобалов в `karaoke-web`.
- [`../domain/publishing.md`](../domain/publishing.md) — публикация и
  шаблоны новостей.
- [`../domain/processing.md`](../domain/processing.md) — общий паттерн
  `TextFileDictionary`.

## История

- Создан: 2026-08-14 (Pass 45 follow-up спеки 189-live-documentation)
- Последнее обновление: 2026-08-14