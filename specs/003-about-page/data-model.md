# Data Model: Страница «О проекте» (QW-9)

**Branch**: `003-about-page` | **Phase**: 1 | **Date**: 2026-07-25

Фича не вводит новых сущностей БД (см. `spec.md` → Key Entities, Assumptions:
«Не требует изменений в БД (только frontend)»). Ниже — переиспользуемые
существующие сущности/контракты, которые фича использует как есть.

---

## Переиспользуемая сущность 1: Author (из `tbl_authors`)

Источник: `GET /api/public/authors` (`karaoke-web/.../PublicApiController.kt#authors`).

Ответ — `List<String>` (просто имена авторов, без обёртки в объект):

```json
["Кино", "ДДТ", "Аквариум", "..."]
```

**Использование в фиче**: `AboutView.vue` фильтрует статический топ-15
(`TOP_AUTHORS`, hardcoded в компоненте) из полного списка → `otherAuthors`
(computed, алфавитная сортировка `localeCompare(..., 'ru')`).

**Примечание**: есть более богатый эндпоинт `GET /api/public/authors-tiles`
(возвращает `AuthorTilePublicDto`: имя + счётчик песен + признак
special-order) — `AboutView.vue` его не использует (не нужны счётчики/спецзаказ
на этой странице), берёт более простой `/authors`. Осознанный выбор, не
пробел.

---

## Переиспользуемая сущность 2: Stat (кеш `StatBySong`)

Источник: `GET /api/public/stats` (`karaoke-web/.../PublicApiController.kt#stats`).

```json
{
  "onSponsr": 0,
  "onAir": 0,
  "exclusive": 0,
  "inWork": 0,
  "total": 0
}
```

**Использование в фиче**: 4 карточки в секции «Коллекция»
(`onSponsr`→«Песен в коллекции», `onAir`→«В открытом доступе»,
`exclusive`→«По подписке», `inWork`→«В работе»). Поле `total` в UI не
используется.

---

## Статическая (не БД) сущность: TOP_AUTHORS

Hardcoded массив в `AboutView.vue` (15 записей: имя + примечание про состав
группы/сольные проекты). Не сущность БД — данные в исходниках компонента, как
и было заявлено в spec.md («AboutContent — статический, живёт в исходниках»).
Расширение до `tbl_authors.is_top boolean` — явно отложено на будущее
(комментарий в коде), не в скоупе этой фичи.

---

## Новая (для gap'ов) не-сущность: список площадок для «Поделиться»

Не БД-сущность — статический массив share-целей внутри `ShareButton.vue`
(аналогично `SocialLinks.vue`'s `links`):

| name | share-URL шаблон |
|------|-------------------|
| vk | `https://vk.com/share.php?url={pageUrl}` |
| telegram | `https://t.me/share/url?url={pageUrl}&text={title}` |
| whatsapp | `https://wa.me/?text={title}%20{pageUrl}` |
| ok | `https://connect.ok.ru/offer?url={pageUrl}&title={title}` |
| copy | `navigator.clipboard.writeText(pageUrl)` (не URL, отдельная ветка) |

`{pageUrl}` = `window.location.href` (URL-encoded), `{title}` = заголовок
страницы (URL-encoded).

---

## Что НЕ является сущностью этой фичи

- Новых таблиц/колонок/миграций — нет.
- Новых REST-эндпоинтов — нет (переиспользует `/api/public/authors`,
  `/api/public/stats`, существующий `/api/public/events` для трекинга кликов
  через `trackLinkToSocialNetwork`/`trackUi`).
- Vuex store — новый модуль не создаётся (см. `research.md` Decision 3).
