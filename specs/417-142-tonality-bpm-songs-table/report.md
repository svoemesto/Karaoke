# Отчёт по #142 — Тональность и темп в таблице песен

> **Спека**: [spec.md](spec.md) | **Ветка**: `417-142-tonality-bpm-songs-table` |
> **Дата**: 2026-09-21.

## Что сделано

В таблицу песен админки добавлены колонки **Ton** (тональность сокращённо) и
**BPM** (темп) между `t/c` и `HR`.

- **Backend** (`karaoke-app`):
  - `SongDTOdigest` расширен полями `key: String` и `bpm: Long`
    (default `""`/`0`);
  - `SongDTO.toDtoDigest()` заполняет их из `SongDTO.key`/`SongDTO.bpm`.
    Эндпоинт `POST /api/songsdigests` теперь отдаёт эти поля.
- **Frontend** (`webvue3`):
  - `SongsTable.vue`: импорт `KaraokePlayer`, метод `shortKey(key)`
    (делегирует в `KaraokePlayer._shortKey`, `-` при пустом);
  - в `songDigestFields` добавлены `tonality` (`Ton`, не сортируется) и
    `bpm` (`BPM`, сортируется) между `timecode` и `healthReportText`;
  - template-слоты `#cell(tonality)` / `#cell(bpm)`; CSS `.fld-tonality`/`.fld-bpm`.
- **Knowledge/docs**: обновлены `knowledge/domains/integration/components/dtos.md`,
  `knowledge/system/frontend/webvue3-views-detailed.md`; новый
  `docs/features/tonality-bpm-songs-table.md`.

## Принятые решения

- **Единый источник сокращения**: используется `KaraokePlayer._shortKey`
  (импорт из `webvue3/src/player/KaraokePlayer.js`), а не копия логики. В
  предыдущей (отклонённой владельцем) попытке логика копировалась inline —
  это расхождение устранено.
- **Поля — в `SongDTOdigest`**, а не отдельным запросом: таблица уже грузит
  digest-массив (`POST /api/songsdigests`).
- **`-` для пустых значений** (паттерн таблиц админки).

## Отличие от предыдущего (отклонённого) захода

Карта `#143` и тикеты `#144`–`#149` были закрыты/отклонены; реализация
опиралась на несуществующие поля digest и копировала логику тональности. В этой
реализации добавлен backend-контракт (`SongDTOdigest.key/bpm`) и переиспользована
функция плеера.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` | OK |
| `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | OK |
| `webvue3 npm run lint` | OK |
| `webvue3 npm run build` | OK (7.90s) |
| `webvue3 npm run format:check` | OK (только предсуществующие vendored sockjs MD) |
| `check-feature-doc.sh docs/features/tonality-bpm-songs-table.md` | OK |
| `check-ssot-impact.py` | OK (правил для изменённых файлов нет) |

## Известные ограничения / ручная валидация

- Визуальная проверка колонок в браузере и рестарт контейнера `karaoke-app` —
  за владельцем (машинное правило nsa-i9).
- `SongDTOdigest.key` — сырая строка; если keybpmfinder записал нестандартный
  формат, `_shortKey` вернёт исходную строку (fallback, как в плеере).

## Файлы

- `karaoke-app/.../model/SongDTOdigest.kt`, `.../model/SongDTO.kt`
- `webvue3/src/components/Songs/SongsTable.vue`
- `knowledge/domains/integration/components/dtos.md`,
  `knowledge/system/frontend/webvue3-views-detailed.md`
- `docs/features/tonality-bpm-songs-table.md`
- `specs/417-142-tonality-bpm-songs-table/*`
