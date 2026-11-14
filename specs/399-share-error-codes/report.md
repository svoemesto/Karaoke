# Отчёт по KARAOKE-399 «share-флоу: коды ошибок как строковые литералы вместо enum ShareErrorCode + неверный код при недопустимом TTL»

> **Задача**: KARAOKE-399 (Plane, workspace `svoemesto`, проект `KARAOKE`).
> **Ветка**: `766-share-error-codes` (feature-ветка от `master`).
> **Дата**: 2026-11-14.
> **Статус**: реализовано, поведение API для валидных запросов не изменено.

## 1. Что требовалось

1. Завести в `ShareErrorCode` отдельный код для недопустимого TTL.
2. Перевести контроллеры на enum (`dbValue`) вместо строковых литералов.
3. Вынести допустимые TTL в константу и переиспользовать её в проверке.
4. Не менять поведение API для валидных запросов.

Критерий готовности: `:karaoke-web:compileKotlin` — зелёный, `:karaoke-web:ktlintCheck` —
зелёный, добавлен юнит-тест «недопустимый TTL даёт 400 с НОВЫМ кодом, валидные TTL
работают как прежде», изменения закоммичены.

## 2. Что было

Конкретная ошибка: в `PublicShareController.create` при недопустимом TTL отдавался
`400` с `errorCode = "share.tokenMissing"` — код не соответствовал причине (токен тут
ни при чём, неверный TTL), из-за чего фронт показывал пользователю неверный текст.

Дополнительно: коды отдавались строковыми литералами в двух контроллерах, хотя в проекте
есть enum `ShareErrorCode` — «единственный источник правды, по нему фронт показывает
пользовательские тексты».

## 3. Что сделано

### 3.1. Новый код в enum

`ShareErrorCode.INVALID_TTL("share.invalidTtl")` — недопустимый срок действия ссылки.

Заодно в enum перенесён `NOT_EDITOR("share.notEditor")` — код отдавался
`SiteShareLinksController`, но в enum отсутствовал, то есть правило «enum — единственный
источник правды» для него и не выполнялось.

### 3.2. Константа допустимых TTL

Новый объект `karaoke-web/.../util/ShareTtlOptions.kt`:

```kotlin
const val ONE_HOUR: Long = 3600
const val ONE_DAY: Long = 86_400
const val ONE_WEEK: Long = 604_800
const val DEFAULT_SECONDS: Long = ONE_HOUR
val ALLOWED_SECONDS: Set<Long> = setOf(ONE_HOUR, ONE_DAY, ONE_WEEK)
fun isAllowed(ttlSeconds: Long): Boolean = ttlSeconds in ALLOWED_SECONDS
```

Проверка в контроллере стала `if (!ShareTtlOptions.isAllowed(ttlSeconds))` вместо
условия с тремя литералами. Набор значений не изменился: 3600 / 86400 / 604800.

Фронт (`karaoke-public/src/composables/useShareLink.js`, `SHARE_TTL_OPTIONS`) показывает
те же три варианта. Это разные приложения с общим рантаймом — связать их импортом нельзя,
поэтому список продублирован, и в обоих местах теперь стоит комментарий-ссылка друг на
друга.

### 3.3. Перевод контроллеров на enum

`PublicShareController` (13 мест) и `SiteShareLinksController` (5 мест): строковые
литералы `"share.tokenMissing"`, `"share.notFound"`, `"share.internal"`,
`share.leaseExpired"`, `"share.notOwner"`, `"share.notEditor"` заменены на
`ShareErrorCode.<КОНСТАНТА>.dbValue`.

Значения `dbValue` совпадают с прежними литералами один в один — поведение API для
валидных запросов и для обычных ошибок не изменилось.

### 3.4. Фронт узнаёт новый код

Маппинг кодов → пользовательские тексты вынесен из `ShareLinkModal.vue` в
`karaoke-public/src/utils/shareCreateError.js` (без зависимостей — тестируется через
`node --test`). Для `share.invalidTtl` добавлен собственный текст «Недопустимый срок
действия ссылки»; прежние тексты (премиум, недоступная песня, лимиты ссылок) перенесены
без изменений.

## 4. Проверка

### 4.1. Тесты

