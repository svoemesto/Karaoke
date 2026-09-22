# Отчёт по #154 — Таймаут доступа к удалённому хранилищу 5s → 20s

> **Спека**: [spec.md](spec.md) | **Ветка**: `430-remote-storage-timeout-20s` |
> **Дата**: 2026-09-22 | **Pass**: 430.

## Требование

Владелец: «надо установить таймаут не 5 секунд, а 20» — для доступа к удалённому
хранилищу.

## Что сделано

`storage.file-exists-timeout-seconds`: **5 → 20** во всех дефолтах:

- `application.yml:75` — значение + обновлён комментарий.
- `@Value` fallback `:5` → `:20` в `StorageCircuitBreaker.kt`,
  `StorageCircuitBreakerConfig.kt` (оба bean), `StorageApiClient.kt`.
- Обновлён KDoc (`StorageCircuitBreaker` — теперь OkHttp-таймаут, не `readTimeout=60s`).
- Knowledge: `storage-api-client.md` (default 20s), `docs/features/storage-metadata-cache.md` (V2.6).

## Что это меняет (remote-путь)

| Потребитель | Было | Стало |
|---|---|---|
| circuit `.timeout(...)` | 5s | **20s** |
| OkHttp `connectTimeout`/`readTimeout` (remote) | 5s | **20s** |
| watchdog-дедлайн (`timeout + buffer 10`) | 15s | **30s** |
| `writeTimeout` (upload) | 300s | 300s (без изменений) |

**Local не затронут** — у `KaraokeStorageServiceImpl` свои хардкод-таймауты
(connect 10s / read 30s), независимые от этого свойства (решение владельца).

## Зачем

Remote MinIO нестабилен (TCP-connect то проходит, то таймаутится; время коннекта
0.003s…5.16s). При `connectTimeout=5s` часть вызовов обрывалась на границе и circuit
открывался, хотя ответ приходил чуть позже. 20s даёт медленному, но живому бэкенду
шанс ответить и снижает ложные срабатывания.

## Совместимость

- Pass 426/428 не конфликтуют: `.timeout(20s)` по-прежнему реально прерывает
  блокирующий loader, а OkHttp connect/read (20s) ему не мешают (раньше было
  наоборот — OkHttp 15s > timeout 5s, из-за чего circuit залипал).
- env-override `STORAGE_FILE_EXISTS_TIMEOUT_SECONDS` сохранён.
- Тесты используют явные значения конструктора — дефолт их не меняет.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:test --tests "*Storage*"` | 30/30 PASS |
| `:karaoke-app:bootJar` | OK |
| `check-knowledge-structure.sh` | 9/9 OK |
| `check-knowledge-cross-links.sh` | 631/631 OK |
| `lint-knowledge.py --baseline` | PASS (no new) |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9) — значение читается при
  старте bean. После рестарта: `GET /api/health/circuit-breaker` должен показать
  `timeoutSeconds: 20` у обоих брейкеров.
