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

**[WARN] Поправка Pass 474: типа `AccessMode` не существует.**
Файла `model/AccessMode.kt` нет; `AccessMode` встречается в
репозитории только внутри комментариев (`Song.kt:923`,
`deploy/karaoke-db/50_tbl_songs_free_after_on_air.sql:5`).

Реальная логика «эфирная/платная» — в `model/SongStateResolver.kt`
(свободный доступ после истечения `publishDate` при
`isExclusive=false`). Ни типа-перечисления, ни эндпоинта
`/api/songs/{id}/access` в коде нет.

Таблица значений ниже сохранена как описание ЗАДУМАННОЙ модели:

### `VisitorType` — сегмент трафика

| Значение | Описание |
| --- | --- |
| `real_user` | Реальный пользователь (`BotScore < 0.5`). |
| `good_bot` | Известный бот (Google, Yandex), учитывается в счётчиках. |
| `bad_bot` | Подозрительный бот (`BotScore >= 0.7`), **не учитывается**. |

**[WARN] Поправка Pass 474: `VisitorType` не существует** —
файла `model/VisitorType.kt` нет, строки `real_user`/`good_bot`/
`bad_bot` не встречаются ни в одном `.kt`/`.sql`. Описание ниже —
не реализованная модель.

### `BotScore` — пороги

| Диапазон | VisitorType |
| --- | --- |
| `0.0..0.5` | `real_user` |
| `0.5..0.7` | пограничная зона (требует ручного разбора) |
| `0.7..1.0` | `bad_bot` |

**[WARN] Поправка Pass 474: `BotScore` и `BOT_SCORE_THRESHOLD`
не существуют** — ни класса `StatsService`, ни константы, ни поля
`bot_score` в миграциях нет. Порогов детекции ботов в коде нет;
описание ниже — не реализованная модель.

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
- → [identity](../../identity/domain.md) — `SiteUser` для проверки подписки.
- → [catalog](../../catalog/domain.md) — `Song.isExclusive`, `Song.publishDate`.
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

- ADR по [caching context](../../caching/domain.md) для `StatBySong` — TODO.
