# Internal API Contracts: Премиум-автопубликация

Эта фича не вводит новых HTTP endpoint'ов. Ниже — контракт двух уже
существующих endpoint'ов (`karaoke-app`, `ApiController.kt`), которые
`webvue3` начинает вызывать из нового UI (кнопка «Повторить», FR-008
spec.md). Их поведение (тела запроса/ответа) не меняется этой фичей.

## `POST /api/song/publishPremiumTelegram`

Форсирует премиум-публикацию в Telegram для песни `id` (используется как
«Повторить» после `FAILED`, и как ручной триггер вне зависимости от
`premiumAutoPublishEnabled`).

**Request**: `id: Long` (query/form param)

**Response** (`Map<String, Any>`):

| Поле | Тип | Описание |
|---|---|---|
| `success` | Boolean | `true`, если состояние — `published`/`rendering`/`publishing` |
| `state` | String | `TelegramAutoPublishState.code` (`scheduled`/`rendering`/`publishing`/`published`/`send_failed`/`cancelled`) |
| `messageId` | String | Пусто (премиум не сохраняет id — `persistMessageId=false`) |
| `error` | String | Текст ошибки, если есть |
| `newsPremiumPublishPending` | Boolean | Состояние песни после вызова |
| `newsPremiumTelegramSent` | Boolean | — |
| `newsPremiumVkSent` | Boolean | — |
| `premiumAutoPublishState` | String | — |

**Идемпотентность**: если `idTelegramDemo` уже заполнен (AIR уже
произошёл) — `success=false`, `state="published"`, без побочных эффектов.

## `POST /api/song/publishPremiumVk`

Симметрично, для ВК. `postId` вместо `messageId` в ответе; идемпотентность
по `idVk`.

## Новые вычисляемые поля песни, используемые фронтендом (без нового endpoint'а)

Уже присутствуют в стандартном JSON песни (не `@JsonIgnore`), фронтенд
только начинает их читать:

- `newsPremiumPublishPending: Boolean`
- `newsPremiumTelegramSent: Boolean`
- `newsPremiumVkSent: Boolean`
- `premiumAutoPublishState: String`
- `premiumAutoPublishLastError: String`
- `telegramAutoPublishState: String`, `vkAutoPublishState: String`
- **Новые** (data-model.md): `premiumAttemptCountTelegram: String`,
  `premiumAttemptCountVk: String`

Лимит `premiumAutoPublishMaxAttempts` — уже отдаётся общим списком
`KaraokeProperties` (`webvue3` → Свойства), который фронтенд уже грузит
для других экранов; для расчёта `failed`-статуса (data-model.md) UI
использует то же значение (без нового endpoint'а).
