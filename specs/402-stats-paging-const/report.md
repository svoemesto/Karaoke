# Отчёт по KARAOKE-402 «karaoke-web: магические числа limit=1000 и pageSize=50 в MainController — вынести в константы»

> **Задача**: KARAOKE-402 (Plane, workspace `svoemesto`, проект `KARAOKE`).
> **Ветка**: `767-stats-paging-const` (feature-ветка от `master`).
> **Дата**: 2026-11-14.
> **Статус**: реализовано, значения не изменились.

## 1. Что требовалось

В `karaoke-web/.../controllers/MainController.kt` магические числа: строка 674 —
`getStatBySong(database = WORKING_DATABASE, limit = 1000)`, строка 707 —
`@RequestParam(required = false, defaultValue = "50") pageSize: Int`.

Что сделать:
1. Вынести значения в именованные константы рядом с местом использования (`companion object`)
   с коротким KDoc: почему 1000 (страховка на полную выборку, полная выгрузка — через
   REST `/api/stats/by-song?page=N&pageSize=50`) и почему дефолт `pageSize = 50`.
2. Не менять поведение: значения остаются теми же.
3. Проверить, нет ли в этом же контроллере других таких же дефолтов пагинации, и привести
   их к тому же виду.

Критерии готовности: `:karaoke-web:compileKotlin` — зелёный, `:karaoke-web:ktlintCheck` —
зелёный, diff содержит только вынесение констант и комментариев без изменения логики,
изменения закоммичены.

## 2. Что сделано

### 2.1. Константы

В `companion object` `MainController` добавлены шесть констант:

| Константа | Значение | Зачем |
|---|---|---|
| `STAT_BY_SONG_PAGE_LIMIT` | 1000 | верх выборки для страницы `/statbysong` (страховка на полную выборку) |
| `STAT_BY_SONG_DEFAULT_PAGE_SIZE` | "50" | дефолт `pageSize` в выгрузке `/statbysong-export` |
| `STAT_BY_SONG_DEFAULT_PAGE` | "1" | дефолт номера страницы в выгрузке |
| `STAT_BY_SONG_MIN_PAGE` | 1 | минимальный номер страницы (safety-guard) |
| `STAT_BY_SONG_MIN_PAGE_SIZE` | 1 | минимальный размер страницы (safety-guard) |
| `STAT_BY_SONG_MAX_PAGE_SIZE` | 1000 | верхний размер страницы (safety-guard) |
| `LATEST_NEWS_LIMIT` | 5 | сколько последних новостей показывает блок на главной |

Дефолты `page` и `pageSize` — **строки**, а не числа: они подставляются в
`@RequestParam(defaultValue = …)`, где нужен compile-time constant строкового типа.
Числовые границы (`coerceIn`/`coerceAtLeast`) — числа.

### 2.2. KDoc — почему именно такие значения

- **1000 (`STAT_BY_SONG_PAGE_LIMIT`)** — до FR-007 (Pass 241) здесь был `limit = 100_000`:
  минутный запрос на всю `tbl_events` с 17 условными `count(*) filter (...)`. Страница
  показывает топ-1000, полная выгрузка — через REST `/statbysong-export?page=N&pageSize=50`.
- **50 (`STAT_BY_SONG_DEFAULT_PAGE_SIZE`)** — совпадает с размером страницы, который баннер
  на странице `/statbysong` предлагает в ссылке «полная выгрузка» (`?page=N&pageSize=50`),
  чтобы переход по баннеру выглядел для администратора так же, как выборка по умолчанию.
- **1000 (`STAT_BY_SONG_MAX_PAGE_SIZE`)** — совпадает с `MAX_STAT_BY_SONG_LIMIT` в
  `karaoke-app` (`StatBySong.kt`), где SQL clamp'ит `limit` в `[1, 1000]`. Дублирование
  осознанное: контроллер отсекает слишком большую страницу ДО похода в БД.

### 2.3. Пункт 3 задачи — другие дефолты в этом же контроллере

Проверен весь `MainController` (1033 строки). Найдено и приведено к тому же виду:

- **`limit = 5` в блоке «последние новости» на главной** (`News.loadPublished`) — тот же
  класс литерала в том же контроллере; вынесен в `LATEST_NEWS_LIMIT`.
- **Границы пагинации в выгрузке** — `coerceAtLeast(1)` и `coerceIn(1, 1000)`; вынесены в
  `STAT_BY_SONG_MIN_PAGE`, `STAT_BY_SONG_MIN_PAGE_SIZE`, `STAT_BY_SONG_MAX_PAGE_SIZE`.
  Это те же «дефолты пагинации», только неявные.
- **Дефолт `page = "1"`** — тоже неявный дефолт пагинации; вынесен.

Не тронуто: закомментированный код (`// @RequestParam(… defaultValue = "604800") …`) —
это мёртвый код, а не поведение.

Других дефолтов пагинации в контроллере нет: остальные `@RequestParam` — без `defaultValue`
(`author`, `songName`, `text`, `album`, `id`, `data`).

## 3. Проверка

- `:karaoke-web:compileKotlin` — зелёный.
- `:karaoke-web:ktlintCheck` — зелёный.
- `:karaoke-web:test` — **240 тестов, 0 падений** (45 классов).
- Grep-проверка: в `MainController` не осталось литералов вида `limit = <число>`,
  `defaultValue = "<число>"`, `coerceIn(<число>…)`, `coerceAtLeast(<число>)` — только
  упоминания в KDoc.
- Знание: `knowledge/domains/karaoke-web/components/thymeleaf-templates.md` — описание
  лимитов и дефолтов пагинации + Changelog.

## 4. Что НЕ менялось

- Значения: 1000 (лимит выборки), 50 (дефолт `pageSize`), 1 (минимальные границы), 5 (новости).
- Логика: ни один оператор не изменён — только замена литералов на константы.
- Поведение эндпоинтов `/statbysong` и `/statbysong-export` — прежнее.

## 5. Артефакты

- Код: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/MainController.kt`.
- Знание: `knowledge/domains/karaoke-web/components/thymeleaf-templates.md`.
- Отчёт: `specs/402-stats-paging-const/report.md`.
