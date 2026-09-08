# Component: dictionaries

> **Домен**: [publishing](../domain.md)
> **Компонент**: централизованное хранение магических кодов публикации:
> `AccessMode`, `VisitorType`, пороги `BotScore`.

## Ответственность | Responsibility

В publishing-домене часто возникают ситуации, когда:

- строковое значение `"open"`/`"premium-only"` используется в API и SQL;
- числовой порог `BotScore > 0.7` определяет сегментацию трафика;
- enum `VisitorType` нужен для фильтрации ботов.

Эта компонента — единственное место, где эти константы определены.
Использование литералов в L3-спецификациях и коде без ссылки на эту
страницу — **критический дефект** (см. `audit-living-docs` «Magic Codes»).

## Интерфейсы и Контракты | Interfaces and Contracts

### `AccessMode` — режим доступа к песне

| Значение | Описание |
| --- | --- |
| `open` | Песня доступна всем, `publishDate` истёк, `isExclusive=false`. |
| `premium-only` | Только для подписчиков (`isExclusive=true` ИЛИ `publishDate` в будущем). |

**Место определения**: `karaoke-app/.../model/AccessMode.kt`.

**Контракт**: стабильный, используется в API (`/api/songs/{id}/access`).

**Запрещено**: хардкод `"open"`/`"premium-only"` в коде (вместо —
`AccessMode.OPEN.name.lowercase()` или `AccessMode.PREMIUM_ONLY`).

### `VisitorType` — сегмент трафика

| Значение | Описание |
| --- | --- |
| `real_user` | Реальный пользователь (`BotScore < 0.5`). |
| `good_bot` | Известный бот (Google, Yandex), учитывается в счётчиках. |
| `bad_bot` | Подозрительный бот (`BotScore >= 0.7`), **не учитывается**. |

**Место определения**: `karaoke-app/.../model/VisitorType.kt`.

**Запрещено**: хардкод строковых литералов `"real_user"` и т.п.

### `BotScore` — пороги

| Диапазон | VisitorType |
| --- | --- |
| `0.0..0.5` | `real_user` |
| `0.5..0.7` | пограничная зона (требует ручного разбора) |
| `0.7..1.0` | `bad_bot` |

**Место определения**: константа `BOT_SCORE_THRESHOLD = 0.7` в
`StatsService.kt`.

**Запрещено**: использовать `0.7` напрямую в коде — только через
константу.

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм определения `VisitorType`

```kotlin
fun classifyVisitor(botScore: Double): VisitorType = when {
    botScore < 0.5 -> VisitorType.REAL_USER
    botScore < 0.7 -> VisitorType.REAL_USER  // пограничные считаем как реальные
    else -> VisitorType.BAD_BOT
}
```

**[WARN]** Текущая реализация «пограничные считаем как реальные» —
преднамеренное решение. Изменение требует анализа накопленной статистики.

### Алгоритм определения `AccessMode` для песни

```kotlin
fun accessModeFor(song: Song, user: SiteUser?): AccessMode {
    if (song.isExclusive) return AccessMode.PREMIUM_ONLY
    if (song.publishDate?.isAfter(Instant.now()) == true) {
        return AccessMode.PREMIUM_ONLY  // ещё не вышла в эфир
    }
    return AccessMode.OPEN
}
```

Grandfathered песни (см. domain invariants) — отдельная логика в
`StatsService.accessModeForGrandfathered()`.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `PublishWindow`, `Subscription`, `SiteStats`.
- → [identity](../identity/domain.md) — `SiteUser` для проверки подписки.
- → [catalog](../catalog/domain.md) — `Song.isExclusive`, `Song.publishDate`.
- → [stats-cache](stats-cache.md) — счётчики используют `VisitorType`.

## Ловушки и предупреждения

**[WARN] Не путать `isExclusive` и `publishDate` в будущем** — оба
дают `AccessMode.PREMIUM_ONLY`, но семантически разные:
- `isExclusive=true` — песня намеренно premium-only (бизнес-решение).
- `publishDate в будущем` — песня ещё не вышла в эфир (тайминг).

**[WARN] `BotScore` динамически меняется** — порог `0.7` основан на
статистике 2026-08. Если в будущем bot-атаки станут тоньше, порог
нужно пересмотреть через отдельный эпик.

## Связанные ADR | Related ADRs

- ADR по [caching context](../caching/domain.md) для `StatBySong` — TODO.
