# Data Model: Удаление 18 столбцов-флагов из таблицы «Песни»

**Created**: 2026-08-06
**Feature**: [spec.md](./spec.md)

## Изменения в данных

**Нет.** Эта фича — чисто UI-удаление 18 столбцов из отображения в админке `webvue3`.

## Существующие сущности (не изменяются)

### `Song` (таблица `tbl_settings` / `tbl_settings_sync` в PostgreSQL)

Поля, **затрагиваемые логически** (но не удаляемые):
| Поле БД | Тип | Где отображалось в UI | Что происходит |
|---------|-----|----------------------|----------------|
| `flag_sponsr` | bool | SP | Значение остаётся в БД, в админке больше не видно |
| `flag_vk` | bool | VG | Значение остаётся в БД, в админке больше не видно |
| `flag_dzen_lyrics` | bool | ZL | Значение остаётся в БД, в админке больше не видно |
| `flag_dzen_karaoke` | bool | ZK | Значение остаётся в БД, в админке больше не видно |
| `flag_dzen_chords` | bool | ZC | Значение остаётся в БД, в админке больше не видно |
| `flag_dzen_melody` | bool | ZM | Значение остаётся в БД, в админке больше не видно |
| `flag_vk_lyrics` | bool | VL | Значение остаётся в БД, в админке больше не видно |
| `flag_vk_karaoke` | bool | VK | Значение остаётся в БД, в админке больше не видно |
| `flag_vk_chords` | bool | VC | Значение остаётся в БД, в админке больше не видно |
| `flag_vk_melody` | bool | VM | Значение остаётся в БД, в админке больше не видно |
| `flag_telegram_lyrics` | bool | TL | Значение остаётся в БД, в админке больше не видно |
| `flag_telegram_karaoke` | bool | TK | Значение остаётся в БД, в админке больше не видно |
| `flag_telegram_chords` | bool | TC | Значение остаётся в БД, в админке больше не видно |
| `flag_telegram_melody` | bool | TM | Значение остаётся в БД, в админке больше не видно |
| `flag_max_lyrics` | bool | ML | Значение остаётся в БД, в админке больше не видно |
| `flag_max_karaoke` | bool | MK | Значение остаётся в БД, в админке больше не видно |
| `flag_max_chords` | bool | MC | Значение остаётся в БД, в админке больше не видно |
| `flag_max_melody` | bool | MM | Значение остаётся в БД, в админке больше не видно |

### Vuex Store `webvue3/src/components/Songs/store.js`

State-поля, **затрагиваемые**:
- `state.fieldSongParams[]` — массив описаний колонок. 10 из 18 удаляемых определений (`flagSponsr`, `flagVk`, `flagDzenLyrics`, `flagDzenKaraoke`, `flagVkLyrics`, `flagVkKaraoke`, `flagTelegramLyrics`, `flagTelegramKaraoke`, `flagMaxLyrics`, `flagMaxKaraoke`) удаляются. 8 остальных уже отсутствуют.

State-поля, **НЕ затрагиваемые**:
- `publications`, `unpublications`, `skipedpublications` — массивы публикаций (используются в `Publications`-роуте, не связаны с таблицей песен).
- `songsDigest`, `countRows` и прочие — не связаны.

### Vuex Store `webvue3/src/components/Publish/store.js`

**НЕ затрагивается.** Использует те же `processColorSponsr/Vk` и геттеры `playLyrics/Karaoke/Chords`, но относится к разделу «Публикации», а не «Песни».

## Миграции

**Не требуются.** Никаких изменений схемы БД, типов, или API-контрактов нет.

## Связи между сущностями

```
Song (PostgreSQL)
  ↓ /api/songs/list
SongDTO (бэкенд)
  ↓ JSON
state.songs (Vuex, webvue3)
  ↓ fields[] + <template #cell(...)>
SongsTable.vue (UI)
```

Удаление затрагивает только последнюю стрелку (UI-отображение). Все upstream/downstream элементы цепочки сохраняются как есть.

## Валидация / правила

Нет новых правил валидации. Существующие правила (boolean в БД, флаги не могут быть null) сохраняются без изменений.