**Бэкенд** — новый `PublicShareControllerTtlTest` (6 кейсов):

| Тест | Что проверяет |
|---|---|
| `недопустимый TTL даёт 400 с кодом share invalidTtl` | 400 + `share.invalidTtl` |
| `недопустимый TTL не равен коду про отсутствие токена` | код не `share.tokenMissing` |
| `все допустимые TTL проходят проверку и доходят до создания ссылки` | 3600/86400/604800 доходят до `createLink` (409 — `SongUnavailable`) |
| `недопустимый TTL не доходит до создания ссылки` | `createLink` не вызывается |
| `набор допустимых TTL не изменился` | набор значений прежний |
| `код недопустимого TTL зарегистрирован в enum` | `dbValue == "share.invalidTtl"` |

Прогон модуля: `./gradlew :karaoke-web:test` — **240 тестов, 0 падений** (45 классов).
`:karaoke-web:ktlintCheck` — чисто.

**Фронт** — новый `karaoke-public/src/utils/__tests__/shareCreateError.test.js` (5 кейсов):
свой текст для недопустимого срока, совпадение кода с бэкендовым, прежние тексты
премиума/недоступной песни/лимитов, общий текст для неизвестного кода.

`npm run test:unit` — **348 тестов, 0 падений**. `npm run lint` — чисто.
`npx prettier --check` — чисто.

### 4.2. Обновлён существующий контрактный тест

`PkgS329SourceContractsTest` (BR-007) проверял **исходник** на наличие байтовых
литералов `"share.notEditor"` и `"share.internal"` в `SiteShareLinksController`. После
перевода на enum литералов там больше нет — и проверка стала проверять правило «код
берётся из enum», а не «в файле есть такая строка»: теперь она требует
`ShareErrorCode.NOT_EDITOR.dbValue` / `ShareErrorCode.INTERNAL.dbValue` в контроллере и
наличие самих значений в `ShareErrorCode.kt`. Смысл контракта (код отказа — 403
`share.notEditor`, `IllegalStateException` — 500 `share.internal`) сохранён.

### 4.3. Знание

- `knowledge/domains/karaoke-web/components/song-share-link-service.md` — в таблицу кодов
  добавлены `share.invalidTtl` и `share.notEditor`; в описании `POST /{songId}/create`
  указан новый код и `ShareTtlOptions.ALLOWED_SECONDS`; Changelog.
- `knowledge/system/error-handling.md` — в перечень кодов добавлен `share.invalidTtl`;
  зафиксировано, что источник правды — enum `ShareErrorCode`, а фронт выбирает текст по
  коду в `shareCreateError.js`; Changelog.

## 5. Что НЕ менялось

- Поведение API для валидных запросов: те же коды, те же статусы, те же тела ответа.
- Способы отказа, не связанные с TTL: `403 share.notOwner`, `409 share.songUnavailable`,
  `429 share.linkAlreadyActive`, `400 share.tokenMissing` — прежние значения.
- Набор допустимых TTL: 3600 / 86400 / 604800.
- `site.remote_unavailable` (503) — код другого домена (`site`), не share-флоу; в
  `ShareErrorCode` не переносился.
- Тексты ошибок на фронте, кроме нового кода.

## 6. Артефакты

- Код: `karaoke-web/.../util/ShareErrorCode.kt`, `karaoke-web/.../util/ShareTtlOptions.kt`,
  `karaoke-web/.../controllers/PublicShareController.kt`,
  `karaoke-web/.../controllers/SiteShareLinksController.kt`.
- Фронт: `karaoke-public/src/utils/shareCreateError.js`,
  `karaoke-public/src/components/ShareLinkModal.vue`,
  `karaoke-public/src/composables/useShareLink.js`.
- Тесты: `karaoke-web/src/test/kotlin/.../PublicShareControllerTtlTest.kt`,
  `karaoke-public/src/utils/__tests__/shareCreateError.test.js`,
  обновлён `karaoke-web/src/test/kotlin/.../audit/PkgS329SourceContractsTest.kt`.
- Знание: `knowledge/domains/karaoke-web/components/song-share-link-service.md`,
  `knowledge/system/error-handling.md`.
- Отчёт: `specs/399-share-error-codes/report.md`.
