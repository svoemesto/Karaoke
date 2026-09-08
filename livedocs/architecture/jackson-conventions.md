---
status: Active
slug: jackson-conventions
type: topic
related:
  - ../domain/identity.md
  - ../features/185-song-dto-audit-sponsr-remove.md
---

# Jackson — конвенции для Kotlin DTO

> Drill-down из `AGENTS.md` Q&A «Jackson отбрасывает `is` в boolean-полях Kotlin DTO».
> Этот LiveDoc — полная версия. В AGENTS.md осталась только короткая ссылка.

## Проблема

В Kotlin data class Jackson по умолчанию **отбрасывает префикс `is`** в
boolean-полях при сериализации в JSON. Это особенность Kotlin-Jackson-маппинга
(`is`-префикс считается частью геттера `isXxx` → Jackson `Boolean`-биндинг
отбрасывает его как языковой префикс, а не как префикс имени поля).

## Симптом

- Данные компилируются и работают на бэке.
- Фронт получает пустые значения / `undefined`.
- Первое подозрение — баг в коде или binding к форме.
- На самом деле проблема в **имени JSON-поля**: фронт ждёт `isXxx`, бэк
  отдаёт `xxx`.

Пример:
```kotlin
// Kotlin DTO
data class AuthorDTO(
  val isSpecialOrder: Boolean = false
)

// Сериализованный JSON (без @JsonProperty)
{"specialOrder": true}   // ← фронт не видит поле как "isSpecialOrder"
```

## Решение — ВСЕГДА `@JsonProperty("isOriginalName")` на boolean-полях DTO

```kotlin
import com.fasterxml.jackson.annotation.JsonProperty

data class AuthorDTO(
  @get:JsonProperty("isSpecialOrder")
  val isSpecialOrder: Boolean = false,
)
```

**С аннотацией** поле попадёт во ВСЕ ответы API как `{"isSpecialOrder": true}`.

## Где применять

✅ **Применить ко всем DTO**, у которых boolean-поле идёт в `get*`/`post*` API:

- `karaoke-app/src/main/kotlin/.../dto/*DTO.kt` (responses)
- `karaoke-web/src/main/kotlin/.../dto/*Dto.kt` (responses)
- Любые другие классы, сериализующиеся как ответ API контроллера.

❌ **НЕ нужно** на `@RequestParam` (Spring берёт параметр напрямую по имени —
там Jackson-конвенция НЕ применяется).

## Дополнительно — boolean updateable через админку

Не использовать `save()` через `getDiff()` для boolean-апдейтов (плохо
работает с boxed `Boolean?` + recordhash-триггер на любые изменения ломает
sync LOCAL↔SERVER). Лучше прямой `UPDATE` через `@RequestParam`.

## Исторический контекст

**M-23 «Спецзаказные» PR #48** был сломан именно этим — DTO `AuthorDTO`
сериализовало `isSpecialOrder` → `{"specialOrder": ...}`, фронт ждал
`isSpecialOrder`. Поле «не работало» во фронте 2 дня.

**Фикс в PR #49**: добавили `@JsonProperty("isSpecialOrder")` на всех
boolean-полях `AuthorDTO`.

## Применённые фиксы (на 2026-08-14)

- ✅ `AuthorDTO.isSpecialOrder` — PR #49
- ✅ `SiteUserDTO.isActive`, `canSelfAssign` — аналогичный паттерн
- ✅ Все boolean-поля в `SongDTO` / `AlbumDTO` — проверка в фиче 185

## `@RequestBody` без `jackson-module-kotlin` — НЕ использовать Kotlin data class

**Проблема**: Spring Boot 3.x в проекте Karaoke **НЕ включает** `jackson-module-kotlin` в
classpath (`build.gradle.kts` имеет `kotlin-reflect`, `kotlin-stdlib`, но НЕ
`jackson-module-kotlin`). Без модуля Jackson не может десериализовать Kotlin data class
через `@RequestBody` (primary constructor с `val`-параметрами), даже если Content-Type
правильный (`application/json`) — Spring не находит подходящий `HttpMessageConverter` →
**HTTP 415 Unsupported Media Type**.

**Симптом**: `POST /api/.../endpoint` с `Content-Type: application/json` и валидным JSON →
backend отдаёт 415. Frontend видит «HTTP 415». Content-Type заголовок правильный,
body валидный — но Spring не может распарсить body в Kotlin data class без Jackson
модуля.

**Решение** (выбрать одно):

### Вариант A: использовать `Map<String, Any?>` (максимально совместимо)

Соответствует существующему паттерну в проекте (см. `KaraokeProcessAdminController.edit` —
`@RequestBody changes: Map<String, Any?>`):

```kotlin
@PostMapping("/api/admin/processes/bulk-update")
fun bulkUpdate(
    @RequestBody changes: Map<String, Any?>,
    @RequestHeader("X-Admin-Username") username: String?,
): BulkOperationReport {
    val ids = changes["ids"] as? List<Int>
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ids required")
    val field = changes["field"] as? String
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "field required")
    // ...
}
```

Плюсы: ноль зависимостей, работает «из коробки».
Минусы: нет type-safety в IDE, runtime-ошибки при неправильном типе.

### Вариант B: plain class с сеттерами (Jackson setter-based)

Если нужен type-safety в контроллере, но не хочется добавлять зависимость:

```kotlin
class BulkUpdateRequest {
    var ids: List<Int>? = null
    var field: String? = null
    var value: Any? = null
    var batchId: UUID? = null
}
```

Jackson по умолчанию использует setter-based deserialization — работает без Kotlin module.
В контроллере использовать `req.field ?: throw ResponseStatusException(...)` для nullable.

### ❌ НЕ делать: Kotlin data class без модуля

```kotlin
data class BulkUpdateRequest(
    val ids: List<Int>,
    val field: String,
    val value: Any?,
    val batchId: UUID? = null,
)
```

Без `jackson-module-kotlin` Jackson **не может десериализовать** в primary constructor.
Spring 6 / Boot 3.x в этом случае возвращает 415 (даже при правильном Content-Type).

**Правило для Karaoke**:
- `@RequestBody` принимает `Map<String, Any?>` — паттерн проекта (см. `editProcess`)
- `@RequestBody` принимает plain class с сеттерами — допустимо, но менее распространено
- `@RequestBody` принимает Kotlin data class — **ТОЛЬКО** если добавить
  `jackson-module-kotlin` в `build.gradle.kts` (сейчас не добавлено)

## Связанные LiveDocs

- Domain: [identity.md](../domain/identity.md) (SiteUser с boolean-полями)
- Feature: [185-song-dto-audit-sponsr-remove.md](../features/185-song-dto-audit-sponsr-remove.md)
- Feature: [319-process-bulk-actions.md](../features/319-process-bulk-actions.md) (где это впервые проявилось)

## Связанные LiveDocs

- Domain: [identity.md](../domain/identity.md) (SiteUser с boolean-полями)
- Feature: [185-song-dto-audit-sponsr-remove.md](../features/185-song-dto-audit-sponsr-remove.md)

## Код

- Пример правильного DTO: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/AuthorDTO.kt`
- Тест: ручной — `curl` к API и проверить JSON-ключи

## История

- Создан: 2026-08-14 (мигрировано из `AGENTS.md` v1.7.1)
- Последнее обновление: 2026-08-14