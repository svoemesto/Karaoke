# Contracts

Эта фича **не вводит новых внешних API**. Все изменения — внутренняя runtime-оптимизация в `HealthReport` и `KaraokeProcess`. UI не меняется.

**Существующие SSE-каналы** (без изменений):
- `HEALTH_REPORTS` — рассылается `HealthReport.recomputeAndBroadcast(songId, ...)`. После применения `key`/`bpm` из файла нужно вызвать этот метод вручную, чтобы UI обновился.

