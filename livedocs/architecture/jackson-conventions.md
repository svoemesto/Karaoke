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

## Связанные LiveDocs

- Domain: [identity.md](../domain/identity.md) (SiteUser с boolean-полями)
- Feature: [185-song-dto-audit-sponsr-remove.md](../features/185-song-dto-audit-sponsr-remove.md)

## Код

- Пример правильного DTO: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/AuthorDTO.kt`
- Тест: ручной — `curl` к API и проверить JSON-ключи

## История

- Создан: 2026-08-14 (мигрировано из `AGENTS.md` v1.7.1)
- Последнее обновление: 2026-08-14