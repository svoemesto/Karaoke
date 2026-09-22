# Отчёт по #155 — Устойчивость синхронизации с прод-сайтом (sync resilience)

> **Спека**: [spec.md](spec.md) | **Ветка**: `431-sync-resilience` |
> **Дата**: 2026-09-22 | **Pass**: 431.

## Симптом

2026-09-22 13:10, логи `karaoke-app`:

```
ERROR [nio-8899-exec-3] dispatcherServlet : Servlet.service() ... threw exception
javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake
    at ...UtilsKt.updateDatabases(Utils.kt:1261)
    at ...UtilsKt.runEntitySync(Utils.kt:1074)
    at ...ApiController.postSyncOneClick(ApiController.kt:5256)
Caused by: java.io.IOException: Connection timed out
```

Кратковременный сетевой сбой до `sm-karaoke.ru` валил **всю** «Синхронизацию в
1 клик» в HTTP 500 со стектрейсом.

## Root cause

6 вызовов `POST https://sm-karaoke.ru/changerecords` в `Utils.kt`
(`setSongToSyncRemoteTable`, `updateDatabases`) строились одноразовым
`HttpClient.newBuilder().build()`:

- **без таймаутов** — потенциально долгое зависание;
- **без `try/catch`** — `IOException`/`SSLException` летела наверх;
- в `postSyncOneClick` не было `catch` → DispatcherServlet → HTTP 500.

## Что сделано

- **`SyncRemoteClient.kt`** (NEW) — единый helper:
  - `connectTimeout=10s`, `requestTimeout=60s`;
  - **1 retry** (задержка 2s) на транзиентные ошибки (`SSLException`,
    `ConnectException`, `HttpTimeoutException`, `IOException` timeout/reset);
  - внутренний `try/catch` — наружу не бросает (`true`/`false`);
  - логи категории `infra.sync.remote` (`ok`/`retry`/`failure`).
- **`Utils.kt`** — все 6 call sites `POST /changerecords` заменены на
  `SyncRemoteClient.postChangeRecords(requestBody)`; `updateDatabases` не
  пропагирует сетевые ошибки.
- **`ApiController.postSyncOneClick`** — per-target `try/catch` (как
  `AutoOneClickSyncScheduler`, FR-012 спеки #235); `SyncOneClickResultDto.error`.
- **`webvue3 SyncTable.vue`** — `showResultAlert` показывает `r.error`.
- **Knowledge SSoT + per-feature doc**: `run-entity-sync.md`, `two-db-sync.md`,
  `log-categories.md` (`infra.sync.remote`), `docs/features/sync-resilience.md`.
- **R-11 baseline** перенумерован (сдвиг строк `Utils.kt`/`ApiController.kt`).

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `SyncRemoteClientTest` | 7/7 PASS |
| `:karaoke-app:bootJar` | OK |
| `webvue3 npm run lint` | OK |
| `check-knowledge-structure.sh` | 9/9 OK |
| `check-knowledge-cross-links.sh` | 631/631 OK |
| `check-feature-doc.sh docs/features/sync-resilience.md` | OK |
| `check-no-mp4-mentions.sh` | OK (baseline) |

## Поведение после фикса

| Ситуация | До | После |
|---|---|---|
| 1-я попытка транзиентно упала | HTTP 500 | 1 retry; при успехе — без ошибки |
| Обе попытки упали | HTTP 500 + стек | `perTarget[].error`, HTTP 200 |
| Одна сущность упала | весь клик падает | остальные обрабатываются |
| Зависание соединения | бесконечно | таймаут 10s/60s |

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9).
- Нестабильность канала до прод-сайта — инфраструктурная задача (не код).
