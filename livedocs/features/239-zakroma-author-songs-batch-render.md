---
status: Active
slug: 239-zakroma-author-songs-batch-render
related:
  - ../domain/catalog.md
  - ../architecture/L3-components.md
  - ../../specs/239-zakroma-author-songs-batch-render/spec.md
---

# 239 — Закрома автора: отрисовка списка песен без N×3 фоновых запросов (LiveDoc)

> Drill-down — [specs/239-zakroma-author-songs-batch-render/spec.md](../../specs/239-zakroma-author-songs-batch-render/spec.md).

## Что делает

Устраняет freeze-баг на крупных авторах (~2500 песен у «Машины Времени»):
per-row readiness/membership → статусные флаги в DTO.

## Freeze-баг

Раньше для каждой песни загружались отдельно (≥3 запроса × 2500 = 7500):
доступность плеера, наличие в избранном, наличие в плейлистах. При прокрутке
списка вниз — спиннеры не останавливались, сайт «висел».

## Решение

* `idStatus` и `contentReady` (из `tbl_settings`) — приходят с песней разом.
* Иконка плеера рисуется на основе флагов без per-row запросов (Pass 100, `26_player_readiness_flags.sql`).
* Избранное/плейлисты — fetch-on-demand + локальный store.

## Effect

* Freeze устранён на крупных авторах.
* Сайт остаётся отзывчивым при прокрутке длинных списков.